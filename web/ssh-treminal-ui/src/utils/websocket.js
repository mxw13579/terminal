import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ElMessage } from 'element-plus'

class WebSocketClient {
  constructor() {
    this.client = null
    this.connected = false
    this.subscriptions = new Map()
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 1000
    this.maxReconnectDelay = 30000
    this.reconnectTimer = null
  }

  connect() {
    return new Promise((resolve, reject) => {
      try {
        // 创建STOMP客户端
        this.client = new Client({
          // 使用SockJS作为WebSocket实现
          webSocketFactory: () => new SockJS('/ws/stomp'),
          debug: process.env.NODE_ENV === 'development' ? (str) => console.log('STOMP Debug:', str) : null,
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
        })

        // 连接成功回调
        this.client.onConnect = (frame) => {
          console.log('WebSocket连接成功:', frame)
          this.connected = true
          this.reconnectAttempts = 0
          this.reconnectDelay = 1000
          this.clearReconnectTimer()
          this.resubscribeAll()
          resolve(frame)
        }

        // 连接错误回调
        this.client.onStompError = (frame) => {
          console.error('WebSocket连接错误:', frame)
          this.connected = false
          const error = new Error('WebSocket连接失败: ' + (frame.headers['message'] || 'Unknown error'))
          this.handleConnectionError(error)
          reject(error)
        }

        // WebSocket层错误处理
        this.client.onWebSocketError = (error) => {
          console.error('WebSocket transport error:', error)
          this.connected = false
          this.handleConnectionError(error)
        }

        // 连接断开回调
        this.client.onDisconnect = () => {
          console.log('WebSocket连接断开')
          this.connected = false
          // 如果不是主动断开，则尝试重连
          if (this.client && this.client.active) {
            this.handleConnectionError(new Error('Connection lost'))
          }
        }

        // 激活连接
        this.client.activate()

      } catch (error) {
        console.error('创建WebSocket连接失败:', error)
        this.handleConnectionError(error)
        reject(error)
      }
    })
  }

  /**
   * 处理连接错误并尝试重连
   */
  handleConnectionError(error) {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket重连次数已达上限，停止重连')
      ElMessage.error('WebSocket连接失败，请刷新页面重试')
      return
    }

    this.reconnectAttempts++
    console.log(`WebSocket重连中... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

    this.reconnectTimer = setTimeout(() => {
      this.connect().then(() => {
        ElMessage.success('WebSocket连接已恢复')
      }).catch(() => {
        // 指数退避算法，增加重连延迟
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay)
      })
    }, this.reconnectDelay)
  }

  /**
   * 重新订阅所有之前的订阅
   */
  resubscribeAll() {
    const subscriptionData = new Map(this.subscriptions)
    this.subscriptions.clear()
    
    for (const [destination, data] of subscriptionData) {
      try {
        this.subscribe(destination, data.callback)
      } catch (error) {
        console.error(`重新订阅失败: ${destination}`, error)
      }
    }
  }

  /**
   * 清除重连定时器
   */
  clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  disconnect() {
    this.clearReconnectTimer()
    if (this.client) {
      this.client.deactivate()
      this.client = null
      this.connected = false
      this.subscriptions.clear()
      console.log('WebSocket连接已断开')
    }
  }

  subscribe(destination, callback) {
    if (!this.connected || !this.client) {
      // 存储订阅信息，连接恢复后自动重新订阅
      this.subscriptions.set(destination, { callback })
      console.warn('WebSocket未连接，订阅已暂存:', destination)
      return null
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        try {
          const data = JSON.parse(message.body)
          callback(data)
        } catch (error) {
          console.error('解析WebSocket消息失败:', error)
          callback({ error: '消息格式错误', raw: message.body })
        }
      })

      this.subscriptions.set(destination, { callback, subscription })
      console.log('订阅成功:', destination)
      return subscription

    } catch (error) {
      console.error('订阅失败:', destination, error)
      return null
    }
  }

  unsubscribe(destination) {
    const data = this.subscriptions.get(destination)
    if (data && data.subscription) {
      data.subscription.unsubscribe()
    }
    this.subscriptions.delete(destination)
    console.log('取消订阅:', destination)
  }

  send(destination, body = {}) {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法发送消息')
      ElMessage.warning('连接已断开，正在尝试重连...')
      return false
    }

    try {
      this.client.publish({
        destination,
        body: JSON.stringify(body)
      })
      console.log('发送消息成功:', destination)
      return true
    } catch (error) {
      console.error('发送消息失败:', error)
      ElMessage.error('发送消息失败: ' + error.message)
      return false
    }
  }

  isConnected() {
    return this.connected
  }

  /**
   * 获取连接状态信息
   */
  getStatus() {
    return {
      connected: this.connected,
      reconnectAttempts: this.reconnectAttempts,
      subscriptions: Array.from(this.subscriptions.keys())
    }
  }
}

// 创建单例实例
const webSocketClient = new WebSocketClient()

// 便捷连接函数
export const connectWebSocket = (endpoint = '/ws/stomp') => {
  const client = new WebSocketClient()
  
  // 返回一个包装对象，提供简化的API
  return {
    connect: () => client.connect(),
    disconnect: () => client.disconnect(),
    subscribe: (destination, callback) => client.subscribe(destination, callback),
    unsubscribe: (destination) => client.unsubscribe(destination),
    send: (destination, body) => client.send(destination, body),
    isConnected: () => client.isConnected(),
    
    // 设置连接回调
    onConnect: null,
    onError: null,
    onDisconnect: null,
    
    // 自动连接并设置回调
    init() {
      this.connect().then(() => {
        if (this.onConnect) this.onConnect()
      }).catch((error) => {
        if (this.onError) this.onError(error)
      })
    }
  }
}

export default webSocketClient