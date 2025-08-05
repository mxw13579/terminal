<template>
  <div class="version-manager">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">
          <i class="fab fa-docker me-2"></i>
          Docker版本管理
        </h5>
        <small class="text-muted">查看当前版本，升级到最新版本或清理旧镜像</small>
      </div>
      
      <div class="card-body">
        <!-- 版本信息加载中 -->
        <div v-if="loading" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">加载版本信息...</span>
          </div>
          <p class="mt-2 text-muted">获取版本信息中...</p>
        </div>

        <!-- 版本信息展示 -->
        <div v-else-if="versionInfo" class="version-info">
          <!-- 当前版本信息 -->
          <div class="row mb-4">
            <div class="col-md-6">
              <div class="version-card current-version">
                <div class="version-header">
                  <i class="fas fa-tag me-2"></i>
                  <span class="version-title">当前版本</span>
                </div>
                <div class="version-content">
                  <div class="version-number">{{ versionInfo.currentVersion }}</div>
                  <div class="version-date" v-if="versionInfo.currentVersionReleaseDate">
                    发布时间: {{ formatDate(versionInfo.currentVersionReleaseDate) }}
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-md-6">
              <div class="version-card latest-version">
                <div class="version-header">
                  <i class="fas fa-rocket me-2"></i>
                  <span class="version-title">最新版本</span>
                  <span v-if="versionInfo.hasUpdate" class="update-badge">有更新</span>
                </div>
                <div class="version-content">
                  <div class="version-number">{{ versionInfo.latestVersion || '检查中...' }}</div>
                  <div class="version-date" v-if="versionInfo.latestVersionReleaseDate">
                    发布时间: {{ formatDate(versionInfo.latestVersionReleaseDate) }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 可用版本列表 -->
          <div class="available-versions mb-4" v-if="versionInfo.availableVersions && versionInfo.availableVersions.length > 0">
            <h6 class="fw-bold text-primary mb-3">
              <i class="fas fa-list me-2"></i>
              可用版本（最新3个）
            </h6>
            <div class="row">
              <div 
                v-for="version in versionInfo.availableVersions" 
                :key="version"
                class="col-md-4 mb-2"
              >
                <div class="version-item" :class="{ 'current': version === versionInfo.currentVersion }">
                  <div class="version-info-item">
                    <span class="version-tag">{{ version }}</span>
                    <span v-if="version === versionInfo.currentVersion" class="current-indicator">当前</span>
                  </div>
                  <button 
                    v-if="version !== versionInfo.currentVersion"
                    @click="upgradeVersion(version)"
                    :disabled="isUpgrading"
                    class="btn btn-sm btn-outline-primary"
                  >
                    <i class="fas fa-download me-1"></i>
                    升级
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 更新描述 -->
          <div v-if="versionInfo.updateDescription" class="update-description mb-4">
            <h6 class="fw-bold text-primary mb-2">
              <i class="fas fa-info-circle me-2"></i>
              更新说明
            </h6>
            <div class="description-content">
              {{ versionInfo.updateDescription }}
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="version-actions">
            <div class="row">
              <div class="col-md-6">
                <button 
                  @click="refreshVersionInfo"
                  :disabled="loading || isUpgrading"
                  class="btn btn-outline-secondary me-2"
                >
                  <i :class="loading ? 'fas fa-spinner fa-spin' : 'fas fa-sync-alt'" class="me-1"></i>
                  刷新版本信息
                </button>
                
                <button 
                  v-if="versionInfo.hasUpdate"
                  @click="upgradeToLatest"
                  :disabled="isUpgrading"
                  class="btn btn-success"
                >
                  <span v-if="isUpgrading" class="spinner-border spinner-border-sm me-2" role="status"></span>
                  <i v-else class="fas fa-arrow-up me-1"></i>
                  {{ isUpgrading ? '升级中...' : '升级到最新版本' }}
                </button>
              </div>
              
              <div class="col-md-6 text-end">
                <button 
                  @click="cleanupImages"
                  :disabled="isCleaningUp"
                  class="btn btn-outline-warning"
                >
                  <span v-if="isCleaningUp" class="spinner-border spinner-border-sm me-2" role="status"></span>
                  <i v-else class="fas fa-trash-alt me-1"></i>
                  {{ isCleaningUp ? '清理中...' : '清理旧镜像' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="errorMessage" class="alert alert-danger" role="alert">
          <i class="fas fa-exclamation-circle me-2"></i>
          {{ errorMessage }}
          <button @click="refreshVersionInfo" class="btn btn-outline-danger btn-sm ms-2">
            重试
          </button>
        </div>

        <!-- 升级进度 -->
        <div v-if="upgradeProgress" class="upgrade-progress mt-3">
          <h6 class="fw-bold mb-2">升级进度</h6>
          <div class="progress mb-2">
            <div 
              class="progress-bar progress-bar-striped progress-bar-animated" 
              :style="{ width: getProgressWidth() + '%' }"
              role="progressbar"
            ></div>
          </div>
          <div class="progress-text">{{ upgradeProgress }}</div>
        </div>

        <!-- 最后检查时间 -->
        <div v-if="versionInfo && versionInfo.lastChecked" class="last-checked mt-3 text-muted">
          <small>
            <i class="fas fa-clock me-1"></i>
            最后检查: {{ formatDate(versionInfo.lastChecked) }}
          </small>
        </div>

        <!-- 成功消息 -->
        <div v-if="successMessage" class="alert alert-success mt-3" role="alert">
          <i class="fas fa-check-circle me-2"></i>
          {{ successMessage }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'

export default {
  name: 'VersionManager',
  setup() {
    const { stompClient, isConnected } = useSillyTavern()
    
    const loading = ref(true)
    const isUpgrading = ref(false)
    const isCleaningUp = ref(false)
    const versionInfo = ref(null)
    const upgradeProgress = ref('')
    const errorMessage = ref('')
    const successMessage = ref('')
    
    // WebSocket subscriptions
    let versionSubscription = null
    let upgradeSubscription = null
    let upgradeProgressSubscription = null
    let cleanupSubscription = null
    
    const formatDate = (dateString) => {
      if (!dateString) return ''
      const date = new Date(dateString)
      return date.toLocaleString('zh-CN')
    }
    
    const getProgressWidth = () => {
      const progress = upgradeProgress.value
      if (progress.includes('检查')) return 10
      if (progress.includes('停止')) return 30
      if (progress.includes('拉取')) return 50
      if (progress.includes('更新')) return 70
      if (progress.includes('启动')) return 90
      if (progress.includes('完成')) return 100
      return 20
    }
    
    const refreshVersionInfo = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket 连接未建立'
        return
      }
      
      loading.value = true
      errorMessage.value = ''
      successMessage.value = ''
      
      try {
        stompClient.value.send('/app/sillytavern/get-version-info', {}, JSON.stringify({}))
      } catch (error) {
        errorMessage.value = '发送请求失败: ' + error.message
        loading.value = false
      }
    }
    
    const upgradeVersion = (targetVersion) => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket 连接未建立'
        return
      }
      
      if (confirm(`确定要升级到版本 ${targetVersion} 吗？此操作会重启容器。`)) {
        isUpgrading.value = true
        upgradeProgress.value = ''
        errorMessage.value = ''
        successMessage.value = ''
        
        const request = {
          targetVersion: targetVersion,
          containerName: 'sillytavern'
        }
        
        try {
          stompClient.value.send('/app/sillytavern/upgrade-version', {}, JSON.stringify(request))
        } catch (error) {
          errorMessage.value = '发送升级请求失败: ' + error.message
          isUpgrading.value = false
        }
      }
    }
    
    const upgradeToLatest = () => {
      if (versionInfo.value && versionInfo.value.latestVersion) {
        upgradeVersion(versionInfo.value.latestVersion)
      }
    }
    
    const cleanupImages = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket 连接未建立'
        return
      }
      
      if (confirm('确定要清理未使用的Docker镜像吗？这将删除不再需要的旧版本镜像。')) {
        isCleaningUp.value = true
        errorMessage.value = ''
        successMessage.value = ''
        
        try {
          stompClient.value.send('/app/sillytavern/cleanup-images', {}, JSON.stringify({}))
        } catch (error) {
          errorMessage.value = '发送清理请求失败: ' + error.message
          isCleaningUp.value = false
        }
      }
    }
    
    // WebSocket message handlers
    const handleVersionInfoResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        loading.value = false
        
        if (response.success && response.payload) {
          versionInfo.value = response.payload
          errorMessage.value = ''
        } else {
          errorMessage.value = response.error || '获取版本信息失败'
        }
      } catch (error) {
        console.error('处理版本信息响应失败:', error)
        errorMessage.value = '处理版本信息响应失败'
        loading.value = false
      }
    }
    
    const handleUpgradeProgressResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        if (response.type === 'version-upgrade-progress' && response.message) {
          upgradeProgress.value = response.message
        }
      } catch (error) {
        console.error('处理升级进度响应失败:', error)
      }
    }
    
    const handleUpgradeResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        isUpgrading.value = false
        upgradeProgress.value = ''
        
        if (response.success) {
          successMessage.value = response.message || '版本升级成功'
          // 刷新版本信息
          setTimeout(() => {
            refreshVersionInfo()
          }, 2000)
        } else {
          errorMessage.value = response.error || response.message || '版本升级失败'
        }
      } catch (error) {
        console.error('处理升级响应失败:', error)
        errorMessage.value = '处理升级响应失败'
        isUpgrading.value = false
      }
    }
    
    const handleCleanupResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        isCleaningUp.value = false
        
        if (response.success) {
          successMessage.value = response.message || '镜像清理成功'
        } else {
          errorMessage.value = response.error || response.message || '镜像清理失败'
        }
      } catch (error) {
        console.error('处理清理响应失败:', error)
        errorMessage.value = '处理清理响应失败'
        isCleaningUp.value = false
      }
    }
    
    onMounted(() => {
      if (isConnected.value && stompClient.value) {
        const sessionId = stompClient.value.ws._websocket?.extensions?.sessionId || 
                          Math.random().toString(36).substr(2, 9)
        
        // 订阅各种响应
        versionSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/version-info-user${sessionId}`,
          handleVersionInfoResponse
        )
        
        upgradeProgressSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/version-upgrade-progress-user${sessionId}`,
          handleUpgradeProgressResponse
        )
        
        upgradeSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/version-upgrade-user${sessionId}`,
          handleUpgradeResponse
        )
        
        cleanupSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/cleanup-images-user${sessionId}`,
          handleCleanupResponse
        )
        
        // 初始加载版本信息
        refreshVersionInfo()
      }
    })
    
    onUnmounted(() => {
      if (versionSubscription) versionSubscription.unsubscribe()
      if (upgradeSubscription) upgradeSubscription.unsubscribe()
      if (upgradeProgressSubscription) upgradeProgressSubscription.unsubscribe()
      if (cleanupSubscription) cleanupSubscription.unsubscribe()
    })
    
    return {
      loading,
      isUpgrading,
      isCleaningUp,
      versionInfo,
      upgradeProgress,
      errorMessage,
      successMessage,
      formatDate,
      getProgressWidth,
      refreshVersionInfo,
      upgradeVersion,
      upgradeToLatest,
      cleanupImages
    }
  }
}
</script>

