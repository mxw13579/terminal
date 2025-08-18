import { ref, readonly, watch } from 'vue';
import { formatSpeed } from '../utils/formatters.js';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { AuthService } from '../services/auth.js';
import { StreamingFileService } from '../services/streamingFile.js';

// Composable函数接收一个配置对象，用于与外部通信（如显示Modal）
export function useTerminal(options = {}) {
    const { onShowModal = () => {}, getStompClient: getExternalClient } = options;

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
    // 不再需要分片上传相关变量，已改为HTTP流式传输
    let uploadStartTime = 0;
    let uploadBytesSent = 0;
    let currentCredentials = null; // 存储当前连接凭据，用于重连
    let stompSessionId = null; // 存储STOMP会话ID
    // 外部 STOMP 客户端复用标记与订阅状态
    let usingExternalClient = false;
    let subscriptionsReady = false;
    let externalAttachTimer = null;
    const streamingFileService = new StreamingFileService(() => stompSessionId);

    // 声明函数变量，稍后定义
    let tryAttachExternalClient;
    let scheduleExternalAttach;
    let subscribeToQueues;
    let testStompSubscription;
    let forceResubscribe;

    // 立即定义所有函数，避免作用域问题
    
    // 强制重新订阅所有队列的函数
    forceResubscribe = () => {
        console.log('🔄 强制重新订阅所有队列...');
        console.log('🔄 当前STOMP客户端:', stompClient);
        console.log('🔄 客户端连接状态:', stompClient?.connected);
        console.log('🔄 当前会话ID:', stompSessionId);
        
        if (stompClient && stompClient.connected) {
            console.log('🔄 清理现有订阅...');
            // 先清理现有订阅
            Object.keys(stompClient.subscriptions || {}).forEach(subId => {
                console.log('🔄 取消订阅:', subId);
                stompClient.subscriptions[subId].unsubscribe();
            });
            
            // 重新订阅
            console.log('🔄 重新执行订阅...');
            subscribeToQueues();
            
            // 重新请求会话信息
            setTimeout(() => {
                console.log('🔄 重新请求会话信息...');
                stompClient.publish({
                    destination: '/app/session/info',
                    body: JSON.stringify({ request: 'sessionInfo' })
                });
            }, 1000);
        } else {
            console.error('🔄 STOMP客户端未连接，无法重新订阅');
        }
    };

    // 测试STOMP订阅功能
    testStompSubscription = () => {
        console.log('🔍 开始测试STOMP订阅...');
        console.log('🔍 STOMP客户端连接状态:', stompClient?.connected);
        console.log('🔍 当前会话ID:', stompSessionId);
        console.log('🔍 当前订阅列表:', Object.keys(stompClient?.subscriptions || {}));
        
        // 发送一个测试消息请求
        if (stompClient && stompClient.connected) {
            console.log('🔍 发送测试消息到后端...');
            stompClient.publish({
                destination: '/app/test/subscription',
                body: JSON.stringify({ 
                    test: 'subscription',
                    sessionId: stompSessionId 
                })
            });
        } else {
            console.error('🔍 STOMP客户端未连接，无法发送测试消息');
        }
    };

    // 订阅所有队列
    subscribeToQueues = () => {
        console.log('🚀 开始订阅所有STOMP队列...');
        console.log('🚀 当前STOMP客户端状态:', stompClient?.connected);
        console.log('🚀 当前subscriptionsReady状态:', subscriptionsReady);
        console.log('🚀 STOMP客户端对象:', stompClient);
        console.log('🚀 STOMP连接URL:', stompClient?.webSocket?.url);
        
        // 首先订阅会话信息（最重要）
        const sessionSub = stompClient.subscribe('/user/queue/session', (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('📨 收到会话消息:', data);
                if (data.type === 'session_established' && data.sessionId) {
                    stompSessionId = data.sessionId;
                    console.log('✅ 收到后端发送的STOMP会话ID:', stompSessionId);
                    console.log('🔗 当前streamingFileService的sessionIdProvider将返回:', stompSessionId);
                    
                    // 🔧 调试：更新window对象中的会话ID
                    window.stompSessionId = stompSessionId;
                    console.log('🔧 已更新window.stompSessionId:', stompSessionId);
                }
            } catch (e) {
                console.error('Error processing session message:', e);
            }
        });
        console.log('✅ 会话订阅完成:', sessionSub);
        
        // 订阅终端输出
        const terminalSub = stompClient.subscribe('/user/queue/terminal', (message) => {
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
        const errorSub = stompClient.subscribe('/user/queue/errors', (message) => {
            try {
                const data = JSON.parse(message.body);
                console.log('收到错误消息:', data);
                
                // 提取错误信息，支持多种格式
                let errorMessage = '未知错误';
                if (data.payload) {
                    errorMessage = data.payload;
                } else if (data.message) {
                    errorMessage = data.message;
                } else if (data.error) {
                    errorMessage = data.error;
                } else if (typeof data === 'string') {
                    errorMessage = data;
                } else {
                    errorMessage = JSON.stringify(data);
                }
                
                onShowModal("终端错误: " + errorMessage);
            } catch (e) {
                console.error('Error processing terminal error:', e);
                onShowModal("终端错误: 消息解析失败");
            }
        });

        // 订阅SFTP响应（统一路由）
        stompClient.subscribe('/user/queue/sftp', (message) => {
            try {
                const data = JSON.parse(message.body);
                const messageType = data.type;
                console.log('收到SFTP消息，类型:', messageType, '数据:', data);
                
                // 根据消息类型分发到不同的处理器
                switch (messageType) {
                    case 'sftp_list_response':
                        handleSftpListResponse(data);
                        break;
                    case 'sftp_download_response':
                        handleSftpDownloadResponse(data);
                        break;
                    // 彻底禁用旧的WebSocket上传消息处理，避免与新流式上传冲突
                    case 'sftp_remote_progress':
                    case 'sftp_upload_final_success':
                    case 'upload_completed':
                    case 'upload_cancelled':
                    case 'upload_failed':
                        console.log('忽略旧的上传相关STOMP消息:', messageType, '- 现已使用HTTP流式传输');
                        break;
                    case 'sftp_error':
                        handleSftpError(data);
                        break;
                    default:
                        console.warn('未知的SFTP消息类型:', messageType);
                        break;
                }
            } catch (e) {
                console.error('Error processing SFTP message:', e);
                onShowModal('处理SFTP响应出错: ' + e.message);
            }
        });

        // 订阅上传进度数据（实时后端传输进度）
        console.log('🔔 正在订阅STOMP上传进度消息: /user/queue/upload/progress');
        console.log('🔔 STOMP客户端详情:', {
            connected: stompClient.connected,
            active: stompClient.active,
            subscriptions: stompClient.subscriptions,
            webSocketUrl: stompClient.webSocket?.url,
            webSocketReadyState: stompClient.webSocket?.readyState
        });
        
        const uploadProgressSub = stompClient.subscribe('/user/queue/upload/progress', (message) => {
            try {
                console.log('📨 🎉 STOMP进度消息收到!!! - 原始message:', message);
                console.log('📨 🎉 STOMP进度消息收到!!! - message.body:', message.body);
                console.log('📨 🎉 STOMP进度消息收到!!! - headers:', message.headers);
                
                const progressData = JSON.parse(message.body);
                console.log('🎯 🎉 收到后端实时上传进度:', progressData);
                
                // 优先使用新的回调管理器
                if (window.streamingCallbackManager) {
                    console.log('📞 🎉 使用回调管理器处理STOMP进度');
                    window.streamingCallbackManager.handleProgress(progressData);
                } else if (streamingFileService && streamingFileService.progressManager) {
                    console.log('📞 🎉 使用ProgressStateManager处理STOMP进度');
                    streamingFileService.progressManager.handleStompProgress(progressData);
                } else if (window.streamingProgressCallback) {
                    console.log('📞 🎉 使用全局回调处理STOMP进度');
                    window.streamingProgressCallback(progressData);
                } else {
                    console.warn('⚠️  所有进度处理机制都未设置，缓存STOMP消息');
                    // 缓存消息，等待回调设置
                    if (!window.cachedStompMessages) {
                        window.cachedStompMessages = [];
                    }
                    window.cachedStompMessages.push(progressData);
                    
                    // 5秒后清理缓存，避免内存泄漏
                    setTimeout(() => {
                        if (window.cachedStompMessages) {
                            const index = window.cachedStompMessages.indexOf(progressData);
                            if (index > -1) {
                                window.cachedStompMessages.splice(index, 1);
                            }
                        }
                    }, 5000);
                    }
                }
            } catch (e) {
                console.error('❌ 处理上传进度消息失败:', e, 'message:', message);
            }
        });
        console.log('✅ 上传进度订阅完成:', uploadProgressSub);
        console.log('✅ 订阅对象详情:', {
            id: uploadProgressSub?.id,
            destination: uploadProgressSub?.destination,
            callback: typeof uploadProgressSub?.callback
        });
        console.log('✅ 当前所有订阅:', Object.keys(stompClient.subscriptions || {}));
        console.log('✅ STOMP客户端状态:', stompClient.connected);

        // 🔧 添加通用消息监听器来捕获所有可能的进度消息
        console.log('🔧 添加通用进度消息监听器...');
        
        // 尝试订阅可能的进度队列变体
        const progressVariants = [
            '/user/queue/upload/progress',
            '/queue/upload/progress', 
            '/user/queue/progress',
            '/topic/upload/progress'
        ];
        
        progressVariants.forEach(destination => {
            try {
                stompClient.subscribe(destination, (message) => {
                    console.log(`🔍 收到${destination}的消息:`, message.body);
                    try {
                        const data = JSON.parse(message.body);
                        if (data.filename || data.uploadId || data.percentage !== undefined) {
                            console.log('🎯 这看起来像进度消息:', data);
                            if (window.streamingProgressCallback) {
                                window.streamingProgressCallback(data);
                            }
                        }
                    } catch (e) {
                        console.log('无法解析为JSON:', e);
                    }
                });
                console.log(`✅ 已订阅变体: ${destination}`);
            } catch (e) {
                console.log(`❌ 订阅${destination}失败:`, e.message);
            }
        });

        // 订阅监控数据
        stompClient.subscribe('/user/queue/monitor', (message) => {
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
    
    // 若外部提供了 STOMP 客户端获取方法，尝试复用并在连接后订阅
    tryAttachExternalClient = () => {
        if (!getExternalClient) return;
        const client = getExternalClient();
        console.log('🔍 tryAttachExternalClient - 外部客户端:', client);
        console.log('🔍 外部客户端连接状态:', client?.connected);
        if (!client) return;
        if (client !== stompClient) {
            console.log('🔍 使用外部STOMP客户端，替换当前客户端');
            stompClient = client;
            usingExternalClient = true;
            subscriptionsReady = false;
            
            // 🔧 调试：暴露外部客户端到window对象
            window.stompClient = stompClient;
            window.testStompSubscription = testStompSubscription;
            window.subscribeToQueues = subscribeToQueues;
            window.forceResubscribe = forceResubscribe;
            console.log('🔧 已暴露外部STOMP客户端到window');
        }
        
        // 🔧 强制检查并订阅
        if (stompClient && stompClient.connected) {
            console.log('🔍 外部客户端已连接，当前订阅状态:', subscriptionsReady);
            console.log('🔍 当前订阅数量:', Object.keys(stompClient.subscriptions || {}).length);
            
            if (!subscriptionsReady || Object.keys(stompClient.subscriptions || {}).length === 0) {
                console.log('🔍 开始订阅队列...');
                subscribeToQueues();
                startTerminalOutputForwarding();
                
                // 对于外部客户端，也请求会话信息
                setTimeout(() => {
                    console.log('外部客户端：发送会话信息请求...');
                    if (stompClient && stompClient.connected) {
                        stompClient.publish({
                            destination: '/app/session/info',
                            body: JSON.stringify({ request: 'sessionInfo' })
                        });
                    }
                }, 500);
                
                subscriptionsReady = true;
            } else {
                console.log('🔍 订阅已完成，跳过重复订阅');
            }
            
            if (externalAttachTimer) {
                clearInterval(externalAttachTimer);
                externalAttachTimer = null;
            }
        }
    };

    scheduleExternalAttach = () => {
        if (!getExternalClient) return;
        // 立即尝试一次并短暂轮询等待外部连接就绪
        tryAttachExternalClient();
        let attempts = 0;
        externalAttachTimer = setInterval(() => {
            attempts += 1;
            tryAttachExternalClient();
            if (subscriptionsReady || attempts > 60) { // ~30s (500ms * 60)
                clearInterval(externalAttachTimer);
                externalAttachTimer = null;
            }
        }, 500);
    };

    // --- STOMP Connection Logic ---
    const connect = async (details) => {
        // 若复用外部连接，则不在此创建新连接
        if (getExternalClient) {
            host.value = details.host;
            port.value = details.port;
            user.value = details.user;
            isConnecting.value = true;
            return;
        }
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
                console.log('🎉 STOMP Connected: ' + frame);
                console.log('🎉 STOMP Frame details:', frame);
                console.log('🎉 WebSocket URL:', stompClient.webSocket.url);
                console.log('🎉 STOMP Client ID:', stompClient.webSocket.protocol);
                console.log('🎉 当前时间:', new Date().toISOString());
                
                // 会话ID将由后端通过消息发送，这里先不设置
                console.log('⏳ 等待后端发送STOMP会话ID...');
                
                isConnecting.value = false;
                isConnected.value = true;

                // 订阅消息队列
                console.log('📋 准备订阅所有队列...');
                subscribeToQueues();
                
                // 🔧 调试：暴露STOMP客户端到window对象
                window.stompClient = stompClient;
                window.stompSessionId = stompSessionId;
                window.testStompSubscription = testStompSubscription;
                window.subscribeToQueues = subscribeToQueues;
                window.forceResubscribe = forceResubscribe;
                console.log('🔧 已暴露调试对象到window: stompClient, stompSessionId, testStompSubscription, subscribeToQueues, forceResubscribe');
                
                // 测试：请求会话信息
                setTimeout(() => {
                    console.log('📤 发送会话信息请求...');
                    if (stompClient && stompClient.connected) {
                        stompClient.publish({
                            destination: '/app/session/info',
                            body: JSON.stringify({ request: 'sessionInfo' })
                        });
                    }
                }, 1000); // 延迟1秒确保订阅完成
                
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

    // 如果由外部连接管理器提供 STOMP 客户端，则开始轮询等待其就绪并订阅
    if (getExternalClient) {
        scheduleExternalAttach();
    }

    // 🔧 添加强制检查机制 - 确保订阅正常工作
    setTimeout(() => {
        console.log('🔧 延迟检查STOMP订阅状态...');
        console.log('🔧 STOMP客户端:', window.stompClient?.connected);
        console.log('🔧 订阅数量:', Object.keys(window.stompClient?.subscriptions || {}).length);
        
        if (window.stompClient?.connected && Object.keys(window.stompClient?.subscriptions || {}).length === 0) {
            console.log('🔧 检测到STOMP客户端已连接但无订阅，强制执行订阅...');
            window.subscribeToQueues?.();
        }
    }, 2000); // 2秒后检查

    const disconnect = () => {
        if (stompClient && !usingExternalClient) {
            stompClient.deactivate();
        }
        if (term) {
            term.write('\r\n🔌 连接已由用户关闭。\r\n');
        }
        
        // 清理认证状态
        AuthService.clearToken();
        currentCredentials = null;
        stompSessionId = null;
        
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
        // 使用新的流式上传架构，不再处理旧的STOMP上传消息
        console.log('收到旧的STOMP上传消息，已忽略:', data.type);
        
        // 注释掉旧的逻辑以避免与新的流式上传冲突
        /*
        if (data.type === 'sftp_remote_progress') {
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
        */
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
        stompSessionId = null;
        
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
        // 清理遗留变量，现在使用HTTP流式传输

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
        } else {
            console.warn('未连接到STOMP客户端，无法发送终端数据');
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
        
        console.log('准备上传文件:', file.name);
        console.log('当前stompSessionId:', stompSessionId);
        console.log('当前连接状态:', {
            isConnected: isConnected.value,
            stompClientConnected: stompClient && stompClient.connected
        });
        
        isSftpActionInProgress.value = true;
        sftpError.value = '';
        localUploadProgress.value = 0;
        remoteUploadProgress.value = 0;
        uploadStatusText.value = `开始流式传输: ${file.name}`;
        uploadSpeed.value = '';
        sftpUploadSpeed.value = '';
        
        // 标记是否已处理完成
        let uploadHandled = false;
        
        try {
            // 进度回调
            const onProgress = (progressData) => {
                localUploadProgress.value = progressData.percentage;
                uploadSpeed.value = streamingFileService.formatSpeed(progressData.speed);
                uploadStatusText.value = `正在流式传输: ${progressData.percentage}%`;
            };

            // 完成回调 - 现在意味着整个文件已100%成功上传
            const onComplete = (result) => {
                uploadStatusText.value = '流式传输完成！';
                localUploadProgress.value = 100; // 确保进度条显示100%
                onShowModal(`文件 "${file.name}" 流式传输成功`);
                
                // 立即刷新文件列表，因为文件已经完全上传完成
                fetchSftpList(currentSftpPath.value);
                
                // 短暂延迟后隐藏进度条
                setTimeout(() => {
                    isSftpActionInProgress.value = false;
                }, 1500);
                
                // 标记已成功处理，避免finally块重复设置
                uploadHandled = true;
            };

            // 错误回调
            const onError = (error) => {
                console.error('上传失败:', error);
                sftpError.value = `上传失败: ${error.message}`;
                onShowModal(`上传失败: ${error.message}`);
            };

            // 启动真正的流式上传 (使用新的流式传输架构)
            await streamingFileService.streamUploadFile(
                file, 
                currentSftpPath.value,
                onProgress,
                onComplete,
                onError
            );

        } catch (error) {
            console.error('启动上传失败:', error);
            
            // 运行诊断
            try {
                const diagnosis = await streamingFileService.diagnoseConnection();
                console.log('连接诊断结果:', diagnosis);
                
                let errorMessage = `启动上传失败: ${error.message}`;
                if (diagnosis.recommendations.length > 0) {
                    errorMessage += '\n\n建议检查:\n' + diagnosis.recommendations.join('\n');
                }
                sftpError.value = errorMessage;
                onShowModal(errorMessage);
            } catch (diagError) {
                console.error('诊断失败:', diagError);
                sftpError.value = `启动上传失败: ${error.message}`;
                onShowModal(`启动上传失败: ${error.message}`);
            }
        } finally {
            // 只有在未成功处理的情况下才立即隐藏进度条
            if (!uploadHandled) {
                isSftpActionInProgress.value = false;
            }
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
        testStompSubscription, // 添加测试函数
        forceResubscribe, // 添加强制重新订阅函数
    };
}
