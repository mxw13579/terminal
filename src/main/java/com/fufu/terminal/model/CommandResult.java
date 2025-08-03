package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Command Execution Result
 * 
 * Contains the result of executing a command via SSH including
 * output, error information, and execution metadata.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {
    
    private String command;
    private int exitCode;
    private String output;
    private String errorOutput;
    private long executionTimeMs;
    private boolean success;
    private Instant startTime;
    private Instant endTime;
    private String connectionInfo;
    
    /**
     * Create a successful command result
     */
    public static CommandResult success(String command, String output, long executionTimeMs) {
        return CommandResult.builder()
            .command(command)
            .exitCode(0)
            .output(output)
            .executionTimeMs(executionTimeMs)
            .success(true)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a failed command result
     */
    public static CommandResult failure(String command, int exitCode, String errorOutput, long executionTimeMs) {
        return CommandResult.builder()
            .command(command)
            .exitCode(exitCode)
            .errorOutput(errorOutput)
            .executionTimeMs(executionTimeMs)
            .success(false)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a timeout result
     */
    public static CommandResult timeout(String command, long executionTimeMs) {
        return CommandResult.builder()
            .command(command)
            .exitCode(-1)
            .errorOutput("Command execution timed out")
            .executionTimeMs(executionTimeMs)
            .success(false)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Check if command executed successfully
     */
    public boolean isSuccess() {
        return exitCode == 0 && success;
    }
    
    /**
     * Get formatted output combining stdout and stderr
     */
    public String getFormattedOutput() {
        StringBuilder result = new StringBuilder();
        
        if (isSuccess()) {
            return output != null ? output : "";
        } else {
            result.append("Command failed with exit code ").append(exitCode);
            
            if (output != null && !output.trim().isEmpty()) {
                result.append("\n\nSTDOUT:\n").append(output);
            }
            
            if (errorOutput != null && !errorOutput.trim().isEmpty()) {
                result.append("\n\nSTDERR:\n").append(errorOutput);
            }
            
            return result.toString();
        }
    }
    
    /**
     * Get combined output (stdout + stderr)
     */
    public String getCombinedOutput() {
        StringBuilder result = new StringBuilder();
        
        if (output != null && !output.trim().isEmpty()) {
            result.append(output);
        }
        
        if (errorOutput != null && !errorOutput.trim().isEmpty()) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(errorOutput);
        }
        
        return result.toString();
    }
    
    /**
     * Check if there is any output (stdout or stderr)
     */
    public boolean hasOutput() {
        return (output != null && !output.trim().isEmpty()) ||
               (errorOutput != null && !errorOutput.trim().isEmpty());
    }
    
    /**
     * Get execution time in seconds
     */
    public double getExecutionTimeSeconds() {
        return executionTimeMs / 1000.0;
    }
    
    /**
     * Get a summary string for logging
     */
    public String getSummary() {
        return String.format("Command: %s, Exit Code: %d, Duration: %.2fs, Success: %s",
            command, exitCode, getExecutionTimeSeconds(), success);
    }
    
    /**
     * Create a copy with timing information
     */
    public CommandResult withTiming(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.executionTimeMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        }
        return this;
    }
    
    /**
     * Create a copy with connection info
     */
    public CommandResult withConnectionInfo(String connectionInfo) {
        this.connectionInfo = connectionInfo;
        return this;
    }
    
    // Legacy compatibility methods for existing code
    public int exitStatus() {
        return exitCode;
    }
    
    public String stdout() {
        return output;
    }
    
    public String stderr() {
        return errorOutput;
    }
}
