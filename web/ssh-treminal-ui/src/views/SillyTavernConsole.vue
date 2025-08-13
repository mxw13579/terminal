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
            <!-- 第一部分：服务器信息展示 -->
            <div class="sidebar-section server-info">
              <div class="section-header">
                <h4 class="section-title">
                  <span class="section-icon">🖥️</span>
                  服务器信息
                </h4>
                <button
                  v-if="connectionState.isConnected"
                  @click="toggleServerInfoExpanded"
                  class="expand-toggle-btn"
                  :class="{ 'expanded': isServerInfoExpanded }"
                  style="width: 24px; height: 24px; border: 1px solid #999; background: #f5f5f5;"
                  title="展开/收缩详情"
                >
                  {{ isServerInfoExpanded ? '−' : '+' }}
                </button>
              </div>
              <div class="section-content">
                <!-- 服务器监控数据 -->
                <div v-if="systemStats" class="server-stats">

                  <!-- CPU使用率 -->
                  <div class="stat-item">
                    <!-- 第一行：标签 + 进度条 -->
                    <div class="stat-header">
                      <span class="stat-icon">⚡</span>
                      <span class="stat-label">CPU</span>
                    </div>
                    <div class="progress-bar">
                      <div class="progress-bar-inner" :style="{ width: getCpuUsage(systemStats) + '%' }"></div>
                    </div>
                    <!-- 第二行：百分比 -->
                    <div class="stat-details">
                      <span class="stat-percent">{{ getCpuUsage(systemStats).toFixed(1) }}%</span>
                    </div>
                  </div>

                  <!-- 内存使用率 -->
                  <div class="stat-item">
                    <!-- 第一行：标签 + 进度条 -->
                    <div class="stat-header">
                      <span class="stat-icon">💾</span>
                      <span class="stat-label">内存</span>
                    </div>
                    <div class="progress-bar">
                      <div class="progress-bar-inner" :style="{ width: (formatMemoryUsage(systemStats).percentage || 0) + '%' }"></div>
                    </div>
                    <!-- 第二行：百分比 + 容量信息 -->
                    <div class="stat-details">
                      <span class="stat-percent">{{ (formatMemoryUsage(systemStats).percentage || 0).toFixed(1) }}%</span>
                      <span v-if="formatMemoryUsage(systemStats).used !== '未知'" class="stat-usage">
                        {{ formatMemoryUsage(systemStats).used }}G / {{ formatMemoryUsage(systemStats).total }}G
                      </span>
                    </div>
                  </div>

                  <!-- 硬盘使用率 -->
                  <div class="stat-item">
                    <!-- 第一行：标签 + 进度条 -->
                    <div class="stat-header">
                      <span class="stat-icon">💿</span>
                      <span class="stat-label">硬盘</span>
                    </div>
                    <div class="progress-bar">
                      <div class="progress-bar-inner" :style="{ width: (formatDiskUsage(systemStats).percentage || 0) + '%' }"></div>
                    </div>
                    <!-- 第二行：百分比 + 容量信息 -->
                    <div class="stat-details">
                      <span class="stat-percent">{{ (formatDiskUsage(systemStats).percentage || 0).toFixed(1) }}%</span>
                      <span v-if="formatDiskUsage(systemStats).used !== '未知'" class="stat-usage">
                        {{ formatDiskUsage(systemStats).used }}G / {{ formatDiskUsage(systemStats).total }}G
                      </span>
                    </div>
                  </div>

                  <!-- 展开详情 -->
                  <div v-if="isServerInfoExpanded" class="expanded-details">
                    <!-- 基本信息 -->
                    <div class="detail-section">
                      <div class="section-subtitle">基本信息</div>
                      <div class="detail-item">
                        <span class="detail-label">CPU 型号</span>
                        <span class="detail-value" :title="systemStats.cpuModel">{{ systemStats.cpuModel || '获取中...' }}</span>
                      </div>
                      <div class="detail-item">
                        <span class="detail-label">系统运行时长</span>
                        <span class="detail-value">{{ systemStats.uptime || '获取中...' }}</span>
                      </div>
                      <div class="detail-item">
                        <span class="detail-label">网络 I/O</span>
                        <span class="detail-value">
                          接收: {{ systemStats.netRx || '0 B' }} | 发送: {{ systemStats.netTx || '0 B' }}
                        </span>
                      </div>
                    </div>

                    <!-- Docker 容器信息 -->
                    <div v-if="terminalDockerContainers && terminalDockerContainers.length" class="detail-section">
                      <div class="section-subtitle">Docker 容器</div>
                      <div class="docker-list">
                        <div v-for="container in terminalDockerContainers" :key="container.id" class="docker-item">
                          <div class="docker-item-header">
                            <span class="docker-name" :title="container.name">{{ container.name }}</span>
                            <span class="docker-status" :class="container.status.includes('Up') ? 'up' : 'exited'">
                              {{ container.status.split(' ')[0] }}
                            </span>
                          </div>
                          <div class="docker-item-body">
                            <span>CPU: {{ container.cpuPerc }}</span>
                            <span>内存: {{ container.memPerc }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 服务器连接中或无数据状态 -->
                <div v-else-if="!connectionState.isConnected" class="server-disconnected">
                  <i class="fas fa-unlink"></i>
                  <p>服务器未连接</p>
                  <small class="text-muted">请先连接到服务器</small>
                </div>

                <!-- 加载状态 -->
                <div v-else-if="!isMonitoringActive" class="server-loading">
                  <i class="fas fa-spinner fa-spin"></i>
                  <p>正在连接监控服务...</p>
                </div>

                <!-- 监控中但未收到数据 -->
                <div v-else class="server-loading">
                  <i class="fas fa-spinner fa-spin"></i>
                  <p>获取服务器信息...</p>
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
                    <p>SillyTavern未部署</p>
                    <small class="text-muted">{{ containerStatus.error }}</small>
                  </div>
                  <div v-else-if="containerStatus.exists && !containerStatus.running">
                    <p>服务已停止</p>
                    <small class="text-muted">容器存在但未运行</small>
                  </div>
                  <div v-else-if="!containerStatus.exists">
                    <p>SillyTavern未部署</p>
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
                  :server-stats="systemStats"
                  :docker-containers="terminalDockerContainers"
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
                  :is-performing-action="isActionLoading"
                  :current-action="currentActionType"
                  @service-action="handleServiceAction"
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
import { useTerminal } from '../composables/useTerminal'

// 连接管理
const { connectionState, connectionStatus, getStompClient } = useConnectionManager()

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

// 服务器监控数据状态
const systemStats = ref(null)
const terminalDockerContainers = ref([])
const isMonitoringActive = ref(false)
const isServerInfoExpanded = ref(false)

// 状态管理
const showConnectionModal = ref(false)
const activeTab = ref('deployment')
const currentActionType = ref('')

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

const handleServiceAction = async (action, options = {}) => {
  try {
    console.log('执行服务操作:', action, options)
    currentActionType.value = getActionDisplayName(action)

    await performServiceAction(action, options)

    // 操作完成后刷新状态
    await refreshStatus()

    console.log(`服务操作 ${action} 完成`)
  } catch (error) {
    console.error(`服务操作 ${action} 失败:`, error)
  } finally {
    currentActionType.value = ''
  }
}

// 启动服务器监控
const startServerMonitoring = () => {
  const stompClient = getStompClient()

  if (!stompClient || !stompClient.connected) {
    console.warn('⚠️ STOMP客户端未连接，无法启动监控')
    setTimeout(() => startServerMonitoring(), 1000) // 1秒后重试
    return
  }

  console.log('🚀 开始启动服务器监控...')

  // 尝试从URL中提取会话ID
  let sessionId = 'unknown'
  if (stompClient.webSocket?.url) {
    const urlMatch = stompClient.webSocket.url.match(/\/ws\/terminal\/(\w+)\//)
    if (urlMatch) {
      sessionId = urlMatch[1]
    }
  }
  console.log('🆔 检测到的会话ID:', sessionId)

  // 首先订阅监控数据和错误消息
  try {
    // 订阅监控数据 - 使用正确的路由
    console.log('📡 订阅监控数据: /user/queue/monitor')
    const monitorSub = stompClient.subscribe('/user/queue/monitor', (message) => {
      try {
        const data = JSON.parse(message.body)
        handleMonitorUpdate(data)
      } catch (e) {
        console.error('❌ 处理监控数据失败:', e)
      }
    })

    // 订阅错误消息
    console.log('🚨 订阅错误消息: /user/queue/errors')
    const errorSub = stompClient.subscribe('/user/queue/errors', (message) => {
      try {
        const data = JSON.parse(message.body)
        console.error('🚨 收到监控错误:', data)
      } catch (e) {
        console.error('❌ 处理监控错误失败:', e)
      }
    })

    console.log('✅ 监控数据订阅成功:', {
      monitorSub: !!monitorSub,
      errorSub: !!errorSub
    })

    // 发送启动监控请求
    const startMessage = {
      frequencySeconds: 5 // 每5秒更新一次
    }
    console.log('📤 发送监控启动请求:', startMessage)

    stompClient.publish({
      destination: '/app/monitor/start',
      body: JSON.stringify(startMessage)
    })

    isMonitoringActive.value = true
    console.log('🎯 服务器监控启动请求已发送')

  } catch (error) {
    console.error('❌ 启动服务器监控失败:', error)
    // 3秒后重试
    setTimeout(() => startServerMonitoring(), 3000)
  }
}

// 处理监控数据更新
const handleMonitorUpdate = (data) => {
  if (data.type === 'monitor_update') {
    systemStats.value = data.payload
    terminalDockerContainers.value = data.payload.dockerContainers || []
  } else {
    console.log('❌ 监控数据类型不匹配:', data.type)
  }
}

// 格式化CPU使用率
const getCpuUsage = (stats) => {
  if (!stats) return 0
  return typeof stats.cpuUsage === 'number' ? stats.cpuUsage : 0
}

// 格式化内存和磁盘使用情况
const formatMemoryUsage = (stats) => {


  if (!stats || stats.memUsage === undefined) {
    return { used: '0', total: '0', percentage: 0 }
  }

  // 如果是数字，说明只有百分比信息（旧格式兼容）
  if (typeof stats.memUsage === 'number') {
    return { used: '未知', total: '未知', percentage: stats.memUsage }
  }

  // 如果是对象，说明有详细信息（新格式）
  const memData = stats.memUsage

  if (!memData || typeof memData !== 'object') {
    return { used: '0', total: '0', percentage: 0 }
  }

  return {
    used: (memData.used / 1024).toFixed(1), // MB->GB (free -m返回MB)
    total: (memData.total / 1024).toFixed(1), // MB->GB (free -m返回MB)
    percentage: memData.percentage || 0
  }
}

const formatDiskUsage = (stats) => {

  if (!stats || stats.diskUsage === undefined) {
    return { used: '0', total: '0', percentage: 0 }
  }

  // 如果是数字，说明只有百分比信息（旧格式兼容）
  if (typeof stats.diskUsage === 'number') {
    return { used: '未知', total: '未知', percentage: stats.diskUsage }
  }

  // 如果是对象，说明有详细信息（新格式）
  const diskData = stats.diskUsage

  if (!diskData || typeof diskData !== 'object') {
    return { used: '0', total: '0', percentage: 0 }
  }

  return {
    used: (diskData.used / 1024 / 1024).toFixed(1), // KB->MB->GB (df -P返回KB)
    total: (diskData.total / 1024 / 1024).toFixed(1), // KB->MB->GB (df -P返回KB)
    percentage: diskData.percentage || 0
  }
}

// 切换服务器信息展开/收缩状态
const toggleServerInfoExpanded = () => {
  isServerInfoExpanded.value = !isServerInfoExpanded.value
}

// 调试方法：手动触发监控测试
const debugMonitoring = () => {
  console.log('=== 调试监控状态 ===')
  console.log('连接状态:', connectionState.isConnected)
  console.log('监控活动状态:', isMonitoringActive.value)
  console.log('系统统计数据:', systemStats.value)
  console.log('Docker容器数据:', terminalDockerContainers.value)

  const stompClient = getStompClient()
  console.log('STOMP客户端:', stompClient)
  console.log('STOMP连接状态:', stompClient?.connected)
  console.log('WebSocket URL:', stompClient?.webSocket?.url)

  if (stompClient && stompClient.connected) {
    console.log('手动发送监控启动请求...')

    // 尝试直接订阅并发送测试
    const testSub = stompClient.subscribe('/user/queue/monitor', (message) => {
      console.log('测试订阅收到消息:', message)
      try {
        const data = JSON.parse(message.body)
        console.log('测试订阅解析数据:', data)
      } catch (e) {
        console.error('测试订阅解析失败:', e)
      }
    })

    console.log('测试订阅创建:', !!testSub)

    // 发送监控启动请求
    stompClient.publish({
      destination: '/app/monitor/start',
      body: JSON.stringify({ frequencySeconds: 3 })
    })

    // 也尝试直接发送到用户队列（测试）
    setTimeout(() => {
      console.log('发送测试消息...')
      try {
        stompClient.publish({
          destination: '/user/queue/monitor',
          body: JSON.stringify({
            type: 'test_message',
            payload: { message: 'This is a test' }
          })
        })
      } catch (error) {
        console.error('发送测试消息失败:', error)
      }
    }, 1000)
  }
}

const getActionDisplayName = (action) => {
  const actionNames = {
    'start': '启动容器',
    'stop': '停止容器',
    'restart': '重启容器',
    'upgrade': '升级容器'
  }
  return actionNames[action] || action
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

      // 启动服务器监控以获取系统信息
      console.log('启动服务器监控...')
      setTimeout(() => {
        startServerMonitoring()
      }, 2000) // 延迟2秒确保SSH连接完全建立

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
  console.log('SillyTavernConsole 组件即将卸载，清理资源...')

  // 清理定时器
  if (statusInterval) {
    clearInterval(statusInterval)
    console.log('已清理状态刷新定时器')
  }

  // 停止监控服务
  if (isMonitoringActive.value) {
    console.log('停止监控服务...')
    const stompClient = getStompClient()
    if (stompClient && stompClient.connected) {
      try {
        stompClient.publish({
          destination: '/app/monitor/stop',
          body: JSON.stringify({})
        })
        console.log('已发送监控停止请求')
      } catch (error) {
        console.warn('发送监控停止请求失败:', error)
      }
    }
    isMonitoringActive.value = false
  }

  // 清理监控数据
  systemStats.value = null
  terminalDockerContainers.value = []

  console.log('SillyTavernConsole 资源清理完成')
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
  display: flex;
  justify-content: space-between;
  align-items: center;
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

/* 服务器信息统一样式 */
.server-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-item {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
  display: block !important;
  width: 100%;
}

.stat-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.stat-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  width: 100%;
}

.stat-icon {
  font-size: 14px;
  margin-right: 6px;
}

.stat-label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.progress-bar {
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
  margin: 6px 0;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.1);
  width: 100% !important;
  display: block !important;
  clear: both;
}

.progress-bar-inner {
  height: 100%;
  background: linear-gradient(90deg, #10b981 0%, #f59e0b 70%, #ef4444 90%);
  border-radius: 2px;
  transition: width 0.3s ease;
  position: relative;
  overflow: hidden;
  display: block;
}

.progress-bar-inner::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.3) 50%, transparent 100%);
  animation: shimmer 2s infinite;
}

