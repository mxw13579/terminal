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
        
        // Initialization helper
        initializeSillyTavernSubscriptions
    };
}