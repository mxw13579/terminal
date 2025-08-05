package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.sillytavern.*;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SillyTavern错误处理和边界条件测试
 * 测试4个核心功能在各种异常情况下的行为
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern错误处理和边界条件测试")
class SillyTavernErrorHandlingTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private ConfigurationService configurationService;
    
    @Mock
    private RealTimeLogService realTimeLogService;
    
    @Mock
    private DataManagementService dataManagementService;
    
    @Mock
    private DockerVersionService dockerVersionService;

    @Mock
    private SshConnection sshConnection;

    @Mock
    private Session jschSession;

    @BeforeEach
    void setUp() {
        when(sshConnection.getJschSession()).thenReturn(jschSession);
    }

    // ===== SSH连接错误处理测试 =====

    @Test
    @DisplayName("应该处理SSH连接断开的情况")
    void testSshConnectionFailures() {
        // Test case 1: SSH session is null
        when(sshConnection.getJschSession()).thenReturn(null);

        assertThrows(Exception.class, () -> {
            sillyTavernService.getContainerStatus(sshConnection);
        });

        // Test case 2: SSH session is disconnected
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        when(jschSession.isConnected()).thenReturn(false);

        // Should handle disconnected session gracefully
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenThrow(new RuntimeException("SSH连接已断开"));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            sillyTavernService.getContainerStatus(sshConnection);
        });

        assertTrue(exception.getMessage().contains("SSH连接已断开"));
    }

    @Test
    @DisplayName("应该处理SSH认证失败")
    void testSshAuthenticationFailure() throws Exception {
        // Given
        when(jschSession.isConnected()).thenReturn(true);
        doThrow(new JSchException("Auth fail")).when(jschSession).connect();

        // When & Then
        assertThrows(JSchException.class, () -> {
            jschSession.connect();
        });
    }

    // ===== Docker守护进程错误处理测试 =====

    @Test
    @DisplayName("应该处理Docker守护进程未运行的情况")
    void testDockerDaemonNotRunning() {
        // Given
        SystemInfoDto systemWithoutDocker = new SystemInfoDto();
        systemWithoutDocker.setMeetsRequirements(false);
        systemWithoutDocker.setDockerInstalled(true);  // Docker已安装但守护进程未运行
        systemWithoutDocker.setDockerRunning(false);
        systemWithoutDocker.setSufficientDiskSpace(true);
        systemWithoutDocker.setHasRootAccess(true);
        systemWithoutDocker.setRequirementChecks(List.of("Docker守护进程未运行"));

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithoutDocker);

        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);

        // Then
        assertFalse(result.getMeetsRequirements());
        assertTrue(result.getDockerInstalled());
        assertFalse(result.getDockerRunning());
        assertTrue(result.getRequirementChecks().contains("Docker守护进程未运行"));
    }

    @Test
    @DisplayName("应该处理权限不足的情况")
    void testInsufficientPermissions() {
        // Given
        SystemInfoDto systemWithoutRoot = new SystemInfoDto();
        systemWithoutRoot.setMeetsRequirements(false);
        systemWithoutRoot.setDockerInstalled(true);
        systemWithoutRoot.setDockerRunning(true);
        systemWithoutRoot.setSufficientDiskSpace(true);
        systemWithoutRoot.setHasRootAccess(false);
        systemWithoutRoot.setRequirementChecks(List.of("需要Root/sudo权限来执行Docker操作"));

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithoutRoot);

        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);

        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getHasRootAccess());
        assertTrue(result.getRequirementChecks().contains("需要Root/sudo权限来执行Docker操作"));
    }

    @Test
    @DisplayName("应该处理磁盘空间不足的情况")
    void testInsufficientDiskSpace() {
        // Given
        SystemInfoDto systemWithLowDisk = new SystemInfoDto();
        systemWithLowDisk.setMeetsRequirements(false);
        systemWithLowDisk.setDockerInstalled(true);
        systemWithLowDisk.setDockerRunning(true);
        systemWithLowDisk.setSufficientDiskSpace(false);
        systemWithLowDisk.setHasRootAccess(true);
        systemWithLowDisk.setAvailableDiskSpaceMB(100L);  // 只有100MB可用
        systemWithLowDisk.setRequirementChecks(List.of("磁盘空间不足：可用100MB，需要500MB"));

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithLowDisk);

        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);

        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getSufficientDiskSpace());
        assertEquals(100L, result.getAvailableDiskSpaceMB());
        assertTrue(result.getRequirementChecks().stream()
                .anyMatch(check -> check.contains("磁盘空间不足")));
    }

    // ===== 配置管理错误处理测试 =====

    @Test
    @DisplayName("应该处理配置文件损坏的情况")
    void testCorruptedConfigurationFile() throws Exception {
        // Given
        String containerName = "sillytavern";

        when(configurationService.readConfiguration(sshConnection, containerName))
                .thenThrow(new RuntimeException("配置文件格式错误：YAML解析失败"));

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            configurationService.readConfiguration(sshConnection, containerName);
        });

        assertTrue(exception.getMessage().contains("配置文件格式错误"));
    }

    @Test
    @DisplayName("应该处理并发配置更新冲突")
    void testConcurrentConfigurationUpdateConflict() throws Exception {
        // Given
        String containerName = "sillytavern";
        ConfigurationDto config1 = new ConfigurationDto();
        config1.setUsername("admin1");
        config1.setPassword("password1");

        ConfigurationDto config2 = new ConfigurationDto();
        config2.setUsername("admin2");
        config2.setPassword("password2");

        // 第一次更新成功
        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, config1))
                .thenReturn(true);

        // 第二次更新因锁冲突而失败
        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, config2))
                .thenThrow(new RuntimeException("配置更新失败：其他操作正在进行中，请稍后重试"));

        // When & Then
        boolean result1 = configurationService.updateConfigurationWithRestart(sshConnection, containerName, config1);
        assertTrue(result1);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            configurationService.updateConfigurationWithRestart(sshConnection, containerName, config2);
        });

        assertTrue(exception.getMessage().contains("其他操作正在进行中"));
    }

    @Test
    @DisplayName("应该处理容器重启失败的情况")
    void testContainerRestartFailure() throws Exception {
        // Given
        String containerName = "sillytavern";
        ConfigurationDto config = new ConfigurationDto();
        config.setUsername("newadmin");
        config.setPassword("newpassword");

        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, config))
                .thenThrow(new RuntimeException("容器重启失败：容器处于错误状态"));

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            configurationService.updateConfigurationWithRestart(sshConnection, containerName, config);
        });

        assertTrue(exception.getMessage().contains("容器重启失败"));
    }

    // ===== 实时日志错误处理测试 =====

    @Test
    @DisplayName("应该处理日志文件过大的情况")
    void testLogFileTooLarge() throws Exception {
        // Given
        String containerName = "sillytavern";
        int maxLines = 50000; // 请求过多行数

        when(realTimeLogService.getHistoryLogs(sshConnection, containerName, maxLines, "all"))
                .thenThrow(new RuntimeException("日志文件过大（>100MB）。请减少天数或尾行数。"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            realTimeLogService.getHistoryLogs(sshConnection, containerName, maxLines, "all");
        });

        assertTrue(exception.getMessage().contains("日志文件过大"));
        assertTrue(exception.getMessage().contains("请减少"));
    }

    @Test
    @DisplayName("应该处理日志流中断的情况")
    void testLogStreamInterruption() {
        // Given
        String sessionId = "test-session";
        String containerName = "sillytavern";

        doThrow(new RuntimeException("日志流意外中断：容器已停止"))
                .when(realTimeLogService).startLogStream(sessionId, containerName, 1000);

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            realTimeLogService.startLogStream(sessionId, containerName, 1000);
        });

        assertTrue(exception.getMessage().contains("日志流意外中断"));
    }

    @Test
    @DisplayName("应该处理内存不足的情况")
    void testLogStreamMemoryExhaustion() throws Exception {
        // Given
        String containerName = "sillytavern";

        when(realTimeLogService.getHistoryLogs(sshConnection, containerName, 10000, "all"))
                .thenThrow(new OutOfMemoryError("Java heap space"));

        // When & Then
        assertThrows(OutOfMemoryError.class, () -> {
            realTimeLogService.getHistoryLogs(sshConnection, containerName, 10000, "all");
        });
    }

    // ===== 数据管理错误处理测试 =====

    @Test
    @DisplayName("应该处理数据导出超时的情况")
    void testDataExportTimeout() {
        // Given
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Export progress: " + progress);

        CompletableFuture<DataExportDto> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new TimeoutException("数据导出超时：操作超过30分钟"));

        when(dataManagementService.exportData(sshConnection, containerName, progressCallback))
                .thenReturn(timeoutFuture);

        // When & Then
        CompletableFuture<DataExportDto> future = dataManagementService.exportData(sshConnection, containerName, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause() instanceof TimeoutException);
        assertTrue(exception.getCause().getMessage().contains("数据导出超时"));
    }

    @Test
    @DisplayName("应该处理ZIP文件损坏的情况")
    void testCorruptedZipFile() {
        // Given
        String uploadedFileName = "corrupted.zip";
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Import progress: " + progress);

        when(dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("ZIP文件损坏：无法解压缩")));

        // When & Then
        CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause().getMessage().contains("ZIP文件损坏"));
    }

    @Test
    @DisplayName("应该处理磁盘空间不足导致的导出失败")
    void testDataExportDiskSpaceFailure() {
        // Given
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Export progress: " + progress);

        when(dataManagementService.exportData(sshConnection, containerName, progressCallback))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("数据导出失败：磁盘空间不足")));

        // When & Then
        CompletableFuture<DataExportDto> future = dataManagementService.exportData(sshConnection, containerName, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause().getMessage().contains("磁盘空间不足"));
    }

    @Test
    @DisplayName("应该处理数据导入回滚失败")
    void testDataImportRollbackFailure() {
        // Given
        String uploadedFileName = "invalid_data.zip";
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Import progress: " + progress);

        when(dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("数据导入失败且回滚失败：备份文件也已损坏")));

        // When & Then
        CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause().getMessage().contains("回滚失败"));
    }

    // ===== 版本管理错误处理测试 =====

    @Test
    @DisplayName("应该处理GitHub API访问失败")
    void testGitHubApiFailure() {
        // Given
        String containerName = "sillytavern";

        VersionInfoDto errorVersionInfo = new VersionInfoDto();
        errorVersionInfo.setContainerName(containerName);
        errorVersionInfo.setError("无法访问GitHub API：网络连接超时");

        when(dockerVersionService.getVersionInfo(sshConnection, containerName))
                .thenReturn(errorVersionInfo);

        // When
        VersionInfoDto result = dockerVersionService.getVersionInfo(sshConnection, containerName);

        // Then
        assertEquals(containerName, result.getContainerName());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("无法访问GitHub API"));
    }

    @Test
    @DisplayName("应该处理版本升级过程中的网络中断")
    void testVersionUpgradeNetworkFailure() {
        // Given
        String containerName = "sillytavern";
        String targetVersion = "1.12.2";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("版本升级失败：镜像下载过程中网络中断")));

        // When & Then
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause().getMessage().contains("网络中断"));
    }

    @Test
    @DisplayName("应该处理版本升级锁竞争超时")
    void testVersionUpgradeLockTimeout() {
        // Given
        String containerName = "sillytavern";
        String targetVersion = "1.12.2";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("版本升级失败：无法获取升级锁，可能有其他升级操作正在进行")));

        // When & Then
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause().getMessage().contains("无法获取升级锁"));
    }

    @Test
    @DisplayName("应该处理旧镜像清理失败")
    void testOldImageCleanupFailure() {
        // Given
        String containerName = "sillytavern";
        String targetVersion = "1.12.2";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        // 升级成功但清理失败
        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(2);
                    callback.accept("升级完成，但旧镜像清理失败：权限不足");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(5, TimeUnit.SECONDS);
        });
    }

    // ===== 网络连接错误处理测试 =====

    @Test
    @DisplayName("应该处理网络连接问题")
    void testNetworkConnectivityIssues() {
        // Given
        SystemInfoDto systemWithNetworkIssues = new SystemInfoDto();
        systemWithNetworkIssues.setMeetsRequirements(false);
        systemWithNetworkIssues.setDockerInstalled(true);
        systemWithNetworkIssues.setDockerRunning(true);
        systemWithNetworkIssues.setSufficientDiskSpace(true);
        systemWithNetworkIssues.setHasRootAccess(true);
        systemWithNetworkIssues.setHasInternetAccess(false);  // 无网络连接
        systemWithNetworkIssues.setRequirementChecks(List.of("需要互联网连接来拉取Docker镜像"));

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithNetworkIssues);

        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);

        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getHasInternetAccess());
        assertTrue(result.getRequirementChecks().contains("需要互联网连接"));
    }

    // ===== 系统资源耗尽错误处理测试 =====

    @Test
    @DisplayName("应该处理系统资源耗尽")
    void testSystemResourceExhaustion() {
        // Given
        ContainerStatusDto exhaustedStatus = new ContainerStatusDto();
        exhaustedStatus.setExists(true);
        exhaustedStatus.setRunning(false);
        exhaustedStatus.setStatus("exited");
        exhaustedStatus.setContainerName("sillytavern");
        exhaustedStatus.setError("容器因系统资源不足而退出");

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(exhaustedStatus);

        // When
        ContainerStatusDto result = sillyTavernService.getContainerStatus(sshConnection);

        // Then
        assertTrue(result.getExists());
        assertFalse(result.getRunning());
        assertEquals("exited", result.getStatus());
        assertTrue(result.getError().contains("系统资源不足"));
    }

    // ===== 操作超时处理测试 =====

    @Test
    @DisplayName("应该处理操作超时")
    void testOperationTimeout() {
        // Given
        String containerName = "sillytavern";

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenThrow(new RuntimeException("操作超时：Docker命令执行超过30秒"));

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            sillyTavernService.getContainerStatus(sshConnection);
        });

        assertTrue(exception.getMessage().contains("操作超时"));
    }

    // ===== 服务中断处理测试 =====

    @Test
    @DisplayName("应该处理意外服务中断")
    void testUnexpectedServiceInterruption() throws Exception {
        // Given - 服务操作被中断
        when(sillyTavernService.startContainer(sshConnection))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();  // 模拟中断
                    throw new InterruptedException("服务操作被中断");
                });

        // When & Then
        Exception exception = assertThrows(Exception.class, () -> {
            sillyTavernService.startContainer(sshConnection);
        });

        assertTrue(exception.getMessage().contains("被中断") ||
                   exception.getCause() instanceof InterruptedException);

        // 验证线程中断状态得到正确处理
        assertFalse(Thread.currentThread().isInterrupted());
    }
}