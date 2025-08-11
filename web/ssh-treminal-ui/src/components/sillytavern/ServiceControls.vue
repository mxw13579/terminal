<template>
  <div class="service-controls">
    <div class="controls-header">
      <h4 class="controls-title">服务控制</h4>
      <div class="header-status">
        <div v-if="isPerformingAction" class="action-status">
          <span class="action-spinner">🔄</span>
          {{ currentAction }}...
        </div>
        <div v-else-if="versionSwitching" class="action-status version-switching">
          <span class="action-spinner">🔄</span>
          版本切换中...
          <span v-if="switchTotalTime > 0" class="switch-timer">{{ switchTotalTime }}秒</span>
        </div>
      </div>
    </div>

    <!-- Version Switch Progress -->
    <div v-if="versionSwitching || switchProgress" class="switch-progress">
      <div class="progress-bar">
        <div class="progress-fill" :class="{ 'animated': versionSwitching }"></div>
      </div>
      <div class="progress-text">{{ switchProgress || '正在处理...' }}</div>
    </div>
    
    <div class="controls-grid">
      <!-- Start Button -->
      <button 
        @click="handleServiceAction('start')"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists || containerStatus.running"
        class="control-button start-button"
      >
        <span class="button-icon">▶️</span>
        <div class="button-content">
          <div class="button-title">启动</div>
          <div class="button-subtitle">启动容器</div>
        </div>
      </button>
      
      <!-- Stop Button -->
      <button 
        @click="handleServiceAction('stop')"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists || !containerStatus.running"
        class="control-button stop-button"
      >
        <span class="button-icon">⏹️</span>
        <div class="button-content">
          <div class="button-title">停止</div>
          <div class="button-subtitle">停止容器</div>
        </div>
      </button>
      
      <!-- Restart Button -->
      <button 
        @click="handleServiceAction('restart')"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists"
        class="control-button restart-button"
      >
        <span class="button-icon">🔄</span>
        <div class="button-content">
          <div class="button-title">重启</div>
          <div class="button-subtitle">重启容器</div>
        </div>
      </button>
      
      <!-- Version Switch Button -->
      <button 
        @click="openModal"
        :disabled="isPerformingAction || versionSwitching || !containerStatus || !containerStatus.exists"
        class="control-button upgrade-button"
      >
        <span class="button-icon">🔄</span>
        <div class="button-content">
          <div class="button-title">版本切换</div>
          <div class="button-subtitle">选择版本进行切换</div>
        </div>
      </button>
    </div>
    
    <!-- Container Info -->
    <div v-if="containerStatus && containerStatus.exists" class="container-info">
      <div class="info-row">
        <span class="info-label">状态:</span>
        <span class="info-value" :class="containerStatus.running ? 'status-running' : 'status-stopped'">
          {{ containerStatus.running ? '运行中' : '已停止' }}
        </span>
      </div>
      
      <div v-if="containerStatus.port" class="info-row">
        <span class="info-label">访问地址:</span>
        <span class="info-value info-link">
          <a :href="`http://${containerStatus.hostAddress || connectionState.connectionInfo?.host || 'localhost'}:${containerStatus.port}`" target="_blank">
            http://{{ containerStatus.hostAddress || connectionState.connectionInfo?.host || 'localhost' }}:{{ containerStatus.port }}
          </a>
        </span>
      </div>
      
      <div v-if="containerStatus.image" class="info-row">
        <span class="info-label">镜像:</span>
        <span class="info-value">{{ containerStatus.image }}</span>
      </div>
    </div>
    
    <!-- Version Switch Modal -->
    <div v-if="showUpgradeModal" class="upgrade-modal-overlay" @click="showUpgradeModal = false">
      <div class="upgrade-modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">选择切换版本</h3>
          <button @click="showUpgradeModal = false" class="modal-close">&times;</button>
        </div>
        <div class="modal-body">
          <p class="modal-description">选择要切换到的版本：</p>
          
          <!-- Loading state -->
          <div v-if="versionLoading" class="version-loading">
            <div class="loading-spinner"></div>
            <p>获取版本信息中...</p>
          </div>
          
          <!-- Version list -->
          <div v-else-if="availableVersions.length > 0" class="version-options">
            <button 
              v-for="version in availableVersions" 
              :key="version"
              @click="handleUpgrade(version)" 
              class="version-option"
              :class="{ 'current-version': version === containerStatus?.currentVersion }"
            >
              <div class="version-tag" :class="getVersionClass(version, availableVersions.indexOf(version))">
                {{ version }}
              </div>
              <div class="version-info">
                <div class="version-name">
                  {{ getVersionName(version, availableVersions.indexOf(version)) }}
                  <span v-if="version === containerStatus?.currentVersion" class="current-badge">当前版本</span>
                </div>
                <div class="version-desc">{{ getVersionDescription(version, availableVersions.indexOf(version)) }}</div>
              </div>
            </button>
          </div>
          
          <!-- Error state -->
          <div v-else class="version-error">
            <p>{{ !isConnected ? 'WebSocket连接未建立' : '无法获取版本信息，请稍后重试' }}</p>
            <button v-if="isConnected" @click="fetchVersionInfo" class="retry-button">重试</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, ref, onMounted, onUnmounted, watch, computed } from 'vue'