<style scoped>
.version-manager {
  max-width: 900px;
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

.version-card {
  background: #f8f9fa;
  border: 2px solid #e9ecef;
  border-radius: 8px;
  padding: 1.5rem;
  height: 100%;
  transition: all 0.3s ease;
}

.version-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}

.current-version {
  border-color: #28a745;
  background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%);
}

.latest-version {
  border-color: #007bff;
  background: linear-gradient(135deg, #d1ecf1 0%, #bee5eb 100%);
}

.version-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
  font-weight: 600;
  color: #495057;
}

.version-title {
  display: flex;
  align-items: center;
}

.update-badge {
  background: #dc3545;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.version-content {
  text-align: center;
}

.version-number {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 0.5rem;
}

.version-date {
  font-size: 0.9rem;
  color: #6c757d;
}

.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.version-item:hover {
  background: #e9ecef;
}

.version-item.current {
  background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%);
  border-color: #28a745;
}

.version-info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.version-tag {
  font-weight: 500;
  color: #495057;
}

.current-indicator {
  background: #28a745;
  color: white;
  padding: 0.125rem 0.5rem;
  border-radius: 10px;
  font-size: 0.75rem;
  font-weight: 500;
}

.available-versions h6 {
  border-bottom: 2px solid #e9ecef;
  padding-bottom: 0.5rem;
}

.update-description {
  background: #f8f9fa;
  border-left: 4px solid #007bff;
  padding: 1rem;
  border-radius: 4px;
}

.description-content {
  color: #495057;
  line-height: 1.6;
}

.version-actions {
  padding-top: 1rem;
  border-top: 1px solid #e9ecef;
}

.upgrade-progress {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 6px;
  border-left: 4px solid #007bff;
}

.progress {
  height: 8px;
  background: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar {
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
}

.progress-text {
  font-size: 0.9rem;
  color: #495057;
  font-weight: 500;
}

.last-checked {
  text-align: center;
  font-size: 0.875rem;
}

.btn {
  font-weight: 500;
  border-radius: 6px;
}

.btn-success {
  background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
  border: none;
}

.btn-outline-warning {
  color: #fd7e14;
  border-color: #fd7e14;
}

.btn-outline-warning:hover {
  background: #fd7e14;
  border-color: #fd7e14;
}

.text-primary {
  color: #667eea !important;
}

.alert {
  border: none;
  border-radius: 8px;
}
</style>