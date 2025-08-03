package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * STOMP message for requesting SFTP directory listing.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpListRequest extends StompMessage {
    
    /**
     * Path to list files from (default is current directory).
     */
    @NotBlank
    private String path = ".";
    
    /**
     * Constructor for creating SFTP list requests.
     */
    public SftpListRequest(String path) {
        super("sftp_list");
        this.path = path;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpListRequest() {
        super("sftp_list");
    }
}