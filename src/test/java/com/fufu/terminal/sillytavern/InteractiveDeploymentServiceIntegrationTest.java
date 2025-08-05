package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.InteractiveDeploymentDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fufu.terminal.service.sillytavern.*;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InteractiveDeploymentService 深度集成测试
 * 
 * 专注测试核心Docker安装缺失修复功能和完整部署流程：
 * - Docker Missing → Auto-Install → Deploy 流程验证
 * - 分步交互式部署完整流程测试
 * - WebSocket会话管理和状态同步
 * - 错误恢复和用户交互处理
 * 
 * 验证82/100代码审查中标识的关键改进点在实际部署场景中的表现
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("交互式部署服务深度集成测试")
class InteractiveDeploymentServiceIntegrationTest {

    private InteractiveDeploymentService deploymentService;

    @Mock
    private GeolocationDetectionService geolocationService;

    @Mock
    private PackageManagerService packageManagerService;

    @Mock
    private DockerInstallationService dockerInstallationService;

    @Mock
    private DockerMirrorService dockerMirrorService;

    @Mock
    private SillyTavernDeploymentService sillyTavernDeploymentService;

    @Mock
    private ExternalAccessService externalAccessService;

    @Mock
    private ServiceValidationService validationService;

    @Mock
    private SystemDetectionService systemDetectionService;

    @Mock
    private SystemConfigurationService systemConfigurationService;

