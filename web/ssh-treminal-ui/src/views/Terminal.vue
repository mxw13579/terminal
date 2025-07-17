<template>
  <div class="app-container">
    <transition name="fade" mode="out-in">
      <!-- 连接视图 -->
      <ConnectionForm
          v-if="!isConnected"
          key="connection-view"
          :is-connecting="isConnecting"
          @connect="connect"
      />

      <!-- 工作区视图 -->
      <SshConsole
          v-else
          key="workspace-view"
          :connection-info="{ user, host, port }"
          :sftp-visible="sftpVisible"
          :monitor-visible="monitorVisible"
          @disconnect="disconnect"
          @toggle-sftp="toggleSftpPanel"
          @toggle-monitor="toggleMonitorPanel"
          @terminal-data="sendTerminalData"
          @terminal-resize="sendTerminalResize"
          @terminal-ready="setTerminalInstance"
          @terminal-unmount="setTerminalInstance(null)"
      >
        <!-- 监控面板 -->
        <template #monitor-aside>
          <MonitorPanel
              :is-visible="monitorVisible"
              :is-loading="isConnecting || (monitorVisible && !systemStats)"
              :stats="systemStats"
              :docker-containers="dockerContainers"
          />
        </template>
        <!-- SFTP -->
        <template #aside>
          <SftpPanel
              :is-visible="sftpVisible"
              :is-loading="sftpLoading"
              :error="sftpError"
              :path="currentSftpPath"
              :files="sftpFiles"
              :is-action-in-progress="isSftpActionInProgress"
              :local-upload-progress="localUploadProgress"
              :remote-upload-progress="remoteUploadProgress"
              :upload-status-text="uploadStatusText"
              :upload-speed="uploadSpeed"
              :sftp-upload-speed="sftpUploadSpeed"
              @fetch-list="fetchSftpList"
              @download-files="downloadSftpFiles"
              @upload-file="uploadSftpFile"
          />
        </template>
      </SshConsole>
    </transition>
  </div>
  <Modal v-if="modal.visible" :title="modal.title" :message="modal.message" @close="modal.visible = false" />
</template>

<script setup>
import { ref } from 'vue';
import { useTerminal } from '@/composables/useTerminal.js';
import ConnectionForm from '@/components/ConnectionForm.vue';
import SshConsole from '@/components/SshConsole.vue';
import SftpPanel from '@/components/SftpPanel.vue';
import Modal from '@/components/Modal.vue';
import MonitorPanel from "@/components/MonitorPanel.vue";

// Modal state remains in the component as it's a pure UI concern
const modal = ref({ visible: false, title: '', message: '' });
function showModal(message, title = '提示') {
  modal.value = { visible: true, title, message };
}

// All business logic and state is now handled by the composable!
const {
  host, port, user, isConnected, isConnecting,
  sftpVisible, sftpLoading, sftpError, currentSftpPath, sftpFiles,
  isSftpActionInProgress, localUploadProgress, remoteUploadProgress,
  uploadStatusText, uploadSpeed, sftpUploadSpeed,
  monitorVisible, isMonitoring, systemStats, dockerContainers,
  connect, disconnect, setTerminalInstance,
  sendTerminalData, sendTerminalResize, toggleSftpPanel,
  toggleMonitorPanel,
  fetchSftpList, downloadSftpFiles, uploadSftpFile
} = useTerminal({
  onShowModal: showModal, // Pass the modal function to the composable
});

// Import utility functions directly in SftpPanel or where needed.
// Or you can re-export them from the composable if you prefer.
</script>

