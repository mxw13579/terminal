<template>
  <div class="connection-manager">
    <!-- 连接状态栏 -->
    <div class="connection-status" :class="connectionStatusClass">
      <div class="status-info">
        <div class="status-indicator">
          <div class="status-dot" :class="connectionStatus"></div>
          <span class="status-text">{{ statusText }}</span>
        </div>
        <div class="connection-details" v-if="connectionState.isConnected">
          {{ connectionDisplay }}
        </div>
      </div>
      
      <div class="status-actions">
        <button 
          v-if="connectionState.isConnected" 
          @click="handleDisconnect"
          class="btn btn-sm btn-outline"
        >
          断开连接
        </button>
        <button 
          v-else 
          @click="showConnectionModal = true"
          class="btn btn-sm btn-primary"
        >
          连接服务器
        </button>
      </div>
    </div>
    
    <!-- 连接配置模态框 -->
    <div v-if="showConnectionModal" class="modal-overlay" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>连接SSH服务器</h3>
          <button @click="closeModal" class="modal-close">&times;</button>
        </div>
        
        <div class="modal-body">
          <!-- 快速连接（历史记录） -->
          <div v-if="connectionState.connectionHistory.length > 0" class="quick-connect">
            <h4>最近连接</h4>
            <div class="connection-history">
              <div 
                v-for="conn in connectionState.connectionHistory" 
                :key="conn.id"
                class="history-item"
                @click="selectConnection(conn)"
              >
                <div class="history-info">
                  <div class="history-primary">{{ conn.user }}@{{ conn.host }}</div>
                  <div class="history-secondary">端口: {{ conn.port || 22 }}</div>
                </div>
                <div class="history-actions">
                  <button @click.stop="removeConnection(conn.id)" class="btn-icon">
                    <i class="fas fa-trash"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
          
          <!-- 新建连接表单 -->
          <div class="connection-form">
            <h4>{{ selectedConnection ? '编辑连接' : '新建连接' }}</h4>
            
            <form @submit.prevent="handleConnect" class="form">
              <div class="form-group">
                <label for="host">服务器地址 *</label>
                <input 
                  id="host"
                  v-model="connectionForm.host" 
                  type="text" 
                  placeholder="例如: 192.168.1.100"
                  required
                  class="form-input"
                />
              </div>
              
              <div class="form-row">
                <div class="form-group">
                  <label for="port">端口</label>
                  <input 
                    id="port"
                    v-model.number="connectionForm.port" 
                    type="number" 
                    placeholder="22"
                    class="form-input"
                  />
                </div>
                
                <div class="form-group">
                  <label for="user">用户名 *</label>
                  <input 
                    id="user"
                    v-model="connectionForm.user" 
                    type="text" 
                    placeholder="root"
                    required
                    class="form-input"
                  />
                </div>
              </div>
              
              <div class="form-group">
                <label for="password">密码 *</label>
                <input 
                  id="password"
                  v-model="connectionForm.password" 
                  type="password" 
                  placeholder="请输入密码"
                  required
                  class="form-input"
                />
              </div>
              
              <div class="form-group">
                <label class="checkbox-label">
                  <input 
                    v-model="connectionForm.savePassword" 
                    type="checkbox"
                    class="checkbox-input"
                  />
                  <span class="checkbox-text">保存密码（仅本地存储）</span>
                </label>
              </div>
              
              <div class="form-actions">
                <button type="button" @click="resetForm" class="btn btn-outline">
                  重置
                </button>
                <button 
                  type="button" 
                  @click="testConnection"
                  :disabled="connectionState.connecting || !isFormValid"
                  class="btn btn-secondary"
                >
                  <span v-if="testing">测试中...</span>
                  <span v-else>测试连接</span>
                </button>
                <button 
                  type="submit" 
                  :disabled="connectionState.connecting"
                  class="btn btn-primary"
                >
                  <span v-if="connectionState.connecting">连接中...</span>
                  <span v-else>连接</span>
                </button>
              </div>
            </form>
          </div>
        </div>
        
        <!-- 测试结果显示 -->
        <div v-if="testResult" class="test-result" :class="{ 'test-success': testResult.success, 'test-error': !testResult.success }">
          <i :class="testResult.success ? 'fas fa-check-circle' : 'fas fa-exclamation-triangle'"></i>
          {{ testResult.message }}
        </div>
        
        <!-- 错误提示 -->
        <div v-if="connectionState.error" class="error-message">
          <i class="fas fa-exclamation-triangle"></i>
          {{ connectionState.error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch } from 'vue'
import useConnectionManager from '../composables/useConnectionManager'

const { 
  connectionState, 
  connectionStatus, 
  connectionDisplay,
  connect, 
  disconnect, 
  removeConnection 
} = useConnectionManager()

// 模态框控制
const showConnectionModal = ref(false)
const selectedConnection = ref(null)
const testing = ref(false)
const testResult = ref(null)

// 连接表单
const connectionForm = reactive({
  host: '',
  port: 22,
  user: '',
  password: '',
  savePassword: false
})

// 计算属性
const connectionStatusClass = computed(() => ({
  'status-connected': connectionState.isConnected,
  'status-connecting': connectionState.connecting,
  'status-error': connectionState.error,
  'status-disconnected': !connectionState.isConnected && !connectionState.connecting
}))

const statusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connecting': return '连接中...'
    case 'connected': return '已连接'
    case 'error': return '连接失败'
    default: return '未连接'
  }
})

