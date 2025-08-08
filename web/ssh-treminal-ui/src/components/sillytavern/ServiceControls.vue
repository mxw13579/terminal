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
        @click="$emit('service-action', 'start')"
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
        @click="$emit('service-action', 'stop')"
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
        @click="$emit('service-action', 'restart')"
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
        @click="$emit('service-action', 'upgrade')"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists"
        class="control-button upgrade-button"
      >
        <span class="button-icon">⬆️</span>
        <div class="button-content">
          <div class="button-title">升级</div>
          <div class="button-subtitle">更新到最新版本</div>
        </div>
      </button>
      
      <!-- Delete Button -->
      <button 
        @click="handleDelete"
        :disabled="isPerformingAction || !containerStatus || !containerStatus.exists"
        class="control-button delete-button"
      >
        <span class="button-icon">🗑️</span>
        <div class="button-content">
          <div class="button-title">删除</div>
          <div class="button-subtitle">删除容器</div>
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
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
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

const handleDelete = () => {
  const confirmed = confirm('确定要删除容器吗？此操作不可撤销。\n\n选择"确定"仅删除容器，选择"取消"放弃删除。')
  if (confirmed) {
    // For now, just delete container without data
    // In a full implementation, you might want a more sophisticated dialog
    emit('service-action', 'delete', { removeData: false })
  }
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

.delete-button:hover:not(:disabled) {
  border-color: #e53e3e;
  background: #fff5f5;
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