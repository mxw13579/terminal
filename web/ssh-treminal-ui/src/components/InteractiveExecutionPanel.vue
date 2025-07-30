<template>
  <div class="interactive-execution-panel">
    <div class="header">
      <h3>{{ scriptName }}</h3>
      <div class="controls">
        <button 
          v-if="executionStatus === 'idle'" 
          @click="startExecution" 
          class="btn btn-primary"
          :disabled="loading"
        >
          {{ loading ? '启动中...' : '开始执行' }}
        </button>
        <button 
          v-if="executionStatus === 'running'" 
          @click="cancelExecution" 
          class="btn btn-danger"
        >
          取消执行
        </button>
      </div>
    </div>
    
    <div class="execution-log" ref="logContainer">
      <div 
        v-for="message in executionMessages" 
        :key="message.stepId"
        :class="['step', `step-${message.status.toLowerCase()}`]"
      >
        <div class="step-header">
          <span class="status-icon">
            <i :class="getStatusIcon(message.status)"></i>
          </span>
          <span class="step-name">{{ message.stepName }}</span>
          <span class="timestamp">{{ formatTime(message.timestamp) }}</span>
        </div>
        
        <div class="step-content">
          <p class="message">{{ message.message }}</p>
          
          <!-- 命令输出 -->
          <pre v-if="message.output" class="output">{{ message.output }}</pre>
          
          <!-- 进度条 -->
          <div v-if="message.progress !== undefined" class="progress">
            <div 
              class="progress-bar" 
              :style="{ width: message.progress + '%' }"
            >
              {{ message.progress }}%
            </div>
          </div>
          
          <!-- 交互确认按钮 -->
          <div 
            v-if="message.status === 'WAITING_CONFIRM' && message.interaction"
            class="interaction-confirm"
          >
            <p class="interaction-prompt">{{ message.interaction.prompt }}</p>
            <div class="confirm-buttons">
              <button 
                v-for="option in message.interaction.options"
                :key="option"
                @click="handleConfirmation(message.stepId, option)"
                :class="['btn', option === '是' ? 'btn-primary' : 'btn-secondary']"
              >
                {{ option }}
              </button>
            </div>
          </div>
          
          <!-- 交互表单 -->
          <div 
            v-if="message.status === 'WAITING_INPUT' && message.interaction"
            class="interaction-form"
          >
            <p class="interaction-prompt">{{ message.interaction.prompt }}</p>
            <form @submit.prevent="handleFormSubmit(message.stepId)">
              <div 
                v-for="(field, fieldName) in message.interaction.inputFields"
                :key="fieldName"
                class="form-group"
              >
                <label>{{ field.label }}</label>
                <input
                  v-model="formData[fieldName]"
                  :type="field.type"
                  :required="field.required"
                  :placeholder="field.placeholder || field.defaultValue"
                  class="form-control"
                />
              </div>
              <button type="submit" class="btn btn-primary">确认</button>
            </form>
          </div>
        </div>
      </div>
      
      <!-- 空状态 -->
      <div v-if="executionMessages.length === 0" class="empty-state">
        <p>点击"开始执行"启动脚本</p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'