const isFormValid = computed(() => {
  return connectionForm.host && connectionForm.user && connectionForm.password
})

// 方法
const closeModal = () => {
  showConnectionModal.value = false
  selectedConnection.value = null
  resetForm()
}

const resetForm = () => {
  Object.assign(connectionForm, {
    host: '',
    port: 22,
    user: '',
    password: '',
    savePassword: false
  })
}

const selectConnection = (conn) => {
  selectedConnection.value = conn
  Object.assign(connectionForm, {
    host: conn.host,
    port: conn.port || 22,
    user: conn.user,
    password: conn.savePassword ? conn.password : '',
    savePassword: conn.savePassword || false
  })
}

const handleConnect = async () => {
  try {
    await connect(connectionForm)
    closeModal()
  } catch (error) {
    // 错误已在 connectionState 中处理
    console.error('连接失败:', error)
  }
}

const testConnection = async () => {
  if (!isFormValid.value) return
  
  testing.value = true
  testResult.value = null
  
  try {
    // 使用相同的STOMP连接方式进行测试
    // 但是不保存连接状态，只是测试连通性
    await connect(connectionForm)
    
    // 如果连接成功，立即断开测试连接
    if (connectionState.isConnected) {
      await disconnect()
      testResult.value = { 
        success: true, 
        message: '✅ 连接测试成功！服务器可达且SSH认证通过。' 
      }
    }
  } catch (error) {
    let errorMessage = error.message || '连接测试失败'
    
    // 根据错误类型提供具体的指导
    if (errorMessage.includes('Connection refused')) {
      errorMessage = '❌ 连接被拒绝，请检查：\n• 服务器地址和端口是否正确\n• SSH服务是否运行\n• 防火墙是否允许连接'
    } else if (errorMessage.includes('Authentication') || errorMessage.includes('Auth')) {
      errorMessage = '❌ 认证失败，请检查：\n• 用户名是否正确\n• 密码是否正确\n• 用户是否有SSH权限'
    } else if (errorMessage.includes('timeout')) {
      errorMessage = '❌ 连接超时，请检查：\n• 网络连接是否正常\n• 服务器是否可达'
    }
    
    testResult.value = { 
      success: false, 
      message: errorMessage
    }
  } finally {
    testing.value = false
  }
}

const handleDisconnect = async () => {
  try {
    await disconnect()
  } catch (error) {
    console.error('断开连接失败:', error)
  }
}
</script>

