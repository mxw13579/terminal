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
 * 负责处理所有SFTP相关操作的服务
 * @author lizelin
 */
@Slf4j
@Service
public class SftpService {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public SftpService(ObjectMapper objectMapper,
                       @Qualifier("taskExecutor") ExecutorService executorService) {
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }
    /**
     * 分片缓存
     */
    private final Map<String, List<byte[]>> uploadChunks = new ConcurrentHashMap<>();

    /**
     * 处理列出SFTP目录的请求
     */
    public void handleSftpList(WebSocketSession session, SshConnection sshConnection, String path) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            path = (path == null || path.isEmpty() || path.equals(".")) ? channelSftp.getHome() : path;
            String absolutePath = channelSftp.realpath(path);

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);
            List<Map<String, Object>> fileList = new ArrayList<>();

            // 添加 ".." 返回上级目录的条目
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

            // 目录优先，然后按名称不区分大小写排序
            fileList.sort(Comparator.comparing((Map<String, Object> m) -> (Boolean) m.get("isDirectory")).reversed()
                    .thenComparing(m -> (String) m.get("name"), String.CASE_INSENSITIVE_ORDER));

            Map<String, Object> response = Map.of(
                    "type", "sftp_list_response",
                    "path", absolutePath,
                    "files", fileList
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JSchException | SftpException e) {
            log.error("SFTP List Error: " + e.getMessage(), e);
            sendSftpError(session, "SFTP操作失败: " + e.getMessage());
        }
    }

    /**
     * 处理SFTP文件/目录下载请求
     */
    public void handleSftpDownload(WebSocketSession session, SshConnection sshConnection, List<String> paths) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            if (paths.size() == 1) {
                // 单个文件或目录
                String filePath = paths.get(0);
                SftpATTRS attrs = channelSftp.lstat(filePath);
                if (attrs.isDir()) {
                    // 压缩目录
                    byte[] zipData = zipDirectoryToBytes(channelSftp, filePath);
                    sendDownloadResponse(session, Paths.get(filePath).getFileName().toString() + ".zip", zipData);
                } else {
                    // 下载单个文件
                    try (InputStream inputStream = channelSftp.get(filePath); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        inputStream.transferTo(baos);
                        sendDownloadResponse(session, Paths.get(filePath).getFileName().toString(), baos.toByteArray());
                    }
                }
            } else {
                // 多个文件/目录
                byte[] zipData = zipMultiplePathsToBytes(channelSftp, paths);
                sendDownloadResponse(session, "download.zip", zipData);
            }
        } catch (JSchException | SftpException e) {
            log.error("SFTP Download Error: " + e.getMessage(), e);
            sendSftpError(session, "SFTP下载失败: " + e.getMessage());
        }
    }

    /**
     * 处理分片上传
     */
    public void handleSftpUploadChunk(WebSocketSession session, SshConnection sshConnection, String remotePath, String filename, int chunkIndex, int totalChunks, String contentBase64) throws IOException {
        String uploadKey = session.getId() + ":" + remotePath + "/" + filename;
        List<byte[]> chunks = uploadChunks.computeIfAbsent(uploadKey, k -> Collections.synchronizedList(new ArrayList<>(Collections.nCopies(totalChunks, null))));
        byte[] decodedChunk = Base64.getDecoder().decode(contentBase64);
        chunks.set(chunkIndex, decodedChunk);

        // 检查所有分片是否已到达
        if (chunks.stream().allMatch(Objects::nonNull)) {
            List<byte[]> finalChunks = uploadChunks.remove(uploadKey);
            if (finalChunks == null) {
                log.warn("Upload task for {} already processed or removed.", uploadKey);
                return; // 任务已被其他线程处理
            }

            // 在后台线程中合并文件并上传
            executorService.submit(() -> assembleAndUploadFile(session, sshConnection, remotePath, filename, finalChunks));
        } else {
            // 告知前端分片上传成功
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "sftp_upload_chunk_success",
                    "chunkIndex", chunkIndex,
                    "totalChunks", totalChunks
            ))));
        }
    }

    /**
     * 清理指定会话的上传缓存
     */
    public void clearUploadCacheForSession(String sessionId) {
        uploadChunks.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
    }

    private void assembleAndUploadFile(WebSocketSession session, SshConnection sshConnection, String remotePath, String filename, List<byte[]> finalChunks) {
        // 为上传操作创建一个临时的、独立的sftp通道，避免与主通道冲突
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

                // 进度监控初始化
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
                    sftpChannel.disconnect(); // 手动断开SFTP通道
                }
            }
        } catch (Exception e) {
            log.error("上传文件{}到{}失败", filename, remotePath, e);
            try {
                sendSftpError(session, "后台上传文件失败: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("发送SFTP上传错误消息失败", ioException);
            }
        }
    }


    // --- 私有辅助方法 ---

    private byte[] zipDirectoryToBytes(ChannelSftp channelSftp, String dirPath) throws IOException, SftpException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            zipDirectory(channelSftp, dirPath, "", zos);
        }
        return zipOut.toByteArray();
    }

    private byte[] zipMultiplePathsToBytes(ChannelSftp channelSftp, List<String> paths) throws IOException, SftpException {
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

    private void sendDownloadResponse(WebSocketSession session, String filename, byte[] data) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "sftp_download_response",
                "filename", filename,
                "content", Base64.getEncoder().encodeToString(data)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void zipDirectory(ChannelSftp sftp, String dirPath, String base, ZipOutputStream zos) throws SftpException, IOException {
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

    private void zipFile(ChannelSftp sftp, String filePath, String zipEntryName, ZipOutputStream zos) throws SftpException, IOException {
        zos.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream is = sftp.get(filePath)) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    private void sendSftpError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> errorResponse = Map.of(
                "type", "sftp_error",
                "message", message
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }
}
