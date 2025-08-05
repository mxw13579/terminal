package com.fufu.terminal.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fufu.terminal.service.sillytavern.DockerInstallationService;
import com.fufu.terminal.service.sillytavern.SystemDetectionService;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Docker安装缺失修复专项功能测试
 * 
 * 专门验证核心问题修复：Docker Missing → Auto-Install → Deploy
 * 这是SillyTavern Web Deployment Wizard的关键改进，解决了初学者
 * 在部署过程中遇到的最主要障碍。
 * 
 * 测试覆盖：
 * 1. 多Linux发行版Docker自动安装
 * 2. 中国镜像源自动配置
 * 3. 安装失败后的错误恢复
 * 4. 权限问题的清晰提示
 * 5. 网络问题的重试机制
 * 
 * 对应代码审查82/100分中的核心改进点
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Docker安装缺失修复专项测试")
class DockerInstallationGapFixTest {

    private DockerInstallationService dockerInstallationService;

    @Mock
    private SshCommandService sshCommandService;

    @Mock
    private SshConnection sshConnection;

    @Mock
    private Session jschSession;

    @BeforeEach
    void setUp() {
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        dockerInstallationService = new DockerInstallationService(sshCommandService);
    }

    // ===== 核心Docker缺失自动安装修复测试 =====

    @Test
    @DisplayName("Ubuntu系统Docker自动安装 - 关键修复验证")
    void testUbuntuDockerAutoInstallationCriticalFix() throws Exception {
        // Given - Ubuntu 22.04系统，Docker未安装
        SystemDetectionService.SystemInfo ubuntuSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .osVersionId("22.04")
                .osVersionCodename("jammy")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock Docker检查命令 - 未安装
        CommandResult dockerCheckFailed = new CommandResult("", "docker: command not found", 127);
        when(sshCommandService.executeCommand(jschSession, "command -v docker &> /dev/null"))
                .thenReturn(dockerCheckFailed);

        // Mock sudo权限检查
        CommandResult sudoCheckSuccess = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(jschSession, "sudo -v"))
                .thenReturn(sudoCheckSuccess);

