<template>
  <div class="data-manager">
    <div class="cards-container">
      <!-- 导出数据卡片 -->
      <div class="data-card export-card">
        <div class="card-header">
          <div class="header-icon export-icon">
            <i class="fas fa-download"></i>
          </div>
          <div class="header-content">
            <h3 class="card-title">导出数据</h3>
            <p class="card-subtitle">备份您的 SillyTavern 数据</p>
          </div>
        </div>

        <div class="card-content">
          <!-- 导出按钮 -->
          <div class="action-section">
            <button
              class="action-btn export-btn"
              @click="startExport"
              :disabled="exporting || importing"
            >
              <i class="fas fa-download"></i>
              {{ exporting ? '正在导出...' : '开始导出' }}
            </button>
          </div>

          <!-- 导出进度 -->
          <div v-if="exportProgress > 0" class="progress-section">
            <div class="progress-header">
              <span class="progress-label">{{ exportStatus || '正在导出...' }}</span>
              <span class="progress-percentage">{{ Math.round(exportProgress) }}%</span>
            </div>
            <div class="progress-bar-container">
              <div 
                class="progress-bar export-progress"
                :style="{ width: exportProgress + '%' }"
              ></div>
            </div>
          </div>

          <!-- 导出结果 -->
          <div v-if="exportResult && !exporting" class="result-section">
            <div class="success-message">
              <i class="fas fa-check-circle"></i>
              <span>数据导出完成</span>
            </div>
            <div class="download-info">
              <div class="file-info">
                <span class="file-name">{{ exportResult.fileName }}</span>
                <span class="file-size">{{ formatFileSize(exportResult.sizeBytes) }}</span>
              </div>
              <a 
                :href="getDownloadUrl" 
                class="download-btn"
                download
              >
                <i class="fas fa-download"></i>
                下载文件
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- 导入数据卡片 -->
      <div class="data-card import-card">
        <div class="card-header">
          <div class="header-icon import-icon">
            <i class="fas fa-upload"></i>
          </div>
          <div class="header-content">
            <h3 class="card-title">导入数据</h3>
            <p class="card-subtitle">从备份文件恢复数据</p>
          </div>
        </div>

        <div class="card-content">
          <!-- 警告信息 -->
          <div class="warning-section">
            <div class="warning-message">
              <i class="fas fa-exclamation-triangle"></i>
              <strong>重要提醒：</strong>导入操作将完全替换现有的所有数据，请谨慎操作！
            </div>
          </div>

          <!-- 临时调试面板 - 检查进度状态 -->
          <div style="background: #ff5722; color: white; padding: 15px; margin: 10px 0; border-radius: 5px; font-weight: bold;">
            <div>🔧 进度调试状态:</div>
            <div>importing: {{ importing }}</div>
            <div>uploadProgress: {{ uploadProgress }}</div>
            <div>importProgress: {{ importProgress }}</div>
            <div>importStatus: {{ importStatus }}</div>
            <div>uploading条件: {{ uploadProgress > 0 && uploadProgress < 100 && importing }}</div>
            <div>importing条件: {{ importProgress > 0 && importing }}</div>
          </div>

          <!-- 文件选择区域 -->
          <div class="file-upload-section">
            <div class="upload-area" :class="{ 'has-file': selectedFile, 'disabled': importing || exporting }">
              <input
                type="file"
                id="importFile"
                ref="fileInput"
                @change="handleFileSelect"
                accept=".zip,.tar.gz,.tgz,application/zip,application/x-compressed,application/x-zip-compressed,application/gzip,application/x-gzip,application/x-tar"
                :disabled="importing || exporting"
                class="file-input"
              >
              <label for="importFile" class="upload-label">
                <div class="upload-icon">
                  <i class="fas fa-cloud-upload-alt"></i>
                </div>
                <div class="upload-text">
                  <span v-if="!selectedFile" class="upload-title">选择数据备份文件</span>
                  <span v-else class="upload-title">已选择文件</span>
                  <span class="upload-subtitle">支持 ZIP、TAR.GZ 格式，最大 5GB</span>
                </div>
              </label>
            </div>
            
            <div v-if="uploadError" class="error-message">
              <i class="fas fa-exclamation-circle"></i>
              {{ uploadError }}
            </div>
          </div>

          <!-- 已选择文件信息 -->
          <div v-if="selectedFile" class="selected-file-info">
            <div class="file-icon">
              <i class="fas fa-file-archive"></i>
            </div>
            <div class="file-details">
              <div class="file-name">{{ selectedFile.name }}</div>
              <div class="file-size">{{ formatFileSize(selectedFile.size) }}</div>
            </div>
            <button 
              class="remove-file-btn"
              @click="clearSelection"
              :disabled="importing || exporting || uploadProgress > 0"
            >
              <i class="fas fa-times"></i>
            </button>
          </div>

          <!-- 强制显示的上传进度条 -->
          <div v-if="importing && (uploadProgress > 0 || importProgress > 0)" class="progress-section" style="background: #e3f2fd; border: 2px solid #2196f3; border-radius: 8px; padding: 15px;">
            <div class="progress-header" style="margin-bottom: 10px;">
              <span class="progress-label" style="font-weight: bold; color: #1976d2;">
                {{ importStatus || (uploadProgress < 100 ? '正在上传文件...' : '正在处理数据...') }}
              </span>
              <span class="progress-percentage" style="font-weight: bold; color: #1976d2;">
                {{ Math.round(uploadProgress > 0 ? uploadProgress : importProgress) }}%
              </span>
            </div>
            <div class="progress-bar-container" style="background: #bbdefb; border-radius: 10px; height: 20px; overflow: hidden;">
              <div 
                class="progress-bar"
                style="background: linear-gradient(90deg, #2196f3, #1976d2); height: 100%; transition: width 0.3s ease;"
                :style="{ width: (uploadProgress > 0 ? uploadProgress : importProgress) + '%' }"
              ></div>
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: #666;">
              阶段: {{ uploadProgress > 0 && uploadProgress < 100 ? '文件上传' : '数据导入' }}
            </div>
          </div>
          
          <div class="action-section">
            <button
              class="action-btn import-btn"
              @click="startImport"
              :disabled="!selectedFile || importing || exporting"
            >
              <i class="fas fa-upload"></i>
              {{ importing ? '正在导入...' : '开始导入' }}
            </button>
          </div>

          <!-- 成功消息 -->
          <div v-if="successMessage && !importing" class="success-message">
            <i class="fas fa-check-circle"></i>
            {{ successMessage }}
          </div>

          <!-- 错误消息 -->
          <div v-if="errorMessage && !importing" class="error-message">
            <i class="fas fa-exclamation-circle"></i>
            {{ errorMessage }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useSillyTavern } from '@/composables/useSillyTavern'
