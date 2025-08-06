package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.InteractiveDeploymentDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 交互式部署服务
 * <p>
 * 负责管理 SillyTavern 的交互式部署流程，支持分步确认和完全信任两种模式。
 * 提供部署状态跟踪、步骤执行、用户交互等功能。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveDeploymentService {

    private final GeolocationDetectionService geolocationService;
    private final PackageManagerService packageManagerService;
    private final DockerInstallationService dockerInstallationService;
    private final DockerMirrorService dockerMirrorService;
    private final SillyTavernDeploymentService sillyTavernDeploymentService;
    private final ExternalAccessService externalAccessService;
    private final ServiceValidationService validationService;
    private final SystemDetectionService systemDetectionService;
    private final SystemConfigurationService systemConfigurationService;
    private final SshCommandService sshCommandService;
    private final SimpMessagingTemplate messagingTemplate;

    /** 存储部署状态，key为sessionId */
    private final Map<String, InteractiveDeploymentDto.StatusDto> deploymentStates = new ConcurrentHashMap<>();
    /** 存储待确认的请求，key为sessionId */
    private final Map<String, InteractiveDeploymentDto.ConfirmationRequestDto> pendingConfirmations = new ConcurrentHashMap<>();
    /** 存储SSH连接，key为sessionId */
    private final Map<String, SshConnection> deploymentConnections = new ConcurrentHashMap<>();
    /** 存储部署请求，key为sessionId */
    private final Map<String, InteractiveDeploymentDto.RequestDto> deploymentRequests = new ConcurrentHashMap<>();

    /** 定义部署步骤 */
    private static final List<String> DEPLOYMENT_STEPS = Arrays.asList(
            "geolocation_detection",
            "system_detection",
            "package_manager_config",
            "docker_installation",
            "docker_mirror_config",
            "sillytavern_deployment",
            "external_access_config",
            "service_validation",
            "deployment_complete"
    );

    /**
     * 启动增强版交互式部署
     * <p>包含Docker自动安装功能的完整部署流程</p>
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param request 部署请求
     * @return 异步部署任务
     */
    public CompletableFuture<Void> startEnhancedInteractiveDeployment(
            String sessionId,
            SshConnection connection,
            InteractiveDeploymentDto.RequestDto request) {
        
        log.info("启动增强版交互式部署，会话: {}, 模式: {}", sessionId, request.getDeploymentMode());
        
        // 增强版部署逻辑与普通版本相同，但在Docker安装步骤会自动处理未安装的情况
        // 关键改进已经在 executeDockerInstallation 方法中实现
        return startInteractiveDeployment(sessionId, connection, request);
    }

    /**
     * 启动交互式部署
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param request 部署请求
     * @return 异步部署任务
     */
    public CompletableFuture<Void> startInteractiveDeployment(
            String sessionId,
            SshConnection connection,
            InteractiveDeploymentDto.RequestDto request) {

        log.info("启动交互式部署，会话: {}, 模式: {}", sessionId, request.getDeploymentMode());

        // 初始化部署状态
        InteractiveDeploymentDto.StatusDto status = initializeDeploymentStatus(sessionId, request);
        deploymentStates.put(sessionId, status);
        
        // 保存连接和请求以供恢复使用
        deploymentConnections.put(sessionId, connection);
        deploymentRequests.put(sessionId, request);

        // 发送初始状态
        sendDeploymentStatus(sessionId, status);

        return CompletableFuture.runAsync(() -> {
            try {
                executeDeploymentSteps(sessionId, connection, request);
            } catch (Exception e) {
                log.error("部署执行失败，会话: {}", sessionId, e);
                handleDeploymentError(sessionId, e);
            }
        });
    }

    /**
     * 处理用户确认
     *
     * @param sessionId 会话ID
     * @param confirmation 用户确认响应
     */
    public void handleUserConfirmation(String sessionId, InteractiveDeploymentDto.ConfirmationDto confirmation) {
        log.info("处理用户确认，会话: {}, 步骤: {}, 操作: {}",
                sessionId, confirmation.getStepId(), confirmation.getAction());

        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) {
            log.warn("未找到部署状态，会话: {}", sessionId);
            return;
        }

        // 清除待确认请求
        pendingConfirmations.remove(sessionId);

        // 根据用户操作继续执行
        switch (confirmation.getAction()) {
            case "confirm":
                resumeDeploymentAfterConfirmation(sessionId, confirmation);
                break;
            case "skip":
                skipCurrentStep(sessionId, confirmation);
                break;
            case "cancel":
                cancelDeployment(sessionId, confirmation.getReason());
                break;
            default:
                log.warn("未知的用户操作: {}", confirmation.getAction());
        }
    }

    /**
     * 取消部署
     *
     * @param sessionId 会话ID
     * @param reason 取消原因
     */
    public void cancelDeployment(String sessionId, String reason) {
        log.info("取消部署，会话: {}, 原因: {}", sessionId, reason);

        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status != null) {
            status.setRunning(false);
            status.setCompleted(true);
            status.setSuccess(false);
            status.setErrorMessage("用户取消部署: " + (reason != null ? reason : "无原因"));
            status.setEndTime(System.currentTimeMillis());

            // 更新当前步骤状态
            if (status.getCurrentStepIndex() < status.getSteps().size()) {
                InteractiveDeploymentDto.StepDto currentStep = status.getSteps().get(status.getCurrentStepIndex());
                currentStep.setStatus("failed");
                currentStep.setMessage("用户取消");
            }

            sendDeploymentStatus(sessionId, status);
        }

        // 清理资源
        cleanupDeploymentSession(sessionId);
    }

    /**
     * 获取部署状态
     *
     * @param sessionId 会话ID
     * @return 部署状态
     */
    public InteractiveDeploymentDto.StatusDto getDeploymentStatus(String sessionId) {
        return deploymentStates.get(sessionId);
    }

    /**
     * 初始化部署状态
     *
     * @param sessionId 会话ID
     * @param request 部署请求
     * @return 初始部署状态对象
     */
    private InteractiveDeploymentDto.StatusDto initializeDeploymentStatus(
            String sessionId, InteractiveDeploymentDto.RequestDto request) {

        List<InteractiveDeploymentDto.StepDto> steps = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);

        DEPLOYMENT_STEPS.forEach(stepId -> {
            InteractiveDeploymentDto.StepDto step = InteractiveDeploymentDto.StepDto.builder()
                    .stepId(stepId)
                    .stepName(getStepDisplayName(stepId))
                    .status("pending")
                    .progress(0)
                    .requiresConfirmation("confirmation".equals(request.getDeploymentMode()))
                    .logs(new ArrayList<>())
                    .timestamp(System.currentTimeMillis())
                    .build();
            steps.add(step);
        });

        return InteractiveDeploymentDto.StatusDto.builder()
                .sessionId(sessionId)
                .deploymentMode(request.getDeploymentMode())
                .steps(steps)
                .currentStepIndex(0)
                .isRunning(true)
                .isCompleted(false)
                .isSuccess(false)
                .startTime(System.currentTimeMillis())
                .request(request) // 保存原始请求
                .build();
    }

    /**
     * 执行部署步骤
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param request 部署请求
     */
    private void executeDeploymentSteps(String sessionId, SshConnection connection,
                                        InteractiveDeploymentDto.RequestDto request) {

        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) return;

        for (int i = 0; i < status.getSteps().size(); i++) {
            if (!status.isRunning()) {
                log.info("部署已停止，会话: {}", sessionId);
                return;
            }

            status.setCurrentStepIndex(i);
            InteractiveDeploymentDto.StepDto currentStep = status.getSteps().get(i);

            try {
                // 检查是否需要在步骤执行前等待用户确认
                if (currentStep.isRequiresConfirmation() && "pending".equals(currentStep.getStatus())) {
                    log.debug("步骤需要用户确认，等待确认中: {}", currentStep.getStepId());
                    currentStep.setStatus("waiting");
                    waitForUserConfirmation(sessionId, currentStep);
                    sendDeploymentProgress(sessionId);
                    return; // 等待用户确认，暂停执行
                }
                
                executeStep(sessionId, connection, currentStep, request);

                // 标记步骤完成
                currentStep.setStatus("completed");
                currentStep.setProgress(100);
                currentStep.setMessage("步骤完成");

            } catch (Exception e) {
                log.error("步骤执行失败，会话: {}, 步骤: {}", sessionId, currentStep.getStepId(), e);
                currentStep.setStatus("failed");
                currentStep.setErrorMessage(e.getMessage());

                if ("confirmation".equals(status.getDeploymentMode())) {
                    // 确认模式下询问用户如何处理错误
                    requestErrorHandling(sessionId, currentStep, e);
                    return;
                } else {
                    // 信任模式下终止部署
                    handleDeploymentError(sessionId, e);
                    return;
                }
            }

            sendDeploymentProgress(sessionId);
        }

        // 所有步骤完成
        completeDeployment(sessionId);
    }

    /**
     * 执行单个步骤
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 当前步骤
     * @param request 部署请求
     * @throws Exception 步骤执行异常
     */
    private void executeStep(String sessionId, SshConnection connection,
                             InteractiveDeploymentDto.StepDto step,
                             InteractiveDeploymentDto.RequestDto request) throws Exception {

        step.setStatus("running");
        step.setTimestamp(System.currentTimeMillis());
        sendDeploymentProgress(sessionId);

        switch (step.getStepId()) {
            case "geolocation_detection":
                executeGeolocationDetection(sessionId, connection, step);
                break;
            case "system_detection":
                executeSystemDetection(sessionId, connection, step);
                break;
            case "package_manager_config":
                executePackageManagerConfig(sessionId, connection, step);
                break;
            case "docker_installation":
                executeDockerInstallation(sessionId, connection, step);
                break;
            case "docker_mirror_config":
                executeDockerMirrorConfig(sessionId, connection, step);
                break;
            case "sillytavern_deployment":
                executeSillyTavernDeployment(sessionId, connection, step);
                break;
            case "external_access_config":
                executeExternalAccessConfig(sessionId, connection, step);
                break;
            case "service_validation":
                executeServiceValidation(sessionId, connection, step);
                break;
            case "deployment_complete":
                executeDeploymentComplete(sessionId, connection, step);
                break;
            default:
                throw new UnsupportedOperationException("未知步骤: " + step.getStepId());
        }
    }

    /**
     * 执行地理位置检测
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 检测异常
     */
    private void executeGeolocationDetection(String sessionId, SshConnection connection,
                                             InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("检测服务器地理位置...");
        step.setProgress(20);
        sendDeploymentProgress(sessionId);

        GeolocationDetectionService.GeolocationInfo result = geolocationService.detectGeolocation(connection,
                (progressMsg) -> {
                    step.setMessage(progressMsg);
                    sendDeploymentProgress(sessionId);
                }).join();

        step.setMessage("地理位置检测完成: " + (result.getCountryCode() != null ? result.getCountryCode() : "未知"));
        step.setProgress(100);

        // 存储检测结果供后续步骤使用
        step.setConfirmationData(Map.of("geolocationResult", result));

        addStepLog(step, "检测到国家代码: " + result.getCountryCode());
        addStepLog(step, "使用中国镜像源: " + result.isUseChineseMirror());
    }

    /**
     * 执行系统检测
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 检测异常
     */
    private void executeSystemDetection(String sessionId, SshConnection connection,
                                        InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("检测系统环境...");
        step.setProgress(30);
        sendDeploymentProgress(sessionId);

        SystemDetectionService.SystemInfo systemInfo = systemDetectionService.detectSystemEnvironmentSync(connection);

        step.setMessage("系统环境检测完成");
        step.setProgress(100);
        step.setConfirmationData(Map.of("systemInfo", systemInfo));

        addStepLog(step, "操作系统: " + systemInfo.getOsType());
        addStepLog(step, "架构: " + systemInfo.getArchitecture());
    }

    /**
     * 执行包管理器配置
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 配置异常
     */
    private void executePackageManagerConfig(String sessionId, SshConnection connection,
                                             InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("配置包管理器镜像源...");
        step.setProgress(25);
        sendDeploymentProgress(sessionId);

        // 使用新的SystemConfigurationService进行系统配置
        SystemConfigurationService.SystemConfigResult result = systemConfigurationService
                .configureSystemMirrors(connection, (progressMsg) -> {
                    step.setMessage(progressMsg);
                    addStepLog(step, progressMsg);
                    sendDeploymentProgress(sessionId);
                }).join();

        step.setMessage(result.isSuccess() ? "系统镜像源配置完成" : 
                       (result.isSkipped() ? "系统镜像源配置跳过" : "系统镜像源配置失败"));
        step.setProgress(100);
        step.setConfirmationData(Map.of("configResult", result));

        addStepLog(step, result.getMessage());
        
        if (result.getGeolocationInfo() != null) {
            addStepLog(step, "地理位置: " + result.getGeolocationInfo().getCountryCode());
            addStepLog(step, "使用中国镜像: " + result.getGeolocationInfo().isUseChineseMirror());
        }
    }

    /**
     * 执行Docker安装
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 安装异常
     */
    private void executeDockerInstallation(String sessionId, SshConnection connection,
                                           InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("检查Docker安装状态...");
        step.setProgress(10);
        sendDeploymentProgress(sessionId);

        Consumer<String> progressCallback = (message) -> {
            step.setMessage(message);
            step.setProgress(Math.min(step.getProgress() + 10, 90));
            addStepLog(step, message);
            sendDeploymentProgress(sessionId);
        };

        // 首先检查Docker是否已安装
        DockerInstallationService.DockerInstallationStatus dockerStatus = 
                dockerInstallationService.checkDockerInstallation(connection).join();
        
        if (dockerStatus.isInstalled() && dockerStatus.isServiceRunning()) {
            step.setMessage("Docker已安装且运行正常");
            step.setProgress(100);
            addStepLog(step, "Docker版本: " + dockerStatus.getVersion());
            addStepLog(step, "Docker服务状态: 正常运行");
            step.setConfirmationData(Map.of("dockerStatus", dockerStatus));
            return;
        }
        
        if (dockerStatus.isInstalled() && !dockerStatus.isServiceRunning()) {
            progressCallback.accept("Docker已安装但服务未运行，尝试启动...");
            try {
                startDockerService(connection, progressCallback);
                step.setMessage("Docker服务启动成功");
                step.setProgress(100);
                addStepLog(step, "Docker服务已启动");
                return;
            } catch (Exception e) {
                progressCallback.accept("Docker服务启动失败，将重新安装Docker");
                addStepLog(step, "服务启动失败: " + e.getMessage());
            }
        }

        // 关键修复：Docker未安装时，自动安装而不是失败
        progressCallback.accept("Docker未安装，开始自动安装...");
        
        // 获取系统信息
        SystemDetectionService.SystemInfo systemInfo = systemDetectionService.detectSystemEnvironmentSync(connection);
        
        // 判断是否使用中国镜像源（从前面步骤获取）
        boolean useChineseMirror = false;
        try {
            InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
            if (status != null && status.getSteps().size() > 2) {
                // 从包管理器配置步骤获取地理位置信息
                InteractiveDeploymentDto.StepDto packageStep = status.getSteps().get(2);
                if (packageStep.getConfirmationData() != null) {
                    SystemConfigurationService.SystemConfigResult configResult =
                            (SystemConfigurationService.SystemConfigResult) packageStep.getConfirmationData().get("configResult");
                    if (configResult != null && configResult.getGeolocationInfo() != null) {
                        useChineseMirror = configResult.getGeolocationInfo().isUseChineseMirror();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("无法获取地理位置信息，使用默认设置: {}", e.getMessage());
        }

        // 执行Docker安装
        DockerInstallationService.DockerInstallationResult result = dockerInstallationService.installDocker(
                connection, systemInfo, useChineseMirror, progressCallback).join();

        if (!result.isSuccess()) {
            // 安装失败时的处理
            step.setMessage("Docker安装失败");
            step.setProgress(100);
            step.setConfirmationData(Map.of("installationResult", result));
            addStepLog(step, "安装失败: " + result.getMessage());
            throw new RuntimeException("Docker安装失败: " + result.getMessage());
        }

        step.setMessage("Docker安装成功");
        step.setProgress(100);
        step.setConfirmationData(Map.of("installationResult", result));

        addStepLog(step, "安装方式: " + result.getInstallationMethod());
        addStepLog(step, "安装版本: " + result.getInstalledVersion());
        
        // 验证安装结果
        progressCallback.accept("验证Docker安装...");
        DockerInstallationService.DockerInstallationStatus finalStatus = 
                dockerInstallationService.checkDockerInstallation(connection).join();
                
        if (finalStatus.isInstalled() && finalStatus.isServiceRunning()) {
            addStepLog(step, "Docker安装验证成功");
        } else {
            addStepLog(step, "警告: Docker安装后验证失败，可能需要手动启动服务");
        }
    }
    
    /**
     * 启动Docker服务
     *
     * @param connection SSH连接
     * @param progressCallback 进度回调
     * @throws Exception 启动异常
     */
    private void startDockerService(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        try {
            progressCallback.accept("正在启动Docker服务...");
            
            // 首先检查Docker是否已经在运行
            CommandResult statusResult = sshCommandService.executeCommand(
                    connection.getJschSession(),
                    "sudo systemctl is-active docker"
            );
            
            if (statusResult.exitStatus() == 0 && "active".equals(statusResult.stdout().trim())) {
                progressCallback.accept("Docker服务已经在运行");
                return;
            }
            
            // 尝试使用systemctl启动Docker
            CommandResult result = sshCommandService.executeCommand(
                    connection.getJschSession(),
                    "sudo systemctl start docker && sudo systemctl enable docker"
            );
            
            if (result.exitStatus() != 0) {
                // 获取详细错误信息
                CommandResult detailResult = sshCommandService.executeCommand(
                        connection.getJschSession(),
                        "sudo systemctl status docker.service -l --no-pager"
                );
                log.warn("systemctl启动Docker失败，详细信息: {}", detailResult.stdout());
                
                throw new RuntimeException("systemctl启动Docker失败: " + result.stderr());
            }
            
            // 验证Docker是否真正启动
            Thread.sleep(2000); // 等待服务完全启动
            CommandResult verifyResult = sshCommandService.executeCommand(
                    connection.getJschSession(),
                    "sudo systemctl is-active docker"
            );
            
            if (verifyResult.exitStatus() == 0 && "active".equals(verifyResult.stdout().trim())) {
                progressCallback.accept("Docker服务启动成功");
            } else {
                throw new RuntimeException("Docker服务启动后验证失败");
            }
            
        } catch (Exception e) {
            log.error("使用systemctl启动Docker失败", e);
            
            // 尝试使用service命令
            try {
                progressCallback.accept("尝试使用service命令启动Docker...");
                CommandResult serviceResult = sshCommandService.executeCommand(
                        connection.getJschSession(),
                        "sudo service docker start"
                );
                
                if (serviceResult.exitStatus() != 0) {
                    // 尝试重新安装或重置Docker
                    progressCallback.accept("尝试重新初始化Docker...");
                    CommandResult reinitResult = sshCommandService.executeCommand(
                            connection.getJschSession(),
                            "sudo systemctl reset-failed docker && sudo systemctl daemon-reload && sudo systemctl start docker"
                    );
                    
                    if (reinitResult.exitStatus() != 0) {
                        throw new RuntimeException("Docker服务启动失败，可能需要手动检查Docker安装: " + serviceResult.stderr());
                    }
                }
                
                // 验证服务状态
                Thread.sleep(2000);
                CommandResult finalVerifyResult = sshCommandService.executeCommand(
                        connection.getJschSession(),
                        "docker --version && sudo docker ps"
                );
                
                if (finalVerifyResult.exitStatus() == 0) {
                    progressCallback.accept("Docker服务启动成功");
                } else {
                    throw new RuntimeException("Docker服务验证失败: " + finalVerifyResult.stderr());
                }
                
            } catch (Exception serviceException) {
                log.error("使用service启动Docker也失败", serviceException);
                
                // 提供详细的错误诊断信息
                try {
                    CommandResult diagResult = sshCommandService.executeCommand(
                            connection.getJschSession(),
                            "sudo journalctl -xeu docker.service --no-pager -n 20"
                    );
                    log.error("Docker启动失败诊断信息: {}", diagResult.stdout());
                } catch (Exception diagException) {
                    log.warn("无法获取Docker诊断信息", diagException);
                }
                
                throw new RuntimeException("无法启动Docker服务，请检查系统配置和Docker安装状态。错误信息: " + serviceException.getMessage());
            }
        }
    }

    /**
     * 执行Docker镜像源配置
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 配置异常
     */
    private void executeDockerMirrorConfig(String sessionId, SshConnection connection,
                                           InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("配置Docker镜像加速器...");
        step.setProgress(30);
        sendDeploymentProgress(sessionId);

        DockerMirrorService.DockerMirrorConfigResult result = dockerMirrorService.configureMirror(connection).join();

        step.setMessage(result.isSuccess() ? "Docker镜像加速器配置完成" : "Docker镜像加速器配置跳过");
        step.setProgress(100);

        addStepLog(step, result.getMessage());
    }

    /**
     * 执行SillyTavern部署
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 部署异常
     */
    private void executeSillyTavernDeployment(String sessionId, SshConnection connection,
                                              InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("部署SillyTavern容器...");
        step.setProgress(10);
        sendDeploymentProgress(sessionId);

        Consumer<String> progressCallback = (message) -> {
            step.setMessage(message);
            step.setProgress(Math.min(step.getProgress() + 15, 90));
            addStepLog(step, message);
            sendDeploymentProgress(sessionId);
        };

        // 从部署状态中获取用户配置
        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        Map<String, Object> customConfig = status != null && status.getRequest() != null ? 
            status.getRequest().getCustomConfig() : new HashMap<>();

        // 安全地从customConfig中提取配置，避免ClassCastException
        String selectedVersion = getStringValue(customConfig, "selectedVersion", "latest");
        String port = getStringValue(customConfig, "port", "8000");
        boolean enableExternalAccess = getBooleanValue(customConfig, "enableExternalAccess", false);
        String username = getStringValue(customConfig, "username", "");
        String password = getStringValue(customConfig, "password", "");

        // 创建部署配置 - 使用用户提供的配置
        SillyTavernDeploymentService.SillyTavernDeploymentConfig deploymentConfig =
                SillyTavernDeploymentService.SillyTavernDeploymentConfig.builder()
                        .selectedVersion(selectedVersion)
                        .port(port)
                        .enableExternalAccess(enableExternalAccess)
                        .username(username)
                        .password(password)
                        .build();

        progressCallback.accept("使用配置: " + selectedVersion + ", 端口: " + port + 
                               (enableExternalAccess ? ", 开启外网访问" : ", 仅本地访问"));

        // 判断是否使用中国镜像源
        boolean useChineseMirror = false;
        try {
            if (status != null && status.getSteps().size() > 0) {
                InteractiveDeploymentDto.StepDto geoStep = status.getSteps().get(0);
                if (geoStep.getConfirmationData() != null) {
                    GeolocationDetectionService.GeolocationInfo geoResult =
                            (GeolocationDetectionService.GeolocationInfo) geoStep.getConfirmationData().get("geolocationResult");
                    if (geoResult != null) {
                        useChineseMirror = geoResult.isUseChineseMirror();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("无法获取地理位置信息，使用默认设置: {}", e.getMessage());
        }

        SillyTavernDeploymentService.SillyTavernDeploymentResult result =
                sillyTavernDeploymentService.deploySillyTavern(connection, deploymentConfig, useChineseMirror, progressCallback).join();

        step.setMessage(result.isSuccess() ? "SillyTavern部署完成" : "SillyTavern部署失败");
        step.setProgress(100);
        step.setConfirmationData(Map.of("deploymentResult", result));

        addStepLog(step, result.getMessage());
        if (result.isSuccess() && result.getAccessUrl() != null) {
            addStepLog(step, "访问地址: " + result.getAccessUrl());
        }
    }

    /**
     * 执行外网访问配置
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 配置异常
     */
    private void executeExternalAccessConfig(String sessionId, SshConnection connection,
                                             InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("配置外网访问...");
        step.setProgress(20);
        sendDeploymentProgress(sessionId);

        // 创建外网访问配置
        ExternalAccessService.ExternalAccessConfig accessConfig =
                ExternalAccessService.ExternalAccessConfig.builder()
                        .enableExternalAccess(true)
                        .useRandomCredentials(false)
                        .username("admin")
                        .password("password123")
                        .port("8000")
                        .build();

        ExternalAccessService.ExternalAccessConfigResult result =
                externalAccessService.configureExternalAccess(connection, accessConfig, (message) -> {
                    step.setMessage(message);
                    addStepLog(step, message);
                    sendDeploymentProgress(sessionId);
                }).join();

        step.setMessage(result.isSuccess() ? "外网访问配置完成" : "外网访问配置跳过");
        step.setProgress(100);

        addStepLog(step, result.getMessage());
        if (result.isSuccess()) {
            addStepLog(step, "用户名: " + result.getUsername());
            addStepLog(step, "密码: " + result.getPassword());
        }
    }

    /**
     * 执行服务验证
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     * @throws Exception 验证异常
     */
    private void executeServiceValidation(String sessionId, SshConnection connection,
                                          InteractiveDeploymentDto.StepDto step) throws Exception {
        step.setMessage("验证服务状态...");
        step.setProgress(25);
        sendDeploymentProgress(sessionId);

        ServiceValidationService.ServiceValidationResult result = validationService.validateDeployment(connection).join();

        step.setMessage(result.isSuccess() ? "服务验证通过" : "服务验证失败");
        step.setProgress(100);
        step.setConfirmationData(Map.of("validationResult", result));

        addStepLog(step, result.getMessage());
        if (result.isSuccess()) {
            addStepLog(step, "容器运行状态: " + (result.isContainerRunning() ? "正常" : "异常"));
            addStepLog(step, "端口监听状态: " + (result.isPortListening() ? "正常" : "异常"));
            addStepLog(step, "HTTP响应状态: " + (result.isHttpResponsive() ? "正常" : "异常"));
        }

        if (!result.isSuccess()) {
            throw new RuntimeException("服务验证失败: " + result.getMessage());
        }
    }

    /**
     * 执行部署完成
     *
     * @param sessionId 会话ID
     * @param connection SSH连接
     * @param step 步骤对象
     */
    private void executeDeploymentComplete(String sessionId, SshConnection connection,
                                           InteractiveDeploymentDto.StepDto step) {
        step.setMessage("部署完成");
        step.setProgress(100);

        addStepLog(step, "SillyTavern部署成功完成");
        addStepLog(step, "您现在可以开始使用服务");
    }

    /**
     * 等待用户确认
     *
     * @param sessionId 会话ID
     * @param step 当前步骤
     */
    private void waitForUserConfirmation(String sessionId, InteractiveDeploymentDto.StepDto step) {
        InteractiveDeploymentDto.ConfirmationRequestDto confirmationRequest =
                InteractiveDeploymentDto.ConfirmationRequestDto.builder()
                        .stepId(step.getStepId())
                        .stepName(step.getStepName())
                        .message("是否继续执行 " + step.getStepName() + "?")
                        .options(Arrays.asList(
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("confirm")
                                        .label("确认")
                                        .description("继续执行此步骤")
                                        .isRecommended(true)
                                        .build(),
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("skip")
                                        .label("跳过")
                                        .description("跳过此步骤")
                                        .build(),
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("cancel")
                                        .label("取消")
                                        .description("取消整个部署")
                                        .isRisky(true)
                                        .build()
                        ))
                        .defaultChoice("confirm")
                        .timeoutSeconds(180)
                        .additionalData(step.getConfirmationData())
                        .build();

        pendingConfirmations.put(sessionId, confirmationRequest);
        step.setStatus("waiting_confirmation");

        // 发送确认请求到前端
        sendConfirmationRequest(sessionId, confirmationRequest);
    }

    /**
     * 确认后恢复部署
     *
     * @param sessionId 会话ID
     * @param confirmation 用户确认响应
     */
    private void resumeDeploymentAfterConfirmation(String sessionId, InteractiveDeploymentDto.ConfirmationDto confirmation) {
        // 在新线程中恢复部署执行
        CompletableFuture.runAsync(() -> {
            InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
            SshConnection connection = deploymentConnections.get(sessionId);
            InteractiveDeploymentDto.RequestDto request = deploymentRequests.get(sessionId);
            
            if (status == null || connection == null || request == null) {
                log.error("无法恢复部署，缺少必要数据，会话: {}", sessionId);
                return;
            }

            try {
                // 将当前等待的步骤标记为确认状态
                if (status.getCurrentStepIndex() < status.getSteps().size()) {
                    InteractiveDeploymentDto.StepDto currentStep = status.getSteps().get(status.getCurrentStepIndex());
                    currentStep.setStatus("running");
                    currentStep.setMessage("用户已确认，正在执行...");
                    sendDeploymentProgress(sessionId);
                }
                
                // 从当前步骤继续执行
                executeDeploymentSteps(sessionId, connection, request);
            } catch (Exception e) {
                handleDeploymentError(sessionId, e);
            }
        });
    }

    /**
     * 跳过当前步骤
     *
     * @param sessionId 会话ID
     * @param confirmation 用户确认响应
     */
    private void skipCurrentStep(String sessionId, InteractiveDeploymentDto.ConfirmationDto confirmation) {
        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) return;

        InteractiveDeploymentDto.StepDto currentStep = status.getSteps().get(status.getCurrentStepIndex());
        currentStep.setStatus("completed");
        currentStep.setProgress(100);
        currentStep.setMessage("用户跳过此步骤");
        addStepLog(currentStep, "跳过原因: " + confirmation.getReason());

        // 继续下一步
        resumeDeploymentAfterConfirmation(sessionId, confirmation);
    }

    /**
     * 完成部署
     *
     * @param sessionId 会话ID
     */
    private void completeDeployment(String sessionId) {
        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) return;

        status.setRunning(false);
        status.setCompleted(true);
        status.setSuccess(true);
        status.setEndTime(System.currentTimeMillis());

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("deploymentTime", status.getEndTime() - status.getStartTime());
        finalResult.put("totalSteps", status.getSteps().size());
        finalResult.put("completedSteps", (int) status.getSteps().stream().filter(s -> "completed".equals(s.getStatus())).count());
        status.setFinalResult(finalResult);

        sendDeploymentStatus(sessionId, status);
        log.info("部署完成，会话: {}", sessionId);
    }

    /**
     * 处理部署错误
     *
     * @param sessionId 会话ID
     * @param error 异常对象
     */
    private void handleDeploymentError(String sessionId, Exception error) {
        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) return;

        status.setRunning(false);
        status.setCompleted(true);
        status.setSuccess(false);
        status.setErrorMessage(error.getMessage());
        status.setEndTime(System.currentTimeMillis());

        sendDeploymentStatus(sessionId, status);
        log.error("部署失败，会话: {}", sessionId, error);
    }

    /**
     * 请求错误处理
     *
     * @param sessionId 会话ID
     * @param step 当前步骤
     * @param error 异常对象
     */
    private void requestErrorHandling(String sessionId, InteractiveDeploymentDto.StepDto step, Exception error) {
        InteractiveDeploymentDto.ConfirmationRequestDto confirmationRequest =
                InteractiveDeploymentDto.ConfirmationRequestDto.builder()
                        .stepId(step.getStepId())
                        .stepName(step.getStepName())
                        .message("步骤执行失败: " + error.getMessage() + "\n您希望如何处理?")
                        .options(Arrays.asList(
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("retry")
                                        .label("重试")
                                        .description("重新执行此步骤")
                                        .isRecommended(true)
                                        .build(),
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("skip")
                                        .label("跳过")
                                        .description("跳过此步骤继续")
                                        .build(),
                                InteractiveDeploymentDto.ConfirmationOptionDto.builder()
                                        .key("cancel")
                                        .label("取消")
                                        .description("取消整个部署")
                                        .isRisky(true)
                                        .build()
                        ))
                        .defaultChoice("retry")
                        .timeoutSeconds(300)
                        .additionalData(Map.of("error", error.getMessage()))
                        .build();

        pendingConfirmations.put(sessionId, confirmationRequest);
        sendConfirmationRequest(sessionId, confirmationRequest);
    }

    /**
     * 发送部署状态
     *
     * @param sessionId 会话ID
     * @param status 部署状态
     */
    private void sendDeploymentStatus(String sessionId, InteractiveDeploymentDto.StatusDto status) {
        messagingTemplate.convertAndSend("/queue/sillytavern/interactive-deployment-status-user" + sessionId, status);
    }

    /**
     * 发送部署进度
     *
     * @param sessionId 会话ID
     */
    private void sendDeploymentProgress(String sessionId) {
        InteractiveDeploymentDto.StatusDto status = deploymentStates.get(sessionId);
        if (status == null) return;

        InteractiveDeploymentDto.ProgressDto progress = InteractiveDeploymentDto.ProgressDto.builder()
                .sessionId(sessionId)
                .currentStep(status.getCurrentStepIndex() < status.getSteps().size() ?
                        status.getSteps().get(status.getCurrentStepIndex()) : null)
                .totalSteps(status.getSteps().size())
                .completedSteps((int) status.getSteps().stream().filter(s -> "completed".equals(s.getStatus())).count())
                .overallProgress((int) ((double) status.getCurrentStepIndex() / status.getSteps().size() * 100))
                .waitingForConfirmation(pendingConfirmations.containsKey(sessionId))
                .pendingConfirmation(pendingConfirmations.get(sessionId))
                .build();

        messagingTemplate.convertAndSend("/queue/sillytavern/interactive-deployment-progress-user" + sessionId, progress);
    }

    /**
     * 发送确认请求
     *
     * @param sessionId 会话ID
     * @param request 确认请求对象
     */
    private void sendConfirmationRequest(String sessionId, InteractiveDeploymentDto.ConfirmationRequestDto request) {
        messagingTemplate.convertAndSend("/queue/sillytavern/interactive-deployment-confirmation-user" + sessionId, request);
    }

    /**
     * 清理部署会话
     *
     * @param sessionId 会话ID
     */
    private void cleanupDeploymentSession(String sessionId) {
        deploymentStates.remove(sessionId);
        pendingConfirmations.remove(sessionId);
        deploymentConnections.remove(sessionId);
        deploymentRequests.remove(sessionId);
        log.debug("清理部署会话: {}", sessionId);
    }

    /**
     * 获取步骤显示名称
     *
     * @param stepId 步骤ID
     * @return 步骤显示名称
     */
    private String getStepDisplayName(String stepId) {
        Map<String, String> stepNames = Map.of(
                "geolocation_detection", "地理位置检测",
                "system_detection", "系统环境检测",
                "package_manager_config", "包管理器配置",
                "docker_installation", "Docker安装",
                "docker_mirror_config", "Docker镜像源配置",
                "sillytavern_deployment", "SillyTavern部署",
                "external_access_config", "外网访问配置",
                "service_validation", "服务验证",
                "deployment_complete", "部署完成"
        );
        return stepNames.getOrDefault(stepId, stepId);
    }

    /**
     * 添加步骤日志
     *
     * @param step 步骤对象
     * @param message 日志消息
     */
    private void addStepLog(InteractiveDeploymentDto.StepDto step, String message) {
        if (step.getLogs() == null) {
            step.setLogs(new ArrayList<>());
        }
        step.getLogs().add(String.format("[%s] %s",
                new Date().toString(), message));
    }

    /**
     * 安全地从Map中获取String值
     */
    private String getStringValue(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 处理数字类型转换为字符串
        return String.valueOf(value);
    }

    /**
     * 安全地从Map中获取Boolean值
     */
    private boolean getBooleanValue(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        // 处理数字类型：0为false，其他为true
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        return defaultValue;
    }
}
