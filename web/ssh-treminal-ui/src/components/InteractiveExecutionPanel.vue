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
        v-for="(message, index) in executionMessages" 
        :key="index"
        :class="['step', `step-${message.status.toLowerCase()}`]"
      >
        <div class="step-header">
          <span class="status-icon">
            <i :class="getStatusIcon(message.status)"></i>
          </span>
          <span class="step-name">{{ message.stepName || message.status }}</span>
          <span class="timestamp">{{ formatTime(message.timestamp) }}</span>
        </div>
        
        <div class="step-content">
          <p class="message">{{ message.message }}</p>
          
          <pre v-if="message.output" class="output">{{ message.output }}</pre>
          
          <div v-if="message.progress !== undefined" class="progress">
            <div class="progress-bar" :style="{ width: message.progress + '%' }">
              {{ message.progress }}%
            </div>
          </div>
          
          <!-- 交互UI (已重构) -->
          <div 
            v-if="message.status === 'WAITING_INPUT' && message.interaction"
            class="interaction-form"
          >
            <p class="interaction-prompt">{{ message.interaction.prompt }}</p>
            
            <!-- 确认按钮 -->
            <div v-if="message.interaction.type === 'CONFIRMATION'" class="confirm-buttons">
              <button @click="handleConfirmation(message.interaction, 'yes')" class="btn btn-primary">是</button>
              <button @click="handleConfirmation(message.interaction, 'no')" class="btn btn-secondary">否</button>
            </div>

            <!-- 输入表单 -->
            <form v-else @submit.prevent="handleFormSubmit(message.interaction)">
                <div class="form-group">
                  <input
                    v-model="formData[message.interaction.interactionId]"
                    :type="message.interaction.type === 'PASSWORD' ? 'password' : 'text'"
                    required
                    class="form-control"
                  />
                </div>
              <button type="submit" class="btn btn-primary">确认</button>
            </form>
          </div>
        </div>
      </div>
      
      <div v-if="executionMessages.length === 0" class="empty-state">
        <p>点击 "开始执行" 启动脚本</p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import http from '../utils/http'; // 假设你有一个封装好的http客户端

export default {
  name: 'InteractiveExecutionPanel',
  props: {
    scriptId: { type: [Number, String], required: true },
    scriptName: { type: String, required: true }
  },
  setup(props) {
    const executionStatus = ref('idle'); // idle, running, completed, failed
    const loading = ref(false);
    const sessionId = ref(null);
    const executionMessages = ref([]);
    const formData = reactive({});
    const logContainer = ref(null);
    
    let stompClient = null;

    const scrollToBottom = () => {
      nextTick(() => {
        if (logContainer.value) {
          logContainer.value.scrollTop = logContainer.value.scrollHeight;
        }
      });
    };

    const startExecution = async () => {
      loading.value = true;
      executionMessages.value = [];
      try {
        const { data } = await http.post(`/api/user/interactive-execution/start/${props.scriptId}`);
        if (data.success) {
          sessionId.value = data.sessionId;
          executionStatus.value = 'running';
          connectWebSocket();
        } else {
          alert('启动执行失败: ' + data.message);
        }
      } catch (error) {
        console.error('启动执行失败:', error);
        alert('启动执行失败: ' + (error.response?.data?.message || error.message));
      } finally {
        loading.value = false;
      }
    };

    const connectWebSocket = () => {
      const Stomp = window.Stomp;
      const SockJS = window.SockJS;
      const socket = new SockJS('/ws'); // Spring Boot WebSocket端点
      stompClient = Stomp.over(socket);

      stompClient.connect({}, frame => {
        console.log('Connected: ' + frame);
        stompClient.subscribe(`/topic/execution/${sessionId.value}`, (message) => {
          const body = JSON.parse(message.body);
          executionMessages.value.push(body);
          scrollToBottom();

          if (body.status === 'COMPLETED' || body.status === 'FAILED' || body.status === 'CANCELLED') {
            executionStatus.value = body.status.toLowerCase();
            disconnectWebSocket();
          }
        });
      }, error => {
        console.error('WebSocket connection error:', error);
      });
    };

    const disconnectWebSocket = () => {
      if (stompClient !== null) {
        stompClient.disconnect();
        console.log("Disconnected");
      }
    };
    
    // (重构) 发送交互响应
    const sendInteractionResponse = async (responsePayload) => {
      try {
        await http.post(`/api/user/interactive-execution/respond`, responsePayload);
      } catch (error) {
        console.error('发送交互响应失败:', error);
        alert('发送交互响应失败: ' + (error.response?.data?.message || error.message));
      }
    };

    // (重构) 处理确认按钮
    const handleConfirmation = async (interaction, choice) => {
      const response = {
        interactionId: interaction.interactionId,
        response: { confirmed: choice === 'yes' }
      };
      await sendInteractionResponse(response);
    };

    // (重构) 处理表单提交
    const handleFormSubmit = async (interaction) => {
      const interactionId = interaction.interactionId;
      const response = {
        interactionId: interactionId,
        response: { value: formData[interactionId] }
      };
      await sendInteractionResponse(response);
      formData[interactionId] = ''; // 清空表单
    };

    const getStatusIcon = (status) => {
      const iconMap = {
        'PREPARING': 'fas fa-clock',
        'EXECUTING': 'fas fa-cog fa-spin',
        'WAITING_INPUT': 'fas fa-question-circle',
        'COMPLETED': 'fas fa-check-circle',
        'FAILED': 'fas fa-times-circle',
        'CANCELLED': 'fas fa-ban'
      };
      return iconMap[status] || 'fas fa-info-circle';
    };

    const formatTime = (timestamp) => new Date(timestamp).toLocaleTimeString();

    onUnmounted(disconnectWebSocket);

    return {
      executionStatus,
      loading,
      scriptName: props.scriptName,
      executionMessages,
      formData,
      logContainer,
      startExecution,
      // cancelExecution, // 待实现
      handleConfirmation,
      handleFormSubmit,
      getStatusIcon,
      formatTime
    };
  }
}
</script>

<style scoped>
/* 样式保持不变 */
.interactive-execution-panel { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); }
.header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #e8e8e8; }
.header h3 { margin: 0; font-size: 16px; font-weight: 600; }
.btn { padding: 6px 16px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }
.btn-primary { background: #1890ff; color: white; }
.btn-danger { background: #ff4d4f; color: white; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.execution-log { flex: 1; padding: 16px 20px; overflow-y: auto; }
.step { margin-bottom: 16px; border-left: 3px solid #e8e8e8; padding-left: 12px; transition: border-color 0.3s; }
.step-completed { border-left-color: #52c41a; }
.step-executing { border-left-color: #1890ff; }
.step-failed { border-left-color: #ff4d4f; }
.step-waiting_input { border-left-color: #faad14; }
.step-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.status-icon { width: 20px; text-align: center; }
.step-name { font-weight: 500; flex: 1; }
.timestamp { font-size: 12px; color: #999; }
.message { margin: 0 0 8px 0; color: #333; }
.interaction-form { background: #fafafa; padding: 12px; border-radius: 4px; margin: 8px 0; }
.interaction-prompt { margin: 0 0 12px 0; font-weight: 500; }
.confirm-buttons, .form-group { display: flex; gap: 8px; margin-bottom: 12px; }
.form-control { width: 100%; padding: 6px 8px; border: 1px solid #d9d9d9; border-radius: 4px; }
.empty-state { text-align: center; color: #999; padding: 40px 0; }
</style>