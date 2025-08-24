<template>
  <teleport to="body">
    <transition-group
      name="toast"
      tag="div"
      class="toast-container"
      @before-enter="onBeforeEnter"
      @enter="onEnter"
      @leave="onLeave"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="getToastClasses(toast)"
        :style="getToastStyle(toast)"
        role="alert"
        :aria-live="toast.type === 'error' ? 'assertive' : 'polite'"
        @mouseenter="pauseTimer(toast)"
        @mouseleave="resumeTimer(toast)"
        @click="dismissToast(toast.id)"
      >
        <div class="toast-content">
          <div class="toast-icon" v-if="toast.icon !== false">
            <component
              :is="getIconComponent(toast.type)"
              :size="20"
              :color="getIconColor(toast.type)"
            />
          </div>
          
          <div class="toast-body">
            <div v-if="toast.title" class="toast-title">{{ toast.title }}</div>
            <div class="toast-message" v-html="toast.message"></div>
            
            <div v-if="toast.actions" class="toast-actions">
              <button
                v-for="action in toast.actions"
                :key="action.label"
                :class="['toast-action', action.variant || 'secondary']"
                @click.stop="handleAction(action, toast)"
              >
                {{ action.label }}
              </button>
            </div>
          </div>
          
          <button
            v-if="toast.closable !== false"
            class="toast-close"
            @click.stop="dismissToast(toast.id)"
            aria-label="关闭通知"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        
        <!-- 进度条 -->
        <div
          v-if="toast.showProgress !== false && toast.duration > 0"
          class="toast-progress"
          :style="{ animationDuration: `${toast.duration}ms` }"
        ></div>
      </div>
    </transition-group>
  </teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useToastStore } from '../../stores/toast'

// Icons
import SuccessIcon from './icons/SuccessIcon.vue'
import ErrorIcon from './icons/ErrorIcon.vue'
import WarningIcon from './icons/WarningIcon.vue'
import InfoIcon from './icons/InfoIcon.vue'

const toastStore = useToastStore()
const toasts = computed(() => toastStore.toasts)

// Toast样式类
const getToastClasses = (toast) => [
  'toast',
  `toast--${toast.type}`,
  {
    'toast--with-actions': toast.actions?.length > 0,
    'toast--loading': toast.loading
  }
]

// Toast样式
const getToastStyle = (toast) => {
  const style = {}
  
  if (toast.position?.includes('left')) {
    style.left = '1rem'
  } else if (toast.position?.includes('right')) {
    style.right = '1rem'
  }
  
  return style
}

// 图标组件映射
const iconComponents = {
  success: SuccessIcon,
  error: ErrorIcon,
  warning: WarningIcon,
  info: InfoIcon,
  loading: InfoIcon
}

const getIconComponent = (type) => iconComponents[type] || InfoIcon

const getIconColor = (type) => {
  const colorMap = {
    success: 'var(--color-success)',
    error: 'var(--color-error)',
    warning: 'var(--color-warning)',
    info: 'var(--color-primary)',
    loading: 'var(--color-primary)'
  }
  return colorMap[type] || colorMap.info
}

// 定时器管理
const timers = new Map()

const startTimer = (toast) => {
  if (toast.duration <= 0) return
  
  const timer = setTimeout(() => {
    dismissToast(toast.id)
  }, toast.duration)
  
  timers.set(toast.id, timer)
}

const clearTimer = (toastId) => {
  const timer = timers.get(toastId)
  if (timer) {
    clearTimeout(timer)
    timers.delete(toastId)
  }
}

const pauseTimer = (toast) => {
  clearTimer(toast.id)
}

const resumeTimer = (toast) => {
  startTimer(toast)
}

// Toast操作
const dismissToast = (toastId) => {
  clearTimer(toastId)
  toastStore.remove(toastId)
}

const handleAction = async (action, toast) => {
  if (typeof action.handler === 'function') {
    try {
      await action.handler(toast)
    } catch (error) {
      console.error('Toast action error:', error)
    }
  }
  
  if (action.dismiss !== false) {
    dismissToast(toast.id)
  }
}

