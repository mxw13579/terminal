<template>
  <div class="configuration-editor">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">
          <i class="fas fa-cog me-2"></i>
          Configuration Settings
        </h5>
        <small class="text-muted">Modify SillyTavern authentication and server settings</small>
      </div>
      
      <div class="card-body">
        <div v-if="loading" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading configuration...</span>
          </div>
          <p class="mt-2 text-muted">Loading configuration...</p>
        </div>

        <form v-else @submit.prevent="saveConfiguration" class="needs-validation" novalidate>
          <!-- Authentication Section -->
          <div class="row mb-4">
            <div class="col-md-12">
              <h6 class="fw-bold text-primary mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                Authentication Settings
              </h6>
            </div>
            
            <div class="col-md-6">
              <label for="username" class="form-label">
                Username <span class="text-danger">*</span>
              </label>
              <input
                type="text"
                class="form-control"
                :class="{ 'is-invalid': errors.username }"
                id="username"
                v-model="config.username"
                placeholder="Enter username"
                required
                minlength="3"
                pattern="[-a-zA-Z_]+"
              />
              <div v-if="errors.username" class="invalid-feedback">
                {{ errors.username }}
              </div>
              <div class="form-text">
                用户名至少3个字符（只允许字母、下划线和短横线，不能包含数字）
              </div>
            </div>
            
            <div class="col-md-6">
              <label for="password" class="form-label">
                Password
                <small class="text-muted">(leave empty to remove password)</small>
              </label>
              <div class="input-group">
                <input
                  :type="showPassword ? 'text' : 'password'"
                  class="form-control"
                  :class="{ 'is-invalid': errors.password }"
                  id="password"
                  v-model="config.password"
                  placeholder="Enter new password"
                  minlength="6"
                />
                <button
                  type="button"
                  class="btn btn-outline-secondary"
                  @click="showPassword = !showPassword"
                >
                  <i :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
                </button>
              </div>
              <div v-if="errors.password" class="invalid-feedback d-block">
                {{ errors.password }}
              </div>
              <div class="form-text">
                Password must be at least 6 characters (optional)
              </div>
            </div>
          </div>

          <!-- Server Settings Section - REMOVED PORT EDITING -->
          <!-- Port is now read-only and not editable by users -->
          <div class="row mb-4" v-if="config.port">
            <div class="col-md-12">
              <h6 class="fw-bold text-primary mb-3">
                <i class="fas fa-server me-2"></i>
                Server Information (Read-only)
              </h6>
            </div>
            
            <div class="col-md-6">
              <label for="port-readonly" class="form-label">
                Current Port
              </label>
              <input
                type="number"
                class="form-control"
                id="port-readonly"
                :value="config.port"
                readonly
                disabled
              />
              <div class="form-text">
                Port configuration is managed automatically
              </div>
            </div>
          </div>

          <!-- Additional Settings -->
          <div class="row mb-4" v-if="config.otherSettings && Object.keys(config.otherSettings).length > 0">
            <div class="col-md-12">
              <h6 class="fw-bold text-primary mb-3">
                <i class="fas fa-sliders-h me-2"></i>
                Additional Settings
              </h6>
              
              <div class="row">
                <div 
                  v-for="(value, key) in config.otherSettings" 
                  :key="key"
                  class="col-md-6 mb-3"
                >
                  <label :for="'setting-' + key" class="form-label text-capitalize">
                    {{ key.replace(/([A-Z])/g, ' $1').trim() }}
                  </label>
                  <input
                    type="text"
                    class="form-control"
                    :id="'setting-' + key"
                    v-model="config.otherSettings[key]"
                    :placeholder="'Enter ' + key"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="row">
            <div class="col-md-12">
              <div class="d-flex justify-content-between align-items-center">
                <div>
                  <button
                    type="button"
                    class="btn btn-outline-secondary me-2"
                    @click="loadConfiguration"
                    :disabled="saving"
                  >
                    <i class="fas fa-refresh me-1"></i>
                    Reset
                  </button>
                </div>
                
                <div>
                  <button
                    type="submit"
                    class="btn btn-primary"
                    :disabled="saving || !isFormValid"
                  >
                    <span v-if="saving" class="spinner-border spinner-border-sm me-2" role="status"></span>
                    <i v-else class="fas fa-save me-1"></i>
                    {{ saving ? 'Saving...' : 'Save Configuration' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </form>

        <!-- Restart Warning -->
        <div v-if="requiresRestart" class="alert alert-warning mt-3" role="alert">
          <i class="fas fa-exclamation-triangle me-2"></i>
          <strong>Container Restart Required:</strong> 
          Configuration changes require a container restart to take effect. 
          Please restart the SillyTavern container from the Service Controls section.
        </div>

        <!-- Success/Error Messages -->
        <div v-if="successMessage" class="alert alert-success mt-3" role="alert">
          <i class="fas fa-check-circle me-2"></i>
          {{ successMessage }}
        </div>
        
        <div v-if="errorMessage" class="alert alert-danger mt-3" role="alert">
          <i class="fas fa-exclamation-circle me-2"></i>
          {{ errorMessage }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'
import useConnectionManager from '@/composables/useConnectionManager'

export default {
  name: 'ConfigurationEditor',
  setup() {
    const sillyTavernApi = useSillyTavern()
    const { connectionState, getStompClient } = useConnectionManager()
    
    // Computed properties for connection status
    const isConnected = computed(() => connectionState.isConnected)
    const stompClient = computed(() => getStompClient())
    
    const loading = ref(true)
    const saving = ref(false)
    const showPassword = ref(false)
    const requiresRestart = ref(false)
    const successMessage = ref('')
    const errorMessage = ref('')
    
    const config = ref({
      username: '',
      password: '',
      hasPassword: false,
      port: 8000,
      containerName: 'sillytavern',
      otherSettings: {}
    })
    
    const errors = ref({})
    
    // Subscriptions for WebSocket responses
    let configSubscription = null
    let updateSubscription = null
    
    const isFormValid = computed(() => {
      return config.value.username && 
             config.value.username.length >= 3 && 
             (!config.value.password || config.value.password.length >= 6)
    })
    
    const validateForm = () => {
      errors.value = {}
      
      if (!config.value.username || config.value.username.trim().length === 0) {
        errors.value.username = 'Username is required'
      } else if (config.value.username.length < 3) {
        errors.value.username = 'Username must be at least 3 characters long'
      } else if (config.value.username.length > 20) {
        errors.value.username = 'Username must not exceed 20 characters'
      } else if (/\d/.test(config.value.username)) {
        errors.value.username = 'Username cannot contain numbers'
      } else if (!/^[a-zA-Z_-]+$/.test(config.value.username)) {
        errors.value.username = 'Username can only contain letters, underscore, and dash'
      }
      
      if (config.value.password && config.value.password.length < 6) {
        errors.value.password = 'Password must be at least 6 characters long'
      }
      
      // Port validation removed - port is no longer editable
      
      return Object.keys(errors.value).length === 0
    }
    
    const loadConfiguration = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket connection not established'
        loading.value = false
        return
      }
      
      const client = stompClient.value
      if (!client) {
        errorMessage.value = 'STOMP client is not available'
        loading.value = false
        return
      }
      
      try {
        loading.value = true
        successMessage.value = ''
        errorMessage.value = ''
        
        client.publish({
          destination: '/app/sillytavern/get-config',
          body: JSON.stringify({})
        })
      } catch (error) {
        console.error('Error sending configuration request:', error)
        errorMessage.value = 'Failed to request configuration: ' + error.message
        loading.value = false
      }
    }
    
    const saveConfiguration = () => {
      if (!validateForm()) {
        return
      }
      
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket connection not established'
        return
      }
      
      const client = stompClient.value
      if (!client) {
        errorMessage.value = 'STOMP client is not available'
        saving.value = false
        return
      }
      
      try {
        saving.value = true
        successMessage.value = ''
        errorMessage.value = ''
        requiresRestart.value = false
        
        client.publish({
          destination: '/app/sillytavern/update-config',
          body: JSON.stringify(config.value)
        })
      } catch (error) {
        console.error('Error sending configuration update:', error)
        errorMessage.value = 'Failed to save configuration: ' + error.message
        saving.value = false
      }
    }
    
    const handleConfigResponse = (message) => {
      console.log('ConfigurationEditor: Received config response:', message)
      try {
        const response = JSON.parse(message.body)
        console.log('Config response parsed:', response)
        loading.value = false
        
        if (response.success && response.payload) {
          config.value = {
            ...response.payload,
            password: '' // Don't populate password field for security
          }
        } else {
          errorMessage.value = 'Failed to load configuration'
        }
      } catch (error) {
        console.error('Error handling config response:', error)
        errorMessage.value = 'Error processing configuration response'
        loading.value = false
      }
    }
    
    const handleUpdateResponse = (message) => {
      console.log('ConfigurationEditor: Received config update response:', message)
      try {
        const response = JSON.parse(message.body)
        console.log('Config update response parsed:', response)
        saving.value = false
        
        if (response.success) {
          successMessage.value = response.message || 'Configuration saved successfully'
          requiresRestart.value = response.requiresRestart || false
          
          // Reload configuration to get updated values
          setTimeout(() => {
            loadConfiguration()
          }, 1000)
        } else {
          errorMessage.value = response.message || 'Failed to save configuration'
          
          if (response.errors) {
            errors.value = response.errors
          }
        }
      } catch (error) {
        console.error('Error handling update response:', error)
        errorMessage.value = 'Error processing configuration update response'
        saving.value = false
      }
    }
    
    onMounted(() => {
      // Subscribe to configuration responses
      console.log('ConfigurationEditor mounted, checking connection...')
      console.log('isConnected:', isConnected.value)
      console.log('stompClient:', !!stompClient.value)
      
      if (isConnected.value && stompClient.value) {
        const client = stompClient.value
        console.log('STOMP client available, setting up subscriptions')
        
        try {
          configSubscription = client.subscribe(
            `/user/queue/sillytavern/config`,
            handleConfigResponse
          )
          console.log('Config subscription created for:', `/user/queue/sillytavern/config`)
          
          updateSubscription = client.subscribe(
            `/user/queue/sillytavern/config-updated`,
            handleUpdateResponse
          )
          console.log('Config update subscription created for:', `/user/queue/sillytavern/config-updated`)
          
          console.log('Subscriptions created, loading configuration')
          // Load initial configuration
          loadConfiguration()
        } catch (error) {
          console.error('Error setting up ConfigurationEditor subscriptions:', error)
          errorMessage.value = 'Failed to initialize configuration editor: ' + error.message
          loading.value = false
        }
      } else {
        console.warn('ConfigurationEditor: Connection not available')
        errorMessage.value = 'SSH connection is required for configuration management'
        loading.value = false
      }
    })
    
    onUnmounted(() => {
      if (configSubscription) {
        configSubscription.unsubscribe()
      }
      if (updateSubscription) {
        updateSubscription.unsubscribe()
      }
    })
    
    return {
      loading,
      saving,
      showPassword,
      requiresRestart,
      successMessage,
      errorMessage,
      config,
      errors,
      isFormValid,
      loadConfiguration,
      saveConfiguration
    }
  }
}
</script>