<style>
/*
  为了保持原样, 所有样式都放在顶层组件。
  在大型项目中, 建议使用 <style scoped> 并将全局样式提取到单独的CSS文件中。
*/
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
  margin: 0; padding: 0; width: 100%; height: 100%;
  font-family: 'Poppins', sans-serif;
  background-color: var(--bg-color-dark);
  color: #e0e0e0;
  overflow: hidden;
}
.app-container {
  width: 100%; height: 100%; position: relative;
  background-image: radial-gradient(circle at 1% 1%, var(--accent-color-1), transparent 30%),
  radial-gradient(circle at 99% 99%, var(--accent-color-2), transparent 40%);
  display: flex; flex-direction: column;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.4s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.connection-view, .workspace-view {
  width: 100%; height: 100%; flex: 1 1 0;
  display: flex; flex-direction: column;
}
.connection-view { align-items: center; justify-content: center; }
.connection-form-card {
  background: var(--card-bg-color); backdrop-filter: blur(15px);
  border: 1px solid var(--border-color); border-radius: 20px;
  padding: 30px 40px; width: 100%; max-width: 420px;
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
}
.workspace-view { display: flex; flex-direction: column; padding: 15px; box-sizing: border-box; }
.workspace-header { display: flex; justify-content: space-between; align-items: center; padding: 0 10px 15px; flex-shrink: 0; }
.connection-info { display: flex; align-items: center; gap: 10px; font-size: 0.9rem; color: #ccc; background: rgba(0, 0, 0, 0.2); padding: 6px 12px; border-radius: 20px; }
.dot { width: 10px; height: 10px; border-radius: 50%; }
.dot.connected { background-color: #34d399; box-shadow: 0 0 8px #34d399; }
.workspace-controls { display: flex; gap: 10px; }
.workspace-content { display: flex; flex: 1 1 0; min-width: 0; min-height: 0; gap: 15px; overflow: hidden; }
.terminal-container-main {
  flex: 1 1 0; min-width: 0; min-height: 0; width: 100%;
  background: var(--card-bg-color); border: 1px solid var(--border-color);
  border-radius: 12px; overflow: hidden; padding: 10px; box-sizing: border-box; display: flex;
}
.terminal-wrapper { flex: 1; min-width: 0; min-height: 0; }
.sftp-panel {
  width: 0; opacity: 0; transform: translateX(20px);
  transition: width 0.3s ease, opacity 0.3s ease, transform 0.3s ease;
  background: var(--card-bg-color); border: 1px solid var(--border-color);
  border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; flex-shrink: 0;
}
.sftp-panel-visible { width: 320px; opacity: 1; transform: translateX(0); }
.sftp-header { padding: 15px; border-bottom: 1px solid var(--border-color); text-align: center; flex-shrink: 0; }
.sftp-header h4 { margin: 0; font-weight: 500; }
.sftp-body { padding: 15px; overflow-y: auto; flex-grow: 1; display: flex; flex-direction: column; }
.sftp-path {
  font-size: 0.8rem; color: #999; background: rgba(0, 0, 0, 0.2);
  padding: 5px 8px; border-radius: 5px; margin-bottom: 15px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0;
}
.file-list { list-style: none; padding: 0; margin: 0; flex-grow: 1; overflow-y: auto; }
.file-list li { display: flex; align-items: center; gap: 10px; padding: 8px 5px; border-radius: 5px; cursor: pointer; transition: background-color 0.2s; }
.file-list li:hover { background-color: rgba(255, 255, 255, 0.05); }
.file-list li.selected { background-color: var(--selection-bg-color); font-weight: 500; }
.file-list li svg { color: #a5b4fc; flex-shrink: 0; }
.sftp-actions { display: flex; gap: 10px; margin-top: 15px; padding-top: 15px; border-top: 1px solid var(--border-color); flex-shrink: 0; }
.form-header { text-align: center; margin-bottom: 25px; }
.form-header h3 { margin-top: 10px; font-weight: 500; }
.form-icon { color: var(--glow-color); }
.form-fields { display: flex; flex-direction: column; gap: 15px; }
input {
  background: rgba(0, 0, 0, 0.3); border: 1px solid var(--border-color);
  color: #e0e0e0; padding: 12px 15px; border-radius: 10px;
  font-size: 1rem; outline: none; transition: all 0.3s ease; font-family: inherit;
}
input:focus { border-color: var(--glow-color); box-shadow: 0 0 15px 2px rgba(157, 78, 221, 0.4); }
.form-actions { display: flex; justify-content: center; margin-top: 25px; }
.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 20px; font-size: 1rem; font-weight: 500; border-radius: 10px;
  cursor: pointer; border: none; transition: all 0.2s ease; font-family: inherit;
}
.btn-primary { background: linear-gradient(45deg, #6d28d9, #9d4edd); color: white; box-shadow: 0 4px 15px rgba(123, 44, 191, 0.3); }
.btn-primary:hover:not(:disabled) { box-shadow: 0 6px 20px rgba(123, 44, 191, 0.5); transform: translateY(-2px); }
.btn-secondary { background-color: rgba(255, 255, 255, 0.1); color: #ccc; }
.btn-secondary:hover { background-color: rgba(255, 255, 255, 0.15); }
.btn:active:not(:disabled) { transform: translateY(0) scale(0.97); }
.btn:disabled { cursor: not-allowed; opacity: 0.6; }
.btn-icon { background-color: transparent; border: 1px solid var(--border-color); color: #ccc; padding: 8px; }
.btn-icon:hover, .btn-icon.active { background-color: rgba(255, 255, 255, 0.1); border-color: rgba(255, 255, 255, 0.2); color: white; }
.btn-sm { padding: 6px 12px; font-size: 0.9rem; }
.sftp-loader, .sftp-error { text-align: center; padding: 20px; color: #999; }
.sftp-error { color: #fca5a5; background-color: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 8px; word-break: break-word; }
@media (max-width: 768px) {
  .sftp-panel-visible { width: 260px; }
  .connection-form-card { max-width: 90%; padding: 25px; }
  .workspace-view { padding: 10px; }
  .workspace-header { flex-direction: column; gap: 10px; align-items: stretch; }
  .btn { font-size: 0.9rem; padding: 8px 16px; }
}
.sftp-body, .file-list, .terminal-container-main, .terminal-wrapper, .workspace-content { scrollbar-width: thin; scrollbar-color: #6d28d9 rgba(255, 255, 255, 0.08); }
.sftp-body::-webkit-scrollbar, .file-list::-webkit-scrollbar, .terminal-container-main::-webkit-scrollbar, .terminal-wrapper::-webkit-scrollbar, .workspace-content::-webkit-scrollbar { width: 8px; height: 8px; background: rgba(255, 255, 255, 0.04); border-radius: 8px; }
.progress-bar { width: 100%; height: 8px; background: #222; border-radius: 4px; margin-bottom: 4px; overflow: hidden; }
.progress-bar-inner { height: 100%; background: linear-gradient(90deg, #6d28d9, #9d4edd); transition: width 0.2s; }
.sftp-panel.sftp-panel-visible.details-mode { width: 520px !important; min-width: 400px; }
.sftp-list-header { display: flex; align-items: center; font-size: 0.95em; color: #bdbdbd; font-weight: 500; padding: 4px 0 4px 0; border-bottom: 1px solid var(--border-color); margin-bottom: 2px; gap: 10px; }
.upload-progress-container { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.progress-section { overflow: hidden; max-height: 0; opacity: 0; transition: max-height 0.4s ease, opacity 0.4s ease; }
.upload-progress-container .progress-section:first-child { max-height: 100px; opacity: 1; }
.progress-section.visible { max-height: 100px; opacity: 1; }
.progress-label { display: flex; justify-content: space-between; font-size: 0.85em; color: #bbb; margin-bottom: 4px; }
.upload-status-text { font-size: 0.9em; color: #aaa; text-align: center; margin-top: 5px; }
</style>
