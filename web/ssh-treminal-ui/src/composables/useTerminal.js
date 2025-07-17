import { ref, readonly ,watch } from 'vue';
import { formatSpeed } from '../utils/formatters.js';

// Composable函数接收一个配置对象，用于与外部通信（如显示Modal）
export function useTerminal(options = {}) {
    const { onShowModal = () => {} } = options;

    // --- State ---
    const host = ref('');
    const port = ref('');
    const user = ref('');
    const isConnected = ref(false);
    const isConnecting = ref(false);

    const sftpVisible = ref(false);
    const sftpLoading = ref(false);
    const sftpError = ref('');
    const currentSftpPath = ref('');
    const sftpFiles = ref([]);

    const isSftpActionInProgress = ref(false);
    const localUploadProgress = ref(0);
    const remoteUploadProgress = ref(0);
    const uploadStatusText = ref('');
    const uploadSpeed = ref('');
    const sftpUploadSpeed = ref('');
    const monitorVisible = ref(false);
    const isMonitoring = ref(false);
    const isLoading = ref(false);
    const systemStats = ref(null);
    const dockerContainers = ref([]);

    let ws = null;
    let term = null;
    let sendNextChunk = null;
    let uploadStartTime = 0;
    let uploadBytesSent = 0;

    // --- WebSocket Logic ---
    const connect = (details) => {
        host.value = details.host;
        port.value = details.port;
        user.value = details.user;
        isConnecting.value = true;

        const queryParams = new URLSearchParams({
            host: host.value, port: port.value || 22, user: user.value, password: details.password
        });
        const wsUrl = `ws://localhost:8080/ws/terminal?${queryParams.toString()}`;
        ws = new WebSocket(wsUrl);

        ws.onopen = () => {
            isConnecting.value = false;
            isConnected.value = true;
        };
        ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                handleWsMessage(msg);
            } catch (e) {
                if (term && typeof event.data === 'string') term.write(event.data);
            }
        };
        ws.onclose = (event) => {
            // isConnected 为 false 表示是初始连接就失败了
            if (!isConnected.value) {
                onShowModal("连接失败，请检查主机、端口、用户名和密码。");
            } else { // 否则，是连接成功后意外断开
                if (term) {
                    term.write('\r\n🔌 连接意外断开。\r\n');
                }
                onShowModal("连接已意外断开。");
            }
            resetState(); // 在任何关闭情况下都重置状态
        };
        ws.onerror = () => { if (!isConnected.value) isConnecting.value = false; };
    };

    const disconnect = () => {
        if (ws) {
            // 关键：在主动断开时，立即移除 onclose 监听器。
            // 这可以防止 onclose 中的“意外断开”逻辑被错误地触发。
            ws.onclose = null;
            ws.close(1000, "User disconnected");
        }
        if (term) {
            term.write('\r\n🔌 连接已由用户关闭。\r\n');
        }
        // 立即重置状态，确保UI即时响应，跳转回连接页面。
        resetState();
    };

    // --- WebSocket Message Handling ---
    const handleWsMessage = (msg) => {
        switch (msg.type) {
            case 'terminal_data':
                if (term) term.write(msg.payload);
                break;
            case 'sftp_list_response':
                sftpLoading.value = false;
                sftpError.value = '';
                currentSftpPath.value = msg.path;
                sftpFiles.value = msg.files;
                break;
            case 'sftp_upload_chunk_success':
                localUploadProgress.value = Math.round(((msg.chunkIndex + 1) / msg.totalChunks) * 100);
                if (sendNextChunk) sendNextChunk();
                break;
            case 'sftp_remote_progress':
                remoteUploadProgress.value = msg.progress;
                sftpUploadSpeed.value = formatSpeed(msg.speed);
                uploadStatusText.value = `正在上传到服务器... ${msg.progress}%`;
                break;
            case 'sftp_upload_final_success':
                remoteUploadProgress.value = 100;
                isSftpActionInProgress.value = false;
                uploadStatusText.value = '上传完成！';
                sftpUploadSpeed.value = '';
                onShowModal(msg.message || "上传成功!");
                fetchSftpList(msg.path);
                break;
            case 'sftp_download_response':
                handleFileDownload(msg.filename, msg.content);
                break;
            case 'sftp_error':
                sftpLoading.value = false;
                isSftpActionInProgress.value = false;
                sftpError.value = `SFTP Error: ${msg.message}`;
                onShowModal(`SFTP Error: ${msg.message}`);
                break;
            case 'error':
                isConnecting.value = false;
                onShowModal(`连接时发生错误: ${msg.payload}`);
                resetState();
                break;
            // 监控消息处理
            case 'monitor_update':
                isMonitoring.value = true;
                isLoading.value = false;
                systemStats.value = msg.payload;
                dockerContainers.value = msg.payload.dockerContainers || [];
                break;
        }
    };

    // --- Internal Methods ---
    const sendWsMessage = (message) => {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(message));
        }
    };

    const resetState = () => {
        if (ws) {
            ws.onopen = ws.onmessage = ws.onclose = ws.onerror = null;
            if(ws.readyState === WebSocket.OPEN) ws.close();
            ws = null;
        }
        if (term) term.dispose();
        // --- 将所有相关状态重置到其初始值 ---
        host.value = '';
        port.value = '';
        user.value = '';
        isConnected.value = false;
        isConnecting.value = false;
        term = null;

        // 重置SFTP相关状态
        sftpVisible.value = false;
        sftpLoading.value = false;
        sftpError.value = '';
        currentSftpPath.value = '';
        sftpFiles.value = [];
        isSftpActionInProgress.value = false;

        // 重置上传相关状态
        localUploadProgress.value = 0;
        remoteUploadProgress.value = 0;
        uploadStatusText.value = '';
        uploadSpeed.value = '';
        sftpUploadSpeed.value = '';
        sendNextChunk = null;

        // 重置监控相关状态
        monitorVisible.value = false;
        isMonitoring.value = false;
        isLoading.value = false;
        systemStats.value = null;
        dockerContainers.value = [];
    };

    // --- Public API Methods (to be called from component) ---
    const setTerminalInstance = (instance) => { term = instance; };
    const sendTerminalData = (data) => sendWsMessage({ type: 'data', payload: data });
    const sendTerminalResize = (size) => sendWsMessage({ type: 'resize', ...size });
    const toggleMonitorPanel = () => {
        monitorVisible.value = !monitorVisible.value;
    };

    // 监听 monitorVisible 变化来启动/停止监控
    watch(monitorVisible, (newValue) => {
        if (newValue) { // 当面板打开时
            if (!systemStats.value) {
                isLoading.value = true;
            }
            // 发送消息，触发后端进入“高频模式”
            sendWsMessage({ type: 'monitor_start' });
        } else { // 当面板关闭时
            // 发送消息，触发后端进入“低频模式”
            sendWsMessage({ type: 'monitor_stop' });
            isMonitoring.value = false;
        }
    });

    const toggleSftpPanel = () => {
        sftpVisible.value = !sftpVisible.value;
        if (sftpVisible.value && sftpFiles.value.length === 0) {
            fetchSftpList();
        }
    };

    const fetchSftpList = (path = '.') => {
        sftpLoading.value = true;
        sftpError.value = '';
        sendWsMessage({ type: 'sftp_list', path });
    };

    const downloadSftpFiles = (paths) => {
        if (paths.length === 0) return;
        isSftpActionInProgress.value = true;
        sftpError.value = '';
        sendWsMessage({ type: 'sftp_download', paths: paths });
    };

    const uploadSftpFile = (file) => {
        const chunkSize = 128 * 1024;
        const totalChunks = Math.ceil(file.size / chunkSize);
        let chunkIndex = 0;

        isSftpActionInProgress.value = true;
        sftpError.value = '';
        localUploadProgress.value = 0;
        remoteUploadProgress.value = 0;
        uploadSpeed.value = '';
        sftpUploadSpeed.value = '';
        uploadStatusText.value = `准备上传: ${file.name}`;
        uploadStartTime = Date.now();
        uploadBytesSent = 0;

        sendNextChunk = () => {
            if (chunkIndex >= totalChunks) {
                uploadStatusText.value = '分片发送完毕, 等待服务器处理...';
                sendNextChunk = null;
                return;
            }
            const offset = chunkIndex * chunkSize;
            const reader = new FileReader();
            reader.onload = (e) => {
                const base64Content = e.target.result.split(',')[1];
                uploadBytesSent += file.slice(offset, offset + chunkSize).size;
                const elapsed = (Date.now() - uploadStartTime) / 1000;
                if (elapsed > 0) uploadSpeed.value = formatSpeed(uploadBytesSent / elapsed);
                uploadStatusText.value = `正在上传分片 ${chunkIndex + 1}/${totalChunks}`;
                sendWsMessage({ type: 'sftp_upload_chunk', path: currentSftpPath.value, filename: file.name, chunkIndex, totalChunks, content: base64Content });
                chunkIndex++;
            };
            reader.onerror = () => { onShowModal("读取文件失败！"); isSftpActionInProgress.value = false; sendNextChunk = null; };
            reader.readAsDataURL(file.slice(offset, offset + chunkSize));
        };
        sendNextChunk();
    };

    const handleFileDownload = (filename, base64Content) => {
        try {
            const byteCharacters = atob(base64Content);
            const byteNumbers = Array.from(byteCharacters, char => char.charCodeAt(0));
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray]);

            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = filename;
            link.click();
            URL.revokeObjectURL(link.href);
            link.remove();
        } catch (error) {
            onShowModal("创建下载文件失败！");
        } finally {
            isSftpActionInProgress.value = false;
        }
    };

    // Expose public state and methods
    return {
        // State (use readonly for states the component shouldn't directly modify)
        host: readonly(host),
        port: readonly(port),
        user: readonly(user),
        isConnected: readonly(isConnected),
        isConnecting: readonly(isConnecting),
        sftpVisible: readonly(sftpVisible),
        sftpLoading: readonly(sftpLoading),
        sftpError: readonly(sftpError),
        currentSftpPath: readonly(currentSftpPath),
        sftpFiles: readonly(sftpFiles),
        isSftpActionInProgress: readonly(isSftpActionInProgress),
        localUploadProgress: readonly(localUploadProgress),
        remoteUploadProgress: readonly(remoteUploadProgress),
        uploadStatusText: readonly(uploadStatusText),
        uploadSpeed: readonly(uploadSpeed),
        sftpUploadSpeed: readonly(sftpUploadSpeed),
        monitorVisible: readonly(monitorVisible),
        isMonitoring: readonly(isMonitoring),
        systemStats: readonly(systemStats),
        dockerContainers: readonly(dockerContainers),

        // Methods
        connect,
        disconnect,
        setTerminalInstance,
        sendTerminalData,
        sendTerminalResize,
        toggleSftpPanel,
        fetchSftpList,
        downloadSftpFiles,
        uploadSftpFile,
        toggleMonitorPanel,

    };
}