export default {
  name: 'InteractiveExecutionPanel',
  props: {
    scriptId: {
      type: Number,
      required: true
    },
    scriptName: {
      type: String,
      required: true
    }
  },
  setup(props) {
    const executionStatus = ref('idle') // idle, running, completed, failed
    const loading = ref(false)
    const sessionId = ref(null)
    const executionMessages = ref([])
    const formData = reactive({})
    const logContainer = ref(null)
    
    let websocket = null
    
    // 开始执行
    const startExecution = async () => {
      loading.value = true
      try {
        const response = await fetch(`/api/user/interactive-execution/start/${props.scriptId}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        const data = await response.json()
        if (data.success) {
          sessionId.value = data.sessionId
          executionStatus.value = 'running'
          connectWebSocket()
        } else {
          alert('启动执行失败: ' + data.message)
        }
      } catch (error) {
        console.error('启动执行失败:', error)
        alert('启动执行失败: ' + error.message)
      } finally {
        loading.value = false
      }
    }
    
    // 取消执行
    const cancelExecution = async () => {
      try {
        await fetch(`/api/user/interactive-execution/cancel/${sessionId.value}`, {
          method: 'POST'
        })
        executionStatus.value = 'idle'
        if (websocket) {
          websocket.close()
        }
      } catch (error) {
        console.error('取消执行失败:', error)
      }
    }
    
    // 连接WebSocket
    const connectWebSocket = () => {
      const wsUrl = `ws://localhost:8080/topic/execution/${sessionId.value}`
      websocket = new WebSocket(wsUrl)
      
      websocket.onmessage = (event) => {
        const message = JSON.parse(event.data)
        executionMessages.value.push(message)
        
        // 自动滚动到底部
        nextTick(() => {
          if (logContainer.value) {
            logContainer.value.scrollTop = logContainer.value.scrollHeight
          }
        })
        
        // 检查是否执行完成
        if (message.status === 'COMPLETED' || message.status === 'FAILED') {
          executionStatus.value = message.status === 'COMPLETED' ? 'completed' : 'failed'
        }
      }
      
      websocket.onerror = (error) => {
        console.error('WebSocket error:', error)
      }
      
      websocket.onclose = () => {
        console.log('WebSocket connection closed')
      }
    }
    
    // 处理确认响应
    const handleConfirmation = async (stepId, choice) => {
      const response = {
        interactionId: stepId,
        response: choice,
        responseTime: Date.now()
      }
      
      await sendInteractionResponse(response)
    }
    
    // 处理表单提交
    const handleFormSubmit = async (stepId) => {
      const response = {
        interactionId: stepId,
        response: { ...formData },
        responseTime: Date.now()
      }
      
      await sendInteractionResponse(response)
      
      // 清空表单
      Object.keys(formData).forEach(key => {
        delete formData[key]
      })
    }
    
    // 发送交互响应
    const sendInteractionResponse = async (response) => {
      try {
        await fetch(`/api/user/interactive-execution/interaction-response/${sessionId.value}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(response)
        })
      } catch (error) {
        console.error('发送交互响应失败:', error)
      }
    }
    
    // 获取状态图标
    const getStatusIcon = (status) => {
      const iconMap = {
        'PREPARING': 'fa fa-clock-o',
        'EXECUTING': 'fa fa-spinner fa-spin',
        'WAITING_INPUT': 'fa fa-question-circle',
        'WAITING_CONFIRM': 'fa fa-question-circle',
        'COMPLETED': 'fa fa-check-circle',
        'FAILED': 'fa fa-times-circle',
        'SKIPPED': 'fa fa-minus-circle',
        'CANCELLED': 'fa fa-ban'
      }
      return iconMap[status] || 'fa fa-circle'
    }
    
    // 格式化时间
    const formatTime = (timestamp) => {
      return new Date(timestamp).toLocaleTimeString()
    }
    
    onUnmounted(() => {
      if (websocket) {
        websocket.close()
      }
    })
    
    return {
      executionStatus,
      loading,
      executionMessages,
      formData,
      logContainer,
      startExecution,
      cancelExecution,
      handleConfirmation,
      handleFormSubmit,
      getStatusIcon,
      formatTime
    }
  }
}
</script>

<style scoped>
.interactive-execution-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
}

.header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.controls .btn {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn-primary {
  background: #1890ff;
  color: white;
}

.btn-danger {
  background: #ff4d4f;
  color: white;
}

.btn-secondary {
  background: #f5f5f5;
  color: #666;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.execution-log {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  max-height: 600px;
}

.step {
  margin-bottom: 16px;
  border-left: 3px solid #e8e8e8;
  padding-left: 12px;
}

.step-completed {
  border-left-color: #52c41a;
}

.step-executing {
  border-left-color: #1890ff;
}

.step-failed {
  border-left-color: #ff4d4f;
}

.step-waiting_input, .step-waiting_confirm {
  border-left-color: #faad14;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.status-icon {
  width: 16px;
  text-align: center;
}

.step-name {
  font-weight: 500;
  flex: 1;
}

.timestamp {
  font-size: 12px;
  color: #999;
}

.message {
  margin: 0 0 8px 0;
  color: #333;
}

.output {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  margin: 8px 0;
  white-space: pre-wrap;
}

.progress {
  background: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
  margin: 8px 0;
}

.progress-bar {
  background: #1890ff;
  color: white;
  padding: 4px 8px;
  font-size: 12px;
  transition: width 0.3s;
}

.interaction-confirm, .interaction-form {
  background: #fafafa;
  padding: 12px;
  border-radius: 4px;
  margin: 8px 0;
}

.interaction-prompt {
  margin: 0 0 12px 0;
  font-weight: 500;
}

.confirm-buttons {
  display: flex;
  gap: 8px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
}

.form-control {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
}

.empty-state {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>