import { ref, readonly } from 'vue';
import useConnectionManager from './useConnectionManager.js';

/**
 * Composable for SillyTavern management operations.
 * Uses the unified connection manager instead of creating its own connection.
 */
export function useSillyTavern(options = {}) {
    const { onShowModal = () => {} } = options;

    // Get the unified connection manager
    const { getStompClient } = useConnectionManager();

    // --- State ---
    // Container status
    const containerStatus = ref(null);
    const isStatusLoading = ref(false);

    // System validation
    const systemInfo = ref(null);
    const isSystemValid = ref(false);
    const systemChecking = ref(false);

    // Deployment
    const isDeploying = ref(false);
    const deploymentProgress = ref(null);
    
    // Interactive deployment state
    const interactiveDeployment = ref({
        active: false,
        mode: null, // 'trusted' | 'interactive'
        config: null,
        currentStep: null,
        steps: [],
        completed: false,
        success: false,
        accessInfo: null
    });

    // Service actions
    const isPerformingAction = ref(false);
    const currentAction = ref('');

    // Logs
    const logs = ref([]);
    const isLoadingLogs = ref(false);

    // Initialize STOMP subscriptions when the composable is used
    const initializeSillyTavernSubscriptions = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            console.warn('STOMP client not available for SillyTavern subscriptions');
            return;
        }

        console.log('Subscribing to SillyTavern queues...');

        // Subscribe to system validation responses
        client.subscribe('/user/queue/sillytavern/system-validation', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSystemValidationResponse(data);
            } catch (e) {
                console.error('Error processing system validation response:', e);
            }
        });

        // Subscribe to container status responses
        client.subscribe('/user/queue/sillytavern/status', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleStatusResponse(data);
            } catch (e) {
                console.error('Error processing status response:', e);
            }
        });

        // Subscribe to deployment progress
        client.subscribe('/user/queue/sillytavern/deployment-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDeploymentProgress(data);
            } catch (e) {
                console.error('Error processing deployment progress:', e);
            }
        });

        // Subscribe to service action results
        client.subscribe('/user/queue/sillytavern/action-result', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleActionResult(data);
            } catch (e) {
                console.error('Error processing action result:', e);
            }
        });

        // Subscribe to upgrade progress
        client.subscribe('/user/queue/sillytavern/upgrade-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleUpgradeProgress(data);
            } catch (e) {
                console.error('Error processing upgrade progress:', e);
            }
        });

        // Subscribe to logs
        client.subscribe('/user/queue/sillytavern/logs', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleLogsResponse(data);
            } catch (e) {
                console.error('Error processing logs response:', e);
            }
        });

        // Subscribe to configuration responses
        client.subscribe('/user/queue/sillytavern/config', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleConfigResponse(data);
            } catch (e) {
                console.error('Error processing config response:', e);
            }
        });

        // Subscribe to configuration update responses  
        client.subscribe('/user/queue/sillytavern/config-updated', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleConfigUpdateResponse(data);
            } catch (e) {
                console.error('Error processing config update response:', e);
            }
        });

        // Subscribe to version info responses
        client.subscribe('/user/queue/sillytavern/version-info', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleVersionInfoResponse(data);
            } catch (e) {
                console.error('Error processing version info response:', e);
            }
        });

        // Subscribe to version upgrade responses
        client.subscribe('/user/queue/sillytavern/version-upgrade', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleVersionUpgradeResponse(data);
            } catch (e) {
                console.error('Error processing version upgrade response:', e);
            }
        });

        // Subscribe to version upgrade progress
        client.subscribe('/user/queue/sillytavern/version-upgrade-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleVersionUpgradeProgress(data);
            } catch (e) {
                console.error('Error processing version upgrade progress:', e);
            }
        });

        // Subscribe to image cleanup responses
        client.subscribe('/user/queue/sillytavern/cleanup-images', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleImageCleanupResponse(data);
            } catch (e) {
                console.error('Error processing image cleanup response:', e);
            }
        });

        // Subscribe to real-time logs
        client.subscribe('/user/queue/sillytavern/realtime-logs', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleRealtimeLogsResponse(data);
            } catch (e) {
                console.error('Error processing realtime logs response:', e);
            }
        });

        // Subscribe to real-time log control responses
        client.subscribe('/user/queue/sillytavern/realtime-logs-started', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleRealtimeLogControlResponse(data, 'started');
            } catch (e) {
                console.error('Error processing realtime log start response:', e);
            }
        });

        client.subscribe('/user/queue/sillytavern/realtime-logs-stopped', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleRealtimeLogControlResponse(data, 'stopped');
            } catch (e) {
                console.error('Error processing realtime log stop response:', e);
            }
        });

        // Subscribe to history logs
        client.subscribe('/user/queue/sillytavern/history-logs', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleHistoryLogsResponse(data);
            } catch (e) {
                console.error('Error processing history logs response:', e);
            }
        });

        // Subscribe to data export responses
        client.subscribe('/user/queue/sillytavern/export', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDataExportResponse(data);
            } catch (e) {
                console.error('Error processing data export response:', e);
            }
        });

        // Subscribe to data export progress
        client.subscribe('/user/queue/sillytavern/export-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDataExportProgress(data);
            } catch (e) {
                console.error('Error processing data export progress:', e);
            }
        });

        // Subscribe to data import responses
        client.subscribe('/user/queue/sillytavern/import', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDataImportResponse(data);
            } catch (e) {
                console.error('Error processing data import response:', e);
            }
        });

        // Subscribe to data import progress
        client.subscribe('/user/queue/sillytavern/import-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDataImportProgress(data);
            } catch (e) {
                console.error('Error processing data import progress:', e);
            }
        });

        // Subscribe to interactive deployment progress
        client.subscribe('/user/queue/sillytavern/interactive-deploy-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleInteractiveDeploymentProgress(data);
            } catch (e) {
                console.error('Error processing interactive deployment progress:', e);
            }
        });

        // Subscribe to interactive deployment results
        client.subscribe('/user/queue/sillytavern/interactive-deploy', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleInteractiveDeploymentResult(data);
            } catch (e) {
                console.error('Error processing interactive deployment result:', e);
            }
        });

        // Subscribe to deployment confirmation responses
        client.subscribe('/user/queue/sillytavern/deployment-confirm', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDeploymentConfirmResponse(data);
            } catch (e) {
                console.error('Error processing deployment confirm response:', e);
            }
        });

        // Subscribe to deployment skip responses
        client.subscribe('/user/queue/sillytavern/deployment-skip', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDeploymentSkipResponse(data);
            } catch (e) {
                console.error('Error processing deployment skip response:', e);
            }
        });

        // Subscribe to deployment cancel responses
        client.subscribe('/user/queue/sillytavern/deployment-cancel', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleDeploymentCancelResponse(data);
            } catch (e) {
                console.error('Error processing deployment cancel response:', e);
            }
        });

        // Subscribe to errors
        client.subscribe('/user/queue/errors', (message) => {
            try {
                const data = JSON.parse(message.body);
                onShowModal("SillyTavern 错误: " + data.payload);
            } catch (e) {
                console.error('Error processing error message:', e);
            }
        });
    };

    // --- Message Handlers ---
    const handleSystemValidationResponse = (data) => {
        systemChecking.value = false;
        if (data.success) {
            systemInfo.value = data.payload;
            isSystemValid.value = data.payload.meetsRequirements;
        } else {
            onShowModal("系统验证失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleStatusResponse = (data) => {
        isStatusLoading.value = false;
        if (data.success) {
            containerStatus.value = data.payload;
        } else {
            onShowModal("状态检查失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleDeploymentProgress = (data) => {
        if (data.payload) {
            deploymentProgress.value = data.payload;
            
            if (data.payload.completed) {
                isDeploying.value = false;
                if (data.payload.success) {
                    onShowModal(data.payload.message || "部署成功！", "部署完成");
                    // Refresh container status
                    getContainerStatus();
                } else {
                    onShowModal("部署失败: " + (data.payload.error || 'Unknown error'), "部署失败");
                }
            }
        }
    };

    const handleActionResult = (data) => {
        isPerformingAction.value = false;
        currentAction.value = '';
        
        if (data.success) {
            onShowModal(data.message, "操作成功");
            // Refresh container status after action
            getContainerStatus();
        } else {
            onShowModal("操作失败: " + (data.error || data.message), "操作失败");
        }
    };

    const handleUpgradeProgress = (data) => {
        if (data.payload && data.payload.message) {
            // Update current action with progress message
            currentAction.value = data.payload.message;
        }
    };

    const handleLogsResponse = (data) => {
        isLoadingLogs.value = false;
        if (data.success) {
            logs.value = data.payload.logs || [];
        } else {
            onShowModal("日志获取失败: " + (data.error || 'Unknown error'));
        }
    };

    // --- Interactive Deployment Handlers ---
    const handleInteractiveDeploymentProgress = (data) => {
        if (data.payload) {
            const { step, message, progress, requiresConfirmation, stepData } = data.payload;
            
            // 更新交互式部署状态
            interactiveDeployment.value.active = true;
            interactiveDeployment.value.currentStep = step;
            
            // 如果有步骤数据，更新对应步骤
            if (interactiveDeployment.value.steps && step) {
                const stepIndex = interactiveDeployment.value.steps.findIndex(s => s.id === step);
                if (stepIndex !== -1) {
                    const currentStep = interactiveDeployment.value.steps[stepIndex];
                    currentStep.status = requiresConfirmation ? 'waiting' : 'running';
                    currentStep.progress = progress || 0;
                    currentStep.requiresConfirmation = requiresConfirmation;
                    
                    // 添加日志消息
                    if (message) {
                        if (!currentStep.logs) currentStep.logs = [];
                        currentStep.logs.push({
                            timestamp: new Date(),
                            message: message,
                            type: 'info'
                        });
                    }
                    
                    // 更新步骤数据
                    if (stepData) {
                        currentStep.stepData = stepData;
                    }
                }
            }
        }
    };

    const handleInteractiveDeploymentResult = (data) => {
        if (data.success) {
            interactiveDeployment.value.completed = true;
            interactiveDeployment.value.success = true;
            interactiveDeployment.value.accessInfo = data.accessInfo;
            
            onShowModal(data.message || "交互式部署完成！", "部署成功");
            
            // 刷新容器状态
            getContainerStatus();
        } else {
            interactiveDeployment.value.completed = true;
            interactiveDeployment.value.success = false;
            
            onShowModal("交互式部署失败: " + (data.error || data.message), "部署失败");
        }
    };

    const handleDeploymentConfirmResponse = (data) => {
        if (data.success) {
            // 确认成功，等待下一步进度更新
            console.log('部署步骤确认成功:', data.stepId);
        } else {
            onShowModal("步骤确认失败: " + (data.error || data.message), "确认失败");
        }
    };

    const handleDeploymentSkipResponse = (data) => {
        if (data.success) {
            // 跳过成功，更新步骤状态
            const stepIndex = interactiveDeployment.value.steps.findIndex(s => s.id === data.stepId);
            if (stepIndex !== -1) {
                interactiveDeployment.value.steps[stepIndex].status = 'skipped';
            }
            console.log('部署步骤跳过成功:', data.stepId);
        } else {
            onShowModal("步骤跳过失败: " + (data.error || data.message), "跳过失败");
        }
    };

    const handleDeploymentCancelResponse = (data) => {
        if (data.success) {
            // 取消成功，重置交互式部署状态
            interactiveDeployment.value.active = false;
            interactiveDeployment.value.completed = true;
            interactiveDeployment.value.success = false;
            
            onShowModal("部署已取消", "部署取消");
        } else {
            onShowModal("取消部署失败: " + (data.error || data.message), "取消失败");
        }
    };

    // --- Public API Methods ---
    const validateSystem = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        systemChecking.value = true;
        systemInfo.value = null;
        isSystemValid.value = false;

        client.publish({
            destination: '/app/sillytavern/validate-system',
            body: JSON.stringify({})
        });
    };

    const getContainerStatus = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isStatusLoading.value = true;
        containerStatus.value = null;

        client.publish({
            destination: '/app/sillytavern/status',
            body: JSON.stringify({})
        });
    };

    const deployContainer = (deploymentConfig) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isDeploying.value = true;
        deploymentProgress.value = null;

        client.publish({
            destination: '/app/sillytavern/deploy',
            body: JSON.stringify(deploymentConfig)
        });
    };

    const performServiceAction = (action, options = {}) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isPerformingAction.value = true;
        currentAction.value = action;

        const actionData = {
            action: action,
            containerName: 'sillytavern',
            ...options
        };

        client.publish({
            destination: '/app/sillytavern/service-action',
            body: JSON.stringify(actionData)
        });
    };

    const getContainerLogs = (logConfig = {}) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isLoadingLogs.value = true;
        logs.value = [];

        const logRequest = {
            days: 1,
            tailLines: 100,
            containerName: 'sillytavern',
            ...logConfig
        };

        client.publish({
            destination: '/app/sillytavern/get-logs',
            body: JSON.stringify(logRequest)
        });
    };

    // --- Interactive Deployment Methods ---
    const startInteractiveDeployment = (deploymentConfig) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        // 初始化交互式部署状态
        interactiveDeployment.value = {
            active: true,
            mode: deploymentConfig.mode,
            config: deploymentConfig,
            currentStep: null,
            steps: [
                {
                    id: 'geo-detection',
                    title: '地理位置检测',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                },
                {
                    id: 'system-detection', 
                    title: '系统检测',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                },
                {
                    id: 'package-manager',
                    title: '包管理器配置',
                    status: 'pending',
                    requiresConfirmation: deploymentConfig.mode === 'interactive',
                    confirmationMessage: '是否配置系统镜像源以加速软件包下载？',
                    logs: [],
                    progress: 0
                },
                {
                    id: 'docker-installation',
                    title: 'Docker安装',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                },
                {
                    id: 'docker-mirror',
                    title: 'Docker镜像加速',
                    status: 'pending',
                    requiresConfirmation: deploymentConfig.mode === 'interactive',
                    confirmationMessage: '是否配置Docker镜像加速器？',
                    logs: [],
                    progress: 0
                },
                {
                    id: 'sillytavern-deployment',
                    title: 'SillyTavern部署',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                },
                {
                    id: 'external-access',
                    title: '外网访问配置',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                },
                {
                    id: 'service-validation',
                    title: '服务验证',
                    status: 'pending',
                    requiresConfirmation: false,
                    logs: [],
                    progress: 0
                }
            ],
            completed: false,
            success: false,
            accessInfo: null
        };

        // 发送交互式部署请求
        client.publish({
            destination: '/app/sillytavern/interactive-deploy',
            body: JSON.stringify(deploymentConfig)
        });
    };

    const confirmDeploymentStep = (stepId, confirmed, userInput = {}) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        const confirmRequest = {
            stepId,
            confirmed,
            userInput
        };

        client.publish({
            destination: '/app/sillytavern/deployment-confirm',
            body: JSON.stringify(confirmRequest)
        });
    };

    const skipDeploymentStep = (stepId, reason = '用户选择跳过') => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        const skipRequest = {
            stepId,
            reason
        };

        client.publish({
            destination: '/app/sillytavern/deployment-skip',
            body: JSON.stringify(skipRequest)
        });
    };

    const cancelInteractiveDeployment = (reason = '用户取消部署') => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        const cancelRequest = {
            reason
        };

        client.publish({
            destination: '/app/sillytavern/deployment-cancel',
            body: JSON.stringify(cancelRequest)
        });
    };

    const resetInteractiveDeployment = () => {
        interactiveDeployment.value = {
            active: false,
            mode: null,
            config: null,
            currentStep: null,
            steps: [],
            completed: false,
            success: false,
            accessInfo: null
        };
    };

    // Initialize subscriptions when composable is first used
    const initialized = ref(false);
    const ensureInitialized = () => {
        if (!initialized.value) {
            initializeSillyTavernSubscriptions();
            initialized.value = true;
        }
    };

    // --- Exposed API ---
    return {
        // State (readonly)
        containerStatus: readonly(containerStatus),
        isStatusLoading: readonly(isStatusLoading),
        systemInfo: readonly(systemInfo),
        isSystemValid: readonly(isSystemValid),
        systemChecking: readonly(systemChecking),
        isDeploying: readonly(isDeploying),
        deploymentProgress: readonly(deploymentProgress),
        interactiveDeployment: readonly(interactiveDeployment),
        isPerformingAction: readonly(isPerformingAction),
        currentAction: readonly(currentAction),
        logs: readonly(logs),
        isLoadingLogs: readonly(isLoadingLogs),

        // Methods
        validateSystem: () => {
            ensureInitialized();
            return validateSystem();
        },
        getContainerStatus: () => {
            ensureInitialized();
            return getContainerStatus();
        },
        deployContainer: (config) => {
            ensureInitialized();
            return deployContainer(config);
        },
        performServiceAction: (action, options) => {
            ensureInitialized();
            return performServiceAction(action, options);
        },
        getContainerLogs: (config) => {
            ensureInitialized();
            return getContainerLogs(config);
        },
        
        // Interactive deployment methods
        startInteractiveDeployment: (config) => {
            ensureInitialized();
            return startInteractiveDeployment(config);
        },
        confirmDeploymentStep: (stepId, confirmed, userInput) => {
            ensureInitialized();
            return confirmDeploymentStep(stepId, confirmed, userInput);
        },
        skipDeploymentStep: (stepId, reason) => {
            ensureInitialized();
            return skipDeploymentStep(stepId, reason);
        },
        cancelInteractiveDeployment: (reason) => {
            ensureInitialized();
            return cancelInteractiveDeployment(reason);
        },
        resetInteractiveDeployment: () => {
            return resetInteractiveDeployment();
        },
        
        // Initialization helper
        initializeSillyTavernSubscriptions
    };
}