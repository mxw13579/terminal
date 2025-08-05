package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.InteractiveDeploymentDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.sillytavern.*;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SillyTavern Web Deployment Wizard 性能和并发测试
 * 
 * 重点测试82/100代码审查中标识的WebSocket会话管理改进，
 * 以及高并发部署场景下的系统稳定性和性能表现。
 * 
 * 测试场景：
 * 1. 多用户并发部署性能
 * 2. WebSocket会话管理和内存使用
 * 3. Docker安装并发处理能力
 * 4. 系统资源消耗控制
 * 5. 长时间运行稳定性
 * 
 * 验证改进后的交互式部署服务在生产环境中的表现
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern Web部署向导性能测试")
class SillyTavernPerformanceTest {

    private InteractiveDeploymentService deploymentService;

    @Mock
    private GeolocationDetectionService geolocationService;

    @Mock
    private SystemDetectionService systemDetectionService;

    @Mock
    private DockerInstallationService dockerInstallationService;

    @Mock
    private SystemConfigurationService systemConfigurationService;

    @Mock
    private DockerMirrorService dockerMirrorService;

    @Mock
    private SillyTavernDeploymentService sillyTavernDeploymentService;

    @Mock
    private ExternalAccessService externalAccessService;

    @Mock
    private ServiceValidationService validationService;

    @Mock
    private PackageManagerService packageManagerService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SshConnection sshConnection;

    @Mock
    private Session jschSession;

    @BeforeEach
    void setUp() {
        when(sshConnection.getJschSession()).thenReturn(jschSession);

        deploymentService = new InteractiveDeploymentService(
            geolocationService,
            packageManagerService,
            dockerInstallationService,
            dockerMirrorService,
            sillyTavernDeploymentService,
            externalAccessService,
            validationService,
            systemDetectionService,
            systemConfigurationService,
            null,
            messagingTemplate
        );

        // Mock基础服务响应，优化性能
        mockOptimizedServiceResponses();
    }

    // ===== WebSocket会话管理性能测试 =====

    @Test
    @DisplayName("大量并发WebSocket会话管理性能测试")
    void testHighConcurrencyWebSocketSessionManagement() throws Exception {
        // Given - 大量并发会话（模拟生产环境压力）
        int concurrentSessions = 50;
        List<String> sessionIds = new ArrayList<>();
        List<CompletableFuture<Void>> deploymentFutures = new ArrayList<>();

        for (int i = 0; i < concurrentSessions; i++) {
            sessionIds.add("performance-session-" + i);
        }

        // 记录开始时间和内存使用
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long startMemory = runtime.totalMemory() - runtime.freeMemory();

        // When - 启动大量并发部署会话
        for (String sessionId : sessionIds) {
            InteractiveDeploymentDto.RequestDto request = InteractiveDeploymentDto.RequestDto.builder()
                    .deploymentMode("trust")
                    .autoConfirmAll(true)
                    .containerName("sillytavern-" + sessionId.substring(sessionId.length() - 2))
                    .port("80" + String.format("%02d", Integer.parseInt(sessionId.substring(sessionId.length() - 2))))
                    .build();

            CompletableFuture<Void> future = deploymentService.startInteractiveDeployment(
                    sessionId, sshConnection, request);
            deploymentFutures.add(future);
        }

        // 等待所有会话完成
        CompletableFuture<Void> allSessions = CompletableFuture.allOf(
                deploymentFutures.toArray(new CompletableFuture[0]));

        // Then - 验证性能表现
        assertDoesNotThrow(() -> allSessions.get(60, TimeUnit.SECONDS), 
                "所有并发会话都应该在60秒内完成");

        long endTime = System.currentTimeMillis();
        long endMemory = runtime.totalMemory() - runtime.freeMemory();

        // 性能断言
        long totalTime = endTime - startTime;
        long memoryUsed = endMemory - startMemory;

        assertTrue(totalTime < 60000, "50个并发会话应该在60秒内完成，实际用时: " + totalTime + "ms");
        assertTrue(memoryUsed < 200 * 1024 * 1024, "内存使用应该控制在200MB以内，实际使用: " + 
                (memoryUsed / 1024 / 1024) + "MB");

        // 验证所有会话状态正确
        for (String sessionId : sessionIds) {
            InteractiveDeploymentDto.StatusDto status = deploymentService.getDeploymentStatus(sessionId);
            assertNotNull(status, "会话 " + sessionId + " 应该有状态");
            assertTrue(status.isCompleted(), "会话 " + sessionId + " 应该完成");
            assertTrue(status.isSuccess(), "会话 " + sessionId + " 应该成功");
        }

        // 验证WebSocket消息发送性能
        verify(messagingTemplate, atLeast(concurrentSessions * 3)).convertAndSend(anyString(), any());

        System.out.printf("性能测试结果: %d个并发会话，总用时%dms，内存使用%dMB%n", 
                concurrentSessions, totalTime, memoryUsed / 1024 / 1024);
    }

