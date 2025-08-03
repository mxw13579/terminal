package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * STOMP message for requesting SFTP file/directory download.
 * Supports single file or multiple file/directory download.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpDownloadRequest extends StompMessage {
    
    /**
     * List of file/directory paths to download.
     */
    @NotEmpty
    private List<String> paths;
    
    /**
     * Whether to compress multiple files into a ZIP archive.
     */
    private boolean compress = true;
    
    /**
     * Constructor for creating download requests.
     */
    public SftpDownloadRequest(List<String> paths) {
        super("sftp_download");
        this.paths = paths;
    }
    
    /**
     * Constructor with compression option.
     */
    public SftpDownloadRequest(List<String> paths, boolean compress) {
        super("sftp_download");
        this.paths = paths;
        this.compress = compress;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpDownloadRequest() {
        super("sftp_download");
    }
}