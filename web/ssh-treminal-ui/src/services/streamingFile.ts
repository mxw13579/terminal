/**
 * HTTP流式文件传输服务
 * 替代基于WebSocket的base64传输，提供更高效的文件上传下载
 */

import type {
  FileTransferProgress,
  UploadResult,
  DownloadResult,
  FileUploadInfo,
  FileDownloadInfo
} from '@/types';
import { AuthService } from './auth';

export class StreamingFileService {
  private activeUploads = new Map<string, FileUploadInfo>();
  private activeDownloads = new Map<string, FileDownloadInfo>();

  /**
   * 流式下载文件
   * @param paths - 文件路径列表
   * @param onProgress - 进度回调 (loaded, total, percentage)
   * @param signal - 取消信号
   * @returns Promise containing filename and blob
   */
  async downloadFiles(
    paths: string[],
    onProgress?: ((loaded: number, total: number, percentage: number) => void) | null,
    signal?: AbortSignal | null
  ): Promise<DownloadResult> {
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
        signal: signal || undefined
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

      const chunks: Uint8Array[] = [];
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
          await reader.cancel();
          throw new Error('下载已取消');
        }
      }

      // 合并所有数据块
      const blob = new Blob(chunks);
      
      return { filename, blob };

    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error('下载已取消');
      }
      throw error;
    }
  }

  /**
   * 触发文件下载到本地
   * @param filename - 文件名
   * @param blob - 文件数据
   */
  triggerDownload(filename: string, blob: Blob): void {
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
   * @param files - 文件列表
   * @param remotePath - 远程路径
   * @param onProgress - 进度回调
   * @param onComplete - 完成回调
   * @param onError - 错误回调
   * @returns Promise containing upload ID
   */
  async uploadFiles(
    files: File[],
    remotePath: string,
    onProgress?: ((progress: FileTransferProgress) => void) | null,
    onComplete?: ((result: UploadResult) => void) | null,
    onError?: ((error: Error) => void) | null
  ): Promise<string> {
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
      
      files.forEach((file) => {
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

      return new Promise<string>((resolve, reject) => {
        // 进度处理
        xhr.upload.onprogress = (event: ProgressEvent) => {
          if (event.lengthComputable && onProgress) {
            const percentage = Math.round((event.loaded / event.total) * 100);
            const uploadInfo = this.activeUploads.get(uploadId);
            const elapsed = Date.now() - (uploadInfo?.startTime || Date.now());
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
              const response = JSON.parse(xhr.responseText) as UploadResult;
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
      if (onError && error instanceof Error) onError(error);
      throw error;
    }
  }

  /**
   * 取消上传
   * @param uploadId - 上传ID
   * @returns Promise indicating success
   */
  async cancelUpload(uploadId: string): Promise<boolean> {
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
   * @param uploadId - 上传ID
   * @returns Promise containing progress info or null
   */
  async getUploadProgress(uploadId: string): Promise<FileTransferProgress | null> {
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

      return await response.json() as FileTransferProgress;
    } catch (error) {
      console.error('获取上传进度失败:', error);
      return null;
    }
  }

  /**
   * 取消下载
   * @param downloadId - 下载ID
   */
  cancelDownload(downloadId: string): void {
    const downloadInfo = this.activeDownloads.get(downloadId);
    if (downloadInfo && downloadInfo.controller) {
      downloadInfo.controller.abort();
      this.activeDownloads.delete(downloadId);
    }
  }

  /**
   * 生成唯一上传ID
   * @returns Unique upload ID
   */
  generateUploadId(): string {
    return 'upload_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
  }

  /**
   * 格式化速度显示
   * @param bytesPerSecond - 每秒字节数
   * @returns Formatted speed string
   */
  formatSpeed(bytesPerSecond: number): string {
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
   * @param bytes - 字节数
   * @returns Formatted file size string
   */
  formatFileSize(bytes: number): string {
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
   * 计算预计剩余时间
   * @param loaded - 已加载字节数
   * @param total - 总字节数
   * @param speed - 当前速度（字节/秒）
   * @returns Estimated time remaining in seconds
   */
  calculateETA(loaded: number, total: number, speed: number): number {
    if (speed <= 0 || loaded >= total) {
      return 0;
    }
    
    const remaining = total - loaded;
    return Math.ceil(remaining / speed);
  }

  /**
   * 格式化时间显示
   * @param seconds - 秒数
   * @returns Formatted time string
   */
  formatTime(seconds: number): string {
    if (seconds < 60) {
      return `${seconds}秒`;
    } else if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      const secs = seconds % 60;
      return `${minutes}分${secs}秒`;
    } else {
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      return `${hours}小时${minutes}分`;
    }
  }

  /**
   * 获取活跃上传列表
   * @returns Map of active uploads
   */
  getActiveUploads(): Map<string, FileUploadInfo> {
    return new Map(this.activeUploads);
  }

  /**
   * 获取活跃下载列表
   * @returns Map of active downloads
   */
  getActiveDownloads(): Map<string, FileDownloadInfo> {
    return new Map(this.activeDownloads);
  }

  /**
   * 清理所有活跃传输
   */
  clearAllTransfers(): void {
    // 取消所有上传
    this.activeUploads.forEach((info, id) => {
      if (info.xhr) {
        info.xhr.abort();
      }
    });
    this.activeUploads.clear();

    // 取消所有下载
    this.activeDownloads.forEach((info, id) => {
      if (info.controller) {
        info.controller.abort();
      }
    });
    this.activeDownloads.clear();
  }
}