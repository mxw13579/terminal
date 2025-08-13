<template>
  <div class="realtime-log-viewer">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">
          <i class="fas fa-file-alt me-2"></i>
          实时日志查看
        </h5>
        <small class="text-muted">实时查看容器日志，支持历史日志和WebSocket推送</small>
      </div>
      
      <div class="card-body">
        <!-- 日志控制面板 -->
        <div class="log-controls-panel mb-3">
          <div class="row align-items-center">
            <div class="col-md-6">
              <div class="control-group">
                <label class="control-label">实时日志状态:</label>
                <div class="realtime-status ms-2">
                  <span class="status-indicator" :class="realtimeStatus"></span>
                  <span class="status-text">{{ getStatusText() }}</span>
                </div>
              </div>
            </div>
            
            <div class="col-md-6">
              <div class="control-group">
                <label class="control-label">显示行数:</label>
                <select 
                  v-model="logConfig.maxLines" 
                  class="form-select form-select-sm ms-2"
                  style="width: auto; display: inline-block;"
                  @change="onConfigChange"
                >
                  <option value="50">50行</option>
                  <option value="100">100行</option>
                  <option value="300">300行</option>
                  <option value="500">500行</option>
                </select>
              </div>
            </div>
          </div>
          
          <!-- 实时日志控制 -->
          <div class="row mt-2 align-items-center">
            <div class="col-md-6 text-start">
              <button 
                v-if="!isRealtimeActive"
                @click="startRealtimeLogs"
                :disabled="!isConnected"
                class="btn btn-success btn-sm me-2"
              >
                <i class="fas fa-play me-1"></i>
                开启实时日志
              </button>
              <button 
                v-else
                @click="stopRealtimeLogs"
                class="btn btn-danger btn-sm me-2"
              >
                <i class="fas fa-stop me-1"></i>
                停止实时日志
              </button>
            </div>
            
            <div class="col-md-6 text-end">
              <button 
                @click="clearLogs"
                class="btn btn-outline-danger btn-sm"
                :disabled="logs.length === 0"
              >
                <i class="fas fa-trash me-1"></i>
                清空日志
              </button>
            </div>
          </div>
        </div>

        <!-- 日志统计信息 -->
        <div v-if="logs.length > 0 || memoryInfo" class="log-stats mb-3">
          <div class="row">
            <div class="col-md-6">
              <small class="text-muted">
                <i class="fas fa-list-ol me-1"></i>
                共 {{ logs.length }} 行日志
                <span v-if="totalLines && totalLines !== logs.length">
                  (缓存中共 {{ totalLines }} 行)
                </span>
              </small>
            </div>
            <div class="col-md-6 text-end">
              <small class="text-muted">
                <i class="fas fa-clock me-1"></i>
                最后更新: {{ lastUpdateTime }}
              </small>
            </div>
          </div>
          
          <!-- 内存使用情况 -->
          <div v-if="memoryInfo" class="memory-usage mt-2">
            <div class="row align-items-center">
              <div class="col-md-8">
                <div class="progress" style="height: 6px;">
                  <div 
                    class="progress-bar"
                    :class="getMemoryProgressClass()"
                    :style="{ width: memoryInfo.memoryUsagePercent + '%' }"
                    role="progressbar"
                  ></div>
                </div>
              </div>
              <div class="col-md-4 text-end">
                <small class="text-muted">
                  内存使用: {{ Math.round(memoryInfo.memoryUsagePercent) }}%
                  ({{ memoryInfo.cachedLines }}/{{ memoryInfo.maxLines }})
                </small>
              </div>
            </div>
          </div>
        </div>

        <!-- 日志显示区域 -->
        <div class="log-display-area">
          <div v-if="logs.length === 0" class="log-empty">
            <div class="empty-icon">
              <i class="fas fa-file-alt"></i>
            </div>
            <h6>暂无日志数据</h6>
            <p class="text-muted">开启实时日志查看器以接收日志推送</p>
          </div>
          
          <div v-else class="log-container-wrapper">
            <div class="log-container" ref="logContainer">
              <div 
                v-for="(log, index) in logs" 
                :key="index"
                class="log-line"
                :class="getLogLineClass(log)"
              >
                <span class="log-timestamp">{{ getLogTimestamp(log) }}</span>
                <span class="log-level">{{ getLogLevel(log) }}</span>
                <span class="log-content">{{ getLogContent(log) }}</span>
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

        <!-- 日志操作按钮 -->
        <div v-if="logs.length > 0" class="log-actions mt-3">
          <div class="row">
            <div class="col-md-6">
              <button @click="downloadLogs" class="btn btn-outline-primary btn-sm">
                <i class="fas fa-download me-1"></i>
                下载日志
              </button>
              
              <button @click="copyLogs" class="btn btn-outline-secondary btn-sm ms-2">
                <i class="fas fa-copy me-1"></i>
                复制到剪贴板
              </button>
            </div>
            
            <div class="col-md-6 text-end">
              <div class="log-search">
                <div class="input-group">
                  <span class="input-group-text">
                    <i class="fas fa-search"></i>
                  </span>
                  <input
                    type="text"
                    class="form-control form-control-sm"
                    placeholder="搜索日志..."
                    v-model="searchTerm"
                    @input="onSearchChange"
                  />
                  <button 
                    v-if="searchTerm" 
                    class="btn btn-outline-secondary btn-sm" 
                    type="button"
                    @click="clearSearch"
                  >
                    <i class="fas fa-times"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

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
    
    // 从useSillyTavern获取logs数据
    const logs = sillyTavern.logs
    console.log('RealtimeLogViewer使用的logs数据源:', logs.value?.length || 0)
    
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
    
    // 响应式状态（移除了独立的logs）
    const totalLines = ref(0)
    const isRealtimeActive = ref(false)
    const autoScroll = ref(true)
    const searchTerm = ref('')
    const lastUpdateTime = ref('')
    const errorMessage = ref('')
    const successMessage = ref('')
    const memoryInfo = ref(null)
    
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
    
    const filteredLogs = computed(() => {
      if (!searchTerm.value) return logs.value
      const term = searchTerm.value.toLowerCase()
      return logs.value.filter(log => log.toLowerCase().includes(term))
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
      
      const client = stompClient.value
      if (!client || !client.connected || (typeof client.send !== 'function' && typeof client.publish !== 'function')) {
        errorMessage.value = 'STOMP客户端未就绪或不可用'
        console.error('STOMP客户端状态:', {
          client: !!client,
          connected: client?.connected,
          hasSendMethod: client && typeof client.send === 'function',
          hasPublishMethod: client && typeof client.publish === 'function'
        })
        return
      }
      
      errorMessage.value = ''
      successMessage.value = ''
      
      const request = {
        containerName: props.containerName,
        maxLines: logConfig.maxLines
      }
      
      try {
        // 优先使用publish方法（现代STOMP客户端）
        if (typeof client.publish === 'function') {
          client.publish({
            destination: '/app/sillytavern/start-realtime-logs',
            body: JSON.stringify(request)
          })
        } else {
          // 回退到send方法（传统客户端）
          client.send('/app/sillytavern/start-realtime-logs', {}, JSON.stringify(request))
        }
      } catch (error) {
        errorMessage.value = '启动实时日志失败: ' + error.message
      }
    }
    
    const stopRealtimeLogs = () => {
      if (!isConnected.value) return
      
      const client = stompClient.value
      if (!client || !client.connected || (typeof client.send !== 'function' && typeof client.publish !== 'function')) {
        console.warn('STOMP客户端未就绪，无法停止实时日志')
        isRealtimeActive.value = false
        return
      }
      
      try {
        // 优先使用publish方法（现代STOMP客户端）
        if (typeof client.publish === 'function') {
          client.publish({
            destination: '/app/sillytavern/stop-realtime-logs',
            body: JSON.stringify({})
          })
        } else {
          // 回退到send方法（传统客户端）
          client.send('/app/sillytavern/stop-realtime-logs', {}, JSON.stringify({}))
        }
      } catch (error) {
        console.error('停止实时日志失败:', error)
        isRealtimeActive.value = false
      }
    }
    
    const clearLogs = () => {
      // 不能直接清空useSillyTavern中的logs，因为那是readonly的
      // 这里暂时不提供清空功能，或者可以通过sillyTavern的方法来清空
      console.log('清空日志功能暂时不可用（logs由useSillyTavern管理）')
      totalLines.value = 0
      memoryInfo.value = null
      searchTerm.value = ''
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
    
    const downloadLogs = () => {
      const logText = filteredLogs.value.join('\\n')
      const blob = new Blob([logText], { type: 'text/plain;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      
      const link = document.createElement('a')
      link.href = url
      link.download = `${props.containerName}-logs-${new Date().toISOString().split('T')[0]}.txt`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      URL.revokeObjectURL(url)
      successMessage.value = '日志文件已下载'
    }
    
    const copyLogs = async () => {
      try {
        const logText = filteredLogs.value.join('\\n')
        await navigator.clipboard.writeText(logText)
        successMessage.value = '日志已复制到剪贴板'
      } catch (error) {
        errorMessage.value = '复制失败: ' + error.message
      }
    }
    
    const onSearchChange = () => {
      // 搜索逻辑在计算属性中处理
    }
    
    const clearSearch = () => {
      searchTerm.value = ''
    }
    
    // 生命周期 - 简化版本，不再重复订阅
    onMounted(() => {
      console.log('RealtimeLogViewer mounted, 当前logs长度:', logs.value.length)
      
      // 自动启动实时日志（如果连接已就绪）
      setTimeout(() => {
        if (isConnected.value && !isRealtimeActive.value) {
          startRealtimeLogs()
        }
      }, 1000) // 延迟1秒确保WebSocket连接稳定
    })
    
    onUnmounted(() => {
      // 停止实时日志
      if (isRealtimeActive.value) {
        stopRealtimeLogs()
      }
    })
    
    // 监听自动滚动变化
    watch(logs, () => {
      if (autoScroll.value && logs.value.length > 0) {
        nextTick(() => scrollToBottom())
      }
    }, { deep: true })
    
    return {
      // 响应式状态
      logs: filteredLogs,
      totalLines,
      isRealtimeActive,
      autoScroll,
      searchTerm,
      lastUpdateTime,
      errorMessage,
      successMessage,
      memoryInfo,
      logContainer,
      logConfig,
      
      // 计算属性
      isConnected,
      realtimeStatus,
      
      // 方法
      getStatusText,
      getMemoryProgressClass,
      getLogTimestamp,
      getLogLevel,
      getLogContent,
      getLogLineClass,
      onConfigChange,
      startRealtimeLogs,
      stopRealtimeLogs,
      clearLogs,
      scrollToTop,
      scrollToBottom,
      downloadLogs,
      copyLogs,
      onSearchChange,
      clearSearch
    }
  }
}
</script>

<style scoped>
.realtime-log-viewer {
  max-width: 1200px;
  margin: 0 auto;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.card-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom: none;
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
  display: flex;
  margin-bottom: 1px;
  padding: 2px 0;
  border-radius: 2px;
  word-break: break-word;
}

.log-timestamp {
  color: #888;
  width: 160px;
  flex-shrink: 0;
  margin-right: 8px;
  font-size: 0.8rem;
}

.log-level {
  width: 60px;
  flex-shrink: 0;
  margin-right: 8px;
  font-weight: bold;
  font-size: 0.8rem;
}

.log-content {
  color: #e0e0e0;
  flex: 1;
  word-break: break-word;
}

.log-error {
  background: rgba(220, 53, 69, 0.1);
}

.log-error .log-level {
  color: #ff6b6b;
}

.log-error .log-content {
  color: #ffcccb;
}

.log-warning {
  background: rgba(255, 193, 7, 0.1);
}

.log-warning .log-level {
  color: #ffc107;
}

.log-warning .log-content {
  color: #fff3cd;
}

.log-info {
  background: rgba(13, 202, 240, 0.1);
}

.log-info .log-level {
  color: #0dcaf0;
}

.log-info .log-content {
  color: #cff4fc;
}

.log-debug {
  background: rgba(108, 117, 125, 0.1);
}

.log-debug .log-level {
  color: #6c757d;
}

.log-debug .log-content {
  color: #d3d3d4;
}

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