<style scoped>
.configuration-editor {
  max-width: 800px;
  margin: 0 auto;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.card-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom: none;
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
}

.form-label {
  font-weight: 500;
  color: #495057;
  margin-bottom: 0.5rem;
}

.form-control:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 0.2rem rgba(102, 126, 234, 0.25);
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  padding: 0.6rem 1.5rem;
  font-weight: 500;
}

.btn-primary:hover {
  background: linear-gradient(135deg, #5969d3 0%, #6a4190 100%);
  transform: translateY(-1px);
}

.btn-outline-secondary {
  padding: 0.6rem 1.5rem;
  font-weight: 500;
}

.alert {
  border: none;
  border-radius: 8px;
}

.alert-warning {
  background-color: #fff3cd;
  color: #856404;
}

.alert-success {
  background-color: #d1edff;
  color: #0c5460;
}

.alert-danger {
  background-color: #f8d7da;
  color: #721c24;
}

.text-primary {
  color: #667eea !important;
}

.spinner-border-sm {
  width: 1rem;
  height: 1rem;
}

.input-group .btn {
  border-left: 0;
}

.form-text {
  font-size: 0.875rem;
  color: #6c757d;
}

h6.fw-bold {
  border-bottom: 2px solid #e9ecef;
  padding-bottom: 0.5rem;
  margin-bottom: 1rem;
}
</style>