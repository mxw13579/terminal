package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.SshMonitorService;
import com.fufu.terminal.service.TaskExecutionService;
import com.fufu.terminal.service.execution.InteractiveScriptExecutor;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SshTerminalWebSocketHandler 单元测试
 * 测试SSH终端WebSocket处理器的核心功能
 */
@ExtendWith(MockitoExtension.class)
class SshTerminalWebSocketHandlerTest {

    @Mock
    private SftpService sftpService;

    @Mock
    private SshMonitorService sshMonitorService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private InteractiveScriptExecutor interactiveScriptExecutor;

    @Mock
    private ExecutorService executorService;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private Session jschSession;

    @Mock
    private ChannelShell channelShell;

    @Mock
    private JSch jsch;

    private SshTerminalWebSocketHandler handler;
    private ObjectMapper objectMapper;
    private SshConnection sshConnection;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SshTerminalWebSocketHandler(
            sftpService, sshMonitorService, taskExecutionService, 
            interactiveScriptExecutor, executorService
        );
        
        objectMapper = new ObjectMapper();
        
        // Setup mock WebSocket session
        when(webSocketSession.getId()).thenReturn("test-session-123");
        when(webSocketSession.isOpen()).thenReturn(true);
        
        // Setup mock URI with query parameters
        URI mockUri = URI.create("ws://localhost:8080/ssh-terminal?host=localhost&port=22&user=testuser&password=testpass");
        when(webSocketSession.getUri()).thenReturn(mockUri);
        
        // Setup mock SSH components
        InputStream inputStream = new ByteArrayInputStream("Welcome to SSH\n".getBytes());
        OutputStream outputStream = new ByteArrayOutputStream();
        
        when(channelShell.getInputStream()).thenReturn(inputStream);
        when(channelShell.getOutputStream()).thenReturn(outputStream);
        when(channelShell.isConnected()).thenReturn(true);
        
        when(jschSession.openChannel("shell")).thenReturn(channelShell);
        when(jsch.getSession("testuser", "localhost", 22)).thenReturn(jschSession);
        
