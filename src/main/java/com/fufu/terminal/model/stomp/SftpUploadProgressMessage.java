package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * STOMP message for SFTP upload progress updates.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpUploadProgressMessage extends StompMessage {
    
    /**
     * Name of the file being uploaded.
     */
    @NotBlank
    private String filename;
    
    /**
     * Number of bytes transferred so far.
     */
    @Min(0)
    private long bytesTransferred;
    
    /**
     * Total size of the file in bytes.
     */
    @Min(0)
    private long totalSize;
    
    /**
     * Upload progress as a percentage (0-100).
     */
    @Min(0)
    private double progressPercent;
    
    /**
     * Current transfer speed in bytes per second.
     */
    @Min(0)
    private long speedBps;
    
    /**
     * Constructor for creating progress messages.
     */
    public SftpUploadProgressMessage(String filename, long bytesTransferred, long totalSize) {
        super("sftp_upload_progress");
        this.filename = filename;
        this.bytesTransferred = bytesTransferred;
        this.totalSize = totalSize;
        this.progressPercent = totalSize > 0 ? (double) bytesTransferred / totalSize * 100 : 0;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpUploadProgressMessage() {
        super("sftp_upload_progress");
    }
}