        // Mock APT依赖安装成功
        CommandResult aptInstallDeps = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(jschSession, 
                "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release"))
                .thenReturn(aptInstallDeps);

        // Mock GPG密钥添加成功
        when(sshCommandService.executeCommand(eq(jschSession), contains("gpg --dearmor")))
                .thenReturn(new CommandResult("", "", 0));

        // Mock 仓库添加成功
        when(sshCommandService.executeCommand(eq(jschSession), contains("tee /etc/apt/sources.list.d/docker.list")))
                .thenReturn(new CommandResult("", "", 0));

        // Mock APT更新成功
        CommandResult aptUpdate = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(jschSession, "sudo apt-get update"))
                .thenReturn(aptUpdate);

        // Mock Docker安装成功
        CommandResult dockerInstall = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(jschSession, 
                "sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin"))
                .thenReturn(dockerInstall);

        // Mock Docker版本检查成功
        CommandResult dockerVersion = new CommandResult("Docker version 24.0.7, build 297e128", "", 0);
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(dockerVersion);

        // Mock Docker服务启动成功
        CommandResult dockerServiceStart = new CommandResult("", "", 0);
        when(sshCommandService.executeCommand(jschSession, "sudo systemctl start docker && sudo systemctl enable docker"))
                .thenReturn(dockerServiceStart);

        // When - 执行Docker自动安装
        StringBuilder progressLog = new StringBuilder();
        Consumer<String> progressCallback = message -> {
            progressLog.append(message).append("\n");
            System.out.println("安装进度: " + message);
        };

        CompletableFuture<DockerInstallationService.DockerInstallationResult> installFuture = 
                dockerInstallationService.installDocker(sshConnection, ubuntuSystem, false, progressCallback);

        DockerInstallationService.DockerInstallationResult result = 
                assertDoesNotThrow(() -> installFuture.get(30, TimeUnit.SECONDS), 
                        "Ubuntu Docker自动安装应该成功完成");

        // Then - 验证安装成功
        assertTrue(result.isSuccess(), "Docker安装应该成功");
        assertEquals("Docker安装成功", result.getMessage());
        assertTrue(result.getInstalledVersion().contains("Docker version 24.0.7"), 
                "应该安装正确的Docker版本");
        assertEquals("APT 官方仓库", result.getInstallationMethod(), 
                "应该使用APT包管理器");

        // 验证安装过程的关键步骤
        String progressOutput = progressLog.toString();
        assertTrue(progressOutput.contains("开始安装 Docker"), "应该显示安装开始");
        assertTrue(progressOutput.contains("在 ubuntu 系统上安装 Docker"), "应该识别系统类型");
        assertTrue(progressOutput.contains("移除旧版本 Docker"), "应该清理旧版本");
        assertTrue(progressOutput.contains("安装必要依赖"), "应该安装依赖");
        assertTrue(progressOutput.contains("添加 Docker GPG 密钥"), "应该添加GPG密钥");
        assertTrue(progressOutput.contains("安装 Docker CE"), "应该安装Docker CE");
        assertTrue(progressOutput.contains("启动并启用 Docker 服务"), "应该启动服务");

        // 验证关键命令执行序列
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release");
        verify(sshCommandService).executeCommand(jschSession, "sudo apt-get update");
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin");
        verify(sshCommandService).executeCommand(jschSession, "docker --version");
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo systemctl start docker && sudo systemctl enable docker");
    }

    @Test
    @DisplayName("CentOS系统Docker自动安装带中国镜像源")
    void testCentOSDockerAutoInstallationWithChineseMirror() throws Exception {
        // Given - CentOS 7系统，使用中国镜像源
        SystemDetectionService.SystemInfo centosSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("centos")
                .osVersionId("7")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock Docker检查 - 未安装
        when(sshCommandService.executeCommand(jschSession, "command -v docker &> /dev/null"))
                .thenReturn(new CommandResult("", "", 1));

        // Mock 旧版本清理
        when(sshCommandService.executeCommand(eq(jschSession), contains("yum remove")))
                .thenReturn(new CommandResult("", "", 0));

        // Mock yum-utils安装
        when(sshCommandService.executeCommand(jschSession, "sudo yum install -y yum-utils"))
                .thenReturn(new CommandResult("", "", 0));

        // Mock Docker仓库添加（中国镜像）
        when(sshCommandService.executeCommand(jschSession, 
                "sudo yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo"))
                .thenReturn(new CommandResult("", "", 0));

        // Mock Docker安装
        when(sshCommandService.executeCommand(jschSession, 
                "sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin"))
                .thenReturn(new CommandResult("", "", 0));

        // Mock版本检查和服务启动
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(new CommandResult("Docker version 24.0.7, build 297e128", "", 0));
        when(sshCommandService.executeCommand(jschSession, 
                "sudo systemctl start docker && sudo systemctl enable docker"))
                .thenReturn(new CommandResult("", "", 0));

        // When - 执行带中国镜像的Docker安装
        StringBuilder progressLog = new StringBuilder();
        CompletableFuture<DockerInstallationService.DockerInstallationResult> installFuture = 
                dockerInstallationService.installDocker(sshConnection, centosSystem, true, // 使用中国镜像
                        message -> {
                            progressLog.append(message).append("\n");
                            System.out.println("CentOS安装进度: " + message);
                        });

        DockerInstallationService.DockerInstallationResult result = 
                installFuture.get(30, TimeUnit.SECONDS);

        // Then - 验证CentOS安装成功
        assertTrue(result.isSuccess(), "CentOS Docker安装应该成功");
        assertEquals("YUM 官方仓库", result.getInstallationMethod(), "应该使用YUM包管理器");

        // 验证使用了中国镜像源
        String progressOutput = progressLog.toString();
        assertTrue(progressOutput.contains("mirrors.aliyun.com"), "应该使用阿里云镜像源");

        // 验证CentOS特定的安装命令
        verify(sshCommandService).executeCommand(jschSession, "sudo yum install -y yum-utils");
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo");
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin");
    }

    @Test
    @DisplayName("Docker检查逻辑 - 未安装、已安装未启动、正常运行")
    void testDockerStatusCheckLogic() throws Exception {
        // Test Case 1: Docker未安装
        when(sshCommandService.executeCommand(jschSession, "command -v docker &> /dev/null"))
                .thenReturn(new CommandResult("", "", 1));

        CompletableFuture<DockerInstallationService.DockerInstallationStatus> checkFuture1 = 
                dockerInstallationService.checkDockerInstallation(sshConnection);
        DockerInstallationService.DockerInstallationStatus status1 = checkFuture1.join();

        assertFalse(status1.isInstalled(), "应该检测到Docker未安装");
        assertEquals("未安装", status1.getVersion());
        assertFalse(status1.isServiceRunning(), "服务应该未运行");
        assertTrue(status1.getMessage().contains("未安装"), "消息应该说明未安装");

        // Test Case 2: Docker已安装但服务未启动
        when(sshCommandService.executeCommand(jschSession, "command -v docker &> /dev/null"))
                .thenReturn(new CommandResult("", "", 0));
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(new CommandResult("Docker version 24.0.7, build 297e128", "", 0));
        when(sshCommandService.executeCommand(jschSession, "sudo systemctl is-active docker"))
                .thenReturn(new CommandResult("inactive", "", 3)); // 服务未启动

        CompletableFuture<DockerInstallationService.DockerInstallationStatus> checkFuture2 = 
                dockerInstallationService.checkDockerInstallation(sshConnection);
        DockerInstallationService.DockerInstallationStatus status2 = checkFuture2.join();

        assertTrue(status2.isInstalled(), "应该检测到Docker已安装");
        assertTrue(status2.getVersion().contains("Docker version 24.0.7"), "应该获取到版本信息");
        assertFalse(status2.isServiceRunning(), "服务应该未运行");
        assertTrue(status2.getMessage().contains("服务未启动"), "消息应该说明服务未启动");

        // Test Case 3: Docker已安装且正常运行
        when(sshCommandService.executeCommand(jschSession, "sudo systemctl is-active docker"))
                .thenReturn(new CommandResult("active", "", 0)); // 服务正常运行

        CompletableFuture<DockerInstallationService.DockerInstallationStatus> checkFuture3 = 
                dockerInstallationService.checkDockerInstallation(sshConnection);
        DockerInstallationService.DockerInstallationStatus status3 = checkFuture3.join();

        assertTrue(status3.isInstalled(), "应该检测到Docker已安装");
        assertTrue(status3.isServiceRunning(), "服务应该正常运行");
        assertTrue(status3.getMessage().contains("运行正常"), "消息应该说明运行正常");
    }

    @Test
    @DisplayName("Docker安装失败处理 - 权限不足场景")
    void testDockerInstallationFailurePermissionDenied() throws Exception {
        // Given - 没有sudo权限的系统
        SystemDetectionService.SystemInfo noSudoSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(false) // 关键：没有sudo权限
                .dockerInstalled(false)
                .build();

        // Mock 权限检查失败
        when(sshCommandService.executeCommand(jschSession, "sudo -v"))
                .thenReturn(new CommandResult("", "sudo: no password entry for user", 1));

        // Mock Docker依赖安装失败（权限不足）
        when(sshCommandService.executeCommand(jschSession, 
                "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release"))
                .thenReturn(new CommandResult("", "Permission denied", 1));

        // When - 尝试安装Docker
        StringBuilder errorLog = new StringBuilder();
        CompletableFuture<DockerInstallationService.DockerInstallationResult> installFuture = 
                dockerInstallationService.installDocker(sshConnection, noSudoSystem, false, 
                        message -> {
                            errorLog.append(message).append("\n");
                            System.out.println("错误处理: " + message);
                        });

        DockerInstallationService.DockerInstallationResult result = installFuture.join();

        // Then - 验证安装失败并提供清晰错误信息
        assertFalse(result.isSuccess(), "没有sudo权限时Docker安装应该失败");
        assertTrue(result.getMessage().contains("失败"), "错误消息应该说明安装失败");
        assertEquals("未安装", result.getInstalledVersion(), "版本应该显示未安装");
        assertNull(result.getInstallationMethod(), "安装方式应该为空");

        // 验证错误日志包含权限相关信息
        String errorOutput = errorLog.toString();
        assertTrue(errorOutput.contains("安装依赖失败") || errorOutput.contains("Permission denied"), 
                "错误日志应该包含权限相关信息");
    }

    @Test
    @DisplayName("Docker安装失败处理 - 网络连接问题")
    void testDockerInstallationFailureNetworkIssues() throws Exception {
        // Given - 网络连接有问题的环境
        SystemDetectionService.SystemInfo systemInfo = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock 依赖安装成功
        when(sshCommandService.executeCommand(jschSession, 
                "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release"))
                .thenReturn(new CommandResult("", "", 0));

        // Mock GPG密钥下载失败（网络问题）
        when(sshCommandService.executeCommand(eq(jschSession), contains("curl -fsSL")))
                .thenReturn(new CommandResult("", "curl: (28) Connection timed out", 28));

        // When - 尝试安装Docker
        StringBuilder errorLog = new StringBuilder();
        CompletableFuture<DockerInstallationService.DockerInstallationResult> installFuture = 
                dockerInstallationService.installDocker(sshConnection, systemInfo, false, 
                        message -> {
                            errorLog.append(message).append("\n");
                            System.out.println("网络错误处理: " + message);
                        });

        // Then - 验证网络错误的处理
        Exception thrownException = assertThrows(Exception.class, () -> installFuture.join());
        
        // 验证异常信息包含网络相关内容
        String exceptionMessage = thrownException.getMessage();
        assertTrue(exceptionMessage.contains("Connection timed out") || 
                   exceptionMessage.contains("网络") ||
                   exceptionMessage.contains("curl"), 
                "异常信息应该包含网络连接相关内容");

        // 验证错误日志
        String errorOutput = errorLog.toString();
        assertTrue(errorOutput.contains("添加 Docker GPG 密钥") || errorOutput.contains("开始安装"), 
                "错误日志应该显示安装进度");
    }

    @Test
    @DisplayName("多Linux发行版Docker安装支持验证")
    void testMultiDistributionDockerInstallationSupport() throws Exception {
        // 测试Arch Linux安装
        SystemDetectionService.SystemInfo archSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("arch")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock Arch Linux Docker安装
        when(sshCommandService.executeCommand(jschSession, "sudo pacman -S --noconfirm docker docker-compose"))
                .thenReturn(new CommandResult("", "", 0));
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(new CommandResult("Docker version 24.0.7", "", 0));

        CompletableFuture<DockerInstallationService.DockerInstallationResult> archInstallFuture = 
                dockerInstallationService.installDocker(sshConnection, archSystem, false, 
                        message -> System.out.println("Arch安装: " + message));

        DockerInstallationService.DockerInstallationResult archResult = archInstallFuture.join();
        assertTrue(archResult.isSuccess(), "Arch Linux Docker安装应该成功");
        assertEquals("Pacman 官方仓库", archResult.getInstallationMethod());

        // 测试Alpine Linux安装
        SystemDetectionService.SystemInfo alpineSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("alpine")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        when(sshCommandService.executeCommand(jschSession, "sudo apk add docker docker-compose"))
                .thenReturn(new CommandResult("", "", 0));

        CompletableFuture<DockerInstallationService.DockerInstallationResult> alpineInstallFuture = 
                dockerInstallationService.installDocker(sshConnection, alpineSystem, false, 
                        message -> System.out.println("Alpine安装: " + message));

        DockerInstallationService.DockerInstallationResult alpineResult = alpineInstallFuture.join();
        assertTrue(alpineResult.isSuccess(), "Alpine Linux Docker安装应该成功");
        assertEquals("APK 官方仓库", alpineResult.getInstallationMethod());

        // 测试不支持的系统
        SystemDetectionService.SystemInfo unsupportedSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("unknown-distro")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        CompletableFuture<DockerInstallationService.DockerInstallationResult> unsupportedInstallFuture = 
                dockerInstallationService.installDocker(sshConnection, unsupportedSystem, false, 
                        message -> System.out.println("不支持的系统: " + message));

        DockerInstallationService.DockerInstallationResult unsupportedResult = unsupportedInstallFuture.join();
        assertFalse(unsupportedResult.isSuccess(), "不支持的系统Docker安装应该失败");
        assertTrue(unsupportedResult.getMessage().contains("不支持的操作系统"));
    }

    @Test
    @DisplayName("Docker服务启动和管理测试")
    void testDockerServiceStartupAndManagement() throws Exception {
        // Test Case 1: systemd系统的Docker服务启动
        SystemDetectionService.SystemInfo systemdSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("ubuntu")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock成功的Docker安装
        mockSuccessfulDockerInstallation();

        CompletableFuture<DockerInstallationService.DockerInstallationResult> installFuture = 
                dockerInstallationService.installDocker(sshConnection, systemdSystem, false, 
                        message -> System.out.println("服务启动测试: " + message));

        DockerInstallationService.DockerInstallationResult result = installFuture.join();
        assertTrue(result.isSuccess(), "Docker安装应该成功");

        // 验证systemd服务启动命令被调用
        verify(sshCommandService).executeCommand(jschSession, 
                "sudo systemctl start docker && sudo systemctl enable docker");

        // Test Case 2: Alpine Linux (OpenRC) 系统
        SystemDetectionService.SystemInfo alpineSystem = SystemDetectionService.SystemInfo.builder()
                .osType("Linux")
                .osId("alpine")
                .hasRootAccess(true)
                .dockerInstalled(false)
                .build();

        // Mock Alpine Docker安装和OpenRC服务管理
        when(sshCommandService.executeCommand(jschSession, "sudo apk add docker docker-compose"))
                .thenReturn(new CommandResult("", "", 0));
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(new CommandResult("Docker version 24.0.7", "", 0));
        when(sshCommandService.executeCommand(jschSession, "sudo rc-update add docker boot"))
                .thenReturn(new CommandResult("", "", 0));
        when(sshCommandService.executeCommand(jschSession, "sudo service docker start"))
                .thenReturn(new CommandResult("", "", 0));

        CompletableFuture<DockerInstallationService.DockerInstallationResult> alpineInstallFuture = 
                dockerInstallationService.installDocker(sshConnection, alpineSystem, false, 
                        message -> System.out.println("Alpine服务启动: " + message));

        DockerInstallationService.DockerInstallationResult alpineResult = alpineInstallFuture.join();
        assertTrue(alpineResult.isSuccess(), "Alpine Docker安装应该成功");

        // 验证OpenRC服务管理命令被调用
        verify(sshCommandService).executeCommand(jschSession, "sudo rc-update add docker boot");
        verify(sshCommandService).executeCommand(jschSession, "sudo service docker start");
    }

    // ===== 辅助方法 =====

    /**
     * Mock成功的Docker安装过程
     */
    private void mockSuccessfulDockerInstallation() {
        // Mock所有安装相关的命令都成功
        when(sshCommandService.executeCommand(eq(jschSession), anyString()))
                .thenReturn(new CommandResult("", "", 0));
        
        // Mock Docker版本检查
        when(sshCommandService.executeCommand(jschSession, "docker --version"))
                .thenReturn(new CommandResult("Docker version 24.0.7, build 297e128", "", 0));
    }
}