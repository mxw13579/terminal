import { AuthService } from './auth.js';

/**
 * 上传进度状态管理器
 * 统一管理HTTP和STOMP的上传状态，解决冲突和时序问题
 */
class ProgressStateManager {
    constructor() {
        this.uploads = new Map();
        this.messageBuffer = new Map(); // 存储早到的STOMP消息
        this.pendingTimeouts = new Map(); // 管理超时清理
        this.filenameLookup = new Map(); // 文件名到uploadId的映射，解决竞争条件
        
        // 启动定期清理
        this.startPeriodicCleanup();
    }
    
    /**
     * 启动定期清理任务
     */
    startPeriodicCleanup() {
        // 每30秒清理一次过期的缓冲消息
        setInterval(() => {
            this.cleanupExpiredBuffers();
        }, 30000);
    }
    
    /**
     * 清理过期的缓冲消息
     */
    cleanupExpiredBuffers() {
        const now = Date.now();
        const maxAge = 5 * 60 * 1000; // 5分钟
        
        for (const [filename, messages] of this.messageBuffer.entries()) {
            const validMessages = messages.filter(msg => {
                const age = now - (msg.timestamp || 0);
                return age < maxAge;
            });
            
            if (validMessages.length !== messages.length) {
                console.log(`清理过期缓冲消息: ${filename}, 清理 ${messages.length - validMessages.length} 条`);
                if (validMessages.length > 0) {
                    this.messageBuffer.set(filename, validMessages);
                } else {
                    this.messageBuffer.delete(filename);
                }
            }
        }
        
        console.log(`完成定期清理，当前缓冲数量: ${this.messageBuffer.size}`);
    }

