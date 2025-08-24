// WebSocket连接管理器 - 优化版本
import { ref, reactive, computed, readonly, watch } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// 连接状态管理
const connectionState = reactive({
  isConnected: false,
  connectionInfo: null,
  connectionHistory: JSON.parse(localStorage.getItem('ssh-connections') || '[]').map(conn => {
    // 兼容性：转换旧的username字段为user字段
    if (conn.username && !conn.user) {
      conn.user = conn.username;
      delete conn.username;
    }
    return conn;
  }),
  currentSessionId: null,
  error: null,
  connecting: false,
  reconnecting: false,
  reconnectAttempts: 0,
  maxReconnectAttempts: 3
})

// STOMP客户端实例
let stompClient = null
let keepAliveInterval = null
let reconnectTimeout = null
let heartbeatTimeout = null

// 性能监控
const performanceMetrics = reactive({
  connectionTime: 0,
  lastHeartbeat: null,
  messagesSent: 0,
  messagesReceived: 0,
  averageLatency: 0,
  connectionQuality: 'unknown' // unknown, excellent, good, fair, poor
})

// 连接质量评估
const assessConnectionQuality = () => {
  const now = Date.now()
  const timeSinceHeartbeat = performanceMetrics.lastHeartbeat 
    ? now - performanceMetrics.lastHeartbeat 
    : Infinity
  
  if (timeSinceHeartbeat > 60000) { // 超过60秒无心跳
    performanceMetrics.connectionQuality = 'poor'
  } else if (timeSinceHeartbeat > 30000) { // 超过30秒无心跳
    performanceMetrics.connectionQuality = 'fair'
  } else if (performanceMetrics.averageLatency > 1000) { // 延迟超过1秒
    performanceMetrics.connectionQuality = 'fair'
  } else if (performanceMetrics.averageLatency > 500) { // 延迟超过500ms
    performanceMetrics.connectionQuality = 'good'
  } else {
    performanceMetrics.connectionQuality = 'excellent'
  }
}

// 智能重连策略
const getReconnectDelay = (attempts) => {
  // 指数退避策略，最大延迟30秒
  return Math.min(1000 * Math.pow(2, attempts), 30000)
}

// Keep-alive优化机制
const startKeepAlive = () => {
  if (keepAliveInterval) {
    clearInterval(keepAliveInterval)
  }
  
  // 根据连接质量调整心跳间隔
  const getHeartbeatInterval = () => {
    switch (performanceMetrics.connectionQuality) {
      case 'excellent': return 30000 // 30秒
      case 'good': return 20000      // 20秒
      case 'fair': return 15000      // 15秒
      case 'poor': return 10000      // 10秒
      default: return 25000          // 默认25秒
    }
  }
  
  keepAliveInterval = setInterval(() => {
    if (stompClient && connectionState.isConnected) {
      try {
        const startTime = Date.now()
        console.log('[KeepAlive] Sending heartbeat ping...')
        
        stompClient.publish({
          destination: '/app/keep-alive',
          body: JSON.stringify({ 
            timestamp: startTime,
            quality: performanceMetrics.connectionQuality 
          })
        })
        
        performanceMetrics.messagesSent++
        performanceMetrics.lastHeartbeat = startTime
        
        // 监听心跳响应以计算延迟
        const heartbeatSubscription = stompClient.subscribe('/user/queue/heartbeat', (message) => {
          const endTime = Date.now()
          const latency = endTime - startTime
          
          // 更新平均延迟
          performanceMetrics.averageLatency = 
            (performanceMetrics.averageLatency * 0.8) + (latency * 0.2)
          
          performanceMetrics.messagesReceived++
          assessConnectionQuality()
          
          // 取消订阅避免内存泄漏
          heartbeatSubscription.unsubscribe()
          
          console.log(`[KeepAlive] Heartbeat latency: ${latency}ms, quality: ${performanceMetrics.connectionQuality}`)
        })
        
      } catch (error) {
        console.warn('[KeepAlive] Failed to send heartbeat:', error)
        performanceMetrics.connectionQuality = 'poor'
      }
    }
    
    // 动态调整心跳间隔
    clearInterval(keepAliveInterval)
    keepAliveInterval = setInterval(arguments.callee, getHeartbeatInterval())
  }, getHeartbeatInterval())
}

