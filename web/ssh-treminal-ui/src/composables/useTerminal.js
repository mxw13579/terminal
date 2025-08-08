import { ref, readonly, watch } from 'vue';
import { formatSpeed } from '../utils/formatters.js';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { AuthService } from '../services/auth.js';
import { StreamingFileService } from '../services/streamingFile.js';

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
    let terminalOutputBuffer = [];
    let terminalOutputTimer = null;
    let resizeTimeout = null;
    let sendNextChunk = null;
    let uploadStartTime = 0;
    let uploadBytesSent = 0;
    let currentCredentials = null; // 存储当前连接凭据，用于重连
    const streamingFileService = new StreamingFileService();

    // --- STOMP Connection Logic ---
    const connect = async (details) => {
        try {
            host.value = details.host;
            port.value = details.port;
            user.value = details.user;
            isConnecting.value = true;

            // 存储凭据用于重连
            currentCredentials = { ...details };

            console.debug('开始安全连接流程...');

            // 获取会话令牌（使用RSA加密的凭据）
            console.debug('获取会话令牌...');
            await AuthService.getSessionToken(details);
            
            // 获取连接头（包含Authorization令牌）
            const connectHeaders = AuthService.getConnectionHeaders();
            if (!connectHeaders) {
                throw new Error('无法获取有效的认证令牌');
            }

            console.debug('使用安全令牌创建STOMP连接...');

            // 创建STOMP客户端，使用Authorization头替代明文密码
            stompClient = new Client({
                webSocketFactory: () => new SockJS('/ws/terminal'),
                connectHeaders,
                debug: function (str) {
                    // 环境守卫：仅在开发环境输出详细日志
                    if (import.meta.env?.MODE === 'development' || process.env.NODE_ENV === 'development') {
                        console.log('STOMP: ' + str);
                    }
                },
                // 指数退避重连策略（带抖动）
                reconnectDelay: () => {
                    const attempt = stompClient.reconnectAttempts || 0;
                    const baseDelay = 1000;
                    const maxDelay = 30000;
                    const delay = Math.min(baseDelay * Math.pow(2, attempt), maxDelay);
                    const jitter = Math.random() * 1000;
                    return delay + jitter;
                },
                heartbeatIncoming: 4000,
                heartbeatOutgoing: 4000,
            });

            // 连接成功处理
            stompClient.onConnect = (frame) => {
                console.log('STOMP Connected: ' + frame);
                console.log('STOMP Frame details:', frame);
                console.log('Session ID:', stompClient.webSocket.url);
                isConnecting.value = false;
                isConnected.value = true;

                // 订阅消息队列
                subscribeToQueues();
                
                // SSH连接由StompAuthenticationInterceptor在CONNECT时建立
                // 启动终端输出转发
                startTerminalOutputForwarding();
            };

            // STOMP错误处理
            stompClient.onStompError = async (frame) => {
                console.error('STOMP Error: ' + frame.headers['message']);
                console.error('Additional details: ' + frame.body);
                
                const errorMessage = frame.headers['message'] || '连接认证失败';
                
                // 检查是否为认证错误，如果是则尝试重新获取令牌
                if (errorMessage.includes('认证') || errorMessage.includes('令牌') || 
                    errorMessage.includes('授权') || errorMessage.includes('Authentication')) {
                    
                    console.warn('检测到认证错误，尝试重新获取令牌...');
                    
                    const retryHeaders = await AuthService.handleConnectionRetry(currentCredentials);
                    if (retryHeaders) {
                        console.info('令牌更新成功，重新尝试连接...');
                        // 更新连接头并重连
                        stompClient.connectHeaders = retryHeaders;
                        return; // 让STOMP客户端处理重连
                    }
                }
                
                isConnecting.value = false;
                onShowModal("连接错误: " + errorMessage);
            };

            // 断开连接处理
            stompClient.onDisconnect = async () => {
                console.log('STOMP Disconnected');
                
                if (isConnected.value) {
                    console.info('连接意外断开，尝试恢复...');
                    
                    // 尝试通过令牌刷新恢复连接
                    const retryHeaders = await AuthService.handleConnectionRetry(currentCredentials);
                    if (retryHeaders) {
                        console.info('准备使用新令牌重连...');
                        stompClient.connectHeaders = retryHeaders;
                        return;
                    } else {
                        onShowModal("连接已断开，请重新连接");
                    }
                }
                
                resetState();
            };

            // 激活STOMP连接
            stompClient.activate();

        } catch (error) {
            console.error('连接失败:', error);
            isConnecting.value = false;
            
            // 提供用户友好的错误信息
            let userMessage = error.message;
            if (error.message.includes('不支持')) {
                userMessage = '浏览器不支持必要的安全功能，请升级到最新版本的Chrome、Firefox或Edge';
            } else if (error.message.includes('网络')) {
                userMessage = '网络连接失败，请检查网络连接后重试';
            } else if (error.message.includes('凭据')) {
                userMessage = '登录信息验证失败，请检查主机地址、用户名和密码';
            }
            
            onShowModal("连接失败: " + userMessage);
            resetState();
        }
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
        console.log('Starting to subscribe to queues...');
        
        // 订阅终端输出
        const terminalSub = stompClient.subscribe('/user/queue/terminal/output', (message) => {
            console.log('Received terminal output message:', message);
            try {
                const data = JSON.parse(message.body);
                console.log('Parsed terminal data:', data);
                if (term && data.payload) {
                    // 使用缓冲区和requestAnimationFrame优化输出
                    bufferTerminalOutput(data.payload);
                } else {
                    console.warn('Cannot write to terminal:', { term: !!term, payload: !!data.payload });
                }
            } catch (e) {
                console.error('Error processing terminal output:', e, 'Message body:', message.body);
            }
        });
        console.log('Subscribed to terminal output:', terminalSub);

        // 订阅终端错误
        const errorSub = stompClient.subscribe('/user/queue/terminal/error', (message) => {
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
        
        // 清理认证状态
        AuthService.clearToken();
        currentCredentials = null;
        
        resetState();
    };

    // --- Terminal Output Buffering ---
    const bufferTerminalOutput = (data) => {
        terminalOutputBuffer.push(data);
        
        // 如果没有定时器运行，启动一个
        if (!terminalOutputTimer) {
            terminalOutputTimer = requestAnimationFrame(flushTerminalOutput);
        }
    };

    const flushTerminalOutput = () => {
        terminalOutputTimer = null;
        
        if (!term || terminalOutputBuffer.length === 0) {
            return;
        }

        // 合并缓冲区内容，限制单次写入的数据量
        const maxChunkSize = 4096; // 4KB per frame
        let totalSize = 0;
        let flushData = '';
        
        while (terminalOutputBuffer.length > 0 && totalSize < maxChunkSize) {
            const data = terminalOutputBuffer.shift();
            if (totalSize + data.length <= maxChunkSize) {
                flushData += data;
                totalSize += data.length;
            } else {
                // 数据太大，放回缓冲区，下次处理
                terminalOutputBuffer.unshift(data);
                break;
            }
        }

        if (flushData) {
            try {
                term.write(flushData);
            } catch (e) {
                console.error('Terminal write error:', e);
            }
        }

        // 如果还有数据，继续下一帧
        if (terminalOutputBuffer.length > 0) {
            terminalOutputTimer = requestAnimationFrame(flushTerminalOutput);
        }
    };

    // --- Debounced Resize Handler ---
    const debouncedTerminalResize = (size) => {
        if (resizeTimeout) {
            clearTimeout(resizeTimeout);
        }
        
        resizeTimeout = setTimeout(() => {
            if (stompClient && stompClient.connected) {
                stompClient.publish({
                    destination: '/app/terminal/resize',
                    body: JSON.stringify({ cols: size.cols, rows: size.rows })
                });
            }
        }, 150); // 150ms debounce
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
            // Fallback to old base64 method for compatibility
            handleLegacyFileDownload(data.filename, data.content);
        }
    };

    const handleLegacyFileDownload = (filename, base64Content) => {
        try {
            const byteCharacters = atob(base64Content);
            const byteNumbers = Array.from(byteCharacters, char => char.charCodeAt(0));
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray]);

            streamingFileService.triggerDownload(filename, blob);
            onShowModal(`下载完成: ${filename}`);
        } catch (error) {
            console.error('Legacy download failed:', error);
            onShowModal("创建下载文件失败！");
        } finally {
            isSftpActionInProgress.value = false;
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
        
        // 清理缓冲区和定时器
        terminalOutputBuffer = [];
        if (terminalOutputTimer) {
            cancelAnimationFrame(terminalOutputTimer);
            terminalOutputTimer = null;
        }
        if (resizeTimeout) {
            clearTimeout(resizeTimeout);
            resizeTimeout = null;
        }
        
        // 清理认证状态
        AuthService.clearToken();
        currentCredentials = null;
        
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
        debouncedTerminalResize(size);
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

    const downloadSftpFiles = async (paths) => {
        if (paths.length === 0) return;
        
        isSftpActionInProgress.value = true;
        sftpError.value = '';

        try {
            // 创建取消控制器
            const abortController = new AbortController();
            
            // 进度回调
            const onProgress = (loaded, total, percentage) => {
                if (percentage !== undefined) {
                    console.log(`下载进度: ${percentage}% (${streamingFileService.formatFileSize(loaded)}/${streamingFileService.formatFileSize(total)})`);
                }
            };

            // 使用流式下载
            const { filename, blob } = await streamingFileService.downloadFiles(
                paths, 
                onProgress, 
                abortController.signal
            );

            // 触发下载
            streamingFileService.triggerDownload(filename, blob);
            
            onShowModal(`下载完成: ${filename}`);

        } catch (error) {
            console.error('下载失败:', error);
            sftpError.value = `下载失败: ${error.message}`;
            onShowModal(`下载失败: ${error.message}`);
        } finally {
            isSftpActionInProgress.value = false;
        }
    };

    const uploadSftpFile = async (file) => {
        if (!file) return;
        
        isSftpActionInProgress.value = true;
        sftpError.value = '';
        localUploadProgress.value = 0;
        remoteUploadProgress.value = 0;
        uploadStatusText.value = `准备上传: ${file.name}`;
        uploadSpeed.value = '';
        sftpUploadSpeed.value = '';
        
        try {
            // 进度回调
            const onProgress = (progressData) => {
                localUploadProgress.value = progressData.percentage;
                uploadSpeed.value = streamingFileService.formatSpeed(progressData.speed);
                uploadStatusText.value = `正在上传: ${progressData.percentage}%`;
            };

            // 完成回调
            const onComplete = (result) => {
                uploadStatusText.value = '上传完成！';
                onShowModal(`文件 "${file.name}" 上传成功`);
                // 刷新文件列表
                setTimeout(() => fetchSftpList(currentSftpPath.value), 1000);
            };

            // 错误回调
            const onError = (error) => {
                console.error('上传失败:', error);
                sftpError.value = `上传失败: ${error.message}`;
                onShowModal(`上传失败: ${error.message}`);
            };

            // 启动流式上传
            await streamingFileService.uploadFiles(
                [file], 
                currentSftpPath.value,
                onProgress,
                onComplete,
                onError
            );

        } catch (error) {
            console.error('启动上传失败:', error);
            sftpError.value = `启动上传失败: ${error.message}`;
            onShowModal(`启动上传失败: ${error.message}`);
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