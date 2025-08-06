<template>
  <div class="interactive-deployment-wizard">
    <!-- 部署模式选择器 -->
    <div class="deployment-mode-selector" v-if="!deploymentStarted">
      <div class="mode-header">
        <h3 class="mode-title">🚀 SillyTavern 交互式部署向导</h3>
        <p class="mode-subtitle">选择您的部署模式，开始智能化部署流程</p>
      </div>
      
      <div class="mode-options">
        <div 
          class="mode-card" 
          :class="{ 'mode-selected': selectedMode === 'trusted' }"
          @click="selectMode('trusted')"
        >
          <div class="mode-icon">⚡</div>
          <h4 class="mode-name">完全信任模式</h4>
          <p class="mode-description">
            自动执行所有必要操作，无需逐步确认<br/>
            <small>推荐：熟悉Linux的用户</small>
          </p>
          <div class="mode-features">
            <span class="feature-tag">✓ 自动安装Docker</span>
            <span class="feature-tag">✓ 配置镜像源</span>
            <span class="feature-tag">✓ 一键部署</span>
          </div>
          <div class="mode-time">预计用时：5-10分钟</div>
        </div>
        
        <div 
          class="mode-card" 
          :class="{ 'mode-selected': selectedMode === 'interactive' }"
          @click="selectMode('interactive')"
        >
          <div class="mode-icon">🛡️</div>
          <h4 class="mode-name">分步确认模式</h4>
          <p class="mode-description">
            每个关键步骤都需要用户确认<br/>
            <small>推荐：首次使用或谨慎操作</small>
          </p>
          <div class="mode-features">
            <span class="feature-tag">✓ 逐步确认</span>
            <span class="feature-tag">✓ 详细说明</span>
            <span class="feature-tag">✓ 安全可控</span>
          </div>
          <div class="mode-time">预计用时：10-20分钟</div>
        </div>
      </div>
      
      <!-- 系统状态检查 -->
      <div class="system-status-panel" v-if="selectedMode">
        <h4 class="status-title">🔍 系统状态检查</h4>
        
        <div v-if="systemInfo" class="status-checks">
          <div v-for="check in systemInfo.requirementChecks" 
               :key="check"
               class="status-item"
               :class="getCheckClass(check)">
            {{ check }}
          </div>
          
          <!-- Docker未安装时的特别提示 -->
          <div v-if="!systemInfo.dockerInstalled" class="docker-install-notice">
            <div class="notice-header">
              <span class="notice-icon">🐳</span>
              <span class="notice-title">Docker自动安装</span>
            </div>
            <div class="notice-content">
              <p>检测到系统未安装Docker，系统将在部署过程中自动安装。</p>
              <p>{{ selectedMode === 'trusted' ? '自动安装模式：无需用户确认' : '交互确认模式：每个安装步骤需要您的确认' }}</p>
            </div>
          </div>
        </div>
        
        <div v-else class="status-loading">
          <p>请先进行系统检查以了解当前状态</p>
          <button @click="$emit('validate-system')" class="btn btn-secondary">
            检查系统状态
          </button>
        </div>
      </div>
      
      <!-- 部署配置 -->
      <div class="deployment-config" v-if="selectedMode">
        <h4 class="config-title">部署配置</h4>
        <div class="config-form">
          <div class="form-group">
            <label class="form-label">SillyTavern版本</label>
            <select v-model="deploymentConfig.selectedVersion" class="form-select">
              <option value="latest">latest (推荐)</option>
              <option value="staging">staging (预发布)</option>
              <option value="release">release (稳定版)</option>
            </select>
          </div>
          
          <div class="form-group">
            <label class="form-label">访问端口</label>
            <input 
              type="number" 
              v-model="deploymentConfig.port" 
              class="form-input"
              min="1000" 
              max="65535"
              placeholder="8000"
            />
          </div>
          
          <div class="form-group">
            <label class="form-checkbox">
              <input 
                type="checkbox" 
                v-model="deploymentConfig.enableExternalAccess"
              />
              <span class="checkbox-text">开启外网访问（配置用户名密码）</span>
            </label>
          </div>
          
          <!-- 用户名密码配置 -->
          <div v-if="deploymentConfig.enableExternalAccess" class="auth-config">
            <div class="form-group">
              <label class="form-label">认证方式</label>
              <div class="auth-options">
                <label class="form-radio">
                  <input 
                    type="radio" 
                    v-model="deploymentConfig.authMode" 
                    value="manual"
                  />
                  <span class="radio-text">手动输入</span>
                </label>
                <label class="form-radio">
                  <input 
                    type="radio" 
                    v-model="deploymentConfig.authMode" 
                    value="random"
                  />
                  <span class="radio-text">随机生成</span>
                </label>
              </div>
            </div>
            
            <div v-if="deploymentConfig.authMode === 'manual'" class="manual-auth">
              <div class="form-group">
                <label class="form-label">用户名</label>
                <input 
                  type="text" 
                  v-model="deploymentConfig.username" 
                  class="form-input"
                  placeholder="请输入用户名（不能为纯数字）"
                />
              </div>
              <div class="form-group">
                <label class="form-label">密码</label>
                <input 
                  type="password" 
                  v-model="deploymentConfig.password" 
                  class="form-input"
                  placeholder="请输入密码（不能为纯数字）"
                />
              </div>
            </div>
          </div>
        </div>
        
        <!-- 开始部署按钮 -->
        <div class="start-deployment">
          <button 
            @click="startDeployment" 
            class="btn btn-primary btn-lg"
            :disabled="!isConfigValid"
          >
            <span class="btn-icon">🚀</span>
            开始{{ selectedMode === 'trusted' ? '一键' : '交互式' }}部署
          </button>
        </div>
      </div>
    </div>
    
    <!-- 部署进度展示 -->
    <div class="deployment-progress" v-if="deploymentStarted">
      <div class="progress-header">
        <h3 class="progress-title">
          <span class="progress-icon">⚙️</span>
          {{ selectedMode === 'trusted' ? '自动部署进行中' : '交互式部署进行中' }}
        </h3>
        <button 
          @click="cancelDeployment" 
          class="btn btn-danger btn-sm"
          :disabled="deploymentCompleted"
        >
          取消部署
        </button>
      </div>
      
      <!-- 横向卡片步骤展示 -->
      <div class="deployment-steps">
        <div 
          v-for="step in deploymentSteps" 
          :key="step.id"
          class="step-card"
          :class="getStepCardClass(step)"
        >
          <div class="step-header">
            <div class="step-status">
              <span class="status-icon">{{ getStepIcon(step.status) }}</span>
              <span class="status-text">{{ getStepStatusText(step.status) }}</span>
            </div>
            <div class="step-title">{{ step.title }}</div>
          </div>
          
          <div class="step-content">
            <!-- 步骤日志 -->  
            <div v-if="step.logs && step.logs.length > 0" class="step-logs">
              <div 
                v-for="(log, index) in step.logs" 
                :key="index"
                class="log-entry"
                :class="getLogEntryClass(log)"
              >
                <span class="log-time">{{ formatTime(log.timestamp) }}</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
            </div>
            
            <!-- Docker安装特殊提示 -->
            <div v-if="step.id === 'docker-installation' && step.status === 'running'" class="docker-install-info">
              <div class="install-info-header">
                <span class="info-icon">🐳</span>
                <span class="info-title">Docker自动安装</span>
              </div>
              <div class="install-info-content">
                <p>正在自动检测和安装Docker...</p>
                <div class="install-steps">
                  <div class="mini-step">✓ 检测系统类型</div>
                  <div class="mini-step">✓ 配置安装源</div>
                  <div class="mini-step active">🔄 安装Docker引擎</div>
                  <div class="mini-step">⏳ 启动Docker服务</div>
                  <div class="mini-step">⏳ 验证安装结果</div>
                </div>
                <p class="install-tip">首次安装可能需要5-10分钟，请耐心等待...</p>
              </div>
            </div>
            
            <!-- 用户交互区域 -->
            <div v-if="step.requiresConfirmation && step.status === 'waiting'" class="step-interaction">
              <div class="interaction-content">
                <div class="interaction-message">{{ step.confirmationMessage }}</div>
                
                <!-- 用户输入表单 -->
                <div v-if="step.userInput" class="user-input-form">
                  <div 
                    v-for="input in step.userInput" 
                    :key="input.name"
                    class="input-group"
                  >
                    <label class="input-label">{{ input.label }}</label>
                    <input 
                      v-if="input.type === 'text'"
                      :type="input.type"
                      v-model="userInputValues[input.name]"
                      class="form-input"
                      :placeholder="input.placeholder"
                    />
                    <select 
                      v-else-if="input.type === 'select'"
                      v-model="userInputValues[input.name]"
                      class="form-select"
                    >
                      <option 
                        v-for="option in input.options" 
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select>
                  </div>
                </div>
                
                <!-- 确认按钮 -->
                <div class="interaction-buttons">
                  <button 
                    @click="confirmStep(step.id, true)"
                    class="btn btn-success btn-sm"
                  >
                    <span class="btn-icon">✓</span>
                    确认执行
                  </button>
                  <button 
                    @click="confirmStep(step.id, false)"
                    class="btn btn-secondary btn-sm"
                  >
                    <span class="btn-icon">⏭️</span>
                    跳过
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 进度条 -->
            <div v-if="step.progress !== undefined" class="step-progress">
              <div class="progress-bar">
                <div 
                  class="progress-fill" 
                  :style="{ width: step.progress + '%' }"
                ></div>
              </div>
              <div class="progress-text">{{ step.progress }}%</div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 部署完成信息 -->
      <div v-if="deploymentCompleted" class="deployment-result">
        <div class="result-card" :class="deploymentSuccess ? 'result-success' : 'result-error'">
          <div class="result-icon">
            {{ deploymentSuccess ? '🎉' : '❌' }}
          </div>
          <div class="result-content">
            <h4 class="result-title">
              {{ deploymentSuccess ? '部署成功！' : '部署失败' }}
            </h4>
            <p class="result-message">{{ deploymentMessage }}</p>
            
            <!-- 访问信息 -->
            <div v-if="deploymentSuccess && accessInfo" class="access-info">
              <h5 class="access-title">访问信息</h5>
              <div class="access-details">
                <div class="access-item">
                  <span class="access-label">访问地址：</span>
                  <span class="access-value">
                    <a :href="accessInfo.url" target="_blank" class="access-link">
                      {{ accessInfo.url }}
                    </a>
                    <button 
                      @click="copyToClipboard(accessInfo.url)" 
                      class="btn btn-ghost btn-xs"
                      title="复制地址"
                    >
                      📋
                    </button>
                  </span>
                </div>
                <div v-if="accessInfo.username" class="access-item">
                  <span class="access-label">用户名：</span>
                  <span class="access-value">
                    <code>{{ accessInfo.username }}</code>
                    <button 
                      @click="copyToClipboard(accessInfo.username)" 
                      class="btn btn-ghost btn-xs"
                      title="复制用户名"
                    >
                      📋
                    </button>
                  </span>
                </div>
                <div v-if="accessInfo.password" class="access-item">
                  <span class="access-label">密码：</span>
                  <span class="access-value">
                    <code>{{ showPassword ? accessInfo.password : '••••••••' }}</code>
                    <button 
                      @click="togglePasswordVisibility" 
                      class="btn btn-ghost btn-xs"
                      :title="showPassword ? '隐藏密码' : '显示密码'"
                    >
                      {{ showPassword ? '👁️‍🗨️' : '👁️' }}
                    </button>
                    <button 
                      @click="copyToClipboard(accessInfo.password)" 
                      class="btn btn-ghost btn-xs"
                      title="复制密码"
                    >
                      📋
                    </button>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 操作按钮 -->
        <div class="result-actions">
          <button 
            @click="resetDeployment" 
            class="btn btn-primary"
          >
            重新部署
          </button>
          <button 
            @click="$emit('deployment-complete')" 
            class="btn btn-secondary"
          >
            返回管理界面
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'InteractiveDeploymentWizard',
  emits: ['deployment-complete', 'validate-system', 'deploy'],
  props: {
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
    },
    isDeploying: {
      type: Boolean,
      default: false
    },
    deploymentProgress: {
      type: Object,
      default: null
    }
  },
  
  data() {
    return {
      // 部署模式
      selectedMode: null, // 'trusted' | 'interactive'
      
      // 部署配置
      deploymentConfig: {
        selectedVersion: 'latest',
        port: '8000',
        enableExternalAccess: false,
        authMode: 'random', // 'manual' | 'random'
        username: '',
        password: ''
      },
      
      // 部署状态
      deploymentStarted: false,
      deploymentCompleted: false,
      deploymentSuccess: false,
      deploymentMessage: '',
      
      // 用户输入
      userInputValues: {},
      
      // 访问信息
      accessInfo: null,
      showPassword: false,
      
      // 部署步骤
      deploymentSteps: [
        {
          id: 'geo-detection',
          title: '地理位置检测',
          status: 'pending', // pending | running | completed | error | waiting
          requiresConfirmation: false,
          logs: [],
          progress: 0
        },
        {
          id: 'system-detection',
          title: '系统检测',
          status: 'pending',
          requiresConfirmation: false,
          logs: [],
          progress: 0
        },
        {
          id: 'package-manager',
          title: '包管理器配置',
          status: 'pending',
          requiresConfirmation: true,
          confirmationMessage: '是否配置系统镜像源以加速软件包下载？',
          logs: [],
          progress: 0
        },
        {
          id: 'docker-installation',
          title: 'Docker安装',
          status: 'pending',
          requiresConfirmation: false,
          logs: [],
          progress: 0
        },
        {
          id: 'docker-mirror',
          title: 'Docker镜像加速',
          status: 'pending',
          requiresConfirmation: true,
          confirmationMessage: '是否配置Docker镜像加速器？',
          logs: [],
          progress: 0
        },
        {
          id: 'sillytavern-deployment',
          title: 'SillyTavern部署',
          status: 'pending',
          requiresConfirmation: false,
          logs: [],
          progress: 0
        },
        {
          id: 'external-access',
          title: '外网访问配置',
          status: 'pending',
          requiresConfirmation: false,
          logs: [],
          progress: 0
        },
        {
          id: 'service-validation',
          title: '服务验证',
          status: 'pending',
          requiresConfirmation: false,
          logs: [],
          progress: 0
        }
      ]
    }
  },
  
  computed: {
    isConfigValid() {
      if (!this.selectedMode) return false
      if (!this.deploymentConfig.port || this.deploymentConfig.port < 1000 || this.deploymentConfig.port > 65535) return false
      
      if (this.deploymentConfig.enableExternalAccess && this.deploymentConfig.authMode === 'manual') {
        if (!this.deploymentConfig.username || !this.deploymentConfig.password) return false
        if (/^\d+$/.test(this.deploymentConfig.username) || /^\d+$/.test(this.deploymentConfig.password)) return false
      }
      
      return true
    }
  },
  
  methods: {
    selectMode(mode) {
      this.selectedMode = mode
    },
    
    getCheckClass(check) {
      if (check.startsWith('✓')) return 'status-pass'
      if (check.startsWith('✗')) return 'status-fail'
      if (check.startsWith('⚠')) return 'status-warning'
      return 'status-info'
    },
    
    startDeployment() {
      // 使用真正的交互式部署API而不是模拟
      const deploymentRequest = {
        deploymentMode: this.selectedMode, // 修正字段名从 mode 到 deploymentMode
        customConfig: this.deploymentConfig, // 修正字段名从 config 到 customConfig
        enableLogging: true,
        timeoutSeconds: 300
      }
      
      // 调用父组件的部署方法，传递交互式部署配置
      this.$emit('deploy', deploymentRequest)
    },
    
    confirmStep(stepId, confirmed) {
      const step = this.deploymentSteps.find(s => s.id === stepId)
      if (!step) return
      
      const userInput = step.userInput ? 
        Object.fromEntries(step.userInput.map(input => [input.name, this.userInputValues[input.name]])) : {}
      
      this.$emit('step-confirmed', {
        stepId,
        confirmed,
        userInput
      })
    },
    
    cancelDeployment() {
      if (confirm('确定要取消部署吗？已执行的操作可能无法撤销。')) {
        this.$emit('deployment-cancelled', '用户主动取消')
      }
    },
    
    resetDeployment() {
      this.deploymentStarted = false
      this.deploymentCompleted = false
      this.deploymentSuccess = false
      this.selectedMode = null
      this.accessInfo = null
      this.showPassword = false
      this.userInputValues = {}
    },
    
    // 步骤状态处理
    updateStepStatus(stepId, status, data = {}) {
      const step = this.deploymentSteps.find(s => s.id === stepId)
      if (!step) return
      
      step.status = status
      if (data.progress !== undefined) step.progress = data.progress
      if (data.message) {
        step.logs.push({
          timestamp: new Date(),
          message: data.message,
          type: data.logType || 'info'
        })
      }
    },
    
    addStepLog(stepId, message, type = 'info') {
      const step = this.deploymentSteps.find(s => s.id === stepId)
      if (!step) return
      
      step.logs.push({
        timestamp: new Date(),
        message,
        type
      })
    },
    
    completeDeployment(success, message, accessInfo = null) {
      this.deploymentCompleted = true
      this.deploymentSuccess = success
      this.deploymentMessage = message
      this.accessInfo = accessInfo
    },
    
    // UI辅助方法
    getStepCardClass(step) {
      return {
        'step-pending': step.status === 'pending',
        'step-running': step.status === 'running',
        'step-completed': step.status === 'completed',
        'step-error': step.status === 'error',
        'step-waiting': step.status === 'waiting'
      }
    },
    
    getStepIcon(status) {
      const icons = {
        pending: '⏳',
        running: '🔄',
        completed: '✅',
        error: '❌',
        waiting: '⏸️'
      }
      return icons[status] || '⏳'
    },
    
    getStepStatusText(status) {
      const texts = {
        pending: '等待中',
        running: '执行中',
        completed: '已完成',
        error: '失败',
        waiting: '等待确认'
      }
      return texts[status] || '未知'
    },
    
    getLogEntryClass(log) {
      return {
        'log-info': log.type === 'info',
        'log-warning': log.type === 'warning',
        'log-error': log.type === 'error',
        'log-success': log.type === 'success'
      }
    },
    
    formatTime(timestamp) {
      return timestamp.toLocaleTimeString()
    },
    
    copyToClipboard(text) {
      navigator.clipboard.writeText(text).then(() => {
        // 这里可以添加复制成功的提示
        alert('已复制到剪贴板')
      }).catch(err => {
        console.error('复制失败:', err)
      })
    },
    
    togglePasswordVisibility() {
      this.showPassword = !this.showPassword
    }
  }
}
</script>

