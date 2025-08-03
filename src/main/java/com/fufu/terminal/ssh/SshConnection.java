package com.fufu.terminal.ssh;

import com.fufu.terminal.model.CommandResult;
import java.time.Duration;

/**
 * SSH Connection Interface
 * 
 * Defines the contract for SSH connections with command execution capabilities.
 */
public interface SshConnection extends AutoCloseable {
    
    /**
     * Execute a command with default timeout
     * @param command Command to execute
     * @return Command execution result
     */
    default CommandResult executeCommand(String command) {
        return executeCommand(command, Duration.ofMinutes(5));
    }
    
    /**
     * Execute a command with specified timeout
     * @param command Command to execute
     * @param timeout Maximum execution time
     * @return Command execution result
     */
    CommandResult executeCommand(String command, Duration timeout);
    
    /**
     * Check if the connection is established
     * @return true if connected
     */
    boolean isConnected();
    
    /**
     * Check if the connection is valid and usable
     * @return true if valid
     */
    boolean isValid();
    
    /**
     * Get connection information for logging
     * @return Connection info string
     */
    String getConnectionInfo();
    
    /**
     * Close the connection and release resources
     */
    @Override
    void close();
}