const stopKeepAlive = () => {
  if (keepAliveInterval) {
    clearInterval(keepAliveInterval)
    keepAliveInterval = null
    console.log('[KeepAlive] Keep-alive mechanism stopped')
  }
}

// 连接管理器主函数
export function useConnectionManager() {
  
  // 错误恢复机制
  const handleConnectionError = (error, context = 'unknown') => {
    console.error(`[ConnectionManager] Error in ${context}:`, error)
    
    // 记录错误统计
    performanceMetrics.connectionQuality = 'poor'
    
    // 根据错误类型采取不同策略
    if (error.message?.includes('Authentication')) {
      // 认证错误不重连
      connectionState.error = '认证失败，请检查用户名和密码'
      connectionState.reconnecting = false
      return
    }
    
    if (error.message?.includes('Network')) {
      // 网络错误尝试重连
      scheduleReconnect()
      return
    }
    
    connectionState.error = error.message || '连接错误'
  }
  
  // 智能重连调度
  const scheduleReconnect = () => {
    if (connectionState.reconnectAttempts >= connectionState.maxReconnectAttempts) {
      console.warn('[ConnectionManager] Max reconnect attempts reached')
      connectionState.reconnecting = false
      connectionState.error = '重连失败，请手动重试连接'
      return
    }
    
    const delay = getReconnectDelay(connectionState.reconnectAttempts)
    console.log(`[ConnectionManager] Scheduling reconnect in ${delay}ms (attempt ${connectionState.reconnectAttempts + 1})`)
    
    connectionState.reconnecting = true
    connectionState.reconnectAttempts++
    
    reconnectTimeout = setTimeout(async () => {
      if (connectionState.connectionInfo) {
        try {
          await connect(connectionState.connectionInfo)
          // 重连成功，重置计数器
          connectionState.reconnectAttempts = 0
        } catch (error) {
          handleConnectionError(error, 'reconnect')
        }
      }
    }, delay)
  }
  
  // 优化的连接函数
  const connect = async (connectionInfo) => {
    const connectionStartTime = Date.now()
    console.log('[ConnectionManager] Starting connection process...')
    
    // 清理旧连接
    if (stompClient) {
      await disconnect()
    }
    
    // 重置状态
    connectionState.connecting = true
    connectionState.error = null
    connectionState.reconnectAttempts = 0
    
    // 参数验证优化
    const validateConnectionInfo = (info) => {
      const required = ['host', 'user', 'password']
      const missing = required.filter(field => !info?.[field])
      
      if (missing.length > 0) {
        throw new Error(`缺少必要参数: ${missing.join(', ')}`)
      }
      
      // 兼容性处理
      if (info.username && !info.user) {
        info.user = info.username
        delete info.username
      }
      
      return info
    }
    
    try {
      const validatedInfo = validateConnectionInfo(connectionInfo)
      
      // Step 1: 加密凭据
      console.log('[ConnectionManager] Encrypting credentials...')
      const { CryptoService } = await import('../services/crypto.js')
      const encryptedCredentialsString = await CryptoService.encryptCredentials({
        host: validatedInfo.host,
        port: validatedInfo.port?.toString() || '22',
        user: validatedInfo.user,
        password: validatedInfo.password
      })
      
      // Step 2: 获取访问令牌
      console.log('[ConnectionManager] Obtaining access token...')
      const tokenResponse = await fetch('/api/security/session/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ encryptedCredentials: encryptedCredentialsString })
      })
      
      if (!tokenResponse.ok) {
        const error = await tokenResponse.json()
        throw new Error(error.message || 'Token获取失败')
      }
      
      const { token } = await tokenResponse.json()
      
      // Step 3: 建立STOMP连接
      console.log('[ConnectionManager] Establishing STOMP connection...')
      stompClient = new Client({
        webSocketFactory: () => new SockJS('/ws/terminal'),
        connectHeaders: { 'Authorization': `Bearer ${token}` },
        debug: (str) => console.log('[STOMP]', str),
        
        // 优化的连接参数
        reconnectDelay: 0, // 禁用STOMP内置重连，使用自定义重连逻辑
        heartbeatIncoming: 25000,
        heartbeatOutgoing: 25000,
        
        // 连接超时
        connectionTimeout: 10000
      })
      
      // 连接成功处理
      stompClient.onConnect = (frame) => {
        const connectionTime = Date.now() - connectionStartTime
        performanceMetrics.connectionTime = connectionTime
        
        console.log(`[ConnectionManager] Connected successfully in ${connectionTime}ms`)
        
        connectionState.isConnected = true
        connectionState.connectionInfo = validatedInfo
        connectionState.connecting = false
        connectionState.reconnecting = false
        connectionState.currentSessionId = frame.headers['user-name'] || frame.headers.session || 'default'
        
        // 启动性能监控
        startKeepAlive()
        
        // 设置终端订阅
        setupTerminalSubscriptions()
        
        // 保存连接历史
        saveConnection(validatedInfo)
      }
      
      // 错误处理
      stompClient.onStompError = (frame) => {
        handleConnectionError(new Error(frame.headers.message || '连接失败'), 'stomp')
      }
      
      stompClient.onDisconnect = () => {
        console.log('[ConnectionManager] Disconnected')
        connectionState.isConnected = false
        stopKeepAlive()
        
        // 如果不是主动断开，尝试重连
        if (!connectionState.connecting) {
          scheduleReconnect()
        }
      }
      
      stompClient.onWebSocketError = (error) => {
        handleConnectionError(error, 'websocket')
      }
      
      // 激活连接
      stompClient.activate()
      
      return new Promise((resolve, reject) => {
        const originalOnConnect = stompClient.onConnect
        const originalOnStompError = stompClient.onStompError
        
        stompClient.onConnect = (frame) => {
          originalOnConnect(frame)
          resolve({ sessionId: connectionState.currentSessionId })
        }
        
        stompClient.onStompError = (frame) => {
          originalOnStompError(frame)
          reject(new Error(frame.headers.message || '连接失败'))
        }
        
        // 连接超时
        setTimeout(() => {
          if (connectionState.connecting) {
            connectionState.connecting = false
            connectionState.error = '连接超时'
            reject(new Error('连接超时'))
          }
        }, 15000) // 增加超时时间到15秒
      })
      
    } catch (error) {
      connectionState.error = error.message
      connectionState.connecting = false
      throw error
    }
  }
  
  // 优雅断开连接
  const disconnect = async () => {
    console.log('[ConnectionManager] Initiating disconnect...')
    
    // 清理定时器
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout)
      reconnectTimeout = null
    }
    
    if (heartbeatTimeout) {
      clearTimeout(heartbeatTimeout)
      heartbeatTimeout = null
    }
    
    stopKeepAlive()
    
    // 清理终端处理器
    if (stompClient?.terminalHandlers) {
      stompClient.terminalHandlers.clear()
    }
    
    // 断开STOMP连接
    if (stompClient?.connected) {
      try {
        stompClient.deactivate()
      } catch (error) {
        console.warn('[ConnectionManager] Error during disconnect:', error)
      }
    }
    
    // 重置状态
    Object.assign(connectionState, {
      isConnected: false,
      connectionInfo: null,
      currentSessionId: null,
      error: null,
      connecting: false,
      reconnecting: false,
      reconnectAttempts: 0
    })
    
    // 重置性能指标
    Object.assign(performanceMetrics, {
      connectionTime: 0,
      lastHeartbeat: null,
      messagesSent: 0,
      messagesReceived: 0,
      averageLatency: 0,
      connectionQuality: 'unknown'
    })
    
    stompClient = null
    console.log('[ConnectionManager] Disconnected successfully')
  }
  
  // 终端订阅设置
  const setupTerminalSubscriptions = () => {
    if (!stompClient) return
    
    console.log('[ConnectionManager] Setting up terminal subscriptions...')
    
    const terminalHandlers = new Set()
    stompClient.terminalHandlers = terminalHandlers
    
    stompClient.subscribe('/user/queue/terminal', (message) => {
      try {
        const data = JSON.parse(message.body)
        performanceMetrics.messagesReceived++
        
        if (terminalHandlers.size > 0) {
          for (const handler of terminalHandlers) {
            if (typeof handler === 'function') {
              handler(data.payload)
            }
          }
        }
      } catch (error) {
        console.error('[ConnectionManager] Terminal message processing error:', error)
      }
    })
    
    // 启动终端转发
    setTimeout(() => {
      if (stompClient?.connected) {
        stompClient.publish({
          destination: '/app/terminal/start-forwarding',
          body: JSON.stringify({})
        })
      }
    }, 500)
  }
  
  // 连接历史保存
  const saveConnection = (connectionInfo) => {
    const existing = connectionState.connectionHistory.findIndex(
      conn => conn.host === connectionInfo.host && conn.user === connectionInfo.user
    )
    
    const connectionEntry = {
      ...connectionInfo,
      lastUsed: new Date().toISOString(),
      performance: {
        connectionTime: performanceMetrics.connectionTime,
        quality: performanceMetrics.connectionQuality
      }
    }
    
    if (existing !== -1) {
      connectionState.connectionHistory[existing] = connectionEntry
    } else {
      connectionState.connectionHistory.unshift({
        ...connectionEntry,
        id: Date.now().toString()
      })
    }
    
    // 保持最近10个连接
    connectionState.connectionHistory = connectionState.connectionHistory.slice(0, 10)
    localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
  }
  
  // 计算属性
  const connectionStatus = computed(() => {
    if (connectionState.reconnecting) return 'reconnecting'
    if (connectionState.connecting) return 'connecting'
    if (connectionState.isConnected) return 'connected'
    if (connectionState.error) return 'error'
    return 'disconnected'
  })
  
  const connectionDisplay = computed(() => {
    if (!connectionState.connectionInfo) return '未连接'
    const { user, host, port } = connectionState.connectionInfo
    return `${user}@${host}:${port || 22}`
  })
  
  const connectionQuality = computed(() => performanceMetrics.connectionQuality)
  
  // 监听连接状态变化
  watch(connectionStatus, (newStatus) => {
    console.log(`[ConnectionManager] Status changed to: ${newStatus}`)
  })
  
  return {
    // 状态
    connectionState: readonly(connectionState),
    performanceMetrics: readonly(performanceMetrics),
    connectionStatus,
    connectionDisplay,
    connectionQuality,
    
    // 方法
    connect,
    disconnect,
    
    // 工具方法
    getStompClient: () => stompClient,
    startKeepAlive,
    stopKeepAlive,
    
    // 终端处理器管理
    registerTerminalHandler: (handler) => {
      if (stompClient?.terminalHandlers && typeof handler === 'function') {
        stompClient.terminalHandlers.add(handler)
        console.log(`[ConnectionManager] Registered handler, total: ${stompClient.terminalHandlers.size}`)
        return true
      }
      return false
    },
    
    unregisterTerminalHandler: (handler) => {
      if (stompClient?.terminalHandlers) {
        const removed = stompClient.terminalHandlers.delete(handler)
        console.log(`[ConnectionManager] Unregistered handler, remaining: ${stompClient.terminalHandlers.size}`)
        return removed
      }
      return false
    },
    
    // 连接历史管理
    removeConnection: (connectionId) => {
      connectionState.connectionHistory = connectionState.connectionHistory.filter(
        conn => conn.id !== connectionId
      )
      localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
    },
    
    // 手动重连
    reconnect: () => {
      if (connectionState.connectionInfo && !connectionState.connecting) {
        connectionState.reconnectAttempts = 0
        return connect(connectionState.connectionInfo)
      }
    }
  }
}

// 单例模式，确保全局状态一致性
export default useConnectionManager