// 动画钩子
const onBeforeEnter = (el) => {
  el.style.transform = 'translateX(100%)'
  el.style.opacity = '0'
}

const onEnter = (el, done) => {
  nextTick(() => {
    el.style.transition = 'all 0.3s var(--ease-out)'
    el.style.transform = 'translateX(0)'
    el.style.opacity = '1'
    
    setTimeout(done, 300)
  })
}

const onLeave = (el, done) => {
  el.style.transition = 'all 0.2s var(--ease-in)'
  el.style.transform = 'translateX(100%)'
  el.style.opacity = '0'
  el.style.height = '0'
  el.style.marginBottom = '0'
  el.style.paddingTop = '0'
  el.style.paddingBottom = '0'
  
  setTimeout(done, 200)
}

// 监听新Toast添加
const unwatchToasts = toastStore.$subscribe((mutation) => {
  if (mutation.type === 'direct' && mutation.events?.type === 'add') {
    const newToasts = mutation.events.newValue.filter(toast => 
      !timers.has(toast.id) && toast.duration > 0
    )
    
    newToasts.forEach(startTimer)
  }
})

// 清理
onUnmounted(() => {
  timers.forEach(clearTimer)
  timers.clear()
  unwatchToasts()
})
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: var(--space-4);
  right: var(--space-4);
  z-index: 9999;
  max-width: 420px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  pointer-events: none;
}

.toast {
  background: var(--bg-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  pointer-events: auto;
  position: relative;
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition: transform var(--duration-200) var(--ease-out);
}

.toast:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-xl);
}

.toast-content {
  display: flex;
  align-items: flex-start;
  padding: var(--space-4);
  gap: var(--space-3);
}

.toast-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.1);
}

.toast-body {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  margin-bottom: var(--space-1);
  line-height: var(--line-height-tight);
}

.toast-message {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: var(--line-height-normal);
  word-wrap: break-word;
}

.toast-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.toast-action {
  padding: var(--space-1) var(--space-3);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-base);
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--duration-150) var(--ease-out);
}

.toast-action:hover {
  background: var(--bg-secondary);
  border-color: var(--border-primary);
}

.toast-action.primary {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.toast-action.primary:hover {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.toast-close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-base);
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0.7;
  transition: all var(--duration-150) var(--ease-out);
}

.toast-close:hover {
  opacity: 1;
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: currentColor;
  opacity: 0.3;
  transform-origin: left;
  animation: toast-progress linear forwards;
}

@keyframes toast-progress {
  from { transform: scaleX(1); }
  to { transform: scaleX(0); }
}

/* Toast类型样式 */
.toast--success {
  border-left: 4px solid var(--color-success);
}

.toast--success .toast-progress {
  background: var(--color-success);
}

.toast--error {
  border-left: 4px solid var(--color-error);
}

.toast--error .toast-progress {
  background: var(--color-error);
}

.toast--warning {
  border-left: 4px solid var(--color-warning);
}

.toast--warning .toast-progress {
  background: var(--color-warning);
}

.toast--info {
  border-left: 4px solid var(--color-primary);
}

.toast--info .toast-progress {
  background: var(--color-primary);
}

.toast--loading {
  border-left: 4px solid var(--color-primary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .toast-container {
    top: var(--space-2);
    right: var(--space-2);
    left: var(--space-2);
    max-width: none;
  }
  
  .toast-content {
    padding: var(--space-3);
  }
  
  .toast-message {
    font-size: var(--font-size-xs);
  }
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  .toast {
    border-width: 2px;
  }
  
  .toast--success { border-left-width: 6px; }
  .toast--error { border-left-width: 6px; }
  .toast--warning { border-left-width: 6px; }
  .toast--info { border-left-width: 6px; }
}

/* 减少动画 */
@media (prefers-reduced-motion: reduce) {
  .toast {
    transition: none;
  }
  
  .toast-progress {
    animation: none;
  }
  
  .toast-enter-active,
  .toast-leave-active {
    transition: opacity 0.2s ease;
  }
}

/* 动画效果 */
.toast-enter-active {
  transition: all 0.3s var(--ease-out);
}

.toast-leave-active {
  transition: all 0.2s var(--ease-in);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
  height: 0;
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
}
</style>