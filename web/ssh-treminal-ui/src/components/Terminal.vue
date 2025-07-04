<template>
  <div class="app-container">
    <transition name="fade" mode="out-in" @after-enter="onWorkspaceEntered">
      <!-- 连接视图 -->
      <div v-if="!isConnected" key="connection-view" class="connection-view">
        <div class="connection-form-card">
          <div class="form-header">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                 class="form-icon">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <h3>远程 Shell 连接</h3>
          </div>
          <div class="form-fields">
            <input v-model="host" placeholder="主机 (e.g., 192.168.1.100)" @keyup.enter="connect" autocomplete="off"/>
            <input v-model="port" placeholder="端口 (默认: 22)" type="number" @keyup.enter="connect"/>
            <input v-model="user" placeholder="用户名" @keyup.enter="connect"/>
            <input v-model="password" placeholder="密码" type="password" @keyup.enter="connect"/>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-primary" @click="connect" :disabled="isConnecting">
              <svg v-if="!isConnecting" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                   fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.72"></path>
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.72-1.72"></path>
              </svg>
              <span v-if="!isConnecting">连接</span>
              <span v-if="isConnecting">连接中...</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 工作区视图 -->
      <div v-else key="workspace-view" class="workspace-view">
        <header class="workspace-header">
          <div class="connection-info">
            <span class="dot connected"></span>
            <span>{{ user }}@{{ host }}:{{ port }}</span>
          </div>
          <div class="workspace-controls">
            <button type="button" class="btn btn-icon" :class="{active: sftpVisible}" @click="toggleSftpPanel" title="SFTP 文件传输">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
              </svg>
            </button>
            <button type="button" class="btn btn-secondary" @click="disconnect">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
              <span>断开连接</span>
            </button>
          </div>
        </header>

        <main class="workspace-content">
          <div class="terminal-container-main" ref="terminalContainerRef">
            <div class="terminal-wrapper" ref="terminalRef"></div>
          </div>
          <aside class="sftp-panel" :class="{'sftp-panel-visible': sftpVisible}">
            <div class="sftp-header">
              <h4>SFTP Explorer</h4>
            </div>
            <div class="sftp-body">
              <p class="sftp-path" :title="currentSftpPath">{{ currentSftpPath }}</p>
              <div v-if="sftpLoading" class="sftp-loader">正在加载...</div>
              <div v-if="sftpError" class="sftp-error">{{ sftpError }}</div>
              <ul v-if="!sftpLoading && !sftpError" class="file-list">
                <li v-for="file in sftpFiles"
                    :key="file.path"
                    @click="handleSftpItemClick(file)"
                    :title="file.longname"
                    :class="{ 'selected': selectedSftpFile && selectedSftpFile.path === file.path }">
                  <svg v-if="file.isDirectory" xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                       viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                       stroke-linejoin="round">
                    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
                    <polyline points="13 2 13 9 20 9"></polyline>
                  </svg>
                  <span>{{ file.name }}</span>
                </li>
              </ul>
              <!-- 隐藏的文件输入框 -->
              <input type="file" ref="fileInputRef" style="display: none" @change="handleFileSelected">

              <div class="sftp-actions">
                <!-- Bug修复点 -->
                <button type="button" class="btn btn-sm" @click="triggerUpload" :disabled="isSftpActionInProgress">
                  {{ isSftpActionInProgress ? '处理中...' : '上传' }}
                </button>
                <!-- Bug修复点 -->
                <button type="button" class="btn btn-sm"
                        @click="downloadSelectedFile"
                        :disabled="!selectedSftpFile || selectedSftpFile.isDirectory || isSftpActionInProgress">
                  {{ isSftpActionInProgress ? '处理中...' : '下载' }}
                </button>
              </div>
            </div>
          </aside>
        </main>
      </div>
    </transition>
  </div>
</template>

<script setup>
import {ref, onBeforeUnmount, nextTick, watch} from 'vue';
import {Terminal} from 'xterm';
import {FitAddon} from 'xterm-addon-fit';
import 'xterm/css/xterm.css';

const host = ref('123');
const port = ref(22);
const user = ref('root');
const password = ref('123');
const isConnected = ref(false);
const isConnecting = ref(false);

// --- SFTP State ---
const sftpVisible = ref(false);
const sftpLoading = ref(false);
const sftpError = ref('');
const currentSftpPath = ref('');
const sftpFiles = ref([]);
const selectedSftpFile = ref(null);
const isSftpActionInProgress = ref(false);
const fileInputRef = ref(null);

