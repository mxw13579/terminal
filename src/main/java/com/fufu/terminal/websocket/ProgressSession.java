package com.fufu.terminal.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress Session tracking for individual script executions
 * 
 * Tracks the progress and metadata for a single script execution session.
 */
@Data
@AllArgsConstructor
public class ProgressSession {
    private final String sessionId;
    private final String scriptName;
    private final long startTime;
    private String currentStage;
    private Integer currentPercentage;
    private String currentDetails;
    private final List<ProgressRecord> progressHistory;
    
    public ProgressSession(String sessionId, String scriptName) {
        this.sessionId = sessionId;
        this.scriptName = scriptName;
        this.startTime = System.currentTimeMillis();
        this.currentStage = "Initialization";
        this.currentPercentage = 0;
        this.progressHistory = new ArrayList<>();
    }
    
    /**
     * Update progress information
     */
    public void updateProgress(String stage, Integer percentage, String details) {
        // Record previous progress
        if (currentStage != null) {
            progressHistory.add(new ProgressRecord(currentStage, currentPercentage, 
                currentDetails, System.currentTimeMillis() - startTime));
        }
        
        this.currentStage = stage;
        this.currentPercentage = percentage;
        this.currentDetails = details;
    }
    
    /**
     * Get elapsed time in seconds
     */
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
    
    /**
     * Get elapsed time in milliseconds
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Check if session is active (not too old)
     */
    public boolean isActive(long maxAgeMs) {
        return getElapsedMs() < maxAgeMs;
    }
    
    /**
     * Get progress summary
     */
    public String getProgressSummary() {
        return String.format("%s: %s (%d%%) - %ds elapsed", 
            scriptName, currentStage, currentPercentage != null ? currentPercentage : 0, getElapsedSeconds());
    }
    
    /**
     * Progress Record for history tracking
     */
    @Data
    @AllArgsConstructor
    public static class ProgressRecord {
        private String stage;
        private Integer percentage;
        private String details;
        private long elapsedMs;
    }
}