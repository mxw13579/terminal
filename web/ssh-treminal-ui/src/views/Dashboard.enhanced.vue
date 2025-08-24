<template>
  <div class="dashboard">
    <!-- 导航头部 -->
    <NavigationHeader />
    
    <!-- 主要内容区域 -->
    <main class="dashboard-main">
      <!-- 错误边界包装关键区域 -->
      <ErrorBoundary>
        <template #help>
          <p>如果问题持续，请检查网络连接或联系技术支持。</p>
        </template>
        
        <!-- 头部区域 -->
        <section class="dashboard-hero" aria-labelledby="dashboard-title">
          <h1 id="dashboard-title" class="dashboard-title">
            终端管理中心
          </h1>
          <p class="dashboard-subtitle">
            统一管理您的服务器和应用程序
          </p>
        </section>

        <!-- 连接管理区域 -->
        <section class="connection-section" aria-labelledby="connection-title">
          <h2 id="connection-title" class="sr-only">连接管理</h2>
          <ConnectionManager />
        </section>

        <!-- 服务选择区域 -->
        <section class="services-section" aria-labelledby="services-title">
          <h2 id="services-title" class="section-title">选择服务</h2>
          
          <!-- 使用优化的加载状态 -->
          <LoadingState 
            :is-loading="servicesLoading"
            :error="servicesError"
            :is-empty="services.length === 0"
            empty-title="暂无可用服务"
            empty-description="请确保服务器连接正常"
            :retry="loadServices"
            skeleton-type="card"
          >
            <div class="service-cards" role="list">
              <ServiceCard
                v-for="service in services"
                :key="service.id"
                :service="service"
                :disabled="!connectionState.isConnected"
                :loading="service.loading"
                @click="navigateToService(service)"
                role="listitem"
              />
            </div>
          </LoadingState>
        </section>

        <!-- 统计信息区域 -->
        <section v-if="showStats" class="stats-section" aria-labelledby="stats-title">
          <h2 id="stats-title" class="section-title">系统概览</h2>
          <DashboardStats :stats="systemStats" />
        </section>

        <!-- 平台特色区域 -->
        <section class="features-section" aria-labelledby="features-title">
          <h2 id="features-title" class="section-title">平台特色</h2>
          <div class="highlight-grid" role="list">
            <FeatureHighlight
              v-for="feature in features"
              :key="feature.id"
              :feature="feature"
              role="listitem"
            />
          </div>
        </section>
      </ErrorBoundary>
    </main>

    <!-- 状态栏 -->
    <StatusBar 
      :connection-status="connectionStatus"
      :performance-metrics="performanceMetrics"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, provide } from 'vue'
import { useRouter } from 'vue-router'

// 组件导入
import NavigationHeader from '@/components/NavigationHeader.vue'
import ConnectionManager from '@/components/ConnectionManager.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorBoundary from '@/components/ui/ErrorBoundary.vue'
import ServiceCard from '@/components/ServiceCard.vue'
import DashboardStats from '@/components/dashboard/DashboardStats.vue'
import FeatureHighlight from '@/components/dashboard/FeatureHighlight.vue'
import StatusBar from '@/components/ui/StatusBar.vue'

// Composables
import useConnectionManager from '@/composables/useConnectionManager'
import { useAsyncState, createCache } from '@/composables/useReactiveState'
import { useToast } from '@/stores/toast'

const router = useRouter()
const toast = useToast()
const { connectionState, connectionStatus, performanceMetrics } = useConnectionManager()

// 创建服务缓存
const serviceCache = createCache({
  maxSize: 50,
  ttl: 5 * 60 * 1000 // 5分钟缓存
})

// 异步加载服务列表
const {
  data: services,
  loading: servicesLoading,
  error: servicesError,
  execute: loadServices
} = useAsyncState(
  async () => {
    const cacheKey = 'dashboard-services'
    const cached = serviceCache.get(cacheKey)
    
    if (cached) {
      return cached
    }

    // 模拟服务数据加载
    const servicesData = [
      {
        id: 'sillytavern',
        name: 'SillyTavern 控制台',
        description: 'AI 角色扮演与对话平台',
        icon: '🤖',
        route: '/sillytavern',
        features: [
          { icon: '🚀', text: '一键部署' },
          { icon: '⚙️', text: '在线配置' },
          { icon: '📊', text: '实时监控' },
          { icon: '📁', text: '数据管理' }
        ],
        status: 'available',
        loading: false
      },
      {
        id: 'terminal',
        name: 'SSH 终端',
        description: '远程服务器命令行管理',
        icon: '💻',
        route: '/terminal',
        features: [
          { icon: '🔒', text: '安全连接' },
          { icon: '📁', text: '文件传输' },
          { icon: '📊', text: '系统监控' },
          { icon: '⚡', text: '实时操作' }
        ],
        status: 'available',
        loading: false
      }
    ]

    // 缓存结果
    serviceCache.set(cacheKey, servicesData)
    return servicesData
  },
  { immediate: true }
)

// 系统统计信息
const {
  data: systemStats,
  loading: statsLoading,
  execute: loadStats
} = useAsyncState(
  async () => {
    if (!connectionState.isConnected) return null
    
    // 这里可以调用实际的API获取系统统计
    return {
      connections: 1,
      uptime: '2h 34m',
      memory: '512 MB',
      cpu: '15%'
    }
  }
)

