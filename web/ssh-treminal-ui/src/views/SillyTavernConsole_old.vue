<template>
  <div class="sillytavern-console">
    <NavigationHeader>
      <template #actions>
        <div class="header-actions">
          <!-- 连接状态显示 -->
          <div class="connection-status" :class="connectionStatusClass">
            <div class="status-dot" :class="connectionStatus"></div>
            <span class="status-text">{{ statusText }}</span>
          </div>
          
          <!-- 操作按钮 -->
          <button 
            v-if="connectionState.isConnected" 
            @click="refreshStatus" 
            :disabled="isStatusLoading"
            class="btn btn-secondary btn-sm"
          >
            <i :class="isStatusLoading ? 'fas fa-spinner fa-spin' : 'fas fa-sync-alt'"></i>
            刷新状态
          </button>
          
          <button 
            v-if="!connectionState.isConnected" 
            @click="showConnectionModal = true"
            class="btn btn-primary btn-sm"
          >
            <i class="fas fa-plug"></i>
            连接服务器
          </button>
        </div>
      </template>
    </NavigationHeader>
    
    <main class="console-main">
      <div class="console-header">
        <h1 class="console-title">SillyTavern 管理控制台</h1>
        <p class="console-subtitle">一站式 AI 对话平台管理中心</p>
      </div>
      
      <div class="console-content">
        <!-- 未连接状态 -->
        <div v-if="!connectionState.isConnected && !connectionState.connecting" class="connection-prompt">
          <div class="prompt-card">
            <div class="prompt-icon">
              <i class="fas fa-server"></i>
            </div>
            <h3>需要连接服务器</h3>
            <p>请连接到您的服务器以开始管理 SillyTavern 服务</p>
            <button @click="showConnectionModal = true" class="btn btn-primary btn-lg">
              <i class="fas fa-plug"></i>
              连接服务器
            </button>
          </div>
        </div>
        
        <!-- 连接中状态 -->
        <div v-else-if="connectionState.connecting" class="connecting-state">
          <div class="connecting-card">
            <div class="connecting-spinner">
              <i class="fas fa-spinner fa-spin"></i>
            </div>
            <h3>正在连接服务器...</h3>
            <p>请稍候，正在建立安全连接</p>
          </div>
        </div>
        
        <!-- 已连接状态 - 主要功能区域 -->
        <div v-else class="console-dashboard">
          <!-- 容器状态卡片 -->
          <div class="status-banner" :class="statusBannerClass">
            <div class="banner-content">
              <div class="banner-info">
                <div class="status-indicator">
                  <div class="status-icon" :class="containerStatusClass">
                    <i :class="containerStatusIcon"></i>
                  </div>
                  <div class="status-details">
                    <h3 class="status-title">{{ containerStatusTitle }}</h3>
                    <p class="status-description">{{ containerStatusDescription }}</p>
                  </div>
                </div>
                
                <div v-if="containerStatus && containerStatus.running" class="quick-info">
                  <div class="info-item" v-if="containerStatus.port">
                    <i class="fas fa-globe"></i>
                    <span>端口: {{ containerStatus.port }}</span>
                  </div>
                  <div class="info-item" v-if="containerStatus.uptimeSeconds">
                    <i class="fas fa-clock"></i>
                    <span>运行时间: {{ formatUptime(containerStatus.uptimeSeconds) }}</span>
                  </div>
                </div>
              </div>
              
              <div class="banner-actions">
                <button 
                  v-if="!containerStatus || !containerStatus.exists"
                  @click="activeTab = 'deployment'"
                  class="btn btn-primary"
                >
                  <i class="fas fa-rocket"></i>
                  开始部署
                </button>
                <button 
                  v-else-if="containerStatus.running"
                  @click="showAccessInfo = true"
                  class="btn btn-success"
                >
                  <i class="fas fa-external-link-alt"></i>
                  访问服务
                </button>
                <button 
                  v-else
                  @click="startContainer"
                  :disabled="isActionLoading"
                  class="btn btn-primary"
                >
                  <i :class="isActionLoading ? 'fas fa-spinner fa-spin' : 'fas fa-play'"></i>
                  启动服务
                </button>
              </div>
            </div>
          </div>
          
          <!-- 功能标签页 -->
          <div class="console-tabs">
            <div class="tab-headers">
              <button 
                v-for="tab in tabs" 
                :key="tab.id"
                @click="activeTab = tab.id"
                :class="['tab-header', { active: activeTab === tab.id }]"
                :disabled="tab.disabled"
              >
                <i :class="tab.icon"></i>
                <span>{{ tab.name }}</span>
                <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
              </button>
            </div>
            
            <div class="tab-content">
              <!-- 部署向导 -->
              <div v-if="activeTab === 'deployment'" class="tab-panel">
                <DeploymentWizard 
                  :connection="connectionState.connectionInfo"
                  :system-info="systemInfo"
                  :is-system-valid="isSystemValid"
                  :system-checking="systemChecking"
                  :is-deploying="isDeploying"
                  :deployment-progress="deploymentProgress"
                  @validate-system="handleValidateSystem"
                  @deploy="handleDeploy"
                />
              </div>
              
              <!-- 服务控制 -->
              <div v-if="activeTab === 'services'" class="tab-panel">
                <ServiceControls 
                  :connection="connectionState.connectionInfo"
                  :container-status="containerStatus"
                  @action-started="handleActionStarted"
                  @action-completed="handleActionCompleted"
                  @status-updated="handleStatusUpdated"
                />
              </div>
              
              <!-- 配置管理 -->
              <div v-if="activeTab === 'configuration'" class="tab-panel">
                <ConfigurationEditor 
                  :connection="connectionState.connectionInfo"
                  @configuration-updated="handleConfigurationUpdated"
                />
              </div>
              
              <!-- 日志查看 -->
              <div v-if="activeTab === 'logs'" class="tab-panel">
                <LogViewer 
                  :connection="connectionState.connectionInfo"
                  container-name="sillytavern"
                />
              </div>
              
              <!-- 数据管理 -->
              <div v-if="activeTab === 'data'" class="tab-panel">
                <DataManager 
                  :connection="connectionState.connectionInfo"
                  @export-completed="handleExportCompleted"
                  @import-completed="handleImportCompleted"
                />
              </div>
              
              <!-- 访问信息 -->
              <div v-if="activeTab === 'access'" class="tab-panel">
                <AccessInfo 
                  :connection="connectionState.connectionInfo"
                  :container-status="containerStatus"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
    
    <!-- 连接模态框 -->
    <ConnectionManager v-if="showConnectionModal" @close="showConnectionModal = false" />
    
    <!-- 访问信息快速弹窗 -->
    <div v-if="showAccessInfo" class="modal-overlay" @click="showAccessInfo = false">
      <div class="modal-content access-modal" @click.stop>
        <div class="modal-header">
          <h3>服务访问信息</h3>
          <button @click="showAccessInfo = false" class="modal-close">&times;</button>
        </div>
        <div class="modal-body">
          <AccessInfo 
            :connection="connectionState.connectionInfo"
            :container-status="containerStatus"
            :compact="true"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import NavigationHeader from '../components/NavigationHeader.vue'
