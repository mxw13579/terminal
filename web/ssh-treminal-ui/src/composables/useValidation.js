/**
 * Form validation composable with real-time feedback
 * Provides comprehensive validation features for forms
 */

import { ref, reactive, computed, watch, nextTick } from 'vue'
import { debounce } from '@/utils/performance'

/**
 * Built-in validation rules
 */
export const validationRules = {
  required: (value, message = 'This field is required') => {
    if (value === null || value === undefined || value === '') {
      return message
    }
    if (Array.isArray(value) && value.length === 0) {
      return message
    }
    return true
  },

  email: (value, message = 'Please enter a valid email address') => {
    if (!value) return true // Let required rule handle empty values
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(value) || message
  },

  minLength: (min) => (value, message = `Must be at least ${min} characters`) => {
    if (!value) return true
    return value.length >= min || message
  },

  maxLength: (max) => (value, message = `Must be no more than ${max} characters`) => {
    if (!value) return true
    return value.length <= max || message
  },

  min: (min) => (value, message = `Must be at least ${min}`) => {
    if (value === '' || value === null || value === undefined) return true
    return Number(value) >= min || message
  },

  max: (max) => (value, message = `Must be no more than ${max}`) => {
    if (value === '' || value === null || value === undefined) return true
    return Number(value) <= max || message
  },

  pattern: (regex, message = 'Invalid format') => (value) => {
    if (!value) return true
    return regex.test(value) || message
  },

  url: (value, message = 'Please enter a valid URL') => {
    if (!value) return true
    try {
      new URL(value)
      return true
    } catch {
      return message
    }
  },

  numeric: (value, message = 'Must be a number') => {
    if (!value) return true
    return !isNaN(Number(value)) || message
  },

  integer: (value, message = 'Must be a whole number') => {
    if (!value) return true
    return Number.isInteger(Number(value)) || message
  },

  port: (value, message = 'Must be a valid port number (1-65535)') => {
    if (!value) return true
    const num = Number(value)
    return (Number.isInteger(num) && num >= 1 && num <= 65535) || message
  },

  hostname: (value, message = 'Must be a valid hostname or IP address') => {
    if (!value) return true
    
    // Check for valid hostname
    const hostnameRegex = /^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/
    
    // Check for valid IPv4
    const ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
    
    // Check for valid IPv6
    const ipv6Regex = /^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$/
    
    return (hostnameRegex.test(value) || ipv4Regex.test(value) || ipv6Regex.test(value)) || message
  },

  match: (otherField, otherLabel) => (value, message = `Must match ${otherLabel}`) => {
    return value === otherField || message
  },

  custom: (validator, message) => (value) => {
    const result = validator(value)
    return result === true || message
  }
}

/**
 * Validation composable
 * @param {Object} initialValues - Initial form values
 * @param {Object} rules - Validation rules for each field
 * @param {Object} options - Validation options
 * @returns {Object} Validation state and methods
 */
