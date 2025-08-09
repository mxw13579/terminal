<template>
  <div :class="emptyClasses">
    <div v-if="showIcon" class="empty-icon">
      <slot name="icon">
        <svg class="empty-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="3"/>
          <path d="M12 1v6m0 6v6"/>
          <path d="m1 12 6 0m6 0 6 0"/>
        </svg>
      </slot>
    </div>
    
    <div class="empty-content">
      <div v-if="title" class="empty-title">
        {{ title }}
      </div>
      
      <div v-if="message || $slots.default" class="empty-message">
        <slot>{{ message }}</slot>
      </div>
      
      <div v-if="showActions" class="empty-actions">
        <BaseButton
          v-if="actionText"
          :variant="actionVariant"
          :size="actionSize"
          :loading="actionLoading"
          @click="handleAction"
        >
          {{ actionText }}
        </BaseButton>
        
        <BaseButton
          v-if="secondaryActionText"
          variant="secondary"
          :size="actionSize"
          @click="handleSecondaryAction"
        >
          {{ secondaryActionText }}
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
  name: 'EmptyState',
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
    
    // Visual
    variant: {
      type: String,
      default: 'default',
      validator: (value) => ['default', 'search', 'create', 'error'].includes(value)
    },
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['sm', 'md', 'lg'].includes(value)
    },
    
    // Layout
    centered: {
      type: Boolean,
      default: true
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
    
    // Actions
    actionText: {
      type: String,
      default: null
    },
    actionVariant: {
      type: String,
      default: 'primary'
    },
    actionSize: {
      type: String,
      default: 'md'
    },
    actionLoading: {
      type: Boolean,
      default: false
    },
    secondaryActionText: {
      type: String,
      default: null
    }
  },
  
  emits: ['action', 'secondary-action'],
  
  setup(props, { emit }) {
    const emptyClasses = computed(() => [
      'empty-state',
      `empty-state--${props.variant}`,
      `empty-state--${props.size}`,
      {
        'empty-state--centered': props.centered,
        'empty-state--full-height': props.fullHeight
      }
    ])
    
    const showActions = computed(() => {
      return props.actionText || props.secondaryActionText || !!props.$slots?.actions
    })
    
    const handleAction = () => {
      emit('action')
    }
    
    const handleSecondaryAction = () => {
      emit('secondary-action')
    }
    
    return {
      emptyClasses,
      showActions,
      handleAction,
      handleSecondaryAction
    }
  }
}
</script>

<style scoped>
/* Base empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-8);
  color: var(--text-secondary);
}

/* Centered layout */
.empty-state--centered {
  justify-content: center;
}

/* Full height */
.empty-state--full-height {
  min-height: 50vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Size variants */
.empty-state--sm {
  padding: var(--space-4);
}

.empty-state--lg {
  padding: var(--space-12);
}

/* Color variants */
.empty-state--search .empty-icon {
  color: var(--color-primary);
}

.empty-state--create .empty-icon {
  color: var(--color-success);
}

.empty-state--error .empty-icon {
  color: var(--color-error);
}

/* Icon */
.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-4);
  color: var(--text-tertiary);
  opacity: 0.6;
}

.empty-icon-svg {
  width: 3rem;
  height: 3rem;
}

.empty-state--sm .empty-icon {
  margin-bottom: var(--space-3);
}

.empty-state--sm .empty-icon-svg {
  width: 2.5rem;
  height: 2.5rem;
}

.empty-state--lg .empty-icon {
  margin-bottom: var(--space-6);
}

.empty-state--lg .empty-icon-svg {
  width: 4rem;
  height: 4rem;
}

/* Content */
.empty-content {
  max-width: 28rem;
  width: 100%;
}

.empty-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin-bottom: var(--space-2);
}

.empty-state--sm .empty-title {
  font-size: var(--font-size-base);
  margin-bottom: var(--space-1);
}

.empty-state--lg .empty-title {
  font-size: var(--font-size-xl);
  margin-bottom: var(--space-3);
}

.empty-message {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
  margin-bottom: var(--space-6);
}

.empty-state--sm .empty-message {
  margin-bottom: var(--space-4);
  font-size: var(--font-size-xs);
}

.empty-state--lg .empty-message {
  font-size: var(--font-size-base);
  margin-bottom: var(--space-8);
}

/* Actions */
.empty-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
  flex-wrap: wrap;
}

.empty-state--sm .empty-actions {
  gap: var(--space-2);
}

.empty-state--lg .empty-actions {
  gap: var(--space-4);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .empty-state {
    padding: var(--space-6) var(--space-4);
  }
  
  .empty-actions {
    flex-direction: column;
    width: 100%;
    max-width: 16rem;
  }
  
  .empty-content {
    max-width: 100%;
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .empty-icon {
    opacity: 1;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .empty-state {
    transition: none;
  }
}
</style>