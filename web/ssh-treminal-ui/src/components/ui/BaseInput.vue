<template>
  <div class="input-wrapper" :class="wrapperClasses">
    <label v-if="label" :for="inputId" class="input-label">
      {{ label }}
      <span v-if="required" class="input-required" aria-label="required">*</span>
    </label>
    
    <div class="input-container">
      <div v-if="$slots.prefix || prefixIcon" class="input-prefix">
        <slot name="prefix">
          <component v-if="prefixIcon" :is="prefixIcon" class="input-icon" />
        </slot>
      </div>
      
      <input
        :id="inputId"
        ref="inputRef"
        v-model="inputValue"
        :class="inputClasses"
        :type="inputType"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :required="required"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :minlength="minlength"
        :min="min"
        :max="max"
        :step="step"
        :pattern="pattern"
        :aria-label="ariaLabel || label"
        :aria-describedby="ariaDescribedby"
        :aria-invalid="hasError"
        @input="handleInput"
        @change="handleChange"
        @blur="handleBlur"
        @focus="handleFocus"
        @keydown="handleKeydown"
      />
      
      <div v-if="$slots.suffix || suffixIcon || clearable || type === 'password'" class="input-suffix">
        <button
          v-if="clearable && inputValue && !disabled && !readonly"
          type="button"
          class="input-clear"
          :aria-label="clearLabel || 'Clear input'"
          @click="clearInput"
        >
          <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M15 9l-6 6"/>
            <path d="M9 9l6 6"/>
          </svg>
        </button>
        
        <button
          v-if="type === 'password'"
          type="button"
          class="input-toggle-password"
          :aria-label="showPassword ? 'Hide password' : 'Show password'"
          @click="togglePassword"
        >
          <svg v-if="showPassword" class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94L17.94 17.94z"/>
            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19l-6.84-6.84z"/>
            <path d="M1 1l22 22"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          <svg v-else class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </button>
        
        <slot name="suffix">
          <component v-if="suffixIcon" :is="suffixIcon" class="input-icon" />
        </slot>
      </div>
    </div>
    
    <div v-if="hasError || hint" class="input-help" :class="{ 'input-help--error': hasError }">
      <div v-if="hasError" class="input-error" role="alert" :aria-live="liveRegion">
        {{ error }}
      </div>
      <div v-else-if="hint" class="input-hint">
        {{ hint }}
      </div>
    </div>
  </div>
</template>

<script>
import { computed, ref, nextTick } from 'vue'

export default {
  name: 'BaseInput',
  props: {
    // Value
    modelValue: {
      type: [String, Number],
      default: ''
    },
    
    // Input configuration
    type: {
      type: String,
      default: 'text',
      validator: (value) => ['text', 'email', 'password', 'number', 'tel', 'url', 'search'].includes(value)
    },
    label: {
      type: String,
      default: null
    },
    placeholder: {
      type: String,
      default: null
    },
    hint: {
      type: String,
      default: null
    },
    
    // Validation
    error: {
      type: String,
      default: null
    },
    required: {
      type: Boolean,
      default: false
    },
    
    // HTML attributes
    maxlength: {
      type: [String, Number],
      default: null
    },
    minlength: {
      type: [String, Number],
      default: null
    },
    min: {
      type: [String, Number],
      default: null
    },
    max: {
      type: [String, Number],
      default: null
    },
    step: {
      type: [String, Number],
      default: null
    },
    pattern: {
      type: String,
      default: null
    },
    autocomplete: {
      type: String,
      default: null
    },
    
    // States
    disabled: {
      type: Boolean,
      default: false
    },
    readonly: {
      type: Boolean,
      default: false
    },
    
    // Visual
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['sm', 'md', 'lg'].includes(value)
    },
    
    // Features
    clearable: {
      type: Boolean,
      default: false
    },
    
    // Icons
    prefixIcon: {
      type: [String, Object],
      default: null
    },
    suffixIcon: {
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
    clearLabel: {
      type: String,
      default: null
    },
    liveRegion: {
      type: String,
      default: 'polite',
      validator: (value) => ['polite', 'assertive', 'off'].includes(value)
    }
  },
  
  emits: ['update:modelValue', 'input', 'change', 'blur', 'focus', 'clear'],
  
  setup(props, { emit }) {
    const inputRef = ref(null)
    const showPassword = ref(false)
    const isFocused = ref(false)
    
    // Generate unique ID
    const inputId = computed(() => `input-${Math.random().toString(36).substr(2, 9)}`)
    
    // Computed properties
    const inputValue = computed({
      get: () => props.modelValue,
      set: (value) => emit('update:modelValue', value)
    })
    
    const inputType = computed(() => {
      if (props.type === 'password') {
        return showPassword.value ? 'text' : 'password'
      }
      return props.type
    })
    
    const hasError = computed(() => !!props.error)
    
    const wrapperClasses = computed(() => [
      'input-wrapper',
      `input-wrapper--${props.size}`,
      {
        'input-wrapper--disabled': props.disabled,
        'input-wrapper--readonly': props.readonly,
        'input-wrapper--error': hasError.value,
        'input-wrapper--focused': isFocused.value
      }
    ])
    
    const inputClasses = computed(() => [
      'input',
      {
        'input--has-prefix': props.prefixIcon || !!props.$slots?.prefix,
        'input--has-suffix': props.suffixIcon || !!props.$slots?.suffix || props.clearable || props.type === 'password'
      }
    ])
    
    // Event handlers
    const handleInput = (event) => {
      const value = event.target.value
      emit('update:modelValue', value)
      emit('input', event)
    }
    
    const handleChange = (event) => {
      emit('change', event)
    }
    
    const handleFocus = (event) => {
      isFocused.value = true
      emit('focus', event)
    }
    
    const handleBlur = (event) => {
      isFocused.value = false
      emit('blur', event)
    }
    
    const handleKeydown = (event) => {
      // Clear input on Escape
      if (event.key === 'Escape' && props.clearable && inputValue.value) {
        clearInput()
      }
    }
    
    const clearInput = () => {
      emit('update:modelValue', '')
      emit('clear')
      nextTick(() => {
        inputRef.value?.focus()
      })
    }
    
    const togglePassword = () => {
      showPassword.value = !showPassword.value
      nextTick(() => {
        inputRef.value?.focus()
      })
    }
    
    // Public methods
    const focus = () => {
      inputRef.value?.focus()
    }
    
    const blur = () => {
      inputRef.value?.blur()
    }
    
    const select = () => {
      inputRef.value?.select()
    }
    
    return {
      inputRef,
      inputId,
      inputValue,
      inputType,
      hasError,
      wrapperClasses,
      inputClasses,
      showPassword,
      isFocused,
      handleInput,
      handleChange,
      handleFocus,
      handleBlur,
      handleKeydown,
      clearInput,
      togglePassword,
      focus,
      blur,
      select
    }
  }
}
</script>

