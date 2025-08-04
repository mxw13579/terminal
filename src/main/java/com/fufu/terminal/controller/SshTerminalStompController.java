package com.fufu.terminal.controller;

import com.fufu.terminal.dto.TerminalDataDto;
import com.fufu.terminal.dto.TerminalResizeDto;
import com.fufu.terminal.model.SshConnection;
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
     */
    @MessageMapping("/terminal/data")
    public void handleTerminalData(
            @Valid TerminalDataDto message,
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
                outputStream.write(message.getData().getBytes());
                outputStream.flush();
                log.debug("Sent {} bytes to terminal for session: {}", 
                         message.getData().length(), sessionId);
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
     */
    @MessageMapping("/terminal/resize")
    public void handleTerminalResize(
            @Valid TerminalResizeDto message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling terminal resize for session: {}: {}x{}", 
                 sessionId, message.getCols(), message.getRows());
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Resize the terminal PTY
            if (connection.getChannelShell() != null) {
                connection.getChannelShell().setPtySize(
                    message.getCols(), 
                    message.getRows(), 
                    message.getCols() * 8, 
                    message.getRows() * 8
                );
                log.debug("Terminal resized for session {}: {}x{}", 
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
     * Start terminal output forwarding (called after SSH connection is established)
     */
    @MessageMapping("/terminal/start-forwarding")
    public void startOutputForwarding(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Starting output forwarding for session: {}", sessionId);
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection != null) {
                sessionManager.startOutputForwarding(sessionId, connection);
                log.info("Output forwarding started for session: {}", sessionId);
            } else {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
            }
        } catch (Exception e) {
            log.error("Error starting output forwarding for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to start output forwarding: " + e.getMessage());
        }
    }
}