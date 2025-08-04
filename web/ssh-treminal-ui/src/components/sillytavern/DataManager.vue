<template>
  <div class="data-manager">
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">
          <i class="fas fa-archive me-2"></i>
          Data Management
        </h5>
        <small class="text-muted">Export and import your SillyTavern data</small>
      </div>
      
      <div class="card-body">
        <!-- Export Section -->
        <div class="mb-5">
          <h6 class="fw-bold text-primary mb-3">
            <i class="fas fa-download me-2"></i>
            Export Data
          </h6>
          <p class="text-muted mb-3">
            Create a backup of your SillyTavern data including characters, chats, settings, and user content.
          </p>
          
          <div class="export-section">
            <div v-if="exportProgress" class="mb-3">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="fw-medium">{{ exportStatus }}</span>
                <span class="text-muted">{{ Math.round(exportProgress) }}%</span>
              </div>
              <div class="progress">
                <div 
                  class="progress-bar progress-bar-striped progress-bar-animated"
                  :style="{ width: exportProgress + '%' }"
                  role="progressbar"
                ></div>
              </div>
            </div>
            
            <div v-if="exportResult" class="alert alert-success mb-3" role="alert">
              <i class="fas fa-check-circle me-2"></i>
              <strong>Export Complete!</strong>
              <div class="mt-2">
                <p class="mb-2">
                  <strong>File:</strong> {{ exportResult.filename }}<br>
                  <strong>Size:</strong> {{ formatFileSize(exportResult.sizeBytes) }}<br>
                  <strong>Expires:</strong> {{ formatDate(exportResult.expiresAt) }}
                </p>
                <a 
                  :href="exportResult.downloadUrl" 
                  class="btn btn-success btn-sm"
                  download
                >
                  <i class="fas fa-download me-1"></i>
                  Download Archive
                </a>
              </div>
            </div>
            
            <button
              type="button"
              class="btn btn-primary"
              @click="startExport"
              :disabled="exporting || importing"
            >
              <span v-if="exporting" class="spinner-border spinner-border-sm me-2" role="status"></span>
              <i v-else class="fas fa-download me-1"></i>
              {{ exporting ? 'Exporting...' : 'Export Data' }}
            </button>
          </div>
        </div>

        <!-- Import Section -->
        <div class="mb-4">
          <h6 class="fw-bold text-primary mb-3">
            <i class="fas fa-upload me-2"></i>
            Import Data
          </h6>
          <p class="text-muted mb-3">
            Restore SillyTavern data from a previously exported archive. 
            <span class="text-warning">
              <strong>Warning:</strong> This will replace all existing data.
            </span>
          </p>
          
          <div class="import-section">
            <!-- File Upload -->
            <div class="mb-3">
              <label for="importFile" class="form-label">
                Select Data Archive <span class="text-danger">*</span>
              </label>
              <input
                type="file"
                class="form-control"
                :class="{ 'is-invalid': uploadError }"
                id="importFile"
                ref="fileInput"
                @change="handleFileSelect"
                accept=".zip"
                :disabled="importing || exporting"
              >
              <div v-if="uploadError" class="invalid-feedback">
                {{ uploadError }}
              </div>
              <div class="form-text">
                Only ZIP files are supported. Maximum size: 5GB.
              </div>
            </div>

            <!-- Selected File Info -->
            <div v-if="selectedFile" class="alert alert-info mb-3">
              <i class="fas fa-file-archive me-2"></i>
              <strong>Selected File:</strong> {{ selectedFile.name }}<br>
              <strong>Size:</strong> {{ formatFileSize(selectedFile.size) }}
            </div>

            <!-- Upload Progress -->
            <div v-if="uploadProgress > 0 && uploadProgress < 100 && !importProgress" class="mb-3">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="fw-medium">Uploading file...</span>
                <span class="text-muted">{{ Math.round(uploadProgress) }}%</span>
              </div>
              <div class="progress">
                <div 
                  class="progress-bar bg-info progress-bar-striped progress-bar-animated"
                  :style="{ width: uploadProgress + '%' }"
                  role="progressbar"
                ></div>
              </div>
            </div>

            <!-- Import Progress -->
            <div v-if="importProgress" class="mb-3">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="fw-medium">{{ importStatus }}</span>
                <span class="text-muted">{{ Math.round(importProgress) }}%</span>
              </div>
              <div class="progress">
                <div 
                  class="progress-bar bg-warning progress-bar-striped progress-bar-animated"
                  :style="{ width: importProgress + '%' }"
                  role="progressbar"
                ></div>
              </div>
            </div>
            
            <!-- Import Controls -->
            <div class="d-flex align-items-center gap-3">
              <button
                type="button"
                class="btn btn-warning"
                @click="startImport"
                :disabled="!selectedFile || importing || exporting || uploadProgress > 0"
              >
                <span v-if="importing" class="spinner-border spinner-border-sm me-2" role="status"></span>
                <i v-else class="fas fa-upload me-1"></i>
                {{ importing ? 'Importing...' : 'Import Data' }}
              </button>
              
              <button
                type="button"
                class="btn btn-outline-secondary"
                @click="clearSelection"
                :disabled="importing || exporting || uploadProgress > 0"
              >
                <i class="fas fa-times me-1"></i>
                Clear
              </button>
            </div>
          </div>
        </div>

        <!-- Important Notes -->
        <div class="alert alert-warning" role="alert">
          <i class="fas fa-exclamation-triangle me-2"></i>
          <strong>Important Notes:</strong>
          <ul class="mb-0 mt-2">
            <li>Data import will completely replace all existing SillyTavern data</li>
            <li>The container will be restarted after successful import</li>
            <li>Export files are automatically deleted after 1 hour for security</li>
            <li>Ensure you have sufficient disk space before importing large archives</li>
          </ul>
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'

