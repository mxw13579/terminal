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
            path = (path == null || path.isEmpty() || path.equals(".")) ? channelSftp.getHome() : path;
            String absolutePath = channelSftp.realpath(path);

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);
            List<Map<String, Object>> fileList = new ArrayList<>();

            // 添加返回上级目录的条目
            if (!"/".equals(absolutePath)) {
                fileList.add(Map.of(
                        "name", "..",
                        "longname", "d---------   - owner group         0 Jan 01 00:00 ..",
                        "isDirectory", true,
                        "path", Paths.get(absolutePath, "..").normalize().toString().replace("\\", "/")
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
                fileInfo.put("path", Paths.get(absolutePath, entry.getFilename()).normalize().toString().replace("\\", "/"));
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
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     * @param remotePath     远程目录路径
     * @param filename       文件名
     * @param chunkIndex     当前分片索引
     * @param totalChunks    总分片数
     * @param contentBase64  分片内容（Base64编码）
     * @throws IOException   发送消息失败时抛出
     */
    public void handleSftpUploadChunk(final WebSocketSession session, final SshConnection sshConnection,
                                      final String remotePath, final String filename,
                                      final int chunkIndex, final int totalChunks, final String contentBase64) throws IOException {
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
        Map<String, Object> response = Map.of(
                "type", "sftp_download_response",
                "filename", filename,
                "content", Base64.getEncoder().encodeToString(data)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
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