export function useValidation(initialValues = {}, rules = {}, options = {}) {
  const {
    validateOnMount = false,
    validateOnChange = true,
    validateOnBlur = true,
    debounceMs = 300,
    mode = 'progressive' // 'progressive', 'aggressive', 'lazy'
  } = options

  // Form state
  const values = reactive({ ...initialValues })
  const errors = reactive({})
  const touched = reactive({})
  const validating = reactive({})
  const fieldMeta = reactive({})

  // Global validation state
  const isValidating = ref(false)
  const isValid = computed(() => {
    return Object.keys(errors).length === 0 && Object.values(errors).every(error => !error)
  })
  const isDirty = computed(() => {
    return Object.keys(touched).some(key => touched[key])
  })

  // Initialize field meta
  const initializeField = (fieldName) => {
    if (!fieldMeta[fieldName]) {
      fieldMeta[fieldName] = {
        initialValue: values[fieldName],
        isChanged: false,
        validationAttempted: false
      }
    }
  }

  // Validate single field
  const validateField = async (fieldName, value = values[fieldName]) => {
    const fieldRules = rules[fieldName]
    if (!fieldRules) return true

    initializeField(fieldName)
    validating[fieldName] = true

    try {
      const ruleArray = Array.isArray(fieldRules) ? fieldRules : [fieldRules]
      
      for (const rule of ruleArray) {
        let result
        
        if (typeof rule === 'function') {
          result = await rule(value)
        } else if (typeof rule === 'object' && rule.validator) {
          result = await rule.validator(value)
        } else {
          continue
        }

        if (result !== true) {
          errors[fieldName] = result
          fieldMeta[fieldName].validationAttempted = true
          return false
        }
      }

      // Clear error if validation passed
      delete errors[fieldName]
      fieldMeta[fieldName].validationAttempted = true
      return true
      
    } finally {
      validating[fieldName] = false
    }
  }

  // Debounced validation
  const debouncedValidateField = debounce(validateField, debounceMs)

  // Validate all fields
  const validateAll = async () => {
    isValidating.value = true
    
    try {
      const fieldNames = Object.keys(rules)
      const results = await Promise.all(
        fieldNames.map(fieldName => validateField(fieldName))
      )
      
      return results.every(Boolean)
    } finally {
      isValidating.value = false
    }
  }

  // Clear validation for field
  const clearFieldValidation = (fieldName) => {
    delete errors[fieldName]
    validating[fieldName] = false
    if (fieldMeta[fieldName]) {
      fieldMeta[fieldName].validationAttempted = false
    }
  }

  // Clear all validation
  const clearValidation = () => {
    Object.keys(errors).forEach(key => delete errors[key])
    Object.keys(validating).forEach(key => validating[key] = false)
    Object.keys(fieldMeta).forEach(key => {
      if (fieldMeta[key]) {
        fieldMeta[key].validationAttempted = false
      }
    })
  }

  // Set field value with validation
  const setFieldValue = (fieldName, value, shouldValidate = validateOnChange) => {
    values[fieldName] = value
    
    initializeField(fieldName)
    fieldMeta[fieldName].isChanged = value !== fieldMeta[fieldName].initialValue
    
    if (shouldValidate) {
      const shouldValidateNow = 
        mode === 'aggressive' ||
        (mode === 'progressive' && (touched[fieldName] || fieldMeta[fieldName].validationAttempted))
      
      if (shouldValidateNow) {
        debouncedValidateField(fieldName, value)
      }
    }
  }

  // Set field touched
  const setFieldTouched = (fieldName, isTouched = true, shouldValidate = validateOnBlur) => {
    touched[fieldName] = isTouched
    
    if (isTouched && shouldValidate) {
      const shouldValidateNow = 
        mode === 'aggressive' ||
        mode === 'progressive' ||
        (mode === 'lazy' && fieldMeta[fieldName]?.validationAttempted)
      
      if (shouldValidateNow) {
        validateField(fieldName)
      }
    }
  }

  // Reset form
  const resetForm = () => {
    Object.keys(values).forEach(key => {
      values[key] = initialValues[key]
    })
    Object.keys(touched).forEach(key => touched[key] = false)
    Object.keys(fieldMeta).forEach(key => {
      if (fieldMeta[key]) {
        fieldMeta[key].isChanged = false
        fieldMeta[key].validationAttempted = false
      }
    })
    clearValidation()
  }

  // Set form values
  const setValues = (newValues, shouldValidate = false) => {
    Object.keys(newValues).forEach(key => {
      setFieldValue(key, newValues[key], shouldValidate)
    })
  }

  // Set form errors
  const setErrors = (newErrors) => {
    Object.keys(newErrors).forEach(key => {
      errors[key] = newErrors[key]
    })
  }

  // Get field props for form inputs
  const getFieldProps = (fieldName) => {
    initializeField(fieldName)
    
    return {
      modelValue: values[fieldName],
      error: errors[fieldName],
      loading: validating[fieldName],
      'onUpdate:modelValue': (value) => setFieldValue(fieldName, value),
      onBlur: () => setFieldTouched(fieldName, true),
      onFocus: () => {
        // Clear validation error on focus if in progressive mode
        if (mode === 'progressive' && errors[fieldName]) {
          clearFieldValidation(fieldName)
        }
      }
    }
  }

  // Get field state
  const getFieldState = (fieldName) => {
    return {
      value: values[fieldName],
      error: errors[fieldName],
      touched: touched[fieldName],
      validating: validating[fieldName],
      isChanged: fieldMeta[fieldName]?.isChanged || false,
      isValid: !errors[fieldName],
      isDirty: touched[fieldName] && fieldMeta[fieldName]?.isChanged
    }
  }

  // Watch for form changes in aggressive mode
  if (mode === 'aggressive') {
    Object.keys(rules).forEach(fieldName => {
      watch(() => values[fieldName], (newValue) => {
        debouncedValidateField(fieldName, newValue)
      })
    })
  }

  // Validate on mount if requested
  if (validateOnMount) {
    nextTick(() => {
      validateAll()
    })
  }

  return {
    // Values
    values,
    errors,
    touched,
    validating,
    fieldMeta,
    
    // Computed state
    isValidating,
    isValid,
    isDirty,
    
    // Methods
    validateField,
    validateAll,
    clearFieldValidation,
    clearValidation,
    setFieldValue,
    setFieldTouched,
    resetForm,
    setValues,
    setErrors,
    getFieldProps,
    getFieldState,
    
    // Utilities
    initializeField
  }
}

/**
 * Field validation composable for individual fields
 * @param {*} initialValue - Initial field value
 * @param {Array|Function} rules - Validation rules
 * @param {Object} options - Validation options
 * @returns {Object} Field validation state and methods
 */
export function useFieldValidation(initialValue = '', rules = [], options = {}) {
  const {
    validateOnChange = true,
    validateOnBlur = true,
    debounceMs = 300
  } = options

  const value = ref(initialValue)
  const error = ref(null)
  const touched = ref(false)
  const validating = ref(false)
  const validationAttempted = ref(false)

  const isValid = computed(() => !error.value)
  const isDirty = computed(() => value.value !== initialValue)

  const validate = async (val = value.value) => {
    validating.value = true
    error.value = null

    try {
      const ruleArray = Array.isArray(rules) ? rules : [rules]
      
      for (const rule of ruleArray) {
        if (typeof rule === 'function') {
          const result = await rule(val)
          if (result !== true) {
            error.value = result
            break
          }
        }
      }
      
      validationAttempted.value = true
      return !error.value
    } finally {
      validating.value = false
    }
  }

  const debouncedValidate = debounce(validate, debounceMs)

  const setValue = (newValue, shouldValidate = validateOnChange) => {
    value.value = newValue
    
    if (shouldValidate && (touched.value || validationAttempted.value)) {
      debouncedValidate(newValue)
    }
  }

  const setTouched = (isTouched = true, shouldValidate = validateOnBlur) => {
    touched.value = isTouched
    
    if (isTouched && shouldValidate) {
      validate()
    }
  }

  const clear = () => {
    error.value = null
    validating.value = false
    validationAttempted.value = false
  }

  const reset = () => {
    value.value = initialValue
    touched.value = false
    clear()
  }

  return {
    value,
    error,
    touched,
    validating,
    validationAttempted,
    isValid,
    isDirty,
    validate,
    setValue,
    setTouched,
    clear,
    reset
  }
}