export default {
  name: 'DataManager',
  setup() {
    const { stompClient, isConnected } = useSillyTavern()
    
    const exporting = ref(false)
    const importing = ref(false)
    const selectedFile = ref(null)
    const uploadProgress = ref(0)
    const uploadError = ref('')
    
    const exportProgress = ref(0)
    const exportStatus = ref('')
    const exportResult = ref(null)
    
    const importProgress = ref(0)
    const importStatus = ref('')
    
    const successMessage = ref('')
    const errorMessage = ref('')
    
    const fileInput = ref(null)
    
    // Subscriptions for WebSocket responses
    let exportSubscription = null
    let importSubscription = null
    let exportProgressSubscription = null
    let importProgressSubscription = null
    
    const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024 // 5GB
    
    const formatFileSize = (bytes) => {
      if (bytes === 0) return '0 Bytes'
      const k = 1024
      const sizes = ['Bytes', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }
    
    const formatDate = (dateString) => {
      return new Date(dateString).toLocaleString()
    }
    
    const handleFileSelect = (event) => {
      const file = event.target.files[0]
      uploadError.value = ''
      
      if (!file) {
        selectedFile.value = null
        return
      }
      
      // Validate file type
      if (!file.name.toLowerCase().endsWith('.zip')) {
        uploadError.value = 'Only ZIP files are supported'
        event.target.value = ''
        return
      }
      
      // Validate file size
      if (file.size > MAX_FILE_SIZE) {
        uploadError.value = `File is too large. Maximum size is ${formatFileSize(MAX_FILE_SIZE)}`
        event.target.value = ''
        return
      }
      
      selectedFile.value = file
    }
    
    const clearSelection = () => {
      selectedFile.value = null
      uploadProgress.value = 0
      uploadError.value = ''
      if (fileInput.value) {
        fileInput.value.value = ''
      }
    }
    
    const startExport = () => {
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket connection not established'
        return
      }
      
      exporting.value = true
      exportProgress.value = 0
      exportStatus.value = 'Starting export...'
      exportResult.value = null
      successMessage.value = ''
      errorMessage.value = ''
      
      stompClient.value.send('/app/sillytavern/export-data', {}, JSON.stringify({}))
    }
    
    const startImport = async () => {
      if (!selectedFile.value) {
        uploadError.value = 'Please select a file to import'
        return
      }
      
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket connection not established'
        return
      }
      
      importing.value = true
      importProgress.value = 0
      importStatus.value = 'Preparing import...'
      successMessage.value = ''
      errorMessage.value = ''
      
      try {
        // Upload file first
        const uploadedFileName = await uploadFile(selectedFile.value)
        
        // Start import process
        stompClient.value.send('/app/sillytavern/import-data', {}, JSON.stringify({
          uploadedFileName: uploadedFileName
        }))
        
      } catch (error) {
        importing.value = false
        errorMessage.value = 'File upload failed: ' + error.message
      }
    }
    
    const uploadFile = (file) => {
      return new Promise((resolve, reject) => {
        const formData = new FormData()
        formData.append('file', file)
        
        const xhr = new XMLHttpRequest()
        
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            uploadProgress.value = (event.loaded / event.total) * 100
          }
        })
        
        xhr.addEventListener('load', () => {
          if (xhr.status === 200) {
            const response = JSON.parse(xhr.responseText)
            uploadProgress.value = 100
            resolve(response.filename)
          } else {
            reject(new Error('Upload failed with status: ' + xhr.status))
          }
        })
        
        xhr.addEventListener('error', () => {
          reject(new Error('Upload failed due to network error'))
        })
        
        // This endpoint would need to be implemented in the backend
        xhr.open('POST', '/api/sillytavern/upload')
        xhr.send(formData)
      })
    }
    
    const handleExportResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        exporting.value = false
        exportProgress.value = 100
        
        if (response.success && response.payload) {
          exportResult.value = response.payload
          exportStatus.value = 'Export completed successfully'
          successMessage.value = 'Data exported successfully. Download will expire in 1 hour.'
        } else {
          errorMessage.value = 'Export failed: ' + (response.message || 'Unknown error')
        }
      } catch (error) {
        console.error('Error handling export response:', error)
        errorMessage.value = 'Error processing export response'
        exporting.value = false
      }
    }
    
    const handleImportResponse = (message) => {
      try {
        const response = JSON.parse(message.body)
        importing.value = false
        importProgress.value = 100
        
        if (response.success) {
          successMessage.value = response.message || 'Data imported successfully'
          importStatus.value = 'Import completed successfully'
          
          if (response.requiresRestart) {
            successMessage.value += ' Container restart is recommended.'
          }
          
          // Clear the selected file
          clearSelection()
        } else {
          errorMessage.value = response.message || 'Import failed'
          importStatus.value = 'Import failed'
        }
      } catch (error) {
        console.error('Error handling import response:', error)
        errorMessage.value = 'Error processing import response'
        importing.value = false
      }
    }
    
    const handleExportProgress = (message) => {
      try {
        const progress = JSON.parse(message.body)
        exportStatus.value = progress.message || 'Exporting...'
        // Simple progress simulation since we don't have real progress from backend
        if (exportProgress.value < 90) {
          exportProgress.value += 10
        }
      } catch (error) {
        console.error('Error handling export progress:', error)
      }
    }
    
    const handleImportProgress = (message) => {
      try {
        const progress = JSON.parse(message.body)
        importStatus.value = progress.message || 'Importing...'
        // Simple progress simulation
        if (importProgress.value < 90) {
          importProgress.value += 15
        }
      } catch (error) {
        console.error('Error handling import progress:', error)
      }
    }
    
    onMounted(() => {
      if (isConnected.value && stompClient.value) {
        const sessionId = stompClient.value.ws._websocket?.extensions?.sessionId || 
                          Math.random().toString(36).substr(2, 9)
        
        exportSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/export-user${sessionId}`,
          handleExportResponse
        )
        
        importSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/import-user${sessionId}`,
          handleImportResponse
        )
        
        exportProgressSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/export-progress-user${sessionId}`,
          handleExportProgress
        )
        
        importProgressSubscription = stompClient.value.subscribe(
          `/queue/sillytavern/import-progress-user${sessionId}`,
          handleImportProgress
        )
      }
    })
    
    onUnmounted(() => {
      if (exportSubscription) exportSubscription.unsubscribe()
      if (importSubscription) importSubscription.unsubscribe()
      if (exportProgressSubscription) exportProgressSubscription.unsubscribe()
      if (importProgressSubscription) importProgressSubscription.unsubscribe()
    })
    
    return {
      exporting,
      importing,
      selectedFile,
      uploadProgress,
      uploadError,
      exportProgress,
      exportStatus,
      exportResult,
      importProgress,
      importStatus,
      successMessage,
      errorMessage,
      fileInput,
      formatFileSize,
      formatDate,
      handleFileSelect,
      clearSelection,
      startExport,
      startImport
    }
  }
}
</script>

<style scoped>
.data-manager {
  max-width: 800px;
  margin: 0 auto;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.card-header {
  background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
  color: white;
  border-bottom: none;
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
}

.export-section,
.import-section {
  padding: 1.5rem;
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.progress {
  height: 8px;
  border-radius: 4px;
  background-color: #e9ecef;
}

.progress-bar {
  border-radius: 4px;
}

.form-control:focus {
  border-color: #28a745;
  box-shadow: 0 0 0 0.2rem rgba(40, 167, 69, 0.25);
}

.btn-primary {
  background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
  border: none;
  padding: 0.6rem 1.5rem;
  font-weight: 500;
}

.btn-warning {
  background: linear-gradient(135deg, #ffc107 0%, #e0a800 100%);
  border: none;
  padding: 0.6rem 1.5rem;
  font-weight: 500;
  color: #212529;
}

.btn-success {
  background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
  border: none;
  padding: 0.4rem 1rem;
  font-weight: 500;
}

.btn:hover {
  transform: translateY(-1px);
}

.alert {
  border: none;
  border-radius: 8px;
}

.alert-info {
  background-color: #cce7ff;
  color: #004085;
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
  color: #28a745 !important;
}

.text-warning {
  color: #856404 !important;
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

.gap-3 {
  gap: 1rem !important;
}
</style>