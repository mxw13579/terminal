<template>
  <div class="container-status-widget">
    <div class="status-header">
      <h4 class="status-title">容器状态</h4>
      <div v-if="isLoading" class="loading-indicator">
        <span class="loading-spinner">🔄</span>
        检查中...
      </div>
    </div>
    
    <div v-if="!isLoading && status" class="status-content">
      <div class="status-row">
        <span class="status-label">存在状态:</span>
        <span class="status-value" :class="status.exists ? 'status-positive' : 'status-negative'">
          {{ status.exists ? '已创建' : '未创建' }}
        </span>
      </div>
      
      <div v-if="status.exists" class="status-row">
        <span class="status-label">运行状态:</span>
        <span class="status-value" :class="status.running ? 'status-positive' : 'status-warning'">
          {{ status.running ? '运行中' : '已停止' }}
        </span>
      </div>
      
      <div v-if="status.containerName" class="status-row">
        <span class="status-label">容器名称:</span>
        <span class="status-value">{{ status.containerName }}</span>
      </div>
      
      <div v-if="status.image" class="status-row">
        <span class="status-label">镜像:</span>
        <span class="status-value">{{ status.image }}</span>
      </div>
      
      <div v-if="status.port" class="status-row">
        <span class="status-label">端口:</span>
        <span class="status-value">{{ status.port }}</span>
      </div>
      
      <div v-if="status.uptimeSeconds" class="status-row">
        <span class="status-label">运行时间:</span>
        <span class="status-value">{{ formatUptime(status.uptimeSeconds) }}</span>
      </div>
      
      <div v-if="status.lastUpdated" class="status-row">
        <span class="status-label">更新时间:</span>
        <span class="status-value">{{ formatDateTime(status.lastUpdated) }}</span>
      </div>
    </div>
    
    <div v-else-if="!isLoading" class="status-content">
      <div class="no-status">
        <span class="no-status-icon">❓</span>
        <p>暂无状态信息</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps } from 'vue'

const props = defineProps({
  status: {
    type: Object,
    default: null
  },
  isLoading: {
    type: Boolean,
    default: false
  }
})

const formatUptime = (seconds) => {
  if (!seconds) return 'N/A'
  
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  
  if (days > 0) return `${days}天 ${hours}小时`
  if (hours > 0) return `${hours}小时 ${minutes}分钟`
  return `${minutes}分钟`
}

const formatDateTime = (dateTime) => {
  if (!dateTime) return 'N/A'
  return new Date(dateTime).toLocaleString('zh-CN')
}
</script>

<style scoped>
.container-status-widget {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.status-title {
  margin: 0;
  font-size: 1.1rem;
  color: #2d3748;
  font-weight: 600;
}

.loading-indicator {
  display: flex;
  align-items: center;
  color: #718096;
  font-size: 0.9rem;
}

.loading-spinner {
  margin-right: 8px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.status-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}

.status-label {
  font-weight: 500;
  color: #4a5568;
  font-size: 0.9rem;
}

.status-value {
  font-weight: 500;
  font-size: 0.9rem;
  padding: 4px 8px;
  border-radius: 6px;
  background: #f7fafc;
}

.status-positive {
  color: #22543d;
  background: #c6f6d5;
}

.status-warning {
  color: #744210;
  background: #faf089;
}

.status-negative {
  color: #742a2a;
  background: #fed7d7;
}

.no-status {
  text-align: center;
  padding: 20px;
  color: #718096;
}

.no-status-icon {
  font-size: 2rem;
  display: block;
  margin-bottom: 10px;
}

.no-status p {
  margin: 0;
  font-size: 0.9rem;
}

/* Responsive design */
@media (max-width: 600px) {
  .status-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  
  .status-value {
    align-self: flex-end;
  }
}
</style>