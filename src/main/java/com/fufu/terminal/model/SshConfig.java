package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * SSH Configuration
 * 
 * Contains all configuration needed to establish an SSH connection
 * with validation and security features.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SshConfig {
    
    @NotBlank(message = "Host is required")
    private String host;
    
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private int port = 22;
    
    @NotBlank(message = "Username is required")
    private String username;
    
    // Authentication methods
    private String password;
    private String privateKey;
    private String passphrase;
    
    // Connection settings
    private boolean strictHostKeyChecking = false;
    private int connectionTimeoutMs = 30000; // 30 seconds
    private int sessionTimeoutMs = 300000;   // 5 minutes
    private String encoding = "UTF-8";
    
    // Security settings
    private boolean allowPasswordAuth = true;
    private boolean allowPublicKeyAuth = true;
    private boolean allowKeyboardInteractiveAuth = false;
    
    /**
     * Validate the SSH configuration
     */
    public ValidationResult validate() {
        // Check if at least one authentication method is provided
        if ((password == null || password.isEmpty()) && 
            (privateKey == null || privateKey.isEmpty())) {
            return ValidationResult.invalid("authentication", 
                "Either password or private key must be provided")
                .withSuggestion("Provide a password")
                .withSuggestion("Provide a private key");
        }
        
        // Validate host
        if (host != null && (host.contains("..") || host.contains("localhost"))) {
            return ValidationResult.invalid("host", "Invalid host address")
                .withSuggestion("Use a valid remote host address");
        }
        
        // Validate port range
        if (port < 1 || port > 65535) {
            return ValidationResult.invalid("port", "Port must be between 1 and 65535")
                .withSuggestion("Use port 22 for standard SSH");
        }
        
        // Validate timeouts
        if (connectionTimeoutMs < 1000) {
            return ValidationResult.invalid("connectionTimeout", 
                "Connection timeout must be at least 1 second")
                .withSuggestion("Use at least 10 seconds for connection timeout");
        }
        
        if (sessionTimeoutMs < 30000) {
            return ValidationResult.invalid("sessionTimeout", 
                "Session timeout must be at least 30 seconds")
                .withSuggestion("Use at least 5 minutes for session timeout");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Check if password authentication is configured
     */
    public boolean hasPasswordAuth() {
        return password != null && !password.isEmpty() && allowPasswordAuth;
    }
    
    /**
     * Check if public key authentication is configured
     */
    public boolean hasPublicKeyAuth() {
        return privateKey != null && !privateKey.isEmpty() && allowPublicKeyAuth;
    }
    
    /**
     * Get a connection string representation (without sensitive data)
     */
    public String getConnectionString() {
        return String.format("%s@%s:%d", username, host, port);
    }
    
    /**
     * Create a copy with masked sensitive data for logging
     */
    public SshConfig forLogging() {
        return SshConfig.builder()
            .host(host)
            .port(port)
            .username(username)
            .password(password != null ? "****" : null)
            .privateKey(privateKey != null ? "****" : null)
            .passphrase(passphrase != null ? "****" : null)
            .strictHostKeyChecking(strictHostKeyChecking)
            .connectionTimeoutMs(connectionTimeoutMs)
            .sessionTimeoutMs(sessionTimeoutMs)
            .encoding(encoding)
            .allowPasswordAuth(allowPasswordAuth)
            .allowPublicKeyAuth(allowPublicKeyAuth)
            .allowKeyboardInteractiveAuth(allowKeyboardInteractiveAuth)
            .build();
    }
    
    /**
     * Generate a unique key for connection pooling
     */
    public String getPoolKey(String userId) {
        return String.format("%s@%s:%d-%s", username, host, port, userId);
    }
    
    /**
     * Create default SSH config for localhost testing
     */
    public static SshConfig localhost(String username, String password) {
        return SshConfig.builder()
            .host("localhost")
            .port(22)
            .username(username)
            .password(password)
            .strictHostKeyChecking(false)
            .build();
    }
    
    /**
     * Create SSH config with private key authentication
     */
    public static SshConfig withPrivateKey(String host, String username, String privateKey) {
        return SshConfig.builder()
            .host(host)
            .port(22)
            .username(username)
            .privateKey(privateKey)
            .allowPasswordAuth(false)
            .allowPublicKeyAuth(true)
            .strictHostKeyChecking(false)
            .build();
    }
}