<style scoped>
/* Input wrapper */
.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

/* Label */
.input-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.input-required {
  color: var(--color-error);
}

/* Input container */
.input-container {
  position: relative;
  display: flex;
  align-items: center;
}

/* Input styles */
.input {
  width: 100%;
  font-family: var(--font-family-sans);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  color: var(--text-primary);
  background-color: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  transition: 
    border-color var(--duration-150) var(--ease-out),
    box-shadow var(--duration-150) var(--ease-out),
    background-color var(--duration-150) var(--ease-out);
  
  &::placeholder {
    color: var(--input-placeholder);
  }
  
  &:focus {
    outline: none;
    border-color: var(--input-focus);
    box-shadow: 0 0 0 1px var(--input-focus);
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    background-color: var(--bg-secondary);
  }
  
  &:readonly {
    background-color: var(--bg-secondary);
    cursor: default;
  }
}

/* Size variants */
.input-wrapper--sm .input {
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  min-height: 2rem;
}

.input-wrapper--md .input {
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  min-height: 2.5rem;
}

.input-wrapper--lg .input {
  padding: var(--space-4) var(--space-5);
  font-size: var(--font-size-base);
  min-height: 3rem;
}

/* Input with prefix/suffix adjustments */
.input--has-prefix {
  padding-left: 2.5rem;
}

.input--has-suffix {
  padding-right: 2.5rem;
}

.input-wrapper--sm .input--has-prefix {
  padding-left: 2rem;
}

.input-wrapper--sm .input--has-suffix {
  padding-right: 2rem;
}

.input-wrapper--lg .input--has-prefix {
  padding-left: 3rem;
}

.input-wrapper--lg .input--has-suffix {
  padding-right: 3rem;
}

/* Prefix and suffix */
.input-prefix,
.input-suffix {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: var(--space-1);
  pointer-events: none;
  color: var(--text-tertiary);
  z-index: 1;
}

.input-prefix {
  left: var(--space-3);
}

.input-suffix {
  right: var(--space-3);
}

.input-wrapper--sm .input-prefix {
  left: var(--space-2);
}

.input-wrapper--sm .input-suffix {
  right: var(--space-2);
}

.input-wrapper--lg .input-prefix {
  left: var(--space-4);
}

.input-wrapper--lg .input-suffix {
  right: var(--space-4);
}

/* Interactive elements in suffix */
.input-clear,
.input-toggle-password {
  pointer-events: auto;
  background: none;
  border: none;
  padding: var(--space-1);
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: color var(--duration-150) var(--ease-out);
  
  &:hover {
    color: var(--text-secondary);
  }
  
  &:focus-visible {
    outline: 2px solid var(--border-focus);
    outline-offset: 2px;
  }
}

/* Icons */
.input-icon {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
}

/* Help text */
.input-help {
  min-height: 1.25rem;
}

.input-error,
.input-hint {
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
}

.input-hint {
  color: var(--text-tertiary);
}

.input-error {
  color: var(--color-error);
}

/* Error state */
.input-wrapper--error .input {
  border-color: var(--border-error);
  
  &:focus {
    border-color: var(--border-error);
    box-shadow: 0 0 0 1px var(--border-error);
  }
}

/* Focused state */
.input-wrapper--focused .input-prefix,
.input-wrapper--focused .input-suffix {
  color: var(--text-secondary);
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .input {
    border-width: 2px;
  }
  
  .input:focus {
    box-shadow: 0 0 0 2px var(--input-focus);
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .input,
  .input-clear,
  .input-toggle-password {
    transition: none;
  }
}
</style>