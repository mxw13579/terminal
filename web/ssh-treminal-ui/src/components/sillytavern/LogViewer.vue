<template>
  <div class="log-viewer">
    <div class="log-header">
      <h4 class="log-title">容器日志</h4>
      <div class="log-controls">
        <select v-model="logConfig.days" class="log-select">
          <option value="1">最近 1 天</option>
          <option value="3">最近 3 天</option>
          <option value="7">最近 7 天</option>
        </select>
        <select v-model="logConfig.tailLines" class="log-select">
          <option value="50">50 行</option>
          <option value="100">100 行</option>
          <option value="500">500 行</option>
          <option value="1000">1000 行</option>
        </select>
        <button 
          @click="refreshLogs"
          :disabled="isLoading"
          class="btn btn-secondary btn-sm"
        >
          <span v-if="isLoading">🔄</span>
          <span v-else>🔄</span>
          刷新
        </button>
      </div>
    </div>
    
    <div class="log-content">
      <div v-if="isLoading" class="log-loading">
        <div class="loading-spinner">🔄</div>
        <p>正在加载日志...</p>
      </div>
      
      <div v-else-if="logs.length === 0" class="log-empty">
        <div class="empty-icon">📄</div>
        <p>暂无日志数据</p>
        <button @click="refreshLogs" class="btn btn-secondary btn-sm">
          重新加载
        </button>
      </div>
      
      <div v-else class="log-display">
        <div class="log-meta">
          <span class="log-count">共 {{ logs.length }} 行日志</span>
          <span class="log-time">{{ new Date().toLocaleString('zh-CN') }}</span>
        </div>
        
        <div class="log-container" ref="logContainer">
          <div 
            v-for="(log, index) in logs" 
            :key="index"
            class="log-line"
            :class="getLogLineClass(log)"
          >
            <span class="log-index">{{ index + 1 }}</span>
            <span class="log-text">{{ log }}</span>
          </div>
        </div>
        
        <div class="log-actions">
          <button @click="scrollToTop" class="btn btn-secondary btn-sm">
            ⬆️ 顶部
          </button>
          <button @click="scrollToBottom" class="btn btn-secondary btn-sm">
            ⬇️ 底部
          </button>
          <button @click="downloadLogs" class="btn btn-secondary btn-sm">
            💾 下载
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, defineProps, defineEmits } from 'vue'

const props = defineProps({
  logs: {
    type: Array,
    default: () => []
  },
  isLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['refresh-logs'])

const logContainer = ref(null)

const logConfig = reactive({
  days: 1,
  tailLines: 100
})

const refreshLogs = () => {
  emit('refresh-logs', { ...logConfig })
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
  const logText = props.logs.join('\n')
  const blob = new Blob([logText], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  
  const link = document.createElement('a')
  link.href = url
  link.download = `sillytavern-logs-${new Date().toISOString().split('T')[0]}.txt`
  link.click()
  
  URL.revokeObjectURL(url)
}

const getLogLineClass = (log) => {
  const lowerLog = log.toLowerCase()
  if (lowerLog.includes('error') || lowerLog.includes('err')) {
    return 'log-error'
  }
  if (lowerLog.includes('warn') || lowerLog.includes('warning')) {
    return 'log-warning'
  }
  if (lowerLog.includes('info') || lowerLog.includes('debug')) {
    return 'log-info'
  }
  return ''
}
</script>

<style scoped>
.log-viewer {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  height: 600px;
  display: flex;
  flex-direction: column;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.log-title {
  margin: 0;
  font-size: 1.1rem;
  color: #2d3748;
  font-weight: 600;
}

.log-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-select {
  padding: 6px 10px;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  font-size: 0.8rem;
  background: white;
}

.log-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.log-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #718096;
}

.loading-spinner {
  font-size: 2rem;
  margin-bottom: 10px;
  animation: spin 1s linear infinite;
}

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #718096;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 10px;
  opacity: 0.5;
}

.log-display {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.log-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 0.8rem;
  color: #718096;
  flex-shrink: 0;
}

.log-container {
  flex: 1;
  background: #1a202c;
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  line-height: 1.4;
  min-height: 0;
}

.log-line {
  display: flex;
  margin-bottom: 2px;
  word-break: break-word;
}

.log-index {
  color: #718096;
  width: 40px;
  flex-shrink: 0;
  text-align: right;
  margin-right: 12px;
  border-right: 1px solid #2d3748;
  padding-right: 8px;
}

.log-text {
  color: #e2e8f0;
  flex: 1;
  word-break: break-word;
}

.log-error .log-text {
  color: #feb2b2;
  background: rgba(254, 178, 178, 0.1);
}

.log-warning .log-text {
  color: #faf089;
  background: rgba(250, 240, 137, 0.1);
}

.log-info .log-text {
  color: #90cdf4;
  background: rgba(144, 205, 244, 0.1);
}

.log-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 16px;
  flex-shrink: 0;
}

/* Common Button Styles */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #e2e8f0;
  color: #4a5568;
}

.btn-secondary:hover:not(:disabled) {
  background: #cbd5e0;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.8rem;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Custom scrollbar */
.log-container::-webkit-scrollbar {
  width: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #2d3748;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb {
  background: #4a5568;
  border-radius: 4px;
}

.log-container::-webkit-scrollbar-thumb:hover {
  background: #718096;
}

/* Responsive design */
@media (max-width: 768px) {
  .log-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .log-controls {
    width: 100%;
    justify-content: space-between;
  }
  
  .log-actions {
    flex-wrap: wrap;
  }
}
</style>