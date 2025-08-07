// 统一连接管理 composable
import { ref, reactive, computed, readonly } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// 连接状态管理
const connectionState = reactive({
  isConnected: false,
  connectionInfo: null,
  connectionHistory: JSON.parse(localStorage.getItem('ssh-connections') || '[]').map(conn => {
    // 兼容处理：将旧的username字段转换为user字段
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

// STOMP客户端实例
let stompClient = null

// 连接配置
export function useConnectionManager() {

  // 保存连接信息到本地存储
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

    // 只保留最近10个连接
    connectionState.connectionHistory = connectionState.connectionHistory.slice(0, 10)
    localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
  }

  // 连接到服务器
  const connect = async (connectionInfo) => {
    console.log('接收到的连接信息:', connectionInfo); // 调试日志
    
    // 兼容处理：如果传入的是username字段，转换为user字段
    if (connectionInfo.username && !connectionInfo.user) {
      connectionInfo.user = connectionInfo.username;
      delete connectionInfo.username;
    }
    
    // 验证必要的连接参数
    if (!connectionInfo || !connectionInfo.host || !connectionInfo.user || !connectionInfo.password) {
      const error = new Error('缺少必要的连接参数 (host, user, password)');
      connectionState.error = error.message;
      throw error;
    }
    
    connectionState.connecting = true
    connectionState.error = null

    try {
      // 创建STOMP客户端，SSH参数通过连接头传递
      stompClient = new Client({
        webSocketFactory: () => new SockJS(`/ws/terminal`),
        connectHeaders: {
          host: connectionInfo.host,
          port: connectionInfo.port.toString(),
          user: connectionInfo.user,  // 后端期望的是 'user'
          password: connectionInfo.password
        },
        debug: (str) => {
          console.log('[STOMP Debug]', str)
        },
        reconnectDelay: 0, // 禁用自动重连
        heartbeatIncoming: 0, // 禁用心跳检测
        heartbeatOutgoing: 0 // 禁用心跳检测
      })

      // 连接成功处理
      stompClient.onConnect = (frame) => {
        console.log('STOMP连接成功:', frame)
        connectionState.isConnected = true
        connectionState.connectionInfo = connectionInfo
        connectionState.currentSessionId = frame.headers.session || 'default'
        connectionState.connecting = false

        // 保存成功的连接
        saveConnection(connectionInfo)
      }

      // 连接错误处理
      stompClient.onStompError = (frame) => {
        console.error('STOMP连接错误:', frame)
        let errorMessage = frame.headers.message || '连接失败'

        // 根据不同的错误类型提供更详细的错误信息
        if (errorMessage.includes('Connection refused')) {
          errorMessage = `无法连接到SSH服务器，请检查：
1. 服务器地址是否正确
2. SSH端口是否正确（通常是22）
3. SSH服务是否已启动
4. 防火墙是否允许SSH连接`
        } else if (errorMessage.includes('Authentication')) {
          errorMessage = `SSH认证失败，请检查：
1. 用户名是否正确
2. 密码是否正确
3. 用户是否有SSH登录权限`
        } else if (errorMessage.includes('timeout')) {
          errorMessage = `连接超时，请检查：
1. 网络连接是否正常
2. 服务器是否可达
3. 防火墙设置`
        }

        connectionState.error = errorMessage
        connectionState.connecting = false
        connectionState.isConnected = false
      }

      // 连接断开处理
      stompClient.onDisconnect = () => {
        console.log('STOMP连接断开')
        connectionState.isConnected = false
        connectionState.connectionInfo = null
        connectionState.currentSessionId = null
      }

      // WebSocket错误处理
      stompClient.onWebSocketError = (error) => {
        console.error('WebSocket错误:', error)
        connectionState.error = 'WebSocket连接错误'
        connectionState.connecting = false
        connectionState.isConnected = false
      }

      // 激活连接
      stompClient.activate()

      // 返回Promise，等待连接完成
      return new Promise((resolve, reject) => {
        const originalOnConnect = stompClient.onConnect
        const originalOnStompError = stompClient.onStompError

        stompClient.onConnect = (frame) => {
          originalOnConnect(frame)
          resolve({ sessionId: frame.headers.session || 'default' })
        }

        stompClient.onStompError = (frame) => {
          originalOnStompError(frame)
          reject(new Error(frame.headers.message || '连接失败'))
        }

        // 设置超时
        setTimeout(() => {
          if (connectionState.connecting) {
            connectionState.connecting = false
            connectionState.error = '连接超时'
            reject(new Error('连接超时'))
          }
        }, 10000)
      })

    } catch (error) {
      connectionState.error = error.message
      connectionState.connecting = false
      throw error
    }
  }

  // 断开连接
  const disconnect = async () => {
    if (stompClient && stompClient.connected) {
      try {
        stompClient.deactivate()
        console.log('STOMP连接已断开')
      } catch (error) {
        console.warn('断开连接时出错:', error)
      }
    }

    connectionState.isConnected = false
    connectionState.connectionInfo = null
    connectionState.currentSessionId = null
    connectionState.error = null
    stompClient = null
  }

  // 检查连接状态
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

  // 删除历史连接
  const removeConnection = (connectionId) => {
    connectionState.connectionHistory = connectionState.connectionHistory.filter(
      conn => conn.id !== connectionId
    )
    localStorage.setItem('ssh-connections', JSON.stringify(connectionState.connectionHistory))
  }

  // 计算属性
  const connectionStatus = computed(() => {
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

  return {
    // 状态
    connectionState: readonly(connectionState),
    connectionStatus,
    connectionDisplay,

    // 方法
    connect,
    disconnect,
    checkConnection,
    saveConnection,
    removeConnection,

    // STOMP客户端访问
    getStompClient: () => stompClient
  }
}

// 单例模式，确保全局状态一致
export default useConnectionManager
