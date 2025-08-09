<template>
  <div class="select-wrapper" :class="wrapperClasses">
    <label v-if="label" :for="selectId" class="select-label">
      {{ label }}
      <span v-if="required" class="select-required" aria-label="required">*</span>
    </label>
    
    <div class="select-container">
      <div v-if="$slots.prefix || prefixIcon" class="select-prefix">
        <slot name="prefix">
          <component v-if="prefixIcon" :is="prefixIcon" class="select-icon" />
        </slot>
      </div>
      
      <select
        :id="selectId"
        ref="selectRef"
        v-model="selectValue"
        :class="selectClasses"
        :disabled="disabled"
        :required="required"
        :multiple="multiple"
        :aria-label="ariaLabel || label"
        :aria-describedby="ariaDescribedby"
        :aria-invalid="hasError"
        @change="handleChange"
        @blur="handleBlur"
        @focus="handleFocus"
      >
        <option v-if="placeholder && !multiple" value="" disabled>
          {{ placeholder }}
        </option>
        
        <template v-if="options.length">
          <optgroup
            v-for="group in groupedOptions"
            :key="group.label || 'ungrouped'"
            :label="group.label"
          >
            <option
              v-for="option in group.options"
              :key="getOptionKey(option)"
              :value="getOptionValue(option)"
              :disabled="getOptionDisabled(option)"
              :selected="isOptionSelected(option)"
            >
              {{ getOptionLabel(option) }}
            </option>
          </optgroup>
        </template>
        
        <slot v-else />
      </select>
      
      <div class="select-chevron" aria-hidden="true">
        <svg class="select-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6,9 12,15 18,9"></polyline>
        </svg>
      </div>
    </div>
    
    <div v-if="hasError || hint" class="select-help" :class="{ 'select-help--error': hasError }">
      <div v-if="hasError" class="select-error" role="alert" :aria-live="liveRegion">
        {{ error }}
      </div>
      <div v-else-if="hint" class="select-hint">
        {{ hint }}
      </div>
    </div>
  </div>
</template>

<script>
import { computed, ref } from 'vue'

