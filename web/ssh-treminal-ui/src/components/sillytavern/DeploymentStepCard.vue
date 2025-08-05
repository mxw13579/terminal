<template>
  <div class="deployment-step-card" :class="stepCardClass">
    <div class="step-header">
      <div class="step-status">
        <span class="status-icon">{{ statusIcon }}</span>
        <span class="status-text">{{ statusText }}</span>
      </div>
      <div class="step-title">{{ step.title }}</div>
      <div v-if="step.progress !== undefined" class="step-progress-indicator">
        {{ step.progress }}%
      </div>
    </div>
    
    <div class="step-content">
      <!-- 步骤描述 -->
      <div v-if="step.description" class="step-description">
        {{ step.description }}
      </div>
      
      <!-- 实时日志显示 -->
      <div v-if="showLogs && step.logs && step.logs.length > 0" class="step-logs">
        <div class="logs-header">
          <span class="logs-title">执行日志</span>
          <button 
            @click="toggleLogsExpanded" 
            class="logs-toggle"
            :class="{ 'logs-expanded': logsExpanded }"
          >
            {{ logsExpanded ? '收起' : '展开' }}
          </button>
        </div>
        <div v-show="logsExpanded" class="logs-content">
          <div 
            v-for="(log, index) in displayLogs" 
            :key="index"
            class="log-entry"
            :class="getLogClass(log.type)"
          >
            <span class="log-time">{{ formatTime(log.timestamp) }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
          <div v-if="step.logs.length > maxDisplayLogs" class="logs-more">
            还有 {{ step.logs.length - maxDisplayLogs }} 条日志...
          </div>
        </div>
      </div>
      
      <!-- 用户确认区域 -->
      <div v-if="step.requiresConfirmation && step.status === 'waiting'" class="step-confirmation">
        <div class="confirmation-content">
          <div class="confirmation-icon">⚠️</div>
          <div class="confirmation-text">
            <div class="confirmation-title">需要用户确认</div>
            <div class="confirmation-message">{{ step.confirmationMessage || '此步骤需要您的确认才能继续' }}</div>
          </div>
        </div>
        
        <!-- 用户输入表单 -->
        <div v-if="step.userInputFields && step.userInputFields.length > 0" class="user-input-section">
          <div class="input-section-title">请填写以下信息：</div>
          <div 
            v-for="field in step.userInputFields" 
            :key="field.name"
            class="input-field"
          >
            <label class="field-label">{{ field.label }}</label>
            <input 
              v-if="field.type === 'text' || field.type === 'password'"
              :type="field.type"
              v-model="userInputs[field.name]"
              class="field-input"
              :placeholder="field.placeholder"
              :required="field.required"
            />
            <select 
              v-else-if="field.type === 'select'"
              v-model="userInputs[field.name]"
              class="field-select"
              :required="field.required"
            >
              <option value="">请选择...</option>
              <option 
                v-for="option in field.options" 
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <div v-if="field.helpText" class="field-help">{{ field.helpText }}</div>
          </div>
        </div>
        
        <!-- 确认按钮组 -->
        <div class="confirmation-actions">
          <button 
            @click="handleConfirm(true)"
            class="btn btn-confirm"
            :disabled="!canConfirm"
          >
            <span class="btn-icon">✓</span>
            确认执行
          </button>
          <button 
            @click="handleConfirm(false)"
            class="btn btn-skip"
          >
            <span class="btn-icon">⏭️</span>
            跳过此步骤
          </button>
          <button 
            v-if="allowCancel"
            @click="handleCancel"
            class="btn btn-cancel"
          >
            <span class="btn-icon">✖️</span>
            取消部署
          </button>
        </div>
      </div>
      
      <!-- 进度条 -->
      <div v-if="step.progress !== undefined && step.status === 'running'" class="step-progress-bar">
        <div class="progress-bar">
          <div 
            class="progress-fill" 
            :style="{ width: step.progress + '%' }"
          ></div>
        </div>
        <div class="progress-text">{{ step.progress }}% 完成</div>
      </div>
      
      <!-- 步骤结果信息 -->
      <div v-if="step.result" class="step-result">
        <div class="result-summary" :class="step.result.success ? 'result-success' : 'result-error'">
          <span class="result-icon">{{ step.result.success ? '✅' : '❌' }}</span>
          <span class="result-message">{{ step.result.message }}</span>
        </div>
        <div v-if="step.result.details" class="result-details">
          <div 
            v-for="(detail, key) in step.result.details" 
            :key="key"
            class="result-detail-item"
          >
            <span class="detail-key">{{ key }}:</span>
            <span class="detail-value">{{ detail }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DeploymentStepCard',
  props: {
    step: {
      type: Object,
      required: true
    },
    showLogs: {
      type: Boolean,
      default: true
    },
    allowCancel: {
      type: Boolean,
      default: true
    },
    maxDisplayLogs: {
      type: Number,
      default: 10
    }
  },
  
  emits: ['step-confirmed', 'step-cancelled'],
  
  data() {
    return {
      logsExpanded: true,
      userInputs: {}
    }
  },
  
  computed: {
    stepCardClass() {
      return {
        'step-pending': this.step.status === 'pending',
        'step-running': this.step.status === 'running', 
        'step-completed': this.step.status === 'completed',
        'step-error': this.step.status === 'error',
        'step-waiting': this.step.status === 'waiting',
        'step-skipped': this.step.status === 'skipped'
      }
    },
    
    statusIcon() {
      const icons = {
        pending: '⏳',
        running: '🔄',
        completed: '✅',
        error: '❌',
        waiting: '⏸️',
        skipped: '⏭️'
      }
      return icons[this.step.status] || '⏳'
    },
    
    statusText() {
      const texts = {
        pending: '等待执行',
        running: '正在执行',
        completed: '执行完成',
        error: '执行失败',
        waiting: '等待确认',
        skipped: '已跳过'
      }
      return texts[this.step.status] || '未知状态'
    },
    
    displayLogs() {
      if (!this.step.logs) return []
      return this.step.logs.slice(-this.maxDisplayLogs)
    },
    
    canConfirm() {
      if (!this.step.userInputFields) return true
      
      // 检查必填字段是否都已填写
      return this.step.userInputFields.every(field => {
        if (!field.required) return true
        const value = this.userInputs[field.name]
        return value && value.toString().trim() !== ''
      })
    }
  },
  
  watch: {
    'step.userInputFields': {
      handler(newFields) {
        if (newFields) {
          // 初始化用户输入值
          newFields.forEach(field => {
            if (!(field.name in this.userInputs)) {
              this.$set(this.userInputs, field.name, field.defaultValue || '')
            }
          })
        }
      },
      immediate: true,
      deep: true
    }
  },
  
  methods: {
    toggleLogsExpanded() {
      this.logsExpanded = !this.logsExpanded
    },
    
    handleConfirm(confirmed) {
      const eventData = {
        stepId: this.step.id,
        confirmed,
        userInputs: { ...this.userInputs }
      }
      
      this.$emit('step-confirmed', eventData)
    },
    
    handleCancel() {
      this.$emit('step-cancelled', this.step.id)
    },
    
    getLogClass(logType) {
      return {
        'log-info': logType === 'info',
        'log-success': logType === 'success',
        'log-warning': logType === 'warning',
        'log-error': logType === 'error'
      }
    },
    
    formatTime(timestamp) {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString('zh-CN', { 
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    }
  }
}
</script>

<style scoped>
.deployment-step-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  transition: all 0.3s ease;
  position: relative;
}

.deployment-step-card::before {
  content: '';
  position: absolute;
  left: -2px;
  top: -2px;
  bottom: -2px;
  width: 4px;
  border-radius: 12px 0 0 12px;
  background: #e9ecef;
  transition: background 0.3s ease;
}

.step-pending {
  border-color: #e9ecef;
}

.step-pending::before {
  background: #e9ecef;
}

.step-running {
  border-color: #007bff;
  box-shadow: 0 2px 12px rgba(0, 123, 255, 0.15);
  animation: pulse 2s infinite;
}

.step-running::before {
  background: #007bff;
}

.step-completed {
  border-color: #28a745;
  background: #f8fff9;
}

.step-completed::before {
  background: #28a745;
}

.step-error {
  border-color: #dc3545;
  background: #fff8f8;
}

.step-error::before {  
  background: #dc3545;
}

.step-waiting {
  border-color: #ffc107;
  background: #fffbf0;
  box-shadow: 0 2px 12px rgba(255, 193, 7, 0.15);
}

.step-waiting::before {
  background: #ffc107;
}

.step-skipped {
  border-color: #6c757d;
  background: #f8f9fa;
  opacity: 0.8;
}

.step-skipped::before {
  background: #6c757d;
}

@keyframes pulse {
  0% { box-shadow: 0 2px 12px rgba(0, 123, 255, 0.15); }
  50% { box-shadow: 0 4px 16px rgba(0, 123, 255, 0.25); }
  100% { box-shadow: 0 2px 12px rgba(0, 123, 255, 0.15); }
}

.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.step-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.status-icon {
  font-size: 16px;
}

.step-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 16px;
}

.step-progress-indicator {
  font-size: 14px;
  font-weight: 500;
  color: #007bff;
  background: #e3f2fd;
  padding: 4px 8px;
  border-radius: 12px;
}

.step-description {
  color: #6c757d;
  margin-bottom: 16px;
  line-height: 1.5;
}

.step-logs {
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid #e9ecef;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e9ecef;
}

.logs-title {
  font-weight: 500;
  color: #495057;
}

.logs-toggle {
  background: none;
  border: none;
  color: #007bff;
  cursor: pointer;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s ease;
}

.logs-toggle:hover {
  background: #e3f2fd;
}

.logs-content {
  max-height: 300px;
  overflow-y: auto;
  padding: 12px 16px;
}

.log-entry {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.4;
  font-family: 'Consolas', 'Monaco', monospace;
}

.log-time {
  color: #6c757d;
  flex-shrink: 0;
  min-width: 60px;
}

.log-message {
  flex: 1;
  word-break: break-word;
}

.log-info { color: #495057; }
.log-success { color: #28a745; font-weight: 500; }
.log-warning { color: #fd7e14; font-weight: 500; }
.log-error { color: #dc3545; font-weight: 500; }

.logs-more {
  text-align: center;
  color: #6c757d;
  font-size: 12px;
  font-style: italic;
  margin-top: 8px;
}

.step-confirmation {
  background: linear-gradient(135deg, #fff3cd 0%, #ffeaa7 100%);
  border: 2px solid #ffc107;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.confirmation-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.confirmation-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.confirmation-text {
  flex: 1;
}

.confirmation-title {
  font-weight: 600;
  color: #856404;
  margin-bottom: 4px;
}

.confirmation-message {
  color: #856404;
  line-height: 1.5;
}

.user-input-section {
  background: white;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid #e9ecef;
}

.input-section-title {
  font-weight: 600;
  color: #495057;
  margin-bottom: 12px;
}

.input-field {
  margin-bottom: 12px;
}

.field-label {
  display: block;
  font-weight: 500;
  color: #495057;
  margin-bottom: 4px;
  font-size: 14px;
}

.field-input, .field-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.2s ease;
}

.field-input:focus, .field-select:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.field-help {
  font-size: 12px;
  color: #6c757d;
  margin-top: 4px;
  line-height: 1.4;
}

.confirmation-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.step-progress-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #007bff 0%, #0056b3 100%);
  transition: width 0.5s ease;
  border-radius: 4px;
}

.progress-text {
  font-size: 14px;
  font-weight: 500;
  color: #495057;
  min-width: 80px;
}

.step-result {
  background: #f8f9fa;
  border-radius: 6px;
  padding: 12px;
  border: 1px solid #e9ecef;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-success {
  color: #28a745;
}

.result-error {
  color: #dc3545;
}

.result-icon {
  font-size: 16px;
}

.result-message {
  font-weight: 500;
  flex: 1;
}

.result-details {
  background: white;
  border-radius: 4px;
  padding: 8px;
  font-size: 13px;
}

.result-detail-item {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}

.detail-key {
  font-weight: 500;
  color: #495057;
  min-width: 100px;
}

.detail-value {
  color: #6c757d;
  word-break: break-word;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-confirm {
  background: #28a745;
  color: white;
}

.btn-confirm:hover:not(:disabled) {
  background: #218838;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(40, 167, 69, 0.3);
}

.btn-skip {
  background: #6c757d;
  color: white;
}

.btn-skip:hover:not(:disabled) {
  background: #5a6268;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(108, 117, 125, 0.3);
}

.btn-cancel {
  background: #dc3545;
  color: white;
}

.btn-cancel:hover:not(:disabled) {
  background: #c82333;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.3);
}

.btn-icon {
  font-size: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .step-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .step-title {
    margin: 8px 0;
  }
  
  .confirmation-actions {
    flex-direction: column;
  }
  
  .btn {
    justify-self: stretch;
    text-align: center;
  }
}
</style>