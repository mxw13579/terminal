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
                <div v-if="containerStatus" class="docker-stats">
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
                    <span class="access-label">服务地址</span>
                    <span class="access-value">{{ connectionState.connectionInfo?.host || 'localhost' }}:{{ containerStatus.port || '8000' }}</span>
                  </div>
                  <div class="access-item">
                    <span class="access-label">协议</span>
                    <span class="access-value">HTTP</span>
                  </div>
                  <div class="access-item">
                    <span class="access-label">状态</span>
                    <span class="access-value status-running">🟢 运行中</span>
                  </div>
                  <button @click="openService" class="btn btn-primary btn-sm access-button">
                    <i class="fas fa-external-link-alt"></i>
                    访问服务
                  </button>
                </div>
                <div v-else class="access-unavailable">
                  <i class="fas fa-times-circle"></i>
                  <p>服务未运行</p>
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
import ServiceControls from '../components/sillytavern/ServiceControls.vue'
import ConfigurationEditor from '../components/sillytavern/ConfigurationEditor.vue'
import LogViewer from '../components/sillytavern/LogViewer.vue'
import DataManager from '../components/sillytavern/DataManager.vue'
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
  getContainerStatus,
  performServiceAction,
  validateSystem,
  deployContainer,
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
    const url = `http://${connectionState.connectionInfo?.host || 'localhost'}:${containerStatus.value.port || 8000}`
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
    await deployContainer(deploymentConfig)
    activeTab.value = 'services'
  } catch (error) {
    console.error('部署失败:', error)
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

// 生命周期
let statusInterval = null

onMounted(async () => {
  if (connectionState.isConnected) {
    // 使用现有连接，初始化SillyTavern订阅
    try {
      // 确保SillyTavern订阅已初始化
      initializeSillyTavernSubscriptions()
      await refreshStatus()
    } catch (error) {
      console.error('获取SillyTavern状态失败:', error)
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