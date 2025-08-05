import { ref, readonly } from 'vue';
import useConnectionManager from './useConnectionManager.js';

/**
 * SillyTavern扩展功能的Composable
 * 处理配置管理、版本管理、实时日志和数据管理
 */
export function useSillyTavernExtended(options = {}) {
    const { onShowModal = () => {} } = options;

    // Get the unified connection manager
    const { getStompClient } = useConnectionManager();

    // --- Configuration Management State ---
    const configuration = ref(null);
    const isConfigLoading = ref(false);
    const configErrors = ref({});

    // --- Version Management State ---
    const versionInfo = ref(null);
    const isVersionLoading = ref(false);
    const upgradeProgress = ref('');
    const isUpgrading = ref(false);
    const isCleaningImages = ref(false);

    // --- Real-time Logs State ---
    const realtimeLogData = ref([]);
    const isRealtimeActive = ref(false);
    const logMemoryInfo = ref(null);

    // --- Data Management State ---
    const exportData = ref(null);
    const isExporting = ref(false);
    const exportProgress = ref('');
    const isImporting = ref(false);
    const importProgress = ref('');

    // --- Configuration Management ---
    const getConfiguration = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isConfigLoading.value = true;
        configuration.value = null;
        configErrors.value = {};

        client.publish({
            destination: '/app/sillytavern/get-config',
            body: JSON.stringify({})
        });
    };

    const updateConfiguration = (config) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isConfigLoading.value = true;
        configErrors.value = {};

        client.publish({
            destination: '/app/sillytavern/update-config',
            body: JSON.stringify(config)
        });
    };

    // --- Version Management ---
    const getVersionInfo = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isVersionLoading.value = true;
        versionInfo.value = null;

        client.publish({
            destination: '/app/sillytavern/get-version-info',
            body: JSON.stringify({})
        });
    };

    const upgradeToVersion = (targetVersion, containerName = 'sillytavern') => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isUpgrading.value = true;
        upgradeProgress.value = '';

        const request = {
            targetVersion,
            containerName
        };

        client.publish({
            destination: '/app/sillytavern/upgrade-version',
            body: JSON.stringify(request)
        });
    };

    const cleanupImages = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isCleaningImages.value = true;

        client.publish({
            destination: '/app/sillytavern/cleanup-images',
            body: JSON.stringify({})
        });
    };

    // --- Real-time Logs ---
    const startRealtimeLogs = (containerName = 'sillytavern', maxLines = 1000) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        const request = {
            containerName,
            maxLines
        };

        client.publish({
            destination: '/app/sillytavern/start-realtime-logs',
            body: JSON.stringify(request)
        });
    };

    const stopRealtimeLogs = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            return;
        }

        client.publish({
            destination: '/app/sillytavern/stop-realtime-logs',
            body: JSON.stringify({})
        });
    };

    const getHistoryLogs = (containerName = 'sillytavern', lines = 500, level = 'all') => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        const request = {
            containerName,
            lines,
            level
        };

        client.publish({
            destination: '/app/sillytavern/get-history-logs',
            body: JSON.stringify(request)
        });
    };

    // --- Data Management ---
    const exportSillyTavernData = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isExporting.value = true;
        exportProgress.value = '';
        exportData.value = null;

        client.publish({
            destination: '/app/sillytavern/export-data',
            body: JSON.stringify({})
        });
    };

    const importSillyTavernData = (uploadedFileName) => {
        const client = getStompClient();
        if (!client || !client.connected) {
            onShowModal("WebSocket 未连接");
            return;
        }

        isImporting.value = true;
        importProgress.value = '';

        const request = {
            uploadedFileName
        };

        client.publish({
            destination: '/app/sillytavern/import-data',
            body: JSON.stringify(request)
        });
    };

    // --- Message Handlers ---
    const handleConfigResponse = (data) => {
        isConfigLoading.value = false;
        if (data.success) {
            configuration.value = data.payload;
        } else {
            onShowModal("获取配置失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleConfigUpdateResponse = (data) => {
        isConfigLoading.value = false;
        if (data.success) {
            onShowModal(data.message || "配置更新成功", "配置更新");
            // Refresh configuration
            setTimeout(() => getConfiguration(), 1000);
        } else {
            if (data.errors) {
                configErrors.value = data.errors;
            }
            onShowModal("配置更新失败: " + (data.error || data.message), "配置更新失败");
        }
    };

    const handleVersionInfoResponse = (data) => {
        isVersionLoading.value = false;
        if (data.success) {
            versionInfo.value = data.payload;
        } else {
            onShowModal("获取版本信息失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleVersionUpgradeProgress = (data) => {
        if (data.type === 'version-upgrade-progress' && data.message) {
            upgradeProgress.value = data.message;
        }
    };

    const handleVersionUpgradeResponse = (data) => {
        isUpgrading.value = false;
        upgradeProgress.value = '';
        
        if (data.success) {
            onShowModal(data.message || "版本升级成功", "版本升级");
            // Refresh version info
            setTimeout(() => getVersionInfo(), 2000);
        } else {
            onShowModal("版本升级失败: " + (data.error || data.message), "版本升级失败");
        }
    };

    const handleImageCleanupResponse = (data) => {
        isCleaningImages.value = false;
        
        if (data.success) {
            onShowModal(data.message || "镜像清理成功", "镜像清理");
        } else {
            onShowModal("镜像清理失败: " + (data.error || data.message), "镜像清理失败");
        }
    };

    const handleRealtimeLogsResponse = (data) => {
        if (data.type === 'realtime-logs' && data.payload) {
            const logData = data.payload;
            if (logData.lines && logData.lines.length > 0) {
                realtimeLogData.value.push(...logData.lines);
                
                // Limit memory usage
                if (realtimeLogData.value.length > 5000) {
                    realtimeLogData.value = realtimeLogData.value.slice(-3000);
                }
            }
            logMemoryInfo.value = logData.memoryInfo;
        } else if (data.type === 'realtime-logs-error') {
            onShowModal("实时日志错误: " + data.message, "实时日志错误");
            isRealtimeActive.value = false;
        }
    };

    const handleRealtimeLogControlResponse = (data, action) => {
        if (action === 'started') {
            if (data.success) {
                isRealtimeActive.value = true;
                onShowModal("实时日志已启动", "实时日志");
            } else {
                onShowModal("启动实时日志失败: " + (data.error || data.message), "实时日志错误");
            }
        } else if (action === 'stopped') {
            isRealtimeActive.value = false;
            if (data.success) {
                onShowModal("实时日志已停止", "实时日志");
            }
        }
    };

    const handleHistoryLogsResponse = (data) => {
        if (data.success && data.payload) {
            // This will be handled by individual components
            // as they have their own state management
        } else {
            onShowModal("获取历史日志失败: " + (data.error || 'Unknown error'));
        }
    };

    const handleDataExportProgress = (data) => {
        if (data.type === 'export-progress' && data.message) {
            exportProgress.value = data.message;
        }
    };

    const handleDataExportResponse = (data) => {
        isExporting.value = false;
        exportProgress.value = '';
        
        if (data.success && data.payload) {
            exportData.value = data.payload;
            onShowModal("数据导出成功", "数据导出");
        } else {
            onShowModal("数据导出失败: " + (data.error || data.message), "数据导出失败");
        }
    };

    const handleDataImportProgress = (data) => {
        if (data.type === 'import-progress' && data.message) {
            importProgress.value = data.message;
        }
    };

    const handleDataImportResponse = (data) => {
        isImporting.value = false;
        importProgress.value = '';
        
        if (data.success) {
            onShowModal(data.message || "数据导入成功", "数据导入");
        } else {
            onShowModal("数据导入失败: " + (data.error || data.message), "数据导入失败");
        }
    };

    // Initialize extended subscriptions
    const initializeExtendedSubscriptions = () => {
        const client = getStompClient();
        if (!client || !client.connected) {
            console.warn('STOMP client not available for extended subscriptions');
            return;
        }

        console.log('Subscribing to extended SillyTavern queues...');

        // All the subscription handlers will be set up here
        // This function should be called by components that need these features
    };

    // --- Exposed API ---
    return {
        // Configuration Management
        configuration: readonly(configuration),
        isConfigLoading: readonly(isConfigLoading),
        configErrors: readonly(configErrors),
        getConfiguration,
        updateConfiguration,

        // Version Management
        versionInfo: readonly(versionInfo),
        isVersionLoading: readonly(isVersionLoading),
        upgradeProgress: readonly(upgradeProgress),
        isUpgrading: readonly(isUpgrading),
        isCleaningImages: readonly(isCleaningImages),
        getVersionInfo,
        upgradeToVersion,
        cleanupImages,

        // Real-time Logs
        realtimeLogData: readonly(realtimeLogData),
        isRealtimeActive: readonly(isRealtimeActive),
        logMemoryInfo: readonly(logMemoryInfo),
        startRealtimeLogs,
        stopRealtimeLogs,
        getHistoryLogs,

        // Data Management
        exportData: readonly(exportData),
        isExporting: readonly(isExporting),
        exportProgress: readonly(exportProgress),
        isImporting: readonly(isImporting),
        importProgress: readonly(importProgress),
        exportSillyTavernData,
        importSillyTavernData,

        // Message handlers (for internal use)
        handleConfigResponse,
        handleConfigUpdateResponse,
        handleVersionInfoResponse,
        handleVersionUpgradeProgress,
        handleVersionUpgradeResponse,
        handleImageCleanupResponse,
        handleRealtimeLogsResponse,
        handleRealtimeLogControlResponse,
        handleHistoryLogsResponse,
        handleDataExportProgress,
        handleDataExportResponse,
        handleDataImportProgress,
        handleDataImportResponse,

        // Initialization
        initializeExtendedSubscriptions
    };
}