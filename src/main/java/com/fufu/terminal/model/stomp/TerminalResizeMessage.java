package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Min;

/**
 * STOMP message for terminal resize operations.
 * Sent when the terminal window is resized to update the PTY size.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TerminalResizeMessage extends StompMessage {
    
    /**
     * Number of columns in the terminal.
     */
    @Min(1)
    private int cols;
    
    /**
     * Number of rows in the terminal.
     */
    @Min(1)
    private int rows;
    
    /**
     * Constructor for creating terminal resize messages.
     */
    public TerminalResizeMessage(int cols, int rows) {
        super("terminal_resize");
        this.cols = cols;
        this.rows = rows;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public TerminalResizeMessage() {
        super("terminal_resize");
    }
}