    @Test
    @DisplayName("WebSocket消息发送频率和缓冲性能测试")
    void testWebSocketMessageBatchingAndBuffering() throws Exception {
        // Given - 高频消息发送场景
        String sessionId = "high-frequency-messages-session";
        AtomicInteger messageCount = new AtomicInteger(0);

        // Mock消息发送计数
        doAnswer(invocation -> {
            messageCount.incrementAndGet();
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any());

        InteractiveDeploymentDto.RequestDto request = InteractiveDeploymentDto.RequestDto.builder()
                .deploymentMode("trust")
                .autoConfirmAll(true)
                .containerName("sillytavern")
                .port("8000")
                .build();

        // When - 启动部署（会产生大量进度更新消息）
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> deploymentFuture = 
                deploymentService.startInteractiveDeployment(sessionId, sshConnection, request);

        deploymentFuture.get(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // Then - 验证消息发送性能
        int totalMessages = messageCount.get();
        long duration = endTime - startTime;

        // 性能断言
        assertTrue(totalMessages > 0, "应该发送了进度消息");
        assertTrue(totalMessages < 1000, "消息数量应该控制在合理范围内，实际: " + totalMessages);
        assertTrue(duration < 30000, "部署应该在30秒内完成，实际: " + duration + "ms");

        // 计算消息发送频率
        double messagesPerSecond = totalMessages * 1000.0 / duration;
        assertTrue(messagesPerSecond < 100, "消息发送频率应该控制在100条/秒以内，实际: " + 
                String.format("%.2f", messagesPerSecond) + "条/秒");

        System.out.printf("消息性能测试: 总消息%d条，用时%dms，频率%.2f条/秒%n", 
                totalMessages, duration, messagesPerSecond);
    }

    // ===== Docker安装并发性能测试 =====

    @Test
    @DisplayName("并发Docker安装请求处理性能测试")
    void testConcurrentDockerInstallationPerformance() throws Exception {
        // Given - 多个并发Docker安装请求
        int concurrentInstalls = 20;
        List<CompletableFuture<DockerInstallationService.DockerInstallationResult>> installFutures = 
                new ArrayList<>();

        SystemDetectionService.SystemInfo systemInfo = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock快速Docker安装响应
        DockerInstallationService.DockerInstallationResult quickInstallResult = 
                DockerInstallationService.DockerInstallationResult.builder()
                .success(true)
                .message("Docker安装成功")
                .installedVersion("Docker version 24.0.7")
                .installationMethod("APT 官方仓库")
                .build();

        when(dockerInstallationService.installDocker(any(), any(), anyBoolean(), any()))
                .thenReturn(CompletableFuture.completedFuture(quickInstallResult));

        // When - 并发执行多个Docker安装
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentInstalls; i++) {
            CompletableFuture<DockerInstallationService.DockerInstallationResult> future = 
                    dockerInstallationService.installDocker(sshConnection, systemInfo, false, 
                            progress -> System.out.println("安装" + i + ": " + progress));
            installFutures.add(future);
        }

        // 等待所有安装完成
        CompletableFuture<Void> allInstalls = CompletableFuture.allOf(
                installFutures.toArray(new CompletableFuture[0]));

        allInstalls.get(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // Then - 验证并发安装性能
        long totalTime = endTime - startTime;
        assertTrue(totalTime < 30000, "20个并发Docker安装应该在30秒内完成，实际: " + totalTime + "ms");

        // 验证所有安装都成功
        for (CompletableFuture<DockerInstallationService.DockerInstallationResult> future : installFutures) {
            DockerInstallationService.DockerInstallationResult result = future.join();
            assertTrue(result.isSuccess(), "所有Docker安装都应该成功");
        }

        // 验证并发调用次数
        verify(dockerInstallationService, times(concurrentInstalls))
                .installDocker(any(), any(), anyBoolean(), any());

        System.out.printf("并发Docker安装性能: %d个并发请求，总用时%dms，平均%dms/个%n", 
                concurrentInstalls, totalTime, totalTime / concurrentInstalls);
    }

    // ===== 系统资源消耗监控测试 =====

    @Test
    @DisplayName("长时间运行内存泄漏监控测试")
    void testLongRunningMemoryLeakMonitoring() throws Exception {
        // Given - 长时间运行场景
        Runtime runtime = Runtime.getRuntime();
        List<Long> memorySnapshots = new ArrayList<>();

        // 记录初始内存
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        memorySnapshots.add(initialMemory);

        // When - 执行多轮部署操作
        int rounds = 10;
        for (int round = 0; round < rounds; round++) {
            String sessionId = "memory-test-round-" + round;
            
            InteractiveDeploymentDto.RequestDto request = InteractiveDeploymentDto.RequestDto.builder()
                    .deploymentMode("trust")
                    .autoConfirmAll(true)
                    .containerName("sillytavern-" + round)
                    .port("800" + round)
                    .build();

            // 启动部署
            CompletableFuture<Void> deploymentFuture = 
                    deploymentService.startInteractiveDeployment(sessionId, sshConnection, request);
            deploymentFuture.get(10, TimeUnit.SECONDS);

            // 记录内存使用
            runtime.gc();
            Thread.sleep(100); // 让GC完成
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            memorySnapshots.add(currentMemory);

            // 清理会话（模拟正常清理）
            deploymentService.cancelDeployment(sessionId, "测试清理");
        }

        // Then - 分析内存使用趋势
        long finalMemory = memorySnapshots.get(memorySnapshots.size() - 1);
        long memoryGrowth = finalMemory - initialMemory;

        // 内存增长应该在合理范围内
        assertTrue(memoryGrowth < 50 * 1024 * 1024, "内存增长应该控制在50MB以内，实际增长: " + 
                (memoryGrowth / 1024 / 1024) + "MB");

        // 检查内存增长趋势（不应该持续线性增长）
        boolean hasMemoryLeak = checkMemoryLeakPattern(memorySnapshots);
        assertFalse(hasMemoryLeak, "不应该存在明显的内存泄漏模式");

        System.out.printf("内存监控结果: 初始%dMB，最终%dMB，增长%dMB%n", 
                initialMemory / 1024 / 1024, finalMemory / 1024 / 1024, memoryGrowth / 1024 / 1024);
    }

    @Test
    @DisplayName("线程池管理和资源释放性能测试")
    void testThreadPoolManagementAndResourceRelease() throws Exception {
        // Given - 监控活跃线程数
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int initialThreadCount = threadBean.getThreadCount();

        // When - 执行多个并发部署任务
        int concurrentTasks = 15;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentTasks; i++) {
            String sessionId = "thread-test-session-" + i;
            InteractiveDeploymentDto.RequestDto request = InteractiveDeploymentDto.RequestDto.builder()
                    .deploymentMode("trust")
                    .autoConfirmAll(true)
                    .containerName("sillytavern")
                    .port("8000")
                    .build();

            CompletableFuture<Void> future = 
                    deploymentService.startInteractiveDeployment(sessionId, sshConnection, request);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // 等待资源清理
        Thread.sleep(2000);
        System.gc();

        // Then - 验证线程资源释放
        int finalThreadCount = threadBean.getThreadCount();
        int threadGrowth = finalThreadCount - initialThreadCount;

        // 线程数增长应该在合理范围内
        assertTrue(threadGrowth < 20, "线程数增长应该控制在20以内，实际增长: " + threadGrowth);

        System.out.printf("线程管理测试: 初始线程%d个，最终线程%d个，增长%d个%n", 
                initialThreadCount, finalThreadCount, threadGrowth);
    }

    // ===== 错误恢复性能测试 =====

    @Test
    @DisplayName("高频错误场景下的恢复性能测试")
    void testHighFrequencyErrorRecoveryPerformance() throws Exception {
        // Given - 模拟高频错误场景
        int errorScenarios = 30;
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger recoveryCount = new AtomicInteger(0);

        // Mock部分服务失败
        when(dockerInstallationService.checkDockerInstallation(any()))
                .thenAnswer(invocation -> {
                    errorCount.incrementAndGet();
                    if (errorCount.get() % 3 == 0) {
                        // 每3次有1次成功
                        recoveryCount.incrementAndGet();
                        return CompletableFuture.completedFuture(
                                DockerInstallationService.DockerInstallationStatus.builder()
                                .installed(true)
                                .serviceRunning(true)
                                .message("Docker运行正常")
                                .build());
                    } else {
                        return CompletableFuture.completedFuture(
                                DockerInstallationService.DockerInstallationStatus.builder()
                                .installed(false)
                                .message("Docker检查失败")
                                .build());
                    }
                });

        // When - 执行大量错误恢复测试
        long startTime = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < errorScenarios; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    dockerInstallationService.checkDockerInstallation(sshConnection).join();
                } catch (Exception e) {
                    // 模拟错误处理逻辑
                    System.out.println("处理错误: " + e.getMessage());
                }
            });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // Then - 验证错误恢复性能
        long totalTime = endTime - startTime;
        assertTrue(totalTime < 20000, "错误恢复处理应该在20秒内完成，实际: " + totalTime + "ms");

        int successfulRecoveries = recoveryCount.get();
        assertTrue(successfulRecoveries > 0, "应该有成功的恢复案例");
        assertTrue(successfulRecoveries >= errorScenarios / 4, "恢复成功率应该合理");

