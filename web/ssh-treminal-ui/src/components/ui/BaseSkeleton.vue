<template>
  <div :class="skeletonClasses" :style="skeletonStyles" :aria-label="ariaLabel">
    <div v-if="avatar" class="skeleton-avatar"></div>
    
    <div v-if="lines > 0 || $slots.default" class="skeleton-content">
      <div
        v-for="line in normalizedLines"
        :key="line"
        class="skeleton-line"
        :style="getLineStyles(line)"
      ></div>
      
      <slot />
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'BaseSkeleton',
  props: {
    // Layout
    lines: {
      type: Number,
      default: 1
    },
    lineWidths: {
      type: Array,
      default: () => []
    },
    avatar: {
      type: Boolean,
      default: false
    },
    
    // Dimensions
    width: {
      type: [String, Number],
      default: null
    },
    height: {
      type: [String, Number],
      default: null
    },
    
    // Animation
    animated: {
      type: Boolean,
      default: true
    },
    
    // Variant
    variant: {
      type: String,
      default: 'default',
      validator: (value) => ['default', 'card', 'text', 'media'].includes(value)
    },
    
    // Accessibility
    ariaLabel: {
      type: String,
      default: 'Loading content...'
    }
  },
  
  setup(props) {
    const skeletonClasses = computed(() => [
      'skeleton',
      `skeleton--${props.variant}`,
      {
        'skeleton--animated': props.animated,
        'skeleton--with-avatar': props.avatar
      }
    ])
    
    const skeletonStyles = computed(() => {
      const styles = {}
      
      if (props.width) {
        styles.width = typeof props.width === 'number' ? `${props.width}px` : props.width
      }
      
      if (props.height) {
        styles.height = typeof props.height === 'number' ? `${props.height}px` : props.height
      }
      
      return styles
    })
    
    const normalizedLines = computed(() => {
      return Array.from({ length: props.lines }, (_, i) => i + 1)
    })
    
    const getLineStyles = (lineIndex) => {
      const styles = {}
      
      // Apply custom width if specified
      if (props.lineWidths[lineIndex - 1]) {
        const width = props.lineWidths[lineIndex - 1]
        styles.width = typeof width === 'number' ? `${width}%` : width
      }
      
      // Make last line shorter by default
      if (lineIndex === props.lines && props.lines > 1 && !props.lineWidths[lineIndex - 1]) {
        styles.width = '75%'
      }
      
      return styles
    }
    
    return {
      skeletonClasses,
      skeletonStyles,
      normalizedLines,
      getLineStyles
    }
  }
}
</script>

<style scoped>
/* Base skeleton */
.skeleton {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: 100%;
}

/* Avatar */
.skeleton-avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  flex-shrink: 0;
}

.skeleton--animated .skeleton-avatar {
  background: linear-gradient(
    90deg,
    var(--bg-secondary) 25%,
    var(--bg-tertiary) 50%,
    var(--bg-secondary) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 2s infinite;
}

/* Content area */
.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

/* Skeleton lines */
.skeleton-line {
  height: 0.75rem;
  background-color: var(--bg-secondary);
  border-radius: var(--radius-sm);
  width: 100%;
}

.skeleton--animated .skeleton-line {
  background: linear-gradient(
    90deg,
    var(--bg-secondary) 25%,
    var(--bg-tertiary) 50%,
    var(--bg-secondary) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 2s infinite;
}

/* Variants */
.skeleton--card {
  padding: var(--space-4);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  background-color: var(--bg-elevated);
}

.skeleton--text .skeleton-line {
  height: 1rem;
  margin-bottom: var(--space-1);
}

.skeleton--text .skeleton-line:last-child {
  margin-bottom: 0;
}

.skeleton--media {
  flex-direction: column;
}

.skeleton--media .skeleton-avatar {
  width: 100%;
  height: 12rem;
  border-radius: var(--radius-md);
}

/* Full width skeleton without avatar */
.skeleton:not(.skeleton--with-avatar) {
  flex-direction: column;
}

.skeleton:not(.skeleton--with-avatar) .skeleton-content {
  width: 100%;
}

/* Animation */
@keyframes skeleton-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .skeleton--animated .skeleton-line,
  .skeleton--animated .skeleton-avatar {
    animation: none;
    background: var(--bg-secondary);
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .skeleton-line,
  .skeleton-avatar {
    border: 1px solid currentColor;
    opacity: 0.3;
  }
}
</style>