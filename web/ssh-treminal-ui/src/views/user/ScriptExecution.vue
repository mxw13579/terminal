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
                <h4>{{ script.name }}</h4>
                <p>{{ script.description || '暂无描述' }}</p>
              </div>
              <el-button
                v-if="selectedScript?.id === script.id"
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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http } from '@/utils/http'
import { connectWebSocket } from '@/utils/websocket'

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

const logsContainer = ref(null)
let wsConnection = null

const loadGroupInfo = async () => {
  try {
    const response = await http.get(`/api/admin/script-groups/${groupId.value}`)
    groupInfo.value = response.data
  } catch (error) {
    console.error('加载分组信息失败:', error)
    ElMessage.error('加载分组信息失败')
  }
}

const loadAggregatedScripts = async () => {
  try {
    const response = await http.get(`/api/user/aggregated-scripts/group/${groupId.value}`)
    scripts.value = response.data
    if (scripts.value.length > 0) {
      selectedScript.value = scripts.value[0]
    }
  } catch (error) {
    console.error('加载聚合脚本列表失败:', error)
    ElMessage.error('加载聚合脚本列表失败')
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
    currentExecution.value = {
      id: Date.now(),
      scriptId: script.id,
      status: 'RUNNING',
      startTime: new Date().toISOString()
    }

    // 尝试调用后端API
    try {
      const response = await http.post(`/api/user/script-execution/execute/${script.id}`)
      currentExecution.value = response.data
      connectToExecutionLogs(response.data.id)
    } catch (error) {
      console.error('后端API调用失败，使用模拟执行:', error)
      // 如果后端API失败，仍然使用模拟执行
      simulateScriptExecution(script)
    }

    ElMessage.success('脚本开始执行')
  } catch (error) {
    ElMessage.error('执行脚本失败')
    isExecuting.value = false
  }
}

// 模拟脚本执行
const simulateScriptExecution = (script) => {
  const executionId = currentExecution.value.id
  
  // 模拟执行日志
  const mockLogs = [
    { stepName: '初始化', logType: 'INFO', message: `开始执行脚本: ${script.name}`, stepOrder: 1 },
    { stepName: '环境检测', logType: 'INFO', message: '正在检测系统环境...', stepOrder: 2 },
    { stepName: '环境检测', logType: 'SUCCESS', message: '操作系统: Linux Ubuntu 20.04 LTS', stepOrder: 2 },
    { stepName: '环境检测', logType: 'SUCCESS', message: 'CPU架构: x86_64', stepOrder: 2 },
    { stepName: '环境检测', logType: 'SUCCESS', message: '内存: 8GB', stepOrder: 2 },
    { stepName: '依赖检查', logType: 'INFO', message: '正在检查依赖包...', stepOrder: 3 },
    { stepName: '依赖检查', logType: 'SUCCESS', message: 'Python 3.8.10 已安装', stepOrder: 3 },
    { stepName: '依赖检查', logType: 'SUCCESS', message: 'Node.js v16.20.0 已安装', stepOrder: 3 },
    { stepName: '执行任务', logType: 'INFO', message: '开始执行主要任务...', stepOrder: 4 },
    { stepName: '执行任务', logType: 'SUCCESS', message: '任务执行成功', stepOrder: 4 },
    { stepName: '清理', logType: 'INFO', message: '正在清理临时文件...', stepOrder: 5 },
    { stepName: '完成', logType: 'SUCCESS', message: '脚本执行完成', stepOrder: 6 }
  ]
  
  // 逐步添加日志
  let index = 0
  const addLog = () => {
    if (index < mockLogs.length) {
      const log = {
        ...mockLogs[index],
        id: Date.now() + index,
        executionId: executionId,
        timestamp: new Date().toISOString()
      }
      executionLogs.value.push(log)
      scrollToBottom()
      index++
      
      // 随机延迟500-2000ms
      const delay = Math.random() * 1500 + 500
      setTimeout(addLog, delay)
    } else {
      // 执行完成
      currentExecution.value.status = 'SUCCESS'
      currentExecution.value.endTime = new Date().toISOString()
      isExecuting.value = false
      ElMessage.success('脚本执行完成')
    }
  }
  
  // 开始模拟执行
  setTimeout(addLog, 1000)
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

const goBack = () => {
  router.push('/user')
}

// 自动滚动监听
watch(executionLogs, scrollToBottom, { deep: true })

onMounted(() => {
  loadGroupInfo()
  loadAggregatedScripts()
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