import ConnectionManager from '../components/ConnectionManager.vue'
import DeploymentWizard from '../components/sillytavern/DeploymentWizard.vue'
import ServiceControls from '../components/sillytavern/ServiceControls.vue'
import ConfigurationEditor from '../components/sillytavern/ConfigurationEditor.vue'
import LogViewer from '../components/sillytavern/LogViewer.vue'
import DataManager from '../components/sillytavern/DataManager.vue'
import AccessInfo from '../components/sillytavern/AccessInfo.vue'
import useConnectionManager from '../composables/useConnectionManager'
import { useSillyTavern } from '../composables/useSillyTavern'

// 连接管理
const { connectionState, connectionStatus } = useConnectionManager()

// SillyTavern 管理
const { 
  containerStatus, 
  isStatusLoading, 
  isPerformingAction: isActionLoading,
  systemInfo,
  isSystemValid,
  systemChecking,
  isDeploying,
  deploymentProgress,
  getContainerStatus,
  performServiceAction,
  validateSystem,
  deployContainer,
  connectToSillyTavern
} = useSillyTavern()

// 状态管理
const showConnectionModal = ref(false)
const showAccessInfo = ref(false)
const activeTab = ref('deployment')

// 标签页配置
const tabs = computed(() => [
  {
    id: 'deployment',
    name: '部署',
    icon: 'fas fa-rocket',
    disabled: false
  },
  {
    id: 'services',
    name: '服务控制',
    icon: 'fas fa-cogs',
    disabled: !containerStatus.value?.exists
  },
  {
    id: 'configuration',
    name: '配置管理',
    icon: 'fas fa-edit',
    disabled: !containerStatus.value?.exists
  },
  {
    id: 'logs',
    name: '日志查看',
    icon: 'fas fa-file-alt',
    disabled: !containerStatus.value?.exists,
    badge: containerStatus.value?.running ? null : '离线'
  },
  {
    id: 'data',
    name: '数据管理',
    icon: 'fas fa-database',
    disabled: false
  },
  {
    id: 'access',
    name: '访问信息',
    icon: 'fas fa-info-circle',
    disabled: !containerStatus.value?.running
  }
])

