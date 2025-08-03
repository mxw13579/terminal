package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * STOMP response message for SFTP directory listing.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpListResponse extends StompMessage {
    
    /**
     * The path that was listed.
     */
    @NotBlank
    private String path;
    
    /**
     * List of files and directories in the path.
     */
    private List<Map<String, Object>> files;
    
    /**
     * Constructor for creating list responses.
     */
    public SftpListResponse(String path, List<Map<String, Object>> files) {
        super("sftp_list_response");
        this.path = path;
        this.files = files;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SftpListResponse() {
        super("sftp_list_response");
    }
}