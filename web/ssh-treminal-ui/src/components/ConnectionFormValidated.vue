<template>
  <form class="connection-form" @submit.prevent="handleSubmit">
    <div class="connection-form-header">
      <h2 class="connection-form-title">SSH Connection</h2>
      <p class="connection-form-description">
        Enter your SSH connection details below
      </p>
    </div>

    <div class="connection-form-body">
      <div class="connection-form-row">
        <BaseInput
          v-bind="getFieldProps('host')"
          label="Host"
          placeholder="Enter hostname or IP address"
          required
          :aria-describedby="errors.host ? 'host-error' : 'host-hint'"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="2" y1="12" x2="22" y2="12"/>
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
            </svg>
          </template>
        </BaseInput>
        <div v-if="!errors.host" id="host-hint" class="form-hint">
          Enter a valid hostname, domain, or IP address
        </div>
      </div>

      <div class="connection-form-row">
        <BaseInput
          v-bind="getFieldProps('port')"
          type="number"
          label="Port"
          placeholder="22"
          :min="1"
          :max="65535"
          required
          :aria-describedby="errors.port ? 'port-error' : 'port-hint'"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
              <circle cx="12" cy="16" r="1"/>
            </svg>
          </template>
        </BaseInput>
        <div v-if="!errors.port" id="port-hint" class="form-hint">
          SSH port (default: 22)
        </div>
      </div>

      <div class="connection-form-row">
        <BaseInput
          v-bind="getFieldProps('user')"
          label="Username"
          placeholder="Enter username"
          required
          autocomplete="username"
          :aria-describedby="errors.user ? 'user-error' : 'user-hint'"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </template>
        </BaseInput>
        <div v-if="!errors.user" id="user-hint" class="form-hint">
          SSH username for authentication
        </div>
      </div>

      <div class="connection-form-row">
        <BaseInput
          v-bind="getFieldProps('password')"
          type="password"
          label="Password"
          placeholder="Enter password"
          required
          autocomplete="current-password"
          :aria-describedby="errors.password ? 'password-error' : 'password-hint'"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            </svg>
          </template>
        </BaseInput>
        <div v-if="!errors.password" id="password-hint" class="form-hint">
          SSH password for authentication
        </div>
      </div>

      <!-- Connection Status -->
      <div v-if="connectionState.isLoading || connectionError" class="connection-status">
        <LoadingSpinner
          v-if="connectionState.isLoading"
          size="sm"
          :message="connectionState.message"
        />
        
        <ErrorState
          v-else-if="connectionError"
          :message="connectionError"
          size="sm"
          :can-retry="true"
          @retry="handleRetry"
        />
      </div>
    </div>

    <div class="connection-form-footer">
      <div class="connection-form-actions">
        <BaseButton
          type="button"
          variant="secondary"
          @click="handleReset"
          :disabled="connectionState.isLoading"
        >
          Reset
        </BaseButton>
        
        <BaseButton
          type="submit"
          variant="primary"
          :loading="connectionState.isLoading"
          :disabled="!isValid || connectionState.isLoading"
        >
          {{ connectionState.isLoading ? 'Connecting...' : 'Connect' }}
        </BaseButton>
      </div>
      
      <div class="connection-form-security">
        <svg class="security-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 12l2 2 4-4"/>
          <path d="M21 12c-1 0-3-1-3-3s2-3 3-3 3 1 3 3-2 3-3 3"/>
          <path d="M3 12c1 0 3-1 3-3s-2-3-3-3-3 1-3 3 2 3 3 3"/>
          <path d="M3 12h6m6 0h6"/>
        </svg>
        <span>Connection secured with RSA encryption</span>
      </div>
    </div>
  </form>
</template>

<script>
import { computed } from 'vue'
import { BaseInput, BaseButton, LoadingSpinner, ErrorState } from '@/components/ui'
import { useValidation, validationRules } from '@/composables/useValidation'
import { useTerminalStore } from '@/stores'

export default {
  name: 'ConnectionForm',
  components: {
    BaseInput,
    BaseButton,
    LoadingSpinner,
    ErrorState
  },
  
  emits: ['connect', 'retry'],
  
  setup(props, { emit }) {
    const terminalStore = useTerminalStore()
    
    // Form validation setup
    const initialValues = {
      host: '',
      port: 22,
      user: '',
      password: ''
    }

    const rules = {
      host: [
        validationRules.required(),
        validationRules.hostname()
      ],
      port: [
        validationRules.required(),
        validationRules.port()
      ],
      user: [
        validationRules.required(),
        validationRules.minLength(1),
        validationRules.maxLength(64)
      ],
      password: [
        validationRules.required(),
        validationRules.minLength(1)
      ]
    }

    const {
      values,
      errors,
      isValid,
      validateAll,
      getFieldProps,
      resetForm
    } = useValidation(initialValues, rules, {
      validateOnChange: true,
      validateOnBlur: true,
      mode: 'progressive'
    })

    // Connection state from store
    const connectionState = computed(() => terminalStore.connectionState)
    const connectionError = computed(() => terminalStore.connectionError)

    // Form handlers
    const handleSubmit = async () => {
      const isFormValid = await validateAll()
      
      if (isFormValid) {
        try {
          await terminalStore.connect({
            host: values.host.trim(),
            port: parseInt(values.port),
            user: values.user.trim(),
            password: values.password
          })
          
          emit('connect', values)
        } catch (error) {
          console.error('Connection failed:', error)
        }
      }
    }

    const handleReset = () => {
      resetForm()
      terminalStore.clearError()
    }

    const handleRetry = () => {
      terminalStore.clearError()
      emit('retry')
    }

    return {
      values,
      errors,
      isValid,
      connectionState,
      connectionError,
      getFieldProps,
      handleSubmit,
      handleReset,
      handleRetry
    }
  }
}
</script>

<style scoped>
.connection-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 28rem;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-6);
  background-color: var(--bg-elevated);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-primary);
  box-shadow: var(--shadow-lg);
}

.connection-form-header {
  text-align: center;
}

.connection-form-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin: 0 0 var(--space-2) 0;
}

.connection-form-description {
  color: var(--text-secondary);
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
}

.connection-form-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.connection-form-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.form-hint {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  line-height: var(--line-height-tight);
}

.connection-status {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-primary);
  background-color: var(--bg-secondary);
}

.connection-form-footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.connection-form-actions {
  display: flex;
  gap: var(--space-3);
}

.connection-form-actions > * {
  flex: 1;
}

.connection-form-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  padding: var(--space-3);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  background-color: var(--bg-secondary);
}

.security-icon {
  width: 1rem;
  height: 1rem;
  color: var(--color-success);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .connection-form {
    padding: var(--space-4);
    max-width: none;
    margin: var(--space-4);
  }
  
  .connection-form-title {
    font-size: var(--font-size-xl);
  }
}

/* High contrast mode */
@media (prefers-contrast: high) {
  .connection-form {
    border-width: 2px;
  }
  
  .connection-status,
  .connection-form-security {
    border-width: 2px;
  }
}

/* Focus styles for form accessibility */
.connection-form:focus-within {
  box-shadow: var(--shadow-lg), 0 0 0 2px var(--border-focus);
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .connection-form {
    transition: none;
  }
}
</style>