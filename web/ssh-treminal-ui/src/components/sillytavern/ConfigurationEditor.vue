<template>
  <div class="configuration-editor">
    <div class="modern-card">
      <!-- Header Section -->
      <div class="card-header-modern">
        <div class="header-content">
          <div class="header-icon">
            <i class="fas fa-cog"></i>
          </div>
          <div class="header-text">
            <h5 class="card-title-modern mb-1">
              账号密码设置
            </h5>
            <p class="card-subtitle-modern mb-0">
              管理您的SillyTavern登录凭据
            </p>
          </div>
        </div>
      </div>
      
      <div class="card-body-modern">
        <!-- Loading State -->
        <div v-if="loading" class="loading-container">
          <div class="loading-spinner">
            <div class="spinner-custom"></div>
          </div>
          <p class="loading-text">加载配置中...</p>
        </div>

        <!-- Form Content -->
        <form v-else @submit.prevent="saveConfiguration" class="modern-form">
          <!-- Authentication Section -->
          <div class="config-section">
            <div class="section-header">
              <div class="section-icon auth-icon">
                <i class="fas fa-shield-alt"></i>
              </div>
              <div class="section-info">
                <h6 class="section-title">身份验证设置</h6>
                <p class="section-description">配置您的登录凭据</p>
              </div>
            </div>
            
            <div class="form-grid">
              <div class="form-group">
                <label for="username" class="modern-label">
                  <i class="fas fa-user label-icon"></i>
                  用户名 <span class="required-mark">*</span>
                </label>
                <div class="input-wrapper">
                  <input
                    type="text"
                    class="modern-input"
                    :class="{ 'input-error': errors.username }"
                    id="username"
                    v-model="config.username"
                    placeholder="请输入用户名"
                    required
                    minlength="2"
                    pattern="[a-zA-Z]+"
                  />
                  <div v-if="errors.username" class="error-message">
                    <i class="fas fa-exclamation-circle"></i>
                    {{ errors.username }}
                  </div>
                  <div class="input-hint">
                    <i class="fas fa-info-circle"></i>
                    至少2个字符，只允许英文字母
                  </div>
                </div>
              </div>
              
              <div class="form-group">
                <label for="password" class="modern-label">
                  <i class="fas fa-lock label-icon"></i>
                  密码 <span class="required-mark">*</span>
                </label>
                <div class="input-wrapper">
                  <input
                    type="text"
                    class="modern-input"
                    :class="{ 'input-error': errors.password }"
                    id="password"
                    v-model="config.password"
                    placeholder="请输入密码"
                    required
                    minlength="6"
                  />
                  <div v-if="errors.password" class="error-message">
                    <i class="fas fa-exclamation-circle"></i>
                    {{ errors.password }}
                  </div>
                  <div class="input-hint">
                    <i class="fas fa-info-circle"></i>
                    至少6个字符，必填字段
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="action-section">
            <div class="button-group-center">
              <button
                type="submit"
                class="modern-btn btn-primary"
                :disabled="saving || !isFormValid"
              >
                <span v-if="saving" class="btn-spinner"></span>
                <i v-else class="fas fa-save btn-icon"></i>
                {{ saving ? '保存中...' : '保存配置' }}
              </button>
            </div>
          </div>
        </form>

        <!-- Status Messages -->
        <div class="status-messages">
          <!-- Restart Warning -->
          <div v-if="requiresRestart" class="status-alert alert-warning">
            <div class="alert-icon">
              <i class="fas fa-exclamation-triangle"></i>
            </div>
            <div class="alert-content">
              <strong>需要重启容器</strong>
              <p>配置更改需要重启容器才能生效，请在服务控制区域重启SillyTavern容器。</p>
            </div>
          </div>

          <!-- Success Message -->
          <div v-if="successMessage" class="status-alert alert-success">
            <div class="alert-icon">
              <i class="fas fa-check-circle"></i>
            </div>
            <div class="alert-content">
              <strong>成功</strong>
              <p>{{ successMessage }}</p>
            </div>
          </div>
          
          <!-- Error Message -->
          <div v-if="errorMessage" class="status-alert alert-error">
            <div class="alert-icon">
              <i class="fas fa-exclamation-circle"></i>
            </div>
            <div class="alert-content">
              <strong>错误</strong>
              <p>{{ errorMessage }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'
import useConnectionManager from '@/composables/useConnectionManager'

export default {
  name: 'ConfigurationEditor',
  setup() {
    const sillyTavernApi = useSillyTavern()
    const { connectionState, getStompClient } = useConnectionManager()
    
    // Computed properties for connection status
    const isConnected = computed(() => connectionState.isConnected)
    const stompClient = computed(() => getStompClient())
    
    const loading = ref(true)
    const saving = ref(false)
    const requiresRestart = ref(false)
    const successMessage = ref('')
    const errorMessage = ref('')
    
    const config = ref({
      username: '',
      password: '',
      hasPassword: false,
      port: 8000,
      containerName: 'sillytavern',
      otherSettings: {}
    })
    
    const errors = ref({})
    
    // Subscriptions for WebSocket responses
    let configSubscription = null
    let updateSubscription = null
    
    const isFormValid = computed(() => {
      return config.value.username && 
             config.value.username.length >= 2 && 
             /^[a-zA-Z]+$/.test(config.value.username) &&
             config.value.password && 
             config.value.password.length >= 6
    })
    
    const validateForm = () => {
      errors.value = {}
      
      if (!config.value.username || config.value.username.trim().length === 0) {
        errors.value.username = '用户名不能为空'
      } else if (config.value.username.length < 2) {
        errors.value.username = '用户名至少2个字符'
      } else if (config.value.username.length > 20) {
        errors.value.username = '用户名不能超过20个字符'
      } else if (!/^[a-zA-Z]+$/.test(config.value.username)) {
        errors.value.username = '用户名只能包含英文字母'
      }
      
      if (!config.value.password || config.value.password.trim().length === 0) {
        errors.value.password = '密码不能为空'
      } else if (config.value.password.length < 6) {
        errors.value.password = '密码至少6个字符'
      }
      
      return Object.keys(errors.value).length === 0
    }
    
    const loadConfiguration = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket连接未建立'
        loading.value = false
        return
      }
      
      const client = stompClient.value
      if (!client) {
        errorMessage.value = 'STOMP客户端不可用'
        loading.value = false
        return
      }
      
      try {
        loading.value = true
        successMessage.value = ''
        errorMessage.value = ''
        
        client.publish({
          destination: '/app/sillytavern/get-config',
          body: JSON.stringify({})
        })
      } catch (error) {
        console.error('Error sending configuration request:', error)
        errorMessage.value = '获取配置失败: ' + error.message
        loading.value = false
      }
    }
    
    const saveConfiguration = () => {
      if (!validateForm()) {
        return
      }
      
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket连接未建立'
        return
      }
      
      const client = stompClient.value
      if (!client) {
        errorMessage.value = 'STOMP客户端不可用'
        saving.value = false
        return
      }
      
      try {
        saving.value = true
        successMessage.value = ''
        errorMessage.value = ''
        requiresRestart.value = false
        
        client.publish({
          destination: '/app/sillytavern/update-config',
          body: JSON.stringify(config.value)
        })
      } catch (error) {
        console.error('Error sending configuration update:', error)
        errorMessage.value = '保存配置失败: ' + error.message
        saving.value = false
      }
    }
    
    const handleConfigResponse = (message) => {
      console.log('ConfigurationEditor: Received config response:', message)
      try {
        const response = JSON.parse(message.body)
        console.log('Config response parsed:', response)
        loading.value = false
        
        if (response.success && response.payload) {
          config.value = {
            ...response.payload
            // 显示当前密码，不再隐藏
          }
        } else {
          errorMessage.value = '加载配置失败'
        }
      } catch (error) {
        console.error('Error handling config response:', error)
        errorMessage.value = '处理配置响应错误'
        loading.value = false
      }
    }
    
    const handleUpdateResponse = (message) => {
      console.log('ConfigurationEditor: Received config update response:', message)
      try {
        const response = JSON.parse(message.body)
        console.log('Config update response parsed:', response)
        saving.value = false
        
        if (response.success) {
          successMessage.value = response.message || '配置保存成功'
          requiresRestart.value = response.requiresRestart || false
          
          // Reload configuration to get updated values
          setTimeout(() => {
            loadConfiguration()
          }, 1000)
        } else {
          errorMessage.value = response.message || '配置保存失败'
          
          if (response.errors) {
            errors.value = response.errors
          }
        }
      } catch (error) {
        console.error('Error handling update response:', error)
        errorMessage.value = '处理配置更新响应错误'
        saving.value = false
      }
    }
    
    onMounted(() => {
      // Subscribe to configuration responses
      console.log('ConfigurationEditor mounted, checking connection...')
      console.log('isConnected:', isConnected.value)
      console.log('stompClient:', !!stompClient.value)
      
      if (isConnected.value && stompClient.value) {
        const client = stompClient.value
        console.log('STOMP client available, setting up subscriptions')
        
        try {
          configSubscription = client.subscribe(
            `/user/queue/sillytavern/config`,
            handleConfigResponse
          )
          console.log('Config subscription created for:', `/user/queue/sillytavern/config`)
          
          updateSubscription = client.subscribe(
            `/user/queue/sillytavern/config-updated`,
            handleUpdateResponse
          )
          console.log('Config update subscription created for:', `/user/queue/sillytavern/config-updated`)
          
          console.log('Subscriptions created, loading configuration')
          // Load initial configuration
          loadConfiguration()
        } catch (error) {
          console.error('Error setting up ConfigurationEditor subscriptions:', error)
          errorMessage.value = '配置编辑器初始化失败: ' + error.message
          loading.value = false
        }
      } else {
        console.warn('ConfigurationEditor: Connection not available')
        errorMessage.value = 'SSH连接是配置管理所必需的'
        loading.value = false
      }
    })
    
    onUnmounted(() => {
      if (configSubscription) {
        configSubscription.unsubscribe()
      }
      if (updateSubscription) {
        updateSubscription.unsubscribe()
      }
    })
    
    return {
      loading,
      saving,
      requiresRestart,
      successMessage,
      errorMessage,
      config,
      errors,
      isFormValid,
      saveConfiguration
    }
  }
}
</script>

