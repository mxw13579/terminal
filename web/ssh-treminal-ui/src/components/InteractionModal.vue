<template>
  <el-dialog
    v-model="dialogVisible"
    :title="getDialogTitle()"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    width="500px"
    :before-close="handleClose"
  >
    <!-- 确认类型交互 -->
    <div v-if="interactionRequest.type === 'CONFIRMATION'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#E6A23C"><Warning /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <!-- 显示相关变量信息 -->
        <div v-if="interactionRequest.contextInfo" class="context-info">
          <el-tag 
            v-for="(value, key) in interactionRequest.contextInfo" 
            :key="key" 
            size="small" 
            class="context-tag"
          >
            {{ key }}: {{ value }}
          </el-tag>
        </div>
      </div>
    </div>
    
    <!-- 文本输入类型交互 -->
    <div v-else-if="interactionRequest.type === 'TEXT_INPUT'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#409EFF"><Edit /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <el-input 
          v-model="responseText" 
          :placeholder="interactionRequest.placeholder || '请输入内容'"
          maxlength="200"
          show-word-limit
          class="input-field"
        />
      </div>
    </div>
    
    <!-- 密码输入类型交互 -->
    <div v-else-if="interactionRequest.type === 'PASSWORD'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#F56C6C"><Lock /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <el-input 
          v-model="responseText" 
          :placeholder="interactionRequest.placeholder || '请输入密码'"
          type="password" 
          show-password
          class="input-field"
        />
      </div>
    </div>

    <!-- 智能推荐类型交互 -->
    <div v-else-if="interactionRequest.type === 'SMART_RECOMMENDATION'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#67C23A"><Star /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <div class="recommendation-info">
          <el-alert
            :title="interactionRequest.recommendationTitle"
            :description="interactionRequest.recommendationDesc"
            type="success"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <!-- 确认类型按钮 -->
        <template v-if="interactionRequest.type === 'CONFIRMATION' || interactionRequest.type === 'SMART_RECOMMENDATION'">
          <el-button @click="handleResponse(false)" :disabled="responseLoading">
            {{ interactionRequest.cancelText || '否' }}
          </el-button>
          <el-button type="primary" @click="handleResponse(true)" :loading="responseLoading">
            {{ interactionRequest.confirmText || '是' }}
          </el-button>
        </template>
        
        <!-- 输入类型按钮 -->
        <template v-else>
          <el-button @click="handleResponse(null)" :disabled="responseLoading">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleResponse(responseText)" 
            :loading="responseLoading"
            :disabled="!responseText.trim()"
          >
            提交
          </el-button>
        </template>
        
        <!-- 超时提示 -->
        <div v-if="timeoutSeconds > 0" class="timeout-info">
          <el-text size="small" type="warning">
            {{ timeoutSeconds }}秒后超时
          </el-text>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, defineProps, defineEmits, onMounted, onUnmounted } from 'vue'
import { ElDialog, ElButton, ElInput, ElIcon, ElTag, ElAlert, ElText } from 'element-plus'
import { Warning, Edit, Lock, Star } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const dialogVisible = ref(false)
const interactionRequest = ref({})
const responseText = ref('')
const responseLoading = ref(false)
const timeoutSeconds = ref(0)

let timeoutTimer = null
let countdownTimer = null

// 监听交互请求变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    interactionRequest.value = newVal
    dialogVisible.value = true
    responseText.value = ''
    
    // 启动超时计时器
    if (newVal.timeout) {
      startTimeout(newVal.timeout)
    }
  } else {
    dialogVisible.value = false
    clearTimers()
  }
}, { immediate: true })

// 获取对话框标题
const getDialogTitle = () => {
  const titleMap = {
    'CONFIRMATION': '确认执行',
    'TEXT_INPUT': '输入信息', 
    'PASSWORD': '密码输入',
    'SMART_RECOMMENDATION': '智能推荐'
  }
  return titleMap[interactionRequest.value.type] || '用户交互'
}

// 处理用户响应
const handleResponse = async (response) => {
  responseLoading.value = true
  
  try {
    const responseData = {
      requestId: interactionRequest.value.id,
      response: response,
      timestamp: new Date().toISOString()
    }
    
    emit('submit', responseData)
    
    // 关闭对话框
    emit('update:modelValue', null)
    
  } catch (error) {
    console.error('提交交互响应失败:', error)
  } finally {
    responseLoading.value = false
  }
}

// 处理对话框关闭
const handleClose = (done) => {
  // 交互对话框不允许直接关闭，必须响应
  return false
}

// 启动超时计时器
const startTimeout = (timeout) => {
  timeoutSeconds.value = Math.floor(timeout / 1000)
  
  // 超时处理
  timeoutTimer = setTimeout(() => {
    handleResponse(null) // 超时自动取消
  }, timeout)
  
  // 倒计时显示
  countdownTimer = setInterval(() => {
    timeoutSeconds.value--
    if (timeoutSeconds.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

// 清除计时器
const clearTimers = () => {
  if (timeoutTimer) {
    clearTimeout(timeoutTimer)
    timeoutTimer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  timeoutSeconds.value = 0
}

onUnmounted(() => {
  clearTimers()
})
</script>

<style scoped>
.interaction-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 20px 0;
}

.interaction-icon {
  margin-bottom: 16px;
}

.interaction-message {
  width: 100%;
}

.interaction-message p {
  font-size: 16px;
  color: #606266;
  margin-bottom: 16px;
  line-height: 1.5;
}

.context-info {
  margin: 12px 0;
}

.context-tag {
  margin: 4px;
}

.input-field {
  margin-top: 12px;
}

.recommendation-info {
  margin: 16px 0;
  text-align: left;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.timeout-info {
  flex: 1;
  text-align: left;
}
</style>