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
                <span class="file-name">{{ exportResult.fileName || exportResult.filename || '导出文件' }}</span>
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

          <!-- 多步骤进度显示 -->
          <div v-if="importing" class="steps-progress-section" style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
            <!-- 总体进度标题 -->
            <div class="steps-header" style="margin-bottom: 15px; text-align: center;">
              <h4 style="margin: 0; color: #495057; font-size: 16px;">
                数据导入进度：第 {{ currentStep + 1 }} 步 / 共 {{ totalSteps }} 步
              </h4>
              <p style="margin: 5px 0 0 0; color: #6c757d; font-size: 14px;">
                当前：{{ currentStepName }}
              </p>
            </div>

            <!-- 步骤列表 -->
            <div class="steps-list">
              <div
                v-for="(step, index) in importSteps"
                :key="step.id"
                class="step-item"
                :class="{
                  'step-completed': step.status === 'completed',
                  'step-active': step.status === 'active',
                  'step-error': step.status === 'error',
                  'step-pending': step.status === 'pending'
                }"
                style="display: flex; align-items: center; margin-bottom: 10px; padding: 8px 12px; border-radius: 6px;"
              >
                <!-- 步骤图标 -->
                <div class="step-icon" style="width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-right: 12px; font-size: 12px; font-weight: bold;">
                  <i v-if="step.status === 'completed'" class="fas fa-check" style="color: white;"></i>
                  <i v-else-if="step.status === 'active'" class="fas fa-spinner fa-spin" style="color: white;"></i>
                  <i v-else-if="step.status === 'error'" class="fas fa-times" style="color: white;"></i>
                  <span v-else style="color: white;">{{ step.id }}</span>
                </div>

                <!-- 步骤名称 -->
                <div class="step-name" style="flex: 1; font-weight: 500;">
                  {{ step.name }}
                </div>

                <!-- 步骤状态 -->
                <div class="step-status" style="font-size: 12px; margin-left: 8px;">
                  <span v-if="step.status === 'completed'" style="color: #28a745;">✓ 完成</span>
                  <span v-else-if="step.status === 'active'" style="color: #007bff;">进行中...</span>
                  <span v-else-if="step.status === 'error'" style="color: #dc3545;">失败</span>
                  <span v-else style="color: #6c757d;">等待中</span>
                </div>
              </div>
            </div>

            <!-- 当前步骤进度条 -->
            <div v-if="currentStep >= 0 && importSteps[currentStep]?.status === 'active'" class="current-step-progress" style="margin-top: 15px;">
              <div class="progress-bar-container" style="background: #e9ecef; border-radius: 10px; height: 8px; overflow: hidden;">
                <div
                  class="progress-bar"
                  style="background: linear-gradient(90deg, #007bff, #0056b3); height: 100%; transition: width 0.3s ease;"
                  :style="{ width: importSteps[currentStep].progress + '%' }"
                ></div>
              </div>
              <div style="text-align: center; margin-top: 5px; font-size: 12px; color: #6c757d;">
                {{ importSteps[currentStep].progress }}%
              </div>
            </div>
          </div>

          <!-- 原来的进度条保持作为备用（隐藏） -->
          <div v-if="false" class="progress-section" style="background: #e3f2fd; border: 2px solid #2196f3; border-radius: 8px; padding: 15px;">
            <div class="progress-header" style="margin-bottom: 10px;">
              <span class="progress-label" style="font-weight: bold; color: #1976d2;">
                {{ importStatus || (uploadProgress < 100 ? '正在上传文件...' : '正在处理数据...') }}
              </span>
              <span class="progress-percentage" style="font-weight: bold; color: #1976d2;">
                {{ Math.round(uploadProgress < 100 ? uploadProgress : importProgress) }}%
              </span>
            </div>
            <div class="progress-bar-container" style="background: #bbdefb; border-radius: 10px; height: 20px; overflow: hidden;">
              <div
                class="progress-bar"
                style="background: linear-gradient(90deg, #2196f3, #1976d2); height: 100%; transition: width 0.3s ease;"
                :style="{ width: (uploadProgress < 100 ? uploadProgress : importProgress) + '%' }"
              ></div>
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: #666;">
              阶段: {{ uploadProgress < 100 ? '文件上传' : '数据导入' }}
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
import useConnectionManager from '@/composables/useConnectionManager'
import { StreamingFileService } from '@/services/streamingFile.js'

