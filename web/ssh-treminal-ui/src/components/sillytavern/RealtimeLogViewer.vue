<template>
  <div class="realtime-log-viewer" :class="{ 'fullscreen': isFullscreen }">
    <div class="card">
      <div class="card-header">
        <div class="header-content">
          <div class="title-section">
            <!-- 简化标题，只保留全屏按钮 -->
          </div>
          <div class="header-actions">
            <button 
              @click="toggleFullscreen" 
              class="btn btn-sm btn-outline-light"
              :title="isFullscreen ? '退出全屏' : '全屏显示'"
            >
              <i :class="isFullscreen ? 'fas fa-compress' : 'fas fa-expand'"></i>
            </button>
          </div>
        </div>
      </div>
      
      <div class="card-body">
        <!-- 直接显示日志区域，移除所有控制面板 -->
        <div class="log-display-area">
          <div v-if="logs.length === 0" class="log-empty">
            <div class="empty-icon">
              <i class="fas fa-file-alt"></i>
            </div>
            <h6>暂无日志数据</h6>
            <p class="text-muted">开启实时日志查看器以接收日志推送</p>
            <!-- 调试信息 -->
            <div style="margin-top: 10px; font-size: 12px; color: #999;">
              调试: filteredLogs.length={{ logs.length }}, rawLogs.length={{ rawLogs.value?.length || 0 }}
            </div>
          </div>
          
          <div v-else class="log-container-wrapper">
            <div class="log-container" ref="logContainer">
              
              <div 
                v-for="(log, index) in logs" 
                :key="index"
                class="log-line"
              >
                {{ log }}
              </div>
            </div>
            
            <!-- 自动滚动开关 -->
            <div class="scroll-controls">
              <div class="form-check form-switch">
                <input 
                  class="form-check-input" 
                  type="checkbox" 
                  id="autoScroll" 
                  v-model="autoScroll"
                >
                <label class="form-check-label" for="autoScroll">
                  自动滚动到底部
                </label>
              </div>
              
              <div class="scroll-buttons">
                <button @click="scrollToTop" class="btn btn-sm btn-outline-secondary me-1">
                  <i class="fas fa-angle-double-up"></i>
                </button>
                <button @click="scrollToBottom" class="btn btn-sm btn-outline-secondary">
                  <i class="fas fa-angle-double-down"></i>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 日志操作按钮 - 已移除下载、复制和搜索功能 -->

        <!-- 错误消息 -->
        <div v-if="errorMessage" class="alert alert-danger mt-3" role="alert">
          <i class="fas fa-exclamation-circle me-2"></i>
          {{ errorMessage }}
          <button @click="errorMessage = ''" type="button" class="btn-close ms-auto" aria-label="Close"></button>
        </div>

        <!-- 成功消息 -->
        <div v-if="successMessage" class="alert alert-success mt-3" role="alert">
          <i class="fas fa-check-circle me-2"></i>
          {{ successMessage }}
          <button @click="successMessage = ''" type="button" class="btn-close ms-auto" aria-label="Close"></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'
import { useConnectionManager } from '@/composables/useConnectionManager'