// --- Terminal State ---
const terminalRef = ref(null);
const terminalContainerRef = ref(null);
let term;
let fitAddon;
let ws;
let resizeObserver;

// --- Watchers and Lifecycle Hooks ---
watch(isConnected, async (newValue) => {
  if (newValue) {
    await nextTick();
    setupResizeObserver();
  }
});
onBeforeUnmount(() => {
  resetState();
});

// --- Terminal Methods ---
const initializeTerminal = () => {
  if (!terminalRef.value || term) return;
  term = new Terminal({
    cursorBlink: true, fontSize: 14, fontFamily: '"Fira Code", Consolas, "Courier New", monospace',
    theme: {
      background: 'rgba(0, 0, 0, 0)',
      foreground: '#d4d4d4', cursor: '#d4d4d4',
      selectionBackground: '#264f78',
    },
    allowTransparency: true,
  });
  fitAddon = new FitAddon();
  term.loadAddon(fitAddon);
  term.open(terminalRef.value);
  if (term.element) {
    term.element.style.width = '100%';
    term.element.style.height = '100%';
  }
  term.onData(data => {
    sendWsMessage({type: 'data', payload: data});
  });
  term.write('✅ 连接成功！欢迎使用 Web Terminal。\r\n');
  term.focus();
};

const setupResizeObserver = () => {
  if (!terminalContainerRef.value) return;
  resizeObserver = new ResizeObserver(() => {
    safeFit();
  });
  resizeObserver.observe(terminalContainerRef.value);
};

const safeFit = () => {
  try {
    if (fitAddon && term?.element) {
      fitAddon.fit();
      const {cols, rows} = term;
      sendWsMessage({type: 'resize', cols, rows});
    }
  } catch (e) { /* ignore */ }
};

const onWorkspaceEntered = () => {
  if (isConnected.value) {
    initializeTerminal();
    if (term) {
      safeFit();
      term.focus();
    }
  }
};

// --- Connection and WebSocket Logic ---
const connect = () => {
  if (!host.value || !user.value || !password.value) {
    alert("请填写所有连接信息！");
    return;
  }
  isConnecting.value = true;

  const queryParams = new URLSearchParams({
    host: host.value,
    port: port.value,
    user: user.value,
    password: password.value,
  });
  // 注意：请将 'localhost:8080' 替换为您后端服务的实际地址和端口
  const wsUrl = `ws://localhost:8080/ws/terminal?${queryParams.toString()}`;
  ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    isConnecting.value = false;
    isConnected.value = true;
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      switch (msg.type) {
        case 'terminal_data':
          if (term) term.write(msg.payload);
          break;
        case 'sftp_list_response':
          sftpLoading.value = false;
          sftpError.value = '';
          currentSftpPath.value = msg.path;
          sftpFiles.value = msg.files;
          break;
        case 'sftp_upload_success':
          isSftpActionInProgress.value = false;
          alert(msg.message || "上传成功!");
          fetchSftpList(msg.path); // 刷新当前目录
          break;
        case 'sftp_download_response':
          handleFileDownload(msg.filename, msg.content);
          break;
        case 'sftp_error':
          sftpLoading.value = false;
          isSftpActionInProgress.value = false;
          sftpError.value = `SFTP Error: ${msg.message}`;
          alert(`SFTP Error: ${msg.message}`); // 同时弹窗提示
          break;
        case 'error':
          isConnecting.value = false;
          alert(`连接时发生错误: ${msg.payload}`);
          resetState();
          break;
        default:
          console.warn('Unknown message type received:', msg.type);
      }
    } catch (e) {
      // 兼容非JSON格式的原始终端数据
      if (term && typeof event.data === 'string') term.write(event.data);
    }
  };

  ws.onclose = (event) => {
    console.warn('WebSocket closed:', event);
    if (term && isConnected.value) {
      term.write('\r\n🔌 连接已断开。\r\n');
    }
    if (event.code !== 1000 && !isConnected.value) {
      alert("连接失败，请检查主机、端口、用户名和密码，并确保后端服务正在运行。");
    }
    resetState();
  };

  ws.onerror = (error) => {
    console.error('💥 WebSocket Error:', error);
    if (!isConnected.value) {
      isConnecting.value = false;
    }
  };
};

const sendWsMessage = (message) => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
};

const disconnect = () => {
  if (ws) {
    ws.close(1000, "User disconnected");
  } else {
    resetState();
  }
};