import useConnectionManager from '@/composables/useConnectionManager'

const { connectionState, getStompClient } = useConnectionManager()

// Computed properties for connection state
const stompClient = computed(() => getStompClient())
const isConnected = computed(() => connectionState.isConnected)

const props = defineProps({
  containerStatus: {
    type: Object,
    default: null
  },
  isPerformingAction: {
    type: Boolean,
    default: false
  },
  currentAction: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['service-action'])

const showUpgradeModal = ref(false)
const versionLoading = ref(false)
const availableVersions = ref([])
const versionSwitching = ref(false)
const switchProgress = ref('')
const switchStartTime = ref(null)
const switchTotalTime = ref(0)
let versionSubscription = null
let progressSubscription = null
let actionResultSubscription = null
let timerInterval = null // 添加定时器引用

const handleServiceAction = (action, options = {}) => {
  emit('service-action', action, options)
}

const handleUpgrade = (version) => {
  showUpgradeModal.value = false
  versionSwitching.value = true
  switchProgress.value = '开始版本切换...'
  switchStartTime.value = Date.now()
  switchTotalTime.value = 0
  
  // 启动定时器来更新显示的时间
  if (timerInterval) {
    clearInterval(timerInterval)
  }
  timerInterval = setInterval(() => {
    if (switchStartTime.value && versionSwitching.value) {
      switchTotalTime.value = Math.round((Date.now() - switchStartTime.value) / 1000)
    } else {
      clearInterval(timerInterval)
      timerInterval = null
    }
  }, 1000)
  
  console.log('开始版本切换:', {
    version,
    startTime: switchStartTime.value,
    versionSwitching: versionSwitching.value
  })
  
  handleServiceAction('switch-version', { targetVersion: version })
}

// 获取版本信息
const fetchVersionInfo = async () => {
  // 使用computed属性
  const client = stompClient.value
  const connected = isConnected.value
  
  if (!connected) {
    console.warn('WebSocket未连接，无法获取版本信息')
    versionLoading.value = false
    return
  }
  
  if (!client) {
    console.warn('STOMP客户端未初始化，无法获取版本信息')
    versionLoading.value = false
    return
  }
  
  versionLoading.value = true
  try {
    client.publish({
      destination: '/app/sillytavern/get-version-info',
      body: JSON.stringify({})
    })
  } catch (error) {
    console.error('发送版本信息请求失败:', error)
    versionLoading.value = false
  }
}

// 处理版本信息响应
const handleVersionInfoResponse = (message) => {
  try {
    const response = JSON.parse(message.body)
    versionLoading.value = false
    
    if (response.success && response.payload && response.payload.availableVersions) {
      availableVersions.value = response.payload.availableVersions
    } else {
      console.error('获取版本信息失败:', response.error || '未知错误')
      availableVersions.value = []
    }
  } catch (error) {
    console.error('处理版本信息响应失败:', error)
    versionLoading.value = false
    availableVersions.value = []
  }
}

// 处理版本切换进度消息
const handleSwitchProgressResponse = (message) => {
  try {
    const response = JSON.parse(message.body)
    console.log('收到版本切换进度:', response)
    
    if (response.success && response.payload && response.payload.message) {
      const progressMessage = response.payload.message
      switchProgress.value = progressMessage
      
      // 计算耗时（如果有开始时间）
      if (switchStartTime.value) {
        switchTotalTime.value = Math.round((Date.now() - switchStartTime.value) / 1000)
      }
      
      // 为不同的步骤添加图标
      let displayMessage = progressMessage
      if (progressMessage.includes('步骤 1/8')) {
        displayMessage = `🔍 ${progressMessage}`
      } else if (progressMessage.includes('步骤 2/8')) {
        displayMessage = `⏹️ ${progressMessage}`
      } else if (progressMessage.includes('步骤 3/8')) {
        displayMessage = `🔧 ${progressMessage}`
      } else if (progressMessage.includes('步骤 4/8')) {
        displayMessage = `⬇️ ${progressMessage}`
      } else if (progressMessage.includes('步骤 5/8')) {
        displayMessage = `▶️ ${progressMessage}`
      } else if (progressMessage.includes('步骤 6/8')) {
        displayMessage = `⏱️ ${progressMessage}`
      } else if (progressMessage.includes('步骤 7/8')) {
        displayMessage = `🧹 ${progressMessage}`
      } else if (progressMessage.includes('步骤 8/8')) {
        displayMessage = `✅ ${progressMessage}`
        // 关键修复：当收到步骤8/8完成消息时，立即停止版本切换状态
        versionSwitching.value = false
        // 清理定时器
        if (timerInterval) {
          clearInterval(timerInterval)
          timerInterval = null
        }
        console.log('版本切换完成，停止切换状态和定时器')
      }
      
      switchProgress.value = displayMessage
      
      // 如果是完成消息，设置自动清理定时器
      if (progressMessage.includes('步骤 8/8')) {
        setTimeout(() => {
          if (!versionSwitching.value) { // 只有在确实已经停止的情况下才清理
            switchProgress.value = ''
            switchStartTime.value = null
            switchTotalTime.value = 0
          }
        }, 5000)
      }
      
      console.log('更新进度显示:', displayMessage)
    }
  } catch (error) {
    console.error('处理版本切换进度失败:', error)
  }
}

// 处理服务操作结果
const handleActionResult = (message) => {
  try {
    const response = JSON.parse(message.body)
    console.log('收到服务操作结果:', response)
    
    // 计算总耗时
    if (switchStartTime.value) {
      switchTotalTime.value = Math.round((Date.now() - switchStartTime.value) / 1000)
    }
    
    // 确保版本切换状态被停止（防御性编程）
    if (versionSwitching.value) {
      versionSwitching.value = false
      // 清理定时器
      if (timerInterval) {
        clearInterval(timerInterval)
        timerInterval = null
      }
      console.log('通过ActionResult停止版本切换状态和定时器')
    }
    
    if (response.success) {
      // 从后端进度消息中提取总耗时（如果有的话）
      const progressMessage = response.message || response.error || '版本切换成功'
      const timeMatch = progressMessage.match(/总耗时\s*(\d+)\s*秒/)
      if (timeMatch) {
        switchTotalTime.value = parseInt(timeMatch[1])
      }
      
      // 显示完成消息，包含总耗时
      switchProgress.value = `✅ ${progressMessage}${switchTotalTime.value > 0 ? ` (总耗时: ${switchTotalTime.value}秒)` : ''}`
      
      // 5秒后清除进度消息
      setTimeout(() => {
        switchProgress.value = ''
        switchStartTime.value = null
        switchTotalTime.value = 0
      }, 5000)
    } else {
      // 显示失败消息
      switchProgress.value = `❌ ${response.error || response.message || '版本切换失败'}${switchTotalTime.value > 0 ? ` (耗时: ${switchTotalTime.value}秒)` : ''}`
      
      // 8秒后清除错误消息
      setTimeout(() => {
        switchProgress.value = ''
        switchStartTime.value = null
        switchTotalTime.value = 0
      }, 8000)
    }
  } catch (error) {
    console.error('处理服务操作结果失败:', error)
    versionSwitching.value = false
    switchProgress.value = '❌ 处理响应失败'
    setTimeout(() => {
      switchProgress.value = ''
      switchStartTime.value = null
      switchTotalTime.value = 0
    }, 5000)
  }
}

// 获取版本样式类
const getVersionClass = (version, index) => {
  if (version === 'latest' || index === 0) return 'latest'
  if (index === 1) return 'stable'
  return 'release'
}

// 获取版本名称
const getVersionName = (version, index) => {
  if (version === 'latest' || index === 0) return '最新版'
  if (index === 1) return '稳定版'
  return `版本 ${version}`
}

// 获取版本描述
const getVersionDescription = (version, index) => {
  if (version === 'latest' || index === 0) return '包含最新功能和修复'
  if (index === 1) return '推荐生产环境使用'
  return '正式发布版本'
}

// 当模态框打开时获取版本信息
const openModal = () => {
  showUpgradeModal.value = true
  
  // 如果已经有版本数据，直接显示
  if (availableVersions.value.length > 0) {
    return
  }
  
  // 使用computed属性进行连接检查
  const client = stompClient.value
  const connected = isConnected.value
  
  if (connected && client) {
    fetchVersionInfo()
  } else {
    // 连接未就绪时显示错误状态
    versionLoading.value = false
    console.warn('WebSocket连接未就绪，无法获取版本信息', {
      connected: connected,
      clientExists: !!client
    })
  }
}

// 设置WebSocket订阅
const setupSubscriptions = () => {
  if (!isConnected.value) {
    console.log('WebSocket未连接，跳过订阅设置')
    return
  }
  
  if (!stompClient.value) {
    console.log('STOMP客户端未初始化，跳过订阅设置')
    return
  }
  
  // 如果已经有订阅，先取消
  if (versionSubscription) {
    try {
      versionSubscription.unsubscribe()
      versionSubscription = null
    } catch (error) {
      console.warn('取消旧版本信息订阅失败:', error)
    }
  }
  
  if (progressSubscription) {
    try {
      progressSubscription.unsubscribe()
      progressSubscription = null
    } catch (error) {
      console.warn('取消旧进度订阅失败:', error)
    }
  }
  
  if (actionResultSubscription) {
    try {
      actionResultSubscription.unsubscribe()
      actionResultSubscription = null
    } catch (error) {
      console.warn('取消旧操作结果订阅失败:', error)
    }
  }
  
  // 获取sessionId
  let sessionId = 'default'
  try {
    if (connectionState.currentSessionId) {
      sessionId = connectionState.currentSessionId
    } else {
      sessionId = Math.random().toString(36).substr(2, 9)
    }
  } catch (error) {
    console.warn('无法获取WebSocket sessionId, 使用随机值:', error)
    sessionId = Math.random().toString(36).substr(2, 9)
  }
  
  try {
    // 订阅版本信息 (使用sendSuccessMessage模式)
    versionSubscription = stompClient.value.subscribe(
      `/user/queue/sillytavern/version-info`,
      handleVersionInfoResponse
    )
    console.log('版本信息订阅设置成功')
    
    // 订阅版本切换进度 (使用sendSuccessMessage模式)
    progressSubscription = stompClient.value.subscribe(
      `/user/queue/sillytavern/version-switch-progress`,
      handleSwitchProgressResponse
    )
    console.log('版本切换进度订阅设置成功')
    
    // 订阅服务操作结果 (使用convertAndSend模式，需要sessionId)
    actionResultSubscription = stompClient.value.subscribe(
      `/queue/sillytavern/action-result-user${sessionId}`,
      handleActionResult
    )
    console.log('服务操作结果订阅设置成功, sessionId:', sessionId)
    
  } catch (error) {
    console.error('设置订阅失败:', error)
    versionSubscription = null
    progressSubscription = null
  }
}

onMounted(() => {
  // 延迟设置订阅以确保WebSocket连接已建立
  setTimeout(() => {
    setupSubscriptions()
  }, 100)
})

// 监听WebSocket连接状态变化
watch([isConnected, stompClient], ([connected, client], [prevConnected, prevClient]) => {
  try {
    if (connected && client && (!prevConnected || !prevClient)) {
      // 连接建立时设置订阅
      console.log('WebSocket连接状态变化，重新设置订阅')
      setTimeout(() => {
        setupSubscriptions()
      }, 100)
    }
  } catch (error) {
    console.error('监听WebSocket连接变化时发生错误:', error)
  }
}, { 
  immediate: false,  // 不立即执行
  deep: false        // 浅比较
})

onUnmounted(() => {
  if (versionSubscription) {
    versionSubscription.unsubscribe()
  }
  if (progressSubscription) {
    progressSubscription.unsubscribe()
  }
  if (actionResultSubscription) {
    actionResultSubscription.unsubscribe()
  }
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
})
</script>

<style scoped>
.service-controls {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.controls-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.header-status {
  display: flex;
  align-items: center;
}

.controls-title {
  margin: 0;
  font-size: 1.1rem;
  color: #2d3748;
  font-weight: 600;
}

.action-status {
  display: flex;
  align-items: center;
  color: #667eea;
  font-size: 0.9rem;
  font-weight: 500;
}

.action-spinner {
  margin-right: 8px;
  animation: spin 1s linear infinite;
}

.controls-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.control-button {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 10px;
  border: 2px solid transparent;
  background: #f7fafc;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.control-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.button-icon {
  font-size: 1.8rem;
  margin-right: 12px;
}

.button-content {
  flex: 1;
}

.button-title {
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 2px;
}

.button-subtitle {
  font-size: 0.8rem;
  color: #718096;
}

/* Specific button styles */
.start-button:hover:not(:disabled) {
  border-color: #22543d;
  background: #f0fff4;
}

.stop-button:hover:not(:disabled) {
  border-color: #742a2a;
  background: #fffaf0;
}

.restart-button:hover:not(:disabled) {
  border-color: #744210;
  background: #fffff0;
}

.upgrade-button:hover:not(:disabled) {
  border-color: #553c9a;
  background: #faf5ff;
}


/* Container Info */
.container-info {
  background: #f7fafc;
  border-radius: 8px;
  padding: 16px;
  border-left: 4px solid #667eea;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}

.info-label {
  font-weight: 500;
  color: #4a5568;
  font-size: 0.9rem;
}

.info-value {
  font-weight: 500;
  font-size: 0.9rem;
  color: #2d3748;
}

.status-running {
  color: #22543d;
  background: #c6f6d5;
  padding: 2px 8px;
  border-radius: 4px;
}

.status-stopped {
  color: #744210;
  background: #faf089;
  padding: 2px 8px;
  border-radius: 4px;
}

.info-link a {
  color: #667eea;
  text-decoration: none;
}

.info-link a:hover {
  text-decoration: underline;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Version Switch Modal Styles */
.upgrade-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.upgrade-modal {
  background: white;
  border-radius: 12px;
  padding: 0;
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 600;
  color: #2d3748;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #718096;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  color: #4a5568;
}

.modal-body {
  padding: 24px;
}

.modal-description {
  margin: 0 0 20px 0;
  color: #4a5568;
  font-size: 0.95rem;
}

.version-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-option {
  display: flex;
  align-items: center;
  padding: 16px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  background: white;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.version-option:hover {
  border-color: #667eea;
  background: #f7fafc;
  transform: translateY(-1px);
}

.version-tag {
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
  margin-right: 16px;
  min-width: 60px;
  text-align: center;
  color: white;
}

.version-tag.latest {
  background: #f56565;
}

.version-tag.stable {
  background: #48bb78;
}

.version-tag.release {
  background: #667eea;
}

.version-info {
  flex: 1;
}

.version-name {
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 4px;
}

.version-desc {
  font-size: 0.85rem;
  color: #718096;
}

/* Responsive design */
@media (max-width: 768px) {
  .controls-grid {
    grid-template-columns: 1fr;
  }
  
  .info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}

/* Version Switch Progress Styles */
.switch-progress {
  margin: 16px 0;
  padding: 12px 16px;
  background: #f0f8ff;
  border-radius: 8px;
  border-left: 4px solid #667eea;
}

.progress-bar {
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  width: 100%;
  transition: width 0.3s ease;
}

.progress-fill.animated {
  animation: progressPulse 2s ease-in-out infinite;
}

@keyframes progressPulse {
  0%, 100% { opacity: 0.8; }
  50% { opacity: 1; }
}

.progress-text {
  font-size: 0.9rem;
  color: #4a5568;
  font-weight: 500;
  text-align: center;
}

.version-switching {
  color: #667eea !important;
}

.switch-timer {
  margin-left: 8px;
  font-size: 0.9rem;
  color: #667eea;
  font-weight: 600;
  background: #f0f8ff;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #667eea;
}
.version-loading {
  text-align: center;
  padding: 2rem;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

.version-error {
  text-align: center;
  padding: 2rem;
  color: #e53e3e;
}

.retry-button {
  background: #e53e3e;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  margin-top: 1rem;
}

.retry-button:hover {
  background: #c53030;
}

/* Updated version option styles */
.version-option.current-version {
  border: 2px solid #48bb78;
  background: #f0fff4;
}

.current-badge {
  background: #48bb78;
  color: white;
  padding: 0.125rem 0.5rem;
  border-radius: 12px;
  font-size: 0.7rem;
  margin-left: 0.5rem;
  font-weight: 500;
}
</style>