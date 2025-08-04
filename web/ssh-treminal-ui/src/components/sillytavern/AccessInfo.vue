<template>
  <div class="access-info">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">
          <i class="fas fa-link me-2"></i>
          Access Information
        </h5>
        <small class="text-muted">Connection details and access links for your SillyTavern instance</small>
      </div>
      
      <div class="card-body">
        <div v-if="loading" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading access information...</span>
          </div>
          <p class="mt-2 text-muted">Loading access information...</p>
        </div>

        <div v-else-if="!containerStatus.exists" class="text-center py-4">
          <i class="fas fa-exclamation-triangle text-warning fa-3x mb-3"></i>
          <h6 class="text-muted">No SillyTavern Container Found</h6>
          <p class="text-muted">Deploy SillyTavern first to see access information.</p>
        </div>

        <div v-else>
          <!-- Container Status Banner -->
          <div class="row mb-4">
            <div class="col-12">
              <div 
                class="alert d-flex align-items-center"
                :class="containerStatus.running ? 'alert-success' : 'alert-warning'"
                role="alert"
              >
                <i 
                  :class="containerStatus.running ? 'fas fa-check-circle text-success' : 'fas fa-exclamation-triangle text-warning'"
                  class="me-2 fa-lg"
                ></i>
                <div>
                  <strong>Container Status:</strong> 
                  <span :class="containerStatus.running ? 'text-success' : 'text-warning'">
                    {{ containerStatus.running ? 'Running' : 'Stopped' }}
                  </span>
                  <div v-if="containerStatus.running && containerStatus.uptimeSeconds" class="small text-muted">
                    Uptime: {{ formatUptime(containerStatus.uptimeSeconds) }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Access Links -->
          <div v-if="containerStatus.running" class="row mb-4">
            <div class="col-md-6 mb-3">
              <div class="access-card">
                <div class="access-card-header">
                  <i class="fas fa-globe text-primary me-2"></i>
                  <strong>Web Access</strong>
                </div>
                <div class="access-card-body">
                  <div class="access-url">
                    <input
                      type="text"
                      class="form-control"
                      :value="webUrl"
                      readonly
                    />
                    <div class="access-actions">
                      <button
                        type="button"
                        class="btn btn-outline-primary btn-sm"
                        @click="copyToClipboard(webUrl)"
                        :disabled="copying === 'webUrl'"
                      >
                        <i v-if="copying === 'webUrl'" class="fas fa-check text-success"></i>
                        <i v-else class="fas fa-copy"></i>
                      </button>
                      <a
                        :href="webUrl"
                        target="_blank"
                        class="btn btn-primary btn-sm"
                        :class="{ disabled: !isAccessible }"
                      >
                        <i class="fas fa-external-link-alt me-1"></i>
                        Open
                      </a>
                    </div>
                  </div>
                  <div class="form-text">
                    Main SillyTavern web interface
                  </div>
                </div>
              </div>
            </div>

            <div class="col-md-6 mb-3">
              <div class="access-card">
                <div class="access-card-header">
                  <i class="fas fa-server text-info me-2"></i>
                  <strong>Local Access</strong>
                </div>
                <div class="access-card-body">
                  <div class="access-url">
                    <input
                      type="text"
                      class="form-control"
                      :value="localUrl"
                      readonly
                    />
                    <div class="access-actions">
                      <button
                        type="button"
                        class="btn btn-outline-primary btn-sm"
                        @click="copyToClipboard(localUrl)"
                        :disabled="copying === 'localUrl'"
                      >
                        <i v-if="copying === 'localUrl'" class="fas fa-check text-success"></i>
                        <i v-else class="fas fa-copy"></i>
                      </button>
                    </div>
                  </div>
                  <div class="form-text">
                    Direct server access (localhost)
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Connection Details -->
          <div class="row mb-4">
            <div class="col-md-12">
              <h6 class="fw-bold text-primary mb-3">
                <i class="fas fa-info-circle me-2"></i>
                Connection Details
              </h6>
              
              <div class="row">
                <div class="col-md-4 mb-3">
                  <div class="detail-card">
                    <label class="form-label fw-medium">Container Name</label>
                    <div class="detail-value">
                      <input
                        type="text"
                        class="form-control"
                        :value="containerStatus.containerName || 'sillytavern'"
                        readonly
                      />
                      <button
                        type="button"
                        class="btn btn-outline-secondary btn-sm ms-2"
                        @click="copyToClipboard(containerStatus.containerName || 'sillytavern')"
                        :disabled="copying === 'containerName'"
                      >
                        <i v-if="copying === 'containerName'" class="fas fa-check text-success"></i>
                        <i v-else class="fas fa-copy"></i>
                      </button>
                    </div>
                  </div>
                </div>

                <div class="col-md-4 mb-3">
                  <div class="detail-card">
                    <label class="form-label fw-medium">Port</label>
                    <div class="detail-value">
                      <input
                        type="text"
                        class="form-control"
                        :value="containerStatus.port || 8000"
                        readonly
                      />
                      <button
                        type="button"
                        class="btn btn-outline-secondary btn-sm ms-2"
                        @click="copyToClipboard(String(containerStatus.port || 8000))"
                        :disabled="copying === 'port'"
                      >
                        <i v-if="copying === 'port'" class="fas fa-check text-success"></i>
                        <i v-else class="fas fa-copy"></i>
                      </button>
                    </div>
                  </div>
                </div>

                <div class="col-md-4 mb-3">
                  <div class="detail-card">
                    <label class="form-label fw-medium">Container ID</label>
                    <div class="detail-value">
                      <input
                        type="text"
                        class="form-control"
                        :value="containerStatus.containerId || 'N/A'"
                        readonly
                      />
                      <button
                        type="button"
                        class="btn btn-outline-secondary btn-sm ms-2"
                        @click="copyToClipboard(containerStatus.containerId || 'N/A')"
                        :disabled="copying === 'containerId' || !containerStatus.containerId"
                      >
                        <i v-if="copying === 'containerId'" class="fas fa-check text-success"></i>
                        <i v-else class="fas fa-copy"></i>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Resource Usage (if available) -->
          <div v-if="containerStatus.running && (containerStatus.memoryUsageMB || containerStatus.cpuUsagePercent)" class="row mb-4">
            <div class="col-md-12">
              <h6 class="fw-bold text-primary mb-3">
                <i class="fas fa-chart-bar me-2"></i>
                Resource Usage
              </h6>
              
              <div class="row">
                <div class="col-md-6 mb-3" v-if="containerStatus.memoryUsageMB">
                  <div class="resource-card">
                    <div class="resource-header">
                      <i class="fas fa-memory text-info me-2"></i>
                      <strong>Memory Usage</strong>
                    </div>
                    <div class="resource-value">
                      {{ containerStatus.memoryUsageMB }} MB
                    </div>
                  </div>
                </div>

                <div class="col-md-6 mb-3" v-if="containerStatus.cpuUsagePercent !== undefined">
                  <div class="resource-card">
                    <div class="resource-header">
                      <i class="fas fa-microchip text-success me-2"></i>
                      <strong>CPU Usage</strong>
                    </div>
                    <div class="resource-value">
                      {{ containerStatus.cpuUsagePercent.toFixed(1) }}%
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Connection Test -->
          <div class="row">
            <div class="col-md-12">
              <div class="test-connection-section">
                <div class="d-flex justify-content-between align-items-center mb-3">
                  <h6 class="fw-bold text-primary mb-0">
                    <i class="fas fa-network-wired me-2"></i>
                    Connection Test
                  </h6>
                  <button
                    type="button"
                    class="btn btn-outline-primary btn-sm"
                    @click="testConnection"
                    :disabled="testing || !containerStatus.running"
                  >
                    <span v-if="testing" class="spinner-border spinner-border-sm me-2" role="status"></span>
                    <i v-else class="fas fa-sync me-1"></i>
                    {{ testing ? 'Testing...' : 'Test Connection' }}
                  </button>
                </div>
                
                <div v-if="connectionTestResult" class="alert" :class="connectionTestResult.success ? 'alert-success' : 'alert-danger'" role="alert">
                  <i :class="connectionTestResult.success ? 'fas fa-check-circle' : 'fas fa-times-circle'" class="me-2"></i>
                  {{ connectionTestResult.message }}
                  <div v-if="connectionTestResult.responseTime" class="small mt-1">
                    Response time: {{ connectionTestResult.responseTime }}ms
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Error Message -->
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

export default {
  name: 'AccessInfo',
  props: {
    containerStatus: {
      type: Object,
      default: () => ({
        exists: false,
        running: false,
        port: 8000,
        containerName: 'sillytavern',
        containerId: null,
        uptimeSeconds: 0,
        memoryUsageMB: null,
        cpuUsagePercent: null
      })
    }
  },
  setup(props) {
    const { stompClient, isConnected } = useSillyTavern()
    
    const loading = ref(false)
    const testing = ref(false)
    const copying = ref('')
    const isAccessible = ref(false)
    const connectionTestResult = ref(null)
    const errorMessage = ref('')
    
    const serverHost = window.location.hostname
    
    const webUrl = computed(() => {
      const port = props.containerStatus.port || 8000
      return `http://${serverHost}:${port}`
    })
    
    const localUrl = computed(() => {
      const port = props.containerStatus.port || 8000
      return `http://localhost:${port}`
    })
    
    const formatUptime = (seconds) => {
      if (!seconds) return 'N/A'
      
      const days = Math.floor(seconds / 86400)
      const hours = Math.floor((seconds % 86400) / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      
      if (days > 0) {
        return `${days}d ${hours}h ${minutes}m`
      } else if (hours > 0) {
        return `${hours}h ${minutes}m`
      } else {
        return `${minutes}m`
      }
    }
    
    const copyToClipboard = async (text) => {
      try {
        await navigator.clipboard.writeText(text)
        copying.value = getCopyingKey(text)
        
        setTimeout(() => {
          copying.value = ''
        }, 2000)
        
      } catch (error) {
        console.error('Failed to copy to clipboard:', error)
        // Fallback for older browsers
        const textArea = document.createElement('textarea')
        textArea.value = text
        document.body.appendChild(textArea)
        textArea.select()
        try {
          document.execCommand('copy')
          copying.value = getCopyingKey(text)
          setTimeout(() => {
            copying.value = ''
          }, 2000)
        } catch (err) {
          console.error('Fallback copy failed:', err)
        }
        document.body.removeChild(textArea)
      }
    }
    
    const getCopyingKey = (text) => {
      if (text === webUrl.value) return 'webUrl'
      if (text === localUrl.value) return 'localUrl'
      if (text === (props.containerStatus.containerName || 'sillytavern')) return 'containerName'
      if (text === String(props.containerStatus.port || 8000)) return 'port'
      if (text === (props.containerStatus.containerId || 'N/A')) return 'containerId'
      return 'unknown'
    }
    
    const testConnection = async () => {
      if (!props.containerStatus.running) {
        connectionTestResult.value = {
          success: false,
          message: 'Container is not running'
        }
        return
      }
      
      testing.value = true
      connectionTestResult.value = null
      
      try {
        const startTime = Date.now()
        const response = await fetch(webUrl.value, {
          method: 'HEAD',
          mode: 'no-cors',
          cache: 'no-cache'
        })
        
        const responseTime = Date.now() - startTime
        
        connectionTestResult.value = {
          success: true,
          message: 'Connection successful! SillyTavern is accessible.',
          responseTime: responseTime
        }
        
        isAccessible.value = true
        
      } catch (error) {
        connectionTestResult.value = {
          success: false,
          message: 'Connection failed. The service may not be ready yet or there may be a network issue.'
        }
        
        isAccessible.value = false
      } finally {
        testing.value = false
      }
    }
    
    // Auto-test connection when container becomes running
    const prevRunning = ref(props.containerStatus.running)
    
    const checkConnectionStatus = () => {
      if (props.containerStatus.running && !prevRunning.value) {
        // Container just started, test connection after a delay
        setTimeout(() => {
          testConnection()
        }, 3000)
      }
      prevRunning.value = props.containerStatus.running
    }
    
    onMounted(() => {
      if (props.containerStatus.running) {
        // Test connection on mount if container is running
        setTimeout(() => {
          testConnection()
        }, 1000)
      }
    })
    
    // Watch for container status changes
    const unwatchStatus = ref(null)
    onMounted(() => {
      unwatchStatus.value = setInterval(checkConnectionStatus, 2000)
    })
    
    onUnmounted(() => {
      if (unwatchStatus.value) {
        clearInterval(unwatchStatus.value)
      }
    })
    
    return {
      loading,
      testing,
      copying,
      isAccessible,
      connectionTestResult,
      errorMessage,
      webUrl,
      localUrl,
      formatUptime,
      copyToClipboard,
      testConnection
    }
  }
}
</script>

<style scoped>
.access-info {
  max-width: 900px;
  margin: 0 auto;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.card-header {
  background: linear-gradient(135deg, #6f42c1 0%, #6610f2 100%);
  color: white;
  border-bottom: none;
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
}

.access-card,
.detail-card,
.resource-card {
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  padding: 1rem;
  height: 100%;
}

.access-card-header,
.resource-header {
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: #495057;
}

.access-url {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.access-url .form-control {
  flex: 1;
}

.access-actions {
  display: flex;
  gap: 0.25rem;
}

.detail-value {
  display: flex;
  align-items: center;
}

.detail-value .form-control {
  flex: 1;
}

.resource-value {
  font-size: 1.5rem;
  font-weight: 600;
  color: #495057;
  text-align: center;
  margin-top: 0.5rem;
}

.test-connection-section {
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  padding: 1.5rem;
}

.form-control:focus {
  border-color: #6f42c1;
  box-shadow: 0 0 0 0.2rem rgba(111, 66, 193, 0.25);
}

.btn-primary {
  background: linear-gradient(135deg, #6f42c1 0%, #6610f2 100%);
  border: none;
  padding: 0.4rem 1rem;
  font-weight: 500;
}

.btn-outline-primary {
  border-color: #6f42c1;
  color: #6f42c1;
  padding: 0.4rem 1rem;
  font-weight: 500;
}

.btn-outline-primary:hover {
  background-color: #6f42c1;
  border-color: #6f42c1;
}

.btn-outline-secondary {
  padding: 0.4rem 0.8rem;
}

.btn:hover {
  transform: translateY(-1px);
}

.alert {
  border: none;
  border-radius: 8px;
}

.alert-success {
  background-color: #d1edff;
  color: #0c5460;
}

.alert-warning {
  background-color: #fff3cd;
  color: #856404;
}

.alert-danger {
  background-color: #f8d7da;
  color: #721c24;
}

.text-primary {
  color: #6f42c1 !important;
}

h6.fw-bold {
  border-bottom: 2px solid #e9ecef;
  padding-bottom: 0.5rem;
  margin-bottom: 1rem;
}

.form-text {
  font-size: 0.875rem;
  color: #6c757d;
}

.disabled {
  opacity: 0.6;
  pointer-events: none;
}
</style>