package com.fufu.terminal.controller;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.model.stomp.TerminalDataMessage;
import com.fufu.terminal.model.stomp.TerminalResizeMessage;
import com.fufu.terminal.service.StompSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStream;

/**
 * STOMP controller for SSH terminal operations.
 * Handles terminal data input and resize operations.
 * 
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SshTerminalStompController {

    private final StompSessionManager sessionManager;

    /**
     * Handle terminal data input from the client.
     * This replaces the "data" message handler from the original WebSocket implementation.
     */
    @MessageMapping("/terminal/data")
    public void handleTerminalData(
            @Valid TerminalDataMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling terminal data for session: {}", sessionId);
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Send the data to the SSH terminal
            OutputStream outputStream = connection.getOutputStream();
            if (outputStream != null) {
                outputStream.write(message.getPayload().getBytes());
                outputStream.flush();
                log.debug("Sent {} bytes to terminal for session: {}", 
                         message.getPayload().length(), sessionId);
            } else {
                log.error("Output stream is null for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "Terminal output stream unavailable");
            }

        } catch (IOException e) {
            log.error("Error sending data to terminal for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to send data to terminal: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error handling terminal data for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Terminal error: " + e.getMessage());
        }
    }

    /**
     * Handle terminal resize operations.
     * This replaces the "resize" message handler from the original WebSocket implementation.
     */
    @MessageMapping("/terminal/resize")
    public void handleTerminalResize(
            @Valid TerminalResizeMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling terminal resize for session: {} ({}x{})", sessionId, message.getCols(), message.getRows());
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Update the PTY size
            if (connection.getChannelShell() != null) {
                connection.getChannelShell().setPtySize(
                    message.getCols(), 
                    message.getRows(), 
                    message.getCols() * 8,  // pixel width (approximate)
                    message.getRows() * 8   // pixel height (approximate)
                );
                
                log.debug("Updated terminal size for session: {} to {}x{}", 
                         sessionId, message.getCols(), message.getRows());
            } else {
                log.error("Channel shell is null for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "Terminal channel unavailable");
            }

        } catch (Exception e) {
            log.error("Error resizing terminal for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to resize terminal: " + e.getMessage());
        }
    }

    /**
     * Handle terminal connection establishment.
     * This is called automatically when a STOMP session connects and will start output forwarding.
     */
    @MessageMapping("/terminal/connect")
    public void handleTerminalConnect(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Starting terminal session for: {}", sessionId);
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.error("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Start the terminal output forwarding
            sessionManager.startTerminalOutputForwarder(sessionId);
            
            // Send confirmation message
            sessionManager.sendSuccessMessage(sessionId, "terminal_connected", 
                "Terminal session established successfully");
            
            log.info("Terminal session started successfully for: {}", sessionId);

        } catch (Exception e) {
            log.error("Error starting terminal session for {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to start terminal session: " + e.getMessage());
        }
    }

    /**
     * Handle terminal disconnection cleanup.
     */
    @MessageMapping("/terminal/disconnect")
    public void handleTerminalDisconnect(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Disconnecting terminal session: {}", sessionId);
        
        try {
            // Stop output forwarding
            sessionManager.stopTerminalOutputForwarder(sessionId);
            
            // Cleanup session resources
            sessionManager.cleanupSession(sessionId);
            
            log.info("Terminal session disconnected: {}", sessionId);

        } catch (Exception e) {
            log.error("Error disconnecting terminal session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}