    /**
     * 创建新的上传状态跟踪
     */
    createUpload(uploadId, file, callbacks = {}) {
        const uploadState = {
            uploadId,
            filename: file.name,
            fileSize: file.size,
            status: 'starting',
            progress: {
                loaded: 0,
                total: file.size,
                percentage: 0,
                speed: 0
            },
            callbacks: {
                onProgress: callbacks.onProgress || (() => {}),
                onComplete: callbacks.onComplete || (() => {}),
                onError: callbacks.onError || (() => {})
            },
            backendUploadId: null,
            usingBackendProgress: false,
            httpCompleted: false,
            backendCompleted: false,
            startTime: Date.now(),
            lastProgressTime: Date.now(),
            lastProgressLoaded: 0,
            // 增加唯一标识符用于精确匹配
            uniqueKey: `${file.name}_${file.size}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
        };

        this.uploads.set(uploadId, uploadState);
        
        // 更新文件名映射，使用唯一键避免竞争条件
        this.filenameLookup.set(uploadState.uniqueKey, uploadId);
        
        // 检查是否有缓冲的消息
        this.processBufferedMessages(uploadState);
        
        console.log('📋 创建上传状态跟踪:', uploadId, uploadState);
        return uploadState;
    }

    /**
     * 处理缓冲的STOMP消息
     */
    processBufferedMessages(uploadState) {
        // 先查找精确匹配的唯一键
        const exactMessages = this.messageBuffer.get(uploadState.uniqueKey) || [];
        
        // 再查找文件名匹配的消息（兼容旧版本）
        const filenameMessages = this.messageBuffer.get(uploadState.filename) || [];
        
        const allMessages = [...exactMessages, ...filenameMessages];
        
        console.log(`🔍 检查${uploadState.filename}的缓冲消息:`, allMessages.length);
        
        allMessages.forEach(message => {
            console.log('🔄 处理缓冲的STOMP消息:', message);
            this.handleStompProgress(message);
        });

        // 清理已处理的消息
        this.messageBuffer.delete(uploadState.uniqueKey);
        this.messageBuffer.delete(uploadState.filename);
    }

    /**
     * 更新HTTP进度
     */
    updateHttpProgress(uploadId, progressData) {
        const uploadState = this.uploads.get(uploadId);
        if (!uploadState) return;

        // 只有在没有使用后端进度时才更新HTTP进度
        if (!uploadState.usingBackendProgress) {
            uploadState.progress = {
                loaded: progressData.loaded || 0,
                total: progressData.total || uploadState.fileSize,
                percentage: Math.round(progressData.percentage || 0),
                speed: progressData.speed || 0
            };
            uploadState.status = 'uploading';

            console.log(`📊 HTTP进度更新 (${uploadId}):`, uploadState.progress);
            uploadState.callbacks.onProgress(uploadState.progress);
        }
    }

    /**
     * 处理STOMP进度消息
     */
    handleStompProgress(progressData) {
        console.log('📨 处理STOMP进度消息:', progressData);
        
        // 增加时间戳
        if (!progressData.timestamp) {
            progressData.timestamp = Date.now();
        }
        
        // 查找对应的上传状态 - 优化查找逻辑
        let uploadState = this.findUploadByProgressData(progressData);

        if (!uploadState) {
            // 如果找不到对应的上传状态，缓存这个消息
            console.log(`🔍 未找到${progressData.filename}的上传状态，缓存消息`);
            
            // 使用多种键缓存，提高匹配概率
            const keys = [
                progressData.filename,
                progressData.uploadId,
                `${progressData.filename}_${progressData.totalBytes}`
            ].filter(Boolean);
            
            keys.forEach(key => {
                if (!this.messageBuffer.has(key)) {
                    this.messageBuffer.set(key, []);
                }
                this.messageBuffer.get(key).push(progressData);
            });
            
            // 设置超时清理
            setTimeout(() => {
                keys.forEach(key => {
                    const messages = this.messageBuffer.get(key) || [];
                    const filtered = messages.filter(msg => msg !== progressData);
                    if (filtered.length > 0) {
                        this.messageBuffer.set(key, filtered);
                    } else {
                        this.messageBuffer.delete(key);
                    }
                });
            }, 60000); // 1分钟后清理
            
            return;
        }

        // 切换到后端进度模式
        if (!uploadState.usingBackendProgress) {
            console.log('✅ 切换到后端进度模式:', progressData.filename);
            uploadState.usingBackendProgress = true;
        }

        // 更新后端uploadId
        if (progressData.uploadId && !uploadState.backendUploadId) {
            uploadState.backendUploadId = progressData.uploadId;
            console.log('🔗 设置后端uploadId:', progressData.uploadId);
        }

        // 更新进度
        uploadState.progress = {
            loaded: progressData.transferredBytes || 0,
            total: progressData.totalBytes || uploadState.fileSize,
            percentage: Math.round(progressData.percentage || 0),
            speed: progressData.speed || 0
        };
        uploadState.status = progressData.status === 'completed' ? 'completed' : 'uploading';

        console.log(`🎯 STOMP进度更新 (${uploadState.uploadId}):`, uploadState.progress);
        uploadState.callbacks.onProgress(uploadState.progress);

        // 检查是否完成
        if (progressData.status === 'completed' || progressData.percentage >= 100) {
            this.completeUpload(uploadState.uploadId, 'backend');
        }
    }

    /**
     * 通过进度数据查找上传状态（精确匹配）
     */
    findUploadByProgressData(progressData) {
        // 1. 优先通过uploadId查找
        if (progressData.uploadId) {
            for (const state of this.uploads.values()) {
                if (state.backendUploadId === progressData.uploadId) {
                    return state;
                }
            }
        }
        
        // 2. 通过文件名+大小匹配
        if (progressData.filename && progressData.totalBytes) {
            for (const state of this.uploads.values()) {
                if (state.filename === progressData.filename && 
                    state.fileSize === progressData.totalBytes) {
                    return state;
                }
            }
        }
        
        // 3. 只通过文件名匹配（兼容旧版本）
        if (progressData.filename) {
            for (const state of this.uploads.values()) {
                if (state.filename === progressData.filename) {
                    return state;
                }
            }
        }
        
        return null;
    }

    /**
     * HTTP完成处理
     */
    completeHttpUpload(uploadId, response) {
        const uploadState = this.uploads.get(uploadId);
        if (!uploadState) return;

        uploadState.httpCompleted = true;
        
        // 更新后端uploadId
        if (response.uploadId) {
            uploadState.backendUploadId = response.uploadId;
        }

        console.log(`✅ HTTP完成 (${uploadId}):`, response);

        // 如果后端直接返回完成状态
        if (response.status === 'completed') {
            this.completeUpload(uploadId, 'http');
        }
        // 否则等待STOMP进度消息
    }

    /**
     * 完成上传
     */
    completeUpload(uploadId, source = 'unknown') {
        const uploadState = this.uploads.get(uploadId);
        if (!uploadState) return;

        if (uploadState.backendCompleted) {
            console.log(`🔄 上传${uploadId}已完成，跳过重复处理`);
            return;
        }

        uploadState.backendCompleted = true;
        uploadState.status = 'completed';
        uploadState.progress.percentage = 100;
        uploadState.progress.loaded = uploadState.fileSize;

        console.log(`🏁 上传完成 (${uploadId}, 来源: ${source}):`, uploadState);

        uploadState.callbacks.onComplete({
            uploadId: uploadState.backendUploadId || uploadId,
            status: 'completed',
            message: '文件上传完成！'
        });

        // 延迟清理，避免重复完成事件
        setTimeout(() => {
            this.uploads.delete(uploadId);
        }, 1000);
    }

    /**
     * 处理上传错误
     */
    handleUploadError(uploadId, error, source = 'unknown') {
        const uploadState = this.uploads.get(uploadId);
        if (!uploadState) return;

        // 如果已经完成，忽略后续错误
        if (uploadState.backendCompleted) {
            console.log(`✅ 上传${uploadId}已完成，忽略${source}错误:`, error.message);
            return;
        }

        uploadState.status = 'error';
        console.log(`❌ 上传错误 (${uploadId}, 来源: ${source}):`, error.message);

        uploadState.callbacks.onError(error);
        this.uploads.delete(uploadId);
    }

    /**
     * 获取上传状态
     */
    getUploadState(uploadId) {
        return this.uploads.get(uploadId);
    }

    /**
     * 清理上传状态
     */
    cleanup(uploadId) {
        const uploadState = this.uploads.get(uploadId);
        if (uploadState) {
            // 清理文件名映射
            this.filenameLookup.delete(uploadState.uniqueKey);
            
            // 清理超时定时器
            const timeoutId = this.pendingTimeouts.get(uploadId);
            if (timeoutId) {
                clearTimeout(timeoutId);
                this.pendingTimeouts.delete(uploadId);
            }
        }
        
        this.uploads.delete(uploadId);
        console.log(`🗑️ 清理上传状态:`, uploadId);
    }
}

/**
 * HTTP流式文件传输服务
 * 替代基于WebSocket的base64传输，提供更高效的文件上传下载
 */
export class StreamingFileService {
    constructor(sessionIdProvider = null) {
        this.activeUploads = new Map();
        this.activeDownloads = new Map();
        this.sessionIdProvider = sessionIdProvider;
        this.progressManager = new ProgressStateManager();
        this.callbackInstanceId = null; // 用于跟踪回调实例
        
        // 自动检测后端地址
        this.backendBaseUrl = this.detectBackendUrl();
        console.log('自动检测到后端地址:', this.backendBaseUrl);
        
        // 注册清理回调
        this.setupCleanupHandlers();
    }
    
    /**
     * 设置清理回调
     */
    setupCleanupHandlers() {
        // 页面卸载时清理回调
        if (typeof window !== 'undefined') {
            window.addEventListener('beforeunload', () => {
                this.cleanupGlobalStompCallback();
            });
            
            // 可见性变化时清理（如切换标签页）
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'hidden') {
                    // 不在这里清理，只是暂停
                    console.log('🔇 页面隐藏，保持回调注册');
                } else if (document.visibilityState === 'visible') {
                    console.log('🔆 页面可见，恢复回调注册');
                }
            });
        }
    }
    
    /**
     * 自动检测后端地址
     * @returns {string}
     */
    detectBackendUrl() {
        // 检查是否有环境变量指定后端地址
        if (import.meta.env.VITE_BACKEND_URL) {
            return import.meta.env.VITE_BACKEND_URL;
        }
        
        const currentHost = window.location.hostname;
        const currentPort = window.location.port;
        const currentProtocol = window.location.protocol;
        
        // 检测开发环境的特征
        const isViteDev = currentPort && (
            currentPort.startsWith('517') ||  // Vite默认端口5173及其邻近端口
            currentPort.startsWith('300') ||  // 3000-3999端口范围
            currentPort.startsWith('400') ||  // 4000-4999端口范围  
            currentPort.startsWith('500') ||  // 5000-5999端口范围
            parseInt(currentPort) >= 3000
        );
        
        // 在开发环境中，始终使用相对路径让Vite代理处理
        if (isViteDev || import.meta.env.DEV) {
            console.log('检测到开发环境，使用Vite代理', {
                port: currentPort,
                isDev: import.meta.env.DEV,
                isViteDev
            });
            // 开发环境：使用相对路径，让Vite代理处理
            return ''; // 空字符串表示使用相对路径
        } else {
            console.log('检测到生产环境，使用相对路径');
            // 生产环境：使用相对路径，由Nginx等反向代理处理
            // 这样可以自动适配任何部署环境，无需硬编码端口
            return '';
        }
    }
    
    /**
     * 获取完整的API URL
     * @param {string} path - API路径
     * @returns {string}
     */
    getApiUrl(path) {
        // 检查是否有明确的环境变量设置
        if (import.meta.env.VITE_BACKEND_URL) {
            console.log(`使用环境变量配置的后端: ${import.meta.env.VITE_BACKEND_URL}${path}`);
            return `${import.meta.env.VITE_BACKEND_URL}${path}`;
        }
        
        const currentPort = window.location.port;
        const currentHost = window.location.hostname;
        
        // 更准确的开发环境检测
        const isViteDev = import.meta.env.DEV || 
                         currentPort === '5173' || 
                         currentPort === '5174' ||
                         currentPort.startsWith('517') ||
                         currentPort.startsWith('300') ||
                         currentPort.startsWith('400') ||
                         currentPort.startsWith('500');
        
        const isDevelopment = isViteDev && currentHost === 'localhost';
        
        if (isDevelopment) {
            // 开发环境选项：如果Vite代理导致文件损坏，可以直接连接后端
            const BYPASS_VITE_PROXY = true; // 设置为 false 使用Vite代理，true 直接连接后端
            
            if (BYPASS_VITE_PROXY) {
                console.log(`开发环境 - 直接连接后端: http://localhost:8100${path}`);
                return `http://localhost:8100${path}`;
            } else {
                console.log(`开发环境 - 使用Vite代理: ${path}`);
                return path;
            }
        }
        
