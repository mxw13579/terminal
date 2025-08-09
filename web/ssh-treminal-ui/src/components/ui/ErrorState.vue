<template>
  <div :class="errorClasses" role="alert" :aria-live="ariaLive">
    <div v-if="showIcon" class="error-icon">
      <slot name="icon">
        <svg class="error-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
      </slot>
    </div>
    
    <div class="error-content">
      <div v-if="title" class="error-title">
        {{ title }}
      </div>
      
      <div v-if="message || $slots.default" class="error-message">
        <slot>{{ message }}</slot>
      </div>
      
      <div v-if="details" class="error-details">
        <details>
          <summary class="error-details-summary">
            {{ detailsLabel || 'Technical Details' }}
          </summary>
          <pre class="error-details-content">{{ details }}</pre>
        </details>
      </div>
      
      <div v-if="showActions" class="error-actions">
        <BaseButton
          v-if="canRetry"
          variant="primary"
          size="sm"
          :loading="retryLoading"
          @click="handleRetry"
        >
          {{ retryLabel || 'Retry' }}
        </BaseButton>
        
        <BaseButton
          v-if="canDismiss"
          variant="secondary"
          size="sm"
          @click="handleDismiss"
        >
          {{ dismissLabel || 'Dismiss' }}
        </BaseButton>
        
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'
import BaseButton from './BaseButton.vue'

export default {
  name: 'ErrorState',
  components: {
    BaseButton
  },
  props: {
    // Content
    title: {
      type: String,
      default: null
    },
    message: {
      type: String,
      default: null
    },
    details: {
      type: String,
      default: null
    },
    
    // Visual
    variant: {
      type: String,
      default: 'error',
      validator: (value) => ['error', 'warning', 'info'].includes(value)
    },
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['sm', 'md', 'lg'].includes(value)
    },
    
    // Layout
    centered: {
      type: Boolean,
      default: false
    },
    fullHeight: {
      type: Boolean,
      default: false
    },
    
    // Features
    showIcon: {
      type: Boolean,
      default: true
    },
    canRetry: {
      type: Boolean,
      default: false
    },
    canDismiss: {
      type: Boolean,
      default: false
    },
    retryLoading: {
      type: Boolean,
      default: false
    },
    
    // Labels
    retryLabel: {
      type: String,
      default: null
    },
    dismissLabel: {
      type: String,
      default: null
    },
    detailsLabel: {
      type: String,
      default: null
    },
    
    // Accessibility
    ariaLive: {
      type: String,
      default: 'polite',
      validator: (value) => ['polite', 'assertive', 'off'].includes(value)
    }
  },
  
  emits: ['retry', 'dismiss'],
  
  setup(props, { emit }) {
    const errorClasses = computed(() => [
      'error-state',
      `error-state--${props.variant}`,
      `error-state--${props.size}`,
      {
        'error-state--centered': props.centered,
        'error-state--full-height': props.fullHeight
      }
    ])
    
    const showActions = computed(() => {
      return props.canRetry || props.canDismiss || !!props.$slots?.actions
    })
    
    const handleRetry = () => {
      emit('retry')
    }
    
    const handleDismiss = () => {
      emit('dismiss')
    }
    
    return {
      errorClasses,
      showActions,
      handleRetry,
      handleDismiss
    }
  }
}
</script>

<style scoped>
/* Base error state */
.error-state {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid transparent;
}

/* Centered layout */
.error-state--centered {
  flex-direction: column;
  align-items: center;
  text-align: center;
  justify-content: center;
}

.error-state--centered .error-icon {
  margin-bottom: var(--space-2);
}

/* Full height */
.error-state--full-height {
  min-height: 50vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Size variants */
.error-state--sm {
  padding: var(--space-3);
  gap: var(--space-2);
}

.error-state--lg {
  padding: var(--space-6);
  gap: var(--space-4);
}

/* Color variants */
.error-state--error {
  background-color: var(--color-error-light);
  border-color: var(--color-error-200);
  color: var(--color-error-800);
}

.error-state--warning {
  background-color: var(--color-warning-light);
  border-color: var(--color-warning-200);
  color: var(--color-warning-800);
}

.error-state--info {
  background-color: var(--color-primary-50);
  border-color: var(--color-primary-200);
  color: var(--color-primary-800);
}

/* Dark theme adjustments */
[data-theme="dark"] .error-state--error {
  background-color: var(--color-error-900);
  border-color: var(--color-error-700);
  color: var(--color-error-200);
}

[data-theme="dark"] .error-state--warning {
  background-color: var(--color-warning-900);
  border-color: var(--color-warning-700);
  color: var(--color-warning-200);
}

[data-theme="dark"] .error-state--info {
  background-color: var(--color-primary-900);
  border-color: var(--color-primary-700);
  color: var(--color-primary-200);
}

/* Icon */
.error-icon {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  flex-shrink: 0;
}

.error-icon-svg {
  width: 1.5rem;
  height: 1.5rem;
}

.error-state--sm .error-icon-svg {
  width: 1.25rem;
  height: 1.25rem;
}

.error-state--lg .error-icon-svg {
  width: 2rem;
  height: 2rem;
}

/* Content */
.error-content {
  flex: 1;
  min-width: 0;
}

.error-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--space-2);
  color: inherit;
}

.error-state--sm .error-title {
  font-size: var(--font-size-base);
  margin-bottom: var(--space-1);
}

.error-state--lg .error-title {
  font-size: var(--font-size-xl);
  margin-bottom: var(--space-3);
}

.error-message {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: inherit;
  opacity: 0.9;
}

.error-state--lg .error-message {
  font-size: var(--font-size-base);
}

/* Details */
.error-details {
  margin-top: var(--space-3);
}

.error-details-summary {
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: inherit;
  opacity: 0.8;
  user-select: none;
  
  &:hover {
    opacity: 1;
  }
  
  &:focus-visible {
    outline: 2px solid var(--border-focus);
    outline-offset: 2px;
    border-radius: var(--radius-sm);
  }
}

.error-details-content {
  margin-top: var(--space-2);
  padding: var(--space-3);
  background-color: rgba(0, 0, 0, 0.1);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  font-family: var(--font-family-mono);
  line-height: var(--line-height-relaxed);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Actions */
.error-actions {
  margin-top: var(--space-4);
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.error-state--centered .error-actions {
  justify-content: center;
}

.error-state--sm .error-actions {
  margin-top: var(--space-3);
}

.error-state--lg .error-actions {
  margin-top: var(--space-6);
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .error-state {
    border-width: 2px;
  }
  
  .error-details-content {
    border: 1px solid currentColor;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .error-details-summary {
    transition: none;
  }
}
</style>