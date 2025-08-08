import { AuthService } from './auth.js';

/**
 * HTTP流式文件传输服务
 * 替代基于WebSocket的base64传输，提供更高效的文件上传下载
 */
export class StreamingFileService {
    constructor() {
        this.activeUploads = new Map();
        this.activeDownloads = new Map();
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
            // 获取认证令牌
            const token = AuthService.getToken();
            if (!token) {
                throw new Error('未找到有效的认证令牌');
            }

            // 构建下载URL
            const url = new URL('/api/files/download', window.location.origin);
            url.searchParams.set('token', token);
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

            // 从响应头获取文件名
            const contentDisposition = response.headers.get('content-disposition');
            let filename = 'download';
            if (contentDisposition) {
                const match = contentDisposition.match(/filename="([^"]+)"/);
                if (match) {
                    filename = match[1];
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

            // 合并所有数据块
            const blob = new Blob(chunks);
            
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
     * 流式上传文件
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
            const maxFileSize = 100 * 1024 * 1024; // 100MB
            const maxTotalSize = 1024 * 1024 * 1024; // 1GB
            
            let totalSize = 0;
            for (const file of files) {
                if (file.size > maxFileSize) {
                    throw new Error(`文件 "${file.name}" 超过大小限制 (100MB)`);
                }
                totalSize += file.size;
            }
            
            if (totalSize > maxTotalSize) {
                throw new Error(`总文件大小超过限制 (1GB)`);
            }

            // 获取认证令牌
            const token = AuthService.getToken();
            if (!token) {
                throw new Error('未找到有效的认证令牌');
            }

            // 准备FormData
            const formData = new FormData();
            formData.append('token', token);
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
                    const error = new Error('网络错误');
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
                xhr.open('POST', '/api/files/upload');
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
            const token = AuthService.getToken();
            if (!token) return false;

            const response = await fetch(`/api/files/upload/${uploadId}/cancel`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token })
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
            const token = AuthService.getToken();
            if (!token) {
                throw new Error('未找到有效的认证令牌');
            }

            const response = await fetch(`/api/files/upload/${uploadId}/progress?token=${token}`);
            
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