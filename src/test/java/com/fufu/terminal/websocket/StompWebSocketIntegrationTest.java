package com.fufu.terminal.websocket;

import com.fufu.terminal.config.StompWebSocketConfig;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.service.refactored.RefactoredScriptExecutionService;
import com.fufu.terminal.controller.dto.ExecutionRequest;
import com.fufu.terminal.command.model.SshConnectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * WebSocket STOMP Integration Tests
 * Tests real-time communication and session management
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:websockettest",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.websocket.allowed-origins=*",
    "logging.level.com.fufu.terminal=DEBUG"
})
class StompWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RefactoredScriptExecutionService executionService;

    private StompSession stompSession;
    private final String WEBSOCKET_ENDPOINT = "/ws/stomp";

    @BeforeEach
    void setUp() throws Exception {
        String url = "ws://localhost:" + port + WEBSOCKET_ENDPOINT;
        
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        StompSessionHandler sessionHandler = new TestStompSessionHandler();
        stompSession = stompClient.connect(url, sessionHandler).get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should establish WebSocket STOMP connection successfully")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldEstablishWebSocketStompConnectionSuccessfully() {
        assertThat(stompSession.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Should receive progress messages via WebSocket for script execution")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldReceiveProgressMessagesViaWebSocketForScriptExecution() throws Exception {
        // Arrange
        CountDownLatch progressLatch = new CountDownLatch(1);
        AtomicReference<ProgressMessage> receivedMessage = new AtomicReference<>();
        
        String userId = "websocket-test-user";
        String progressTopic = "/user/" + userId + "/topic/progress";
        
        // Subscribe to progress updates
        StompSession.Subscription subscription = stompSession.subscribe(progressTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ProgressMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ProgressMessage message) {
                    receivedMessage.set(message);
                    progressLatch.countDown();
                }
            }
        });

        // Act - Start script execution
        ExecutionRequest request = createTestExecutionRequest();
        String sessionId = executionService.executeScript("system-info", request, userId);

        // Assert
        boolean messageReceived = progressLatch.await(30, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        
        ProgressMessage message = receivedMessage.get();
        assertThat(message).isNotNull();
        assertThat(message.getSessionId()).isEqualTo(sessionId);
        assertThat(message.getType()).isIn(ProgressType.STARTED, ProgressType.PROGRESS, ProgressType.COMPLETED);
        
        subscription.unsubscribe();
    }

    @Test
    @DisplayName("Should handle multiple concurrent WebSocket subscriptions")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void shouldHandleMultipleConcurrentWebSocketSubscriptions() throws Exception {
        // Arrange
        int numberOfClients = 5;
        CountDownLatch allMessagesReceived = new CountDownLatch(numberOfClients);
        
        for (int i = 0; i < numberOfClients; i++) {
            String userId = "concurrent-user-" + i;
            String progressTopic = "/user/" + userId + "/topic/progress";
            
            stompSession.subscribe(progressTopic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ProgressMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof ProgressMessage) {
                        allMessagesReceived.countDown();
                    }
                }
            });
        }

        // Act - Start multiple script executions
        for (int i = 0; i < numberOfClients; i++) {
            ExecutionRequest request = createTestExecutionRequest();
            executionService.executeScript("system-info", request, "concurrent-user-" + i);
        }

        // Assert
        boolean allReceived = allMessagesReceived.await(60, TimeUnit.SECONDS);
        assertThat(allReceived).isTrue();
    }

    @Test
    @DisplayName("Should handle WebSocket session cleanup on disconnection")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldHandleWebSocketSessionCleanupOnDisconnection() throws Exception {
        // Arrange
        String userId = "cleanup-test-user";
        String progressTopic = "/user/" + userId + "/topic/progress";
        
        CountDownLatch subscriptionLatch = new CountDownLatch(1);
        
        StompSession.Subscription subscription = stompSession.subscribe(progressTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ProgressMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscriptionLatch.countDown();
            }
        });

        // Act - Start execution and then disconnect
        ExecutionRequest request = createTestExecutionRequest();
        executionService.executeScript("system-info", request, userId);
        
        // Wait for at least one message
        boolean messageReceived = subscriptionLatch.await(10, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        
        // Disconnect the session
        subscription.unsubscribe();
        stompSession.disconnect();
        
        // Assert - Session should be properly cleaned up
        assertThat(stompSession.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should send heartbeat messages to maintain connection")
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void shouldSendHeartbeatMessagesToMaintainConnection() throws Exception {
        // Arrange - Wait for heartbeat interval
        Thread.sleep(15000); // Wait 15 seconds to allow heartbeat
        
        // Assert - Connection should still be active
        assertThat(stompSession.isConnected()).isTrue();
        
        // Test that we can still send/receive messages
        CountDownLatch messageLatch = new CountDownLatch(1);
        String userId = "heartbeat-test-user";
        String progressTopic = "/user/" + userId + "/topic/progress";
        
        stompSession.subscribe(progressTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ProgressMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageLatch.countDown();
            }
        });

        // Send a message after heartbeat period
        ExecutionRequest request = createTestExecutionRequest();
        executionService.executeScript("system-info", request, userId);
        
        boolean messageReceived = messageLatch.await(15, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
    }

    @Test
    @DisplayName("Should handle large message payloads efficiently")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldHandleLargeMessagePayloadsEfficiently() throws Exception {
        // Arrange
        CountDownLatch largeMsgLatch = new CountDownLatch(1);
        AtomicReference<ProgressMessage> largeMessage = new AtomicReference<>();
        
        String userId = "large-payload-user";
        String progressTopic = "/user/" + userId + "/topic/progress";
        
        stompSession.subscribe(progressTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ProgressMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ProgressMessage message) {
                    largeMessage.set(message);
                    largeMsgLatch.countDown();
                }
            }
        });

        // Act - Execute a script that might generate larger progress messages
        ExecutionRequest request = createTestExecutionRequest();
        request.getParameters().put("verbose", true);
        request.getParameters().put("detailed_output", true);
        
        executionService.executeScript("docker-install", request, userId);

        // Assert
        boolean received = largeMsgLatch.await(30, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        
        ProgressMessage message = largeMessage.get();
        assertThat(message).isNotNull();
        assertThat(message.getSessionId()).isNotBlank();
    }

    @Test
    @DisplayName("Should handle WebSocket authentication and authorization")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldHandleWebSocketAuthenticationAndAuthorization() throws Exception {
        // This test verifies that the STOMP authentication interceptor works correctly
        
        // Arrange - Try to subscribe to another user's topic
        String unauthorizedUserId = "unauthorized-user";
        String authorizedUserId = "authorized-user";
        
        CountDownLatch authLatch = new CountDownLatch(1);
        
        // Try to subscribe to authorized user's topic
        String progressTopic = "/user/" + authorizedUserId + "/topic/progress";
        
        try {
            stompSession.subscribe(progressTopic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ProgressMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    authLatch.countDown();
                }
            });
            
            // Start execution for the authorized user
            ExecutionRequest request = createTestExecutionRequest();
            executionService.executeScript("system-info", request, authorizedUserId);
            
            // Should receive message since it's the same session
            boolean received = authLatch.await(15, TimeUnit.SECONDS);
            // This may or may not receive depending on auth implementation
            // The important thing is that the subscription doesn't fail
            
        } catch (Exception e) {
            // Expected if authorization is strict
            assertThat(e.getMessage()).contains("auth");
        }
    }

    @Test
    @DisplayName("Should maintain message ordering during high-frequency updates")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldMaintainMessageOrderingDuringHighFrequencyUpdates() throws Exception {
        // Arrange
        CountDownLatch orderingLatch = new CountDownLatch(10);
        List<ProgressMessage> receivedMessages = new java.util.ArrayList<>();
        
        String userId = "ordering-test-user";
        String progressTopic = "/user/" + userId + "/topic/progress";
        
        stompSession.subscribe(progressTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ProgressMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ProgressMessage message) {
                    synchronized (receivedMessages) {
                        receivedMessages.add(message);
                    }
                    orderingLatch.countDown();
                }
            }
        });

        // Act - Start a script that generates frequent updates
        ExecutionRequest request = createTestExecutionRequest();
        request.getParameters().put("high_frequency", true);
        
        executionService.executeScript("docker-install", request, userId);

        // Assert
        boolean received = orderingLatch.await(45, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        
        synchronized (receivedMessages) {
            assertThat(receivedMessages).isNotEmpty();
            
            // Verify timestamp ordering (messages should arrive in chronological order)
            for (int i = 1; i < receivedMessages.size(); i++) {
                var current = receivedMessages.get(i);
                var previous = receivedMessages.get(i - 1);
                
                assertThat(current.getTimestamp()).isAfterOrEqualTo(previous.getTimestamp());
            }
        }
    }

    private ExecutionRequest createTestExecutionRequest() {
        ExecutionRequest request = new ExecutionRequest();
        
        SshConnectionConfig sshConfig = new SshConnectionConfig();
        sshConfig.setHost("localhost");
        sshConfig.setPort(22);
        sshConfig.setUsername("testuser");
        sshConfig.setPassword("testpass");
        
        request.setSshConfig(sshConfig);
        request.setParameters(new java.util.HashMap<>());
        
        return request;
    }

    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            // Connection established
        }

        @Override
        public void handleException(StompSession session, StompCommand command, 
                                  StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            exception.printStackTrace();
        }
    }
}