    @Mock
    private SshCommandService sshCommandService;

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
            sshCommandService,
            messagingTemplate
        );
    }

    // ===== Docker安装缺失修复核心集成测试 =====

    @Test
    @DisplayName("完整验证Docker缺失自动安装部署流程 - 关键修复集成测试")
    void testCompleteDockerMissingAutoInstallDeploymentFlow() throws Exception {
        // Given - 完整的部署场景：中国服务器 + Ubuntu + Docker未安装
        String sessionId = "docker-missing-auto-install-session-001";
        
        InteractiveDeploymentDto.RequestDto deploymentRequest = InteractiveDeploymentDto.RequestDto.builder()
                .deploymentMode("trust")
                .autoConfirmAll(true)
                .containerName("sillytavern")
                .port("8000")
                .build();

        // 1. Mock 地理位置检测 - 中国大陆
        GeolocationDetectionService.GeolocationInfo chineseGeo = 
                GeolocationDetectionService.GeolocationInfo.builder()
                .countryCode("CN")
                .countryName("China")
                .useChineseMirror(true)
                .detectionMethod("IP API")
                .build();

        when(geolocationService.detectGeolocation(eq(sshConnection), any()))
                .thenReturn(CompletableFuture.completedFuture(chineseGeo));

        // 2. Mock 系统检测 - Ubuntu 22.04
        SystemDetectionService.SystemInfo ubuntuSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .osVersionId("22.04")
                .osVersionCodename("jammy")
                .hasRootAccess(true)
                .dockerInstalled(false)  // 关键：Docker未安装
                .cpuCores(2)
                .totalMemoryMB(2048L)
                .availableMemoryMB(1024L)
                .availableDiskSpaceMB(10240L)
                .build();

        when(systemDetectionService.detectSystemEnvironmentSync(sshConnection))
                .thenReturn(ubuntuSystem);

        // 3. Mock 系统配置成功
        SystemConfigurationService.SystemConfigResult configResult = 
                SystemConfigurationService.SystemConfigResult.builder()
                .success(true)
                .message("系统镜像源配置完成")
                .geolocationInfo(chineseGeo)
                .systemInfo(ubuntuSystem)
                .skipped(false)
                .build();

        when(systemConfigurationService.configureSystemMirrors(eq(sshConnection), any()))
                .thenReturn(CompletableFuture.completedFuture(configResult));

        // 4. Mock Docker检查 - 未安装
        DockerInstallationService.DockerInstallationStatus dockerNotInstalled = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(false)
                .version("未安装")
                .serviceRunning(false)
                .message("Docker 未安装")
                .build();

        when(dockerInstallationService.checkDockerInstallation(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(dockerNotInstalled));

        // 5. Mock Docker自动安装成功 - 关键修复点
        DockerInstallationService.DockerInstallationResult dockerInstallSuccess = 
                DockerInstallationService.DockerInstallationResult.builder()
                .success(true)
                .message("Docker安装成功")
                .installedVersion("Docker version 24.0.7, build 297e128")
                .installationMethod("APT 官方仓库")
                .build();

        when(dockerInstallationService.installDocker(eq(sshConnection), eq(ubuntuSystem), eq(true), any()))
                .thenReturn(CompletableFuture.completedFuture(dockerInstallSuccess));

        // 6. Mock Docker安装后验证 - 成功运行
        DockerInstallationService.DockerInstallationStatus dockerInstalled = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(true)
                .version("Docker version 24.0.7, build 297e128")
                .serviceRunning(true)
                .message("Docker 已安装且运行正常")
                .build();

        when(dockerInstallationService.checkDockerInstallation(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(dockerNotInstalled))
                .thenReturn(CompletableFuture.completedFuture(dockerInstalled));

        // 7. Mock Docker镜像源配置
        DockerMirrorService.DockerMirrorConfigResult mirrorResult = 
                DockerMirrorService.DockerMirrorConfigResult.builder()
                .success(true)
                .message("Docker镜像加速器配置完成")
                .mirrorUrl("https://registry.cn-hangzhou.aliyuncs.com")
                .build();

        when(dockerMirrorService.configureMirror(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(mirrorResult));

        // 8. Mock SillyTavern部署成功
        SillyTavernDeploymentService.SillyTavernDeploymentResult deployResult = 
                SillyTavernDeploymentService.SillyTavernDeploymentResult.builder()
                .success(true)
                .message("SillyTavern部署完成")
                .containerId("sillytavern-container-123")
                .accessUrl("http://localhost:8000")
                .build();

        when(sillyTavernDeploymentService.deploySillyTavern(eq(sshConnection), any(), eq(true), any()))
                .thenReturn(CompletableFuture.completedFuture(deployResult));

        // 9. Mock 外网访问配置
        ExternalAccessService.ExternalAccessConfigResult accessResult = 
                ExternalAccessService.ExternalAccessConfigResult.builder()
                .success(true)
                .message("外网访问配置完成")
                .username("admin")
                .password("password123")
                .externalUrl("http://your-server:8000")
                .build();

        when(externalAccessService.configureExternalAccess(eq(sshConnection), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(accessResult));

        // 10. Mock 服务验证成功
        ServiceValidationService.ServiceValidationResult validationResult = 
                ServiceValidationService.ServiceValidationResult.builder()
                .success(true)
                .message("服务验证通过")
                .containerRunning(true)
                .portListening(true)
                .httpResponsive(true)
                .build();

        when(validationService.validateDeployment(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(validationResult));

        // When - 启动增强版交互式部署（包含Docker自动安装功能）
        CompletableFuture<Void> deploymentFuture = 
                deploymentService.startEnhancedInteractiveDeployment(sessionId, sshConnection, deploymentRequest);

        // Then - 验证完整部署流程成功
        assertDoesNotThrow(() -> deploymentFuture.get(30, TimeUnit.SECONDS), 
                "Docker自动安装部署流程应该成功完成");

        // 验证最终部署状态
        InteractiveDeploymentDto.StatusDto finalStatus = deploymentService.getDeploymentStatus(sessionId);
        assertNotNull(finalStatus, "应该有最终部署状态");
        assertTrue(finalStatus.isCompleted(), "部署应该完成");
        assertTrue(finalStatus.isSuccess(), "部署应该成功");

        // 验证关键服务调用序列：地理位置 → 系统检测 → 配置 → Docker检查 → Docker安装 → 部署
        verify(geolocationService).detectGeolocation(eq(sshConnection), any());
        verify(systemDetectionService, atLeast(1)).detectSystemEnvironmentSync(sshConnection);
        verify(systemConfigurationService).configureSystemMirrors(eq(sshConnection), any());
        verify(dockerInstallationService, times(2)).checkDockerInstallation(sshConnection); // 安装前后各一次
        verify(dockerInstallationService).installDocker(eq(sshConnection), eq(ubuntuSystem), eq(true), any()); // 关键：自动安装
        verify(dockerMirrorService).configureMirror(sshConnection);
        verify(sillyTavernDeploymentService).deploySillyTavern(eq(sshConnection), any(), eq(true), any());
        verify(externalAccessService).configureExternalAccess(eq(sshConnection), any(), any());
        verify(validationService).validateDeployment(sshConnection);

        // 验证WebSocket消息发送
        verify(messagingTemplate, atLeast(5)).convertAndSend(contains("sillytavern/interactive-deployment"), any());
    }

    @Test
    @DisplayName("验证Docker服务停止时自动启动并继续部署")
    void testDockerServiceStoppedAutoStartAndContinueDeployment() throws Exception {
        // Given - Docker已安装但服务停止的场景
        String sessionId = "docker-service-stopped-session-002";
        
        InteractiveDeploymentDto.RequestDto deploymentRequest = InteractiveDeploymentDto.RequestDto.builder()
                .deploymentMode("trust")
                .autoConfirmAll(true)
                .containerName("sillytavern")
                .port("8000")
                .build();

        // Mock系统信息 - CentOS系统
        SystemDetectionService.SystemInfo centosSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("centos")
                .osVersionId("7")
                .hasRootAccess(true)
                .dockerInstalled(true)
                .build();

        when(systemDetectionService.detectSystemEnvironmentSync(sshConnection))
                .thenReturn(centosSystem);

        // Mock Docker已安装但服务停止
        DockerInstallationService.DockerInstallationStatus dockerStopped = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(true)
                .version("Docker version 24.0.7")
                .serviceRunning(false)  // 关键：服务未运行
                .message("Docker 已安装但服务未启动")
                .build();

        when(dockerInstallationService.checkDockerInstallation(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(dockerStopped));

        // Mock Docker服务启动成功
        CommandResult startServiceResult = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(eq(jschSession), contains("systemctl start docker")))
                .thenReturn(startServiceResult);

        // Mock其他服务成功响应
        mockSuccessfulDeploymentServices();

        // When - 启动部署
        CompletableFuture<Void> deploymentFuture = 
                deploymentService.startInteractiveDeployment(sessionId, sshConnection, deploymentRequest);

        // Then - 验证能够自动启动Docker服务并继续部署
        assertDoesNotThrow(() -> deploymentFuture.get(20, TimeUnit.SECONDS));

        InteractiveDeploymentDto.StatusDto finalStatus = deploymentService.getDeploymentStatus(sessionId);
        assertTrue(finalStatus.isCompleted(), "部署应该完成");
        assertTrue(finalStatus.isSuccess(), "部署应该成功");

        // 验证Docker检查被调用
        verify(dockerInstallationService).checkDockerInstallation(sshConnection);
        // 验证Docker服务启动命令被执行
        verify(sshCommandService).executeCommand(eq(jschSession), contains("systemctl start docker"));
    }

    @Test
    @DisplayName("验证确认模式下的分步用户交互流程")
    void testConfirmationModeStepByStepUserInteractionFlow() throws Exception {
        // Given - 确认模式部署请求
        String sessionId = "confirmation-mode-session-003";
        
        InteractiveDeploymentDto.RequestDto confirmModeRequest = InteractiveDeploymentDto.RequestDto.builder()
                .deploymentMode("confirmation")
                .autoConfirmAll(false)
                .containerName("sillytavern")
                .port("8000")
                .build();

        // Mock基础服务响应
        mockBasicDeploymentServices();

        // When - 启动确认模式部署
        CompletableFuture<Void> deploymentFuture = 
                deploymentService.startInteractiveDeployment(sessionId, sshConnection, confirmModeRequest);

        // 等待部署开始并到达第一个确认点
        Thread.sleep(500);

        // Then - 验证部署处于等待确认状态
        InteractiveDeploymentDto.StatusDto status = deploymentService.getDeploymentStatus(sessionId);
        assertNotNull(status, "应该有部署状态");
        assertTrue(status.isRunning(), "部署应该正在运行");
        assertFalse(status.isCompleted(), "部署不应该完成（等待确认）");

        // 模拟用户确认第一步
        InteractiveDeploymentDto.ConfirmationDto userConfirmation = 
                InteractiveDeploymentDto.ConfirmationDto.builder()
                .stepId("geolocation_detection")
                .action("confirm")
                .reason("用户同意执行地理位置检测")
                .build();

        deploymentService.handleUserConfirmation(sessionId, userConfirmation);

        // 等待处理用户确认
        Thread.sleep(500);

        // 验证确认请求被发送到前端
        verify(messagingTemplate, atLeast(1)).convertAndSend(
                eq("/queue/sillytavern/interactive-deployment-confirmation-user" + sessionId), any());

        // 验证进度更新被发送
        verify(messagingTemplate, atLeast(3)).convertAndSend(
                eq("/queue/sillytavern/interactive-deployment-progress-user" + sessionId), any());

        // 清理异步任务
        deploymentFuture.cancel(true);
    }

    @Test
    @DisplayName("验证部署过程中错误恢复和用户选择处理")
    void testDeploymentErrorRecoveryAndUserChoiceHandling() throws Exception {
        // Given - 会发生错误的部署场景
        String sessionId = "error-recovery-session-004";
        
        InteractiveDeploymentDto.RequestDto deploymentRequest = InteractiveDeploymentDto.RequestDto.builder()
                .deploymentMode("confirmation")
                .autoConfirmAll(false)
                .containerName("sillytavern")
                .port("8000")
                .build();

        // Mock基础服务
        mockBasicDeploymentServices();

        // Mock Docker安装失败（权限问题）
        SystemDetectionService.SystemInfo noSudoSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(false)  // 关键：没有sudo权限
                .dockerInstalled(false)
                .build();

        when(systemDetectionService.detectSystemEnvironmentSync(sshConnection))
                .thenReturn(noSudoSystem);

        DockerInstallationService.DockerInstallationStatus dockerNotInstalled = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(false)
                .version("未安装")
                .serviceRunning(false)
                .message("Docker 未安装")
                .build();

        when(dockerInstallationService.checkDockerInstallation(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(dockerNotInstalled));

        DockerInstallationService.DockerInstallationResult installFailure = 
                DockerInstallationService.DockerInstallationResult.builder()
                .success(false)
                .message("安装失败: 需要sudo权限才能安装Docker")
                .installedVersion("未安装")
                .installationMethod(null)
                .build();

        when(dockerInstallationService.installDocker(eq(sshConnection), eq(noSudoSystem), anyBoolean(), any()))
                .thenReturn(CompletableFuture.completedFuture(installFailure));

        // When - 启动部署
        CompletableFuture<Void> deploymentFuture = 
                deploymentService.startInteractiveDeployment(sessionId, sshConnection, deploymentRequest);

        // 等待部署进行到Docker安装步骤并失败
        Thread.sleep(1000);

        // 模拟用户选择跳过失败的步骤
        InteractiveDeploymentDto.ConfirmationDto skipConfirmation = 
                InteractiveDeploymentDto.ConfirmationDto.builder()
                .stepId("docker_installation")
                .action("skip")
                .reason("跳过Docker安装，手动处理权限问题")
                .build();

        deploymentService.handleUserConfirmation(sessionId, skipConfirmation);

        // Then - 验证错误处理流程
        Thread.sleep(500);

        InteractiveDeploymentDto.StatusDto status = deploymentService.getDeploymentStatus(sessionId);
        assertNotNull(status, "应该有部署状态");

        // 验证错误确认请求被发送
        verify(messagingTemplate, atLeast(1)).convertAndSend(
                eq("/queue/sillytavern/interactive-deployment-confirmation-user" + sessionId), any());

        // 验证Docker安装尝试和失败
        verify(dockerInstallationService).installDocker(eq(sshConnection), eq(noSudoSystem), anyBoolean(), any());

        // 清理异步任务
        deploymentFuture.cancel(true);
    }

    @Test
    @DisplayName("验证不同Linux发行版的部署适配性")
    void testMultiLinuxDistributionDeploymentAdaptability() throws Exception {
        // 测试多个Linux发行版的部署适配
        String[] distributions = {"ubuntu", "centos", "debian", "fedora", "arch"};
        
        for (String distro : distributions) {
            String sessionId = "multi-distro-session-" + distro;
            
            // Given - 特定发行版系统环境
            SystemDetectionService.SystemInfo distroSystem = SystemDetectionService.SystemInfo.builder()
                    .osType("Linux")
                    .osId(distro)
                    .hasRootAccess(true)
                    .dockerInstalled(false)
                    .build();

            when(systemDetectionService.detectSystemEnvironmentSync(sshConnection))
                    .thenReturn(distroSystem);

            // Mock Docker安装成功（针对不同发行版）
            String expectedMethod = getExpectedInstallationMethod(distro);
            DockerInstallationService.DockerInstallationResult installResult = 
                    DockerInstallationService.DockerInstallationResult.builder()
                    .success(true)
                    .message("Docker安装成功")
                    .installedVersion("Docker version 24.0.7")
                    .installationMethod(expectedMethod)
                    .build();

            when(dockerInstallationService.installDocker(eq(sshConnection), eq(distroSystem), anyBoolean(), any()))
                    .thenReturn(CompletableFuture.completedFuture(installResult));

            DockerInstallationService.DockerInstallationStatus dockerNotInstalled = 
                    DockerInstallationService.DockerInstallationStatus.builder()
                    .installed(false)
                    .version("未安装")
                    .serviceRunning(false)
                    .message("Docker 未安装")
                    .build();

            when(dockerInstallationService.checkDockerInstallation(sshConnection))
                    .thenReturn(CompletableFuture.completedFuture(dockerNotInstalled));

            // Mock其他服务
            mockSuccessfulDeploymentServices();

            InteractiveDeploymentDto.RequestDto deploymentRequest = InteractiveDeploymentDto.RequestDto.builder()
                    .deploymentMode("trust")
                    .autoConfirmAll(true)
                    .containerName("sillytavern")
                    .port("8000")
                    .build();

            // When - 执行部署
            CompletableFuture<Void> deploymentFuture = 
                    deploymentService.startInteractiveDeployment(sessionId, sshConnection, deploymentRequest);

            // Then - 验证部署成功
            assertDoesNotThrow(() -> deploymentFuture.get(15, TimeUnit.SECONDS), 
                    "发行版 " + distro + " 的部署应该成功");

            InteractiveDeploymentDto.StatusDto finalStatus = deploymentService.getDeploymentStatus(sessionId);
            assertTrue(finalStatus.isSuccess(), "发行版 " + distro + " 部署应该成功");

            // 验证Docker安装被调用
            verify(dockerInstallationService).installDocker(eq(sshConnection), eq(distroSystem), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("验证部署会话并发管理和状态隔离")
    void testConcurrentDeploymentSessionManagementAndStateIsolation() throws Exception {
        // Given - 多个并发部署会话
        int concurrentSessions = 3;
        String[] sessionIds = {"concurrent-session-1", "concurrent-session-2", "concurrent-session-3"};
        
        // Mock基础服务
        mockSuccessfulDeploymentServices();

        List<CompletableFuture<Void>> deploymentFutures = new ArrayList<>();

        // When - 启动多个并发部署会话
        for (String sessionId : sessionIds) {
            InteractiveDeploymentDto.RequestDto deploymentRequest = InteractiveDeploymentDto.RequestDto.builder()
                    .deploymentMode("trust")
                    .autoConfirmAll(true)
                    .containerName("sillytavern-" + sessionId.substring(sessionId.length() - 1))
                    .port("800" + sessionId.substring(sessionId.length() - 1))
                    .build();

            CompletableFuture<Void> future = 
                    deploymentService.startInteractiveDeployment(sessionId, sshConnection, deploymentRequest);
            deploymentFutures.add(future);
        }

        // 等待所有部署完成
        CompletableFuture<Void> allDeployments = CompletableFuture.allOf(
                deploymentFutures.toArray(new CompletableFuture[0]));

        // Then - 验证所有并发部署都成功完成
        assertDoesNotThrow(() -> allDeployments.get(30, TimeUnit.SECONDS), 
                "所有并发部署会话都应该成功完成");

        // 验证每个会话的状态都是独立的
        for (String sessionId : sessionIds) {
            InteractiveDeploymentDto.StatusDto status = deploymentService.getDeploymentStatus(sessionId);
            assertNotNull(status, "会话 " + sessionId + " 应该有状态");
            assertEquals(sessionId, status.getSessionId(), "会话ID应该匹配");
            assertTrue(status.isCompleted(), "会话 " + sessionId + " 应该完成");
            assertTrue(status.isSuccess(), "会话 " + sessionId + " 应该成功");
        }

        // 验证WebSocket消息发送次数合理（每个会话都有独立的消息）
        verify(messagingTemplate, atLeast(concurrentSessions * 5)).convertAndSend(anyString(), any());
    }

    // ===== 辅助方法 =====

    /**
     * Mock基础部署服务的成功响应
     */
    private void mockBasicDeploymentServices() {
        // Mock地理位置检测
        GeolocationDetectionService.GeolocationInfo geoInfo = 
                GeolocationDetectionService.GeolocationInfo.builder()
                .countryCode("US")
                .useChineseMirror(false)
                .build();

        when(geolocationService.detectGeolocation(eq(sshConnection), any()))
                .thenReturn(CompletableFuture.completedFuture(geoInfo));

        // Mock系统信息
        SystemDetectionService.SystemInfo systemInfo = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(true)
                .dockerInstalled(true)
                .build();

        when(systemDetectionService.detectSystemEnvironmentSync(sshConnection))
                .thenReturn(systemInfo);

        // Mock系统配置
        SystemConfigurationService.SystemConfigResult configResult = 
                SystemConfigurationService.SystemConfigResult.builder()
                .success(true)
                .message("配置完成")
                .skipped(true)
                .geolocationInfo(geoInfo)
                .build();

        when(systemConfigurationService.configureSystemMirrors(eq(sshConnection), any()))
                .thenReturn(CompletableFuture.completedFuture(configResult));

        // Mock Docker检查 - 运行正常
        DockerInstallationService.DockerInstallationStatus dockerRunning = 
                DockerInstallationService.DockerInstallationStatus.builder()
                .installed(true)
                .version("Docker version 24.0.7")
                .serviceRunning(true)
                .message("Docker 已安装且运行正常")
                .build();

        when(dockerInstallationService.checkDockerInstallation(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(dockerRunning));
    }

    /**
     * Mock成功的部署服务响应
     */
    private void mockSuccessfulDeploymentServices() {
        mockBasicDeploymentServices();

        // Mock Docker镜像源配置
        DockerMirrorService.DockerMirrorConfigResult mirrorResult = 
                DockerMirrorService.DockerMirrorConfigResult.builder()
                .success(true)
                .message("镜像源配置完成")
                .build();

        when(dockerMirrorService.configureMirror(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(mirrorResult));

        // Mock SillyTavern部署
        SillyTavernDeploymentService.SillyTavernDeploymentResult deployResult = 
                SillyTavernDeploymentService.SillyTavernDeploymentResult.builder()
                .success(true)
                .message("部署完成")
                .containerId("sillytavern-123")
                .accessUrl("http://localhost:8000")
                .build();

        when(sillyTavernDeploymentService.deploySillyTavern(eq(sshConnection), any(), anyBoolean(), any()))
                .thenReturn(CompletableFuture.completedFuture(deployResult));

        // Mock外网访问配置
        ExternalAccessService.ExternalAccessConfigResult accessResult = 
                ExternalAccessService.ExternalAccessConfigResult.builder()
                .success(true)
                .message("外网访问配置完成")
                .username("admin")
                .password("password123")
                .build();

        when(externalAccessService.configureExternalAccess(eq(sshConnection), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(accessResult));

        // Mock服务验证
        ServiceValidationService.ServiceValidationResult validationResult = 
                ServiceValidationService.ServiceValidationResult.builder()
                .success(true)
                .message("验证通过")
                .containerRunning(true)
                .portListening(true)
                .httpResponsive(true)
                .build();

        when(validationService.validateDeployment(sshConnection))
                .thenReturn(CompletableFuture.completedFuture(validationResult));
    }

    /**
     * 根据Linux发行版获取预期的Docker安装方法
     */
    private String getExpectedInstallationMethod(String distro) {
        switch (distro) {
            case "ubuntu":
            case "debian":
                return "APT 官方仓库";
            case "centos":
                return "YUM 官方仓库";
            case "fedora":
                return "DNF 官方仓库";
            case "arch":
                return "Pacman 官方仓库";
            default:
                return "未知安装方法";
        }
    }
}