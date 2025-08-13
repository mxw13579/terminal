import { AuthService } from './auth.js';

/**
 * HTTP流式文件传输服务
 * 替代基于WebSocket的base64传输，提供更高效的文件上传下载
 */
export class StreamingFileService {
    constructor(sessionIdProvider = null) {
        this.activeUploads = new Map();
        this.activeDownloads = new Map();
        this.sessionIdProvider = sessionIdProvider;
        
        // 自动检测后端地址
        this.backendBaseUrl = this.detectBackendUrl();
        console.log('自动检测到后端地址:', this.backendBaseUrl);
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
            console.log('检测到生产环境，直接连接后端');
            // 生产环境：直接连接到后端端口
            return `${currentProtocol}//${currentHost}:8080`;
        }
    }
    
    /**
     * 获取完整的API URL
     * @param {string} path - API路径
     * @returns {string}
     */
    getApiUrl(path) {
        // 强制在开发环境中使用相对路径以利用Vite代理
        // 不管检测结果如何，只要端口是5173相关就使用相对路径
        const currentPort = window.location.port;
        const isDevelopment = import.meta.env.DEV || 
                            currentPort === '5173' || 
                            currentPort.startsWith('517') ||
                            !this.backendBaseUrl;
        
        if (isDevelopment) {
            console.log(`开发环境 - 使用相对路径: ${path}`, {
                port: currentPort,
                isDev: import.meta.env.DEV,
                backendBaseUrl: this.backendBaseUrl
            });
            return path;
        }
        
        const fullUrl = this.backendBaseUrl + path;
        console.log(`生产环境 - 使用完整URL: ${fullUrl}`);
        return fullUrl;
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
     * 真正的流式上传文件 - 适配新的阻塞式后端API
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

            // 构建流式上传URL - 强制使用相对路径确保通过Vite代理
            const uploadPath = '/api/streaming/upload';
            const params = new URLSearchParams({
                sessionId,
                remotePath,
                filename: file.name
            });
            const finalUrl = `${uploadPath}?${params.toString()}`;

            // 创建XMLHttpRequest用于进度追踪
            const xhr = new XMLHttpRequest();
            const uploadId = this.generateUploadId();
            
            console.log('流式上传配置:', {
                uploadPath,
                finalUrl,
                uploadId,
                fileSize: file.size,
                currentHost: window.location.host,
                sessionId: sessionId.substring(0, 8) + '...'
            });
            
            // 存储上传信息
            this.activeUploads.set(uploadId, {
                xhr,
                files: [file.name],
                startTime: Date.now(),
                lastProgressTime: Date.now(),
                lastProgressLoaded: 0,
                totalSize: file.size,
                // 添加速度平滑处理
                speedHistory: [], // 保存最近5次的速度记录
                maxSpeedHistory: 5
            });

            return new Promise((resolve, reject) => {
                let realProgressTimer = null;
                let realUploadId = null; // 存储后端返回的真实uploadId
                let usingBackendProgress = false; // 标记是否已切换到后端进度
                let backendCompleted = false; // 标记后端是否已完成
                
                // 设置全局回调来接收STOMP实时进度
                console.log('🎯 设置streamingProgressCallback, uploadId:', uploadId);
                window.streamingProgressCallback = (progressData) => {
                    console.log('📩 streamingProgressCallback被调用:', progressData);
                    
                    // 简化逻辑：如果收到了后端进度数据，直接使用，不做严格的ID匹配
                    // 因为在上传期间，前端uploadId和后端uploadId不同是正常的
                    if (progressData.filename === file.name) {
                        console.log('✅ 文件名匹配，使用后端进度数据');
                        usingBackendProgress = true;
                        
                        // 更新realUploadId以便后续使用
                        if (!realUploadId && progressData.uploadId) {
                            realUploadId = progressData.uploadId;
                            console.log('🔗 设置realUploadId:', realUploadId);
                        }
                    } else {
                        console.log(`⚠️  跳过：文件名不匹配 (期望:${file.name}, 收到:${progressData.filename})`);
                        return;
                    }
                    
                    console.log(`🎯 使用后端实时进度: ${progressData.percentage}% (${progressData.transferredBytes}/${progressData.totalBytes} bytes) - 速度: ${this.formatSpeed(progressData.speed || 0)}`);
                    
                    if (onProgress) {
                        onProgress({
                            uploadId: progressData.uploadId || uploadId,
                            loaded: progressData.transferredBytes || 0,
                            total: progressData.totalBytes || file.size,
                            percentage: Math.round(progressData.percentage || 0),
                            speed: progressData.speed || 0,
                            status: progressData.status === 'completed' ? 'completed' : 'streaming'
                        });
                    }
                    
                    // 如果后端显示完成，清理回调并resolve Promise
                    if (progressData.status === 'completed' || progressData.percentage >= 100) {
                        console.log('🏁 后端进度完成，清理回调');
                        window.streamingProgressCallback = null;
                        backendCompleted = true; // 标记后端已完成
                        
                        // 🔥 关键修复：后端完成时直接resolve Promise
                        console.log('✅ 后端确认上传完成，自动resolve Promise');
                        if (onComplete) {
                            onComplete({
                                uploadId: progressData.uploadId,
                                status: 'completed',
                                message: '文件上传完成！'
                            });
                        }
                        // 直接resolve，不等待XMLHttpRequest
                        resolve(progressData.uploadId);
                    }
                };
                
                // 开始真实进度查询的函数 - 作为STOMP的备用方案
                const startRealProgressTracking = (backendUploadId) => {
                    realUploadId = backendUploadId;
                    console.log(`💡 备用方案：开始使用后端uploadId查询真实进度: ${backendUploadId}`);
                    
                    // 延迟启动，给STOMP消息一点时间
                    setTimeout(() => {
                        if (usingBackendProgress) {
                            console.log('✅ 已有STOMP实时进度，跳过轮询');
                            return;
                        }
                        
                        console.log('🔄 STOMP进度未收到，使用HTTP轮询备用');
                        realProgressTimer = setInterval(async () => {
                            try {
                                if (usingBackendProgress) {
                                    clearInterval(realProgressTimer);
                                    realProgressTimer = null;
                                    return;
                                }
                                
                                const sessionId = this.getSessionId();
                                if (!sessionId || !realUploadId) return;
                                
                                const progressUrl = this.getApiUrl(`/api/streaming/upload/${realUploadId}/progress`);
                                const response = await fetch(`${progressUrl}?sessionId=${encodeURIComponent(sessionId)}`);
                                
                                if (response.ok) {
                                    const progressData = await response.json();
                                    
                                    if (progressData && progressData.percentage !== undefined) {
                                        console.log(`📊 HTTP轮询进度: ${progressData.percentage}% (${progressData.transferredBytes}/${progressData.totalBytes} bytes)`);
                                        
                                        onProgress({
                                            uploadId: realUploadId,
                                            loaded: progressData.transferredBytes || 0,
                                            total: progressData.totalBytes || file.size,
                                            percentage: Math.round(progressData.percentage),
                                            speed: progressData.speed || 0,
                                            status: progressData.status === 'completed' ? 'completed' : 'streaming'
                                        });
                                        
                                        // 如果后端显示完成，停止进度查询
                                        if (progressData.status === 'completed' || progressData.percentage >= 100) {
                                            clearInterval(realProgressTimer);
                                            realProgressTimer = null;
                                        }
                                    }
                                } else {
                                    console.warn(`进度查询失败: ${response.status} ${response.statusText}`);
                                }
                            } catch (error) {
                                console.warn('查询真实上传进度失败:', error.message);
                            }
                        }, 1000); // 每秒查询一次真实进度
                    }, 2000); // 延迟2秒启动
                };
                
                // 使用浏览器原生进度事件作为初始进度显示（仅在后端数据不可用时）
                xhr.upload.onprogress = (event) => {
                    // 如果已经有后端实时进度，就不使用浏览器进度
                    if (usingBackendProgress) return;
                    
                    if (event.lengthComputable && onProgress) {
                        const percentage = Math.round((event.loaded / event.total) * 100);
                        const currentTime = Date.now();
                        const uploadInfo = this.activeUploads.get(uploadId);
                        
                        // 使用滑动窗口计算平均速度（更平滑）
                        let speed = 0;
                        if (uploadInfo) {
                            if (!uploadInfo.speedHistory) {
                                uploadInfo.speedHistory = [];
                            }
                            
                            // 计算瞬时速度
                            const timeDiff = currentTime - uploadInfo.lastProgressTime;
                            const bytesDiff = event.loaded - uploadInfo.lastProgressLoaded;
                            
                            if (timeDiff > 0 && bytesDiff > 0) {
                                const instantSpeed = (bytesDiff / timeDiff) * 1000;
                                
                                // 添加到历史记录
                                uploadInfo.speedHistory.push(instantSpeed);
                                if (uploadInfo.speedHistory.length > 5) { // 保持最近5个数据点
                                    uploadInfo.speedHistory.shift();
                                }
                                
                                // 计算平均速度
                                speed = uploadInfo.speedHistory.reduce((sum, s) => sum + s, 0) / uploadInfo.speedHistory.length;
                                
                                // 更新跟踪信息
                                uploadInfo.lastProgressTime = currentTime;
                                uploadInfo.lastProgressLoaded = event.loaded;
                            } else {
                                // 回退到总体平均速度
                                const totalTime = currentTime - uploadInfo.startTime;
                                if (totalTime > 1000) {
                                    speed = event.loaded * 1000 / totalTime;
                                }
                            }
                        }
                        
                        console.log(`📱 浏览器进度(临时): ${percentage}% (${event.loaded}/${event.total} bytes) - 速度: ${this.formatSpeed(speed)}`);
                        console.warn('⚠️  等待后端实时进度接管...');
                        
                        onProgress({
                            uploadId,
                            loaded: event.loaded,
                            total: event.total,
                            percentage,
                            speed,
                            status: 'streaming'
                        });
                    }
                };

                // 上传完成处理
                xhr.onload = () => {
                    // 不要清理真实进度定时器，因为我们要开始使用它
                    this.activeUploads.delete(uploadId);
                    
                    if (xhr.status >= 200 && xhr.status < 300) {
                        try {
                            const response = JSON.parse(xhr.responseText);
                            const backendUploadId = response.uploadId || uploadId;
                            
                            console.log(`上传HTTP请求完成，获得后端uploadId: ${backendUploadId}`);
                            
                            // 立即开始使用后端uploadId查询真实进度
                            startRealProgressTracking(backendUploadId);
                            
                            if (onComplete) {
                                onComplete({
                                    uploadId: backendUploadId,
                                    status: 'completed',
                                    message: '文件流式传输完成！'
                                });
                            }
                            resolve(backendUploadId);
                        } catch (e) {
                            const error = new Error('解析响应失败: ' + e.message);
                            if (onError) onError(error);
                            reject(error);
                        }
                    } else {
                        let errorMessage = `流式上传失败: ${xhr.status} ${xhr.statusText}`;
                        
                        // 尝试解析错误响应
                        try {
                            if (xhr.responseText) {
                                const errorResponse = JSON.parse(xhr.responseText);
                                if (errorResponse.error) {
                                    errorMessage = `流式上传失败: ${errorResponse.error}`;
                                }
                            }
                        } catch (parseError) {
                            // 如果无法解析响应，显示原始响应文本
                            if (xhr.responseText && xhr.responseText.length < 200) {
                                errorMessage += ` - 响应: ${xhr.responseText}`;
                            }
                        }
                        
                        console.error('流式上传HTTP错误详情:', {
                            status: xhr.status,
                            statusText: xhr.statusText,
                            responseText: xhr.responseText,
                            responseHeaders: xhr.getAllResponseHeaders(),
                            uploadId,
                            filename: file.name,
                            finalUrl,
                            contentLength: file.size
                        });
                        
                        const error = new Error(errorMessage);
                        if (onError) onError(error);
                        reject(error);
                    }
                };

                // 网络错误处理 - 改进错误处理逻辑
                xhr.onerror = () => {
                    // 如果后端已完成，不要报错
                    if (backendCompleted) {
                        console.log('✅ 后端已完成上传，忽略XMLHttpRequest错误');
                        return;
                    }
                    
                    // 清理资源
                    if (realProgressTimer) {
                        clearInterval(realProgressTimer);
                        realProgressTimer = null;
                    }
                    window.streamingProgressCallback = null;
                    
                    this.activeUploads.delete(uploadId);
                    
                    // 改进错误消息 - 区分不同的错误情况
                    let errorMessage = '流式上传网络错误：连接失败';
                    
                    if (xhr.readyState === 4 && xhr.status === 0) {
                        // 这种情况可能是连接意外断开，但文件可能已经传输完成
                        errorMessage = '连接意外断开，但文件可能已经传输完成。请检查后端日志确认上传状态。';
                        console.warn('XMLHttpRequest异常断开，可能是大文件上传过程中的正常现象');
                    } else if (xhr.readyState === 0) {
                        errorMessage = '无法建立连接到服务器，请检查网络状态';
                    }
                    
                    const error = new Error(errorMessage);
                    console.error('流式上传网络错误详细信息:', {
                        readyState: xhr.readyState,
                        status: xhr.status,
                        statusText: xhr.statusText,
                        filename: file.name,
                        uploadId,
                        responseText: xhr.responseText,
                        currentTime: new Date().toISOString()
                    });
                    
                    // 对于可能已完成的上传，不要立即报错，而是等待一会儿看后端是否完成
                    if (xhr.readyState === 4 && xhr.status === 0) {
                        console.log('等待5秒检查后端是否完成上传...');
                        setTimeout(() => {
                            // 再次检查后端是否已完成
                            if (backendCompleted) {
                                console.log('✅ 5秒后检查：后端已完成，不报错');
                                return;
                            }
                            // 如果5秒后还没有后端确认，则报错
                            if (onError) onError(error);
                            reject(error);
                        }, 5000);
                    } else {
                        if (onError) onError(error);
                        reject(error);
                    }
                };

                // 上传中止处理
                xhr.onabort = () => {
                    // 清理资源
                    if (realProgressTimer) {
                        clearInterval(realProgressTimer);
                        realProgressTimer = null;
                    }
                    window.streamingProgressCallback = null;
                    
                    this.activeUploads.delete(uploadId);
                    const error = new Error('流式上传已取消');
                    if (onError) onError(error);
                    reject(error);
                };

                // 发送流式请求
                console.log('开始流式传输:', {
                    url: finalUrl,
                    filename: file.name,
                    size: file.size,
                    type: 'application/octet-stream'
                });
                
                xhr.open('POST', finalUrl);
                xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                xhr.setRequestHeader('Content-Length', file.size.toString());
                
                // 设置超时时间为30分钟，匹配Vite代理配置
                xhr.timeout = 1800000; // 30分钟
                
                // 直接发送文件内容作为二进制流
                xhr.send(file);
            });

        } catch (error) {
            if (onError) onError(error);
            throw error;
        }
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
                    errorMessage += `1. 后端是否运行在端口8080\n`;
                    errorMessage += `2. Vite开发服务器代理配置\n`;
                    errorMessage += `3. 重启前端开发服务器试试\n`;
                    errorMessage += `4. 检查网络连接和防火墙\n\n`;
                    errorMessage += `调试信息:\n`;
                    errorMessage += `- 前端地址: ${window.location.href}\n`;
                    errorMessage += `- 请求URL: ${this.getApiUrl('/api/files/upload')}\n`;
                    errorMessage += `- 后端目标: http://localhost:8080\n`;
                } else {
                    errorMessage += `请检查后端是否运行在端口8080\n`;
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
                    this.activeUploads.delete(uploadId);
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
                        errorMessage = '无法连接到服务器。请检查:\n1. 后端服务是否在端口8080运行\n2. 网络连接是否正常\n3. 防火墙设置';
                    } else if (xhr.status === 0) {
                        errorMessage = 'CORS错误或服务器不可达。请检查后端服务状态。';
                    }
                    
                    const error = new Error(errorMessage);
                    if (onError) onError(error);
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
                console.log('Vite代理预期行为: 相对路径/api/files/upload应该被代理到http://localhost:8080/api/files/upload');
                
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
                diagnosis.recommendations.push('后端服务未响应，请检查后端是否运行在端口8080');
            }
        } catch (e) {
            diagnosis.backendConnection = { error: e.message };
            diagnosis.recommendations.push('无法连接后端服务，请检查:');
            diagnosis.recommendations.push('1. 后端服务是否运行在端口8080');
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
}
