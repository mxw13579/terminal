<template>
  <component
    :is="tag"
    :class="buttonClasses"
    :disabled="disabled || loading"
    :type="nativeType"
    :aria-label="ariaLabel"
    :aria-describedby="ariaDescribedby"
    @click="handleClick"
    @keydown="handleKeydown"
  >
    <span v-if="loading" class="btn-spinner" aria-hidden="true">
      <svg class="btn-spinner-icon" viewBox="0 0 24 24">
        <circle
          class="btn-spinner-circle"
          cx="12"
          cy="12"
          r="10"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        />
      </svg>
    </span>
    
    <slot v-if="!loading" name="icon-left" />
    
    <span v-if="$slots.default && !iconOnly" class="btn-text">
      <slot />
    </span>
    
    <slot v-if="!loading" name="icon-right" />
    
    <!-- Screen reader loading text -->
    <span v-if="loading" class="sr-only">{{ loadingText || 'Loading...' }}</span>
  </component>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'BaseButton',
  props: {
    // Visual variants
    variant: {
      type: String,
      default: 'primary',
      validator: (value) => ['primary', 'secondary', 'ghost', 'danger', 'success', 'warning'].includes(value)
    },
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['xs', 'sm', 'md', 'lg', 'xl'].includes(value)
    },
    
    // States
    disabled: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    },
    
    // Layout
    fullWidth: {
      type: Boolean,
      default: false
    },
    iconOnly: {
      type: Boolean,
      default: false
    },
    
    // HTML attributes
    type: {
      type: String,
      default: 'button',
      validator: (value) => ['button', 'submit', 'reset', 'link'].includes(value)
    },
    nativeType: {
      type: String,
      default: 'button'
    },
    href: {
      type: String,
      default: null
    },
    to: {
      type: [String, Object],
      default: null
    },
    
    // Accessibility
    ariaLabel: {
      type: String,
      default: null
    },
    ariaDescribedby: {
      type: String,
      default: null
    },
    loadingText: {
      type: String,
      default: null
    }
  },
  
  emits: ['click'],
  
  setup(props, { emit }) {
    const tag = computed(() => {
      if (props.href) return 'a'
      if (props.to) return 'router-link'
      return 'button'
    })
    
    const buttonClasses = computed(() => [
      'btn',
      `btn--${props.variant}`,
      `btn--${props.size}`,
      {
        'btn--full-width': props.fullWidth,
        'btn--icon-only': props.iconOnly,
        'btn--loading': props.loading,
        'btn--disabled': props.disabled
      }
    ])
    
    const handleClick = (event) => {
      if (props.disabled || props.loading) {
        event.preventDefault()
        return
      }
      emit('click', event)
    }
    
    const handleKeydown = (event) => {
      // Prevent action on Enter/Space when disabled or loading
      if ((props.disabled || props.loading) && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault()
      }
    }
    
    return {
      tag,
      buttonClasses,
      handleClick,
      handleKeydown
    }
  }
}
</script>

<style scoped>
/* Base button styles */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-family: var(--font-family-sans);
  font-weight: var(--font-weight-medium);
  line-height: var(--line-height-tight);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  cursor: pointer;
  user-select: none;
  text-decoration: none;
  transition: 
    background-color var(--duration-150) var(--ease-out),
    border-color var(--duration-150) var(--ease-out),
    color var(--duration-150) var(--ease-out),
    box-shadow var(--duration-150) var(--ease-out),
    transform var(--duration-150) var(--ease-out);
  
  /* Focus styles */
  &:focus-visible {
    outline: 2px solid var(--border-focus);
    outline-offset: 2px;
  }
  
  /* Active state */
  &:active:not(.btn--disabled):not(.btn--loading) {
    transform: translateY(1px);
  }
}

/* Size variants */
.btn--xs {
  padding: var(--space-1) var(--space-2);
  font-size: var(--font-size-xs);
  min-height: 1.75rem;
}

.btn--sm {
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  min-height: 2rem;
}

.btn--md {
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-sm);
  min-height: 2.5rem;
}

.btn--lg {
  padding: var(--space-3) var(--space-5);
  font-size: var(--font-size-base);
  min-height: 3rem;
}

.btn--xl {
  padding: var(--space-4) var(--space-6);
  font-size: var(--font-size-lg);
  min-height: 3.5rem;
}

/* Icon only variants */
.btn--icon-only.btn--xs {
  padding: var(--space-1);
  min-width: 1.75rem;
}

.btn--icon-only.btn--sm {
  padding: var(--space-2);
  min-width: 2rem;
}

.btn--icon-only.btn--md {
  padding: var(--space-2);
  min-width: 2.5rem;
}

.btn--icon-only.btn--lg {
  padding: var(--space-3);
  min-width: 3rem;
}

.btn--icon-only.btn--xl {
  padding: var(--space-4);
  min-width: 3.5rem;
}

/* Color variants */
.btn--primary {
  background-color: var(--button-primary-bg);
  color: var(--button-primary-text);
  border-color: var(--button-primary-bg);
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--button-primary-hover);
    border-color: var(--button-primary-hover);
  }
}

.btn--secondary {
  background-color: var(--button-secondary-bg);
  color: var(--button-secondary-text);
  border-color: var(--button-secondary-border);
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--button-secondary-hover);
  }
}

.btn--ghost {
  background-color: transparent;
  color: var(--text-secondary);
  border-color: transparent;
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--bg-secondary);
    color: var(--text-primary);
  }
}

.btn--danger {
  background-color: var(--color-error);
  color: #ffffff;
  border-color: var(--color-error);
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--color-error-600);
    border-color: var(--color-error-600);
  }
}

.btn--success {
  background-color: var(--color-success);
  color: #ffffff;
  border-color: var(--color-success);
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--color-success-600);
    border-color: var(--color-success-600);
  }
}

.btn--warning {
  background-color: var(--color-warning);
  color: #ffffff;
  border-color: var(--color-warning);
  
  &:hover:not(.btn--disabled):not(.btn--loading) {
    background-color: var(--color-warning-600);
    border-color: var(--color-warning-600);
  }
}

/* State variants */
.btn--full-width {
  width: 100%;
}

.btn--disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.btn--loading {
  cursor: wait;
  position: relative;
}

/* Loading spinner */
.btn-spinner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-spinner-icon {
  width: 1em;
  height: 1em;
  animation: btn-spin 1s linear infinite;
}

.btn-spinner-circle {
  stroke-dasharray: 31.416;
  stroke-dashoffset: 31.416;
  animation: btn-spinner-dash 2s ease-in-out infinite;
}

.btn-text {
  flex: 1;
  text-align: center;
}

/* Animations */
@keyframes btn-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes btn-spinner-dash {
  0% {
    stroke-dasharray: 1, 200;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 89, 200;
    stroke-dashoffset: -35px;
  }
  100% {
    stroke-dasharray: 89, 200;
    stroke-dashoffset: -124px;
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .btn {
    border-width: 2px;
  }
  
  .btn--ghost {
    border-color: currentColor;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .btn {
    transition: none;
  }
  
  .btn-spinner-icon,
  .btn-spinner-circle {
    animation: none;
  }
}
</style>