const resetState = () => {
  if (ws) {
    ws.onopen = ws.onmessage = ws.onclose = ws.onerror = null;
    if (ws.readyState === WebSocket.OPEN) ws.close();
    ws = null;
  }
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (term) {
    term.dispose();
    term = null;
    fitAddon = null;
  }
  isConnected.value = false;
  isConnecting.value = false;
  // Reset SFTP state
  sftpVisible.value = false;
  sftpFiles.value = [];
  currentSftpPath.value = '';
  sftpError.value = '';
  selectedSftpFile.value = null;
  isSftpActionInProgress.value = false;
};

// --- SFTP Feature Methods ---
const toggleSftpPanel = () => {
  sftpVisible.value = !sftpVisible.value;
  if (sftpVisible.value && sftpFiles.value.length === 0) {
    fetchSftpList();
  }
};

const fetchSftpList = (path = '.') => {
  sftpLoading.value = true;
  sftpError.value = '';
  selectedSftpFile.value = null; // 切换目录时清空选择
  sendWsMessage({type: 'sftp_list', path});
};

const handleSftpItemClick = (file) => {
  if (file.isDirectory) {
    fetchSftpList(file.path);
  } else {
    // 单击文件时，选中或取消选中
    if (selectedSftpFile.value && selectedSftpFile.value.path === file.path) {
      selectedSftpFile.value = null;
    } else {
      selectedSftpFile.value = file;
    }
  }
};

const triggerUpload = () => {
  fileInputRef.value?.click();
};

const handleFileSelected = (event) => {

  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    alert("连接已断开，无法上传文件，请重新连接后再试。");
    return;
  }
  const file = event.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (e) => {
    const base64Content = e.target.result.split(',')[1];
    isSftpActionInProgress.value = true;
    sftpError.value = '';
    sendWsMessage({
      type: 'sftp_upload',
      path: currentSftpPath.value,
      filename: file.name,
      content: base64Content
    });
  };
  reader.onerror = (error) => {
    console.error("File reading error:", error);
    alert("读取文件失败！");
  };
  reader.readAsDataURL(file);

  // 重置input的值，以便可以再次选择相同的文件
  event.target.value = '';
};

const downloadSelectedFile = () => {
  if (!selectedSftpFile.value || selectedSftpFile.value.isDirectory) return;

  isSftpActionInProgress.value = true;
  sftpError.value = '';
  sendWsMessage({
    type: 'sftp_download',
    path: selectedSftpFile.value.path
  });
};

const handleFileDownload = (filename, base64Content) => {
  try {
    const byteCharacters = atob(base64Content);
    const byteNumbers = new Array(byteCharacters.length);
    for (let i = 0; i < byteCharacters.length; i++) {
      byteNumbers[i] = byteCharacters.charCodeAt(i);
    }
    const byteArray = new Uint8Array(byteNumbers);
    const blob = new Blob([byteArray]);

    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);

  } catch (error) {
    console.error("Download creation failed:", error);
    alert("创建下载文件失败！");
  } finally {
    isSftpActionInProgress.value = false;
  }
};
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Fira+Code&display=swap');

:root {
  --glow-color: #9d4edd;
  --bg-color-dark: #0c0c14;
  --card-bg-color: rgba(22, 22, 38, 0.65);
  --border-color: rgba(255, 255, 255, 0.1);
  --accent-color-1: #3c3799;
  --accent-color-2: #843b69;
  --selection-bg-color: rgba(99, 102, 241, 0.3);
}

body, html, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  font-family: 'Poppins', sans-serif;
  background-color: var(--bg-color-dark);
  color: #e0e0e0;
  overflow: hidden;
}

