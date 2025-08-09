<template>
  <div :class="loadingClasses" :aria-label="ariaLabel || 'Loading'">
    <div v-if="showSpinner" class="loading-spinner">
      <svg class="loading-icon" viewBox="0 0 24 24">
        <circle
          class="loading-circle"
          cx="12"
          cy="12"
          r="10"
          fill="none"
          :stroke="spinnerColor"
          stroke-width="2"
        />
      </svg>
    </div>
    
    <div v-if="showContent" class="loading-content">
      <div v-if="title" class="loading-title">
        {{ title }}
      </div>
      
      <div v-if="message" class="loading-message">
        {{ message }}
      </div>
      
      <div v-if="progress !== null" class="loading-progress-container">
        <div class="loading-progress-bar">
          <div 
            class="loading-progress-fill"
            :style="{ width: `${Math.min(100, Math.max(0, progress))}%` }"
          ></div>
        </div>
        <div class="loading-progress-text">
          {{ Math.round(progress) }}%
        </div>
      </div>
      
      <slot />
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'LoadingSpinner',
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
    
    // Progress
    progress: {
      type: Number,
      default: null,
      validator: (value) => value === null || (value >= 0 && value <= 100)
    },
    
    // Visual
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['xs', 'sm', 'md', 'lg', 'xl'].includes(value)
    },
    variant: {
      type: String,
      default: 'primary',
      validator: (value) => ['primary', 'secondary', 'white'].includes(value)
    },
    
    // Layout
    center: {
      type: Boolean,
      default: false
    },
    overlay: {
      type: Boolean,
      default: false
    },
    fullscreen: {
      type: Boolean,
      default: false
    },
    
    // Features
    showSpinner: {
      type: Boolean,
      default: true
    },
    
    // Accessibility
    ariaLabel: {
      type: String,
      default: null
    }
  },
  
  setup(props) {
    const loadingClasses = computed(() => [
      'loading',
      `loading--${props.size}`,
      `loading--${props.variant}`,
      {
        'loading--center': props.center,
        'loading--overlay': props.overlay,
        'loading--fullscreen': props.fullscreen
      }
    ])
    
    const showContent = computed(() => {
      return props.title || props.message || props.progress !== null || props.$slots?.default
    })
    
    const spinnerColor = computed(() => {
      switch (props.variant) {
        case 'white':
          return 'currentColor'
        case 'secondary':
          return 'var(--color-secondary)'
        default:
          return 'var(--color-primary)'
      }
    })
    
    return {
      loadingClasses,
      showContent,
      spinnerColor
    }
  }
}
</script>

<style scoped>
/* Base loading */
.loading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* Center alignment */
.loading--center {
  justify-content: center;
  text-align: center;
  flex-direction: column;
  gap: var(--space-4);
}

/* Overlay styles */
.loading--overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--bg-overlay);
  backdrop-filter: blur(2px);
  z-index: var(--z-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-4);
}

.loading--fullscreen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-4);
}

/* Spinner */
.loading-spinner {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.loading-icon {
  animation: loading-spin 1s linear infinite;
}

.loading-circle {
  stroke-dasharray: 31.416;
  stroke-dashoffset: 31.416;
  animation: loading-dash 2s ease-in-out infinite;
  stroke-linecap: round;
}

/* Size variants */
.loading--xs .loading-icon {
  width: 1rem;
  height: 1rem;
}

.loading--sm .loading-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.loading--md .loading-icon {
  width: 1.5rem;
  height: 1.5rem;
}

.loading--lg .loading-icon {
  width: 2rem;
  height: 2rem;
}

.loading--xl .loading-icon {
  width: 2.5rem;
  height: 2.5rem;
}

/* Content */
.loading-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.loading-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.loading-message {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

/* Progress bar */
.loading-progress-container {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-width: 12rem;
}

.loading-progress-bar {
  flex: 1;
  height: 0.5rem;
  background-color: var(--bg-tertiary);
  border-radius: var(--radius-full);
  overflow: hidden;
  position: relative;
}

.loading-progress-fill {
  height: 100%;
  background-color: var(--color-primary);
  transition: width var(--duration-300) var(--ease-out);
  border-radius: var(--radius-full);
}

.loading-progress-text {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  min-width: 2.5rem;
  text-align: right;
}

/* Color variants */
.loading--white {
  color: white;
}

.loading--white .loading-title {
  color: white;
}

.loading--white .loading-message {
  color: rgba(255, 255, 255, 0.8);
}

.loading--white .loading-progress-text {
  color: rgba(255, 255, 255, 0.8);
}

.loading--white .loading-progress-bar {
  background-color: rgba(255, 255, 255, 0.2);
}

.loading--white .loading-progress-fill {
  background-color: white;
}

/* Animations */
@keyframes loading-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes loading-dash {
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

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .loading-icon,
  .loading-circle {
    animation: none;
  }
  
  .loading-progress-fill {
    transition: none;
  }
  
  /* Show static spinner instead */
  .loading-circle {
    stroke-dasharray: 50, 200;
    stroke-dashoffset: -25px;
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .loading--overlay {
    background-color: rgba(0, 0, 0, 0.9);
  }
  
  .loading-progress-bar {
    border: 1px solid currentColor;
  }
}
</style>