<style scoped>
.connection-manager {
  width: 100%;
}

.connection-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-left: 4px solid #e2e8f0;
  transition: all 0.3s ease;
}

.status-connected {
  border-left-color: #48bb78;
  background: linear-gradient(135deg, #f0fff4 0%, #c6f6d5 100%);
}

.status-connecting {
  border-left-color: #ed8936;
  background: linear-gradient(135deg, #fffaf0 0%, #feebc8 100%);
}

.status-error {
  border-left-color: #f56565;
  background: linear-gradient(135deg, #fff5f5 0%, #fed7d7 100%);
}

.status-disconnected {
  border-left-color: #a0aec0;
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.status-dot.connected {
  background: #48bb78;
  box-shadow: 0 0 8px rgba(72, 187, 120, 0.5);
}

.status-dot.connecting {
  background: #ed8936;
  animation: pulse 2s infinite;
}

.status-dot.error {
  background: #f56565;
}

.status-dot.disconnected {
  background: #a0aec0;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  font-weight: 600;
  color: #2d3748;
}

.connection-details {
  font-size: 0.9rem;
  color: #718096;
  font-family: 'JetBrains Mono', monospace;
}

.status-actions {
  display: flex;
  gap: 8px;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #2d3748;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #a0aec0;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.modal-close:hover {
  background: #e2e8f0;
  color: #2d3748;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

/* 快速连接 */
.quick-connect {
  margin-bottom: 32px;
}

.quick-connect h4 {
  margin: 0 0 16px 0;
  font-size: 1rem;
  font-weight: 600;
  color: #2d3748;
}

.connection-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-item:hover {
  background: #edf2f7;
  border-color: #cbd5e0;
  transform: translateY(-1px);
}

.history-info {
  flex: 1;
}

.history-primary {
  font-weight: 600;
  color: #2d3748;
  font-family: 'JetBrains Mono', monospace;
}

.history-secondary {
  font-size: 0.875rem;
  color: #718096;
}

.history-actions .btn-icon {
  background: none;
  border: none;
  color: #a0aec0;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.history-actions .btn-icon:hover {
  background: #e2e8f0;
  color: #f56565;
}

/* 表单样式 */
.connection-form h4 {
  margin: 0 0 20px 0;
  font-size: 1rem;
  font-weight: 600;
  color: #2d3748;
  padding-top: 32px;
  border-top: 1px solid #e2e8f0;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-weight: 500;
  color: #2d3748;
  font-size: 0.875rem;
}

.form-input {
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.2s ease;
  background: white;
}

.form-input:focus {
  outline: none;
  border-color: #4299e1;
  box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.1);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 0.875rem;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  accent-color: #4299e1;
}

.checkbox-text {
  color: #4a5568;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 8px;
}

/* 错误消息 */
.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fed7d7;
  color: #c53030;
  border-radius: 8px;
  margin: 16px 24px 0;
  font-size: 0.875rem;
}

/* 测试结果样式 */
.test-result {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  margin: 16px 24px 0;
  font-size: 0.875rem;
  white-space: pre-line;
}

.test-success {
  background: #f0fff4;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.test-error {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

/* 按钮样式 */
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.8rem;
}

.btn-primary {
  background: #4299e1;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #3182ce;
  transform: translateY(-1px);
}

.btn-primary:disabled {
  background: #a0aec0;
  cursor: not-allowed;
}

.btn-outline {
  background: transparent;
  color: #4a5568;
  border: 1px solid #e2e8f0;
}

.btn-outline:hover {
  background: #f8fafc;
  border-color: #cbd5e0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .connection-status {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .status-actions {
    justify-content: center;
  }
  
  .modal-content {
    width: 95%;
    margin: 20px;
  }
  
  .form-row {
    grid-template-columns: 1fr;
  }
  
  .form-actions {
    flex-direction: column-reverse;
  }
}
</style>