.app-container {
  width: 100%;
  height: 100%;
  position: relative;
  background-image: radial-gradient(circle at 1% 1%, var(--accent-color-1), transparent 30%),
  radial-gradient(circle at 99% 99%, var(--accent-color-2), transparent 40%);
  display: flex;
  flex-direction: column;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.4s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.connection-view, .workspace-view {
  width: 100%;
  height: 100%;
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
}

.connection-view {
  display: flex;
  align-items: center;
  justify-content: center;
}

.connection-form-card {
  background: var(--card-bg-color);
  backdrop-filter: blur(15px);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 30px 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
}

.workspace-view {
  display: flex;
  flex-direction: column;
  padding: 15px;
  box-sizing: border-box;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 10px 15px;
  flex-shrink: 0;
}

.connection-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.9rem;
  color: #ccc;
  background: rgba(0, 0, 0, 0.2);
  padding: 6px 12px;
  border-radius: 20px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.connected {
  background-color: #34d399;
  box-shadow: 0 0 8px #34d399;
}

.workspace-controls {
  display: flex;
  gap: 10px;
}


.workspace-content {
  display: flex;
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  gap: 15px;
  overflow: hidden;
}

.terminal-container-main {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  width: 100%;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  padding: 10px;
  box-sizing: border-box;
  display: flex;
}

.terminal-wrapper {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.sftp-panel {
  width: 0;
  opacity: 0;
  transform: translateX(20px);
  transition: width 0.3s ease, opacity 0.3s ease, transform 0.3s ease;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sftp-panel-visible {
  width: 320px;
  opacity: 1;
  transform: translateX(0);
}

.sftp-header {
  padding: 15px;
  border-bottom: 1px solid var(--border-color);
  text-align: center;
  flex-shrink: 0;
}

.sftp-header h4 {
  margin: 0;
  font-weight: 500;
}

.sftp-body {
  padding: 15px;
  overflow-y: auto;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
}

.sftp-path {
  font-size: 0.8rem;
  color: #999;
  background: rgba(0, 0, 0, 0.2);
  padding: 5px 8px;
  border-radius: 5px;
  margin-bottom: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.file-list {
  list-style: none;
  padding: 0;
  margin: 0;
  flex-grow: 1;
  overflow-y: auto;
}

.file-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 5px;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.file-list li:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.file-list li.selected {
  background-color: var(--selection-bg-color);
  font-weight: 500;
}

.file-list li svg {
  color: #a5b4fc;
  flex-shrink: 0;
}

.sftp-actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
}

.form-header {
  text-align: center;
  margin-bottom: 25px;
}

.form-header h3 {
  margin-top: 10px;
  font-weight: 500;
}

.form-icon {
  color: var(--glow-color);
}

.form-fields {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

input {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  color: #e0e0e0;
  padding: 12px 15px;
  border-radius: 10px;
  font-size: 1rem;
  outline: none;
  transition: all 0.3s ease;
  font-family: inherit;
}

input:focus {
  border-color: var(--glow-color);
  box-shadow: 0 0 15px 2px rgba(157, 78, 221, 0.4);
}

.form-actions {
  display: flex;
  justify-content: center;
  margin-top: 25px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 1rem;
  font-weight: 500;
  border-radius: 10px;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
  font-family: inherit;
}

.btn-primary {
  background: linear-gradient(45deg, #6d28d9, #9d4edd);
  color: white;
  box-shadow: 0 4px 15px rgba(123, 44, 191, 0.3);
}

.btn-primary:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(123, 44, 191, 0.5);
  transform: translateY(-2px);
}

.btn-secondary {
  background-color: rgba(255, 255, 255, 0.1);
  color: #ccc;
}

.btn-secondary:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.btn:active:not(:disabled) {
  transform: translateY(0) scale(0.97);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.btn-icon {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: #ccc;
  padding: 8px;
}

.btn-icon:hover, .btn-icon.active {
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: white;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 0.9rem;
}

.sftp-loader, .sftp-error {
  text-align: center;
  padding: 20px;
  color: #999;
}

.sftp-error {
  color: #fca5a5;
  background-color: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  word-break: break-word;
}

@media (max-width: 768px) {
  .sftp-panel-visible {
    width: 260px;
  }

  .connection-form-card {
    max-width: 90%;
    padding: 25px;
  }

  .workspace-view {
    padding: 10px;
  }

  .workspace-header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }

  .btn {
    font-size: 0.9rem;
    padding: 8px 16px;
  }
}
/* ----------- Custom Scrollbar Styles ----------- */
.sftp-body,
.file-list,
.terminal-container-main,
.terminal-wrapper,
.workspace-content {
  scrollbar-width: thin;
  scrollbar-color: #6d28d9 rgba(255,255,255,0.08);
}

/* Chrome, Edge, Safari */
.sftp-body::-webkit-scrollbar,
.file-list::-webkit-scrollbar,
.terminal-container-main::-webkit-scrollbar,
.terminal-wrapper::-webkit-scrollbar,
.workspace-content::-webkit-scrollbar {
  width: 8px;
  height: 8px;
  background: rgba(255,255,255,0.04);
  border-radius: 8px;
}
</style>