export default {
  name: 'RealtimeLogViewer',
  props: {
    containerName: {
      type: String,
      default: 'sillytavern'
    }
  },
  setup(props) {
    const sillyTavern = useSillyTavern()
    const { connectionState, getStompClient } = useConnectionManager()
    
    // 直接从单例状态获取logs和实时日志状态
    const sillyTavernLogs = sillyTavern.logs
    const isRealtimeActive = sillyTavern.isRealtimeLogsActive // 使用全局状态
    
    console.log('RealtimeLogViewer获取sillyTavern.logs引用:', {
      isRef: !!sillyTavernLogs,
      isReadonly: sillyTavernLogs?._v_isReadonly,
      initialLength: sillyTavernLogs.value?.length || 0,
      logsType: typeof sillyTavernLogs,
      logsValue: sillyTavernLogs.value,
      sillyTavernRef: !!sillyTavern,
      sillyTavernType: typeof sillyTavern,
      sillyTavernKeys: Object.keys(sillyTavern).slice(0, 10)
    })
    
    // 添加调试监听 - 监听readonly引用
    watch(sillyTavernLogs, (newLogs) => {
      console.log('🔍 RealtimeLogViewer检测到sillyTavern.logs变化:', {
        newLength: newLogs?.length || 0,
        newLogs: newLogs?.slice(-3) || [],
        timestamp: new Date().toLocaleTimeString(),
        isArray: Array.isArray(newLogs)
      })
    }, { deep: true, immediate: true })
    
    // 同时监听整个sillyTavern对象的变化
    watch(() => sillyTavern.logs.value, (newLogs) => {
      console.log('🎯 直接监听sillyTavern.logs.value变化:', {
        newLength: newLogs?.length || 0,
        sample: newLogs?.slice(-2) || []
      })
    }, { immediate: true })
    
    // 从统一连接管理器获取STOMP连接状态
    const isConnected = computed(() => connectionState.isConnected)
    const stompClient = computed(() => {
      const client = getStompClient()
      console.log('RealtimeLogViewer STOMP客户端调试:', {
        client: !!client,
        clientType: client ? client.constructor.name : 'null',
        connected: client?.connected,
        hasSendMethod: client && typeof client.send === 'function',
        hasPublishMethod: client && typeof client.publish === 'function',
        clientKeys: client ? Object.keys(client).slice(0, 10) : []
      })
      return client
    })
    
    // 响应式状态（移除了 isRealtimeActive - 现在使用全局状态）
    const totalLines = ref(0)
    const autoScroll = ref(true)
    const lastUpdateTime = ref('')
    const errorMessage = ref('')
    const successMessage = ref('')
    const memoryInfo = ref(null)
    const isFullscreen = ref(false) // 全屏状态
    
    // 日志容器引用
    const logContainer = ref(null)
    
    // 日志配置
    const logConfig = reactive({
      maxLines: 50
    })
    
    // 计算属性
    const realtimeStatus = computed(() => {
      if (!isConnected.value) return 'disconnected'
      if (isRealtimeActive.value) return 'active'
      return 'inactive'
    })
    
    // 对日志按时间戳排序，但保持原始格式
    const displayLogs = computed(() => {
      console.log('计算displayLogs，当前sillyTavernLogs长度:', sillyTavernLogs.value?.length || 0)
      if (!sillyTavernLogs.value || sillyTavernLogs.value.length === 0) {
        console.log('sillyTavernLogs为空，返回空数组')
        return []
      }
      
      // 清理ANSI转义字符和其他转义序列，但保持原始格式
      const cleanedLogs = sillyTavernLogs.value.map(log => {
        if (typeof log !== 'string') return log
        
        return log
          // 移除ANSI转义序列 (如 \x1B[1m\x1B[32m)
          .replace(/\x1B\[[0-9;]*[mGK]/g, '')
          // 移除其他控制字符
          .replace(/\x1B\]2;[^\x1B]*\x1B\\/g, '')
          // 只移除行尾的空白，保持行首缩进和空行
          .replace(/\s+$/, '')
      })
      
      // 按时间戳排序日志（如果有时间戳的话）
      const sortedLogs = cleanedLogs.slice().sort((a, b) => {
        // 提取时间戳正则：2025-08-13T14:18:38.501597355Z
        const timestampRegex = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z)/
        const timestampA = a.match(timestampRegex)?.[1]
        const timestampB = b.match(timestampRegex)?.[1]
        
        // 如果两个都有时间戳，按时间排序
        if (timestampA && timestampB) {
          return new Date(timestampA).getTime() - new Date(timestampB).getTime()
        }
        
        // 如果只有一个有时间戳，有时间戳的排在前面
        if (timestampA && !timestampB) return -1
        if (!timestampA && timestampB) return 1
        
        // 如果都没有时间戳，保持原顺序
        return 0
      })
      
      console.log('返回排序后的日志:', sortedLogs.length)
      return sortedLogs
    })
    
    // 方法
    // 实用函数
    
    const getStatusText = () => {
      switch (realtimeStatus.value) {
        case 'active': return '实时日志已启动'
        case 'inactive': return '实时日志未启动'
        case 'disconnected': return 'WebSocket未连接'
        default: return '状态未知'
      }
    }
    
    const getMemoryProgressClass = () => {
      if (!memoryInfo.value) return 'bg-primary'
      const percent = memoryInfo.value.memoryUsagePercent
      if (percent >= 90) return 'bg-danger'
      if (percent >= 70) return 'bg-warning'
      return 'bg-success'
    }
    
    const getLogTimestamp = (log) => {
      // 尝试提取时间戳（Docker日志格式：2024-01-01T12:00:00.000000000Z）
      const timestampMatch = log.match(/^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}/)
      if (timestampMatch) {
        return timestampMatch[0]
      }
      return ''
    }
    
    const getLogLevel = (log) => {
      const upperLog = log.toUpperCase()
      if (upperLog.includes('[ERROR]') || upperLog.includes('ERROR')) return 'ERROR'
      if (upperLog.includes('[WARN]') || upperLog.includes('WARN')) return 'WARN'
      if (upperLog.includes('[INFO]') || upperLog.includes('INFO')) return 'INFO'
      if (upperLog.includes('[DEBUG]') || upperLog.includes('DEBUG')) return 'DEBUG'
      return ''
    }
    
    const getLogContent = (log) => {
      // 移除时间戳和日志级别，返回纯内容
      let content = log
      
      // 移除时间戳
      content = content.replace(/^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*\\s*/, '')
      
      // 移除日志级别标记
      content = content.replace(/^\\[(ERROR|WARN|INFO|DEBUG)\\]\\s*/, '')
      
      return content || log
    }
    
    const getLogLineClass = (log) => {
      const level = getLogLevel(log)
      switch (level) {
        case 'ERROR': return 'log-error'
        case 'WARN': return 'log-warning'
        case 'INFO': return 'log-info'
        case 'DEBUG': return 'log-debug'
        default: return ''
      }
    }
    
    const onConfigChange = () => {
      if (isRealtimeActive.value) {
        // 重启实时日志以应用新配置
        stopRealtimeLogs()
        setTimeout(() => {
          startRealtimeLogs()
        }, 500)
      }
    }
    
    const startRealtimeLogs = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket连接未建立'
        return
      }
      
      errorMessage.value = ''
      successMessage.value = ''
      
      // 全局清空已在useSillyTavern中处理，这里无需重复清空
      
      // 使用全局的启动方法
      const success = sillyTavern.startRealtimeLogs(props.containerName, logConfig.maxLines)
      if (!success) {
        errorMessage.value = '启动实时日志失败'
      }
    }
    
    const stopRealtimeLogs = () => {
      // 使用全局的停止方法
      sillyTavern.stopRealtimeLogs()
    }
    
    const clearLogs = () => {
      // 通过useSillyTavern清空日志
      if (sillyTavern && sillyTavern.logs) {
        sillyTavern.logs.value = []
        console.log('日志已清空')
      }
      totalLines.value = 0
      memoryInfo.value = null
    }
    
    const scrollToTop = async () => {
      await nextTick()
      if (logContainer.value) {
        logContainer.value.scrollTop = 0
      }
    }
    
    const scrollToBottom = async () => {
      await nextTick()
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight
      }
    }
    
    const toggleFullscreen = () => {
      isFullscreen.value = !isFullscreen.value
      // 全屏切换后自动滚动到底部
      nextTick(() => {
        if (autoScroll.value) {
          scrollToBottom()
        }
      })
    }
    
    // 监听ESC键退出全屏
    const handleKeydown = (event) => {
      if (event.key === 'Escape' && isFullscreen.value) {
        isFullscreen.value = false
      }
    }
    
    // 监听自动滚动变化 - 使用sillyTavernLogs
    watch(sillyTavernLogs, (newLogs) => {
      console.log('🎯 watch触发，自动滚动条件:', {
        autoScroll: autoScroll.value,
        logsLength: newLogs?.length || 0,
        shouldScroll: autoScroll.value && newLogs && newLogs.length > 0
      })
      if (autoScroll.value && newLogs && newLogs.length > 0) {
        nextTick(() => scrollToBottom())
      }
    }, { deep: true })
    
    // 监听displayLogs变化以调试
    watch(displayLogs, (newDisplayLogs) => {
      console.log('🔍 displayLogs变化:', {
        length: newDisplayLogs?.length || 0,
        isArray: Array.isArray(newDisplayLogs),
        sample: newDisplayLogs?.slice(-2) || []
      })
    })
    
    // 生命周期 - 简化版本，不再重复订阅
    onMounted(() => {
      console.log('RealtimeLogViewer mounted, 当前sillyTavernLogs长度:', sillyTavernLogs.value?.length || 0)
      console.log('🔍 挂载时调试信息:', {
        sillyTavernLogsRef: !!sillyTavernLogs,
        sillyTavernLogsType: typeof sillyTavernLogs,
        sillyTavernLogsValue: sillyTavernLogs.value,
        sillyTavernLogsIsArray: Array.isArray(sillyTavernLogs.value),
        sillyTavernObject: !!sillyTavern,
        sillyTavernKeys: sillyTavern ? Object.keys(sillyTavern) : []
      })
      
      // 添加键盘事件监听
      window.addEventListener('keydown', handleKeydown)
      
      // 自动启动实时日志（如果连接已就绪）
      setTimeout(() => {
        if (isConnected.value && !isRealtimeActive.value) {
          console.log('🚀 自动启动实时日志')
          startRealtimeLogs()
        }
      }, 1000) // 延迟1秒确保WebSocket连接稳定
    })
    
    onUnmounted(() => {
      // 移除键盘事件监听
      window.removeEventListener('keydown', handleKeydown)
      
      // 停止实时日志
      if (isRealtimeActive.value) {
        stopRealtimeLogs()
      }
    })
    
    return {
      // 响应式状态
      logs: displayLogs,
      rawLogs: sillyTavernLogs, // 使用正确的原始logs引用
      totalLines,
      isRealtimeActive,
      autoScroll,
      lastUpdateTime,
      errorMessage,
      successMessage,
      memoryInfo,
      logContainer,
      logConfig,
      isFullscreen, // 全屏状态
      
      // 计算属性
      isConnected,
      realtimeStatus,
      
      // 方法
      getStatusText,
      getMemoryProgressClass,
      onConfigChange,
      startRealtimeLogs,
      stopRealtimeLogs,
      clearLogs,
      scrollToTop,
      scrollToBottom,
      toggleFullscreen // 全屏切换方法
    }
  }
}
</script>

