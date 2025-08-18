import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { 
  SshCredentials, 
  ConnectionConfig, 
  TerminalMessage,
  StompMessage,
  LoadingState,
  ErrorState
} from '@/types'
import { AuthService } from '@/services/auth'
import { StreamingFileService } from '@/services/streamingFile'

export const useTerminalStore = defineStore('terminal', () => {
  // Connection state
  const host = ref('')
  const port = ref('')
  const user = ref('')
  const isConnected = ref(false)
  const isConnecting = ref(false)
  const connectionError = ref<string | null>(null)
  
  // Terminal instance and buffering
  const terminalInstance = ref<any>(null)
  const terminalOutputBuffer: string[] = []
  let terminalOutputTimer: number | null = null
  let resizeTimeout: NodeJS.Timeout | null = null
  
  // STOMP connection
  let stompClient: Client | null = null
  let currentCredentials: SshCredentials | null = null
  
  // Services
  const streamingFileService = new StreamingFileService()

  // Computed states
  const connectionState = computed((): LoadingState => ({
    isLoading: isConnecting.value,
    message: isConnecting.value ? '正在连接...' : undefined
  }))

  const errorState = computed((): ErrorState => ({
    hasError: !!connectionError.value,
    error: connectionError.value,
    canRetry: !isConnected.value && !isConnecting.value
  }))

  // Terminal output buffering for performance
  const bufferTerminalOutput = (data: string) => {
    terminalOutputBuffer.push(data)
    
    if (!terminalOutputTimer) {
      terminalOutputTimer = requestAnimationFrame(flushTerminalOutput)
    }
  }

  const flushTerminalOutput = () => {
    terminalOutputTimer = null
    
    if (!terminalInstance.value || terminalOutputBuffer.length === 0) {
      return
    }

    const maxChunkSize = 4096 // 4KB per frame
    let totalSize = 0
    let flushData = ''
    
    while (terminalOutputBuffer.length > 0 && totalSize < maxChunkSize) {
      const data = terminalOutputBuffer.shift()!
      if (totalSize + data.length <= maxChunkSize) {
        flushData += data
        totalSize += data.length
      } else {
        terminalOutputBuffer.unshift(data)
        break
      }
    }

    if (flushData) {
      try {
        terminalInstance.value.write(flushData)
      } catch (e) {
        console.error('Terminal write error:', e)
      }
    }

    if (terminalOutputBuffer.length > 0) {
      terminalOutputTimer = requestAnimationFrame(flushTerminalOutput)
    }
  }

  // Debounced terminal resize
  const debouncedTerminalResize = (size: { cols: number; rows: number }) => {
    if (resizeTimeout) {
      clearTimeout(resizeTimeout)
    }
    
    resizeTimeout = setTimeout(() => {
      if (stompClient?.connected) {
        stompClient.publish({
          destination: '/app/terminal/resize',
          body: JSON.stringify({ cols: size.cols, rows: size.rows })
        })
      }
    }, 150)
  }

  // STOMP message subscriptions
  const subscribeToQueues = () => {
    if (!stompClient) return

    // Skip terminal output subscription - handled by useTerminal composable to avoid duplication
    // Terminal output subscription moved to useTerminal.js to prevent duplicate subscriptions
    console.log('Terminal store: Skipping terminal output subscription to prevent duplication')
    
    // Note: Terminal output is now handled exclusively by useTerminal.js composable
    // to prevent the "lllsss" duplication issue when typing "ls"

    // Terminal error subscription
    stompClient.subscribe('/user/queue/terminal/error', (message) => {
      try {
        const data = JSON.parse(message.body) as StompMessage
        connectionError.value = `Terminal error: ${data.data}`
      } catch (e) {
        console.error('Error processing terminal error:', e)
      }
    })

    // Global errors subscription
    stompClient.subscribe('/user/queue/errors', (message) => {
      try {
        const data = JSON.parse(message.body) as StompMessage
        connectionError.value = `Error: ${data.data}`
      } catch (e) {
        console.error('Error processing global error:', e)
      }
    })
  }

  const startTerminalOutputForwarding = () => {
    if (stompClient?.connected) {
      stompClient.publish({
        destination: '/app/terminal/start-forwarding',
        body: JSON.stringify({})
      })
    }
  }

  // Connection methods
  const connect = async (credentials: SshCredentials) => {
    try {
      host.value = credentials.host
      port.value = String(credentials.port || 22)
      user.value = credentials.user
      isConnecting.value = true
      connectionError.value = null

      // Store credentials for reconnection
      currentCredentials = { ...credentials }

      console.debug('Starting secure connection flow...')

      // Get session token
      await AuthService.getSessionToken(credentials)
      
      const connectHeaders = AuthService.getConnectionHeaders()
      if (!connectHeaders) {
        throw new Error('Unable to get valid authentication token')
      }

      console.debug('Creating STOMP connection with secure token...')

      // Create STOMP client
      stompClient = new Client({
        webSocketFactory: () => new SockJS('/ws/terminal'),
        connectHeaders,
        debug: function (str) {
          if (import.meta.env?.MODE === 'development') {
            console.log('STOMP: ' + str)
          }
        },
        reconnectDelay: () => {
          const attempt = (stompClient as any).reconnectAttempts || 0
          const baseDelay = 1000
          const maxDelay = 30000
          const delay = Math.min(baseDelay * Math.pow(2, attempt), maxDelay)
          const jitter = Math.random() * 1000
          return delay + jitter
        },
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
      })

      // Connection success handler
      stompClient.onConnect = (frame) => {
        console.log('STOMP Connected:', frame)
        isConnecting.value = false
        isConnected.value = true
        connectionError.value = null

        subscribeToQueues()
        startTerminalOutputForwarding()
      }

      // STOMP error handler
      stompClient.onStompError = async (frame) => {
        console.error('STOMP Error:', frame.headers['message'])
        
        const errorMessage = frame.headers['message'] || 'Connection authentication failed'
        
        // Check for authentication errors and retry with new token
        if (errorMessage.includes('认证') || errorMessage.includes('令牌') || 
            errorMessage.includes('授权') || errorMessage.includes('Authentication')) {
          
          console.warn('Authentication error detected, attempting token refresh...')
          
          if (currentCredentials) {
            const retryHeaders = await AuthService.handleConnectionRetry(currentCredentials)
            if (retryHeaders) {
              console.info('Token updated successfully, retrying connection...')
              stompClient!.connectHeaders = retryHeaders
              return
            }
          }
        }
        
        isConnecting.value = false
        connectionError.value = `Connection error: ${errorMessage}`
      }

      // Disconnect handler
      stompClient.onDisconnect = async () => {
        console.log('STOMP Disconnected')
        
        if (isConnected.value) {
          console.info('Connection unexpectedly closed, attempting recovery...')
          
          if (currentCredentials) {
            const retryHeaders = await AuthService.handleConnectionRetry(currentCredentials)
            if (retryHeaders) {
              console.info('Preparing reconnection with new token...')
              stompClient!.connectHeaders = retryHeaders
              return
            } else {
              connectionError.value = 'Connection lost, please reconnect'
            }
          }
        }
        
        resetConnection()
      }

      // Activate STOMP connection
      stompClient.activate()

    } catch (error) {
      console.error('Connection failed:', error)
      isConnecting.value = false
      
      let userMessage = error instanceof Error ? error.message : String(error)
      if (userMessage.includes('不支持')) {
        userMessage = 'Browser does not support required security features, please upgrade to latest Chrome, Firefox, or Edge'
      } else if (userMessage.includes('网络')) {
        userMessage = 'Network connection failed, please check network connection and retry'
      } else if (userMessage.includes('凭据')) {
        userMessage = 'Login verification failed, please check host address, username, and password'
      }
      
      connectionError.value = `Connection failed: ${userMessage}`
      resetConnection()
    }
  }

  const disconnect = () => {
    if (stompClient) {
      stompClient.deactivate()
    }
    
    if (terminalInstance.value) {
      terminalInstance.value.write('\r\n🔌 Connection closed by user.\r\n')
    }
    
    AuthService.clearToken()
    currentCredentials = null
    
    resetConnection()
  }

  const resetConnection = () => {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    
    // Clear buffering
    terminalOutputBuffer.length = 0
    if (terminalOutputTimer) {
      cancelAnimationFrame(terminalOutputTimer)
      terminalOutputTimer = null
    }
    if (resizeTimeout) {
      clearTimeout(resizeTimeout)
      resizeTimeout = null
    }
    
    // Reset state
    host.value = ''
    port.value = ''
    user.value = ''
    isConnected.value = false
    isConnecting.value = false
    connectionError.value = null
    
    AuthService.clearToken()
    currentCredentials = null
  }

  // Terminal interaction methods
  const setTerminalInstance = (instance: any) => {
    terminalInstance.value = instance
  }
  
  const sendTerminalData = (data: string) => {
    if (stompClient?.connected) {
      stompClient.publish({
        destination: '/app/terminal/data',
        body: JSON.stringify({ data })
      })
    }
  }
  
  const sendTerminalResize = (size: { cols: number; rows: number }) => {
    debouncedTerminalResize(size)
  }

  // Clear error manually
  const clearError = () => {
    connectionError.value = null
  }

  // Retry connection with same credentials
  const retryConnection = async () => {
    if (currentCredentials && !isConnecting.value) {
      await connect(currentCredentials)
    }
  }

  return {
    // State
    host,
    port,
    user,
    isConnected,
    isConnecting,
    connectionError,
    
    // Computed
    connectionState,
    errorState,
    
    // Actions
    connect,
    disconnect,
    resetConnection,
    setTerminalInstance,
    sendTerminalData,
    sendTerminalResize,
    clearError,
    retryConnection,
    
    // For backward compatibility
    terminalInstance
  }
})