        // 生产环境：始终使用相对路径，让反向代理或同域部署处理
        console.log(`生产环境 - 使用相对路径: ${path}，当前域: ${window.location.origin}`);
        return path;
    }

    /**
     * 获取当前会话ID
     * @returns {string|null}
     */
    getSessionId() {
        if (this.sessionIdProvider && typeof this.sessionIdProvider === 'function') {
            const sessionId = this.sessionIdProvider();
            console.log('📋 StreamingFileService获取会话ID:', sessionId);
            return sessionId;
        }
        console.warn('⚠️  StreamingFileService: 没有配置sessionIdProvider');
        return null;
    }

    /**
     * 流式下载文件
     * @param {Array<string>} paths - 文件路径列表
     * @param {Function} onProgress - 进度回调 (loaded, total, percentage)
     * @param {AbortSignal} signal - 取消信号
     * @returns {Promise<{filename: string, blob: Blob}>}
     */
    async downloadFiles(paths, onProgress = null, signal = null) {
        try {
            // 获取会话ID（从STOMP客户端或其他来源）
            const sessionId = this.getSessionId();
            if (!sessionId) {
                throw new Error('未找到有效的会话ID');
            }

            // 构建下载URL
            const downloadUrl = this.getApiUrl('/api/files/download');
            const url = new URL(downloadUrl, this.backendBaseUrl ? undefined : window.location.origin);
            url.searchParams.set('sessionId', sessionId);
            url.searchParams.set('paths', JSON.stringify(paths));

            // 创建下载请求
            const response = await fetch(url, {
                method: 'GET',
                signal
            });

            if (!response.ok) {
                if (response.status === 401) {
                    throw new Error('认证失败，请重新连接');
                } else if (response.status === 404) {
                    throw new Error('SSH连接已断开');
                } else {
                    throw new Error(`下载失败: ${response.statusText}`);
                }
            }

            // 从响应头获取文件名，支持RFC 5987标准
            const contentDisposition = response.headers.get('content-disposition');
            let filename = 'download';
            if (contentDisposition) {
                console.log('Content-Disposition header:', contentDisposition);
                
                // 首先尝试解析 filename*=UTF-8'' 参数（RFC 5987）
                const encodedMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/);
                if (encodedMatch) {
                    try {
                        filename = decodeURIComponent(encodedMatch[1]);
                        console.log('使用RFC 5987解码的文件名:', filename);
                    } catch (e) {
                        console.warn('RFC 5987文件名解码失败:', e);
                    }
                } else {
                    // 回退到普通的filename参数
                    const normalMatch = contentDisposition.match(/filename="([^"]+)"/);
                    if (normalMatch) {
                        try {
                            filename = decodeURIComponent(normalMatch[1]);
                            console.log('使用普通filename参数:', filename);
                        } catch (e) {
                            // 如果解码失败，直接使用原始值
                            filename = normalMatch[1];
                            console.log('文件名无法解码，使用原始值:', filename);
                        }
                    }
                }
            }

            // 获取内容长度
            const contentLength = parseInt(response.headers.get('content-length') || '0');

            // 创建流式读取器
            const reader = response.body?.getReader();
            if (!reader) {
                throw new Error('响应体无法读取');
            }

            const chunks = [];
            let loaded = 0;

            // 流式读取数据
            while (true) {
                const { done, value } = await reader.read();
                
                if (done) break;

                chunks.push(value);
                loaded += value.length;

                // 报告进度
                if (onProgress && contentLength > 0) {
                    const percentage = Math.round((loaded / contentLength) * 100);
                    onProgress(loaded, contentLength, percentage);
                }

                // 检查取消信号
                if (signal?.aborted) {
                    reader.cancel();
                    throw new Error('下载已取消');
                }
            }

            // 合并所有数据块，确保正确的MIME类型
            const blob = new Blob(chunks, {
                type: response.headers.get('content-type') || 'application/octet-stream'
            });
            
            console.log('下载完成:', {
                filename,
                blobSize: blob.size,
                blobType: blob.type,
                expectedSize: contentLength,
                chunksCount: chunks.length
            });
            
            return { filename, blob };

        } catch (error) {
            if (error.name === 'AbortError') {
                throw new Error('下载已取消');
            }
            throw error;
        }
    }

    /**
     * 触发文件下载到本地
     * @param {string} filename - 文件名
     * @param {Blob} blob - 文件数据
     */
    triggerDownload(filename, blob) {
        try {
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.style.display = 'none';
            
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            
            // 清理对象URL
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            
        } catch (error) {
            console.error('触发下载失败:', error);
            throw new Error('无法创建下载链接');
        }
    }

    /**
     * 真正的流式上传文件 - 使用ProgressStateManager统一管理
     * @param {File} file - 单个文件
     * @param {string} remotePath - 远程路径
     * @param {Function} onProgress - 进度回调
     * @param {Function} onComplete - 完成回调
     * @param {Function} onError - 错误回调
     * @returns {Promise<string>} 上传ID
     */
    async streamUploadFile(file, remotePath, onProgress = null, onComplete = null, onError = null) {
        try {
            // 检查文件大小限制
            const maxFileSize = 2 * 1024 * 1024 * 1024; // 2GB
            
            if (file.size > maxFileSize) {
                throw new Error(`文件 "${file.name}" 超过大小限制 (2GB)`);
            }

            // 获取会话ID
            const sessionId = this.getSessionId();
            if (!sessionId) {
                throw new Error('未找到有效的会话ID，请确保SSH连接已建立');
            }

            const uploadId = this.generateUploadId();
            
            // 创建进度状态管理
            const uploadState = this.progressManager.createUpload(uploadId, file, {
                onProgress,
                onComplete,
                onError
            });

            // 设置全局STOMP回调
            this.setupGlobalStompCallback();

            // 构建流式上传URL
            const uploadPath = '/api/streaming/upload';
            const params = new URLSearchParams({
                sessionId,
                remotePath,
                filename: file.name
            });
            const finalUrl = this.getApiUrl(`${uploadPath}?${params.toString()}`);

            console.log('🚀 开始流式上传:', {
                uploadId,
                filename: file.name,
                fileSize: file.size,
                finalUrl
            });

            // 创建XMLHttpRequest
            const xhr = new XMLHttpRequest();
            
            // 存储上传信息
            this.activeUploads.set(uploadId, {
                xhr,
                files: [file.name],
                startTime: Date.now(),
                progressManager: this.progressManager
            });

            return new Promise((resolve, reject) => {
                // HTTP进度处理
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable) {
                        const progressData = {
                            loaded: event.loaded,
                            total: event.total,
                            percentage: Math.round((event.loaded / event.total) * 100),
                            speed: this.calculateSpeed(uploadState, event.loaded)
                        };
                        this.progressManager.updateHttpProgress(uploadId, progressData);
                    }
                };

                // HTTP完成处理
                xhr.onload = () => {
                    this.activeUploads.delete(uploadId);
                    
                    if (xhr.status >= 200 && xhr.status < 300) {
                        try {
                            const response = JSON.parse(xhr.responseText);
                            console.log('✅ HTTP请求完成:', response);
                            
                            this.progressManager.completeHttpUpload(uploadId, response);
                            
                            // 如果后端直接完成，resolve
                            if (response.status === 'completed') {
                                resolve(response.uploadId || uploadId);
                            } else {
                                // 等待STOMP进度消息或轮询
                                this.waitForCompletion(uploadId, response.uploadId || uploadId)
                                    .then(resolve)
                                    .catch(reject);
                            }
                            
                        } catch (e) {
                            const error = new Error('解析响应失败: ' + e.message);
                            this.progressManager.handleUploadError(uploadId, error, 'http');
                            reject(error);
                        }
                    } else {
                        const error = new Error(`流式上传失败: ${xhr.status} ${xhr.statusText}`);
                        this.progressManager.handleUploadError(uploadId, error, 'http');
                        reject(error);
                    }
                };

                // HTTP错误处理
                xhr.onerror = () => {
                    const error = new Error('流式上传网络错误：连接失败');
                    this.handleUploadError(uploadId, error, 'http_network');
                    reject(error);
                };

                // 发送请求
                xhr.open('POST', finalUrl);
                xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                
                file.arrayBuffer().then(arrayBuffer => {
                    xhr.setRequestHeader('Content-Length', arrayBuffer.byteLength.toString());
                    xhr.send(arrayBuffer);
                }).catch(error => {
                    const err = new Error('文件读取失败');
                    this.progressManager.handleUploadError(uploadId, err, 'file');
                    reject(err);
                });
            });

        } catch (error) {
            if (onError) onError(error);
            throw error;
        }
    }

    /**
     * 设置全局STOMP回调 - 解决并发竞争条件
     */
    setupGlobalStompCallback() {
        // 使用唯一标识符防止重复设置
        const callbackId = 'streamingProgressCallback_' + this.constructor.name;
        
        if (!window[callbackId]) {
            // 创建一个中介对象来管理多个实例的回调
            if (!window.streamingCallbackManager) {
                window.streamingCallbackManager = {
                    callbacks: new Map(),
                    register: (instanceId, callback) => {
                        window.streamingCallbackManager.callbacks.set(instanceId, callback);
                        console.log(`🔧 注册回调实例: ${instanceId}`);
                    },
                    unregister: (instanceId) => {
                        window.streamingCallbackManager.callbacks.delete(instanceId);
                        console.log(`🗑️ 注销回调实例: ${instanceId}`);
                    },
                    handleProgress: (progressData) => {
                        console.log('📨 全局STOMP进度回调:', progressData);
                        
                        // 将消息发送给所有注册的回调
                        let handled = false;
                        for (const [instanceId, callback] of window.streamingCallbackManager.callbacks.entries()) {
                            try {
                                const result = callback(progressData);
                                if (result !== false) { // 如果回调返回false，表示未处理
                                    handled = true;
                                }
                            } catch (error) {
                                console.warn(`回调实例 ${instanceId} 处理失败:`, error);
                            }
                        }
                        
                        if (!handled && !window.cachedStompMessages) {
                            console.log('📋 所有回调都未处理，缓存消息');
                            window.cachedStompMessages = [];
                        }
                        
                        if (!handled && window.cachedStompMessages) {
                            window.cachedStompMessages.push(progressData);
                        }
                    }
                };
                
                // 设置全局回调
                window.streamingProgressCallback = window.streamingCallbackManager.handleProgress;
            }
            
            // 注册当前实例的回调
            const instanceId = `instance_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
            this.callbackInstanceId = instanceId;
            
            window.streamingCallbackManager.register(instanceId, (progressData) => {
                return this.progressManager.handleStompProgress(progressData);
            });
            
            // 记录回调ID
            window[callbackId] = instanceId;
            
            console.log('🔧 设置全局STOMP进度回调，实例ID:', instanceId);
            
            // 处理可能缓存的STOMP消息
            if (window.cachedStompMessages && window.cachedStompMessages.length > 0) {
                console.log('🔄 处理缓存的STOMP消息:', window.cachedStompMessages.length);
                const messages = [...window.cachedStompMessages];
                window.cachedStompMessages = [];
                
                messages.forEach(message => {
                    console.log('🔄 处理缓存消息:', message);
                    this.progressManager.handleStompProgress(message);
                });
            }
        }
    }
    
    /**
     * 清理全局回调
     */
    cleanupGlobalStompCallback() {
        if (this.callbackInstanceId && window.streamingCallbackManager) {
            window.streamingCallbackManager.unregister(this.callbackInstanceId);
            this.callbackInstanceId = null;
        }
    }

    /**
     * 等待上传完成
     */
    async waitForCompletion(uploadId, backendUploadId) {
        return new Promise((resolve, reject) => {
            let attempts = 0;
            const maxAttempts = 30; // 30秒超时
            
            const checkCompletion = () => {
                const uploadState = this.progressManager.getUploadState(uploadId);
                
                if (!uploadState) {
                    resolve(backendUploadId);
                    return;
                }
                
                if (uploadState.backendCompleted) {
                    resolve(uploadState.backendUploadId || backendUploadId);
                    return;
                }
                
                attempts++;
                if (attempts >= maxAttempts) {
                    const error = new Error('上传超时');
                    this.progressManager.handleUploadError(uploadId, error, 'timeout');
                    reject(error);
                    return;
                }
                
                // 尝试HTTP轮询获取进度
                this.pollProgress(backendUploadId).catch(console.warn);
                
                setTimeout(checkCompletion, 1000);
            };
            
            checkCompletion();
        });
    }

    /**
     * HTTP轮询获取进度
     */
    async pollProgress(backendUploadId) {
        try {
            const sessionId = this.getSessionId();
            if (!sessionId || !backendUploadId) return;
            
            const progressUrl = this.getApiUrl(`/api/streaming/upload/${backendUploadId}/progress`);
            const response = await fetch(`${progressUrl}?sessionId=${encodeURIComponent(sessionId)}`);
            
            if (response.ok) {
                const progressData = await response.json();
                if (progressData && progressData.percentage !== undefined) {
                    console.log('📊 HTTP轮询获取进度:', progressData);
                    this.progressManager.handleStompProgress({
                        ...progressData,
                        uploadId: backendUploadId
                    });
                }
            }
        } catch (error) {
            console.warn('HTTP轮询进度失败:', error);
        }
    }

    /**
     * 计算传输速度
     */
    calculateSpeed(uploadState, currentLoaded) {
        const currentTime = Date.now();
        const timeDiff = currentTime - uploadState.lastProgressTime;
        const bytesDiff = currentLoaded - uploadState.lastProgressLoaded;
        
        let speed = 0;
        if (timeDiff > 0) {
            speed = (bytesDiff / timeDiff) * 1000; // bytes/sec
        }
        
        // 更新状态
        uploadState.lastProgressTime = currentTime;
        uploadState.lastProgressLoaded = currentLoaded;
        
        return speed;
    }

    /**
     * 批量真正流式上传
     * @param {Array<File>} files - 文件列表
     * @param {string} remotePath - 远程路径
     * @param {Function} onProgress - 进度回调
     * @param {Function} onComplete - 完成回调
     * @param {Function} onError - 错误回调
     * @returns {Promise<Array<string>>} 上传ID列表
     */
    async streamUploadFiles(files, remotePath, onProgress = null, onComplete = null, onError = null) {
        const uploadIds = [];
        
        for (const file of files) {
            try {
                const uploadId = await this.streamUploadFile(
                    file, 
                    remotePath, 
                    onProgress, 
                    onComplete, 
                    onError
                );
                uploadIds.push(uploadId);
                
                // 短暂延迟避免并发冲突
                await new Promise(resolve => setTimeout(resolve, 100));
            } catch (error) {
                console.error(`文件 ${file.name} 流式上传失败:`, error);
                if (onError) onError(error);
            }
        }
        
        return uploadIds;
    }

    /**
     * 流式上传文件 (旧版本，基于FormData)
     * @param {Array<File>} files - 文件列表
     * @param {string} remotePath - 远程路径
     * @param {Function} onProgress - 进度回调
     * @param {Function} onComplete - 完成回调
     * @param {Function} onError - 错误回调
     * @returns {Promise<string>} 上传ID
     */
    async uploadFiles(files, remotePath, onProgress = null, onComplete = null, onError = null) {
        try {
            // 检查文件大小限制
            const maxFileSize = 2 * 1024 * 1024 * 1024; // 2GB
            const maxTotalSize = 2 * 1024 * 1024 * 1024; // 2GB
            
            let totalSize = 0;
            for (const file of files) {
                if (file.size > maxFileSize) {
                    throw new Error(`文件 "${file.name}" 超过大小限制 (2GB)`);
                }
                totalSize += file.size;
            }
            
            if (totalSize > maxTotalSize) {
                throw new Error(`总文件大小超过限制 (2GB)`);
            }

            // 获取会话ID
            const sessionId = this.getSessionId();
            console.log('上传请求获取到的sessionId:', sessionId);
            if (!sessionId) {
                console.error('StreamingFileService: 无法获取会话ID');
                console.error('调试信息:', {
                    hasSessionIdProvider: !!this.sessionIdProvider,
                    sessionIdProviderType: typeof this.sessionIdProvider,
                    providerResult: this.sessionIdProvider ? this.sessionIdProvider() : 'N/A'
                });
                throw new Error('未找到有效的会话ID，请确保SSH连接已建立');
            }
            
            // 测试后端连接
            try {
                const testUrl = this.getApiUrl('/api/files/upload');
                console.log('测试连接到:', testUrl);
                console.log('后端配置:', {
                    backendBaseUrl: this.backendBaseUrl,
                    isDev: import.meta.env.DEV,
                    location: window.location,
                    testUrl
                });
                
                const testResponse = await fetch(testUrl, {
                    method: 'OPTIONS',
                    mode: 'cors'
                });
                console.log('后端连接测试:', {
                    ok: testResponse.ok,
                    status: testResponse.status,
                    url: testResponse.url,
                    requestedUrl: testUrl,
                    type: testResponse.type
                });
                
                if (!testResponse.ok && testResponse.status !== 404) {
                    throw new Error(`后端服务响应异常: ${testResponse.status} ${testResponse.statusText}`);
                }
            } catch (testError) {
                console.error('后端连接测试失败:', testError);
                console.error('完整错误信息:', {
                    name: testError.name,
                    message: testError.message,
                    stack: testError.stack
                });
                
                let errorMessage = `无法连接到后端服务：${testError.message}\n\n`;
                
                if (testError.message.includes('Failed to fetch') || testError.name === 'TypeError') {
                    errorMessage += `当前运行在端口 ${window.location.port}，请检查:\n`;
                    errorMessage += `1. 后端是否运行在端口8100\n`;
                    errorMessage += `2. Vite开发服务器代理配置\n`;
                    errorMessage += `3. 重启前端开发服务器试试\n`;
                    errorMessage += `4. 检查网络连接和防火墙\n\n`;
                    errorMessage += `调试信息:\n`;
                    errorMessage += `- 前端地址: ${window.location.href}\n`;
                    errorMessage += `- 请求URL: ${this.getApiUrl('/api/files/upload')}\n`;
                    errorMessage += `- 后端目标: http://localhost:8100\n`;
                } else {
                    errorMessage += `请检查后端是否运行在端口8100\n`;
                }
                
                throw new Error(errorMessage);
            }

            // 准备FormData
            const formData = new FormData();
            formData.append('sessionId', sessionId);
            formData.append('remotePath', remotePath);
            
            files.forEach((file, index) => {
                formData.append('files', file);
            });

            // 创建XMLHttpRequest用于进度追踪
            const xhr = new XMLHttpRequest();
            const uploadId = this.generateUploadId();
            
            // 存储上传信息，包括进度跟踪
            this.activeUploads.set(uploadId, {
                xhr,
                files: files.map(f => f.name),
                startTime: Date.now(),
                lastProgressTime: Date.now(),
                lastProgressLoaded: 0,
                totalSize
            });

            return new Promise((resolve, reject) => {
                // 进度处理
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable && onProgress) {
                        const percentage = Math.round((event.loaded / event.total) * 100);
                        const currentTime = Date.now();
                        const uploadInfo = this.activeUploads.get(uploadId);
                        
                        // 计算瞬时速度
                        let speed = 0;
                        if (uploadInfo) {
                            const timeDiff = currentTime - uploadInfo.lastProgressTime;
                            const bytesDiff = event.loaded - uploadInfo.lastProgressLoaded;
                            
                            if (timeDiff > 0) {
                                speed = (bytesDiff / timeDiff) * 1000; // bytes/sec
                            }
                            
                            // 更新进度跟踪信息
                            uploadInfo.lastProgressTime = currentTime;
                            uploadInfo.lastProgressLoaded = event.loaded;
                        }
                        
                        onProgress({
                            uploadId,
                            loaded: event.loaded,
                            total: event.total,
                            percentage,
                            speed,
                            status: 'uploading'
                        });
                    }
                };

                // 完成处理
                xhr.onload = () => {
                    this.activeUploads.delete(uploadId);
                    
                    if (xhr.status >= 200 && xhr.status < 300) {
                        try {
                            const response = JSON.parse(xhr.responseText);
                            if (onComplete) {
                                onComplete({
                                    uploadId: response.uploadId || uploadId,
                                    status: 'completed',
                                    message: '文件上传启动成功'
                                });
                            }
                            resolve(response.uploadId || uploadId);
                        } catch (e) {
                            const error = new Error('解析响应失败');
                            if (onError) onError(error);
                            reject(error);
                        }
                    } else {
                        const error = new Error(`上传失败: ${xhr.statusText}`);
                        if (onError) onError(error);
                        reject(error);
                    }
                };

                // 错误处理
                xhr.onerror = () => {
                    console.error('上传网络错误:', {
                        url: uploadUrl,
                        readyState: xhr.readyState,
                        status: xhr.status,
                        statusText: xhr.statusText,
                        responseURL: xhr.responseURL,
                        backendConfig: {
                            baseUrl: this.backendBaseUrl,
                            isDev: import.meta.env.DEV,
                            currentLocation: window.location.href
                        }
                    });
                    
                    let errorMessage = '网络连接失败';
                    
                    // 根据不同的错误状态提供更具体的错误信息
                    if (xhr.readyState === 0) {
                        errorMessage = '无法连接到服务器。请检查:\n1. 后端服务是否在端口8100运行\n2. 网络连接是否正常\n3. 防火墙设置';
                    } else if (xhr.status === 0) {
                        errorMessage = 'CORS错误或服务器不可达。请检查后端服务状态。';
                    }
                    
                    const error = new Error(errorMessage);
                    this.handleUploadError(uploadId, error, 'formdata_network');
                    reject(error);
                };

                // 取消处理
                xhr.onabort = () => {
                    this.activeUploads.delete(uploadId);
                    const error = new Error('上传已取消');
                    if (onError) onError(error);
                    reject(error);
                };

                // 发送请求
                const uploadUrl = this.getApiUrl('/api/files/upload');
                console.log('=== 上传请求调试信息 ===');
                console.log('uploadUrl:', uploadUrl);
                console.log('typeof uploadUrl:', typeof uploadUrl);
                console.log('uploadUrl.startsWith("http"):', uploadUrl.startsWith('http'));
                console.log('window.location.origin:', window.location.origin);
                console.log('Vite代理预期行为: 相对路径/api/files/upload应该被代理到http://localhost:8100/api/files/upload');
                
                console.log('FormData 内容:', {
                    sessionId,
                    remotePath,
                    filesCount: files.length,
                    totalSize: files.reduce((sum, f) => sum + f.size, 0)
                });
                
                // 确保使用相对路径
                const finalUrl = uploadUrl.startsWith('http') ? '/api/files/upload' : uploadUrl;
                console.log('最终请求URL:', finalUrl);
                console.log('===============================');
                
                xhr.open('POST', finalUrl);
                xhr.send(formData);
            });

        } catch (error) {
            if (onError) onError(error);
            throw error;
        }
    }

    /**
     * 取消上传
     * @param {string} uploadId - 上传ID
     * @returns {Promise<boolean>}
     */
    async cancelUpload(uploadId) {
        // 取消本地XMLHttpRequest
        const uploadInfo = this.activeUploads.get(uploadId);
        if (uploadInfo && uploadInfo.xhr) {
            uploadInfo.xhr.abort();
            this.activeUploads.delete(uploadId);
        }

        // 通知服务器取消
        try {
            const sessionId = this.getSessionId();
            if (!sessionId) return false;

            const cancelUrl = this.getApiUrl(`/api/files/upload/${uploadId}/cancel`);
            const response = await fetch(`${cancelUrl}?sessionId=${encodeURIComponent(sessionId)}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });

            return response.ok;
        } catch (error) {
            console.warn('取消上传请求失败:', error);
            return false;
        }
    }

    /**
     * 获取上传进度
     * @param {string} uploadId - 上传ID
     * @returns {Promise<Object>}
     */
    async getUploadProgress(uploadId) {
        try {
            const sessionId = this.getSessionId();
            if (!sessionId) {
                throw new Error('未找到有效的会话ID');
            }

            const progressUrl = this.getApiUrl(`/api/files/upload/${uploadId}/progress`);
            const response = await fetch(`${progressUrl}?sessionId=${encodeURIComponent(sessionId)}`);
            
            if (!response.ok) {
                if (response.status === 404) {
                    return null; // 上传不存在
                }
                throw new Error(`获取进度失败: ${response.statusText}`);
            }

            return await response.json();
        } catch (error) {
            console.error('获取上传进度失败:', error);
            return null;
        }
    }

    /**
     * 诊断连接和配置问题
     * @returns {Promise<Object>} 诊断结果
     */
    async diagnoseConnection() {
        const diagnosis = {
            sessionId: null,
            backendConnection: null,
            proxyConfiguration: null,
            recommendations: []
        };

        // 检查sessionId
        try {
            diagnosis.sessionId = {
                value: this.getSessionId(),
                provider: !!this.sessionIdProvider,
                type: typeof this.sessionIdProvider
            };
            
            if (!diagnosis.sessionId.value) {
                diagnosis.recommendations.push('请先建立SSH连接后再尝试上传文件');
            }
        } catch (e) {
            diagnosis.sessionId = { error: e.message };
        }

        // 检查后端连接
        try {
            const testUrl = this.getApiUrl('/api/files/upload');
            const testResponse = await fetch(testUrl, {
                method: 'OPTIONS'
            });
            diagnosis.backendConnection = {
                ok: testResponse.ok,
                status: testResponse.status,
                statusText: testResponse.statusText,
                url: testResponse.url,
                headers: Object.fromEntries(testResponse.headers.entries())
            };
            
            if (!testResponse.ok) {
                diagnosis.recommendations.push('后端服务未响应，请检查后端是否运行在端口8100');
            }
        } catch (e) {
            diagnosis.backendConnection = { error: e.message };
            diagnosis.recommendations.push('无法连接后端服务，请检查:');
            diagnosis.recommendations.push('1. 后端服务是否运行在端口8100');
            diagnosis.recommendations.push('2. 前端代理配置是否正确 (vite.config.js)');
            diagnosis.recommendations.push('3. 防火墙是否阻止了连接');
        }

        // 检查配置信息
        diagnosis.configuration = {
            currentOrigin: window.location.origin,
            currentPort: window.location.port,
            backendUrl: this.backendBaseUrl || '使用相对路径(开发环境)',
            deploymentMode: this.backendBaseUrl ? '生产模式' : '开发模式'
        };

        return diagnosis;
    }

    /**
     * 取消下载
     * @param {string} downloadId - 下载ID
     */
    cancelDownload(downloadId) {
        const downloadInfo = this.activeDownloads.get(downloadId);
        if (downloadInfo && downloadInfo.controller) {
            downloadInfo.controller.abort();
            this.activeDownloads.delete(downloadId);
        }
    }

    /**
     * 生成唯一上传ID
     * @returns {string}
     */
    generateUploadId() {
        return 'upload_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }

    /**
     * 获取进度管理器（用于外部访问）
     */
    getProgressManager() {
        return this.progressManager;
    }

    /**
     * 格式化速度显示
     * @param {number} bytesPerSecond - 每秒字节数
     * @returns {string}
     */
    formatSpeed(bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond.toFixed(0) + ' B/s';
        } else if (bytesPerSecond < 1024 * 1024) {
            return (bytesPerSecond / 1024).toFixed(1) + ' KB/s';
        } else {
            return (bytesPerSecond / (1024 * 1024)).toFixed(1) + ' MB/s';
        }
    }

    /**
     * 格式化文件大小
     * @param {number} bytes - 字节数
     * @returns {string}
     */
    formatFileSize(bytes) {
        if (bytes < 1024) {
            return bytes + ' B';
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + ' KB';
        } else if (bytes < 1024 * 1024 * 1024) {
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        } else {
            return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
        }
    }
    
    /**
     * 处理上传错误，添加完善的错误处理
     */
    handleUploadError(uploadId, error, context = 'unknown') {
        console.error(`😱 上传错误 (${context}):`, error);
        
        // 清理活动上传
        this.activeUploads.delete(uploadId);
        
        // 通过ProgressManager处理错误
        this.progressManager.handleUploadError(uploadId, error, context);
        
        // 记录错误统计
        this.logErrorStatistics(error, context);
    }
    
    /**
     * 记录错误统计
     */
    logErrorStatistics(error, context) {
        if (!window.uploadErrorStats) {
            window.uploadErrorStats = {
                total: 0,
                byContext: {},
                byType: {},
                recent: []
            };
        }
        
        const stats = window.uploadErrorStats;
        stats.total++;
        stats.byContext[context] = (stats.byContext[context] || 0) + 1;
        stats.byType[error.name || 'Unknown'] = (stats.byType[error.name || 'Unknown'] || 0) + 1;
        
        // 保留最近10个错误
        stats.recent.unshift({
            time: new Date().toISOString(),
            context,
            error: error.message,
            type: error.name || 'Unknown'
        });
        
        if (stats.recent.length > 10) {
            stats.recent = stats.recent.slice(0, 10);
        }
        
        console.log('📈 错误统计已更新:', stats);
    }
}
