package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.dto.sillytavern.DeploymentProgressDto;
import com.fufu.terminal.dto.sillytavern.DeploymentRequestDto;
import com.fufu.terminal.dto.sillytavern.SystemInfoDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Performance and concurrency tests for SillyTavern management.
 * Tests system behavior under load and concurrent user scenarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("SillyTavern Performance and Concurrency Tests")
class SillyTavernPerformanceTest {

    @Autowired
    private SillyTavernService sillyTavernService;
    
    @Autowired
    private StompSessionManager sessionManager;
    
    private WebSocketStompClient stompClient;
    private final String WEBSOCKET_URI = "ws://localhost:8080/ws/terminal";
    private final String TEST_HOST = "test-host";
    private final String TEST_USER = "test-user";
    private final String TEST_PASSWORD = "test-password";
    
    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }
    
    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    @DisplayName("Should handle 20 concurrent users performing different operations")
    @Timeout(60) // 60 seconds timeout
    void testConcurrentUserOperations() throws Exception {
        final int CONCURRENT_USERS = 20;
        final CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_USERS);
        final ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        
        try {
            // Create concurrent user sessions
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int userId = i;
                
                executor.submit(() -> {
                    try {
                        String sessionId = "perf-test-session-" + userId;
                        simulateUserSession(sessionId, userId, results);
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Wait for all operations to complete
            assertTrue(completionLatch.await(45, TimeUnit.SECONDS), 
                    "Not all concurrent operations completed within timeout");
            
            // Verify no exceptions occurred
            if (!exceptions.isEmpty()) {
                Exception firstException = exceptions.poll();
                fail("Concurrent operations failed: " + firstException.getMessage(), firstException);
            }
            
            // Verify all operations completed
            assertEquals(CONCURRENT_USERS, results.size(), 
                    "Not all user sessions completed successfully");
            
            System.out.println("Concurrent users test completed successfully:");
            results.forEach(System.out::println);
            
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Should handle rapid sequential operations from single user")
    @Timeout(30)
    void testRapidSequentialOperations() throws Exception {
        String sessionId = "rapid-ops-session";
        SshConnection mockConnection = createMockSshConnection();
        
        // Mock session manager to return our connection
        StompSessionManager spySessionManager = spy(sessionManager);
        when(spySessionManager.getConnection(sessionId)).thenReturn(mockConnection);
        
        // Mock service responses for rapid operations
        SillyTavernService spyService = spy(sillyTavernService);
        
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        
        when(spyService.getContainerStatus(mockConnection)).thenReturn(runningStatus);
        when(spyService.isContainerRunning(mockConnection)).thenReturn(true);
        
        // Execute rapid sequential operations
        final int RAPID_OPERATIONS = 50;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < RAPID_OPERATIONS; i++) {
            // Alternate between different operations to simulate real usage
            switch (i % 4) {
                case 0:
                    ContainerStatusDto status = spyService.getContainerStatus(mockConnection);
                    assertNotNull(status);
                    break;
                case 1:
                    boolean isRunning = spyService.isContainerRunning(mockConnection);
                    assertTrue(isRunning);
                    break;
                case 2:
                    SystemInfoDto systemInfo = spyService.validateSystemRequirements(mockConnection);
                    assertNotNull(systemInfo);
                    break;
                case 3:
                    // Simulate lightweight operation
                    Thread.sleep(1); // Minimal delay
                    break;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double operationsPerSecond = (RAPID_OPERATIONS * 1000.0) / totalTime;
        
        System.out.println(String.format("Rapid operations completed: %d ops in %d ms (%.2f ops/sec)", 
                RAPID_OPERATIONS, totalTime, operationsPerSecond));
        
        // Performance assertion: should handle at least 10 operations per second
        assertTrue(operationsPerSecond >= 10.0, 
                String.format("Performance too slow: %.2f ops/sec (expected >= 10)", operationsPerSecond));
    }

    @Test
    @DisplayName("Should handle concurrent deployment operations gracefully")
    @Timeout(60)
    void testConcurrentDeployments() throws Exception {
        final int CONCURRENT_DEPLOYMENTS = 5;
        final CountDownLatch deploymentLatch = new CountDownLatch(CONCURRENT_DEPLOYMENTS);
        final ConcurrentLinkedQueue<String> deploymentResults = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Exception> deploymentErrors = new ConcurrentLinkedQueue<>();
        
        ExecutorService deploymentExecutor = Executors.newFixedThreadPool(CONCURRENT_DEPLOYMENTS);
        
        try {
            for (int i = 0; i < CONCURRENT_DEPLOYMENTS; i++) {
                final int deploymentId = i;
                
                deploymentExecutor.submit(() -> {
                    try {
                        String containerName = "sillytavern-deploy-" + deploymentId;
                        int port = 8000 + deploymentId;
                        
                        DeploymentRequestDto request = DeploymentRequestDto.builder()
                                .containerName(containerName)
                                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                                .port(port)
                                .dataPath("/opt/sillytavern-" + deploymentId)
                                .build();
                        
                        SshConnection mockConnection = createMockSshConnection();
                        
                        // Mock deployment progress
                        CompletableFuture<Void> deploymentFuture = simulateDeployment(
                                mockConnection, request, deploymentId);
                        
                        deploymentFuture.get(30, TimeUnit.SECONDS);
                        deploymentResults.add("Deployment " + deploymentId + " completed");
                        
                    } catch (Exception e) {
                        deploymentErrors.add(e);
                    } finally {
                        deploymentLatch.countDown();
                    }
                });
            }
            
            assertTrue(deploymentLatch.await(45, TimeUnit.SECONDS), 
                    "Not all deployments completed within timeout");
            
            // Check for deployment errors
            if (!deploymentErrors.isEmpty()) {
                Exception firstError = deploymentErrors.poll();
                fail("Concurrent deployment failed: " + firstError.getMessage(), firstError);
            }
            
            assertEquals(CONCURRENT_DEPLOYMENTS, deploymentResults.size(), 
                    "Not all deployments completed successfully");
            
            System.out.println("Concurrent deployments completed:");
            deploymentResults.forEach(System.out::println);
            
        } finally {
            deploymentExecutor.shutdown();
            if (!deploymentExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                deploymentExecutor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Should handle memory usage efficiently during extended operations")
    @Timeout(45)
    void testMemoryUsageEfficiency() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Record initial memory usage
        runtime.gc(); // Force garbage collection
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        final int EXTENDED_OPERATIONS = 1000;
        String sessionId = "memory-test-session";
        SshConnection mockConnection = createMockSshConnection();
        
        // Perform extended operations
        for (int i = 0; i < EXTENDED_OPERATIONS; i++) {
            // Simulate various operations that might consume memory
            ContainerStatusDto status = new ContainerStatusDto();
            status.setExists(true);
            status.setRunning(true);
            status.setContainerName("test-container-" + i);
            status.setMemoryUsageMB((long) (Math.random() * 1000));
            status.setCpuUsagePercent(Math.random() * 100);
            
            // Create and discard objects to test memory management
            DeploymentProgressDto progress = DeploymentProgressDto.success(
                    "test-stage", i % 100, "Operation " + i);
            
            // Periodically force garbage collection to test cleanup
            if (i % 100 == 0) {
                runtime.gc();
                Thread.sleep(1); // Give GC time to work
            }
        }
        
        // Final garbage collection and memory check
        runtime.gc();
        Thread.sleep(100); // Allow GC to complete
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        System.out.println(String.format("Memory usage: Initial=%d KB, Final=%d KB, Increase=%d KB", 
                initialMemory / 1024, finalMemory / 1024, memoryIncrease / 1024));
        
        // Memory should not increase by more than 50MB for 1000 operations
        long maxAllowedIncrease = 50 * 1024 * 1024; // 50MB in bytes
        assertTrue(memoryIncrease < maxAllowedIncrease, 
                String.format("Memory usage too high: %d KB increase (max allowed: %d KB)", 
                        memoryIncrease / 1024, maxAllowedIncrease / 1024));
    }

    @Test
    @DisplayName("Should handle WebSocket connection under load")
    @Timeout(30)
    void testWebSocketConnectionLoad() throws Exception {
        final int MESSAGE_COUNT = 100;
        final CountDownLatch messageLatch = new CountDownLatch(MESSAGE_COUNT);
        final ConcurrentLinkedQueue<String> receivedMessages = new ConcurrentLinkedQueue<>();
        
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("host", TEST_HOST);
        connectHeaders.add("user", TEST_USER);
        connectHeaders.add("password", TEST_PASSWORD);
        
        StompSession session = stompClient.connect(WEBSOCKET_URI, 
                new WebSocketStompHeaders(connectHeaders), new TestStompSessionHandler()).get(5, TimeUnit.SECONDS);
        
        try {
            // Subscribe to test queue
            session.subscribe("/user/queue/sillytavern/status", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    receivedMessages.add(payload.toString());
                    messageLatch.countDown();
                }
            });
            
            // Send rapid messages
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                session.send("/app/sillytavern/status", "test-message-" + i);
                
                // Small delay to avoid overwhelming the server
                if (i % 10 == 0) {
                    Thread.sleep(1);
                }
            }
            
            // Wait for all messages to be processed
            assertTrue(messageLatch.await(20, TimeUnit.SECONDS), 
                    "Not all WebSocket messages were processed within timeout");
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double messagesPerSecond = (MESSAGE_COUNT * 1000.0) / totalTime;
            
            System.out.println(String.format("WebSocket load test: %d messages in %d ms (%.2f msg/sec)", 
                    MESSAGE_COUNT, totalTime, messagesPerSecond));
            
            // Performance assertion: should handle at least 5 messages per second
            assertTrue(messagesPerSecond >= 5.0, 
                    String.format("WebSocket performance too slow: %.2f msg/sec (expected >= 5)", messagesPerSecond));
            
        } finally {
            session.disconnect();
        }
    }

    @Test
    @DisplayName("Should maintain stable performance under sustained load")
    @Timeout(120) // 2 minutes timeout
    void testSustainedLoadStability() throws Exception {
        final int DURATION_SECONDS = 60;
        final int OPERATIONS_PER_SECOND = 5;
        final AtomicInteger completedOperations = new AtomicInteger(0);
        final AtomicInteger failedOperations = new AtomicInteger(0);
        final ConcurrentLinkedQueue<Long> operationTimes = new ConcurrentLinkedQueue<>();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Schedule sustained operations
            ScheduledFuture<?> sustainedLoad = scheduler.scheduleAtFixedRate(() -> {
                try {
                    long opStartTime = System.currentTimeMillis();
                    
                    // Perform lightweight operation
                    SshConnection mockConnection = createMockSshConnection();
                    ContainerStatusDto status = sillyTavernService.getContainerStatus(mockConnection);
                    assertNotNull(status);
                    
                    long opEndTime = System.currentTimeMillis();
                    operationTimes.add(opEndTime - opStartTime);
                    completedOperations.incrementAndGet();
                    
                } catch (Exception e) {
                    failedOperations.incrementAndGet();
                }
            }, 0, 1000 / OPERATIONS_PER_SECOND, TimeUnit.MILLISECONDS);
            
            // Let it run for specified duration
            Thread.sleep(DURATION_SECONDS * 1000);
            sustainedLoad.cancel(false);
            
            // Wait for any remaining operations to complete
            Thread.sleep(1000);
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            
            int expectedOperations = DURATION_SECONDS * OPERATIONS_PER_SECOND;
            int actualOperations = completedOperations.get();
            int failures = failedOperations.get();
            
            // Calculate performance metrics
            double successRate = (double) actualOperations / (actualOperations + failures) * 100;
            double averageOperationTime = operationTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            System.out.println(String.format("Sustained load test results:"));
            System.out.println(String.format("  Duration: %d seconds", totalTime / 1000));
            System.out.println(String.format("  Expected operations: %d", expectedOperations));
            System.out.println(String.format("  Completed operations: %d", actualOperations));
            System.out.println(String.format("  Failed operations: %d", failures));
            System.out.println(String.format("  Success rate: %.2f%%", successRate));
            System.out.println(String.format("  Average operation time: %.2f ms", averageOperationTime));
            
            // Performance assertions
            assertTrue(successRate >= 95.0, 
                    String.format("Success rate too low: %.2f%% (expected >= 95%%)", successRate));
            
            assertTrue(actualOperations >= expectedOperations * 0.8, 
                    String.format("Too few operations completed: %d (expected >= %d)", 
                            actualOperations, (int) (expectedOperations * 0.8)));
            
            assertTrue(averageOperationTime <= 1000.0, 
                    String.format("Operations too slow: %.2f ms (expected <= 1000ms)", averageOperationTime));
            
        } finally {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
    }

    // Helper methods
    
    private void simulateUserSession(String sessionId, int userId, ConcurrentLinkedQueue<String> results) 
            throws Exception {
        
        SshConnection mockConnection = createMockSshConnection();
        
        // Simulate different user behaviors
        switch (userId % 4) {
            case 0: // Status checker
                for (int i = 0; i < 10; i++) {
                    ContainerStatusDto status = sillyTavernService.getContainerStatus(mockConnection);
                    assertNotNull(status);
                    Thread.sleep(100);
                }
                results.add("User " + userId + " (status checker) completed");
                break;
                
            case 1: // System validator
                for (int i = 0; i < 5; i++) {
                    SystemInfoDto systemInfo = sillyTavernService.validateSystemRequirements(mockConnection);
                    assertNotNull(systemInfo);
                    Thread.sleep(200);
                }
                results.add("User " + userId + " (system validator) completed");
                break;
                
            case 2: // Container manager
                boolean isRunning = sillyTavernService.isContainerRunning(mockConnection);
                ContainerStatusDto status = sillyTavernService.getContainerStatus(mockConnection);
                assertNotNull(status);
                results.add("User " + userId + " (container manager) completed");
                break;
                
            case 3: // Mixed operations
                SystemInfoDto systemInfo = sillyTavernService.validateSystemRequirements(mockConnection);
                ContainerStatusDto containerStatus = sillyTavernService.getContainerStatus(mockConnection);
                boolean running = sillyTavernService.isContainerRunning(mockConnection);
                assertNotNull(systemInfo);
                assertNotNull(containerStatus);
                results.add("User " + userId + " (mixed operations) completed");
                break;
        }
    }
    
    private CompletableFuture<Void> simulateDeployment(SshConnection connection, 
                                                      DeploymentRequestDto request, 
                                                      int deploymentId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Simulate deployment stages with realistic delays
                Thread.sleep(100); // System validation
                Thread.sleep(500); // Image pull
                Thread.sleep(200); // Container creation
                Thread.sleep(100); // Verification
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Deployment interrupted", e);
            }
        });
    }
    
    private SshConnection createMockSshConnection() throws JSchException {
        SshConnection mockConnection = mock(SshConnection.class);
        Session mockSession = mock(Session.class);
        
        when(mockConnection.getJschSession()).thenReturn(mockSession);
        when(mockSession.isConnected()).thenReturn(true);
        
        return mockConnection;
    }
    
    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("STOMP session connected: " + session.getSessionId());
        }
        
        @Override
        public void handleException(StompSession session, StompCommand command, 
                                  StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("STOMP session exception: " + exception.getMessage());
        }
    }
    
    private static class WebSocketStompHeaders extends StompHeaders {
        public WebSocketStompHeaders(StompHeaders headers) {
            super();
            this.putAll(headers);
        }
    }
}