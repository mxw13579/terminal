package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * STOMP message for terminal data input/output.
 * Handles both user input to the terminal and terminal output to the user.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TerminalDataMessage extends StompMessage {
    
    /**
     * Terminal data payload (input commands or output text).
     */
    @NotBlank
    private String payload;
    
    /**
     * Constructor for creating terminal data messages.
     */
    public TerminalDataMessage(String payload) {
        super("terminal_data");
        this.payload = payload;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public TerminalDataMessage() {
        super("terminal_data");
    }
}