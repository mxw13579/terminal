import { ref, readonly } from 'vue';
import useConnectionManager from './useConnectionManager.js';

// 单例状态 - 确保所有组件共享同一个状态
let singletonState = null;

/**
 * Composable for SillyTavern management operations.
 * Uses the unified connection manager instead of creating its own connection.
 * 实现单例模式确保所有组件共享同一个状态。
 */
export function useSillyTavern(options = {}) {
    const { onShowModal = () => {} } = options;

    // 如果单例状态已存在，直接返回
    if (singletonState) {
        console.log('useSillyTavern: 使用现有单例状态，logs长度:', singletonState.logs.value.length);
        return singletonState.api;
    }

    console.log('useSillyTavern: 创建新的单例状态');

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

    // Logs - 单例状态，所有组件共享
    const logs = ref([]);
    const isLoadingLogs = ref(false);
    const isRealtimeLogsActive = ref(false); // 全局管理实时日志状态

    // Versions
    const availableVersions = ref([]);
    const isLoadingVersions = ref(false);
    const versionError = ref(null);

    // Initialize STOMP subscriptions when the composable is used
    const initializeSillyTavernSubscriptions = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            console.warn('STOMP client not available for SillyTavern subscriptions');
            return;
        }

        // 获取 sessionId
        const sessionId = client.ws?._websocket?.extensions?.sessionId ||
                         (client.ws?.readyState && Math.random().toString(36).substr(2, 9)) ||
                         'default';

        console.log('Subscribing to SillyTavern queues with sessionId:', sessionId);

        // Subscribe to system validation responses
        client.subscribe('/user/queue/sillytavern/system-validation', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSystemValidationResponse(data);
            } catch (e) {
                console.error('Error processing system validation response:', e);
            }
        });

        // Subscribe to unified SillyTavern queue for all message types
        client.subscribe('/user/queue/sillytavern', (message) => {
            console.log('收到统一STOMP消息:', message);
            console.log('消息体内容:', message.body);
            try {
                const data = JSON.parse(message.body);
                console.log('解析后的数据:', data);

                // 根据消息类型分发到对应的处理函数
                if (data.type) {
                    switch (data.type) {
                        case 'status':
                            handleStatusResponse(data);
                            break;
                        case 'deployment-progress':
                            handleDeploymentProgress(data);
                            break;
                        case 'interactive-deployment-progress':
                            console.log('收到交互式部署进度消息:', data);
                            handleInteractiveDeploymentProgress(data.payload);
                            break;
                        case 'action-result':
                            handleActionResult(data);
                            break;
                        case 'upgrade-progress':
                            handleUpgradeProgress(data);
                            break;
                        case 'logs':
                            handleLogsResponse(data);
                            break;
                        case 'version-info':
                            handleVersionInfoResponse(data);
                            break;
                        case 'version-upgrade':
                            handleVersionUpgradeResponse(data);
                            break;
                        case 'version-upgrade-progress':
                            handleVersionUpgradeProgress(data);
                            break;
                        case 'cleanup-images':
                            handleImageCleanupResponse(data);
                            break;
                        case 'versions':
                            console.log('收到版本信息消息:', data);
                            handleVersionsResponse(data);
                            break;
                        default:
                            console.warn('未知的消息类型:', data.type, data);
                    }
                } else {
                    console.warn('收到没有type字段的消息:', data);
                }
            } catch (e) {
                console.error('Error processing unified STOMP message:', e);
            }
        });

        // Keep legacy subscriptions for backward compatibility
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

        // Subscribe to real-time logs - 使用新路径
        console.log('🔗 设置实时日志订阅: /user/queue/sillytavern/realtime-logs');
        client.subscribe('/user/queue/sillytavern/realtime-logs', (message) => {
            try {
                console.log('📨 收到实时日志原始消息:', message);
                console.log('📨 消息体内容:', message.body);
                const data = JSON.parse(message.body);
                console.log('📨 收到实时日志消息 (新路径):', data);
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

        // 注释掉重复的实时日志订阅 - 已使用新路径 /user/queue/sillytavern/realtime-logs
        // client.subscribe('/queue/sillytavern/realtime-logs-user' + sessionId, (message) => {
        //     try {
        //         const data = JSON.parse(message.body);
        //         console.log('收到实时日志 (旧路径):', data);
        //         handleRealtimeLogsResponse(data);
        //     } catch (e) {
        //         console.error('Error processing realtime logs response (old path):', e);
        //     }
        // });

        // Subscribe to interactive deployment progress (临时兼容旧路径)
        client.subscribe('/queue/sillytavern/interactive-deployment-progress-user' + sessionId, (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署进度 (旧路径):', data);
                handleInteractiveDeploymentProgress(data);
            } catch (e) {
                console.error('Error processing interactive deployment progress (old path):', e);
            }
        });

        // Subscribe to interactive deployment status (临时兼容旧路径)
        client.subscribe('/queue/sillytavern/interactive-deployment-status-user' + sessionId, (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署状态 (旧路径):', data);
                handleInteractiveDeploymentStatus(data);
            } catch (e) {
                console.error('Error processing interactive deployment status (old path):', e);
            }
        });

        // Subscribe to interactive deployment confirmation requests (临时兼容旧路径)
        client.subscribe('/queue/sillytavern/interactive-deployment-confirmation-user' + sessionId, (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署确认请求 (旧路径):', data);
                handleInteractiveDeploymentConfirmation(data);
            } catch (e) {
                console.error('Error processing interactive deployment confirmation (old path):', e);
            }
        });

        // Subscribe to deployment confirmation responses (确认响应)
        client.subscribe('/queue/sillytavern/deployment-confirm-user' + sessionId, (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到部署确认响应 (旧路径):', data);
                handleDeploymentConfirmResponse(data);
            } catch (e) {
                console.error('Error processing deployment confirm response (old path):', e);
            }
        });

        // Subscribe to interactive deployment progress (新路径)
        client.subscribe('/user/queue/sillytavern/interactive-deployment-progress', (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署进度 (新路径):', data);
                handleInteractiveDeploymentProgress(data);
            } catch (e) {
                console.error('Error processing interactive deployment progress (new path):', e);
            }
        });

        // Subscribe to interactive deployment status (新路径)
        client.subscribe('/user/queue/sillytavern/interactive-deployment-status', (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署状态 (新路径):', data);
                handleInteractiveDeploymentStatus(data);
            } catch (e) {
                console.error('Error processing interactive deployment status (new path):', e);
            }
        });

        // Subscribe to interactive deployment confirmation requests (新路径)
        client.subscribe('/user/queue/sillytavern/interactive-deployment-confirmation', (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到交互式部署确认请求 (新路径):', data);
                handleInteractiveDeploymentConfirmation(data);
            } catch (e) {
                console.error('Error processing interactive deployment confirmation (new path):', e);
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

        // Subscribe to version information responses
        client.subscribe('/user/queue/sillytavern/versions', (message) => {
            console.log('收到版本信息WebSocket消息:', message);
            console.log('消息体:', message.body);
            try {
                const data = JSON.parse(message.body);
                console.log('解析后的数据:', data);
                handleVersionsResponse(data);
            } catch (e) {
                console.error('Error processing versions response:', e);
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
        console.log('收到状态响应:', data);
        isStatusLoading.value = false;
        if (data.success) {
            console.log('状态响应成功，payload:', data.payload);
            containerStatus.value = data.payload;
        } else {
            console.log('状态响应失败，错误:', data.error);
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

    const handleRealtimeLogsResponse = (data) => {
        console.log('🚨 handleRealtimeLogsResponse 被调用 - 检查重复！');
        console.log('收到实时日志数据:', data);
        console.log('当前logs数组长度:', logs.value.length);

        // 检查是否为错误消息
        if (data.type === 'realtime-logs-error') {
            console.error('实时日志错误:', data.message);
            onShowModal("实时日志错误: " + data.message);
            // 将错误消息也作为日志显示
            logs.value = [...logs.value, `[ERROR] ${new Date().toISOString()} ${data.message}`];
            console.log('添加错误消息后logs数组长度:', logs.value.length);
            return;
        }

        // 检查数据格式：如果有type字段，说明是实时日志消息
        if (data.type === 'realtime-logs' && data.payload) {
            console.log('处理实时日志，payload结构:', data.payload);
            console.log('isComplete标记:', data.payload.isComplete);

            let newLines = [];
            // 检查是否有lines字段（这是真正的日志内容）
            if (data.payload.lines && Array.isArray(data.payload.lines)) {
                console.log('找到lines数组，长度:', data.payload.lines.length);
                console.log('lines内容:', data.payload.lines);
                newLines = data.payload.lines;
            } else if (data.payload.logs && Array.isArray(data.payload.logs)) {
                console.log('添加日志数组，长度:', data.payload.logs.length);
                newLines = data.payload.logs;
            } else if (typeof data.payload === 'string') {
                console.log('添加单行日志:', data.payload);
                newLines = [data.payload];
            } else if (data.payload.message) {
                console.log('添加消息格式日志:', data.payload.message);
                newLines = [data.payload.message];
            } else if (data.payload.content) {
                console.log('添加content格式日志:', data.payload.content);
                newLines = [data.payload.content];
            } else {
                console.log('尝试直接使用payload作为日志');
                console.log('payload类型:', typeof data.payload);
                console.log('payload内容:', data.payload);

                if (typeof data.payload === 'object') {
                    const logEntry = JSON.stringify(data.payload, null, 2);
                    newLines = [logEntry];
                } else {
                    newLines = [String(data.payload)];
                }
            }

            // 去重处理：只添加不存在的日志行
            if (newLines.length > 0) {
                const currentLogs = new Set(logs.value);
                const uniqueNewLines = newLines.filter(line => !currentLogs.has(line));
                
                if (uniqueNewLines.length > 0) {
                    logs.value = [...logs.value, ...uniqueNewLines];
                    console.log('添加去重后的日志，新增行数:', uniqueNewLines.length, '总长度:', logs.value.length);
                    
                    // 特别标记最后一批数据
                    if (data.payload.isComplete) {
                        console.log('🚨 收到最后一批日志数据！isComplete=true，总日志数:', logs.value.length);
                    }
                } else {
                    console.log('所有新日志都是重复的，跳过添加');
                    
                    // 即使是重复数据，也要检查是否为最后批次
                    if (data.payload.isComplete) {
                        console.log('🚨 收到最后一批日志数据（重复数据）！isComplete=true，总日志数:', logs.value.length);
                    }
                }
            } else {
                // 没有新日志行，但检查是否为最后批次
                if (data.payload.isComplete) {
                    console.log('🚨 收到最后一批日志数据（空批次）！isComplete=true，总日志数:', logs.value.length);
                }
            }
        } else if (data.success !== undefined) {
            // 兼容旧的响应格式
            if (data.success) {
                let newLines = [];
                if (data.payload && data.payload.logs) {
                    newLines = data.payload.logs;
                } else if (data.payload && typeof data.payload === 'string') {
                    newLines = [data.payload];
                }
                
                // 去重处理
                if (newLines.length > 0) {
                    const currentLogs = new Set(logs.value);
                    const uniqueNewLines = newLines.filter(line => !currentLogs.has(line));
                    
                    if (uniqueNewLines.length > 0) {
                        logs.value = [...logs.value, ...uniqueNewLines];
                    }
                }
            } else {
                console.error('实时日志错误:', data.error);
                onShowModal("实时日志错误: " + (data.error || 'Unknown error'));
            }
        } else if (data.message) {
            console.log('处理message字段消息:', data.message);
            const newLine = `[${data.type || 'INFO'}] ${new Date().toISOString()} ${data.message}`;
            if (!logs.value.includes(newLine)) {
                logs.value = [...logs.value, newLine];
                console.log('添加message后logs数组长度:', logs.value.length);
            }
        } else {
            console.warn('未知的实时日志数据格式:', data);
            let newLines = [];
            if (data.payload) {
                if (typeof data.payload === 'string') {
                    newLines = [data.payload];
                } else if (data.payload.logs) {
                    newLines = data.payload.logs;
                }
            } else if (typeof data === 'string') {
                newLines = [data];
            } else {
                newLines = [`[UNKNOWN] ${JSON.stringify(data)}`];
            }
            
            // 去重处理
            if (newLines.length > 0) {
                const currentLogs = new Set(logs.value);
                const uniqueNewLines = newLines.filter(line => !currentLogs.has(line));
                
                if (uniqueNewLines.length > 0) {
                    logs.value = [...logs.value, ...uniqueNewLines];
                }
            }
        }

        console.log('处理完成，最终logs数组长度:', logs.value.length);
        console.log('最新的3条日志:', logs.value.slice(-3));
    };

    const handleRealtimeLogControlResponse = (data, action) => {
        console.log(`实时日志${action}响应:`, data);
        if (data.success) {
            if (action === 'started') {
                console.log('实时日志已开启');
                // 可以在这里更新UI状态，比如显示"实时日志已开启"
            } else if (action === 'stopped') {
                console.log('实时日志已停止');
                // 可以在这里更新UI状态，比如显示"实时日志已停止"
            }
        } else {
            onShowModal(`实时日志${action === 'started' ? '开启' : '停止'}失败: ` + (data.error || 'Unknown error'));
        }
    };

    const handleHistoryLogsResponse = (data) => {
        console.log('收到历史日志数据:', data);
        isLoadingLogs.value = false;
        if (data.success) {
            if (data.payload && data.payload.logs) {
                logs.value = data.payload.logs;
            }
        } else {
            onShowModal("历史日志获取失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleDataExportResponse = (data) => {
        console.log('收到数据导出响应:', data);
        if (data.success) {
            console.log('数据导出成功');
            onShowModal("数据导出成功", "导出完成");
        } else {
            console.error('数据导出失败:', data.error);
            onShowModal("数据导出失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleDataExportProgress = (data) => {
        console.log('收到数据导出进度:', data);
        // 可以在这里更新导出进度UI
        if (data.progress !== undefined) {
            console.log(`导出进度: ${data.progress}%`);
        }
    };

    const handleDataImportResponse = (data) => {
        console.log('收到数据导入响应:', data);
        if (data.success) {
            console.log('数据导入成功');
            onShowModal("数据导入成功", "导入完成");
        } else {
            console.error('数据导入失败:', data.error);
            onShowModal("数据导入失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleDataImportProgress = (data) => {
        console.log('收到数据导入进度:', data);
        // 可以在这里更新导入进度UI
        if (data.progress !== undefined) {
            console.log(`导入进度: ${data.progress}%`);
        }
    };

    // --- Interactive Deployment Handlers ---
    const handleInteractiveDeploymentProgress = (data) => {
        console.log('处理交互式部署进度，数据格式:', data);

        // 后端发送的数据格式：{sessionId, currentStep: {stepId, stepName, ...}, totalSteps, completedSteps, overallProgress, waitingForConfirmation, pendingConfirmation}
        if (data.sessionId) {
            // 更新交互式部署状态
            interactiveDeployment.value.active = true;
            interactiveDeployment.value.totalSteps = data.totalSteps || 0;
            interactiveDeployment.value.completedSteps = data.completedSteps || 0;
            interactiveDeployment.value.overallProgress = data.overallProgress || 0;

            // 更新当前步骤
            if (data.currentStep) {
                interactiveDeployment.value.currentStep = data.currentStep.stepId;
                console.log('设置当前步骤:', data.currentStep.stepId);

                // 检查并更新步骤详细信息（包括日志）
                if (interactiveDeployment.value.steps) {
                    const stepIndex = interactiveDeployment.value.steps.findIndex(s => s.id === data.currentStep.stepId);
                    if (stepIndex !== -1) {
                        const currentStepData = data.currentStep;
                        const step = interactiveDeployment.value.steps[stepIndex];

                        // 更新步骤状态和进度
                        step.status = currentStepData.status || 'running';
                        step.progress = currentStepData.progress || 0;
                        step.message = currentStepData.message || currentStepData.stepName;

                        // 处理步骤日志 - 这是关键部分！
                        if (currentStepData.logs && currentStepData.logs.length > 0) {
                            console.log('更新步骤日志:', data.currentStep.stepId, currentStepData.logs);
                            // 将后端的日志格式转换为前端期望的格式
                            step.logs = currentStepData.logs.map(logStr => {
                                // 后端日志格式: "[Tue Dec 10 14:24:22 CST 2024] 检测到国家代码: CN"
                                const timestampMatch = logStr.match(/^\[(.*?)\]\s*(.*)$/);
                                if (timestampMatch) {
                                    return {
                                        timestamp: new Date(timestampMatch[1]),
                                        message: timestampMatch[2],
                                        type: 'info'
                                    };
                                } else {
                                    return {
                                        timestamp: new Date(),
                                        message: logStr,
                                        type: 'info'
                                    };
                                }
                            });
                        }

                        console.log('步骤详细信息已更新:', step);
                    }
                }
            }

            // 更新 deploymentProgress 以触发组件的 watcher
            deploymentProgress.value = {
                completed: false,
                currentStep: data.currentStep?.stepId,
                progress: data.overallProgress || 0,
                totalSteps: data.totalSteps || 0,
                completedSteps: data.completedSteps || 0,
                message: data.currentStep?.message || data.currentStep?.stepName || '部署进行中...',
                waitingForConfirmation: data.waitingForConfirmation || false,
                pendingConfirmation: data.pendingConfirmation || null,
                // 包含完整的当前步骤信息，包括日志
                currentStep: data.currentStep || null
            };

            console.log('更新 deploymentProgress:', deploymentProgress.value);
        }

        // 兼容旧格式（如果有 payload 字段）
        else if (data.payload) {
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

            // 更新 deploymentProgress 以触发组件的watcher
            deploymentProgress.value = {
                completed: true,
                success: true,
                message: data.message || "交互式部署完成！",
                accessInfo: data.accessInfo
            };

            onShowModal(data.message || "交互式部署完成！", "部署成功");

            // 刷新容器状态
            getContainerStatus();
        } else {
            interactiveDeployment.value.completed = true;
            interactiveDeployment.value.success = false;

            // 更新 deploymentProgress 以触发组件的watcher
            deploymentProgress.value = {
                completed: true,
                success: false,
                message: "交互式部署失败: " + (data.error || data.message)
            };

            onShowModal("交互式部署失败: " + (data.error || data.message), "部署失败");
        }
    };

    // 处理交互式部署状态更新
    const handleInteractiveDeploymentStatus = (data) => {
        console.log('收到交互式部署状态:', data);

        if (data.steps) {
            interactiveDeployment.value.steps = data.steps.map(step => ({
                id: step.stepId,
                name: step.stepName,
                description: step.description,
                status: step.status,
                progress: step.progress || 0,
                requiresConfirmation: step.requiresConfirmation || false,
                logs: step.logs || []
            }));
        }

        interactiveDeployment.value.active = true;
        interactiveDeployment.value.currentStepIndex = data.currentStepIndex || 0;

        // 更新 deploymentProgress 以触发组件的 watcher
        deploymentProgress.value = {
            completed: false,
            currentStep: data.currentStepIndex < data.steps.length ? data.steps[data.currentStepIndex].stepId : null,
            progress: data.overallProgress || 0,
            message: data.message || '部署进行中...'
        };
    };

    // 处理交互式部署确认请求
    const handleInteractiveDeploymentConfirmation = (data) => {
        console.log('收到确认请求:', data);

        if (data.stepId) {
            // 更新对应步骤为等待确认状态
            const step = interactiveDeployment.value.steps.find(s => s.id === data.stepId);
            if (step) {
                step.status = 'waiting';
                step.requiresConfirmation = true;
                step.confirmationMessage = data.message;
                step.userInput = data.userInput || [];
            }

            // 更新当前等待确认的步骤
            interactiveDeployment.value.pendingConfirmation = {
                stepId: data.stepId,
                stepName: data.stepName,
                message: data.message,
                userInput: data.userInput || []
            };

            // 更新 deploymentProgress 以触发组件的 watcher
            deploymentProgress.value = {
                completed: false,
                currentStep: data.stepId,
                waitingForConfirmation: true,
                confirmationRequest: {
                    stepId: data.stepId,
                    stepName: data.stepName,
                    message: data.message,
                    userInput: data.userInput || []
                }
            };
        }
    };

    const handleDeploymentConfirmResponse = (data) => {
        console.log('收到部署确认响应:', data);

        if (data.success) {
            // 确认成功，等待下一步进度更新
            console.log('部署步骤确认成功:', data.stepId);

            // 更新对应步骤状态为运行中，移除等待确认状态
            if (interactiveDeployment.value.steps) {
                const step = interactiveDeployment.value.steps.find(s => s.id === data.stepId);
                if (step) {
                    step.status = 'running';
                    step.requiresConfirmation = false;
                    step.confirmationMessage = null;
                    console.log('更新步骤状态为运行中:', step.id);
                }
            }

            // 也更新 deploymentProgress
            if (deploymentProgress.value) {
                deploymentProgress.value.waitingForConfirmation = false;
                deploymentProgress.value.pendingConfirmation = null;
                deploymentProgress.value.message = '步骤确认成功，继续执行...';
                console.log('更新部署进度状态');
            }
        } else {
            console.error('步骤确认失败:', data.error || data.message);
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

    const handleVersionsResponse = (data) => {
        // 清除超时
        if (getAvailableVersions.timeoutId) {
            clearTimeout(getAvailableVersions.timeoutId);
            getAvailableVersions.timeoutId = null;
        }

        isLoadingVersions.value = false;

        console.log('收到版本信息响应:', data);

        if (data.success) {
            availableVersions.value = data.versions || [];
            versionError.value = null;
            console.log('成功获取版本信息:', data.versions);
        } else {
            availableVersions.value = [];
            versionError.value = data.error || data.message || '获取版本信息失败';
            console.error('获取版本信息失败:', data.error);
        }
    };

    // --- Public API Methods ---
    
    // 启动实时日志
    const startRealtimeLogs = (containerName = 'sillytavern', maxLines = 1000) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            console.warn('WebSocket未连接，无法启动实时日志');
            return false;
        }

        if (isRealtimeLogsActive.value) {
            console.log('实时日志已经在运行中，跳过启动');
            return true;
        }

        console.log('🚀 启动实时日志，容器:', containerName, '最大行数:', maxLines);
        
        // 启动前清空旧日志数据
        logs.value = [];
        console.log('✅ 已清空旧日志数据，当前长度:', logs.value.length);
        
        isRealtimeLogsActive.value = true;

        const request = { containerName, maxLines };
        
        try {
            if (typeof client.publish === 'function') {
                client.publish({
                    destination: '/app/sillytavern/start-realtime-logs',
                    body: JSON.stringify(request)
                });
            } else {
                client.send('/app/sillytavern/start-realtime-logs', {}, JSON.stringify(request));
            }
            console.log('✅ 实时日志启动请求已发送');
            return true;
        } catch (error) {
            console.error('启动实时日志失败:', error);
            isRealtimeLogsActive.value = false;
            return false;
        }
    };

    // 停止实时日志
    const stopRealtimeLogs = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            console.warn('WebSocket未连接，但标记实时日志为停止');
            isRealtimeLogsActive.value = false;
            return false;
        }

        if (!isRealtimeLogsActive.value) {
            console.log('实时日志未在运行，跳过停止');
            return true;
        }

        console.log('🛑 停止实时日志');
        isRealtimeLogsActive.value = false;

        try {
            if (typeof client.publish === 'function') {
                client.publish({
                    destination: '/app/sillytavern/stop-realtime-logs',
                    body: JSON.stringify({})
                });
            } else {
                client.send('/app/sillytavern/stop-realtime-logs', {}, JSON.stringify({}));
            }
            console.log('✅ 实时日志停止请求已发送');
            return true;
        } catch (error) {
            console.error('停止实时日志失败:', error);
            return false;
        }
    };

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
        console.log('请求容器状态...');
        const client = getStompClient();
        if (!client || !client.connected) {
            console.error('STOMP客户端未连接');
            onShowModal("WebSocket 未连接");
            return;
        }

        console.log('发送容器状态请求到STOMP...');
        isStatusLoading.value = true;
        containerStatus.value = null;

        client.publish({
            destination: '/app/sillytavern/status',
            body: JSON.stringify({})
        });
        console.log('容器状态请求已发送');
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
        console.log('confirmDeploymentStep 被调用:', { stepId, confirmed, userInput })

        const client = getStompClient();
        if (!client || !client.connected) {
            console.error('WebSocket 未连接，无法发送确认消息')
            onShowModal("WebSocket 未连接");
            return;
        }

        const confirmRequest = {
            stepId,
            confirmed,
            userInput
        };

        console.log('准备发送WebSocket确认消息:', confirmRequest)
        console.log('目标路径:', '/app/sillytavern/deployment-confirm')

        client.publish({
            destination: '/app/sillytavern/deployment-confirm',
            body: JSON.stringify(confirmRequest)
        });

        console.log('WebSocket确认消息已发送')
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

    const getAvailableVersions = () => {
        console.log('getAvailableVersions 被调用')

        const client = getStompClient();
        console.log('STOMP客户端状态:', {
            client: !!client,
            connected: client ? client.connected : 'N/A'
        })

        if (!client || !client.connected) {
            console.warn('WebSocket 未连接，无法获取版本信息');
            versionError.value = 'WebSocket 未连接';
            return;
        }

        console.log('开始获取版本信息...');
        isLoadingVersions.value = true;
        versionError.value = null;

        // 设置30秒超时
        const timeoutId = setTimeout(() => {
            if (isLoadingVersions.value) {
                console.warn('获取版本信息超时');
                isLoadingVersions.value = false;
                versionError.value = '获取版本信息超时，请重试';
            }
        }, 30000);

        // 保存timeout ID用于清理
        if (!getAvailableVersions.timeoutId) {
            getAvailableVersions.timeoutId = timeoutId;
        } else {
            clearTimeout(getAvailableVersions.timeoutId);
            getAvailableVersions.timeoutId = timeoutId;
        }

        console.log('发送获取版本信息请求到:', '/app/sillytavern/get-versions');
        client.publish({
            destination: '/app/sillytavern/get-versions',
            body: JSON.stringify({})
        });
        console.log('版本信息请求已发送');
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
    const api = {
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
        isRealtimeLogsActive: readonly(isRealtimeLogsActive), // 新增实时日志状态
        availableVersions: readonly(availableVersions),
        isLoadingVersions: readonly(isLoadingVersions),
        versionError: readonly(versionError),

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

        // 新增实时日志管理方法
        startRealtimeLogs: (containerName, maxLines) => {
            ensureInitialized();
            return startRealtimeLogs(containerName, maxLines);
        },
        stopRealtimeLogs: () => {
            ensureInitialized();
            return stopRealtimeLogs();
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
        getAvailableVersions: () => {
            ensureInitialized();
            return getAvailableVersions();
        },

        // Initialization helper
        initializeSillyTavernSubscriptions
    };

    // 存储单例状态
    singletonState = {
        logs, // 原始响应式引用
        api   // 公开API
    };

    console.log('useSillyTavern: 单例状态已创建，logs引用:', !!logs, 'logs长度:', logs.value.length);
    
    return api;
}
