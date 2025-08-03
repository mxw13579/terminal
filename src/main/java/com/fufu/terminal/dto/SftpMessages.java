package com.fufu.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * SFTP相关消息DTO
 * 
 * @author lizelin
 */
public class SftpMessages {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListRequest {
        private String path = ".";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadRequest {
        private List<String> paths;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadChunkRequest {
        private String path;
        private String filename;
        private Integer chunkIndex;
        private Integer totalChunks;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private String type = "sftp_list_response";
        private String path;
        private Object files;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadProgress {
        private String type = "sftp_upload_chunk_success";
        private Integer chunkIndex;
        private Integer totalChunks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoteProgress {
        private String type = "sftp_remote_progress";
        private Integer progress;
        private String speed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadSuccess {
        private String type = "sftp_upload_final_success";
        private String message;
        private String path;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadResponse {
        private String type = "sftp_download_response";
        private String filename;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String type = "sftp_error";
        private String message;
    }
}