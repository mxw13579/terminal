// SSH Connection Manager Composable
import { ref, reactive, computed, readonly } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// Connection state management
const connectionState = reactive({
  isConnected: false,
  connectionInfo: null,
  connectionHistory: JSON.parse(localStorage.getItem('ssh-connections') || '[]').map(conn => {
    // Compatibility: convert old username field to user field
    if (conn.username && !conn.user) {
      conn.user = conn.username;
      delete conn.username;
    }
    return conn;
  }),
  currentSessionId: null,
  error: null,
  connecting: false
})

// STOMP client instance
let stompClient = null

// Connection configuration
export function useConnectionManager() {

  // Save connection info to local storage
  const saveConnection = (connectionInfo) => {
    const existing = connectionState.connectionHistory.findIndex(
      conn => conn.host === connectionInfo.host && conn.user === connectionInfo.user
    )

    if (existing !== -1) {
      connectionState.connectionHistory[existing] = {
        ...connectionInfo,
        lastUsed: new Date().toISOString()
      }
    } else {
      connectionState.connectionHistory.unshift({
        ...connectionInfo,
        id: Date.now().toString(),
        lastUsed: new Date().toISOString()
      })
    }

    // Keep only recent 10 connections
    connectionState.connectionHistory = connectionState.connectionHistory.slice(0, 10)
    localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
  }

  // Connect to server
  const connect = async (connectionInfo) => {
    console.log('Connection info received:', connectionInfo); // Debug log
    
    // Compatibility: convert username field to user field
    if (connectionInfo.username && !connectionInfo.user) {
      connectionInfo.user = connectionInfo.username;
      delete connectionInfo.username;
    }
    
    // Validate required connection parameters
    if (!connectionInfo || !connectionInfo.host || !connectionInfo.user || !connectionInfo.password) {
      const error = new Error('Missing required connection parameters (host, user, password)');
      connectionState.error = error.message;
      throw error;
    }
    
    connectionState.connecting = true
    connectionState.error = null

    try {
      // Step 1: Encrypt credentials using CryptoService (auto-fetches public key)
      console.log('[Security Auth] Encrypting SSH credentials...');
      const { CryptoService } = await import('../services/crypto.js');
      const encryptedCredentialsString = await CryptoService.encryptCredentials({
        host: connectionInfo.host,
        port: connectionInfo.port?.toString() || '22',
        user: connectionInfo.user,
        password: connectionInfo.password
      });
      
      // Step 2: Get access token
      console.log('[Security Auth] Getting access token...');
      const tokenResponse = await fetch('/api/security/session/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          encryptedCredentials: encryptedCredentialsString
        })
      });
      
      if (!tokenResponse.ok) {
        const error = await tokenResponse.json();
        throw new Error(error.message || 'Failed to get access token');
      }
      
      const { token } = await tokenResponse.json();
      console.log('[Security Auth] Token obtained successfully');

      // Step 3: Establish STOMP connection using token
      stompClient = new Client({
        webSocketFactory: () => new SockJS(`/ws/terminal`),
        connectHeaders: {
          'Authorization': `Bearer ${token}`
        },
        debug: (str) => {
          console.log('[STOMP Debug]', str)
        },
        reconnectDelay: 0, // Disable auto-reconnect
        heartbeatIncoming: 0, // Disable heartbeat
        heartbeatOutgoing: 0 // Disable heartbeat
      })

      // Connection success handler
      stompClient.onConnect = (frame) => {
        console.log('STOMP connection successful:', frame)
        console.log('STOMP frame headers:', frame.headers)
        console.log('Available header keys:', Object.keys(frame.headers || {}))
        
        connectionState.isConnected = true
        connectionState.connectionInfo = connectionInfo
        
        // 关键修复：正确获取STOMP session ID
        // 真实的session ID在后端被设置为user-name
        let realSessionId = null
        
        // 方法1：从frame headers的user-name获取（这是后端设置的真实session ID）
        if (frame.headers['user-name']) {
          realSessionId = frame.headers['user-name']
          console.log('✅ 从STOMP user-name获取真实sessionId:', realSessionId)
        }
        
        // 方法2：从其他headers获取
        if (!realSessionId) {
          realSessionId = frame.headers.session || frame.headers['session-id'] || frame.headers.sessionId
          if (realSessionId && realSessionId !== 'default') {
            console.log('✅ 从STOMP headers获取sessionId:', realSessionId)
          } else {
            realSessionId = null
          }
        }
        
        // 方法3：从STOMP客户端内部获取
        if (!realSessionId && stompClient && stompClient.ws && stompClient.ws._websocket) {
          const wsUrl = stompClient.ws._websocket.url || ''
          console.log('WebSocket URL:', wsUrl)
          
          const sockJSMatch = wsUrl.match(/\/ws\/[^/]+\/([^/]+)\/websocket/)
          if (sockJSMatch && sockJSMatch[1] && sockJSMatch[1] !== 'websocket') {
            realSessionId = sockJSMatch[1]
            console.log('✅ 从SockJS URL获取sessionId:', realSessionId)
          }
        }
        
        // 如果还是获取不到，使用default但发出警告
        if (!realSessionId) {
          console.warn('⚠️ 无法获取真实session ID，使用default')
          realSessionId = 'default'
        }
        
        connectionState.currentSessionId = realSessionId
        connectionState.connecting = false
        
        // Store connected headers for session access
        if (stompClient && frame.headers) {
          stompClient.connectedHeaders = frame.headers
          console.log('Stored connectedHeaders:', stompClient.connectedHeaders)
        }

        // Save successful connection
        saveConnection(connectionInfo)
      }

      // Connection error handler
      stompClient.onStompError = (frame) => {
        console.error('STOMP connection error:', frame)
        let errorMessage = frame.headers.message || 'Connection failed'

        // Provide detailed error messages based on error type
        if (errorMessage.includes('Connection refused')) {
          errorMessage = `Cannot connect to SSH server, please check:
1. Server address is correct
2. SSH port is correct (usually 22)
3. SSH service is running
4. Firewall allows SSH connections`
        } else if (errorMessage.includes('Authentication')) {
          errorMessage = `SSH authentication failed, please check:
1. Username is correct
2. Password is correct
3. User has SSH login permission`
        } else if (errorMessage.includes('timeout')) {
          errorMessage = `Connection timeout, please check:
1. Network connection is normal
2. Server is reachable
3. Firewall settings`
        }

        connectionState.error = errorMessage
        connectionState.connecting = false
        connectionState.isConnected = false
      }

      // Connection disconnect handler
      stompClient.onDisconnect = () => {
        console.log('STOMP connection disconnected')
        connectionState.isConnected = false
        connectionState.connectionInfo = null
        connectionState.currentSessionId = null
      }

      // WebSocket error handler
      stompClient.onWebSocketError = (error) => {
        console.error('WebSocket error:', error)
        connectionState.error = 'WebSocket connection error'
        connectionState.connecting = false
        connectionState.isConnected = false
      }

      // Activate connection
      stompClient.activate()

      // Return Promise, wait for connection completion
      return new Promise((resolve, reject) => {
        const originalOnConnect = stompClient.onConnect
        const originalOnStompError = stompClient.onStompError

        stompClient.onConnect = (frame) => {
          originalOnConnect(frame)
          resolve({ sessionId: frame.headers.session || 'default' })
        }

        stompClient.onStompError = (frame) => {
          originalOnStompError(frame)
          reject(new Error(frame.headers.message || 'Connection failed'))
        }

        // Set timeout
        setTimeout(() => {
          if (connectionState.connecting) {
            connectionState.connecting = false
            connectionState.error = 'Connection timeout'
            reject(new Error('Connection timeout'))
          }
        }, 10000)
      })

    } catch (error) {
      connectionState.error = error.message
      connectionState.connecting = false
      throw error
    }
  }

  // Disconnect
  const disconnect = async () => {
    if (stompClient && stompClient.connected) {
      try {
        stompClient.deactivate()
        console.log('STOMP connection disconnected')
      } catch (error) {
        console.warn('Error during disconnect:', error)
      }
    }

    connectionState.isConnected = false
    connectionState.connectionInfo = null
    connectionState.currentSessionId = null
    connectionState.error = null
    stompClient = null
  }

  // Check connection status
  const checkConnection = async () => {
    if (!stompClient) {
      connectionState.isConnected = false
      return false
    }

    try {
      const isConnected = stompClient.connected
      connectionState.isConnected = isConnected

      if (!isConnected) {
        connectionState.connectionInfo = null
        connectionState.currentSessionId = null
      }

      return isConnected
    } catch (error) {
      connectionState.isConnected = false
      connectionState.connectionInfo = null
      connectionState.currentSessionId = null
      return false
    }
  }

  // Remove connection from history
  const removeConnection = (connectionId) => {
    connectionState.connectionHistory = connectionState.connectionHistory.filter(
      conn => conn.id !== connectionId
    )
    localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
  }

  // Computed properties
  const connectionStatus = computed(() => {
    if (connectionState.connecting) return 'connecting'
    if (connectionState.isConnected) return 'connected'
    if (connectionState.error) return 'error'
    return 'disconnected'
  })

  const connectionDisplay = computed(() => {
    if (!connectionState.connectionInfo) return 'Not connected'
    const { user, host, port } = connectionState.connectionInfo
    return `${user}@${host}:${port || 22}`
  })

  return {
    // State
    connectionState: readonly(connectionState),
    connectionStatus,
    connectionDisplay,

    // Methods
    connect,
    disconnect,
    checkConnection,
    saveConnection,
    removeConnection,

    // STOMP client access
    getStompClient: () => stompClient
  }
}

// Singleton pattern, ensure global state consistency
export default useConnectionManager