// 计算属性
const connectionStatusClass = computed(() => ({
  'status-connected': connectionState.isConnected,
  'status-connecting': connectionState.connecting,
  'status-error': connectionState.error,
  'status-disconnected': !connectionState.isConnected && !connectionState.connecting
}))

const statusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connecting': return '连接中...'
    case 'connected': return '已连接'
    case 'error': return '连接失败'
    default: return '未连接'
  }
})

const statusBannerClass = computed(() => {
  if (!containerStatus.value) return 'banner-unknown'
  if (containerStatus.value.running) return 'banner-running'
  if (containerStatus.value.exists) return 'banner-stopped'
  return 'banner-not-exists'
})

const containerStatusClass = computed(() => {
  if (!containerStatus.value) return 'status-unknown'
  if (containerStatus.value.running) return 'status-running'
  if (containerStatus.value.exists) return 'status-stopped'
  return 'status-not-exists'
})

const containerStatusIcon = computed(() => {
  if (!containerStatus.value) return 'fas fa-question'
  if (containerStatus.value.running) return 'fas fa-play-circle'
  if (containerStatus.value.exists) return 'fas fa-pause-circle'
  return 'fas fa-plus-circle'
})

const containerStatusTitle = computed(() => {
  if (!containerStatus.value) return '状态未知'
  if (containerStatus.value.running) return 'SillyTavern 运行中'
  if (containerStatus.value.exists) return 'SillyTavern 已停止'
  return 'SillyTavern 未部署'
})

const containerStatusDescription = computed(() => {
  if (!containerStatus.value) return '正在获取容器状态...'
  if (containerStatus.value.running) return '服务正常运行，可以正常访问'
  if (containerStatus.value.exists) return '容器已创建但未启动'
  return '还未部署 SillyTavern 容器'
})

// 方法
const refreshStatus = async () => {
  if (connectionState.isConnected) {
    await getContainerStatus()
  }
}

const startContainer = async () => {
  try {
    await performServiceAction('start')
    await refreshStatus()
  } catch (error) {
    console.error('启动容器失败:', error)
  }
}

const formatUptime = (seconds) => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  }
  return `${minutes}分钟`
}

// 事件处理
const handleValidateSystem = async () => {
  try {
    await validateSystem()
  } catch (error) {
    console.error('系统验证失败:', error)
  }
}

const handleDeploy = async (deploymentConfig) => {
  try {
    console.log('开始部署，配置:', deploymentConfig)
    await deployContainer(deploymentConfig)
    activeTab.value = 'services'
  } catch (error) {
    console.error('部署失败:', error)
  }
}

const handleDeploymentStarted = () => {
  activeTab.value = 'services'
}

const handleDeploymentCompleted = () => {
  refreshStatus()
}

const handleDeploymentFailed = (error) => {
  console.error('部署失败:', error)
}

const handleActionStarted = () => {
  // 处理操作开始
}

const handleActionCompleted = () => {
  refreshStatus()
}

const handleStatusUpdated = (newStatus) => { 
  // 处理状态更新
}

const handleConfigurationUpdated = () => {
  refreshStatus()
}

