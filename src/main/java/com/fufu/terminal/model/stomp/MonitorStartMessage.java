package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * STOMP message for starting system monitoring.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MonitorStartMessage extends StompMessage {
    
    /**
     * Monitoring frequency in seconds (optional, defaults to high frequency).
     */
    private int frequencySeconds = 3;
    
    /**
     * Whether to include Docker container monitoring.
     */
    private boolean includeDocker = true;
    
    /**
     * Constructor for creating monitor start messages.
     */
    public MonitorStartMessage() {
        super("monitor_start");
    }
    
    /**
     * Constructor with frequency.
     */
    public MonitorStartMessage(int frequencySeconds) {
        super("monitor_start");
        this.frequencySeconds = frequencySeconds;
    }
}