import useConnectionManager from '@/composables/useConnectionManager'

export default {
  name: 'DataManager',
  setup() {
    const connectionManager = useConnectionManager()
    
    const isConnected = computed(() => connectionManager.connectionState?.isConnected ?? false)
    const stompClient = computed(() => connectionManager.getStompClient())
    
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
      if (bytes === 0) return '0 字节'
      const k = 1024
      const sizes = ['字节', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }
    
    const formatDate = (dateString) => {
      return new Date(dateString).toLocaleString('zh-CN')
    }
    
    const handleFileSelect = (event) => {
      const file = event.target.files[0]
      uploadError.value = ''
      
      if (!file) {
        selectedFile.value = null
        return
      }
      
      // 验证文件类型
      const fileName = file.name.toLowerCase();
      const isValidFormat = fileName.endsWith('.zip') || 
                           fileName.endsWith('.tar.gz') || 
                           fileName.endsWith('.tgz');
      
      if (!isValidFormat) {
        uploadError.value = '仅支持 ZIP、TAR.GZ、TGZ 格式文件'
        event.target.value = ''
        return
      }
      
      // 验证文件大小
      if (file.size > MAX_FILE_SIZE) {
        uploadError.value = `文件过大，最大支持 ${formatFileSize(MAX_FILE_SIZE)}`
        event.target.value = ''
        return
      }
      
      selectedFile.value = file
      console.log('文件选择成功:', file.name, `(${formatFileSize(file.size)})`)
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
        errorMessage.value = 'WebSocket 连接未建立'
        return
      }
      
      exporting.value = true
      exportProgress.value = 0
      exportStatus.value = '开始导出...'
      exportResult.value = null
      
      const client = stompClient.value
      client.publish({
        destination: '/app/sillytavern/export-data',
        body: JSON.stringify({})
      })
    }
    
    const startImport = async () => {
      if (!selectedFile.value) {
        uploadError.value = '请选择要导入的文件'
        return
      }
      
      if (!isConnected.value) {
        errorMessage.value = 'WebSocket 连接未建立'
        return
      }
      
      console.log('🚀 开始数据导入流程...')
      
      // 立即设置初始状态，确保UI能响应
      importing.value = true
      importProgress.value = 1  // 设置为1确保进度条显示
      uploadProgress.value = 1  // 设置为1确保进度条显示  
      importStatus.value = '🔄 初始化导入流程...'
      successMessage.value = ''
      errorMessage.value = ''
      
      console.log('✅ 状态已设置:', {
        importing: importing.value,
        importProgress: importProgress.value,
        uploadProgress: uploadProgress.value,
        importStatus: importStatus.value
      })
      
      try {
        // 第一阶段：上传文件
        importStatus.value = '📤 正在上传文件到服务器...'
        console.log('阶段1: 开始上传文件')
        
        const uploadedFileName = await uploadFile(selectedFile.value)
        console.log('阶段1: 文件上传完成，文件名:', uploadedFileName)
        
        // 第二阶段：开始导入流程
        importStatus.value = '📨 正在发送导入请求...'
        uploadProgress.value = 0  // 重置上传进度
        importProgress.value = 5   // 开始导入进度
        console.log('阶段2: 开始导入流程')
        
        const client = stompClient.value
        if (!client) {
          throw new Error('STOMP 客户端不可用')
        }
        
        console.log('发送导入请求到STOMP:', {
          destination: '/app/sillytavern/import-data',
          uploadedFileName: uploadedFileName
        })
        
        client.publish({
          destination: '/app/sillytavern/import-data',
          body: JSON.stringify({
            uploadedFileName: uploadedFileName
          })
        })
        
        importStatus.value = '⏳ 已发送导入请求，等待服务器响应...'
        importProgress.value = 10
        console.log('阶段2: 导入请求已发送，等待后端处理')
        
      } catch (error) {
        console.error('❌ 导入流程失败:', error)
        importing.value = false
        uploadProgress.value = 0
        importProgress.value = 0
        importStatus.value = ''
        errorMessage.value = '导入失败：' + error.message
      }
    }
    
    const uploadFile = (file) => {
      return new Promise((resolve, reject) => {
        console.log('开始上传文件:', file.name, `(${formatFileSize(file.size)})`)
        
        // 安全获取会话ID
        const client = stompClient.value;
        let sessionId = '';
        
        try {
          if (client?.ws?._websocket?.extensions?.sessionId) {
            sessionId = client.ws._websocket.extensions.sessionId;
          }
        } catch (e) {
          console.warn('无法获取WebSocket sessionId:', e.message);
        }
        
        if (!sessionId) {
          sessionId = Math.random().toString(36).substr(2, 9);
          console.log('使用随机sessionId:', sessionId);
        }
        
        // 使用现有的流式上传端点
        const remotePath = `/tmp/${file.name}`;
        const uploadUrl = `/api/streaming/upload?sessionId=${encodeURIComponent(sessionId)}&remotePath=${encodeURIComponent(remotePath)}&filename=${encodeURIComponent(file.name)}`;
        
        console.log('上传URL:', uploadUrl);
        
        const xhr = new XMLHttpRequest();
        
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            const progress = (event.loaded / event.total) * 100;
            uploadProgress.value = progress;
            importStatus.value = `📤 正在上传文件... ${Math.round(progress)}%`;
            console.log(`⬆️ 上传进度: ${Math.round(progress)}%`);
          }
        });
        
        xhr.addEventListener('load', () => {
          console.log('上传请求完成，状态码:', xhr.status);
          console.log('响应内容:', xhr.responseText);
          
          if (xhr.status === 200) {
            try {
              const response = JSON.parse(xhr.responseText);
              uploadProgress.value = 100;
              importStatus.value = '文件上传完成，开始导入...';
              console.log('文件上传成功');
              // 返回文件名，供导入流程使用
              resolve(file.name);
            } catch (e) {
              console.error('解析响应失败:', e);
              reject(new Error('响应格式无效: ' + e.message));
            }
          } else {
            const error = `上传失败，状态码: ${xhr.status}, 响应: ${xhr.responseText}`;
            console.error(error);
            reject(new Error(error));
          }
        });
        
        xhr.addEventListener('error', (event) => {
          const error = '上传失败：网络错误';
          console.error(error, event);
          reject(new Error(error));
        });
        
        xhr.addEventListener('timeout', () => {
          const error = '上传超时';
          console.error(error);
          reject(new Error(error));
        });
        
        // 设置超时时间为10分钟
        xhr.timeout = 10 * 60 * 1000;
        
        xhr.open('POST', uploadUrl);
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        
        console.log('开始发送文件数据...');
        xhr.send(file);
      });
    }
    
    const handleExportResponse = (message) => {
      try {
        // 注意：导出响应的下载处理由useSillyTavern.js负责
        // 这里只处理UI状态更新，不触发下载
        const response = JSON.parse(message.body)
        exporting.value = false
        exportProgress.value = 100
        
        if (response.success) {
          exportResult.value = response
          exportStatus.value = 'Export completed successfully'
        } else {
          errorMessage.value = response.message || 'Export failed'
          exportStatus.value = 'Export failed'
        }
      } catch (error) {
        console.error('Error handling export response:', error)
        errorMessage.value = 'Error processing export response'
        exporting.value = false
      }
    }
    
    const handleImportResponse = (message) => {
      try {
        console.log('收到导入响应消息:', message.body)
        const response = JSON.parse(message.body)
        
        importing.value = false
        
        if (response.success) {
          importProgress.value = 100
          importStatus.value = '导入完成'
          successMessage.value = response.message || '数据导入成功'
          
          if (response.requiresRestart) {
            successMessage.value += ' 建议重启容器以应用更改。'
          }
          
          console.log('导入成功完成')
          
          // 清除选中的文件
          clearSelection()
        } else {
          importStatus.value = '导入失败'
          errorMessage.value = response.message || '数据导入失败'
          
          if (response.error) {
            errorMessage.value += ': ' + response.error
          }
          
          console.error('导入失败:', response)
        }
      } catch (error) {
        console.error('处理导入响应时出错:', error)
        errorMessage.value = '处理导入响应时出错: ' + error.message
        importing.value = false
        importStatus.value = '导入失败'
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
        console.log('收到导入进度消息:', message.body)
        const progress = JSON.parse(message.body)
        
        // 更新状态信息
        if (progress.message) {
          importStatus.value = progress.message
        }
        
        // 根据消息内容设置合适的进度值
        if (progress.message) {
          const msg = progress.message.toLowerCase()
          
          if (msg.includes('下载') || msg.includes('download')) {
            importProgress.value = 10
          } else if (msg.includes('验证') || msg.includes('verify')) {
            importProgress.value = 20
          } else if (msg.includes('上传') || msg.includes('upload')) {
            importProgress.value = 30
          } else if (msg.includes('docker-compose') || msg.includes('路径')) {
            importProgress.value = 40
          } else if (msg.includes('备份') || msg.includes('backup')) {
            importProgress.value = 50
          } else if (msg.includes('解压') || msg.includes('extract')) {
            importProgress.value = 60
          } else if (msg.includes('导入') || msg.includes('import')) {
            importProgress.value = 70
          } else if (msg.includes('拷贝') || msg.includes('copy')) {
            importProgress.value = 80
          } else if (msg.includes('重启') || msg.includes('restart')) {
            importProgress.value = 90
          } else if (msg.includes('完成') || msg.includes('complete')) {
            importProgress.value = 100
          }
        }
        
        console.log('导入进度更新:', {
          status: importStatus.value,
          progress: importProgress.value
        })
        
      } catch (error) {
        console.error('处理导入进度消息时出错:', error)
      }
    }
    
    onMounted(() => {
      console.log('DataManager 组件已挂载')
      
      if (isConnected.value && stompClient.value) {
        try {
          // 安全地获取sessionId
          let sessionId = '';
          
          if (stompClient.value.ws && stompClient.value.ws._websocket && stompClient.value.ws._websocket.extensions) {
            sessionId = stompClient.value.ws._websocket.extensions.sessionId;
          }
          
          if (!sessionId) {
            sessionId = Math.random().toString(36).substr(2, 9);
            console.log('使用随机生成的sessionId:', sessionId);
          } else {
            console.log('使用WebSocket sessionId:', sessionId);
          }
          
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
          
          console.log('STOMP订阅设置完成');
        } catch (error) {
          console.error('设置STOMP订阅时出错:', error);
        }
      }
    })
    
    // 监控 selectedFile 变化
    watch(selectedFile, (newValue, oldValue) => {
      if (newValue !== oldValue) {
        console.log('文件选择状态变更:', newValue ? newValue.name : '未选择')
      }
    }, { immediate: true })
    
    onUnmounted(() => {
      if (exportSubscription) exportSubscription.unsubscribe()
      if (importSubscription) importSubscription.unsubscribe()
      if (exportProgressSubscription) exportProgressSubscription.unsubscribe()
      if (importProgressSubscription) importProgressSubscription.unsubscribe()
    })
    
    const getDownloadUrl = computed(() => {
      if (!exportResult.value || !exportResult.value.downloadUrl) {
        return '#'
      }
      
      const client = stompClient.value
      if (!client) {
        console.warn('STOMP 客户端不可用，无法构建下载URL')
        return '#'
      }
      
      let sessionId = 'fallback_' + Math.random().toString(36).substr(2, 9)
      try {
        if (client.ws && client.ws._websocket && client.ws._websocket.extensions && client.ws._websocket.extensions.sessionId) {
          sessionId = client.ws._websocket.extensions.sessionId
        }
      } catch (e) {
        console.warn('DataManager获取WebSocket sessionId失败:', e.message)
      }
      
      const separator = exportResult.value.downloadUrl.includes('?') ? '&' : '?';
      
      // 确保使用后端API服务器的URL（端口8080），而不是前端开发服务器（端口5173）
      const baseUrl = exportResult.value.downloadUrl;
      const backendUrl = baseUrl.startsWith('/') 
        ? `${window.location.protocol}//${window.location.hostname}:8080${baseUrl}`
        : baseUrl;
      const fullUrl = `${backendUrl}${separator}sessionId=${encodeURIComponent(sessionId)}`;
      
      console.log('DataManager构建手动下载URL详细信息:', {
        baseUrl: exportResult.value.downloadUrl,
        backendUrl: backendUrl,
        sessionId: sessionId,
        fullUrl: fullUrl,
        connectionManagerState: connectionManager.connectionState?.currentSessionId,
        clientInfo: client ? 'STOMP客户端存在' : 'STOMP客户端不存在'
      });
      
      return fullUrl;
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
      getDownloadUrl,
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
  padding: 20px;
}

.cards-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
  max-width: 1200px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .cards-container {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}

.data-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.data-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  align-items: center;
  padding: 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-bottom: 1px solid #e1e5e9;
}

