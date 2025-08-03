package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * STOMP response message for SFTP file download.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpDownloadResponse extends StompMessage {
    
    /**
     * Name of the downloaded file.
     */
    @NotBlank
    private String filename;
    
    /**
     * Base64-encoded file content.
     */
    @NotBlank
    private String content;
    
    /**
     * Size of the file in bytes (before Base64 encoding).
     */
    private long size;
    
    /**
     * MIME type of the file (if known).
     */
    private String mimeType;
    
    /**
     * Constructor for creating download responses.
     */
    public SftpDownloadResponse(String filename, String content) {
        super("sftp_download_response");
        this.filename = filename;
        this.content = content;
    }
    
    /**
     * Constructor with size.
     */
    public SftpDownloadResponse(String filename, String content, long size) {
        super("sftp_download_response");
        this.filename = filename;
        this.content = content;
        this.size = size;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpDownloadResponse() {
        super("sftp_download_response");
    }
}