export default {
  name: 'DataManager',
  setup() {
    const connectionManager = useConnectionManager()

    const isConnected = computed(() => connectionManager.connectionState?.isConnected ?? false)
    const stompClient = computed(() => connectionManager.getStompClient())

    // Initialize StreamingFileService with session provider
    const streamingFileService = new StreamingFileService(() => {
      const client = stompClient.value

      console.log('🔍 获取真实session ID:')

      // 方法1: 从connectionManager获取（最可靠）
      const managedSessionId = connectionManager.connectionState?.currentSessionId
      console.log('- connectionManager sessionId:', managedSessionId)

      if (managedSessionId && managedSessionId !== 'default' && !managedSessionId.startsWith('invalid_')) {
        console.log('✅ 使用connectionManager的真实sessionId:', managedSessionId)
        return managedSessionId
      }

      // 方法2: 从STOMP客户端直接获取
      console.log('- client存在:', !!client)
      if (client) {
        console.log('- client.ws存在:', !!client.ws)
        if (client.ws) {
          console.log('- client.ws._websocket存在:', !!client.ws._websocket)
          console.log('- client.ws._transport存在:', !!client.ws._transport)

          // 尝试从_websocket获取
          if (client.ws._websocket) {
            const wsUrl = client.ws._websocket.url || ''
            console.log('- WebSocket URL:', wsUrl)

            const sockJSMatch = wsUrl.match(/\/ws\/[^/]+\/([^/]+)\/websocket/)
            console.log('- URL匹配结果:', sockJSMatch)

            if (sockJSMatch && sockJSMatch[1] && sockJSMatch[1] !== 'websocket') {
              console.log('✅ 从WebSocket URL提取sessionId:', sockJSMatch[1])
              return sockJSMatch[1]
            }
          }

          // 尝试从_transport获取
          if (client.ws._transport && client.ws._transport.url) {
            const transportUrl = client.ws._transport.url || ''
            console.log('- Transport URL:', transportUrl)

            const transportMatch = transportUrl.match(/\/ws\/[^/]+\/([^/]+)\/websocket/)
            console.log('- Transport URL匹配结果:', transportMatch)

            if (transportMatch && transportMatch[1] && transportMatch[1] !== 'websocket') {
              console.log('✅ 从Transport URL提取sessionId:', transportMatch[1])
              return transportMatch[1]
            }
          }
        }
      }

      // 方法3: 从连接头获取
      if (client?.connectedHeaders) {
        const headerSessionId = client.connectedHeaders.session ||
                               client.connectedHeaders['session-id'] ||
                               client.connectedHeaders.sessionId
        if (headerSessionId && headerSessionId !== 'default') {
          console.log('✅ 从connectedHeaders获取sessionId:', headerSessionId)
          return headerSessionId
        }
      }

      // 如果真的获取不到，这表明STOMP连接有问题
      console.error('🚨 无法获取真实session ID，STOMP连接可能有问题')
      console.log('- 当前连接状态:', connectionManager.connectionState)
      console.log('- STOMP客户端状态:', client)

      throw new Error('无法验证用户会话，请重新连接SSH后再试')
    })

    const exporting = ref(false)
    const importing = ref(false)
    const selectedFile = ref(null)
    const uploadProgress = ref(0)
    const uploadError = ref('')

    const exportProgress = ref(0)
    const exportStatus = ref('')
    const exportResult = ref(null)

    // 重新设计的多步骤进度系统
    const importSteps = ref([
      { id: 1, name: '上传文件', status: 'pending', progress: 0 },
      { id: 2, name: '验证文件', status: 'pending', progress: 0 },
      { id: 3, name: '备份数据', status: 'pending', progress: 0 },
      { id: 4, name: '解压文件', status: 'pending', progress: 0 },
      { id: 5, name: '导入数据', status: 'pending', progress: 0 },
      { id: 6, name: '重启容器', status: 'pending', progress: 0 }
    ])
    const currentStep = ref(0) // 当前步骤索引 (0-based)
    const totalSteps = computed(() => importSteps.value.length)
    const currentStepName = computed(() => {
      if (currentStep.value >= 0 && currentStep.value < importSteps.value.length) {
        return importSteps.value[currentStep.value].name
      }
      return ''
    })

    // 更新步骤状态的函数
    const updateStepStatus = (stepIndex, status, progress = 0) => {
      if (stepIndex >= 0 && stepIndex < importSteps.value.length) {
        importSteps.value[stepIndex].status = status
        importSteps.value[stepIndex].progress = progress

        if (status === 'active') {
          currentStep.value = stepIndex
        }
      }
    }

    // 重置所有步骤
    const resetSteps = () => {
      importSteps.value.forEach(step => {
        step.status = 'pending'
        step.progress = 0
      })
      currentStep.value = 0
    }

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
      if (bytes == null || bytes === undefined || isNaN(bytes)) return '0 字节'
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

      // 重置步骤状态
      resetSteps()

      // 启动保活机制以防止长时间操作时连接断开
      console.log('🔄 启动保活机制以维持连接...')
      connectionManager.startKeepAlive()

      console.log('✅ 状态已设置:', {
        importing: importing.value,
        importProgress: importProgress.value,
        uploadProgress: uploadProgress.value,
        importStatus: importStatus.value
      })

      try {
        // 第一阶段：上传文件
        updateStepStatus(0, 'active', 0) // 开始上传文件
        importStatus.value = '📤 正在上传文件到服务器...'
        console.log('阶段1: 开始上传文件')

        const uploadedFileName = await uploadFile(selectedFile.value)
        console.log('阶段1: 文件上传完成，文件名:', uploadedFileName)
        updateStepStatus(0, 'completed', 100) // 上传文件完成

        // 第二阶段：开始导入流程
        updateStepStatus(1, 'active', 0) // 开始验证文件
        importStatus.value = '📨 正在发送导入请求...'
        // 不要重置上传进度，保持显示
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
        // 停止保活机制
        connectionManager.stopKeepAlive()
        // 设置当前步骤为错误状态
        if (currentStep.value >= 0 && currentStep.value < importSteps.value.length) {
          updateStepStatus(currentStep.value, 'error', 0)
        }
        importing.value = false
        uploadProgress.value = 0
        importProgress.value = 0
        importStatus.value = ''
        errorMessage.value = '导入失败：' + error.message
      }
    }

    const uploadFile = (file) => {
      return new Promise(async (resolve, reject) => {
        console.log('开始使用StreamingFileService上传文件:', file.name, `(${formatFileSize(file.size)})`)

        try {
          // const remotePath = `/tmp/${file.name}`
          const remotePath = `/tmp/`

          console.log('🔍 开始上传文件诊断信息:')
          console.log('- 文件名:', file.name)
          console.log('- 文件大小:', file.size, 'bytes (', formatFileSize(file.size), ')')
          console.log('- 文件类型:', file.type)
          console.log('- 最后修改时间:', new Date(file.lastModified).toISOString())

          const uploadId = await streamingFileService.streamUploadFile(
            file,
            remotePath,
            // onProgress callback
            (progressData) => {
              uploadProgress.value = progressData.percentage || 0
              updateStepStatus(0, 'active', progressData.percentage || 0) // 更新第一步进度
              importStatus.value = `📤 正在上传文件... ${Math.round(progressData.percentage || 0)}%`

              if (progressData.speed > 0) {
                const speedText = streamingFileService.formatSpeed(progressData.speed)
                importStatus.value += ` (${speedText})`
              }

              console.log(`⬆️ 上传进度: ${Math.round(progressData.percentage || 0)}%`)
            },
            // onComplete callback
            (completionData) => {
              console.log('文件上传完成:', completionData)
              uploadProgress.value = 100
              importStatus.value = '文件上传完成，开始导入...'
            },
            // onError callback
            (error) => {
              console.error('上传过程中出错:', error)
              reject(error)
            }
          )

          console.log('StreamingFileService上传完成，uploadId:', uploadId)
          // 返回文件名，供导入流程使用
          resolve(file.name)

        } catch (error) {
          console.error('StreamingFileService上传失败:', error)
          reject(error)
        }
      })
    }

    const handleExportResponse = (message) => {
      try {
        // 注意：导出响应的下载处理由useSillyTavern.js负责
        // 这里只处理UI状态更新，不触发下载
        const response = JSON.parse(message.body)
        exporting.value = false
        exportProgress.value = 100

        if (response.success) {
          exportResult.value = response.payload
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
          console.log('导入成功!')
          // 停止保活机制
          connectionManager.stopKeepAlive()

          // 确保所有步骤都标记为完成
          importSteps.value.forEach((step, index) => {
            if (step.status !== 'completed') {
              updateStepStatus(index, 'completed', 100)
            }
          })

          importProgress.value = 100
          importStatus.value = '导入完成'
          successMessage.value = response.message || '数据导入成功'

          if (response.requiresRestart) {
            successMessage.value += ' 服务已重启完成。'
          }

          console.log('导入成功完成')

          // 清除选中的文件
          clearSelection()
        } else {
          console.error('导入失败:', response.message)
          // 停止保活机制
          connectionManager.stopKeepAlive()

          // 设置当前步骤为错误状态
          if (currentStep.value >= 0 && currentStep.value < importSteps.value.length) {
            updateStepStatus(currentStep.value, 'error', 0)
          }

          importStatus.value = '导入失败'
          errorMessage.value = response.message || '数据导入失败'

          if (response.error) {
            errorMessage.value += ': ' + response.error
          }

          console.error('导入失败:', response)
        }
      } catch (error) {
        console.error('处理导入响应时出错:', error)
        // 停止保活机制
        connectionManager.stopKeepAlive()
        // 设置当前步骤为错误状态
        if (currentStep.value >= 0 && currentStep.value < importSteps.value.length) {
          updateStepStatus(currentStep.value, 'error', 0)
        }
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

        // 根据消息内容更新对应步骤状态
        if (progress.message) {
          const msg = progress.message.toLowerCase()

          if (msg.includes('验证') || msg.includes('verify')) {
            // 步骤2：验证文件
            updateStepStatus(1, 'active', 50)
            importProgress.value = 20
          } else if (msg.includes('备份') || msg.includes('backup')) {
            // 完成验证，开始备份
            updateStepStatus(1, 'completed', 100)
            updateStepStatus(2, 'active', 30)
            importProgress.value = 40
          } else if (msg.includes('解压') || msg.includes('extract')) {
            // 完成备份，开始解压
            updateStepStatus(2, 'completed', 100)
            updateStepStatus(3, 'active', 20)
            importProgress.value = 60
          } else if (msg.includes('导入') || msg.includes('import') || msg.includes('拷贝') || msg.includes('copy')) {
            // 完成解压，开始导入数据
            updateStepStatus(3, 'completed', 100)
            updateStepStatus(4, 'active', 40)
            importProgress.value = 80
          } else if (msg.includes('重启') || msg.includes('restart')) {
            // 完成导入，开始重启容器
            updateStepStatus(4, 'completed', 100)
            updateStepStatus(5, 'active', 60)
            importProgress.value = 90
          } else if (msg.includes('完成') || msg.includes('complete')) {
            // 全部完成
            updateStepStatus(5, 'completed', 100)
            importProgress.value = 100
          }
        }

        console.log('导入进度更新:', {
          status: importStatus.value,
          progress: importProgress.value,
          currentStep: currentStep.value + 1,
          totalSteps: totalSteps.value
        })

      } catch (error) {
        console.error('处理导入进度消息时出错:', error)
      }
    }

    onMounted(() => {
      console.log('DataManager 组件已挂载')

      // 设置全局流式上传进度回调，用于接收STOMP进度消息
      if (!window.streamingProgressCallback) {
        console.log('设置全局streamingProgressCallback')
        window.streamingProgressCallback = (progressData) => {
          console.log('DataManager收到流式上传进度:', progressData)
          if (importing.value && uploadProgress.value < 100) {
            uploadProgress.value = progressData.percentage || 0
            updateStepStatus(0, 'active', progressData.percentage || 0) // 更新第一步进度
            if (progressData.speed > 0) {
              const speedText = streamingFileService.formatSpeed(progressData.speed)
              importStatus.value = `📤 正在上传文件... ${Math.round(progressData.percentage || 0)}% (${speedText})`
            } else {
              importStatus.value = `📤 正在上传文件... ${Math.round(progressData.percentage || 0)}%`
            }
          }
        }
      }

      if (isConnected.value && stompClient.value) {
        try {
          // 使用正确的方式获取真实sessionId
          const realSessionId = connectionManager.connectionState?.currentSessionId

          if (!realSessionId || realSessionId === 'default' || realSessionId.startsWith('invalid_')) {
            console.error('无法获取有效的session ID，当前值:', realSessionId)
            console.error('STOMP订阅设置失败 - 需要有效的session ID')
            return
          }

          console.log('✅ 使用真实sessionId设置STOMP订阅:', realSessionId)

          exportSubscription = stompClient.value.subscribe(
            `/queue/sillytavern/export-user${realSessionId}`,
            handleExportResponse
          )

          importSubscription = stompClient.value.subscribe(
            `/queue/sillytavern/import-user${realSessionId}`,
            handleImportResponse
          )

          exportProgressSubscription = stompClient.value.subscribe(
            `/queue/sillytavern/export-progress-user${realSessionId}`,
            handleExportProgress
          )

          importProgressSubscription = stompClient.value.subscribe(
            `/queue/sillytavern/import-progress-user${realSessionId}`,
            handleImportProgress
          )

          console.log('✅ STOMP订阅设置完成，订阅队列:', {
            export: `/queue/sillytavern/export-user${realSessionId}`,
            import: `/queue/sillytavern/import-user${realSessionId}`,
            exportProgress: `/queue/sillytavern/export-progress-user${realSessionId}`,
            importProgress: `/queue/sillytavern/import-progress-user${realSessionId}`
          })
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
      // 停止保活机制
      connectionManager.stopKeepAlive()

      if (exportSubscription) exportSubscription.unsubscribe()
      if (importSubscription) importSubscription.unsubscribe()
      if (exportProgressSubscription) exportProgressSubscription.unsubscribe()
      if (importProgressSubscription) importProgressSubscription.unsubscribe()

      // 清理全局回调
      if (window.streamingProgressCallback) {
        console.log('清理全局streamingProgressCallback')
        window.streamingProgressCallback = null
      }
    })

    const getDownloadUrl = computed(() => {
      if (!exportResult.value || !exportResult.value.downloadUrl) {
        return '#'
      }

      // 使用真实的session ID
      const realSessionId = connectionManager.connectionState?.currentSessionId

      if (!realSessionId || realSessionId === 'default' || realSessionId.startsWith('invalid_')) {
        console.warn('无法获取有效的session ID用于下载URL，当前值:', realSessionId)
        return '#'
      }

      console.log('✅ 使用真实sessionId构建下载URL:', realSessionId)

      const separator = exportResult.value.downloadUrl.includes('?') ? '&' : '?';

      // 生产环境自动适配：如果是相对路径则使用当前域名
      const baseUrl = exportResult.value.downloadUrl;
      const backendUrl = baseUrl.startsWith('/')
        ? (import.meta.env.PROD 
            ? baseUrl // 生产环境使用相对路径，由反向代理处理
            : `${window.location.protocol}//${window.location.hostname}:8100${baseUrl}`) // 开发环境直连8100端口
        : baseUrl;
      const fullUrl = `${backendUrl}${separator}sessionId=${encodeURIComponent(realSessionId)}`;

      console.log('DataManager构建下载URL详细信息:', {
        baseUrl: exportResult.value.downloadUrl,
        backendUrl: backendUrl,
        sessionId: realSessionId,
        fullUrl: fullUrl
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
      startImport,
      // 多步骤进度相关
      importSteps,
      currentStep,
      totalSteps,
      currentStepName,
      updateStepStatus,
      resetSteps
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

.progress-details {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
  text-align: center;
}

.download-btn:hover {
  background: #0056b3;
  text-decoration: none;
  color: white;
}

/* 多步骤进度样式 */
.step-item.step-pending {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
}

.step-item.step-pending .step-icon {
  background: #6c757d;
}

.step-item.step-active {
  background: #e3f2fd;
  border: 1px solid #2196f3;
}

.step-item.step-active .step-icon {
  background: #2196f3;
}

.step-item.step-completed {
  background: #d4edda;
  border: 1px solid #c3e6cb;
}

.step-item.step-completed .step-icon {
  background: #28a745;
}

.step-item.step-error {
  background: #f8d7da;
  border: 1px solid #f5c6cb;
}

.step-item.step-error .step-icon {
  background: #dc3545;
}
</style>
