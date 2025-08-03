package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * STOMP message for uploading file chunks via SFTP.
 * Supports chunked file upload with progress tracking.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpUploadChunkMessage extends StompMessage {
    
    /**
     * Remote path where the file should be uploaded.
     */
    @NotBlank
    private String path;
    
    /**
     * Name of the file being uploaded.
     */
    @NotBlank
    private String filename;
    
    /**
     * Index of this chunk (0-based).
     */
    @Min(0)
    private int chunkIndex;
    
    /**
     * Total number of chunks for this file.
     */
    @Min(1)
    private int totalChunks;
    
    /**
     * Base64-encoded content of this chunk.
     */
    @NotBlank
    private String content;
    
    /**
     * Size of this chunk in bytes (before Base64 encoding).
     */
    @Min(0)
    private long chunkSize;
    
    /**
     * Total file size in bytes.
     */
    @Min(0)
    private long totalSize;
    
    /**
     * Constructor for creating upload chunk messages.
     */
    public SftpUploadChunkMessage(String path, String filename, int chunkIndex, 
                                  int totalChunks, String content) {
        super("sftp_upload_chunk");
        this.path = path;
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.content = content;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpUploadChunkMessage() {
        super("sftp_upload_chunk");
    }
}