// 平台特色数据
const features = [
  {
    id: 'security',
    icon: '🛡️',
    title: '安全可靠',
    description: '采用SSH加密连接，确保数据传输安全'
  },
  {
    id: 'easy',
    icon: '🚀',
    title: '简单易用',
    description: '无需命令行知识，一键式操作管理'
  },
  {
    id: 'monitoring',
    icon: '📊',
    title: '实时监控',
    description: '实时查看服务状态和系统资源使用情况'
  },
  {
    id: 'management',
    icon: '🔧',
    title: '完整管理',
    description: '从部署到配置，提供完整的管理解决方案'
  }
]

// 计算属性
const showStats = computed(() => 
  connectionState.isConnected && systemStats.value
)

// 导航处理
const navigateToService = (service) => {
  if (!connectionState.isConnected) {
    toast.warning('请先连接到服务器')
    return
  }

  if (service.loading) {
    return
  }

  // 添加加载状态
  service.loading = true
  
  router.push(service.route).catch(error => {
    console.error('Navigation error:', error)
    toast.error('页面跳转失败')
  }).finally(() => {
    service.loading = false
  })
}

// 监听连接状态变化
const handleConnectionChange = (newStatus) => {
  if (newStatus === 'connected') {
    toast.connectionToast.connected(connectionState.connectionInfo?.host)
    loadStats()
  } else if (newStatus === 'disconnected') {
    toast.connectionToast.disconnected(connectionState.connectionInfo?.host)
  }
}

// 生命周期
onMounted(() => {
  // 如果已连接，加载统计信息
  if (connectionState.isConnected) {
    loadStats()
  }
})

// 提供上下文给子组件
provide('dashboardContext', {
  services,
  connectionState,
  navigateToService
})

// 错误处理
const handleError = (error) => {
  console.error('Dashboard error:', error)
  toast.error('页面加载出现问题，请刷新重试')
}
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: linear-gradient(135deg, 
    var(--color-primary-600) 0%, 
    var(--color-secondary-700) 100%);
  position: relative;
  overflow: visible;
}

/* 背景装饰 */
.dashboard::before {
  content: '';
  position: absolute;
  inset: 0;
  background: 
    radial-gradient(circle at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(120, 119, 198, 0.2) 0%, transparent 50%);
  pointer-events: none;
  z-index: 0;
}

.dashboard-main {
  position: relative;
  z-index: 1;
  padding: var(--space-10) var(--space-5) var(--space-20);
  max-width: 1400px;
  margin: 0 auto;
}

/* 头部区域 */
.dashboard-hero {
  text-align: center;
  margin-bottom: var(--space-10);
}

.dashboard-title {
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--font-weight-bold);
  background: linear-gradient(135deg, #ffffff 0%, #f0f0f0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: var(--space-4);
  text-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.dashboard-subtitle {
  font-size: clamp(1rem, 2vw, 1.3rem);
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  font-weight: var(--font-weight-light);
}

/* 区域样式 */
.connection-section,
.services-section,
.stats-section,
.features-section {
  margin-bottom: var(--space-12);
}

.section-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: white;
  text-align: center;
  margin-bottom: var(--space-8);
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* 服务卡片网格 */
.service-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(450px, 100%), 1fr));
  gap: var(--space-8);
  max-width: 1000px;
  margin: 0 auto;
}

/* 特色功能网格 */
.highlight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: var(--space-6);
  max-width: 1000px;
  margin: 0 auto;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .service-cards {
    grid-template-columns: 1fr;
    max-width: 600px;
  }
}

@media (max-width: 768px) {
  .dashboard-main {
    padding: var(--space-5) var(--space-4) var(--space-10);
  }
  
  .connection-section,
  .services-section,
  .stats-section,
  .features-section {
    margin-bottom: var(--space-8);
  }
  
  .section-title {
    font-size: var(--font-size-xl);
    margin-bottom: var(--space-6);
  }
}

@media (max-width: 480px) {
  .dashboard-main {
    padding: var(--space-4) var(--space-3) var(--space-8);
  }
  
  .dashboard-hero {
    margin-bottom: var(--space-8);
  }
  
  .highlight-grid {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  .dashboard-title {
    color: white;
    -webkit-text-fill-color: white;
  }
  
  .section-title {
    text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.8);
  }
}

/* 减少动画 */
@media (prefers-reduced-motion: reduce) {
  .dashboard::before {
    background: none;
  }
  
  .dashboard-title {
    background: none;
    color: white;
    -webkit-text-fill-color: white;
  }
}

/* 暗色主题适配 */
[data-theme="dark"] .dashboard {
  background: linear-gradient(135deg, 
    var(--color-secondary-900) 0%, 
    var(--color-secondary-800) 100%);
}

/* 焦点管理 */
.dashboard-main:focus {
  outline: none;
}

/* 跳过链接（无障碍） */
.skip-link {
  position: absolute;
  top: -40px;
  left: 6px;
  background: var(--color-primary);
  color: white;
  padding: 8px;
  text-decoration: none;
  z-index: 1000;
  border-radius: var(--radius-base);
}

.skip-link:focus {
  top: 6px;
}
</style>