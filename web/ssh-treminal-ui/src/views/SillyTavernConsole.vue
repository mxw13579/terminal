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
        
        <!-- 已连接状态 - 新的双边框布局 -->
        <div v-else class="console-dashboard">
          <!-- 左侧边栏 (25%) -->
          <div class="sidebar">
            <!-- 第一部分：Docker信息展示 -->
            <div class="sidebar-section docker-info">
              <div class="section-header">
                <h4 class="section-title">
                  <span class="section-icon">🐳</span>
                  Docker 信息
                </h4>
              </div>
              <div class="section-content">
                <!-- Docker not available case -->
                <div v-if="containerStatus && containerStatus.error" class="docker-unavailable">
                  <i class="fas fa-exclamation-triangle"></i>
                  <p>{{ containerStatus.error }}</p>
                  <small class="text-muted">{{ containerStatus.status }}</small>
                </div>
                <!-- Docker available and container exists -->
                <div v-else-if="containerStatus && containerStatus.exists" class="docker-stats">
                  <div class="stat-item">
                    <span class="stat-label">运行时间</span>
                    <span class="stat-value">{{ containerStatus.uptimeSeconds ? formatUptime(containerStatus.uptimeSeconds) : '未运行' }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">占用内存</span>
                    <span class="stat-value">{{ containerStatus.memoryUsage || '未知' }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">占用CPU</span>
                    <span class="stat-value">{{ containerStatus.cpuUsage || '未知' }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">当前版本</span>
                    <span class="stat-value">{{ containerStatus.currentVersion || '未知' }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">最新版本</span>
                    <span class="stat-value clickable" @click="checkForUpdates">
                      {{ containerStatus.latestVersion || '检查更新' }}
                    </span>
                  </div>
                </div>
                <!-- Container doesn't exist but Docker is available -->
                <div v-else-if="containerStatus && !containerStatus.exists && !containerStatus.error" class="docker-not-deployed">
                  <i class="fas fa-info-circle"></i>
                  <p>Docker已安装，但容器未部署</p>
                  <small class="text-muted">请先部署SillyTavern容器</small>
                </div>
                <!-- Loading state -->
                <div v-else class="docker-loading">
                  <i class="fas fa-spinner fa-spin"></i>
                  <p>加载Docker信息...</p>
                </div>
              </div>
            </div>

            <!-- 第二部分：功能页面 -->
            <div class="sidebar-section function-menu">
              <div class="section-header">
                <h4 class="section-title">
                  <span class="section-icon">⚙️</span>
                  功能菜单
                </h4>
              </div>
              <div class="section-content">
                <nav class="function-nav">
                  <button 
                    v-for="tab in tabs" 
                    :key="tab.id"
                    @click="activeTab = tab.id"
                    :class="['nav-item', { 'nav-item-active': activeTab === tab.id, 'nav-item-disabled': tab.disabled }]"
                    :disabled="tab.disabled"
                  >
                    <i :class="tab.icon"></i>
                    <span class="nav-text">{{ tab.name }}</span>
                    <span v-if="tab.badge" class="nav-badge">{{ tab.badge }}</span>
                  </button>
                </nav>
              </div>
            </div>

            <!-- 第三部分：访问信息展示 -->
            <div class="sidebar-section access-info">
              <div class="section-header">
                <h4 class="section-title">
                  <span class="section-icon">🔗</span>
                  访问信息
                </h4>
              </div>
              <div class="section-content">
                <div v-if="containerStatus && containerStatus.running" class="access-details">
                  <div class="access-item">
                    <span class="access-label">登录地址</span>
                    <span class="access-value">{{ containerStatus.hostAddress || connectionState.connectionInfo?.host || 'localhost' }}:{{ containerStatus.port || '8000' }}</span>
                  </div>
                  <div class="access-item">
                    <span class="access-label">加速访问地址</span>
                    <span class="access-value">{{ containerStatus.acceleratedUrl || '暂无' }}</span>
                  </div>
                  <div class="access-item">
                    <span class="access-label">账号</span>
                    <span class="access-value">{{ containerStatus.username || 'admin' }}</span>
                  </div>
                  <div class="access-item">
                    <span class="access-label">密码</span>
                    <span class="access-value">{{ containerStatus.password || 'password' }}</span>
                  </div>
                  <button @click="openService" class="btn btn-primary btn-sm access-button">
                    <i class="fas fa-external-link-alt"></i>
                    访问服务
                  </button>
                </div>
                <div v-else class="access-unavailable">
                  <i class="fas fa-times-circle"></i>
                  <div v-if="!containerStatus">
                    <p>正在检查服务状态...</p>
                  </div>
                  <div v-else-if="containerStatus.error">
                    <p>{{ containerStatus.error }}</p>
                    <small class="text-muted">{{ containerStatus.status }}</small>
                  </div>
                  <div v-else-if="containerStatus.exists && !containerStatus.running">
                    <p>服务已停止</p>
                    <small class="text-muted">容器存在但未运行</small>
                  </div>
                  <div v-else-if="!containerStatus.exists">
                    <p>服务未部署</p>
                    <small class="text-muted">请先部署SillyTavern容器</small>
                  </div>
                  <div v-else>
                    <p>服务未运行</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧主内容区 (75%) -->
          <div class="main-content">
            <div class="content-header">
              <h2 class="content-title">{{ getTabTitle(activeTab) }}</h2>
              <div class="content-actions">
                <button 
                  @click="refreshStatus" 
                  :disabled="isStatusLoading"
                  class="btn btn-secondary btn-sm"
                >
                  <i :class="isStatusLoading ? 'fas fa-spinner fa-spin' : 'fas fa-sync-alt'"></i>
                  刷新
                </button>
              </div>
            </div>

            <div class="content-body">
              <!-- 部署向导 -->
              <div v-if="activeTab === 'deployment'" class="content-panel">
                <InteractiveDeploymentWizard 
                  :connection="connectionState.connectionInfo"
                  :system-info="systemInfo"
                  :is-system-valid="isSystemValid"
                  :system-checking="systemChecking"
                  :is-deploying="isDeploying"
                  :deployment-progress="deploymentProgress"
                  :available-versions="availableVersions"
                  :is-loading-versions="isLoadingVersions"
                  :version-error="versionError"
                  @validate-system="handleValidateSystem"
                  @deploy="handleDeploy"
                  @deployment-complete="handleDeploymentComplete"
                  @get-versions="handleGetVersions"
                  @step-confirmed="handleStepConfirmed"
                />
              </div>
              
              <!-- 服务控制 -->
              <div v-else-if="activeTab === 'services'" class="content-panel">
                <ServiceControls 
                  :connection="connectionState.connectionInfo"
                  :container-status="containerStatus"
                  @action-started="handleActionStarted"
                  @action-completed="handleActionCompleted"
                  @status-updated="handleStatusUpdated"
                />
              </div>
              
              <!-- 配置管理 -->
              <div v-else-if="activeTab === 'configuration'" class="content-panel">
                <ConfigurationEditor 
                  :connection="connectionState.connectionInfo"
                  @configuration-updated="handleConfigurationUpdated"
                />
              </div>
              
              <!-- 版本管理 -->
              <div v-else-if="activeTab === 'versions'" class="content-panel">
                <VersionManager />
              </div>
              
              <!-- 日志查看 -->
              <div v-else-if="activeTab === 'logs'" class="content-panel">
                <LogViewer 
                  :connection="connectionState.connectionInfo"
                  container-name="sillytavern"
                />
              </div>
              
              <!-- 数据管理 -->
              <div v-else-if="activeTab === 'data'" class="content-panel">
                <DataManager 
                  :connection="connectionState.connectionInfo"
                  @export-completed="handleExportCompleted"
                  @import-completed="handleImportCompleted"
                />
              </div>
              
              <!-- 访问信息 -->
              <div v-else-if="activeTab === 'access'" class="content-panel">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import NavigationHeader from '../components/NavigationHeader.vue'
import ConnectionManager from '../components/ConnectionManager.vue'
import DeploymentWizard from '../components/sillytavern/DeploymentWizard.vue'
import InteractiveDeploymentWizard from '../components/sillytavern/InteractiveDeploymentWizard.vue'
import ServiceControls from '../components/sillytavern/ServiceControls.vue'
import ConfigurationEditor from '../components/sillytavern/ConfigurationEditor.vue'
import LogViewer from '../components/sillytavern/LogViewer.vue'
import DataManager from '../components/sillytavern/DataManager.vue'
import VersionManager from '../components/sillytavern/VersionManager.vue'
import AccessInfo from '../components/sillytavern/AccessInfo.vue'
import useConnectionManager from '../composables/useConnectionManager'
import { useSillyTavern } from '../composables/useSillyTavern'

// 连接管理
const { connectionState, connectionStatus } = useConnectionManager()

// SillyTavern 管理 - 现在使用统一连接管理器
const { 
  containerStatus, 
  isStatusLoading, 
  isPerformingAction: isActionLoading,
  systemInfo,
  isSystemValid,
  systemChecking,
  isDeploying,
  deploymentProgress,
  availableVersions,
  isLoadingVersions,
  versionError,
  getContainerStatus,
  performServiceAction,
  validateSystem,
  deployContainer,
  startInteractiveDeployment,
  confirmDeploymentStep,
  skipDeploymentStep,
  getAvailableVersions,
  initializeSillyTavernSubscriptions
} = useSillyTavern()

// 状态管理
const showConnectionModal = ref(false)
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
    name: '服务管理',
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
    id: 'versions',
    name: '版本管理',
    icon: 'fab fa-docker',
    disabled: false
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
  if (connectionState.connecting) return '连接中...'
  if (connectionState.isConnected) return `已连接: ${connectionState.connectionInfo?.host || ''}`
  if (connectionState.error) return `连接失败: ${connectionState.error}`
  return '未连接'
})

// 方法
const refreshStatus = async () => {
  if (connectionState.isConnected) {
    await getContainerStatus()
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

const getTabTitle = (tabId) => {
  const tab = tabs.value.find(t => t.id === tabId)
  return tab ? tab.name : '未知'
}

const checkForUpdates = () => {
  // TODO: 实现版本更新检查
  console.log('检查更新...')
}

const openService = () => {
  if (containerStatus.value && containerStatus.value.running) {
    const url = `http://${containerStatus.value.hostAddress || connectionState.connectionInfo?.host || 'localhost'}:${containerStatus.value.port || 8000}`
    window.open(url, '_blank')
  }
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
    
    // 检查是否是交互式部署请求（包含deploymentMode属性）
    if (deploymentConfig.deploymentMode) {
      console.log('启动交互式部署，模式:', deploymentConfig.deploymentMode)
      await startInteractiveDeployment(deploymentConfig)
      // 交互式部署会通过 handleDeploymentComplete 处理完成后的导航
    } else {
      // 传统部署方式
      await deployContainer(deploymentConfig)
      // 传统部署成功后直接切换到服务管理页面
      activeTab.value = 'services'
      await refreshStatus()
    }
    
    // 移除了立即切换标签页的逻辑，避免过早跳转
  } catch (error) {
    console.error('部署失败:', error)
    // 部署失败时保持在当前标签页，让用户查看错误信息
  }
}

const handleActionStarted = () => {
  // 处理操作开始
}

const handleActionCompleted = () => {
  refreshStatus()
}

const handleStatusUpdated = () => {
  refreshStatus()
}

const handleConfigurationUpdated = () => {
  console.log('配置已更新')
}

const handleExportCompleted = () => {
  console.log('导出完成')
}

const handleImportCompleted = () => {
  refreshStatus()
}

const handleDeploymentComplete = (success) => {
  console.log('部署完成，状态:', success)
  if (success) {
    // 部署成功后切换到服务管理页面
    activeTab.value = 'services'
    refreshStatus()
  } else {
    // 部署失败时保持在部署页面，让用户查看错误信息
    console.error('部署失败，保持在当前页面')
  }
}

// 处理获取版本信息事件
const handleGetVersions = () => {
  console.log('收到获取版本信息事件')
  getAvailableVersions()
}

// 处理步骤确认事件
const handleStepConfirmed = (confirmationData) => {
  console.log('父组件收到步骤确认事件:', confirmationData)
  const { stepId, confirmed, userInput } = confirmationData
  
  // 调用 SillyTavern 的确认方法
  if (confirmed) {
    console.log('调用确认方法:', stepId, userInput)
    confirmDeploymentStep(stepId, true, userInput)
  } else {
    console.log('调用跳过方法:', stepId)
    skipDeploymentStep(stepId, '用户选择跳过')
  }
}

// 生命周期
let statusInterval = null

onMounted(async () => {
  console.log('SillyTavernConsole onMounted - 连接状态:', connectionState.isConnected)
  
  if (connectionState.isConnected) {
    // 使用现有连接，初始化SillyTavern订阅
    try {
      // 确保SillyTavern订阅已初始化
      console.log('初始化SillyTavern订阅...')
      initializeSillyTavernSubscriptions()
      
      console.log('刷新状态...')
      await refreshStatus()
      
      // 获取可用的Docker版本信息
      console.log('准备获取版本信息...')
      setTimeout(() => {
        console.log('延迟调用getAvailableVersions')
        getAvailableVersions()
      }, 1000) // 延迟1秒确保WebSocket完全连接
      
    } catch (error) {
      console.error('获取SillyTavern状态失败:', error)
    }
    
    // 每30秒自动刷新状态
    statusInterval = setInterval(() => {
      if (connectionState.isConnected && !isStatusLoading.value) {
        refreshStatus()
      }
    }, 30000)
  } else {
    console.log('WebSocket未连接，无法获取版本信息')
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

.console-main {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.console-header {
  text-align: center;
  margin-bottom: 30px;
  color: white;
}

.console-title {
  font-size: 2.5rem;
  font-weight: 800;
  margin-bottom: 8px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.console-subtitle {
  font-size: 1.1rem;
  opacity: 0.9;
  margin: 0;
}

.console-content {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 0;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(15px);
}

/* 双边框布局 */
.console-dashboard {
  display: flex;
  min-height: 70vh;
  border-radius: 20px;
  overflow: hidden;
}

.sidebar {
  width: 25%;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.main-content {
  width: 75%;
  background: white;
  display: flex;
  flex-direction: column;
}

/* 边栏样式 */
.sidebar-section {
  flex: 1;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.sidebar-section:last-child {
  border-bottom: none;
}

.section-header {
  padding: 20px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
}

.section-icon {
  margin-right: 8px;
  font-size: 1.2rem;
}

.section-content {
  flex: 1;
  padding: 20px;
}

/* Docker信息样式 */
.docker-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.stat-label {
  font-size: 0.9rem;
  color: #6b7280;
}

.stat-value {
  font-weight: 500;
  color: #374151;
}

.stat-value.clickable {
  color: #3b82f6;
  cursor: pointer;
  text-decoration: underline;
}

.docker-loading {
  text-align: center;
  color: #6b7280;
  padding: 20px 0;
}

.docker-unavailable {
  text-align: center;
  color: #ef4444;
  padding: 20px 0;
}

.docker-unavailable i {
  font-size: 2rem;
  margin-bottom: 12px;
}

.docker-unavailable p {
  font-weight: 600;
  margin-bottom: 4px;
}

.docker-unavailable small {
  color: #6b7280;
}

.docker-not-deployed {
  text-align: center;
  color: #3b82f6;
  padding: 20px 0;
}

.docker-not-deployed i {
  font-size: 2rem;
  margin-bottom: 12px;
}

.docker-not-deployed p {
  font-weight: 600;
  margin-bottom: 4px;
}

.docker-not-deployed small {
  color: #6b7280;
}

/* 功能导航样式 */
.function-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: none;
  border: none;
  border-radius: 8px;
  color: #374151;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.nav-item:hover:not(:disabled) {
  background: #e5e7eb;
}

.nav-item-active {
  background: #3b82f6;
  color: white;
}

.nav-item-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.nav-item i {
  margin-right: 10px;
  width: 16px;
}

.nav-text {
  flex: 1;
}

.nav-badge {
  background: #ef4444;
  color: white;
  font-size: 0.7rem;
  padding: 2px 6px;
  border-radius: 10px;
}

/* 访问信息样式 */
.access-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.access-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.access-label {
  font-size: 0.9rem;
  color: #6b7280;
}

.access-value {
  font-weight: 500;
  color: #374151;
}

.status-running {
  color: #10b981;
}

.access-button {
  margin-top: 12px;
  width: 100%;
}

.access-unavailable {
  text-align: center;
  color: #6b7280;
  padding: 20px 0;
}

/* 主内容区样式 */
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30px;
  border-bottom: 1px solid #e5e7eb;
}

.content-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #374151;
  margin: 0;
}

.content-actions {
  display: flex;
  gap: 12px;
}

.content-body {
  flex: 1;
  padding: 30px;
}

.content-panel {
  height: 100%;
}

/* 连接状态和其他状态样式 */
.connection-prompt,
.connecting-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 40px;
}

.prompt-card,
.connecting-card {
  text-align: center;
  max-width: 400px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
}

.prompt-icon,
.connecting-spinner {
  font-size: 4rem;
  color: #3b82f6;
  margin-bottom: 20px;
}

.connecting-spinner i {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 按钮样式 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-secondary:hover:not(:disabled) {
  background: #e5e7eb;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.9rem;
}

.btn-lg {
  padding: 12px 24px;
  font-size: 1.1rem;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .console-dashboard {
    flex-direction: column;
  }
  
  .sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }
  
  .main-content {
    width: 100%;
  }
  
  .sidebar-section {
    flex: none;
  }
  
  .function-nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
  }
  
  .nav-item {
    flex: 1;
    min-width: 120px;
  }
}

@media (max-width: 768px) {
  .console-main {
    padding: 15px;
  }
  
  .console-title {
    font-size: 2rem;
  }
  
  .content-header {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }
  
  .content-body,
  .section-content {
    padding: 20px;
  }
}
</style>