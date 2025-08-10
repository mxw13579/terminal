<template>
  <div class="service-controls">
    <div class="controls-header">
      <h4 class="controls-title">服务控制</h4>
      <div v-if="isPerformingAction" class="action-status">
        <span class="action-spinner">🔄</span>
        {{ currentAction }}...
      </div>
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
      
      <!-- Upgrade Button -->
      <button 
        @click="showUpgradeModal = true"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists"
        class="control-button upgrade-button"
      >
        <span class="button-icon">⬆️</span>
        <div class="button-content">
          <div class="button-title">升级</div>
          <div class="button-subtitle">选择版本进行升级</div>
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
    
    <!-- Upgrade Modal -->
    <div v-if="showUpgradeModal" class="upgrade-modal-overlay" @click="showUpgradeModal = false">
      <div class="upgrade-modal" @click.stop>
        <div class="modal-header">
          <h3 class="modal-title">选择升级版本</h3>
          <button @click="showUpgradeModal = false" class="modal-close">&times;</button>
        </div>
        <div class="modal-body">
          <p class="modal-description">选择要升级到的版本：</p>
          <div class="version-options">
            <button @click="handleUpgrade('latest')" class="version-option">
              <div class="version-tag latest">Latest</div>
              <div class="version-info">
                <div class="version-name">最新版</div>
                <div class="version-desc">包含最新功能和修复</div>
              </div>
            </button>
            <button @click="handleUpgrade('stable')" class="version-option">
              <div class="version-tag stable">Stable</div>
              <div class="version-info">
                <div class="version-name">稳定版</div>
                <div class="version-desc">推荐生产环境使用</div>
              </div>
            </button>
            <button @click="handleUpgrade('release')" class="version-option">
              <div class="version-tag release">Release</div>
              <div class="version-info">
                <div class="version-name">发布版</div>
                <div class="version-desc">正式发布版本</div>
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, ref } from 'vue'
import useConnectionManager from '@/composables/useConnectionManager'

const { connectionState } = useConnectionManager()

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

const handleServiceAction = (action, options = {}) => {
  emit('service-action', action, options)
}

const handleUpgrade = (version) => {
  showUpgradeModal.value = false
  handleServiceAction('upgrade', { version })
}
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

/* Upgrade Modal Styles */
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
</style>