.export-card .card-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.import-card .card-header {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.header-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 20px;
}

.export-card .header-icon {
  background: rgba(255, 255, 255, 0.2);
}

.import-card .header-icon {
  background: rgba(255, 255, 255, 0.2);
}

.header-content h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 600;
}

.header-content p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.card-content {
  padding: 24px;
}

.action-section {
  margin-bottom: 20px;
}

.action-btn {
  width: 100%;
  padding: 12px 20px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.export-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.import-btn {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.action-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.progress-section {
  margin: 20px 0;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.progress-label {
  font-size: 14px;
  color: #495057;
}

.progress-percentage {
  font-size: 14px;
  font-weight: 600;
  color: #495057;
}

.progress-bar-container {
  height: 8px;
  background: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.export-progress {
  background: linear-gradient(90deg, #667eea, #764ba2);
}

.import-progress {
  background: linear-gradient(90deg, #f093fb, #f5576c);
}

.warning-section {
  margin-bottom: 20px;
}

.warning-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff3cd;
  border: 1px solid #ffeaa7;
  border-radius: 8px;
  color: #856404;
  font-size: 14px;
}

.file-upload-section {
  margin-bottom: 20px;
}

.upload-area {
  position: relative;
  border: 2px dashed #dee2e6;
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  transition: all 0.2s ease;
  cursor: pointer;
}

.upload-area:hover:not(.disabled) {
  border-color: #f093fb;
  background: #fdf2fd;
}

.upload-area.has-file {
  border-color: #28a745;
  background: #f8fff8;
}

.upload-area.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.file-input:disabled {
  cursor: not-allowed;
}

.upload-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.upload-icon {
  font-size: 32px;
  color: #6c757d;
}

.upload-title {
  font-size: 16px;
  font-weight: 500;
  color: #495057;
}

.upload-subtitle {
  font-size: 14px;
  color: #6c757d;
}

.selected-file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.file-icon {
  font-size: 24px;
  color: #495057;
}

.file-details {
  flex: 1;
}

.file-name {
  display: block;
  font-weight: 500;
  color: #495057;
  margin-bottom: 2px;
}

.file-size {
  display: block;
  font-size: 14px;
  color: #6c757d;
}

.remove-file-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #dc3545;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
}

.remove-file-btn:hover:not(:disabled) {
  background: #c82333;
}

.remove-file-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.success-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #d4edda;
  border: 1px solid #c3e6cb;
  border-radius: 8px;
  color: #155724;
  font-size: 14px;
  margin-bottom: 20px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  border-radius: 8px;
  color: #721c24;
  font-size: 14px;
  margin-bottom: 20px;
}

.result-section {
  margin: 20px 0;
}

.download-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-top: 12px;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-name {
  font-weight: 500;
  color: #495057;
}

.file-size {
  font-size: 14px;
  color: #6c757d;
}

.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #007bff;
  color: white;
  text-decoration: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s ease;
}

.download-btn:hover {
  background: #0056b3;
  text-decoration: none;
  color: white;
}
</style>