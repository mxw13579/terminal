<template>
  <div class="script-builder">
    <!-- 头部工具栏 -->
    <div class="builder-header">
      <div class="header-left">
        <h1>
          <el-icon><Setting /></el-icon>
          可视化环境配置
        </h1>
        <p class="header-desc">拖拽命令构建自动化脚本，小白也能轻松配置服务器环境</p>
      </div>
      <div class="header-actions">
        <el-button 
          type="info" 
          @click="showSshConfig = true"
          :icon="Connection"
        >
          SSH配置
        </el-button>
        <el-button 
          type="warning" 
          @click="validateScript"
          :disabled="scriptCommands.length === 0"
          :icon="Check"
        >
          验证脚本
        </el-button>
        <el-button 
          type="success" 
          @click="executeScript" 
          :disabled="!canExecute"
          :loading="isExecuting"
          :icon="VideoPlay"
        >
          {{ isExecuting ? '执行中...' : '执行脚本' }}
        </el-button>
        <el-button 
          @click="goToTerminal"
          :icon="Monitor"
        >
          专业终端
        </el-button>
      </div>
    </div>

    <div class="builder-content">
      <!-- 左侧命令面板 -->
      <div class="command-panel">
        <div class="panel-header">
          <h3>可用命令</h3>
          <el-tag type="info" size="small">{{ totalCommands }} 个命令</el-tag>
        </div>
        
        <div class="command-categories">
          <div 
            v-for="(commands, category) in categorizedCommands" 
            :key="category" 
            class="category-section"
          >
            <h4 class="category-title">
              <el-icon>
                <component :is="getCategoryIcon(category)" />
              </el-icon>
              {{ category }}
              <el-tag size="small" type="primary">{{ commands.length }}</el-tag>
            </h4>
            
            <draggable
              :list="commands"
              :group="{ name: 'commands', pull: 'clone', put: false }"
              :sort="false"
              class="command-list"
              item-key="id"
            >
              <template #item="{ element: command }">
                <div
                  class="command-item"
                  :class="{ 'required': command.required }"
                  :title="command.description"
                >
                  <div class="command-header">
                    <el-icon class="command-icon">
                      <component :is="command.icon || 'Setting'" />
                    </el-icon>
                    <span class="command-name">{{ command.name }}</span>
                    <el-tag v-if="command.required" size="mini" type="warning">必须</el-tag>
                  </div>
                  <p class="command-desc">{{ command.description }}</p>
                </div>
              </template>
            </draggable>
          </div>
        </div>
      </div>

      <!-- 中间脚本编辑区域 -->
      <div class="script-editor">
        <div class="editor-header">
          <h3>脚本流程</h3>
          <div class="editor-actions">
            <el-button 
              size="small" 
              @click="clearScript"
              :disabled="scriptCommands.length === 0"
              :icon="Delete"
            >
              清空
            </el-button>
            <el-button 
              size="small" 
              @click="optimizeScript"
              :disabled="scriptCommands.length === 0"
              :icon="Magic"
            >
              自动优化
            </el-button>
          </div>
        </div>
        
        <div class="script-flow-container">
          <draggable
            v-model="scriptCommands"
            group="commands"
            class="script-flow"
            :class="{ 'executing': isExecuting }"
            item-key="id"
            @change="onScriptChange"
          >
            <template #item="{ element: command, index }">
              <div
                class="script-command"
                :class="{
                  'executing': command.status === 'executing',
                  'completed': command.status === 'completed', 
                  'failed': command.status === 'failed',
                  'skipped': command.status === 'skipped'
                }"
              >
                <div class="command-header">
                  <div class="step-info">
                    <span class="step-number">{{ index + 1 }}</span>
                    <div class="command-details">
                      <span class="command-name">{{ command.name }}</span>
                      <span class="command-category">{{ command.category }}</span>
                    </div>
                  </div>
                  <div class="command-actions">
                    <el-button
                      type="text"
                      size="small"
                      @click="removeCommand(index)"
                      :icon="Close"
                      :disabled="isExecuting"
                    />
                  </div>
                </div>
                
                <div class="command-status" v-if="command.status">
                  <el-tag :type="getStatusType(command.status)" size="small">
                    {{ getStatusText(command.status) }}
                  </el-tag>
                  <span v-if="command.message" class="status-message">
                    {{ command.message }}
                  </span>
                  <span v-if="command.duration" class="duration">
                    {{ formatDuration(command.duration) }}
                  </span>
                </div>
                
                <p class="command-description">{{ command.description }}</p>
                
                <!-- 依赖关系提示 -->
                <div v-if="command.dependencies && command.dependencies.length > 0" class="dependencies">
                  <el-icon><Link /></el-icon>
                  <span>依赖: {{ getDependencyNames(command.dependencies).join(', ') }}</span>
                </div>
              </div>
            </template>
            
            <template #footer>
              <div v-if="scriptCommands.length === 0" class="empty-script">
                <el-icon class="empty-icon"><Plus /></el-icon>
                <h4>开始构建你的脚本</h4>
                <p>从左侧拖拽命令到这里，构建自动化配置脚本</p>
                <el-button type="primary" @click="addRecommendedCommands">
                  添加推荐命令
                </el-button>
              </div>
            </template>
          </draggable>
        </div>
      </div>

      <!-- 右侧执行进度面板 -->
      <div class="progress-panel" v-show="showProgressPanel">
        <div class="panel-header">
          <h3>执行进度</h3>
          <el-button 
            type="text" 
            @click="showProgressPanel = false"
            :icon="Close"
          />
        </div>
        
        <div class="progress-content">
          <div class="progress-overview">
            <el-progress 
              :percentage="progressPercentage" 
              :status="progressStatus"
              :stroke-width="8"
            />
            <div class="progress-text">
              <span class="current-step">{{ currentStep }}/{{ totalSteps }}</span>
              <span class="progress-percent">{{ progressPercentage }}%</span>
            </div>
          </div>
          
          <div class="current-command" v-if="currentCommand">
            <h4>当前执行</h4>
            <div class="command-info">
              <el-icon class="spinning"><Loading /></el-icon>
              <span>{{ currentCommand }}</span>
            </div>
          </div>
          
          <div class="execution-log">
            <h4>执行日志</h4>
            <div class="log-list" ref="logContainer">
              <div 
                v-for="(log, index) in executionLogs" 
                :key="index"
                class="log-item"
                :class="'log-' + log.status"
              >
                <span class="log-time">{{ formatTime(log.time) }}</span>
                <span class="log-command">{{ log.command }}</span>
                <span class="log-status">{{ getStatusText(log.status) }}</span>
                <span v-if="log.message" class="log-message">{{ log.message }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- SSH配置对话框 -->
    <el-dialog 
      v-model="showSshConfig" 
      title="SSH连接配置" 
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="sshConfig" label-width="80px" @submit.prevent>
        <el-form-item label="主机地址" required>
          <el-input 
            v-model="sshConfig.host" 
            placeholder="服务器IP地址或域名"
            :prefix-icon="Connection"
          />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number 
            v-model="sshConfig.port" 
            :min="1" 
            :max="65535"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input 
            v-model="sshConfig.username" 
            placeholder="SSH用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input 
            v-model="sshConfig.password" 
            type="password" 
            placeholder="SSH密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button 
            type="primary" 
            @click="testConnection" 
            :loading="testingConnection"
            :disabled="!isConfigValid"
          >
            {{ testingConnection ? '测试中...' : '测试连接' }}
          </el-button>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showSshConfig = false">取消</el-button>
        <el-button type="primary" @click="saveSshConfig" :disabled="!isConfigValid">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 脚本验证对话框 -->
    <el-dialog 
      v-model="showValidationResult" 
      title="脚本验证结果" 
      width="500px"
    >
      <div v-if="validationResult">
        <el-alert 
          :type="validationResult.valid ? 'success' : 'warning'"
          :title="validationResult.valid ? '脚本验证通过' : '发现问题，已自动优化'"
          show-icon
          :closable="false"
        />
        
        <div class="validation-details" style="margin-top: 15px;">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="原始命令数">
              {{ validationResult.original?.length || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="优化后命令数">
              {{ validationResult.optimized?.length || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="新增依赖" v-if="validationResult.addedDependencies > 0">
              <el-tag type="info">{{ validationResult.addedDependencies }} 个</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          
          <div v-if="validationResult.addedDependencies > 0" style="margin-top: 15px;">
            <el-button type="primary" @click="applyOptimization">
              应用优化建议
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import http from '@/utils/http'
import webSocketClient from '@/utils/websocket'
import {
  Setting, Connection, Check, VideoPlay, Monitor, Delete, MagicStick, 
  Close, Plus, Loading, Link, User, Lock, Cpu, Search, Download
} from '@element-plus/icons-vue'

export default {
  name: 'ScriptBuilder',
  components: {
    draggable,
    Setting, Connection, Check, VideoPlay, Monitor, Delete, MagicStick, 
    Close, Plus, Loading, Link, User, Lock, Cpu, Search, Download
  },
  setup() {
    const router = useRouter()
    
    // 响应式数据
    const categorizedCommands = ref({})
    const scriptCommands = ref([])
    const showSshConfig = ref(false)
    const showProgressPanel = ref(false)
    const showValidationResult = ref(false)
    const validationResult = ref(null)
    const isExecuting = ref(false)
    const testingConnection = ref(false)
    const currentStep = ref(0)
    const totalSteps = ref(0)
    const currentCommand = ref('')
    const progressStatus = ref(null)
    const executionLogs = ref([])
    
    const sshConfig = reactive({
      host: '',
      port: 22,
      username: '',
      password: ''
    })
    
    // 计算属性
    const totalCommands = computed(() => {
      return Object.values(categorizedCommands.value)
        .reduce((total, commands) => total + commands.length, 0)
    })
    
    const isConfigValid = computed(() => {
      return sshConfig.host && sshConfig.username && sshConfig.password
    })
    
    const canExecute = computed(() => {
      return scriptCommands.value.length > 0 && 
             isConfigValid.value && 
             !isExecuting.value
    })
    
    const progressPercentage = computed(() => {
      return totalSteps.value > 0 ? Math.round((currentStep.value / totalSteps.value) * 100) : 0
    })
    
    // 方法
    const loadCommands = async () => {
      try {
        const response = await http.get('/api/script/commands')
        categorizedCommands.value = response.data.categories || {}
        console.log('命令加载成功:', response.data)
      } catch (error) {
        ElMessage.error('加载命令失败')
        console.error('加载命令失败:', error)
      }
    }
    
    const connectWebSocket = async () => {
      try {
        await webSocketClient.connect()
        
        // 订阅脚本执行进度
        webSocketClient.subscribe('/topic/script-progress', (progress) => {
          updateProgress(progress)
        })
        
        // 订阅错误消息
        webSocketClient.subscribe('/topic/script-error', (error) => {
          console.error('脚本执行错误:', error)
          ElMessage.error('脚本执行失败: ' + error.error)
          isExecuting.value = false
        })
        
      } catch (error) {
        console.error('WebSocket连接失败:', error)
        ElMessage.warning('实时进度功能不可用，但不影响脚本执行')
      }
    }
    
    const updateProgress = (progress) => {
      currentStep.value = progress.current
      totalSteps.value = progress.total
      currentCommand.value = progress.commandName
      
      // 显示进度面板
      if (!showProgressPanel.value) {
        showProgressPanel.value = true
      }
      
      // 更新对应命令的状态
      const command = scriptCommands.value.find(cmd => cmd.name === progress.commandName)
      if (command) {
        command.status = progress.status
        command.message = progress.message
        command.duration = progress.getDuration ? progress.getDuration() : 0
      }
      
      // 添加到执行日志
      executionLogs.value.push({
        time: Date.now(),
        command: progress.commandName,
        status: progress.status,
        message: progress.message
      })
      
      // 自动滚动日志
      nextTick(() => {
        const container = document.querySelector('.log-list')
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
      
      // 检查是否执行完成
      if (progress.current >= progress.total) {
        isExecuting.value = false
        progressStatus.value = 'success'
        ElMessage.success('脚本执行完成！')
      }
    }
    
    const getCategoryIcon = (category) => {
      const icons = {
        '前置处理': 'Cpu',
        '环境检查': 'Search', 
        '安装增强': 'Download'
      }
      return icons[category] || 'Setting'
    }
    
    const getStatusType = (status) => {
      const types = {
        'executing': 'primary',
        'completed': 'success',
        'failed': 'danger',
        'skipped': 'warning'
      }
      return types[status] || 'info'
    }
    
    const getStatusText = (status) => {
      const texts = {
        'executing': '执行中',
        'completed': '完成',
        'failed': '失败', 
        'skipped': '跳过'
      }
      return texts[status] || status
    }
    
    const formatDuration = (duration) => {
      if (!duration) return ''
      return duration < 1000 ? `${duration}ms` : `${(duration/1000).toFixed(1)}s`
    }
    
    const formatTime = (timestamp) => {
      return new Date(timestamp).toLocaleTimeString()
    }
    
    const getDependencyNames = (dependencies) => {
      return dependencies.map(depId => {
        // 从所有命令中查找依赖的名称
        for (const commands of Object.values(categorizedCommands.value)) {
          const cmd = commands.find(c => c.id === depId)
          if (cmd) return cmd.name
        }
        return depId
      })
    }
    
    const onScriptChange = () => {
      // 清除执行状态
      scriptCommands.value.forEach(cmd => {
        cmd.status = null
        cmd.message = null
        cmd.duration = 0
      })
    }
    
    const removeCommand = (index) => {
      scriptCommands.value.splice(index, 1)
    }
    
    const clearScript = () => {
      ElMessageBox.confirm('确定要清空所有命令吗？', '确认清空', {
        type: 'warning'
      }).then(() => {
        scriptCommands.value = []
        ElMessage.success('脚本已清空')
      }).catch(() => {})
    }
    
    const addRecommendedCommands = () => {
      // 添加推荐的命令组合
      const recommended = ['detect-os', 'detect-location', 'check-curl', 'config-mirrors']
      
      for (const cmdId of recommended) {
        for (const commands of Object.values(categorizedCommands.value)) {
          const cmd = commands.find(c => c.id === cmdId)
          if (cmd && !scriptCommands.value.find(sc => sc.id === cmd.id)) {
            scriptCommands.value.push({ ...cmd })
          }
        }
      }
      
      ElMessage.success('已添加推荐命令')
    }
    
    const validateScript = async () => {
      if (scriptCommands.value.length === 0) {
        ElMessage.warning('请先添加命令')
        return
      }
      
      try {
        const commandIds = scriptCommands.value.map(cmd => cmd.id)
        const response = await http.post('/api/script/validate', commandIds)
        validationResult.value = response.data
        showValidationResult.value = true
      } catch (error) {
        ElMessage.error('验证脚本失败')
      }
    }
    
    const optimizeScript = async () => {
      if (scriptCommands.value.length === 0) {
        ElMessage.warning('请先添加命令')
        return
      }
      
      await validateScript()
    }
    
    const applyOptimization = () => {
      if (validationResult.value && validationResult.value.optimized) {
        // 应用优化建议，重新构建脚本命令列表
        const optimizedIds = validationResult.value.optimized
        const newCommands = []
        
        for (const cmdId of optimizedIds) {
          for (const commands of Object.values(categorizedCommands.value)) {
            const cmd = commands.find(c => c.id === cmdId)
            if (cmd) {
              newCommands.push({ ...cmd })
              break
            }
          }
        }
        
        scriptCommands.value = newCommands
        showValidationResult.value = false
        ElMessage.success('脚本已优化')
      }
    }
    
    const testConnection = async () => {
      testingConnection.value = true
      try {
        const response = await http.post('/api/script/test-connection', {
          sshConfig: sshConfig
        })
        
        if (response.data.connected) {
          ElMessage.success('连接测试成功！')
        } else {
          ElMessage.error('连接测试失败: ' + response.data.message)
        }
      } catch (error) {
        ElMessage.error('连接测试失败')
      } finally {
        testingConnection.value = false
      }
    }
    
    const saveSshConfig = () => {
      showSshConfig.value = false
      ElMessage.success('SSH配置已保存')
    }
    
    const executeScript = async () => {
      if (!canExecute.value) return
      
      // 重置执行状态
      isExecuting.value = true
      currentStep.value = 0
      totalSteps.value = scriptCommands.value.length
      progressStatus.value = null
      executionLogs.value = []
      showProgressPanel.value = true
      
      // 清除命令状态
      onScriptChange()
      
      try {
        const response = await http.post('/api/script/execute', {
          commandIds: scriptCommands.value.map(cmd => cmd.id),
          sshConfig: sshConfig,
          autoOptimize: true
        })
        
        if (response.data.success) {
          ElMessage.success('脚本开始执行，请查看右侧进度面板')
        } else {
          throw new Error(response.data.error || '启动失败')
        }
      } catch (error) {
        ElMessage.error('启动脚本执行失败: ' + error.message)
        isExecuting.value = false
      }
    }
    
    const goToTerminal = () => {
      router.push('/terminal')
    }
    
    // 生命周期
    onMounted(async () => {
      await loadCommands()
      await connectWebSocket()
    })
    
    onUnmounted(() => {
      webSocketClient.disconnect()
    })
    
    return {
      // 数据
      categorizedCommands,
      scriptCommands,
      showSshConfig,
      showProgressPanel,
      showValidationResult,
      validationResult,
      isExecuting,
      testingConnection,
      currentStep,
      totalSteps,
      currentCommand,
      progressStatus,
      executionLogs,
      sshConfig,
      
      // 计算属性
      totalCommands,
      isConfigValid,
      canExecute,
      progressPercentage,
      
      // 方法
      getCategoryIcon,
      getStatusType,
      getStatusText,
      formatDuration,
      formatTime,
      getDependencyNames,
      onScriptChange,
      removeCommand,
      clearScript,
      addRecommendedCommands,
      validateScript,
      optimizeScript,
      applyOptimization,
      testConnection,
      saveSshConfig,
      executeScript,
      goToTerminal
    }
  }
}
</script>

<style scoped>
.script-builder {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.builder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 30px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.header-left h1 {
  margin: 0 0 5px 0;
  font-size: 24px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-desc {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.builder-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 命令面板样式 */
.command-panel {
  width: 280px;
  background: white;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.panel-header {
  padding: 20px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.command-categories {
  padding: 15px;
}

.category-section {
  margin-bottom: 25px;
}

.category-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.command-list {
  min-height: 50px;
}

.command-item {
  background: #f8fafc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: grab;
  transition: all 0.2s ease;
}

.command-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64,158,255,0.15);
  transform: translateY(-1px);
}

.command-item.required {
  border-left: 3px solid #E6A23C;
}

.command-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.command-icon {
  color: #409EFF;
  font-size: 16px;
}

.command-name {
  font-weight: 500;
  color: #303133;
  flex: 1;
}

.command-desc {
  margin: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

/* 脚本编辑器样式 */
.script-editor {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.editor-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.script-flow-container {
  background: white;
  border-radius: 8px;
  min-height: 500px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.script-flow {
  min-height: 500px;
  padding: 20px;
  border: 2px dashed #d9ecff;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.script-flow.executing {
  border-color: #409EFF;
  background: #f0f8ff;
}

.script-command {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 15px;
  padding: 16px;
  transition: all 0.3s ease;
  position: relative;
}

.script-command.executing {
  border-color: #409EFF;
  box-shadow: 0 0 12px rgba(64,158,255,0.25);
  background: linear-gradient(90deg, #f0f8ff 0%, white 100%);
}

.script-command.completed {
  border-color: #67C23A;
  background: #f0f9ff;
}

.script-command.failed {
  border-color: #F56C6C;
  background: #fef0f0;
}

.script-command.skipped {
  border-color: #E6A23C;
  background: #fdf6ec;
  opacity: 0.8;
}

.script-command .command-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.step-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-number {
  background: #409EFF;
  color: white;
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 500;
}

.command-details {
  display: flex;
  flex-direction: column;
}

.command-name {
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.command-category {
  font-size: 12px;
  color: #909399;
}

.command-status {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-message {
  color: #606266;
  font-size: 12px;
}

.duration {
  color: #909399;
  font-size: 11px;
  margin-left: auto;
}

.command-description {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.4;
}

.dependencies {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.empty-script {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #d3d9e3;
}

.empty-script h4 {
  margin: 0 0 8px 0;
  color: #606266;
  font-size: 16px;
}

.empty-script p {
  margin: 0 0 20px 0;
  font-size: 14px;
}

/* 进度面板样式 */
.progress-panel {
  width: 320px;
  background: white;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.progress-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.progress-overview {
  margin-bottom: 25px;
}

.progress-text {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.current-step {
  font-size: 14px;
  color: #606266;
}

.progress-percent {
  font-size: 14px;
  font-weight: 600;
  color: #409EFF;
}

.current-command {
  margin-bottom: 25px;
}

.current-command h4 {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: #303133;
}

.command-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f0f8ff;
  border-radius: 6px;
  border-left: 3px solid #409EFF;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.execution-log h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #303133;
}

.log-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafbfc;
}

.log-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-item:last-child {
  border-bottom: none;
}

.log-time {
  color: #909399;
  min-width: 60px;
}

.log-command {
  color: #303133;
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.log-executing .log-status {
  background: #ecf5ff;
  color: #409EFF;
}

.log-completed .log-status {
  background: #f0f9ff;
  color: #67C23A;
}

.log-failed .log-status {
  background: #fef0f0;
  color: #F56C6C;
}

.log-skipped .log-status {
  background: #fdf6ec;
  color: #E6A23C;
}

.log-message {
  color: #909399;
  font-size: 11px;
  margin-left: auto;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 验证结果对话框样式 */
.validation-details {
  margin-top: 15px;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .command-panel {
    width: 240px;
  }
  
  .progress-panel {
    width: 280px;
  }
}

@media (max-width: 768px) {
  .builder-header {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }
  
  .header-actions {
    justify-content: center;
    flex-wrap: wrap;
  }
  
  .builder-content {
    flex-direction: column;
  }
  
  .command-panel,
  .progress-panel {
    width: 100%;
    height: 200px;
  }
  
  .script-editor {
    flex: 1;
    min-height: 400px;
  }
}
</style>