/* 删除了重复的shimmer动画定义 */

.stat-details {
  display: flex !important;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  clear: both;
  width: 100%;
  flex-wrap: nowrap;
}

.stat-percent {
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
}

.stat-usage {
  font-size: 10px;
  color: #6b7280;
  font-weight: 500;
}

/* 展开详情样式 */
.expanded-details {
  margin-top: 16px;
  animation: expandIn 0.3s ease-out;
}

@keyframes expandIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.detail-section {
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f1f5f9;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-label {
  font-size: 11px;
  color: #6b7280;
  font-weight: 500;
}

.detail-value {
  font-size: 11px;
  color: #374151;
  font-weight: 500;
  max-width: 60%;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.docker-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.docker-item {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 11px;
}

.docker-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.docker-name {
  font-weight: 500;
  color: #374151;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.docker-status {
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 3px;
  font-weight: 600;
  text-transform: uppercase;
}

.docker-status.up {
  background: #d4edda;
  color: #155724;
}

.docker-status.exited {
  background: #f8d7da;
  color: #721c24;
}

.docker-item-body {
  display: flex;
  justify-content: space-between;
  color: #6b7280;
  font-size: 10px;
}

.server-disconnected,
.server-loading {
  text-align: center;
  color: #6b7280;
  padding: 20px 0;
}

.server-disconnected i,
.server-loading i {
  font-size: 1.8rem;
  margin-bottom: 10px;
  display: block;
}

.server-disconnected p,
.server-loading p {
  font-weight: 500;
  margin: 8px 0 4px 0;
}

.stat-value.small-text {
  font-size: 10px;
  line-height: 1.2;
}

/* 展开/收缩按钮样式 */
.expand-toggle-btn {
  background: none;
  border: none;
  color: #6b7280;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.expand-toggle-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #374151;
}

.expand-toggle-btn i {
  transition: transform 0.2s ease;
}

.expand-toggle-btn.expanded i {
  transform: rotate(180deg);
}

/* 删除了重复的最小化和展开视图样式，现在使用统一的UI设计 */

/* 进度条统一样式（已清理重复定义）*/

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
