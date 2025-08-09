<template>
  <Teleport to="body">
    <Transition
      name="modal-backdrop"
      enter-active-class="modal-backdrop-enter-active"
      leave-active-class="modal-backdrop-leave-active"
    >
      <div
        v-if="show"
        class="modal-backdrop"
        :class="backdropClasses"
        @click="handleBackdropClick"
        @keydown.esc="handleEscapeKey"
      >
        <Transition
          name="modal-content"
          enter-active-class="modal-content-enter-active"
          leave-active-class="modal-content-leave-active"
        >
          <div
            v-if="show"
            ref="modalRef"
            :class="modalClasses"
            role="dialog"
            :aria-modal="true"
            :aria-labelledby="titleId"
            :aria-describedby="descriptionId"
            tabindex="-1"
            @click.stop
          >
            <!-- Header -->
            <div v-if="!hideHeader" class="modal-header">
              <div class="modal-title-container">
                <h2
                  v-if="title || $slots.title"
                  :id="titleId"
                  class="modal-title"
                >
                  <slot name="title">{{ title }}</slot>
                </h2>
              </div>
              
              <button
                v-if="!hideCloseButton"
                type="button"
                class="modal-close"
                :aria-label="closeLabel || 'Close'"
                @click="handleClose"
              >
                <svg class="modal-close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            
            <!-- Content -->
            <div class="modal-body" :id="descriptionId">
              <slot />
            </div>
            
            <!-- Footer -->
            <div v-if="$slots.footer || showDefaultActions" class="modal-footer">
              <slot name="footer">
                <div v-if="showDefaultActions" class="modal-actions">
                  <BaseButton
                    v-if="showCancel"
                    variant="secondary"
                    @click="handleCancel"
                  >
                    {{ cancelLabel || 'Cancel' }}
                  </BaseButton>
                  
                  <BaseButton
                    v-if="showConfirm"
                    :variant="confirmVariant"
                    :loading="confirmLoading"
                    @click="handleConfirm"
                  >
                    {{ confirmLabel || 'Confirm' }}
                  </BaseButton>
                </div>
              </slot>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import BaseButton from './BaseButton.vue'

export default {
  name: 'BaseModal',
  components: {
    BaseButton
  },
  props: {
    // Visibility
    show: {
      type: Boolean,
      default: false
    },
    
    // Content
    title: {
      type: String,
      default: null
    },
    
    // Layout
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['xs', 'sm', 'md', 'lg', 'xl', 'full'].includes(value)
    },
    fullscreen: {
      type: Boolean,
      default: false
    },
    
    // Behavior
    persistent: {
      type: Boolean,
      default: false
    },
    hideHeader: {
      type: Boolean,
      default: false
    },
    hideCloseButton: {
      type: Boolean,
      default: false
    },
    
    // Actions
    showDefaultActions: {
      type: Boolean,
      default: false
    },
    showCancel: {
      type: Boolean,
      default: true
    },
    showConfirm: {
      type: Boolean,
      default: true
    },
    confirmVariant: {
      type: String,
      default: 'primary'
    },
    confirmLoading: {
      type: Boolean,
      default: false
    },
    
    // Labels
    confirmLabel: {
      type: String,
      default: null
    },
    cancelLabel: {
      type: String,
      default: null
    },
    closeLabel: {
      type: String,
      default: null
    }
  },
  
  emits: ['update:show', 'close', 'confirm', 'cancel', 'after-enter', 'after-leave'],
  
  setup(props, { emit }) {
    const modalRef = ref(null)
    const previousActiveElement = ref(null)
    
    // Generate unique IDs
    const titleId = computed(() => `modal-title-${Math.random().toString(36).substr(2, 9)}`)
    const descriptionId = computed(() => `modal-description-${Math.random().toString(36).substr(2, 9)}`)
    
    // Classes
    const backdropClasses = computed(() => [
      {
        'modal-backdrop--persistent': props.persistent
      }
    ])
    
    const modalClasses = computed(() => [
      'modal',
      `modal--${props.size}`,
      {
        'modal--fullscreen': props.fullscreen
      }
    ])
    
    // Focus management
    const focusableElements = computed(() => {
      if (!modalRef.value) return []
      
      const selector = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])'
      ].join(', ')
      
      return Array.from(modalRef.value.querySelectorAll(selector))
    })
    
    const focusFirstElement = async () => {
      await nextTick()
      const firstElement = focusableElements.value[0]
      if (firstElement) {
        firstElement.focus()
      } else {
        modalRef.value?.focus()
      }
    }
    
    const trapFocus = (event) => {
      if (!modalRef.value?.contains(event.target)) return
      
      const elements = focusableElements.value
      if (elements.length === 0) return
      
      const firstElement = elements[0]
      const lastElement = elements[elements.length - 1]
      
      if (event.shiftKey && event.target === firstElement) {
        event.preventDefault()
        lastElement.focus()
      } else if (!event.shiftKey && event.target === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    }
    
    // Event handlers
    const handleBackdropClick = () => {
      if (!props.persistent) {
        handleClose()
      }
    }
    
    const handleEscapeKey = (event) => {
      if (event.key === 'Escape' && !props.persistent) {
        handleClose()
      }
    }
    
    const handleClose = () => {
      emit('update:show', false)
      emit('close')
    }
    
    const handleConfirm = () => {
      emit('confirm')
    }
    
    const handleCancel = () => {
      emit('cancel')
      handleClose()
    }
    
    // Lifecycle management
    const handleModalEnter = async () => {
      // Store previously focused element
      previousActiveElement.value = document.activeElement
      
      // Prevent body scroll
      document.body.style.overflow = 'hidden'
      
      // Focus management
      await focusFirstElement()
      
      // Add keyboard event listener
      document.addEventListener('keydown', trapFocus)
      
      emit('after-enter')
    }
    
    const handleModalLeave = () => {
      // Restore body scroll
      document.body.style.overflow = ''
      
      // Restore focus
      if (previousActiveElement.value && typeof previousActiveElement.value.focus === 'function') {
        previousActiveElement.value.focus()
      }
      
      // Remove keyboard event listener
      document.removeEventListener('keydown', trapFocus)
      
      emit('after-leave')
    }
    
    // Watch for show changes
    watch(() => props.show, (newValue) => {
      if (newValue) {
        nextTick(handleModalEnter)
      } else {
        handleModalLeave()
      }
    })
    
    // Cleanup on unmount
    onUnmounted(() => {
      if (props.show) {
        handleModalLeave()
      }
    })
    
    return {
      modalRef,
      titleId,
      descriptionId,
      backdropClasses,
      modalClasses,
      handleBackdropClick,
      handleEscapeKey,
      handleClose,
      handleConfirm,
      handleCancel
    }
  }
}
</script>

