package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * STOMP message for system monitoring updates.
 * Contains real-time system statistics and Docker container information.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MonitorUpdateMessage extends StompMessage {
    
    /**
     * CPU model information.
     */
    private String cpuModel;
    
    /**
     * System uptime.
     */
    private String uptime;
    
    /**
     * CPU usage percentage (0-100).
     */
    private double cpuUsage;
    
    /**
     * Memory usage percentage (0-100).
     */
    private double memUsage;
    
    /**
     * Disk usage percentage (0-100).
     */
    private double diskUsage;
    
    /**
     * Network receive rate (formatted string with units).
     */
    private String netRx;
    
    /**
     * Network transmit rate (formatted string with units).
     */
    private String netTx;
    
    /**
     * List of Docker containers with their status and resource usage.
     */
    private List<Map<String, String>> dockerContainers;
    
    /**
     * Timestamp when the monitoring data was collected.
     */
    private long collectionTime;
    
    /**
     * Constructor for creating monitor update messages.
     */
    public MonitorUpdateMessage() {
        super("monitor_update");
        this.collectionTime = System.currentTimeMillis();
    }
    
    /**
     * Constructor with payload data.
     */
    public MonitorUpdateMessage(Map<String, Object> payload) {
        super("monitor_update");
        this.collectionTime = System.currentTimeMillis();
        
        // Map the payload data to individual fields
        if (payload != null) {
            this.cpuModel = (String) payload.get("cpuModel");
            this.uptime = (String) payload.get("uptime");
            this.cpuUsage = payload.get("cpuUsage") instanceof Number ? 
                ((Number) payload.get("cpuUsage")).doubleValue() : 0.0;
            this.memUsage = payload.get("memUsage") instanceof Number ? 
                ((Number) payload.get("memUsage")).doubleValue() : 0.0;
            this.diskUsage = payload.get("diskUsage") instanceof Number ? 
                ((Number) payload.get("diskUsage")).doubleValue() : 0.0;
            this.netRx = (String) payload.get("netRx");
            this.netTx = (String) payload.get("netTx");
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> containers = (List<Map<String, String>>) payload.get("dockerContainers");
            this.dockerContainers = containers;
        }
    }
}