<style scoped>
.realtime-log-viewer {
  max-width: 1200px;
  margin: 0 auto;
  transition: all 0.3s ease;
}

/* 全屏样式 */
.realtime-log-viewer.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  max-width: none;
  margin: 0;
  background: white;
  padding: 0;
}

.realtime-log-viewer.fullscreen .card {
  height: 100vh;
  border: none;
  border-radius: 0;
  display: flex;
  flex-direction: column;
}

.realtime-log-viewer.fullscreen .card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.realtime-log-viewer.fullscreen .log-display-area {
  flex: 1;
  min-height: 0;
}

.realtime-log-viewer.fullscreen .log-container-wrapper {
  flex: 1;
  min-height: 0;
}

/* 头部样式 */
.card-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom: none;
  padding: 0.5rem 1rem; /* 减小padding */
}

.card-header .header-content {
  display: flex;
  justify-content: flex-end; /* 右对齐按钮 */
  align-items: center;
  width: 100%;
  min-height: 40px; /* 确保最小高度 */
}

.card-header .title-section {
  display: none; /* 隐藏标题区域 */
}

.card-header .header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.card-header .header-actions .btn {
  border: 1px solid rgba(255,255,255,0.3);
  color: white;
}

.card-header .header-actions .btn:hover {
  background-color: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.5);
}

