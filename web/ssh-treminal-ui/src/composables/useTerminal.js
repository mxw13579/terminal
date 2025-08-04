import { ref, readonly, watch } from 'vue';
import { formatSpeed } from '../utils/formatters.js';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

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

    let stompClient = null;
    let term = null;
    let sendNextChunk = null;
    let uploadStartTime = 0;
    let uploadBytesSent = 0;

    // --- STOMP Connection Logic ---
    const connect = (details) => {
        host.value = details.host;
        port.value = details.port;
        user.value = details.user;
        isConnecting.value = true;

        // 创建STOMP客户端，SSH参数通过连接头传递
        stompClient = new Client({
            webSocketFactory: () => new SockJS('/ws/terminal'),
            connectHeaders: {
                'host': details.host,
                'port': details.port || '22',
                'user': details.user,
                'password': details.password
            },
            debug: function (str) {
                console.log('STOMP: ' + str);
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        stompClient.onConnect = (frame) => {
            console.log('STOMP Connected: ' + frame);
            isConnecting.value = false;
            isConnected.value = true;

            // 订阅消息队列
            subscribeToQueues();
            
            // SSH连接由StompAuthenticationInterceptor在CONNECT时建立
            // 启动终端输出转发
            startTerminalOutputForwarding();
        };

        stompClient.onStompError = (frame) => {
            console.error('STOMP Error: ' + frame.headers['message']);
            console.error('Additional details: ' + frame.body);
            isConnecting.value = false;
            onShowModal("STOMP连接错误: " + frame.headers['message']);
        };

        stompClient.onDisconnect = () => {
            console.log('STOMP Disconnected');
            if (isConnected.value) {
                onShowModal("连接已断开");
            }
            resetState();
        };

        stompClient.activate();
    };

    const startTerminalOutputForwarding = () => {
        // 请求启动终端输出转发
        if (stompClient && stompClient.connected) {
            stompClient.publish({
                destination: '/app/terminal/start-forwarding',
                body: JSON.stringify({})
            });
        }
    };

    const subscribeToQueues = () => {
        // 订阅终端输出
        stompClient.subscribe('/user/queue/terminal/output', (message) => {
            try {
                const data = JSON.parse(message.body);
                if (term && data.payload) {
                    term.write(data.payload);
                }
            } catch (e) {
                console.error('Error processing terminal output:', e);
            }
        });

        // 订阅终端错误
        stompClient.subscribe('/user/queue/terminal/error', (message) => {
            try {
                const data = JSON.parse(message.body);
                onShowModal("终端错误: " + data.payload);
            } catch (e) {
                console.error('Error processing terminal error:', e);
            }
        });

        // 订阅SFTP响应
        stompClient.subscribe('/user/queue/sftp/list', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSftpListResponse(data);
            } catch (e) {
                console.error('Error processing SFTP list response:', e);
            }
        });

        stompClient.subscribe('/user/queue/sftp/upload', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSftpUploadResponse(data);
            } catch (e) {
                console.error('Error processing SFTP upload response:', e);
            }
        });

        stompClient.subscribe('/user/queue/sftp/download', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSftpDownloadResponse(data);
            } catch (e) {
                console.error('Error processing SFTP download response:', e);
            }
        });

        stompClient.subscribe('/user/queue/sftp/error', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleSftpError(data);
            } catch (e) {
                console.error('Error processing SFTP error:', e);
            }
        });

        // 订阅监控数据
        stompClient.subscribe('/user/queue/monitor/data', (message) => {
            try {
                const data = JSON.parse(message.body);
                handleMonitorUpdate(data);
            } catch (e) {
                console.error('Error processing monitor data:', e);
            }
        });

        // 订阅全局错误
        stompClient.subscribe('/user/queue/errors', (message) => {
            try {
                const data = JSON.parse(message.body);
                onShowModal("错误: " + data.payload);
            } catch (e) {
                console.error('Error processing global error:', e);
            }
        });
    };

    const disconnect = () => {
        if (stompClient) {
            stompClient.deactivate();
        }
        if (term) {
            term.write('\r\n🔌 连接已由用户关闭。\r\n');
        }
        resetState();
    };

    // --- Message Handlers ---
    const handleSftpListResponse = (data) => {
        if (data.type === 'sftp_list_response') {
            sftpLoading.value = false;
            sftpError.value = '';
            currentSftpPath.value = data.path;
            sftpFiles.value = data.files;
        }
    };

    const handleSftpUploadResponse = (data) => {
        if (data.type === 'sftp_upload_chunk_success') {
            localUploadProgress.value = Math.round(((data.chunkIndex + 1) / data.totalChunks) * 100);
            if (sendNextChunk) sendNextChunk();
        } else if (data.type === 'sftp_remote_progress') {
            remoteUploadProgress.value = data.progress;
            sftpUploadSpeed.value = formatSpeed(data.speed);
            uploadStatusText.value = `正在上传到服务器... ${data.progress}%`;
        } else if (data.type === 'sftp_upload_final_success') {
            remoteUploadProgress.value = 100;
            isSftpActionInProgress.value = false;
            uploadStatusText.value = '上传完成！';
            sftpUploadSpeed.value = '';
            onShowModal(data.message || "上传成功!");
            fetchSftpList(data.path);
        }
    };

    const handleSftpDownloadResponse = (data) => {
        if (data.type === 'sftp_download_response') {
            handleFileDownload(data.filename, data.content);
        }
    };

    const handleSftpError = (data) => {
        sftpLoading.value = false;
        isSftpActionInProgress.value = false;
        sftpError.value = `SFTP Error: ${data.message}`;
        onShowModal(`SFTP Error: ${data.message}`);
    };

    const handleMonitorUpdate = (data) => {
        if (data.type === 'monitor_update') {
            isMonitoring.value = true;
            isLoading.value = false;
            systemStats.value = data.payload;
            dockerContainers.value = data.payload.dockerContainers || [];
        }
    };

    const resetState = () => {
        if (stompClient) {
            stompClient.deactivate();
            stompClient = null;
        }
        if (term) term.dispose();
        
        // 重置所有状态
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

    // --- Public API Methods ---
    const setTerminalInstance = (instance) => { term = instance; };
    
    const sendTerminalData = (data) => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({
                destination: '/app/terminal/data',
                body: JSON.stringify({ data: data })
            });
        }
    };
    
    const sendTerminalResize = (size) => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({
                destination: '/app/terminal/resize',
                body: JSON.stringify({ cols: size.cols, rows: size.rows })
            });
        }
    };

    const toggleMonitorPanel = () => {
        monitorVisible.value = !monitorVisible.value;
    };

    // 监听 monitorVisible 变化来启动/停止监控
    watch(monitorVisible, (newValue) => {
        if (stompClient && stompClient.connected) {
            if (newValue) {
                if (!systemStats.value) {
                    isLoading.value = true;
                }
                stompClient.publish({
                    destination: '/app/monitor/start',
                    body: JSON.stringify({})
                });
            } else {
                stompClient.publish({
                    destination: '/app/monitor/stop',
                    body: JSON.stringify({})
                });
                isMonitoring.value = false;
            }
        }
    });

    const toggleSftpPanel = () => {
        sftpVisible.value = !sftpVisible.value;
        if (sftpVisible.value && sftpFiles.value.length === 0) {
            fetchSftpList();
        }
    };

    const fetchSftpList = (path = '.') => {
        if (stompClient && stompClient.connected) {
            sftpLoading.value = true;
            sftpError.value = '';
            stompClient.publish({
                destination: '/app/sftp/list',
                body: JSON.stringify({ path: path })
            });
        }
    };

    const downloadSftpFiles = (paths) => {
        if (paths.length === 0 || !stompClient || !stompClient.connected) return;
        isSftpActionInProgress.value = true;
        sftpError.value = '';
        stompClient.publish({
            destination: '/app/sftp/download',
            body: JSON.stringify({ paths: paths })
        });
    };

    const uploadSftpFile = (file) => {
        if (!stompClient || !stompClient.connected) return;
        
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
                
                stompClient.publish({
                    destination: '/app/sftp/upload',
                    body: JSON.stringify({
                        path: currentSftpPath.value,
                        filename: file.name,
                        chunkIndex,
                        totalChunks,
                        content: base64Content
                    })
                });
                chunkIndex++;
            };
            reader.onerror = () => { 
                onShowModal("读取文件失败！"); 
                isSftpActionInProgress.value = false; 
                sendNextChunk = null; 
            };
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