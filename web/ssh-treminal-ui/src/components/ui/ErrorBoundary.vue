<template>
  <div>
    <slot v-if="!hasError" />
    
    <!-- 错误状态 -->
    <div v-else class="error-boundary">
      <div class="error-boundary-content">
        <div class="error-icon">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
        </div>
        
        <h2 class="error-title">{{ title || '页面出现错误' }}</h2>
        
        <p class="error-description">
          {{ description || '抱歉，页面遇到了意外错误。我们已经记录了这个问题，请尝试刷新页面或返回上一步。' }}
        </p>
        
        <!-- 错误详情 (仅开发模式) -->
        <details v-if="showDetails && (isDev || showDevDetails)" class="error-details">
          <summary>错误详情</summary>
          <div class="error-stack">
            <h4>错误信息:</h4>
            <pre>{{ error.message }}</pre>
            
            <h4 v-if="error.stack">调用堆栈:</h4>
            <pre v-if="error.stack">{{ error.stack }}</pre>
            
            <h4 v-if="errorInfo">组件堆栈:</h4>
            <pre v-if="errorInfo">{{ errorInfo }}</pre>
          </div>
        </details>
        
        <!-- 操作按钮 -->
        <div class="error-actions">
          <BaseButton
            variant="primary"
            @click="handleRetry"
            :loading="retrying"
          >
            <template #icon-left>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
                <path d="M21 3v5h-5"/>
                <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
                <path d="M3 21v-5h5"/>
              </svg>
            </template>
            刷新页面
          </BaseButton>
          
          <BaseButton
            variant="secondary"
            @click="handleGoBack"
          >
            <template #icon-left>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="m12 19-7-7 7-7"/>
                <path d="M19 12H5"/>
              </svg>
            </template>
            返回上页
          </BaseButton>
          
          <BaseButton
            v-if="reportError"
            variant="ghost"
            @click="handleReportError"
            size="sm"
          >
            <template #icon-left>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14,2 14,8 20,8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10,9 9,9 8,9"/>
              </svg>
            </template>
            报告错误
          </BaseButton>
        </div>
        
        <!-- 帮助信息 -->
        <div v-if="helpText || $slots.help" class="error-help">
          <slot name="help">
            <p>{{ helpText }}</p>
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onErrorCaptured, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '../../stores/toast'
import BaseButton from './BaseButton.vue'

const props = defineProps({
  title: {
    type: String,
    default: null
  },
  description: {
    type: String,
    default: null
  },
  showDetails: {
    type: Boolean,
    default: true
  },
  showDevDetails: {
    type: Boolean,
    default: false
  },
  reportError: {
    type: Function,
    default: null
  },
  helpText: {
    type: String,
    default: null
  },
  resetOnPropsChange: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['error', 'retry', 'reset'])

const router = useRouter()
const toast = useToast()

// 状态
const hasError = ref(false)
const error = ref(null)
const errorInfo = ref(null)
const retrying = ref(false)

// 开发模式检测
const isDev = computed(() => {
  return import.meta.env.MODE === 'development'
})

// 错误捕获
onErrorCaptured((err, instance, info) => {
  console.error('ErrorBoundary caught error:', err)
  console.error('Component instance:', instance)
  console.error('Error info:', info)
  
  hasError.value = true
  error.value = err
  errorInfo.value = info
  
  // 发送错误事件
  emit('error', {
    error: err,
    instance,
    info
  })
  
  // 错误上报
  if (typeof props.reportError === 'function') {
    try {
      props.reportError(err, instance, info)
    } catch (reportErr) {
      console.error('Error reporting failed:', reportErr)
    }
  }
  
  // 阻止错误继续传播
  return false
})

// 监听props变化重置错误状态
if (props.resetOnPropsChange) {
  // 这里可以watch相关props变化来重置错误状态
}

// 操作处理
const handleRetry = async () => {
  retrying.value = true
  
  try {
    // 等待一小段时间，给用户视觉反馈
    await new Promise(resolve => setTimeout(resolve, 500))
    
    // 重置错误状态
    hasError.value = false
    error.value = null
    errorInfo.value = null
    
    emit('retry')
    
    // 如果没有自定义重试逻辑，刷新页面
    setTimeout(() => {
      if (hasError.value) {
        window.location.reload()
      }
    }, 100)
    
  } catch (retryError) {
    console.error('Retry failed:', retryError)
    toast.error('重试失败，请手动刷新页面')
  } finally {
    retrying.value = false
  }
}

const handleGoBack = () => {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}

const handleReportError = () => {
  if (typeof props.reportError === 'function') {
    props.reportError(error.value, null, errorInfo.value)
    toast.success('错误报告已发送，感谢您的反馈！')
  }
}

// 键盘快捷键
onMounted(() => {
  const handleKeydown = (event) => {
    if (!hasError.value) return
    
    // Ctrl/Cmd + R: 刷新
    if ((event.ctrlKey || event.metaKey) && event.key === 'r') {
      event.preventDefault()
      handleRetry()
    }
    
    // Escape: 返回
    if (event.key === 'Escape') {
      event.preventDefault()
      handleGoBack()
    }
  }
  
  window.addEventListener('keydown', handleKeydown)
  
  return () => {
    window.removeEventListener('keydown', handleKeydown)
  }
})

// 暴露重置方法
defineExpose({
  reset: () => {
    hasError.value = false
    error.value = null
    errorInfo.value = null
    emit('reset')
  },
  
  getError: () => ({
    hasError: hasError.value,
    error: error.value,
    errorInfo: errorInfo.value
  })
})
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: var(--space-6);
  background: var(--bg-primary);
}

.error-boundary-content {
  max-width: 600px;
  text-align: center;
}

.error-icon {
  color: var(--color-error);
  margin-bottom: var(--space-6);
  display: flex;
  justify-content: center;
}

.error-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin: 0 0 var(--space-4) 0;
}

.error-description {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
  margin: 0 0 var(--space-6) 0;
}

.error-details {
  text-align: left;
  margin: var(--space-6) 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.error-details summary {
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  cursor: pointer;
  margin-bottom: var(--space-3);
}

.error-details summary:hover {
  color: var(--color-primary);
}

.error-stack h4 {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin: var(--space-3) 0 var(--space-2) 0;
}

.error-stack h4:first-child {
  margin-top: 0;
}

.error-stack pre {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  padding: var(--space-3);
  border-radius: var(--radius-base);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0 0 var(--space-3) 0;
}

.error-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
  flex-wrap: wrap;
}

.error-help {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--border-primary);
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  line-height: var(--line-height-relaxed);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-boundary {
    min-height: 300px;
    padding: var(--space-4);
  }
  
  .error-title {
    font-size: var(--font-size-xl);
  }
  
  .error-description {
    font-size: var(--font-size-sm);
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .error-details {
    font-size: var(--font-size-xs);
  }
}

/* 高对比度支持 */
@media (prefers-contrast: high) {
  .error-boundary-content {
    border: 2px solid var(--border-primary);
    border-radius: var(--radius-lg);
    padding: var(--space-6);
  }
  
  .error-icon {
    filter: contrast(1.5);
  }
}

/* 减少动画 */
@media (prefers-reduced-motion: reduce) {
  .error-boundary * {
    animation: none !important;
    transition: none !important;
  }
}
</style>