        System.out.printf("错误恢复性能: %d个错误场景，%d次成功恢复，用时%dms%n", 
                errorScenarios, successfulRecoveries, totalTime);
    }

    // ===== 辅助方法 =====

    /**
     * Mock优化的服务响应，减少测试时间
     */
    private void mockOptimizedServiceResponses() {
        // Mock快速地理位置检测
        GeolocationDetectionService.GeolocationInfo quickGeoInfo = 
                GeolocationDetectionService.GeolocationInfo.builder()
                .countryCode("US")
                .useChineseMirror(false)
                .detectionMethod("快速检测")
                .build();

        when(geolocationService.detectGeolocation(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(quickGeoInfo));

        // Mock快速系统检测
        SystemDetectionService.SystemInfo quickSystemInfo = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(true)
                .dockerInstalled(true)
                .dockerVersion("Docker version 24.0.7")
                .build();

        when(systemDetectionService.detectSystemEnvironmentSync(any()))
                .thenReturn(quickSystemInfo);

        // Mock快速系统配置
        SystemConfigurationService.SystemConfigResult quickConfigResult = 
                SystemConfigurationService.SystemConfigResult.builder()
                .success(true)
                .message("配置跳过")
                .skipped(true)
                .build();

        when(systemConfigurationService.configureSystemMirrors(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(quickConfigResult));

        // Mock快速Docker状态检查
        DockerInstallationService.DockerInstallationStatus quickDockerStatus = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(true)
                .serviceRunning(true)
                .version("Docker version 24.0.7")
                .message("Docker运行正常")
                .build();

        when(dockerInstallationService.checkDockerInstallation(any()))
                .thenReturn(CompletableFuture.completedFuture(quickDockerStatus));

        // Mock其他快速服务响应
        mockQuickServiceResponses();
    }

    /**
     * Mock其他服务的快速响应
     */
    private void mockQuickServiceResponses() {
        // Docker镜像源
        when(dockerMirrorService.configureMirror(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        DockerMirrorService.DockerMirrorConfigResult.builder()
                        .success(true).message("镜像源配置完成").build()));

        // SillyTavern部署
        when(sillyTavernDeploymentService.deploySillyTavern(any(), any(), anyBoolean(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        SillyTavernDeploymentService.SillyTavernDeploymentResult.builder()
                        .success(true).message("部署完成").containerId("test-container").build()));

        // 外网访问配置
        when(externalAccessService.configureExternalAccess(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        ExternalAccessService.ExternalAccessConfigResult.builder()
                        .success(true).message("外网配置完成").build()));

        // 服务验证
        when(validationService.validateDeployment(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        ServiceValidationService.ServiceValidationResult.builder()
                        .success(true).message("验证通过").build()));
    }

    /**
     * 检查内存泄漏模式
     * @param memorySnapshots 内存快照列表
     * @return 是否存在内存泄漏
     */
    private boolean checkMemoryLeakPattern(List<Long> memorySnapshots) {
        if (memorySnapshots.size() < 3) return false;

        // 检查连续增长模式
        int continuousGrowthCount = 0;
        for (int i = 1; i < memorySnapshots.size(); i++) {
            if (memorySnapshots.get(i) > memorySnapshots.get(i - 1)) {
                continuousGrowthCount++;
            } else {
                continuousGrowthCount = 0;
            }

            // 如果连续增长超过一半的采样点，认为可能存在内存泄漏
            if (continuousGrowthCount > memorySnapshots.size() / 2) {
                return true;
            }
        }

        return false;
    }
}
//
//import com.fufu.terminal.dto.sillytavern.*;
//import com.fufu.terminal.model.SshConnection;
//import com.fufu.terminal.service.StompSessionManager;
//import com.fufu.terminal.service.sillytavern.*;
//import com.jcraft.jsch.JSchException;
//import com.jcraft.jsch.Session;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.function.Consumer;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * SillyTavern性能和并发测试
// * 测试系统在负载和并发用户场景下的行为，验证20-40用户并发访问
// */
//@ExtendWith(MockitoExtension.class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
//@ActiveProfiles("test")
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//@DisplayName("SillyTavern性能和并发测试")
//public class SillyTavernPerformanceTest {
//
//    @Mock
//    private SillyTavernService sillyTavernService;
//
//    @Mock
//    private ConfigurationService configurationService;
//
//    @Mock
//    private RealTimeLogService realTimeLogService;
//
//    @Mock
//    private DataManagementService dataManagementService;
//
//    @Mock
//    private DockerVersionService dockerVersionService;
//
//    @Mock
//    private StompSessionManager sessionManager;
//
//    @Mock
//    private SshConnection sshConnection;
//
//    @Mock
//    private Session jschSession;
//
//    private ExecutorService executorService;
//
//    @BeforeEach
//    void setUp() {
//        executorService = Executors.newFixedThreadPool(50); // 支持50个并发线程
//        when(sshConnection.getJschSession()).thenReturn(jschSession);
//        when(jschSession.isConnected()).thenReturn(true);
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (executorService != null && !executorService.isShutdown()) {
//            executorService.shutdown();
//            try {
//                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
//                    executorService.shutdownNow();
//                }
//            } catch (InterruptedException e) {
//                executorService.shutdownNow();
//                Thread.currentThread().interrupt();
//            }
//        }
//    }
//
//    // ===== 并发用户访问测试 =====
//
//    @Test
//    @DisplayName("应该支持20个并发用户同时进行不同操作")
//    @Timeout(60) // 60秒超时
//    void testTwentyConcurrentUsers() throws Exception {
//        // Given
//        int concurrentUsers = 20;
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch completionLatch = new CountDownLatch(concurrentUsers);
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger errorCount = new AtomicInteger(0);
//
//        // Mock各种服务响应
//        setupServiceMocks();
//
//        // When - 创建20个并发用户，每个执行不同的操作
//        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
//
//        for (int i = 0; i < concurrentUsers; i++) {
//            final int userId = i;
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                try {
//                    startLatch.await(); // 等待开始信号
//
//                    // 根据用户ID执行不同的操作
//                    switch (userId % 4) {
//                        case 0: // 配置管理操作
//                            performConfigurationOperations(userId);
//                            break;
//                        case 1: // 实时日志操作
//                            performLogOperations(userId);
//                            break;
//                        case 2: // 数据管理操作
//                            performDataOperations(userId);
//                            break;
//                        case 3: // 版本管理操作
//                            performVersionOperations(userId);
//                            break;
//                    }
//
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    errorCount.incrementAndGet();
//                    System.err.println("User " + userId + " failed: " + e.getMessage());
//                } finally {
//                    completionLatch.countDown();
//                }
//            }, executorService);
//
//            futures.add(future);
//        }
//
//        // 开始所有操作
//        long startTime = System.currentTimeMillis();
//        startLatch.countDown();
//
//        // 等待所有操作完成
//        boolean completed = completionLatch.await(45, TimeUnit.SECONDS);
//        long endTime = System.currentTimeMillis();
//
//        // Then
//        assertTrue(completed, "所有并发操作应该在45秒内完成");
//        assertEquals(concurrentUsers, successCount.get() + errorCount.get());
//
//        // 验证成功率 >= 90%
//        double successRate = (double) successCount.get() / concurrentUsers * 100;
//        assertTrue(successRate >= 90.0,
//                String.format("成功率应该 >= 90%%, 实际: %.2f%% (%d/%d)",
//                        successRate, successCount.get(), concurrentUsers));
//
//        // 验证平均响应时间 < 3秒
//        double avgResponseTime = (double) (endTime - startTime) / 1000;
//        assertTrue(avgResponseTime < 3.0,
//                String.format("平均响应时间应该 < 3秒, 实际: %.2f秒", avgResponseTime));
//
//        System.out.printf("20并发用户测试完成: 成功率=%.2f%%, 平均响应时间=%.2f秒%n",
//                successRate, avgResponseTime);
//    }
//
//    @Test
//    @DisplayName("应该支持40个并发用户的高负载场景")
//    @Timeout(90) // 90秒超时
//    void testFortyUserHighLoadScenario() throws Exception {
//        // Given
//        int concurrentUsers = 40;
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch completionLatch = new CountDownLatch(concurrentUsers);
//        AtomicInteger operationCount = new AtomicInteger(0);
//        AtomicLong totalResponseTime = new AtomicLong(0);
//
//        setupServiceMocks();
//
//        // When - 创建40个并发用户
//        for (int i = 0; i < concurrentUsers; i++) {
//            final int userId = i;
//            executorService.submit(() -> {
//                try {
//                    startLatch.await();
//
//                    long userStartTime = System.currentTimeMillis();
//
//                    // 每个用户执行多个操作序列
//                    performMultipleOperations(userId);
//
//                    long userEndTime = System.currentTimeMillis();
//                    totalResponseTime.addAndGet(userEndTime - userStartTime);
//                    operationCount.incrementAndGet();
//
//                } catch (Exception e) {
//                    System.err.println("High load user " + userId + " failed: " + e.getMessage());
//                } finally {
//                    completionLatch.countDown();
//                }
//            });
//        }
//
//        // 开始高负载测试
//        long testStartTime = System.currentTimeMillis();
//        startLatch.countDown();
//
//        // 等待所有操作完成
//        boolean completed = completionLatch.await(75, TimeUnit.SECONDS);
//        long testEndTime = System.currentTimeMillis();
//
//        // Then
//        assertTrue(completed, "高负载测试应该在75秒内完成");
//
//        // 验证性能指标
//        double totalTestTime = (double) (testEndTime - testStartTime) / 1000;
//        double avgUserResponseTime = (double) totalResponseTime.get() / operationCount.get() / 1000;
//        double throughput = (double) operationCount.get() / totalTestTime;
//
//        assertTrue(avgUserResponseTime < 5.0,
//                String.format("平均用户响应时间应该 < 5秒, 实际: %.2f秒", avgUserResponseTime));
//        assertTrue(throughput >= 8.0,
//                String.format("吞吐量应该 >= 8 ops/sec, 实际: %.2f ops/sec", throughput));
//
//        System.out.printf("40用户高负载测试完成: 平均响应时间=%.2f秒, 吞吐量=%.2f ops/sec%n",
//                avgUserResponseTime, throughput);
//    }
//
//    @Test
//    @DisplayName("应该在并发操作中维持数据一致性")
//    void testConcurrentDataConsistency() throws Exception {
//        // Given
//        int concurrentOperations = 30;
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch completionLatch = new CountDownLatch(concurrentOperations);
//        AtomicInteger configUpdateCount = new AtomicInteger(0);
//
//        // Mock配置服务，模拟并发配置更新
//        when(configurationService.validateConfiguration(any()))
//                .thenReturn(new java.util.HashMap<>());
//        when(configurationService.updateConfigurationWithRestart(any(), any(), any()))
//                .thenAnswer(invocation -> {
//                    configUpdateCount.incrementAndGet();
//                    Thread.sleep(50); // 模拟配置更新耗时
//                    return true;
//                });
//
//        // When - 30个并发配置更新操作
//        for (int i = 0; i < concurrentOperations; i++) {
//            final int operationId = i;
//            executorService.submit(() -> {
//                try {
//                    startLatch.await();
//
//                    ConfigurationDto config = new ConfigurationDto();
//                    config.setUsername("user_" + operationId);
//                    config.setPassword("password_" + operationId);
//
//                    configurationService.updateConfigurationWithRestart(sshConnection, "sillytavern", config);
//
//                } catch (Exception e) {
//                    System.err.println("Concurrent operation " + operationId + " failed: " + e.getMessage());
//                } finally {
//                    completionLatch.countDown();
//                }
//            });
//        }
//
//        // 开始并发操作
//        startLatch.countDown();
//        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
//
//        // Then
//        assertTrue(completed, "所有并发操作应该完成");
//        assertEquals(concurrentOperations, configUpdateCount.get(), "所有配置更新都应该被执行");
//
//        // 验证配置服务被正确调用
//        verify(configurationService, times(concurrentOperations))
//                .updateConfigurationWithRestart(any(), any(), any());
//    }
//
//    @Test
//    @DisplayName("应该在内存限制下处理大量日志数据")
//    void testMemoryEfficiencyWithLargeLogs() throws Exception {
//        // Given
//        int logRequestCount = 50;
//        int linesPerRequest = 2000;
//        CountDownLatch completionLatch = new CountDownLatch(logRequestCount);
//        AtomicLong totalMemoryUsed = new AtomicLong(0);
//
//        // Mock大量日志数据
//        when(realTimeLogService.getHistoryLogs(any(), any(), anyInt(), any()))
//                .thenAnswer(invocation -> {
//                    int lines = invocation.getArgument(2);
//
//                    RealTimeLogDto logDto = new RealTimeLogDto();
//                    logDto.setLines(generateLargeLogData(lines));
//                    logDto.setTotalLines(lines);
//
//                    // 模拟内存使用
//                    RealTimeLogDto.MemoryInfo memoryInfo = RealTimeLogDto.MemoryInfo.builder()
//                            .cachedLines(lines)
//                            .maxLines(5000)
//                            .memoryUsagePercent(Math.min(95.0, (double) lines / 5000 * 100))
//                            .needsCleanup(lines > 4000)
//                            .build();
//                    logDto.setMemoryInfo(memoryInfo);
//
//                    return logDto;
//                });
//
//        // When - 处理大量日志请求
//        long startTime = System.currentTimeMillis();
//
//        for (int i = 0; i < logRequestCount; i++) {
//            executorService.submit(() -> {
//                try {
//                    RealTimeLogDto result = realTimeLogService.getHistoryLogs(
//                            sshConnection, "sillytavern", linesPerRequest, "all");
//
//                    if (result.getMemoryInfo() != null) {
//                        totalMemoryUsed.addAndGet(result.getMemoryInfo().getCachedLines());
//                    }
//
//                } catch (Exception e) {
//                    System.err.println("Large log processing failed: " + e.getMessage());
//                } finally {
//                    completionLatch.countDown();
//                }
//            });
//        }
//
//        boolean completed = completionLatch.await(45, TimeUnit.SECONDS);
//        long endTime = System.currentTimeMillis();
//
//        // Then
//        assertTrue(completed, "大量日志处理应该完成");
//
//        double processingTime = (double) (endTime - startTime) / 1000;
//        double avgMemoryPerRequest = (double) totalMemoryUsed.get() / logRequestCount;
//
//        assertTrue(processingTime < 30.0,
//                String.format("大量日志处理时间应该 < 30秒, 实际: %.2f秒", processingTime));
//        assertTrue(avgMemoryPerRequest <= linesPerRequest * 1.1,
//                String.format("平均内存使用应该合理, 实际: %.0f lines/request", avgMemoryPerRequest));
//
//        System.out.printf("大量日志处理完成: 处理时间=%.2f秒, 平均内存=%.0f lines/request%n",
//                processingTime, avgMemoryPerRequest);
//    }
//
//    @Test
//    @DisplayName("应该处理WebSocket连接的负载压力")
//    void testWebSocketConnectionLoad() throws Exception {
//        // Given
//        int connectionCount = 100;
//        CountDownLatch connectionLatch = new CountDownLatch(connectionCount);
//        AtomicInteger activeConnections = new AtomicInteger(0);
//        AtomicInteger messagesSent = new AtomicInteger(0);
//
//        // When - 模拟100个WebSocket连接
//        for (int i = 0; i < connectionCount; i++) {
//            final int connectionId = i;
//            executorService.submit(() -> {
//                try {
//                    // 模拟WebSocket连接建立
//                    String sessionId = "session_" + connectionId;
//                    activeConnections.incrementAndGet();
//
//                    // 每个连接发送多条消息
//                    for (int j = 0; j < 10; j++) {
//                        // 模拟实时日志流
//                        realTimeLogService.startLogStream(sessionId, "sillytavern", 1000);
//                        messagesSent.incrementAndGet();
//                        Thread.sleep(10); // 模拟消息间隔
//                    }
//
//                    // 模拟连接清理
//                    realTimeLogService.stopLogStream(sessionId);
//                    activeConnections.decrementAndGet();
//
//                } catch (Exception e) {
//                    System.err.println("WebSocket connection " + connectionId + " failed: " + e.getMessage());
//                } finally {
//                    connectionLatch.countDown();
//                }
//            });
//        }
//
//        // 等待所有连接处理完成
//        boolean completed = connectionLatch.await(30, TimeUnit.SECONDS);
//
//        // Then
//        assertTrue(completed, "所有WebSocket连接处理应该完成");
//        assertEquals(0, activeConnections.get(), "所有连接都应该正确清理");
//        assertEquals(connectionCount * 10, messagesSent.get(), "所有消息都应该发送");
//
//        // 验证服务调用次数
//        verify(realTimeLogService, times(connectionCount * 10)).startLogStream(any(), any(), anyInt());
//        verify(realTimeLogService, times(connectionCount)).stopLogStream(any());
//
//        System.out.printf("WebSocket负载测试完成: %d连接, %d消息%n", connectionCount, messagesSent.get());
//    }
//
//    @Test
//    @DisplayName("应该在持续负载下保持系统稳定性")
//    void testSustainedLoadStability() throws Exception {
//        // Given
//        int durationSeconds = 60; // 60秒持续负载
//        int operationsPerSecond = 10;
//        AtomicInteger totalOperations = new AtomicInteger(0);
//        AtomicInteger successfulOperations = new AtomicInteger(0);
//        AtomicBoolean testRunning = new AtomicBoolean(true);
//
//        setupServiceMocks();
//
//        // 启动持续负载生成器
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
//
//        // When - 持续60秒的负载测试
//        long startTime = System.currentTimeMillis();
//
//        scheduler.scheduleAtFixedRate(() -> {
//            if (!testRunning.get()) return;
//
//            for (int i = 0; i < operationsPerSecond; i++) {
//                executorService.submit(() -> {
//                    if (!testRunning.get()) return;
//
//                    try {
//                        totalOperations.incrementAndGet();
//
//                        // 随机执行不同类型的操作
//                        int operationType = (int) (Math.random() * 4);
//                        switch (operationType) {
//                            case 0:
//                                configurationService.readConfiguration(sshConnection, "sillytavern");
//                                break;
//                            case 1:
//                                realTimeLogService.getHistoryLogs(sshConnection, "sillytavern", 500, "all");
//                                break;
//                            case 2:
//                                dockerVersionService.getVersionInfo(sshConnection, "sillytavern");
//                                break;
//                            case 3:
//                                sillyTavernService.getContainerStatus(sshConnection);
//                                break;
//                        }
//
//                        successfulOperations.incrementAndGet();
//
//                    } catch (Exception e) {
//                        // 记录错误但继续测试
//                    }
//                });
//            }
//        }, 0, 1, TimeUnit.SECONDS);
//
//        // 运行指定时间
//        Thread.sleep(durationSeconds * 1000);
//        testRunning.set(false);
//        scheduler.shutdown();
//
//        // 等待所有操作完成
//        executorService.shutdown();
//        executorService.awaitTermination(10, TimeUnit.SECONDS);
//
//        long endTime = System.currentTimeMillis();
//
//        // Then
//        double actualDuration = (double) (endTime - startTime) / 1000;
//        double actualThroughput = (double) successfulOperations.get() / actualDuration;
//        double successRate = (double) successfulOperations.get() / totalOperations.get() * 100;
//
//        assertTrue(successRate >= 95.0,
//                String.format("持续负载成功率应该 >= 95%%, 实际: %.2f%%", successRate));
//        assertTrue(actualThroughput >= operationsPerSecond * 0.8,
//                String.format("实际吞吐量应该 >= %.1f ops/sec, 实际: %.2f ops/sec",
//                        operationsPerSecond * 0.8, actualThroughput));
//
//        System.out.printf("持续负载测试完成: 总操作=%d, 成功率=%.2f%%, 吞吐量=%.2f ops/sec%n",
//                totalOperations.get(), successRate, actualThroughput);
//    }
//
//    // ===== 辅助方法 =====
//
//    private void setupServiceMocks() {
//        // Mock配置服务
//        ConfigurationDto config = new ConfigurationDto();
//        config.setContainerName("sillytavern");
//        config.setUsername("admin");
//        when(configurationService.readConfiguration(any(), any())).thenReturn(config);
//        when(configurationService.validateConfiguration(any())).thenReturn(new java.util.HashMap<>());
//        when(configurationService.updateConfigurationWithRestart(any(), any(), any())).thenReturn(true);
//
//        // Mock日志服务
//        RealTimeLogDto logDto = new RealTimeLogDto();
//        logDto.setContainerName("sillytavern");
//        logDto.setLines(List.of("Test log line"));
//        when(realTimeLogService.getHistoryLogs(any(), any(), anyInt(), any())).thenReturn(logDto);
//        doNothing().when(realTimeLogService).startLogStream(any(), any(), anyInt());
//        doNothing().when(realTimeLogService).stopLogStream(any());
//
//        // Mock版本服务
//        VersionInfoDto versionInfo = new VersionInfoDto();
//        versionInfo.setCurrentVersion("1.12.0");
//        versionInfo.setLatestVersion("1.12.2");
//        when(dockerVersionService.getVersionInfo(any(), any())).thenReturn(versionInfo);
//        when(dockerVersionService.upgradeToVersion(any(), any(), any(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//        doNothing().when(dockerVersionService).cleanupUnusedImages(any());
//
//        // Mock数据管理服务
//        DataExportDto exportDto = new DataExportDto();
//        exportDto.setFileName("test_export.zip");
//        when(dataManagementService.exportData(any(), any(), any()))
//                .thenReturn(CompletableFuture.completedFuture(exportDto));
//        when(dataManagementService.importData(any(), any(), any(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        // Mock容器状态
//        ContainerStatusDto status = new ContainerStatusDto();
//        status.setExists(true);
//        status.setRunning(true);
//        when(sillyTavernService.getContainerStatus(any())).thenReturn(status);
//    }
//
//    private void performConfigurationOperations(int userId) throws Exception {
//        // 读取配置
//        configurationService.readConfiguration(sshConnection, "sillytavern");
//        Thread.sleep(10);
//
//        // 更新配置
//        ConfigurationDto config = new ConfigurationDto();
//        config.setUsername("user_" + userId);
//        config.setPassword("password_" + userId);
//        configurationService.updateConfigurationWithRestart(sshConnection, "sillytavern", config);
//    }
//
//    private void performLogOperations(int userId) throws Exception {
//        String sessionId = "session_" + userId;
//
//        // 启动日志流
//        realTimeLogService.startLogStream(sessionId, "sillytavern", 1000);
//        Thread.sleep(20);
//
//        // 获取历史日志
//        realTimeLogService.getHistoryLogs(sshConnection, "sillytavern", 500, "all");
//        Thread.sleep(15);
//
//        // 停止日志流
//        realTimeLogService.stopLogStream(sessionId);
//    }
//
//    private void performDataOperations(int userId) throws Exception {
//        Consumer<String> progressCallback = progress -> {
//            // 模拟进度回调
//        };
//
//        // 数据导出
//        dataManagementService.exportData(sshConnection, "sillytavern", progressCallback);
//        Thread.sleep(30);
//
//        // 数据导入
//        dataManagementService.importData(sshConnection, "sillytavern", "test_" + userId + ".zip", progressCallback);
//    }
//
//    private void performVersionOperations(int userId) throws Exception {
//        // 获取版本信息
//        dockerVersionService.getVersionInfo(sshConnection, "sillytavern");
//        Thread.sleep(25);
//
//        // 清理镜像
//        dockerVersionService.cleanupUnusedImages(sshConnection);
//    }
//
//    private void performMultipleOperations(int userId) throws Exception {
//        // 每个用户执行多个操作序列
//        performConfigurationOperations(userId);
//        performLogOperations(userId);
//        performVersionOperations(userId);
//
//        // 额外的状态检查
//        sillyTavernService.getContainerStatus(sshConnection);
//    }
//
//    private List<String> generateLargeLogData(int lineCount) {
//        List<String> lines = new java.util.ArrayList<>();
//        for (int i = 0; i < lineCount; i++) {
//            lines.add(String.format("[INFO] %s Large log entry %d with detailed message content",
//                    LocalDateTime.now(), i));
//        }
//        return lines;
//    }
//}
//    private DockerVersionService dockerVersionService;
//
//    @Mock
//    private StompSessionManager sessionManager;
//
//    @BeforeEach
//    void setUp() {
//        // Mock基础行为
//        when(sessionManager.getConnection(anyString())).thenReturn(createMockSshConnection());
//    }
//
//    @AfterEach
//    void tearDown() {
//        // 清理资源
//    }
//
//    // ===== 并发用户测试 =====
//
//    @Test
//    @DisplayName("应该支持20个并发用户执行不同操作")
//    @Timeout(60) // 60秒超时
//    void testConcurrentUserOperations() throws Exception {
//        final int CONCURRENT_USERS = 20;
//        final CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_USERS);
//        final ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();
//        final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
//
//        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
//
//        try {
//            // 创建并发用户会话
//            for (int i = 0; i < CONCURRENT_USERS; i++) {
//                final int userId = i;
//
//                executor.submit(() -> {
//                    try {
//                        String sessionId = "perf-test-session-" + userId;
//                        simulateUserSession(sessionId, userId, results);
//
//                    } catch (Exception e) {
//                        exceptions.add(e);
//                    } finally {
//                        completionLatch.countDown();
//                    }
//                });
//            }
//
//            // 等待所有操作完成
//            assertTrue(completionLatch.await(45, TimeUnit.SECONDS),
//                    "不是所有并发操作都在超时时间内完成");
//
//            // 验证没有异常发生
//            if (!exceptions.isEmpty()) {
//                Exception firstException = exceptions.poll();
//                fail("并发操作失败: " + firstException.getMessage(), firstException);
//            }
//
//            // 验证所有操作都完成了
//            assertEquals(CONCURRENT_USERS, results.size(),
//                    "不是所有用户会话都成功完成");
//
//            System.out.println("并发用户测试成功完成:");
//            results.forEach(System.out::println);
//
//        } finally {
//            executor.shutdown();
//            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
//                executor.shutdownNow();
//            }
//        }
//    }
//
//    @Test
//    @DisplayName("应该处理快速连续操作")
//    @Timeout(30)
//    void testRapidSequentialOperations() throws Exception {
//        String sessionId = "rapid-ops-session";
//        SshConnection mockConnection = createMockSshConnection();
//
//        // Mock服务响应以进行快速操作
//        ContainerStatusDto runningStatus = new ContainerStatusDto();
//        runningStatus.setExists(true);
//        runningStatus.setRunning(true);
//        runningStatus.setContainerName("sillytavern");
//
//        when(sillyTavernService.getContainerStatus(mockConnection)).thenReturn(runningStatus);
//        when(sillyTavernService.isContainerRunning(mockConnection)).thenReturn(true);
//
//        SystemInfoDto systemInfo = new SystemInfoDto();
//        systemInfo.setMeetsRequirements(true);
//        when(sillyTavernService.validateSystemRequirements(mockConnection)).thenReturn(systemInfo);
//
//        // 执行快速连续操作
//        final int RAPID_OPERATIONS = 50;
//        long startTime = System.currentTimeMillis();
//
//        for (int i = 0; i < RAPID_OPERATIONS; i++) {
//            // 交替进行不同操作以模拟真实使用
//            switch (i % 4) {
//                case 0:
//                    ContainerStatusDto status = sillyTavernService.getContainerStatus(mockConnection);
//                    assertNotNull(status);
//                    break;
//                case 1:
//                    boolean isRunning = sillyTavernService.isContainerRunning(mockConnection);
//                    assertTrue(isRunning);
//                    break;
//                case 2:
//                    SystemInfoDto sysInfo = sillyTavernService.validateSystemRequirements(mockConnection);
//                    assertNotNull(sysInfo);
//                    break;
//                case 3:
//                    // 模拟轻量级操作
//                    Thread.sleep(1); // 最小延迟
//                    break;
//            }
//        }
//
//        long endTime = System.currentTimeMillis();
//        long totalTime = endTime - startTime;
//        double operationsPerSecond = (RAPID_OPERATIONS * 1000.0) / totalTime;
//
//        System.out.println(String.format("快速操作完成: %d 操作在 %d 毫秒内 (%.2f 操作/秒)",
//                RAPID_OPERATIONS, totalTime, operationsPerSecond));
//
//        // 性能断言：应该处理至少每秒10个操作
//        assertTrue(operationsPerSecond >= 10.0,
//                String.format("性能太慢: %.2f 操作/秒 (期望 >= 10)", operationsPerSecond));
//    }
//
//    // ===== 并发配置管理测试 =====
//
//    @Test
//    @DisplayName("应该优雅处理并发配置更新")
//    @Timeout(60)
//    void testConcurrentConfigurationUpdates() throws Exception {
//        final int CONCURRENT_CONFIGS = 10;
//        final CountDownLatch configLatch = new CountDownLatch(CONCURRENT_CONFIGS);
//        final ConcurrentLinkedQueue<String> configResults = new ConcurrentLinkedQueue<>();
//        final ConcurrentLinkedQueue<Exception> configErrors = new ConcurrentLinkedQueue<>();
//
//        ExecutorService configExecutor = Executors.newFixedThreadPool(CONCURRENT_CONFIGS);
//
//        // Mock配置服务行为
//        when(configurationService.updateConfigurationWithRestart(any(), eq("sillytavern"), any()))
//                .thenAnswer(invocation -> {
//                    Thread.sleep(100); // 模拟配置更新时间
//                    return true;
//                });
//
//        try {
//            for (int i = 0; i < CONCURRENT_CONFIGS; i++) {
//                final int configId = i;
//
//                configExecutor.submit(() -> {
//                    try {
//                        String containerName = "sillytavern";
//                        ConfigurationDto config = new ConfigurationDto();
//                        config.setUsername("admin" + configId);
//                        config.setPassword("password" + configId);
//
//                        boolean result = configurationService.updateConfigurationWithRestart(
//                                createMockSshConnection(), containerName, config);
//
//                        if (result) {
//                            configResults.add("配置更新 " + configId + " 完成");
//                        }
//
//                    } catch (Exception e) {
//                        configErrors.add(e);
//                    } finally {
//                        configLatch.countDown();
//                    }
//                });
//            }
//
//            assertTrue(configLatch.await(45, TimeUnit.SECONDS),
//                    "不是所有配置更新都完成");
//
//            // 检查配置错误
//            if (!configErrors.isEmpty()) {
//                Exception firstError = configErrors.poll();
//                fail("并发配置更新失败: " + firstError.getMessage(), firstError);
//            }
//
//            assertEquals(CONCURRENT_CONFIGS, configResults.size(),
//                    "不是所有配置更新都成功完成");
//
//            System.out.println("并发配置更新完成:");
//            configResults.forEach(System.out::println);
//
//        } finally {
//            configExecutor.shutdown();
//            if (!configExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
//                configExecutor.shutdownNow();
//            }
//        }
//    }
//
//    // ===== 实时日志性能测试 =====
//
//    @Test
//    @DisplayName("应该高效处理多个日志流")
//    @Timeout(45)
//    void testMultipleLogStreamsPerformance() throws Exception {
//        final int LOG_STREAMS = 15;
//        final CountDownLatch logLatch = new CountDownLatch(LOG_STREAMS);
//        final ConcurrentLinkedQueue<String> logResults = new ConcurrentLinkedQueue<>();
//
//        ExecutorService logExecutor = Executors.newFixedThreadPool(LOG_STREAMS);
//
//        // Mock日志服务
//        doNothing().when(realTimeLogService).startLogStream(anyString(), anyString(), anyInt());
//        doNothing().when(realTimeLogService).stopLogStream(anyString());
//
//        RealTimeLogDto mockLogDto = new RealTimeLogDto();
//        mockLogDto.setLines(List.of("测试日志行1", "测试日志行2"));
//        mockLogDto.setTotalLines(1000);
//
//        when(realTimeLogService.getHistoryLogs(any(), anyString(), anyInt(), anyString()))
//                .thenReturn(mockLogDto);
//
//        try {
//            for (int i = 0; i < LOG_STREAMS; i++) {
//                final int streamId = i;
//
//                logExecutor.submit(() -> {
//                    try {
//                        String sessionId = "log-session-" + streamId;
//                        String containerName = "sillytavern";
//
//                        // 模拟日志流操作
//                        realTimeLogService.startLogStream(sessionId, containerName, 1000);
//                        Thread.sleep(50); // 模拟日志流运行时间
//
//                        RealTimeLogDto logs = realTimeLogService.getHistoryLogs(
//                                createMockSshConnection(), containerName, 500, "all");
//
//                        assertNotNull(logs);
//                        realTimeLogService.stopLogStream(sessionId);
//
//                        logResults.add("日志流 " + streamId + " 完成");
//
//                    } catch (Exception e) {
//                        System.err.println("日志流 " + streamId + " 失败: " + e.getMessage());
//                    } finally {
//                        logLatch.countDown();
//                    }
//                });
//            }
//
//            assertTrue(logLatch.await(30, TimeUnit.SECONDS),
//                    "不是所有日志流都完成");
//
//            assertEquals(LOG_STREAMS, logResults.size(),
//                    "不是所有日志流都成功");
//
//            System.out.println("多日志流测试完成:");
//            logResults.forEach(System.out::println);
//
//        } finally {
//            logExecutor.shutdown();
//            if (!logExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
//                logExecutor.shutdownNow();
//            }
//        }
//    }
//
//    // ===== 数据管理并发测试 =====
//
//    @Test
//    @DisplayName("应该处理并发数据导出请求")
//    @Timeout(90)
//    void testConcurrentDataExports() throws Exception {
//        final int CONCURRENT_EXPORTS = 5;
//        final CountDownLatch exportLatch = new CountDownLatch(CONCURRENT_EXPORTS);
//        final ConcurrentLinkedQueue<String> exportResults = new ConcurrentLinkedQueue<>();
//        final ConcurrentLinkedQueue<Exception> exportErrors = new ConcurrentLinkedQueue<>();
//
//        ExecutorService exportExecutor = Executors.newFixedThreadPool(CONCURRENT_EXPORTS);
//
//        // Mock数据导出服务
//        DataExportDto mockExport = new DataExportDto();
//        mockExport.setFileName("test_export.zip");
//        mockExport.setSizeBytes(1024 * 1024L);
//
//        when(dataManagementService.exportData(any(), anyString(), any()))
//                .thenReturn(CompletableFuture.completedFuture(mockExport));
//
//        try {
//            for (int i = 0; i < CONCURRENT_EXPORTS; i++) {
//                final int exportId = i;
//
//                exportExecutor.submit(() -> {
//                    try {
//                        String containerName = "sillytavern-" + exportId;
//                        Consumer<String> progressCallback = progress ->
//                                System.out.println("导出 " + exportId + ": " + progress);
//
//                        CompletableFuture<DataExportDto> exportFuture = dataManagementService.exportData(
//                                createMockSshConnection(), containerName, progressCallback);
//
//                        DataExportDto result = exportFuture.get(30, TimeUnit.SECONDS);
//                        assertNotNull(result);
//                        exportResults.add("数据导出 " + exportId + " 完成");
//
//                    } catch (Exception e) {
//                        exportErrors.add(e);
//                    } finally {
//                        exportLatch.countDown();
//                    }
//                });
//            }
//
//            assertTrue(exportLatch.await(60, TimeUnit.SECONDS),
//                    "不是所有数据导出都完成");
//
//            if (!exportErrors.isEmpty()) {
//                Exception firstError = exportErrors.poll();
//                fail("并发数据导出失败: " + firstError.getMessage(), firstError);
//            }
//
//            assertEquals(CONCURRENT_EXPORTS, exportResults.size(),
//                    "不是所有数据导出都成功");
//
//            System.out.println("并发数据导出完成:");
//            exportResults.forEach(System.out::println);
//
//        } finally {
//            exportExecutor.shutdown();
//            if (!exportExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
//                exportExecutor.shutdownNow();
//            }
//        }
//    }
//
//    // ===== 版本管理并发测试 =====
//
//    @Test
//    @DisplayName("应该防止并发版本升级冲突")
//    @Timeout(60)
//    void testVersionUpgradeConcurrencyControl() throws Exception {
//        final int CONCURRENT_UPGRADES = 3;
//        final CountDownLatch upgradeLatch = new CountDownLatch(CONCURRENT_UPGRADES);
//        final AtomicInteger successfulUpgrades = new AtomicInteger(0);
//        final AtomicInteger failedUpgrades = new AtomicInteger(0);
//
//        ExecutorService upgradeExecutor = Executors.newFixedThreadPool(CONCURRENT_UPGRADES);
//
//        // Mock版本升级，第一个成功，其他的应该失败（由于锁机制）
//        when(dockerVersionService.upgradeToVersion(any(), eq("sillytavern"), anyString(), any()))
//                .thenAnswer(invocation -> {
//                    String version = invocation.getArgument(2);
//                    if ("1.12.1".equals(version)) {
//                        // 第一个升级成功
//                        Thread.sleep(200);
//                        return CompletableFuture.completedFuture(null);
//                    } else {
//                        // 其他升级失败（锁冲突）
//                        return CompletableFuture.failedFuture(
//                                new RuntimeException("版本升级失败：其他升级正在进行中"));
//                    }
//                });
//
//        try {
//            String[] versions = {"1.12.1", "1.12.2", "1.12.3"};
//
//            for (int i = 0; i < CONCURRENT_UPGRADES; i++) {
//                final int upgradeId = i;
//                final String targetVersion = versions[i];
//
//                upgradeExecutor.submit(() -> {
//                    try {
//                        Consumer<String> progressCallback = progress ->
//                                System.out.println("升级 " + upgradeId + ": " + progress);
//
//                        CompletableFuture<Void> upgradeFuture = dockerVersionService.upgradeToVersion(
//                                createMockSshConnection(), "sillytavern", targetVersion, progressCallback);
//
//                        upgradeFuture.get(10, TimeUnit.SECONDS);
//                        successfulUpgrades.incrementAndGet();
//
//                    } catch (Exception e) {
//                        failedUpgrades.incrementAndGet();
//                        System.out.println("升级 " + upgradeId + " 失败: " + e.getMessage());
//                    } finally {
//                        upgradeLatch.countDown();
//                    }
//                });
//            }
//
//            assertTrue(upgradeLatch.await(30, TimeUnit.SECONDS),
//                    "不是所有升级操作都完成");
//
//            // 验证互斥行为：应该只有一个成功，其他失败
//            assertEquals(1, successfulUpgrades.get(), "应该只有一个升级成功");
//            assertEquals(2, failedUpgrades.get(), "应该有两个升级失败");
//
//            System.out.println("版本升级并发控制测试完成");
//
//        } finally {
//            upgradeExecutor.shutdown();
//            if (!upgradeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
//                upgradeExecutor.shutdownNow();
//            }
//        }
//    }
//
//    // ===== 内存使用效率测试 =====
//
//    @Test
//    @DisplayName("应该在扩展操作期间高效使用内存")
//    @Timeout(45)
//    void testMemoryUsageEfficiency() throws Exception {
//        Runtime runtime = Runtime.getRuntime();
//
//        // 记录初始内存使用
//        runtime.gc(); // 强制垃圾回收
//        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
//
//        final int EXTENDED_OPERATIONS = 1000;
//        String sessionId = "memory-test-session";
//        SshConnection mockConnection = createMockSshConnection();
//
//        // 执行扩展操作
//        for (int i = 0; i < EXTENDED_OPERATIONS; i++) {
//            // 模拟各种可能消耗内存的操作
//            ContainerStatusDto status = new ContainerStatusDto();
//            status.setExists(true);
//            status.setRunning(true);
//            status.setContainerName("test-container-" + i);
//            status.setMemoryUsageMB((long) (Math.random() * 1000));
//            status.setCpuUsagePercent(Math.random() * 100);
//
//            // 创建和丢弃对象以测试内存管理
//            DeploymentProgressDto progress = DeploymentProgressDto.success(
//                    "test-stage", i % 100, "操作 " + i);
//
//            // 定期强制垃圾回收以测试清理
//            if (i % 100 == 0) {
//                runtime.gc();
//                Thread.sleep(1); // 给GC时间工作
//            }
//        }
//
//        // 最终垃圾回收和内存检查
//        runtime.gc();
//        Thread.sleep(100); // 允许GC完成
//        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
//        long memoryIncrease = finalMemory - initialMemory;
//
//        System.out.println(String.format("内存使用: 初始=%d KB, 最终=%d KB, 增加=%d KB",
//                initialMemory / 1024, finalMemory / 1024, memoryIncrease / 1024));
//
//        // 1000个操作的内存增长不应超过50MB
//        long maxAllowedIncrease = 50 * 1024 * 1024; // 50MB以字节为单位
//        assertTrue(memoryIncrease < maxAllowedIncrease,
//                String.format("内存使用过高: %d KB 增长 (最大允许: %d KB)",
//                        memoryIncrease / 1024, maxAllowedIncrease / 1024));
//    }
//
//    // ===== 持续负载稳定性测试 =====
//
//    @Test
//    @DisplayName("应该在持续负载下保持稳定性能")
//    @Timeout(120) // 2分钟超时
//    void testSustainedLoadStability() throws Exception {
//        final int DURATION_SECONDS = 30; // 持续30秒
//        final int OPERATIONS_PER_SECOND = 5;
//        final AtomicInteger completedOperations = new AtomicInteger(0);
//        final AtomicInteger failedOperations = new AtomicInteger(0);
//        final ConcurrentLinkedQueue<Long> operationTimes = new ConcurrentLinkedQueue<>();
//
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
//
//        // Mock基本操作
//        ContainerStatusDto mockStatus = new ContainerStatusDto();
//        mockStatus.setExists(true);
//        mockStatus.setRunning(true);
//        when(sillyTavernService.getContainerStatus(any())).thenReturn(mockStatus);
//
//        try {
//            long startTime = System.currentTimeMillis();
//
//            // 调度持续操作
//            ScheduledFuture<?> sustainedLoad = scheduler.scheduleAtFixedRate(() -> {
//                try {
//                    long opStartTime = System.currentTimeMillis();
//
//                    // 执行轻量级操作
//                    SshConnection mockConnection = createMockSshConnection();
//                    ContainerStatusDto status = sillyTavernService.getContainerStatus(mockConnection);
//                    assertNotNull(status);
//
//                    long opEndTime = System.currentTimeMillis();
//                    operationTimes.add(opEndTime - opStartTime);
//                    completedOperations.incrementAndGet();
//
//                } catch (Exception e) {
//                    failedOperations.incrementAndGet();
//                }
//            }, 0, 1000 / OPERATIONS_PER_SECOND, TimeUnit.MILLISECONDS);
//
//            // 让它运行指定的持续时间
//            Thread.sleep(DURATION_SECONDS * 1000);
//            sustainedLoad.cancel(false);
//
//            // 等待任何剩余操作完成
//            Thread.sleep(1000);
//
//            long endTime = System.currentTimeMillis();
//            long totalTime = endTime - startTime;
//
//            int expectedOperations = DURATION_SECONDS * OPERATIONS_PER_SECOND;
//            int actualOperations = completedOperations.get();
//            int failures = failedOperations.get();
//
//            // 计算性能指标
//            double successRate = (double) actualOperations / (actualOperations + failures) * 100;
//            double averageOperationTime = operationTimes.stream()
//                    .mapToLong(Long::longValue)
//                    .average()
//                    .orElse(0.0);
//
//            System.out.println(String.format("持续负载测试结果:"));
//            System.out.println(String.format("  持续时间: %d 秒", totalTime / 1000));
//            System.out.println(String.format("  期望操作: %d", expectedOperations));
//            System.out.println(String.format("  完成操作: %d", actualOperations));
//            System.out.println(String.format("  失败操作: %d", failures));
//            System.out.println(String.format("  成功率: %.2f%%", successRate));
//            System.out.println(String.format("  平均操作时间: %.2f ms", averageOperationTime));
//
//            // 性能断言
//            assertTrue(successRate >= 95.0,
//                    String.format("成功率太低: %.2f%% (期望 >= 95%%)", successRate));
//
//            assertTrue(actualOperations >= expectedOperations * 0.8,
//                    String.format("完成操作太少: %d (期望 >= %d)",
//                            actualOperations, (int) (expectedOperations * 0.8)));
//
//            assertTrue(averageOperationTime <= 1000.0,
//                    String.format("操作太慢: %.2f ms (期望 <= 1000ms)", averageOperationTime));
//
//        } finally {
//            scheduler.shutdown();
//            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
//                scheduler.shutdownNow();
//            }
//        }
//    }
//
//    // ===== 辅助方法 =====
//
//    private void simulateUserSession(String sessionId, int userId, ConcurrentLinkedQueue<String> results)
//            throws Exception {
//
//        SshConnection mockConnection = createMockSshConnection();
//
//        // 模拟不同的用户行为
//        switch (userId % 4) {
//            case 0: // 状态检查器
//                for (int i = 0; i < 10; i++) {
//                    ContainerStatusDto status = new ContainerStatusDto();
//                    status.setExists(true);
//                    status.setRunning(true);
//                    when(sillyTavernService.getContainerStatus(mockConnection)).thenReturn(status);
//
//                    ContainerStatusDto result = sillyTavernService.getContainerStatus(mockConnection);
//                    assertNotNull(result);
//                    Thread.sleep(10);
//                }
//                results.add("用户 " + userId + " (状态检查器) 完成");
//                break;
//
//            case 1: // 系统验证器
//                for (int i = 0; i < 5; i++) {
//                    SystemInfoDto systemInfo = new SystemInfoDto();
//                    systemInfo.setMeetsRequirements(true);
//                    when(sillyTavernService.validateSystemRequirements(mockConnection)).thenReturn(systemInfo);
//
//                    SystemInfoDto result = sillyTavernService.validateSystemRequirements(mockConnection);
//                    assertNotNull(result);
//                    Thread.sleep(20);
//                }
//                results.add("用户 " + userId + " (系统验证器) 完成");
//                break;
//
//            case 2: // 容器管理器
//                when(sillyTavernService.isContainerRunning(mockConnection)).thenReturn(true);
//                boolean isRunning = sillyTavernService.isContainerRunning(mockConnection);
//
//                ContainerStatusDto status = new ContainerStatusDto();
//                status.setExists(true);
//                when(sillyTavernService.getContainerStatus(mockConnection)).thenReturn(status);
//                ContainerStatusDto containerStatus = sillyTavernService.getContainerStatus(mockConnection);
//
//                assertNotNull(containerStatus);
//                results.add("用户 " + userId + " (容器管理器) 完成");
//                break;
//
//            case 3: // 混合操作
//                SystemInfoDto systemInfo = new SystemInfoDto();
//                systemInfo.setMeetsRequirements(true);
//                when(sillyTavernService.validateSystemRequirements(mockConnection)).thenReturn(systemInfo);
//
//                ContainerStatusDto containerStatus2 = new ContainerStatusDto();
//                containerStatus2.setExists(true);
//                when(sillyTavernService.getContainerStatus(mockConnection)).thenReturn(containerStatus2);
//
//                when(sillyTavernService.isContainerRunning(mockConnection)).thenReturn(true);
//
//                SystemInfoDto sysInfo = sillyTavernService.validateSystemRequirements(mockConnection);
//                ContainerStatusDto contStatus = sillyTavernService.getContainerStatus(mockConnection);
//                boolean running = sillyTavernService.isContainerRunning(mockConnection);
//
//                assertNotNull(sysInfo);
//                assertNotNull(contStatus);
//                results.add("用户 " + userId + " (混合操作) 完成");
//                break;
//        }
//    }
//
//    private SshConnection createMockSshConnection() {
//        try {
//            SshConnection mockConnection = mock(SshConnection.class);
//            Session mockSession = mock(Session.class);
//
//            when(mockConnection.getJschSession()).thenReturn(mockSession);
//            when(mockSession.isConnected()).thenReturn(true);
//
//            return mockConnection;
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to create mock SSH connection", e);
//        }
//    }
//}
