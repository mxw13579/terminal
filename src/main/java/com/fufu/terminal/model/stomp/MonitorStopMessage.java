package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * STOMP message for stopping system monitoring.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MonitorStopMessage extends StompMessage {
    
    /**
     * Whether to completely stop monitoring or switch to low frequency.
     */
    private boolean completelStop = false;
    
    /**
     * Constructor for creating monitor stop messages.
     */
    public MonitorStopMessage() {
        super("monitor_stop");
    }
    
    /**
     * Constructor with stop option.
     */
    public MonitorStopMessage(boolean completeStop) {
        super("monitor_stop");
        this.completelStop = completeStop;
    }
}