<style scoped>
/* Modal backdrop */
.modal-backdrop {
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
  padding: var(--space-4);
  overflow-y: auto;
}

/* Modal */
.modal {
  background-color: var(--bg-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2xl);
  border: 1px solid var(--border-primary);
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Size variants */
.modal--xs {
  width: 100%;
  max-width: 20rem;
}

.modal--sm {
  width: 100%;
  max-width: 24rem;
}

.modal--md {
  width: 100%;
  max-width: 32rem;
}

.modal--lg {
  width: 100%;
  max-width: 42rem;
}

.modal--xl {
  width: 100%;
  max-width: 56rem;
}

.modal--full {
  width: 100%;
  max-width: 90vw;
  max-height: 90vh;
}

.modal--fullscreen {
  width: 100vw;
  height: 100vh;
  max-width: none;
  max-height: none;
  border-radius: 0;
  border: none;
}

/* Modal header */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-6) var(--space-6) 0;
  flex-shrink: 0;
}

.modal-title-container {
  flex: 1;
  min-width: 0;
}

.modal-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  padding: var(--space-1);
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: color var(--duration-150) var(--ease-out);
  margin-left: var(--space-4);
  flex-shrink: 0;
  
  &:hover {
    color: var(--text-secondary);
  }
  
  &:focus-visible {
    outline: 2px solid var(--border-focus);
    outline-offset: 2px;
  }
}

.modal-close-icon {
  width: 1.25rem;
  height: 1.25rem;
}

/* Modal body */
.modal-body {
  padding: var(--space-6);
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

/* Modal footer */
.modal-footer {
  padding: 0 var(--space-6) var(--space-6);
  flex-shrink: 0;
  border-top: 1px solid var(--border-primary);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* Transitions */
.modal-backdrop-enter-active,
.modal-backdrop-leave-active {
  transition: opacity var(--duration-300) var(--ease-out);
}

.modal-backdrop-enter-active {
  opacity: 0;
}

.modal-backdrop-leave-active {
  opacity: 1;
}

.modal-content-enter-active,
.modal-content-leave-active {
  transition: 
    opacity var(--duration-300) var(--ease-out),
    transform var(--duration-300) var(--ease-out);
}

.modal-content-enter-active {
  opacity: 0;
  transform: scale(0.95) translateY(-1rem);
}

.modal-content-leave-active {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .modal-backdrop {
    padding: var(--space-2);
    align-items: flex-start;
  }
  
  .modal {
    margin-top: var(--space-8);
    max-height: calc(100vh - 4rem);
  }
  
  .modal--xs,
  .modal--sm,
  .modal--md,
  .modal--lg,
  .modal--xl {
    width: 100%;
    max-width: none;
  }
  
  .modal-header {
    padding: var(--space-4) var(--space-4) 0;
  }
  
  .modal-body {
    padding: var(--space-4);
  }
  
  .modal-footer {
    padding: 0 var(--space-4) var(--space-4);
  }
  
  .modal-actions {
    flex-direction: column;
    gap: var(--space-2);
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .modal {
    border-width: 2px;
  }
  
  .modal-footer {
    border-top-width: 2px;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .modal-backdrop-enter-active,
  .modal-backdrop-leave-active,
  .modal-content-enter-active,
  .modal-content-leave-active {
    transition: none;
  }
  
  .modal-close {
    transition: none;
  }
}
</style>