const handleExportCompleted = () => {
  // 处理导出完成
}

const handleImportCompleted = () => {
  refreshStatus()
}

// 生命周期
let statusInterval = null

onMounted(async () => {
  if (connectionState.isConnected) {
    // 初始化SillyTavern连接
    try {
      await connectToSillyTavern(connectionState.connectionInfo)
      await refreshStatus()
    } catch (error) {
      console.error('连接SillyTavern失败:', error)
    }
    
    // 每30秒自动刷新状态
    statusInterval = setInterval(() => {
      if (connectionState.isConnected && !isStatusLoading.value) {
        refreshStatus()
      }
    }, 30000)
  }
})

onUnmounted(() => {
  if (statusInterval) {
    clearInterval(statusInterval)
  }
})
</script>

<style scoped>
.sillytavern-console {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

.sillytavern-console::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: 
    radial-gradient(circle at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(120, 119, 198, 0.2) 0%, transparent 50%);
}

.console-main {
  position: relative;
  z-index: 1;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.console-header {
  text-align: center;
  margin-bottom: 32px;
}

.console-title {
  font-size: 2.5rem;
  font-weight: 800;
  background: linear-gradient(135deg, #ffffff 0%, #f0f0f0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  text-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.console-subtitle {
  font-size: 1.2rem;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  font-weight: 300;
}

.console-content {
  max-width: 1200px;
  margin: 0 auto;
}

/* 连接状态样式 */
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.status-dot.connected {
  background: #48bb78;
  box-shadow: 0 0 8px rgba(72, 187, 120, 0.5);
}

.status-dot.connecting {
  background: #ed8936;
  animation: pulse 2s infinite;
}

.status-dot.error {
  background: #f56565;
}

.status-dot.disconnected {
  background: #a0aec0;
}

.status-text {
  color: white;
  font-size: 0.875rem;
  font-weight: 500;
}

/* 连接提示样式 */
.connection-prompt {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.prompt-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 48px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  max-width: 500px;
}

.prompt-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #4299e1, #667eea);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  box-shadow: 0 8px 32px rgba(66, 153, 225, 0.3);
}

.prompt-icon i {
  font-size: 2rem;
  color: white;
}

.prompt-card h3 {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 16px 0;
}

.prompt-card p {
  color: #718096;
  line-height: 1.6;
  margin: 0 0 32px 0;
}

/* 连接中状态 */
.connecting-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.connecting-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 48px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  max-width: 400px;
}

.connecting-spinner {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #ed8936, #f6ad55);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  box-shadow: 0 8px 32px rgba(237, 137, 54, 0.3);
}

.connecting-spinner i {
  font-size: 2rem;
  color: white;
}

.connecting-card h3 {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 16px 0;
}

.connecting-card p {
  color: #718096;
  line-height: 1.6;
  margin: 0;
}