<style scoped>
/* 主容器样式 */
.configuration-editor {
  max-width: 1000px;
  margin: 0 auto;
  padding: 1rem;
}

/* 现代化卡片容器 */
.modern-card {
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 
    0 10px 30px rgba(0, 0, 0, 0.1),
    0 4px 6px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

/* 头部区域 */
.card-header-modern {
  background: linear-gradient(135deg, 
    rgba(99, 102, 241, 0.9) 0%, 
    rgba(139, 92, 246, 0.9) 50%, 
    rgba(236, 72, 153, 0.9) 100%);
  padding: 2rem;
  color: white;
  position: relative;
  overflow: hidden;
}

.card-header-modern::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.05' fill-rule='nonzero'%3E%3Cpath d='m36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  opacity: 0.3;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  position: relative;
  z-index: 1;
}

.header-icon {
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.card-title-modern {
  font-size: 1.75rem;
  font-weight: 700;
  margin: 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.card-subtitle-modern {
  font-size: 1rem;
  opacity: 0.9;
  margin: 0;
  font-weight: 400;
}

/* 主体内容 */
.card-body-modern {
  padding: 2.5rem;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
}

/* 加载状态 */
.loading-container {
  text-align: center;
  padding: 3rem 1rem;
}

.loading-spinner {
  margin-bottom: 1.5rem;
}

.spinner-custom {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(99, 102, 241, 0.2);
  border-left: 4px solid #6366f1;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto;
}

.loading-text {
  color: #64748b;
  font-size: 1.1rem;
  margin: 0;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 配置区域 */
.config-section {
  margin-bottom: 3rem;
}

.config-section:last-of-type {
  margin-bottom: 2rem;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid rgba(99, 102, 241, 0.1);
}

.section-icon {
  width: 45px;
  height: 45px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: white;
}

.auth-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.server-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.settings-icon {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.25rem 0;
}

.section-description {
  font-size: 0.9rem;
  color: #64748b;
  margin: 0;
}

/* 表单网格 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 2rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

/* 标签样式 */
.modern-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 0.75rem;
  font-size: 0.95rem;
}

.label-icon {
  font-size: 0.9rem;
  color: #6b7280;
}

.required-mark {
  color: #ef4444;
  font-weight: 700;
}

.optional-mark {
  color: #64748b;
  font-weight: 400;
  font-size: 0.85rem;
}

/* 输入框样式 */
.input-wrapper {
  position: relative;
}

.modern-input {
  width: 100%;
  padding: 0.875rem 1rem;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 0.95rem;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(5px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  outline: none;
  color: #000000; /* 输入文字黑色 */
}

.modern-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  background: rgba(255, 255, 255, 0.95);
  transform: translateY(-1px);
}

.modern-input::placeholder {
  color: #9ca3af;
}

.input-error {
  border-color: #ef4444 !important;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1) !important;
}

.readonly-input {
  background: rgba(148, 163, 184, 0.1) !important;
  cursor: not-allowed;
  color: #64748b;
}

/* 提示信息 */
.input-hint {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: #64748b;
}

.readonly-hint {
  color: #94a3b8;
}

/* 错误信息 */
.error-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: #ef4444;
  font-weight: 500;
}

/* 操作按钮区域 */
.action-section {
  padding-top: 2rem;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  margin-top: 2rem;
}

.button-group {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.button-group-center {
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 现代化按钮 */
.modern-btn {
  padding: 0.875rem 2rem;
  border: none;
  border-radius: 12px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  position: relative;
  overflow: hidden;
  min-width: 140px;
  justify-content: center;
}

.btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(99, 102, 241, 0.3);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(99, 102, 241, 0.4);
}

.btn-secondary {
  background: rgba(148, 163, 184, 0.1);
  color: #475569;
  border: 2px solid #e2e8f0;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(148, 163, 184, 0.2);
  border-color: #cbd5e1;
  transform: translateY(-1px);
}

.modern-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none !important;
}

.btn-icon {
  font-size: 0.9rem;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-left: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

/* 状态消息 */
.status-messages {
  margin-top: 2rem;
}

.status-alert {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem;
  border-radius: 12px;
  margin-bottom: 1rem;
  backdrop-filter: blur(5px);
}

.alert-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
}

.alert-content strong {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 600;
}

.alert-content p {
  margin: 0;
  font-size: 0.9rem;
  opacity: 0.9;
}

.alert-warning {
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: #92400e;
}

.alert-warning .alert-icon {
  color: #f59e0b;
}

.alert-success {
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.3);
  color: #166534;
}

.alert-success .alert-icon {
  color: #22c55e;
}

.alert-error {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #991b1b;
}

.alert-error .alert-icon {
  color: #ef4444;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .configuration-editor {
    padding: 0.5rem;
  }
  
  .card-header-modern {
    padding: 1.5rem;
  }
  
  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .card-body-modern {
    padding: 1.5rem;
  }
  
  .form-grid {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }
  
  .button-group {
    flex-direction: column-reverse;
    align-items: stretch;
  }
  
  .button-group-center {
    flex-direction: column;
    align-items: stretch;
  }
  
  .modern-btn {
    min-width: 100%;
  }
  
  .section-header {
    flex-direction: column;
    text-align: center;
    gap: 0.75rem;
  }
}

@media (max-width: 480px) {
  .card-title-modern {
    font-size: 1.5rem;
  }
  
  .section-title {
    font-size: 1.1rem;
  }
  
  .modern-input {
    padding: 0.75rem;
  }
  
  .modern-btn {
    padding: 0.75rem 1.5rem;
  }
}
</style>