<style scoped>
.interactive-deployment-wizard {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* 模式选择器样式 */
.deployment-mode-selector {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
}

.mode-header {
  text-align: center;
  margin-bottom: 24px;
}

.mode-title {
  font-size: 24px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.mode-subtitle {
  color: #6c757d;
  margin: 0;
}

.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

/* 系统状态面板样式 */
.system-status-panel {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.status-title {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 16px 0;
}

.status-checks {
  space-y: 8px;
}

.status-item {
  padding: 8px 12px;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  margin-bottom: 4px;
}

.status-pass {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.status-fail {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.status-warning {
  background: #fff3cd;
  color: #856404;
  border: 1px solid #ffeaa7;
}

.status-info {
  background: #d1ecf1;
  color: #0c5460;
  border: 1px solid #bee5eb;
}

.docker-install-notice {
  background: #e3f2fd;
  border: 2px solid #2196f3;
  border-radius: 8px;
  padding: 16px;
  margin-top: 12px;
}

.notice-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.notice-icon {
  font-size: 20px;
  margin-right: 8px;
}

.notice-title {
  font-weight: 600;
  color: #1976d2;
}

.notice-content p {
  margin: 4px 0;
  color: #0d47a1;
  font-size: 14px;
}

.status-loading {
  text-align: center;
  padding: 20px;
  color: #6c757d;
}

.mode-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
}

.mode-card:hover {
  border-color: #007bff;
  box-shadow: 0 4px 12px rgba(0, 123, 255, 0.1);
}

.mode-card.mode-selected {
  border-color: #007bff;
  background: #f8f9ff;
  box-shadow: 0 4px 12px rgba(0, 123, 255, 0.15);
}

.mode-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.mode-name {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.mode-description {
  color: #6c757d;
  margin: 0 0 16px 0;
  line-height: 1.5;
}

.mode-features {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  margin-bottom: 12px;
}

.feature-tag {
  background: #e3f2fd;
  color: #1976d2;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.mode-time {
  font-size: 14px;
  color: #28a745;
  font-weight: 500;
}

/* 配置表单样式 */
.deployment-config {
  background: white;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e9ecef;
}

.config-title {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 20px 0;
}

.config-form {
  display: grid;
  gap: 16px;
  margin-bottom: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-weight: 500;
  color: #495057;
}

.form-input, .form-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
}

.form-input:focus, .form-select:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.form-checkbox, .form-radio {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.auth-options {
  display: flex;
  gap: 16px;
}

.auth-config {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.manual-auth {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.start-deployment {
  text-align: center;
}

/* 部署进度样式 */
.deployment-progress {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 24px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.progress-title {
  font-size: 20px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.deployment-steps {
  display: grid;
  gap: 16px;
}

.step-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.2s ease;
}

.step-card.step-running {
  border-color: #007bff;
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.1);
}

.step-card.step-completed {
  border-color: #28a745;
  background: #f8fff9;
}

.step-card.step-error {
  border-color: #dc3545;
  background: #fff8f8;
}

.step-card.step-waiting {
  border-color: #ffc107;
  background: #fffbf0;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.step-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
}

.step-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.step-logs {
  max-height: 200px;
  overflow-y: auto;
  background: #f8f9fa;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}

.log-entry {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
  line-height: 1.4;
}

.log-time {
  color: #6c757d;
  flex-shrink: 0;
}

.log-message {
  flex: 1;
}

.log-info { color: #495057; }
.log-success { color: #28a745; }
.log-warning { color: #ffc107; }
.log-error { color: #dc3545; }

/* Docker安装信息样式 */
.docker-install-info {
  background: #e8f4fd;
  border: 1px solid #2196f3;
  border-radius: 8px;
  padding: 16px;
  margin: 12px 0;
}

.install-info-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.info-icon {
  font-size: 1.3rem;
  margin-right: 8px;
}

.info-title {
  font-weight: 600;
  color: #1976d2;
  font-size: 1.1rem;
}

.install-info-content {
  color: #424242;
}

.install-steps {
  margin: 12px 0;
  padding: 12px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
}

.mini-step {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 0.9rem;
  opacity: 0.6;
}

.mini-step.active {
  opacity: 1;
  font-weight: 500;
  color: #1976d2;
}

.install-tip {
  font-size: 0.85rem;
  color: #666;
  font-style: italic;
  margin: 8px 0 0 0;
}

.step-interaction {
  background: #fff;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}

.interaction-message {
  margin-bottom: 16px;
  font-weight: 500;
  color: #495057;
}

.user-input-form {
  margin-bottom: 16px;
}

.input-group {
  margin-bottom: 12px;
}

.input-label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
  color: #495057;
}

.interaction-buttons {
  display: flex;
  gap: 8px;
}

.step-progress {
  display: flex;
  align-items: center;
  gap: 12px;
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
  background: #007bff;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  font-weight: 500;
  color: #495057;
}

/* 部署结果样式 */
.deployment-result {
  margin-top: 24px;
}

.result-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  margin-bottom: 20px;
}

.result-card.result-success {
  border: 2px solid #28a745;
  background: #f8fff9;
}

.result-card.result-error {
  border: 2px solid #dc3545;
  background: #fff8f8;
}

.result-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.result-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.result-message {
  color: #6c757d;
  margin: 0 0 20px 0;
}

.access-info {
  text-align: left;
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
}

.access-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px 0;
}

.access-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.access-label {
  min-width: 80px;
  font-weight: 500;
  color: #495057;
}

.access-value {
  display: flex;
  align-items: center;
  gap: 8px;
}

.access-link {
  color: #007bff;
  text-decoration: none;
}

.access-link:hover {
  text-decoration: underline;
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* 按钮样式 */
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

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-success {
  background: #28a745;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: #1e7e34;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #c82333;
}

.btn-ghost {
  background: transparent;
  color: #6c757d;
  border: 1px solid #e9ecef;
}

.btn-ghost:hover:not(:disabled) {
  background: #f8f9fa;
}

.btn-sm {
  padding: 4px 8px;
  font-size: 12px;
}

.btn-xs {
  padding: 2px 6px;
  font-size: 11px;
}

.btn-lg {
  padding: 12px 24px;
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .mode-options {
    grid-template-columns: 1fr;
  }
  
  .step-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .progress-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .interaction-buttons {
    flex-direction: column;
  }
  
  .result-actions {
    flex-direction: column;
  }
}
</style>