/* 控制台仪表板 */
.console-dashboard {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 状态横幅 */
.status-banner {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-left: 6px solid #e2e8f0;
  transition: all 0.3s ease;
}

.banner-running {
  border-left-color: #48bb78;
  background: linear-gradient(135deg, #f0fff4 0%, #c6f6d5 100%);
}

.banner-stopped {
  border-left-color: #ed8936;
  background: linear-gradient(135deg, #fffaf0 0%, #feebc8 100%);
}

.banner-not-exists {
  border-left-color: #a0aec0;
}

.banner-unknown {
  border-left-color: #4299e1;
  background: linear-gradient(135deg, #ebf8ff 0%, #bee3f8 100%);
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
}

.banner-info {
  flex: 1;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.status-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.status-running {
  background: linear-gradient(135deg, #48bb78, #38a169);
  color: white;
  box-shadow: 0 4px 16px rgba(72, 187, 120, 0.3);
}

.status-stopped {
  background: linear-gradient(135deg, #ed8936, #dd6b20);
  color: white;
  box-shadow: 0 4px 16px rgba(237, 137, 54, 0.3);
}

.status-not-exists {
  background: linear-gradient(135deg, #a0aec0, #718096);
  color: white;
  box-shadow: 0 4px 16px rgba(160, 174, 192, 0.3);
}

.status-unknown {
  background: linear-gradient(135deg, #4299e1, #3182ce);
  color: white;
  box-shadow: 0 4px 16px rgba(66, 153, 225, 0.3);
}

.status-icon i {
  font-size: 1.8rem;
}

.status-details h3 {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 8px 0;
}

.status-details p {
  color: #718096;
  margin: 0;
  line-height: 1.4;
}

.quick-info {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: #4a5568;
}

.info-item i {
  color: #4299e1;
}

.banner-actions {
  display: flex;
  gap: 12px;
}

/* 标签页样式 */
.console-tabs {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.tab-headers {
  display: flex;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  overflow-x: auto;
}

.tab-header {
  flex: 1;
  min-width: 120px;
  padding: 16px 20px;
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #4a5568;
  font-weight: 500;
  transition: all 0.2s ease;
  position: relative;
  white-space: nowrap;
}

.tab-header:hover:not(:disabled) {
  background: #edf2f7;
  color: #2d3748;
}

.tab-header.active {
  background: white;
  color: #4299e1;
  border-bottom: 2px solid #4299e1;
}

.tab-header:disabled {
  color: #a0aec0;
  cursor: not-allowed;
}

.tab-badge {
  background: #f56565;
  color: white;
  font-size: 0.75rem;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 600;
}

.tab-content {
  padding: 32px;
  min-height: 400px;
}

.tab-panel {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.access-modal {
  max-width: 500px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #2d3748;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #a0aec0;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.modal-close:hover {
  background: #e2e8f0;
  color: #2d3748;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

/* 按钮样式 */
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.8rem;
}

.btn-lg {
  padding: 14px 24px;
  font-size: 1rem;
}

.btn-primary {
  background: linear-gradient(135deg, #4299e1, #667eea);
  color: white;
  box-shadow: 0 4px 16px rgba(66, 153, 225, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #3182ce, #5a67d8);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(66, 153, 225, 0.4);
}

.btn-secondary {
  background: linear-gradient(135deg, #38b2ac, #319795);
  color: white;
  box-shadow: 0 4px 16px rgba(56, 178, 172, 0.3);
}

.btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #319795, #2c7a7b);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(56, 178, 172, 0.4);
}

.btn-success {
  background: linear-gradient(135deg, #48bb78, #38a169);
  color: white;
  box-shadow: 0 4px 16px rgba(72, 187, 120, 0.3);
}

.btn-success:hover:not(:disabled) {
  background: linear-gradient(135deg, #38a169, #2f855a);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(72, 187, 120, 0.4);
}

.btn:disabled {
  background: #e2e8f0;
  color: #a0aec0;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .banner-content {
    flex-direction: column;
    align-items: stretch;
    gap: 24px;
  }
  
  .banner-actions {
    justify-content: center;
  }
}

@media (max-width: 768px) {
  .console-main {
    padding: 15px;
  }
  
  .console-title {
    font-size: 2rem;
  }
  
  .console-subtitle {
    font-size: 1rem;
  }
  
  .status-banner {
    padding: 24px;
  }
  
  .status-indicator {
    flex-direction: column;
    text-align: center;
    gap: 16px;
    margin-bottom: 20px;
  }
  
  .quick-info {
    justify-content: center;
    gap: 16px;
  }
  
  .tab-content {
    padding: 20px;
  }
  
  .tab-headers {
    flex-wrap: wrap;
  }
  
  .tab-header {
    flex: 1 1 50%;
    min-width: 100px;
    padding: 12px 16px;
    font-size: 0.875rem;
  }
  
  .header-actions {
    flex-direction: column;
    gap: 8px;
  }
  
  .connection-status {
    order: 2;
  }
}

@media (max-width: 480px) {
  .console-title {
    font-size: 1.8rem;
  }
  
  .prompt-card, .connecting-card {
    padding: 32px 24px;
  }
  
  .status-banner {
    padding: 20px;
  }
  
  .status-icon {
    width: 56px;
    height: 56px;
  }
  
  .status-icon i {
    font-size: 1.5rem;
  }
  
  .tab-header {
    flex: 1 1 100%;
    padding: 10px 12px;
  }
  
  .modal-content {
    width: 95%;
    margin: 20px;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>