export default {
  name: 'BaseSelect',
  props: {
    // Value
    modelValue: {
      type: [String, Number, Array, Object],
      default: null
    },
    
    // Options
    options: {
      type: Array,
      default: () => []
    },
    optionLabel: {
      type: [String, Function],
      default: 'label'
    },
    optionValue: {
      type: [String, Function],
      default: 'value'
    },
    optionDisabled: {
      type: [String, Function],
      default: 'disabled'
    },
    optionGroup: {
      type: [String, Function],
      default: null
    },
    
    // Configuration
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
    multiple: {
      type: Boolean,
      default: false
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
    
    // States
    disabled: {
      type: Boolean,
      default: false
    },
    
    // Visual
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['sm', 'md', 'lg'].includes(value)
    },
    
    // Icons
    prefixIcon: {
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
    liveRegion: {
      type: String,
      default: 'polite',
      validator: (value) => ['polite', 'assertive', 'off'].includes(value)
    }
  },
  
  emits: ['update:modelValue', 'change', 'blur', 'focus'],
  
  setup(props, { emit }) {
    const selectRef = ref(null)
    const isFocused = ref(false)
    
    // Generate unique ID
    const selectId = computed(() => `select-${Math.random().toString(36).substr(2, 9)}`)
    
    // Computed properties
    const selectValue = computed({
      get: () => props.modelValue,
      set: (value) => {
        emit('update:modelValue', value)
      }
    })
    
    const hasError = computed(() => !!props.error)
    
    const wrapperClasses = computed(() => [
      'select-wrapper',
      `select-wrapper--${props.size}`,
      {
        'select-wrapper--disabled': props.disabled,
        'select-wrapper--error': hasError.value,
        'select-wrapper--focused': isFocused.value,
        'select-wrapper--multiple': props.multiple
      }
    ])
    
    const selectClasses = computed(() => [
      'select',
      {
        'select--has-prefix': props.prefixIcon || !!props.$slots?.prefix
      }
    ])
    
    // Option helpers
    const getOptionLabel = (option) => {
      if (typeof props.optionLabel === 'function') {
        return props.optionLabel(option)
      }
      return typeof option === 'object' ? option[props.optionLabel] : option
    }
    
    const getOptionValue = (option) => {
      if (typeof props.optionValue === 'function') {
        return props.optionValue(option)
      }
      return typeof option === 'object' ? option[props.optionValue] : option
    }
    
    const getOptionDisabled = (option) => {
      if (typeof props.optionDisabled === 'function') {
        return props.optionDisabled(option)
      }
      return typeof option === 'object' ? option[props.optionDisabled] : false
    }
    
    const getOptionKey = (option, index) => {
      const value = getOptionValue(option)
      return typeof value === 'object' ? index : value
    }
    
    const isOptionSelected = (option) => {
      const value = getOptionValue(option)
      if (props.multiple) {
        return Array.isArray(props.modelValue) && props.modelValue.includes(value)
      }
      return props.modelValue === value
    }
    
    // Group options
    const groupedOptions = computed(() => {
      if (!props.optionGroup) {
        return [{ label: null, options: props.options }]
      }
      
      const groups = new Map()
      
      props.options.forEach(option => {
        const groupKey = typeof props.optionGroup === 'function' 
          ? props.optionGroup(option)
          : option[props.optionGroup]
        
        if (!groups.has(groupKey)) {
          groups.set(groupKey, [])
        }
        groups.get(groupKey).push(option)
      })
      
      return Array.from(groups.entries()).map(([label, options]) => ({
        label,
        options
      }))
    })
    
    // Event handlers
    const handleChange = (event) => {
      const value = props.multiple 
        ? Array.from(event.target.selectedOptions, option => option.value)
        : event.target.value
      
      emit('update:modelValue', value)
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
    
    // Public methods
    const focus = () => {
      selectRef.value?.focus()
    }
    
    const blur = () => {
      selectRef.value?.blur()
    }
    
    return {
      selectRef,
      selectId,
      selectValue,
      hasError,
      wrapperClasses,
      selectClasses,
      groupedOptions,
      getOptionLabel,
      getOptionValue,
      getOptionDisabled,
      getOptionKey,
      isOptionSelected,
      handleChange,
      handleFocus,
      handleBlur,
      focus,
      blur
    }
  }
}
</script>

<style scoped>
/* Select wrapper */
.select-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

/* Label */
.select-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.select-required {
  color: var(--color-error);
}

/* Select container */
.select-container {
  position: relative;
  display: flex;
  align-items: center;
}

/* Select styles */
.select {
  width: 100%;
  font-family: var(--font-family-sans);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  color: var(--text-primary);
  background-color: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  transition: 
    border-color var(--duration-150) var(--ease-out),
    box-shadow var(--duration-150) var(--ease-out),
    background-color var(--duration-150) var(--ease-out);
  
  /* Hide default arrow in IE */
  &::-ms-expand {
    display: none;
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
}

/* Size variants */
.select-wrapper--sm .select {
  padding: var(--space-2) var(--space-8) var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  min-height: 2rem;
}

.select-wrapper--md .select {
  padding: var(--space-3) var(--space-10) var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  min-height: 2.5rem;
}

.select-wrapper--lg .select {
  padding: var(--space-4) var(--space-12) var(--space-4) var(--space-5);
  font-size: var(--font-size-base);
  min-height: 3rem;
}

/* Multiple select */
.select-wrapper--multiple .select {
  min-height: 6rem;
}

/* Select with prefix */
.select--has-prefix {
  padding-left: 2.5rem;
}

.select-wrapper--sm .select--has-prefix {
  padding-left: 2rem;
}

.select-wrapper--lg .select--has-prefix {
  padding-left: 3rem;
}

/* Prefix */
.select-prefix {
  position: absolute;
  left: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  pointer-events: none;
  color: var(--text-tertiary);
  z-index: 1;
}

.select-wrapper--sm .select-prefix {
  left: var(--space-2);
}

.select-wrapper--lg .select-prefix {
  left: var(--space-4);
}

/* Chevron */
.select-chevron {
  position: absolute;
  right: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
  color: var(--text-tertiary);
  transition: transform var(--duration-150) var(--ease-out);
}

.select-wrapper--sm .select-chevron {
  right: var(--space-2);
}

.select-wrapper--lg .select-chevron {
  right: var(--space-4);
}

.select-wrapper--focused .select-chevron {
  transform: translateY(-50%) rotate(180deg);
}

/* Icons */
.select-icon {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
}

/* Help text */
.select-help {
  min-height: 1.25rem;
}

.select-error,
.select-hint {
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
}

.select-hint {
  color: var(--text-tertiary);
}

.select-error {
  color: var(--color-error);
}

/* Error state */
.select-wrapper--error .select {
  border-color: var(--border-error);
  
  &:focus {
    border-color: var(--border-error);
    box-shadow: 0 0 0 1px var(--border-error);
  }
}

/* Focused state */
.select-wrapper--focused .select-prefix {
  color: var(--text-secondary);
}

/* Option groups */
optgroup {
  font-weight: var(--font-weight-semibold);
  color: var(--text-secondary);
}

option {
  padding: var(--space-2);
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

option:disabled {
  opacity: 0.5;
  color: var(--text-disabled);
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .select {
    border-width: 2px;
  }
  
  .select:focus {
    box-shadow: 0 0 0 2px var(--input-focus);
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .select,
  .select-chevron {
    transition: none;
  }
}
</style>