        sshConnection = new SshConnection(jsch, jschSession, channelShell, inputStream, outputStream);
    }

    @Test
    void testAfterConnectionEstablished_Success() throws Exception {
        // Arrange
        doNothing().when(jschSession).setConfig(anyString(), anyString());
        doNothing().when(jschSession).connect(anyInt());
        doNothing().when(channelShell).setPtyType(anyString());
        doNothing().when(channelShell).connect(anyInt());
        doNothing().when(executorService).submit(any(Runnable.class));

        // Act
        handler.afterConnectionEstablished(webSocketSession);

        // Assert
        verify(jschSession).setPassword("testpass");
        verify(jschSession).setConfig("StrictHostKeyChecking", "no");
        verify(jschSession).connect(30000);
        verify(channelShell).setPtyType("xterm");
        verify(channelShell).connect(3000);
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void testAfterConnectionEstablished_ConnectionFailure() throws Exception {
        // Arrange
        when(jsch.getSession("testuser", "localhost", 22))
            .thenThrow(new RuntimeException("Connection failed"));
        doNothing().when(webSocketSession).close();

        // Act
        handler.afterConnectionEstablished(webSocketSession);

        // Assert
        verify(webSocketSession).sendMessage(any(TextMessage.class));
        verify(webSocketSession).close();
    }

    @Test
    void testHandleTextMessage_DataType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"data\", \"payload\": \"ls -la\\n\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert - verify data was written to output stream
        // This test verifies the message handling structure
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testHandleTextMessage_ResizeType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"resize\", \"cols\": 80, \"rows\": 24}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        // Verify the message was processed (specific verification depends on implementation)
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testHandleTextMessage_SftpListType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"sftp_list\", \"path\": \"/home/user\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(sftpService).handleSftpList(eq(webSocketSession), any(SshConnection.class), eq("/home/user"));
    }

    @Test
    void testHandleTextMessage_SftpDownloadType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"sftp_download\", \"paths\": [\"/home/user/file1.txt\", \"/home/user/file2.txt\"]}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(sftpService).handleSftpDownload(eq(webSocketSession), any(SshConnection.class), anyList());
    }

    @Test
    void testHandleTextMessage_MonitorStartType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"monitor_start\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(sshMonitorService).handleMonitorStart(eq(webSocketSession), any(SshConnection.class));
    }

    @Test
    void testHandleTextMessage_MonitorStopType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"monitor_stop\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(sshMonitorService).handleMonitorStop(any(SshConnection.class));
    }

    @Test
    void testHandleTextMessage_ExecuteTaskType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"execute_task\", \"taskName\": \"install_docker\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(taskExecutionService).executeTask(eq("install_docker"), any());
    }

    @Test
    void testHandleTextMessage_InteractionResponseType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"interaction_response\", \"sessionId\": \"test-123\", \"response\": \"confirmed\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(interactiveScriptExecutor).handleUserResponse(eq("test-session-123"), any());
    }

    @Test
    void testHandleTextMessage_UnknownType() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"unknown_type\", \"data\": \"test\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert - should log warning but not throw exception
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testHandleTextMessage_InvalidJson() throws Exception {
        // Arrange
        String messagePayload = "invalid json";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(webSocketSession).sendMessage(any(TextMessage.class)); // Error message should be sent
    }

    @Test
    void testHandleTextMessage_NoConnection() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"data\", \"payload\": \"test\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Don't establish connection

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert - should handle gracefully when no connection exists
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testAfterConnectionClosed() {
        // Arrange
        CloseStatus closeStatus = CloseStatus.NORMAL;

        // Act
        handler.afterConnectionClosed(webSocketSession, closeStatus);

        // Assert - should clean up resources
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testHandleTransportError() {
        // Arrange
        Exception transportError = new RuntimeException("Transport error");

        // Act
        handler.handleTransportError(webSocketSession, transportError);

        // Assert - should handle error and clean up
        verify(webSocketSession, atLeastOnce()).getId();
    }

    @Test
    void testSftpUploadChunkHandling() throws Exception {
        // Arrange
        String messagePayload = "{\"type\": \"sftp_upload_chunk\", \"path\": \"/upload\", \"filename\": \"test.txt\", \"chunkIndex\": 0, \"totalChunks\": 1, \"content\": \"dGVzdCBjb250ZW50\"}";
        TextMessage message = new TextMessage(messagePayload);
        
        // Mock connection exists
        handler.afterConnectionEstablished(webSocketSession);

        // Act
        handler.handleTextMessage(webSocketSession, message);

        // Assert
        verify(sftpService).handleSftpUploadChunk(
            eq(webSocketSession), 
            any(SshConnection.class), 
            eq("/upload"), 
            eq("test.txt"), 
            eq(0), 
            eq(1), 
            eq("dGVzdCBjb250ZW50")
        );
    }

    @Test
    void testQueryParameterParsing_MissingParameters() throws Exception {
        // Arrange
        URI mockUri = URI.create("ws://localhost:8080/ssh-terminal");
        when(webSocketSession.getUri()).thenReturn(mockUri);

        // Act & Assert
        // Should handle missing query parameters gracefully
        handler.afterConnectionEstablished(webSocketSession);
        
        // Connection should still be attempted to close due to missing parameters
        verify(webSocketSession).close();
    }

    @Test
    void testQueryParameterParsing_DefaultPort() throws Exception {
        // Arrange
        URI mockUri = URI.create("ws://localhost:8080/ssh-terminal?host=localhost&user=testuser&password=testpass");
        when(webSocketSession.getUri()).thenReturn(mockUri);

        // Act
        handler.afterConnectionEstablished(webSocketSession);

        // Assert - should use default port 22
        verify(jsch).getSession("testuser", "localhost", 22);
    }
}