/* 全屏样式 */
.realtime-log-viewer.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  max-width: none;
  margin: 0;
  background: white;
  padding: 0;
}

.realtime-log-viewer.fullscreen .card {
  height: 100vh;
  border: none;
  border-radius: 0;
  display: flex;
  flex-direction: column;
}

.realtime-log-viewer.fullscreen .card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.realtime-log-viewer.fullscreen .log-display-area {
  flex: 1;
  min-height: 0;
}

.realtime-log-viewer.fullscreen .log-container-wrapper {
  flex: 1;
  min-height: 0;
}

.realtime-log-viewer.fullscreen .log-container {
  max-height: none;
  height: 100%;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
}

.log-controls-panel {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.control-group {
  display: flex;
  align-items: center;
}

.control-label {
  font-weight: 500;
  color: #495057;
  margin: 0;
  font-size: 0.9rem;
}

.realtime-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.status-indicator.active {
  background: #28a745;
  animation: pulse 2s infinite;
}

.status-indicator.inactive {
  background: #6c757d;
}

.status-indicator.disconnected {
  background: #dc3545;
}

.status-text {
  font-size: 0.9rem;
  color: #495057;
  font-weight: 500;
}

.log-stats {
  background: #f8f9fa;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  border-left: 4px solid #007bff;
}

.memory-usage .progress {
  background: #e9ecef;
}

.log-display-area {
  min-height: 400px;
  max-height: 600px;
  display: flex;
  flex-direction: column;
}

.log-loading,
.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: #6c757d;
}

