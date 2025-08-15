package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.handler.WebSocketSftpProgressMonitor;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * SFTP操作服务，负责处理所有SFTP相关的文件上传、下载、目录浏览等操作。
 * <p>
 * 支持分片上传、目录压缩下载、文件进度监控等功能。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class SftpService {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    /**
     * 分片上传缓存，key为sessionId:remotePath/filename，value为分片内容列表
     */
    private final Map<String, List<byte[]>> uploadChunks = new ConcurrentHashMap<>();

    /**
     * 构造SFTP服务
     *
     * @param objectMapper   JSON对象映射器
     * @param executorService 线程池执行器
     */
    public SftpService(ObjectMapper objectMapper,
                       @Qualifier("taskExecutor") ExecutorService executorService) {
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    /**
     * 处理SFTP目录列表请求，返回指定路径下的文件和目录信息。
     *
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     * @param path           需要列出的目录路径
     * @throws IOException   发送消息失败时抛出
     */
    public void handleSftpList(final WebSocketSession session, final SshConnection sshConnection, String path) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            
            // 🔧 修复：检查SFTP连接状态并强制重新获取干净的连接
            log.debug("检查SFTP连接状态: connected={}, closed={}", 
                channelSftp.isConnected(), channelSftp.isClosed());
            
            if (!channelSftp.isConnected() || channelSftp.isClosed()) {
                log.warn("SFTP通道状态异常，强制重新连接...");
                // 强制关闭旧连接并获取新连接
                try {
                    channelSftp.disconnect();
                } catch (Exception e) {
                    log.debug("关闭旧SFTP连接时出错: {}", e.getMessage());
                }
                channelSftp = sshConnection.getOrCreateSftpChannel();
                log.info("已重新建立SFTP连接");
            }
            
            path = (path == null || path.isEmpty() || path.equals(".")) ? channelSftp.getHome() : path;
            
            // 修复realpath调用问题 - JSch库在某些服务器上会抛出"Success"异常
            String absolutePath;
            try {
                absolutePath = channelSftp.realpath(path);
            } catch (SftpException e) {
                // 如果realpath失败，尝试直接使用path
                log.warn("realpath失败，使用原始路径: {} (错误: {})", path, e.getMessage());
                absolutePath = path;
                
                // 如果原始路径也是相对路径，尝试获取当前工作目录
                if (!absolutePath.startsWith("/")) {
                    try {
                        String currentDir = channelSftp.pwd();
                        absolutePath = currentDir.endsWith("/") ? currentDir + path : currentDir + "/" + path;
                        log.info("构造绝对路径: {}", absolutePath);
                    } catch (SftpException pwdEx) {
                        log.warn("无法获取当前工作目录: {}", pwdEx.getMessage());
                    }
                }
                
                // 验证路径是否存在和可访问
                try {
                    channelSftp.lstat(absolutePath);
                } catch (SftpException statEx) {
                    log.error("路径不存在或不可访问: {}，尝试使用HOME目录", absolutePath);
                    // 最后的备用方案：使用HOME目录
                    try {
                        absolutePath = channelSftp.getHome();
                        log.info("使用HOME目录作为备用: {}", absolutePath);
                        // 再次验证HOME目录是否可访问
                        channelSftp.lstat(absolutePath);
                    } catch (SftpException homeEx) {
                        log.error("HOME目录也无法访问: {}，尝试使用根目录", homeEx.getMessage());
                        // 最终备用方案：使用根目录
                        absolutePath = "/";
                        try {
                            channelSftp.lstat(absolutePath);
                            log.info("使用根目录作为最终备用: {}", absolutePath);
                        } catch (SftpException rootEx) {
                            log.error("根目录也无法访问: {}", rootEx.getMessage());
                            throw new IOException("无法访问任何有效目录");
                        }
                    }
                }
            }

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);
            List<Map<String, Object>> fileList = new ArrayList<>();

            // 添加返回上级目录的条目
            if (!"/".equals(absolutePath)) {
                // 🔧 修复：使用Unix路径处理，避免Windows路径混淆
                String parentPath;
                if (absolutePath.endsWith("/")) {
                    absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
                }
                int lastSlash = absolutePath.lastIndexOf('/');
                if (lastSlash <= 0) {
                    parentPath = "/";
                } else {
                    parentPath = absolutePath.substring(0, lastSlash);
                    if (parentPath.isEmpty()) {
                        parentPath = "/";
                    }
                }
                
                fileList.add(Map.of(
                        "name", "..",
                        "longname", "d---------   - owner group         0 Jan 01 00:00 ..",
                        "isDirectory", true,
                        "path", parentPath
                ));
            }

            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue;
                }
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", entry.getFilename());
                fileInfo.put("longname", entry.getLongname());
                fileInfo.put("isDirectory", entry.getAttrs().isDir());
                fileInfo.put("size", entry.getAttrs().getSize());
                fileInfo.put("mtime", entry.getAttrs().getMTime());
                // 🔧 修复：使用Unix路径拼接，避免Windows路径问题
                String fullPath;
                if (absolutePath.endsWith("/")) {
                    fullPath = absolutePath + entry.getFilename();
                } else {
                    fullPath = absolutePath + "/" + entry.getFilename();
                }
                fileInfo.put("path", fullPath);
                fileList.add(fileInfo);
            }

            // 目录优先，名称排序
            fileList.sort(Comparator.comparing((Map<String, Object> m) -> (Boolean) m.get("isDirectory")).reversed()
                    .thenComparing(m -> (String) m.get("name"), String.CASE_INSENSITIVE_ORDER));

            Map<String, Object> response = Map.of(
                    "type", "sftp_list_response",
                    "path", absolutePath,
                    "files", fileList
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JSchException | SftpException e) {
            log.error("SFTP目录列表失败: {}", e.getMessage(), e);
            sendSftpError(session, "SFTP操作失败: " + e.getMessage());
        }
    }

    /**
     * 处理SFTP文件或目录下载请求，支持单文件、单目录（压缩为zip）、多文件/目录（打包为zip）。
     *
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     * @param paths          需要下载的文件/目录路径列表
     * @throws IOException   发送消息失败时抛出
     */
    public void handleSftpDownload(final WebSocketSession session, final SshConnection sshConnection, final List<String> paths) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            if (paths.size() == 1) {
                String filePath = paths.get(0);
                SftpATTRS attrs = channelSftp.lstat(filePath);
                if (attrs.isDir()) {
                    // 目录压缩为zip
                    byte[] zipData = zipDirectoryToBytes(channelSftp, filePath);
                    sendDownloadResponse(session, Paths.get(filePath).getFileName().toString() + ".zip", zipData);
                } else {
                    // 单文件下载
                    try (InputStream inputStream = channelSftp.get(filePath); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        inputStream.transferTo(baos);
                        sendDownloadResponse(session, Paths.get(filePath).getFileName().toString(), baos.toByteArray());
                    }
                }
            } else {
                // 多文件/目录打包为zip
                byte[] zipData = zipMultiplePathsToBytes(channelSftp, paths);
                sendDownloadResponse(session, "download.zip", zipData);
            }
        } catch (JSchException | SftpException e) {
            log.error("SFTP下载失败: {}", e.getMessage(), e);
            sendSftpError(session, "SFTP下载失败: " + e.getMessage());
        }
    }

    /**
     * 处理分片上传，接收单个分片并缓存，全部分片到齐后合并上传到服务器。
     * 
     * @deprecated 该方法已废弃，建议使用HTTP流式传输（StreamingFileService）
     * 原因：高内存消耗，扩展性差，对大文件有OOM风险
     * 
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     * @param remotePath     远程目录路径
     * @param filename       文件名
     * @param chunkIndex     当前分片索引
     * @param totalChunks    总分片数
     * @param contentBase64  分片内容（Base64编码）
     * @throws IOException   发送消息失败时抛出
     */
    @Deprecated
    public void handleSftpUploadChunk(final WebSocketSession session, final SshConnection sshConnection,
                                      final String remotePath, final String filename,
                                      final int chunkIndex, final int totalChunks, final String contentBase64) throws IOException {
        
        // 检查文件大小，对大文件推荐使用HTTP流式传输
        if (totalChunks > 100) { // 假设每片1MB，大于100MB的文件
            sendSftpError(session, "文件过大，建议使用HTTP流式上传功能获得更好的性能和稳定性");
            return;
        }
        
        final String uploadKey = session.getId() + ":" + remotePath + "/" + filename;
        List<byte[]> chunks = uploadChunks.computeIfAbsent(uploadKey, k -> Collections.synchronizedList(new ArrayList<>(Collections.nCopies(totalChunks, null))));
        byte[] decodedChunk = Base64.getDecoder().decode(contentBase64);
        chunks.set(chunkIndex, decodedChunk);

        // 检查所有分片是否已到达
        if (chunks.stream().allMatch(Objects::nonNull)) {
            List<byte[]> finalChunks = uploadChunks.remove(uploadKey);
            if (finalChunks == null) {
                log.warn("上传任务 {} 已被处理或移除。", uploadKey);
                return;
            }
            // 合并并上传文件，使用线程池异步处理
            executorService.submit(() -> assembleAndUploadFile(session, sshConnection, remotePath, filename, finalChunks));
        } else {
            // 通知前端分片上传成功
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "sftp_upload_chunk_success",
                    "chunkIndex", chunkIndex,
                    "totalChunks", totalChunks
            ))));
        }
    }

    /**
     * 清理指定WebSocket会话的上传分片缓存。
     *
     * @param sessionId WebSocket会话ID
     */
    public void clearUploadCacheForSession(final String sessionId) {
        uploadChunks.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
    }

    /**
     * 合并所有分片并上传文件到SFTP服务器，上传完成后通知前端。
     *
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     * @param remotePath     远程目录路径
     * @param filename       文件名
     * @param finalChunks    所有分片内容
     */
    private void assembleAndUploadFile(final WebSocketSession session, final SshConnection sshConnection,
                                       final String remotePath, final String filename, final List<byte[]> finalChunks) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (byte[] chunk : finalChunks) {
                baos.write(chunk);
            }
            byte[] fileBytes = baos.toByteArray();

            ChannelSftp sftpChannel = null;
            try {
                sftpChannel = (ChannelSftp) sshConnection.getJschSession().openChannel("sftp");
                sftpChannel.connect(5000);
                String fullRemotePath = Paths.get(remotePath, filename).normalize().toString().replace("\\", "/");

                // 进度监控
                WebSocketSftpProgressMonitor monitor = new WebSocketSftpProgressMonitor(session, objectMapper);
                monitor.init(SftpProgressMonitor.PUT, "local-stream", fullRemotePath, fileBytes.length);

                try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                    sftpChannel.put(inputStream, fullRemotePath, monitor);
                }

                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                        "type", "sftp_upload_final_success",
                        "message", String.format("文件 '%s' 已成功上传到服务器。", filename),
                        "path", remotePath
                ))));
            } finally {
                if (sftpChannel != null) {
                    sftpChannel.disconnect();
                }
            }
        } catch (Exception e) {
            log.error("上传文件 {} 到 {} 失败", filename, remotePath, e);
            try {
                sendSftpError(session, "后台上传文件失败: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("发送SFTP上传错误消息失败", ioException);
            }
        }
    }

    /**
     * 将指定目录压缩为zip格式字节数组。
     *
     * @param channelSftp SFTP通道
     * @param dirPath     目录路径
     * @return zip压缩后的字节数组
     * @throws IOException    IO异常
     * @throws SftpException  SFTP异常
     */
    private byte[] zipDirectoryToBytes(final ChannelSftp channelSftp, final String dirPath) throws IOException, SftpException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            zipDirectory(channelSftp, dirPath, "", zos);
        }
        return zipOut.toByteArray();
    }

    /**
     * 将多个文件/目录压缩为zip格式字节数组。
     *
     * @param channelSftp SFTP通道
     * @param paths       文件/目录路径列表
     * @return zip压缩后的字节数组
     * @throws IOException    IO异常
     * @throws SftpException  SFTP异常
     */
    private byte[] zipMultiplePathsToBytes(final ChannelSftp channelSftp, final List<String> paths) throws IOException, SftpException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            for (String path : paths) {
                SftpATTRS attrs = channelSftp.lstat(path);
                String entryName = Paths.get(path).getFileName().toString();
                if (attrs.isDir()) {
                    zipDirectory(channelSftp, path, entryName + "/", zos);
                } else {
                    zipFile(channelSftp, path, entryName, zos);
                }
            }
        }
        return zipOut.toByteArray();
    }

    /**
     * 发送下载响应到前端，内容为Base64编码。
     *
     * @param session  WebSocket会话
     * @param filename 文件名
     * @param data     文件内容字节数组
     * @throws IOException 发送消息失败时抛出
     */
    private void sendDownloadResponse(final WebSocketSession session, final String filename, final byte[] data) throws IOException {
        try {
            // 处理包含特殊字符的文件名，确保JSON序列化安全
            String safeFilename = filename;
            if (filename != null) {
                // 移除或替换可能导致JSON序列化问题的字符
                safeFilename = filename.replaceAll("[\\p{Cntrl}\\p{So}]", "_");
                if (safeFilename.length() > 100) {
                    // 限制文件名长度
                    String ext = "";
                    int dotIndex = safeFilename.lastIndexOf('.');
                    if (dotIndex > 0) {
                        ext = safeFilename.substring(dotIndex);
                        safeFilename = safeFilename.substring(0, Math.min(100 - ext.length(), dotIndex));
                    } else {
                        safeFilename = safeFilename.substring(0, 100);
                    }
                    safeFilename += ext;
                }
            }

            Map<String, Object> response = Map.of(
                    "type", "sftp_download_response",
                    "filename", safeFilename,
                    "content", Base64.getEncoder().encodeToString(data)
            );
            
            String jsonResponse = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(jsonResponse));
            
        } catch (Exception e) {
            log.error("发送SFTP下载响应失败，filename: {}, error: {}", filename, e.getMessage(), e);
            // 发送错误响应
            sendSftpError(session, "下载响应发送失败: " + e.getMessage());
        }
    }

    /**
     * 递归压缩目录下所有文件和子目录到Zip输出流。
     *
     * @param sftp      SFTP通道
     * @param dirPath   当前目录路径
     * @param base      zip内相对路径前缀
     * @param zos       Zip输出流
     * @throws SftpException SFTP异常
     * @throws IOException   IO异常
     */
    private void zipDirectory(final ChannelSftp sftp, final String dirPath, final String base, final ZipOutputStream zos) throws SftpException, IOException {
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(dirPath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                continue;
            }
            String fullPath = Paths.get(dirPath, entry.getFilename()).toString().replace("\\", "/");
            String zipEntryName = base + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                zipDirectory(sftp, fullPath, zipEntryName + "/", zos);
            } else {
                zipFile(sftp, fullPath, zipEntryName, zos);
            }
        }
    }

    /**
     * 压缩单个文件到Zip输出流。
     *
     * @param sftp         SFTP通道
     * @param filePath     文件路径
     * @param zipEntryName zip内文件名
     * @param zos          Zip输出流
     * @throws SftpException SFTP异常
     * @throws IOException   IO异常
     */
    private void zipFile(final ChannelSftp sftp, final String filePath, final String zipEntryName, final ZipOutputStream zos) throws SftpException, IOException {
        zos.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream is = sftp.get(filePath)) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    /**
     * 发送SFTP错误消息到前端。
     *
     * @param session WebSocket会话
     * @param message 错误信息
     * @throws IOException 发送消息失败时抛出
     */
    private void sendSftpError(final WebSocketSession session, final String message) throws IOException {
        Map<String, Object> errorResponse = Map.of(
                "type", "sftp_error",
                "message", message
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }
}
