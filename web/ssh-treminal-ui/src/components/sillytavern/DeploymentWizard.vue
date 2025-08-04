<template>
  <div class="deployment-wizard">
    <div class="wizard-header">
      <h3 class="wizard-title">SillyTavern 部署向导</h3>
      <p class="wizard-subtitle">一键部署您的 AI 对话平台</p>
    </div>
    
    <!-- System Validation -->
    <div class="wizard-section">
      <div class="section-header">
        <h4 class="section-title">
          <span class="section-icon">🔍</span>
          系统要求检查
        </h4>
        <button 
          @click="$emit('validate-system')"
          :disabled="systemChecking"
          class="btn btn-secondary btn-sm"
        >
          {{ systemChecking ? '检查中...' : '重新检查' }}
        </button>
      </div>
      
      <div v-if="systemChecking" class="validation-loading">
        <div class="loading-spinner">🔄</div>
        <p>正在检查系统要求...</p>
      </div>
      
      <div v-else-if="systemInfo" class="validation-results">
        <div class="validation-summary" :class="isSystemValid ? 'validation-success' : 'validation-error'">
          <span class="summary-icon">{{ isSystemValid ? '✅' : '❌' }}</span>
          <span class="summary-text">
            {{ isSystemValid ? '系统满足部署要求' : '系统不满足部署要求' }}
          </span>
        </div>
        
        <div v-if="systemInfo.requirementChecks" class="validation-details">
          <div 
            v-for="check in systemInfo.requirementChecks" 
            :key="check"
            class="validation-item"
            :class="check.startsWith('✓') ? 'validation-pass' : check.startsWith('✗') ? 'validation-fail' : 'validation-warning'"
          >
            {{ check }}
          </div>
        </div>
      </div>
      
      <div v-else class="validation-prompt">
        <p>请先检查系统要求以确保可以部署 SillyTavern</p>
      </div>
    </div>
    
    <!-- Deployment Form -->
    <div v-if="isSystemValid" class="wizard-section">
      <div class="section-header">
        <h4 class="section-title">
          <span class="section-icon">🚀</span>
          部署配置
        </h4>
      </div>
      
      <form @submit.prevent="handleDeploy" class="deployment-form">
        <div class="form-group">
          <label for="username" class="form-label">用户名</label>
          <input 
            id="username"
            v-model="deploymentForm.username"
            type="text"
            class="form-input"
            placeholder="输入 SillyTavern 用户名"
            required
          />
        </div>
        
        <div class="form-group">
          <label for="password" class="form-label">密码</label>
          <input 
            id="password"
            v-model="deploymentForm.password"
            type="password"
            class="form-input"
            placeholder="输入 SillyTavern 密码"
            required
          />
        </div>
        
        <div class="form-group">
          <label for="port" class="form-label">端口</label>
          <input 
            id="port"
            v-model.number="deploymentForm.port"
            type="number"
            class="form-input"
            placeholder="8000"
            min="1024"
            max="65535"
            required
          />
        </div>
        
        <div class="form-group">
          <label for="dataPath" class="form-label">数据目录</label>
          <input 
            id="dataPath"
            v-model="deploymentForm.dataPath"
            type="text"
            class="form-input"
            placeholder="./sillytavern-data"
            required
          />
        </div>
        
        <button 
          type="submit"
          :disabled="isDeploying || !isFormValid"
          class="btn btn-primary btn-deploy"
        >
          <span v-if="isDeploying">🚀</span>
          <span v-else>🎯</span>
          {{ isDeploying ? '部署中...' : '开始部署' }}
        </button>
      </form>
    </div>
    
    <!-- Deployment Progress -->
    <div v-if="isDeploying || deploymentProgress" class="wizard-section">
      <div class="section-header">
        <h4 class="section-title">
          <span class="section-icon">⏳</span>
          部署进度
        </h4>
      </div>
      
      <div v-if="deploymentProgress" class="deployment-progress">
        <div class="progress-info">
          <div class="progress-stage">{{ deploymentProgress.stage || '准备中' }}</div>
          <div class="progress-percentage">{{ deploymentProgress.progress || 0 }}%</div>
        </div>
        
        <div class="progress-bar">
          <div 
            class="progress-fill"
            :style="{ width: (deploymentProgress.progress || 0) + '%' }"
          ></div>
        </div>
        
        <div v-if="deploymentProgress.message" class="progress-message">
          {{ deploymentProgress.message }}
        </div>
        
        <div v-if="deploymentProgress.error" class="progress-error">
          ❌ {{ deploymentProgress.error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, defineProps, defineEmits } from 'vue'

const props = defineProps({
  isDeploying: {
    type: Boolean,
    default: false
  },
  deploymentProgress: {
    type: Object,
    default: null
  },
  systemInfo: {
    type: Object,
    default: null
  },
  isSystemValid: {
    type: Boolean,
    default: false
  },
  systemChecking: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['validate-system', 'deploy'])

// Deployment form
const deploymentForm = ref({
  username: '',
  password: '',
  port: 8000,
  dataPath: './sillytavern-data',
  dockerImage: 'ghcr.io/sillytavern/sillytavern:latest',
  containerName: 'sillytavern'
})

const isFormValid = computed(() => {
  return deploymentForm.value.username && 
         deploymentForm.value.password && 
         deploymentForm.value.port >= 1024 && 
         deploymentForm.value.port <= 65535 &&
         deploymentForm.value.dataPath
})

const handleDeploy = () => {
  if (isFormValid.value) {
    emit('deploy', { ...deploymentForm.value })
  }
}
</script>

<style scoped>
.deployment-wizard {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.wizard-header {
  text-align: center;
  margin-bottom: 30px;
}

.wizard-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 8px;
}

.wizard-subtitle {
  color: #718096;
  margin: 0;
}

.wizard-section {
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e2e8f0;
}

.wizard-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  margin: 0;
  font-size: 1.1rem;
  color: #2d3748;
  font-weight: 600;
}

.section-icon {
  margin-right: 8px;
  font-size: 1.2rem;
}

/* System Validation Styles */
.validation-loading {
  display: flex;
  align-items: center;
  padding: 20px;
  text-align: center;
  color: #718096;
}

.loading-spinner {
  margin-right: 10px;
  animation: spin 1s linear infinite;
  font-size: 1.2rem;
}

.validation-results {
  margin-top: 12px;
}

.validation-summary {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-weight: 600;
}

.validation-success {
  background: #c6f6d5;
  color: #22543d;
  border-left: 4px solid #38a169;
}

.validation-error {
  background: #fed7d7;
  color: #742a2a;
  border-left: 4px solid #e53e3e;
}

.summary-icon {
  margin-right: 10px;
  font-size: 1.1rem;
}

.validation-details {
  background: #f7fafc;
  border-radius: 8px;
  padding: 16px;
}

.validation-item {
  padding: 4px 0;
  font-size: 0.9rem;
  font-family: 'Courier New', monospace;
}

.validation-pass {
  color: #22543d;
}

.validation-fail {
  color: #742a2a;
}

.validation-warning {
  color: #744210;
}

.validation-prompt {
  text-align: center;
  padding: 20px;
  color: #718096;
}

/* Deployment Form Styles */
.deployment-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-label {
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 6px;
  font-size: 0.9rem;
}

.form-input {
  padding: 10px 12px;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  font-size: 0.9rem;
  transition: border-color 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.btn-deploy {
  margin-top: 16px;
  padding: 12px 24px;
  font-size: 1rem;
  font-weight: 600;
}

/* Deployment Progress Styles */
.deployment-progress {
  background: #f7fafc;
  border-radius: 8px;
  padding: 20px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.progress-stage {
  font-weight: 600;
  color: #2d3748;
}

.progress-percentage {
  font-weight: 600;
  color: #667eea;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 12px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  transition: width 0.3s ease;
}

.progress-message {
  color: #4a5568;
  font-size: 0.9rem;
  margin-bottom: 8px;
}

.progress-error {
  color: #e53e3e;
  font-weight: 600;
  font-size: 0.9rem;
}

/* Common Button Styles */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-secondary {
  background: #e2e8f0;
  color: #4a5568;
}

.btn-secondary:hover:not(:disabled) {
  background: #cbd5e0;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.8rem;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Responsive design */
@media (max-width: 600px) {
  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .progress-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>