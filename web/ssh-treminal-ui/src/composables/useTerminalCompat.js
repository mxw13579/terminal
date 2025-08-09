/**
 * Backward compatible useTerminal composable that bridges to Pinia stores
 * This maintains compatibility with existing components during the migration
 */
import { readonly, computed } from 'vue'
import { useTerminalStore, useSftpStore, useMonitorStore } from '@/stores'

export function useTerminal(options = {}) {
  const { onShowModal = () => {} } = options

  const terminalStore = useTerminalStore()
  const sftpStore = useSftpStore()
  const monitorStore = useMonitorStore()

  // Integrate STOMP message handlers with stores
  const originalConnect = terminalStore.connect
  terminalStore.connect = async (credentials) => {
    try {
      await originalConnect(credentials)
      
      // Set up STOMP message handlers for SFTP and Monitor stores
      const stompClient = (terminalStore as any).stompClient
      if (stompClient) {
        // SFTP message handlers
        stompClient.subscribe('/user/queue/sftp/list', (message) => {
          try {
            const data = JSON.parse(message.body)
            sftpStore.handleSftpListResponse(data)
          } catch (e) {
            console.error('Error processing SFTP list response:', e)
          }
        })

        stompClient.subscribe('/user/queue/sftp/upload', (message) => {
          try {
            const data = JSON.parse(message.body)
            sftpStore.handleSftpUploadResponse(data)
          } catch (e) {
            console.error('Error processing SFTP upload response:', e)
          }
        })

        stompClient.subscribe('/user/queue/sftp/download', (message) => {
          try {
            const data = JSON.parse(message.body)
            sftpStore.handleSftpDownloadResponse(data)
          } catch (e) {
            console.error('Error processing SFTP download response:', e)
          }
        })

        stompClient.subscribe('/user/queue/sftp/error', (message) => {
          try {
            const data = JSON.parse(message.body)
            sftpStore.handleSftpError(data)
            onShowModal(`SFTP Error: ${data.message}`)
          } catch (e) {
            console.error('Error processing SFTP error:', e)
          }
        })

        // Monitor message handlers
        stompClient.subscribe('/user/queue/monitor/data', (message) => {
          try {
            const data = JSON.parse(message.body)
            monitorStore.handleMonitorUpdate(data)
          } catch (e) {
            console.error('Error processing monitor data:', e)
          }
        })

        stompClient.subscribe('/user/queue/monitor/error', (message) => {
          try {
            const data = JSON.parse(message.body)
            monitorStore.handleMonitorError(data)
            onShowModal(`Monitor Error: ${data.message}`)
          } catch (e) {
            console.error('Error processing monitor error:', e)
          }
        })
      }
    } catch (error) {
      if (error instanceof Error) {
        onShowModal(error.message)
      }
    }
  }

  // Error handling integration
  const originalClearError = terminalStore.clearError
  terminalStore.clearError = () => {
    originalClearError()
    sftpStore.clearError()
    monitorStore.clearError()
  }

  // Clean disconnect
  const originalDisconnect = terminalStore.disconnect
  terminalStore.disconnect = () => {
    originalDisconnect()
    sftpStore.reset()
    monitorStore.reset()
  }

  // Backwards compatible API that maps to Pinia stores
  return {
    // Terminal state (from terminalStore)
    host: readonly(terminalStore.host),
    port: readonly(terminalStore.port),
    user: readonly(terminalStore.user),
    isConnected: readonly(terminalStore.isConnected),
    isConnecting: readonly(terminalStore.isConnecting),
    
    // SFTP state (from sftpStore)
    sftpVisible: readonly(sftpStore.isVisible),
    sftpLoading: readonly(sftpStore.isLoading),
    sftpError: readonly(sftpStore.error),
    currentSftpPath: readonly(sftpStore.currentPath),
    sftpFiles: readonly(sftpStore.files),
    isSftpActionInProgress: readonly(sftpStore.isActionInProgress),
    localUploadProgress: readonly(sftpStore.localUploadProgress),
    remoteUploadProgress: readonly(sftpStore.remoteUploadProgress),
    uploadStatusText: readonly(sftpStore.uploadStatusText),
    uploadSpeed: readonly(sftpStore.uploadSpeed),
    sftpUploadSpeed: readonly(sftpStore.sftpUploadSpeed),
    
    // Monitor state (from monitorStore)
    monitorVisible: readonly(monitorStore.isVisible),
    isMonitoring: readonly(monitorStore.isMonitoring),
    systemStats: readonly(monitorStore.systemStats),
    dockerContainers: readonly(monitorStore.dockerContainers),
    
    // Terminal methods
    connect: terminalStore.connect,
    disconnect: terminalStore.disconnect,
    setTerminalInstance: terminalStore.setTerminalInstance,
    sendTerminalData: terminalStore.sendTerminalData,
    sendTerminalResize: terminalStore.sendTerminalResize,
    
    // SFTP methods
    toggleSftpPanel: sftpStore.toggle,
    fetchSftpList: sftpStore.fetchFileList,
    downloadSftpFiles: sftpStore.downloadFiles,
    uploadSftpFile: sftpStore.uploadFile,
    
    // Monitor methods
    toggleMonitorPanel: monitorStore.toggle,
    
    // Enhanced error handling
    clearAllErrors: () => {
      terminalStore.clearError()
      sftpStore.clearError()
      monitorStore.clearError()
    },
    
    // Connection retry
    retryConnection: terminalStore.retryConnection,
    
    // Store access for advanced usage
    stores: {
      terminal: terminalStore,
      sftp: sftpStore,
      monitor: monitorStore
    }
  }
}