.empty-icon {
  font-size: 3rem;
  color: #dee2e6;
  margin-bottom: 1rem;
}

.log-container-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.log-container {
  flex: 1;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 1rem;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.85rem;
  line-height: 1.4;
  min-height: 300px;
  max-height: 400px;
}

.log-line {
  display: block;
  margin-bottom: 1px;
  padding: 2px 0;
  border-radius: 2px;
  word-break: break-word;
  text-align: left;
  white-space: pre-wrap;
  color: #e0e0e0;
}

.log-timestamp {
  color: #888;
  font-size: 0.8rem;
  margin-right: 8px;
}

.log-level {
  font-weight: bold;
  font-size: 0.8rem;
  margin-right: 8px;
}

.log-content {
  color: #e0e0e0;
  word-break: break-word;
  white-space: pre-wrap;
  text-align: left;
}

/* 移除不再使用的日志级别样式，因为已简化显示 */

.scroll-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: #f8f9fa;
  border-top: 1px solid #e9ecef;
  border-radius: 0 0 6px 6px;
}

.scroll-buttons {
  display: flex;
  gap: 0.25rem;
}

.log-actions {
  border-top: 1px solid #e9ecef;
  padding-top: 1rem;
}

.log-search .input-group {
  max-width: 250px;
}

/* 自定义滚动条 */
.log-container::-webkit-scrollbar {
  width: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #2d2d2d;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb {
  background: #555;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb:hover {
  background: #777;
}

/* 动画效果 */
@keyframes pulse {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
  100% {
    opacity: 1;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .log-controls-panel .row {
    flex-direction: column;
    gap: 1rem;
  }
  
  .log-stats .row {
    flex-direction: column;
    gap: 0.5rem;
    text-align: center;
  }
  
  .log-actions .row {
    flex-direction: column;
    gap: 1rem;
  }
  
  .log-search {
    text-align: center;
  }
  
  .log-search .input-group {
    max-width: 100%;
  }
  
  .log-timestamp {
    display: none;
  }
  
  .log-level {
    width: 45px;
  }
}
</style>