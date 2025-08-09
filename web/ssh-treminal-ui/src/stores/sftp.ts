import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { 
  SftpMessage,
  FileTransferProgress,
  LoadingState,
  ErrorState,
  EmptyState
} from '@/types'
import { StreamingFileService } from '@/services/streamingFile'
import { formatSpeed } from '@/utils/formatters'
import { useTerminalStore } from './terminal'

export interface SftpFile {
  name: string
  type: 'file' | 'directory' | 'symlink'
  size: number
  permissions: string
  lastModified: string
  owner: string
  group: string
}

export const useSftpStore = defineStore('sftp', () => {
  // Panel visibility
  const isVisible = ref(false)
  
  // File listing state
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const currentPath = ref('')
  const files = ref<SftpFile[]>([])
  
  // File transfer state
  const isActionInProgress = ref(false)
  const localUploadProgress = ref(0)
  const remoteUploadProgress = ref(0)
  const uploadStatusText = ref('')
  const uploadSpeed = ref('')
  const sftpUploadSpeed = ref('')
  
  // Active transfers
  const activeUploads = ref(new Map<string, FileTransferProgress>())
  const activeDownloads = ref(new Map<string, FileTransferProgress>())
  
  // Services
  const streamingFileService = new StreamingFileService()
  
  // Computed states
  const loadingState = computed((): LoadingState => ({
    isLoading: isLoading.value,
    message: isLoading.value ? '正在加载文件列表...' : undefined
  }))

  const errorState = computed((): ErrorState => ({
    hasError: !!error.value,
    error: error.value,
    canRetry: !isLoading.value
  }))

  const emptyState = computed((): EmptyState => ({
    isEmpty: files.value.length === 0 && !isLoading.value && !error.value,
    message: '此目录为空',
    actionText: '刷新',
    onAction: () => fetchFileList(currentPath.value)
  }))

  const transferInProgress = computed(() => 
    isActionInProgress.value || activeUploads.value.size > 0 || activeDownloads.value.size > 0
  )

  const uploadProgress = computed(() => ({
    local: localUploadProgress.value,
    remote: remoteUploadProgress.value,
    status: uploadStatusText.value,
    speed: uploadSpeed.value || sftpUploadSpeed.value
  }))

  // Message handlers
  const handleSftpListResponse = (data: any) => {
    if (data.type === 'sftp_list_response') {
      isLoading.value = false
      error.value = null
      currentPath.value = data.path
      files.value = data.files
    }
  }

  const handleSftpUploadResponse = (data: any) => {
    if (data.type === 'sftp_upload_chunk_success') {
      localUploadProgress.value = Math.round(((data.chunkIndex + 1) / data.totalChunks) * 100)
    } else if (data.type === 'sftp_remote_progress') {
      remoteUploadProgress.value = data.progress
      sftpUploadSpeed.value = formatSpeed(data.speed)
      uploadStatusText.value = `正在上传到服务器... ${data.progress}%`
    } else if (data.type === 'sftp_upload_final_success') {
      remoteUploadProgress.value = 100
      isActionInProgress.value = false
      uploadStatusText.value = '上传完成！'
      sftpUploadSpeed.value = ''
      // Refresh file list
      fetchFileList(data.path || currentPath.value)
    }
  }

  const handleSftpDownloadResponse = (data: any) => {
    if (data.type === 'sftp_download_response') {
      handleLegacyFileDownload(data.filename, data.content)
    }
  }

  const handleLegacyFileDownload = (filename: string, base64Content: string) => {
    try {
      const byteCharacters = atob(base64Content)
      const byteNumbers = Array.from(byteCharacters, char => char.charCodeAt(0))
      const byteArray = new Uint8Array(byteNumbers)
      const blob = new Blob([byteArray])

      streamingFileService.triggerDownload(filename, blob)
    } catch (error) {
      console.error('Legacy download failed:', error)
      setError('创建下载文件失败')
    } finally {
      isActionInProgress.value = false
    }
  }

  const handleSftpError = (data: any) => {
    isLoading.value = false
    isActionInProgress.value = false
    setError(`SFTP Error: ${data.message}`)
  }

  // Actions
  const show = () => {
    isVisible.value = true
    if (files.value.length === 0) {
      fetchFileList()
    }
  }

  const hide = () => {
    isVisible.value = false
  }

  const toggle = () => {
    if (isVisible.value) {
      hide()
    } else {
      show()
    }
  }

  const fetchFileList = (path = '.') => {
    const terminalStore = useTerminalStore()
    const stompClient = (terminalStore as any).stompClient
    
    if (stompClient?.connected) {
      isLoading.value = true
      error.value = null
      stompClient.publish({
        destination: '/app/sftp/list',
        body: JSON.stringify({ path })
      })
    } else {
      setError('SSH连接未建立')
    }
  }

  const navigateToPath = (path: string) => {
    fetchFileList(path)
  }

  const navigateUp = () => {
    if (currentPath.value && currentPath.value !== '/' && currentPath.value !== '.') {
      const parentPath = currentPath.value.split('/').slice(0, -1).join('/') || '/'
      fetchFileList(parentPath)
    }
  }

  const downloadFiles = async (paths: string[]) => {
    if (paths.length === 0) return
    
    isActionInProgress.value = true
    error.value = null

    const downloadId = `download_${Date.now()}`
    
    try {
      const abortController = new AbortController()
      
      // Track active download
      activeDownloads.value.set(downloadId, {
        loaded: 0,
        total: 0,
        percentage: 0,
        status: 'downloading'
      })

      const onProgress = (loaded: number, total: number, percentage: number) => {
        if (percentage !== undefined) {
          activeDownloads.value.set(downloadId, {
            loaded,
            total,
            percentage,
            status: 'downloading',
            speed: total > 0 ? loaded / ((Date.now() - Date.now()) / 1000) : 0
          })
        }
      }

      const { filename, blob } = await streamingFileService.downloadFiles(
        paths, 
        onProgress, 
        abortController.signal
      )

      streamingFileService.triggerDownload(filename, blob)
      
      activeDownloads.value.set(downloadId, {
        loaded: blob.size,
        total: blob.size,
        percentage: 100,
        status: 'completed'
      })

      // Remove completed download after delay
      setTimeout(() => {
        activeDownloads.value.delete(downloadId)
      }, 3000)

    } catch (error) {
      console.error('Download failed:', error)
      const errorMsg = error instanceof Error ? error.message : String(error)
      setError(`下载失败: ${errorMsg}`)
      
      activeDownloads.value.set(downloadId, {
        loaded: 0,
        total: 0,
        percentage: 0,
        status: 'error'
      })
      
      setTimeout(() => {
        activeDownloads.value.delete(downloadId)
      }, 5000)
    } finally {
      isActionInProgress.value = false
    }
  }

  const uploadFile = async (file: File) => {
    if (!file) return
    
    isActionInProgress.value = true
    error.value = null
    localUploadProgress.value = 0
    remoteUploadProgress.value = 0
    uploadStatusText.value = `准备上传: ${file.name}`
    uploadSpeed.value = ''
    sftpUploadSpeed.value = ''
    
    const uploadId = `upload_${Date.now()}`
    
    try {
      const onProgress = (progressData: FileTransferProgress) => {
        localUploadProgress.value = progressData.percentage
        uploadSpeed.value = streamingFileService.formatSpeed(progressData.speed || 0)
        uploadStatusText.value = `正在上传: ${progressData.percentage}%`
        
        activeUploads.value.set(uploadId, {
          ...progressData,
          status: 'uploading'
        })
      }

      const onComplete = (result: any) => {
        uploadStatusText.value = '上传完成！'
        
        activeUploads.value.set(uploadId, {
          loaded: file.size,
          total: file.size,
          percentage: 100,
          status: 'completed'
        })
        
        // Refresh file list
        setTimeout(() => {
          fetchFileList(currentPath.value)
          activeUploads.value.delete(uploadId)
        }, 2000)
      }

      const onError = (error: Error) => {
        console.error('Upload failed:', error)
        setError(`上传失败: ${error.message}`)
        
        activeUploads.value.set(uploadId, {
          loaded: 0,
          total: file.size,
          percentage: 0,
          status: 'error'
        })
        
        setTimeout(() => {
          activeUploads.value.delete(uploadId)
        }, 5000)
      }

      await streamingFileService.uploadFiles(
        [file], 
        currentPath.value,
        onProgress,
        onComplete,
        onError
      )

    } catch (error) {
      console.error('Failed to start upload:', error)
      const errorMsg = error instanceof Error ? error.message : String(error)
      setError(`启动上传失败: ${errorMsg}`)
    } finally {
      isActionInProgress.value = false
    }
  }

  const uploadMultipleFiles = async (files: FileList) => {
    const fileArray = Array.from(files)
    for (const file of fileArray) {
      await uploadFile(file)
    }
  }

  const cancelUpload = async (uploadId: string) => {
    try {
      await streamingFileService.cancelUpload(uploadId)
      activeUploads.value.delete(uploadId)
    } catch (error) {
      console.error('Failed to cancel upload:', error)
    }
  }

  const cancelDownload = (downloadId: string) => {
    streamingFileService.cancelDownload(downloadId)
    activeDownloads.value.delete(downloadId)
  }

  const setError = (message: string) => {
    error.value = message
  }

  const clearError = () => {
    error.value = null
  }

  const refresh = () => {
    fetchFileList(currentPath.value)
  }

  const reset = () => {
    isVisible.value = false
    isLoading.value = false
    error.value = null
    currentPath.value = ''
    files.value = []
    isActionInProgress.value = false
    localUploadProgress.value = 0
    remoteUploadProgress.value = 0
    uploadStatusText.value = ''
    uploadSpeed.value = ''
    sftpUploadSpeed.value = ''
    activeUploads.value.clear()
    activeDownloads.value.clear()
  }

  return {
    // State
    isVisible,
    isLoading,
    error,
    currentPath,
    files,
    isActionInProgress,
    localUploadProgress,
    remoteUploadProgress,
    uploadStatusText,
    uploadSpeed,
    sftpUploadSpeed,
    activeUploads,
    activeDownloads,
    
    // Computed
    loadingState,
    errorState,
    emptyState,
    transferInProgress,
    uploadProgress,
    
    // Actions
    show,
    hide,
    toggle,
    fetchFileList,
    navigateToPath,
    navigateUp,
    downloadFiles,
    uploadFile,
    uploadMultipleFiles,
    cancelUpload,
    cancelDownload,
    setError,
    clearError,
    refresh,
    reset,
    
    // Message handlers (for STOMP integration)
    handleSftpListResponse,
    handleSftpUploadResponse,
    handleSftpDownloadResponse,
    handleSftpError
  }
})