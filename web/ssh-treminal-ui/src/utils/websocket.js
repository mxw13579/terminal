import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

class WebSocketClient {
  constructor() {
    this.client = null
    this.connected = false
    this.subscriptions = new Map()
  }

  connect() {
    return new Promise((resolve, reject) => {
      try {
        // 创建STOMP客户端
        this.client = new Client({
          // 使用SockJS作为WebSocket实现
          webSocketFactory: () => new SockJS('http://localhost:8080/ws/stomp'),
          debug: (str) => {
            console.log('STOMP Debug:', str)
          },
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
        })

        // 连接成功回调
        this.client.onConnect = (frame) => {
          console.log('WebSocket连接成功:', frame)
          this.connected = true
          resolve(frame)
        }

        // 连接错误回调
        this.client.onStompError = (frame) => {
          console.error('WebSocket连接错误:', frame)
          this.connected = false
          reject(new Error('WebSocket连接失败: ' + frame.headers['message']))
        }

        // 连接断开回调
        this.client.onDisconnect = () => {
          console.log('WebSocket连接断开')
          this.connected = false
        }

        // 激活连接
        this.client.activate()

      } catch (error) {
        console.error('创建WebSocket连接失败:', error)
        reject(error)
      }
    })
  }

  disconnect() {
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
      console.warn('WebSocket未连接，无法订阅:', destination)
      return null
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        try {
          const data = JSON.parse(message.body)
          callback(data)
        } catch (error) {
          console.error('解析WebSocket消息失败:', error)
          callback({ error: '消息格式错误' })
        }
      })

      this.subscriptions.set(destination, subscription)
      console.log('订阅成功:', destination)
      return subscription

    } catch (error) {
      console.error('订阅失败:', destination, error)
      return null
    }
  }

  unsubscribe(destination) {
    const subscription = this.subscriptions.get(destination)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      console.log('取消订阅:', destination)
    }
  }

  send(destination, body = {}) {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法发送消息')
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
      return false
    }
  }

  isConnected() {
    return this.connected
  }
}

// 创建单例实例
const webSocketClient = new WebSocketClient()

export default webSocketClient