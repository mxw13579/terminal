<template>
  <div class="loading-state" :class="stateClasses">
    <!-- 加载中状态 -->
    <template v-if="isLoading && !error">
      <div class="loading-content">
        <div v-if="skeletonType" class="skeleton-container">
          <component :is="skeletonComponent" />
        </div>
        <div v-else class="loading-spinner">
          <LoadingSpinner :size="spinnerSize" />
          <p v-if="loadingText" class="loading-text">{{ loadingText }}</p>
        </div>
      </div>
    </template>

    <!-- 错误状态 -->
    <template v-else-if="error">
      <div class="error-state">
        <div class="error-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
        </div>
        <h3 class="error-title">{{ errorTitle || '加载失败' }}</h3>
        <p class="error-message">{{ error }}</p>
        <div v-if="retry" class="error-actions">
          <BaseButton 
            variant="primary" 
            size="sm" 
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
            重试
          </BaseButton>
        </div>
      </div>
    </template>

    <!-- 空状态 -->
    <template v-else-if="isEmpty">
      <slot name="empty">
        <div class="empty-state">
          <div class="empty-icon">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
          </div>
          <h3 class="empty-title">{{ emptyTitle || '暂无数据' }}</h3>
          <p v-if="emptyDescription" class="empty-description">{{ emptyDescription }}</p>
        </div>
      </slot>
    </template>

    <!-- 正常内容 -->
    <template v-else>
      <slot />
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import LoadingSpinner from './LoadingSpinner.vue'
import BaseButton from './BaseButton.vue'

// Skeleton 组件动态导入
const SkeletonCard = () => import('./skeletons/SkeletonCard.vue')
const SkeletonList = () => import('./skeletons/SkeletonList.vue')
const SkeletonTerminal = () => import('./skeletons/SkeletonTerminal.vue')

const props = defineProps({
  isLoading: {
    type: Boolean,
    default: false
  },
  error: {
    type: [String, Error],
    default: null
  },
  errorTitle: {
    type: String,
    default: null
  },
  isEmpty: {
    type: Boolean,
    default: false
  },
  emptyTitle: {
    type: String,
    default: null
  },
  emptyDescription: {
    type: String,
    default: null
  },
  loadingText: {
    type: String,
    default: null
  },
  skeletonType: {
    type: String,
    default: null,
    validator: value => ['card', 'list', 'terminal'].includes(value)
  },
  spinnerSize: {
    type: String,
    default: 'md'
  },
  retry: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['retry'])

const retrying = ref(false)

const stateClasses = computed(() => ({
  'loading-state--loading': props.isLoading,
  'loading-state--error': props.error,
  'loading-state--empty': props.isEmpty
}))

const skeletonComponent = computed(() => {
  switch (props.skeletonType) {
    case 'card':
      return SkeletonCard
    case 'list':
      return SkeletonList
    case 'terminal':
      return SkeletonTerminal
    default:
      return null
  }
})

const handleRetry = async () => {
  if (!props.retry) return
  
  retrying.value = true
  try {
    await props.retry()
    emit('retry')
  } catch (error) {
    console.error('Retry failed:', error)
  } finally {
    retrying.value = false
  }
}
</script>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: var(--space-6);
}

/* 加载状态 */
.loading-content {
  text-align: center;
}

.skeleton-container {
  width: 100%;
  max-width: 400px;
}

.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
}

.loading-text {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  margin: 0;
}

/* 错误状态 */
.error-state {
  text-align: center;
  max-width: 400px;
}

.error-icon {
  color: var(--color-error);
  margin-bottom: var(--space-4);
}

.error-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin: 0 0 var(--space-2) 0;
}

.error-message {
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
  margin: 0 0 var(--space-6) 0;
}

.error-actions {
  display: flex;
  justify-content: center;
}

/* 空状态 */
.empty-state {
  text-align: center;
  max-width: 400px;
}

.empty-icon {
  color: var(--text-tertiary);
  margin-bottom: var(--space-4);
}

.empty-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  margin: 0 0 var(--space-2) 0;
}

.empty-description {
  color: var(--text-tertiary);
  line-height: var(--line-height-relaxed);
  margin: 0;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .loading-state {
    min-height: 150px;
    padding: var(--space-4);
  }
  
  .error-state,
  .empty-state {
    max-width: none;
  }
  
  .error-title,
  .empty-title {
    font-size: var(--font-size-base);
  }
}

/* 高对比度支持 */
@media (prefers-contrast: high) {
  .error-icon,
  .empty-icon {
    filter: contrast(1.2);
  }
}

/* 减少动画支持 */
@media (prefers-reduced-motion: reduce) {
  .loading-spinner {
    animation: none;
  }
}
</style>