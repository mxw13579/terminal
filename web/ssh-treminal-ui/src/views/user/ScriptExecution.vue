<template>
  <div class="script-execution">
    <div class="execution-header">
      <el-button @click="goBack" icon="ArrowLeft">返回</el-button>
      <h2>{{ groupInfo?.name || '脚本执行' }}</h2>
    </div>

    <div class="execution-layout">
      <!-- 左侧：脚本列表和初始化信息 -->
      <div class="left-panel">
        <!-- 上半部分：脚本列表 -->
        <div class="scripts-section">
          <h3>可用脚本</h3>
          <div class="scripts-list">
            <div
              v-for="script in scripts"
              :key="script.id"
              class="script-item"
              :class="{ active: selectedScript?.id === script.id }"
              @click="selectScript(script)"
            >
              <div class="script-info">
                <h4>{{ script.name }}
                  <el-tag 
                    :type="getScriptTypeColor(script.sourceType)" 
                    size="small" 
                    class="script-type-tag"
                  >
                    {{ getScriptTypeText(script.sourceType) }}
                  </el-tag>
                </h4>
                <p>{{ script.description || '暂无描述' }}</p>
              </div>
              <!-- 静态脚本：立即执行按钮 -->
              <el-button
                v-if="selectedScript?.id === script.id && script.sourceType === 'BUILT_IN_STATIC'"
                type="primary"
                size="small"
                @click.stop="executeStaticScript(script)"
                :loading="isExecuting"
              >
                {{ isExecuting ? '执行中...' : '立即执行' }}
              </el-button>
              <!-- 动态脚本：配置执行按钮 -->
              <el-button
                v-else-if="selectedScript?.id === script.id && (script.sourceType === 'BUILT_IN_DYNAMIC' || script.sourceType === 'USER_DEFINED')"
                type="primary"
                size="small"
                @click.stop="showParameterDialog(script)"
                :loading="isExecuting"
              >
                {{ isExecuting ? '执行中...' : '配置执行' }}
              </el-button>
              <!-- 交互脚本：交互执行按钮 -->
              <el-button
                v-else-if="selectedScript?.id === script.id && script.sourceType === 'BUILT_IN_INTERACTIVE'"
                type="danger"
                size="small"
                @click.stop="executeInteractiveScript(script)"
                :loading="isExecuting"
              >
                {{ isExecuting ? '执行中...' : '交互执行' }}
              </el-button>
              <!-- 默认执行按钮（兼容性） -->
              <el-button
                v-else-if="selectedScript?.id === script.id && !script.sourceType"
                type="primary"
                size="small"
                @click.stop="executeScript(script)"
                :loading="isExecuting"
              >
                {{ isExecuting ? '执行中...' : '执行' }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- 下半部分：初始化信息 -->
        <div class="init-section" v-if="groupInfo?.initScript">
          <h3>环境信息</h3>
          <div class="init-content">
            <pre>{{ groupInfo.initScript }}</pre>
          </div>
        </div>
      </div>

      <!-- 右侧：执行日志 -->
      <div class="right-panel">
        <div class="logs-header">
          <h3>执行日志</h3>
          <div class="logs-actions">
            <el-button
              size="small"
              @click="clearLogs"
              :disabled="!executionLogs.length"
            >
              清空日志
            </el-button>
            <el-button
              size="small"
              @click="autoScroll = !autoScroll"
              :type="autoScroll ? 'primary' : 'default'"
            >
              自动滚动
            </el-button>
          </div>
        </div>

        <div class="logs-container" ref="logsContainer">
          <div
            v-for="log in executionLogs"
            :key="log.id"
            class="log-entry"
            :class="`log-${log.logType.toLowerCase()}`"
          >
            <div class="log-header">
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              <span class="log-step" v-if="log.stepName">{{ log.stepName }}</span>
              <span class="log-type" :class="`type-${log.logType.toLowerCase()}`">
                {{ getLogTypeText(log.logType) }}
              </span>
            </div>
            <div class="log-message">{{ log.message }}</div>
          </div>

          <div v-if="!executionLogs.length" class="empty-logs">
            <el-icon><Document /></el-icon>
            <p>暂无执行日志</p>
            <p class="empty-tip">选择脚本并点击执行按钮开始</p>
          </div>
        </div>

        <!-- 执行状态 -->
        <div class="execution-status" v-if="currentExecution">
          <div class="status-info">
            <span>执行状态：</span>
            <el-tag
              :type="getExecutionStatusType(currentExecution.status)"
              size="small"
            >
              {{ getExecutionStatusText(currentExecution.status) }}
            </el-tag>
            <span class="execution-time">
              开始时间：{{ formatTime(currentExecution.startTime) }}
            </span>
            <span v-if="currentExecution.endTime" class="execution-time">
              结束时间：{{ formatTime(currentExecution.endTime) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    <InteractionModal v-model="interactionRequest" @submit="handleInteractionResponse" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http } from '@/utils/http'
import { connectWebSocket } from '@/utils/websocket'
import InteractionModal from '@/components/InteractionModal.vue'

const router = useRouter()
const route = useRoute()

const groupId = ref(route.params.groupId)
const groupInfo = ref(null)
const scripts = ref([])
const selectedScript = ref(null)
const executionLogs = ref([])
const currentExecution = ref(null)
const isExecuting = ref(false)
const autoScroll = ref(true)
const interactionRequest = ref(null)

const logsContainer = ref(null)
let wsConnection = null

const loadGroupData = async () => {
  try {
    const response = await http.get(`/api/user/script-groups/${groupId.value}`)
    groupInfo.value = response.data
    scripts.value = response.data.aggregatedScripts
    if (scripts.value.length > 0) {
      selectedScript.value = scripts.value[0]
    }
  } catch (error) {
    console.error('加载脚本组信息失败:', error)
    ElMessage.error('加载脚本组信息失败')
  }
}

const selectScript = (script) => {
  selectedScript.value = script
}

const executeScript = async (script) => {
  if (isExecuting.value) return

  try {
    isExecuting.value = true
    executionLogs.value = []

    const response = await http.post(`/api/user/script-execution/execute/${script.id}`)
    currentExecution.value = response.data
    connectToExecutionLogs(response.data.id)

    ElMessage.success('脚本开始执行')
  } catch (error) {
    ElMessage.error('执行脚本失败')
    isExecuting.value = false
  }
}

const connectToExecutionLogs = (executionId) => {
  const ws = connectWebSocket('/ws/stomp')
  
  ws.onConnect = () => {
    // 订阅执行日志
    ws.subscribe(`/topic/execution/${executionId}`, (log) => {
      executionLogs.value.push(log)
      scrollToBottom()
    })

    // 订阅执行状态更新
    ws.subscribe(`/topic/execution/${executionId}/status`, (execution) => {
      currentExecution.value = execution
      if (execution.status !== 'RUNNING') {
        isExecuting.value = false
        if (execution.status === 'SUCCESS') {
          ElMessage.success('脚本执行完成')
        } else if (execution.status === 'FAILED') {
          ElMessage.error('脚本执行失败')
        }
      }
    })

    // 订阅交互请求
    ws.subscribe(`/topic/execution/${executionId}/interaction`, (request) => {
      interactionRequest.value = request
    })
  }

  ws.onError = (error) => {
    console.error('WebSocket连接错误:', error)
    ElMessage.error('实时日志连接失败')
    isExecuting.value = false
  }
  
  // 初始化连接
  ws.init()
  
  wsConnection = ws
}

const handleInteractionResponse = async (response) => {
  try {
    await http.post(`/api/user/interactive-execution/respond`, response)
  } catch (error) {
    console.error('提交交互响应失败:', error)
    ElMessage.error('提交交互响应失败')
  }
}

const scrollToBottom = () => {
  if (autoScroll.value) {
    nextTick(() => {
      if (logsContainer.value) {
        logsContainer.value.scrollTop = logsContainer.value.scrollHeight
      }
    })
  }
}

const clearLogs = () => {
  executionLogs.value = []
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString()
}

const getLogTypeText = (logType) => {
  const typeMap = {
    'INFO': '信息',
    'SUCCESS': '成功',
    'ERROR': '错误',
    'WARN': '警告',
    'DEBUG': '调试'
  }
  return typeMap[logType] || logType
}

const getExecutionStatusType = (status) => {
  const statusMap = {
    'RUNNING': 'warning',
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info'
  }
  return statusMap[status] || 'info'
}

const getExecutionStatusText = (status) => {
  const statusMap = {
    'RUNNING': '执行中',
    'SUCCESS': '成功',
    'FAILED': '失败',
    'CANCELLED': '已取消'
  }
  return statusMap[status] || '未知'
}

// 新增：处理脚本类型的方法
const getScriptTypeText = (sourceType) => {
  const typeMap = {
    'BUILT_IN_STATIC': '静态',
    'BUILT_IN_DYNAMIC': '动态',
    'BUILT_IN_INTERACTIVE': '交互', // 新增
    'USER_DEFINED': '自定义'
  }
  return typeMap[sourceType] || '未知'
}

const getScriptTypeColor = (sourceType) => {
  const colorMap = {
    'BUILT_IN_STATIC': 'success',
    'BUILT_IN_DYNAMIC': 'warning', 
    'BUILT_IN_INTERACTIVE': 'danger', // 新增，用红色表示需要交互
    'USER_DEFINED': 'info'
  }
  return colorMap[sourceType] || 'info'
}

// 新增：交互脚本执行方法
const executeInteractiveScript = async (script) => {
  if (isExecuting.value) return

  try {
    isExecuting.value = true
    executionLogs.value = []

    // 使用新的统一API
    const response = await http.post(`/api/user/scripts/execute/${script.id}`, {
      sshConfig: getSshConfig(),
      parameters: {},
      async: true, // 交互脚本需要异步执行
      userId: getCurrentUserId(),
      sessionId: generateSessionId()
    })
    
    currentExecution.value = response.data
    
    // 检查是否需要交互
    if (response.data.requiresInteraction) {
      // 显示交互界面
      interactionRequest.value = response.data.interactionData
    }
    
    // 连接WebSocket监听执行状态
    connectToExecutionLogs(response.data.id)
    
    ElMessage.success('交互脚本开始执行')
    
  } catch (error) {
    console.error('交互脚本执行失败:', error)
    ElMessage.error('脚本执行失败')
    isExecuting.value = false
  }
}

// 静态脚本立即执行
const executeStaticScript = async (script) => {
  if (isExecuting.value) return

  try {
    isExecuting.value = true
    executionLogs.value = []

    // 使用新的简化API
    const response = await http.post(`/api/user/simplified-scripts/${script.id}/execute`, {
      sshConfig: getSshConfig(), // 获取SSH配置
      parameters: {}, // 静态脚本无参数
      async: false,
      userId: getCurrentUserId(),
      sessionId: generateSessionId()
    })
    
    // 显示执行结果
    if (response.data.success) {
      ElMessage.success('脚本执行成功')
      if (response.data.displayOutput) {
        executionLogs.value.push({
          logType: 'SUCCESS',
          message: response.data.displayOutput,
          timestamp: new Date().toISOString()
        })
      }
    } else {
      ElMessage.error(response.data.errorMessage || '脚本执行失败')
      executionLogs.value.push({
        logType: 'ERROR',
        message: response.data.errorMessage || '脚本执行失败',
        timestamp: new Date().toISOString()
      })
    }
    
    isExecuting.value = false
    
  } catch (error) {
    console.error('静态脚本执行失败:', error)
    ElMessage.error('脚本执行失败')
    isExecuting.value = false
  }
}

// 显示参数配置对话框
const showParameterDialog = async (script) => {
  try {
    // 获取脚本参数
    const response = await http.get(`/api/user/simplified-scripts/${script.id}/parameters`)
    const parameters = response.data
    
    if (parameters && parameters.length > 0) {
      // 显示参数配置对话框（需要实现）
      showParameterForm(script, parameters)
    } else {
      // 无参数，直接执行
      executeScriptWithParameters(script, {})
    }
  } catch (error) {
    console.error('获取脚本参数失败:', error)
    ElMessage.error('获取脚本参数失败')
  }
}

// 带参数执行脚本
const executeScriptWithParameters = async (script, parameters) => {
  if (isExecuting.value) return

  try {
    isExecuting.value = true
    executionLogs.value = []

    const response = await http.post(`/api/user/simplified-scripts/${script.id}/execute`, {
      sshConfig: getSshConfig(),
      parameters: parameters,
      async: false,
      userId: getCurrentUserId(),
      sessionId: generateSessionId()
    })
    
    if (response.data.success) {
      ElMessage.success('脚本执行成功')
      if (response.data.displayOutput) {
        executionLogs.value.push({
          logType: 'SUCCESS',
          message: response.data.displayOutput,
          timestamp: new Date().toISOString()
        })
      }
    } else {
      ElMessage.error(response.data.errorMessage || '脚本执行失败')
      executionLogs.value.push({
        logType: 'ERROR',
        message: response.data.errorMessage || '脚本执行失败',
        timestamp: new Date().toISOString()
      })
    }
    
    isExecuting.value = false
    
  } catch (error) {
    console.error('脚本执行失败:', error)
    ElMessage.error('脚本执行失败')
    isExecuting.value = false
  }
}

// 辅助方法
const getSshConfig = () => {
  // 这里应该获取当前的SSH连接配置
  // 暂时返回一个默认配置
  return {
    host: 'localhost',
    port: 22,
    username: 'user',
    password: 'password'
  }
}

const getCurrentUserId = () => {
  // 获取当前用户ID
  return 'current-user-id'
}

const generateSessionId = () => {
  return 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
}

const showParameterForm = (script, parameters) => {
  // 这里应该显示参数配置表单
  // 暂时直接执行，后续需要实现参数表单组件
  console.log('需要实现参数表单:', script, parameters)
  executeScriptWithParameters(script, {})
}

const goBack = () => {
  router.back()
}

// 自动滚动监听
watch(executionLogs, scrollToBottom, { deep: true })

onMounted(() => {
  loadGroupData()
})

onUnmounted(() => {
  if (wsConnection) {
    wsConnection.disconnect()
  }
})
</script>

<style scoped>
.script-execution {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.execution-header {
  display: flex;
  align-items: center;
  padding: 20px;
  background: white;
  border-bottom: 1px solid #e0e0e0;
  gap: 15px;
}

.execution-header h2 {
  margin: 0;
}

.execution-layout {
  flex: 1;
  display: flex;
  gap: 20px;
  padding: 20px;
  overflow: hidden;
}

.left-panel {
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.scripts-section {
  background: white;
  border-radius: 8px;
  padding: 20px;
  flex: 1;
  min-height: 0;
}

.scripts-section h3 {
  margin: 0 0 15px 0;
}

.scripts-list {
  height: calc(100% - 40px);
  overflow-y: auto;
}

.script-item {
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 15px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.script-item:hover {
  border-color: #409eff;
}

.script-item.active {
  border-color: #409eff;
  background: #f0f8ff;
}

.script-info h4 {
  margin: 0 0 5px 0;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.script-type-tag {
  font-size: 10px;
  height: 18px;
}

.script-info p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.init-section {
  background: white;
  border-radius: 8px;
  padding: 20px;
  flex: 0 0 auto;
  max-height: 300px;
}

.init-section h3 {
  margin: 0 0 15px 0;
}

.init-content {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 15px;
  max-height: 200px;
  overflow-y: auto;
}

.init-content pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.4;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
}

.logs-header h3 {
  margin: 0;
}

.logs-actions {
  display: flex;
  gap: 10px;
}

.logs-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.4;
}

.log-entry {
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.02);
}

.log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  font-size: 12px;
}

.log-time {
  color: #888;
}

.log-step {
  color: #569cd6;
  font-weight: bold;
}

.log-type {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: bold;
}

.type-info {
  background: #0e639c;
  color: white;
}

.type-success {
  background: #107c10;
  color: white;
}

.type-error {
  background: #d13438;
  color: white;
}

.type-warn {
  background: #ff8c00;
  color: white;
}

.type-debug {
  background: #666;
  color: white;
}

.log-message {
  white-space: pre-wrap;
  word-break: break-all;
}

.log-info .log-message {
  color: #d4d4d4;
}

.log-success .log-message {
  color: #4ec9b0;
}

.log-error .log-message {
  color: #f48771;
}

.log-warn .log-message {
  color: #dcdcaa;
}

.log-debug .log-message {
  color: #9cdcfe;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.empty-logs .el-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

.empty-tip {
  font-size: 14px;
  color: #999;
}

.execution-status {
  padding: 15px 20px;
  border-top: 1px solid #e0e0e0;
  background: #f8f9fa;
}

.status-info {
  display: flex;
  align-items: center;
  gap: 15px;
  font-size: 14px;
}

.execution-time {
  color: #666;
}
</style>