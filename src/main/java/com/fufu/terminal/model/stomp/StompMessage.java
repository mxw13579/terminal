package com.fufu.terminal.model.stomp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Base class for all STOMP messages in the SSH terminal application.
 * Provides common fields and structure for type-safe message handling.
 * 
 * @author lizelin
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TerminalDataMessage.class, name = "terminal_data"),
    @JsonSubTypes.Type(value = TerminalResizeMessage.class, name = "terminal_resize"),
    @JsonSubTypes.Type(value = SftpListRequest.class, name = "sftp_list"),
    @JsonSubTypes.Type(value = SftpListResponse.class, name = "sftp_list_response"),
    @JsonSubTypes.Type(value = SftpUploadChunkMessage.class, name = "sftp_upload_chunk"),
    @JsonSubTypes.Type(value = SftpUploadProgressMessage.class, name = "sftp_upload_progress"),
    @JsonSubTypes.Type(value = SftpDownloadRequest.class, name = "sftp_download"),
    @JsonSubTypes.Type(value = SftpDownloadResponse.class, name = "sftp_download_response"),
    @JsonSubTypes.Type(value = MonitorStartMessage.class, name = "monitor_start"),
    @JsonSubTypes.Type(value = MonitorStopMessage.class, name = "monitor_stop"),
    @JsonSubTypes.Type(value = MonitorUpdateMessage.class, name = "monitor_update"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "error")
})
public abstract class StompMessage {
    
    /**
     * Message type identifier.
     */
    private String type;
    
    /**
     * Timestamp when the message was created.
     */
    private long timestamp = System.currentTimeMillis();
    
    /**
     * Optional correlation ID for tracking request/response pairs.
     */
    private String correlationId;
    
    /**
     * Constructor with type.
     */
    protected StompMessage(String type) {
        this.type = type;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    protected StompMessage() {
    }
}