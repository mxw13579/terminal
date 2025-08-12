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
        // 在开发环境中，强制使用相对路径以利用Vite代理
        if (import.meta.env.DEV || !this.backendBaseUrl) {
            console.log(`使用相对路径: ${path}`);
            return path;
        }
        
        const fullUrl = this.backendBaseUrl + path;
        console.log(`使用完整URL: ${fullUrl}`);
        return fullUrl;
    }

    /**
     * 获取当前会话ID
     * @returns {string|null}
     */
    getSessionId() {
        if (this.sessionIdProvider && typeof this.sessionIdProvider === 'function') {
            const sessionId = this.sessionIdProvider();
            console.log('StreamingFileService获取会话ID:', sessionId);
            return sessionId;
        }
        console.warn('StreamingFileService: 没有配置sessionIdProvider');
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
     * 真正的流式上传文件 - 直接从浏览器流传输到SFTP
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

            // 构建流式上传URL
            const uploadUrl = this.getApiUrl('/api/streaming/upload');
            const params = new URLSearchParams({
                sessionId,
                remotePath,
                filename: file.name
            });
            const finalUrl = `${uploadUrl}?${params.toString()}`;

            // 创建XMLHttpRequest用于进度追踪
            const xhr = new XMLHttpRequest();
            const uploadId = this.generateUploadId();
            
            // 存储上传信息
            this.activeUploads.set(uploadId, {
                xhr,
                files: [file.name],
                startTime: Date.now(),
                totalSize: file.size
            });

            return new Promise((resolve, reject) => {
                // 进度处理
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable && onProgress) {
                        const percentage = Math.round((event.loaded / event.total) * 100);
                        const elapsed = Date.now() - this.activeUploads.get(uploadId).startTime;
                        const speed = elapsed > 0 ? (event.loaded / elapsed) * 1000 : 0; // bytes/sec
                        
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

                // 完成处理
                xhr.onload = () => {
                    this.activeUploads.delete(uploadId);
                    
                    if (xhr.status >= 200 && xhr.status < 300) {
                        try {
                            const response = JSON.parse(xhr.responseText);
                            if (onComplete) {
                                onComplete({
                                    uploadId: response.uploadId || uploadId,
                                    status: 'streaming',
                                    message: '真正流式上传启动成功'
                                });
                            }
                            resolve(response.uploadId || uploadId);
                        } catch (e) {
                            const error = new Error('解析响应失败');
                            if (onError) onError(error);
                            reject(error);
                        }
                    } else {
                        const error = new Error(`流式上传失败: ${xhr.statusText}`);
                        if (onError) onError(error);
                        reject(error);
                    }
                };

                // 错误处理
                xhr.onerror = () => {
                    this.activeUploads.delete(uploadId);
                    const error = new Error('流式上传网络错误');
                    if (onError) onError(error);
                    reject(error);
                };

                // 取消处理
                xhr.onabort = () => {
                    this.activeUploads.delete(uploadId);
                    const error = new Error('流式上传已取消');
                    if (onError) onError(error);
                    reject(error);
                };

                // 发送流式请求
                xhr.open('POST', finalUrl);
                xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                xhr.setRequestHeader('Content-Length', file.size.toString());
                
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
            
            // 存储上传信息
            this.activeUploads.set(uploadId, {
                xhr,
                files: files.map(f => f.name),
                startTime: Date.now(),
                totalSize
            });

            return new Promise((resolve, reject) => {
                // 进度处理
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable && onProgress) {
                        const percentage = Math.round((event.loaded / event.total) * 100);
                        const elapsed = Date.now() - this.activeUploads.get(uploadId).startTime;
                        const speed = elapsed > 0 ? (event.loaded / elapsed) * 1000 : 0; // bytes/sec
                        
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
