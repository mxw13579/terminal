<!-- /components/SftpPanel.vue -->
<template>
  <aside class="sftp-panel" :class="{'sftp-panel-visible': isVisible, 'details-mode': isVisible && showSftpDetails}">
    <div class="sftp-header">
      <h4 style="display:inline-block;">SFTP Explorer</h4>
      <button class="btn btn-icon" style="float:right; margin-right: 5px;" @click="$emit('fetch-list', path)" :disabled="isLoading" title="刷新当前目录">
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
      </button>
      <button class="btn btn-icon" style="float:right;" @click="showSftpDetails = !showSftpDetails" :title="showSftpDetails ? '简洁视图' : '详细视图'">
        <svg v-if="!showSftpDetails" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        <svg v-else width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><circle cx="4" cy="6" r="2"/><circle cx="4" cy="12" r="2"/><circle cx="4" cy="18" r="2"/></svg>
      </button>
    </div>

    <div class="sftp-body">
      <input class="sftp-path" :value="path" @keyup.enter="e => $emit('fetch-list', e.target.value)" :disabled="isLoading" :title="path" style="width:100%;margin-bottom:15px;"/>

      <div v-if="showSftpDetails" class="sftp-list-header">
        <span style="flex:1;">名称</span><span style="width:90px;text-align:right;">大小</span><span style="width:150px;text-align:right;">修改时间</span>
      </div>

      <div v-if="isLoading" class="sftp-loader">正在加载...</div>
      <div v-if="error" class="sftp-error">{{ error }}</div>

      <ul v-if="!isLoading && !error" class="file-list">
        <li v-for="file in files" :key="file.path"
            @click="handleFileClick(file, $event)"
            @dblclick="handleFileDblClick(file)"
            :title="file.longname"
            :class="{ 'selected': selectedFiles.some(f => f.path === file.path) }"
            :style="showSftpDetails ? 'display:flex;align-items:center;gap:10px;' : ''">
          <svg v-if="file.isDirectory" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
          <span style="flex:1;">{{ file.name }}</span>
          <template v-if="showSftpDetails">
            <span style="width:90px;text-align:right;font-size:0.85em;color:#aaa;">{{ formatFileSize(file.size) }}</span>
            <span style="width:150px;text-align:right;font-size:0.85em;color:#aaa;">{{ formatMtime(file.mtime) }}</span>
          </template>
        </li>
      </ul>

      <input type="file" ref="fileInputRef" style="display: none" @change="onFileSelectedForUpload">

      <div class="sftp-actions">
        <div v-if="isActionInProgress" class="upload-progress-container">
          <div class="progress-section"><div class="progress-label"><span>本地 → Web Terminal</span><span v-if="uploadSpeed">({{ uploadSpeed }})</span></div><div class="progress-bar"><div class="progress-bar-inner" :style="{width: localUploadProgress + '%'}"></div></div></div>
          <div class="progress-section" :class="{ 'visible': localUploadProgress === 100 || remoteUploadProgress > 0 }"><div class="progress-label"><span>Web Terminal → 远程服务器</span><span v-if="sftpUploadSpeed">({{ sftpUploadSpeed }})</span></div><div class="progress-bar"><div class="progress-bar-inner" :style="{width: remoteUploadProgress + '%'}"></div></div></div>
          <div class="upload-status-text">{{ uploadStatusText }}</div>
        </div>
        <template v-else>
          <button type="button" class="btn btn-sm" @click="triggerUpload">上传</button>
          <button type="button" class="btn btn-sm" @click="downloadSelectedFiles" :disabled="selectedFiles.length === 0">下载</button>
        </template>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  isVisible: Boolean, isLoading: Boolean, error: String, path: String, files: Array,
  isActionInProgress: Boolean, localUploadProgress: Number, remoteUploadProgress: Number,
  uploadStatusText: String, uploadSpeed: String, sftpUploadSpeed: String,
});

const emit = defineEmits(['fetch-list', 'download-files', 'upload-file']);

const showSftpDetails = ref(false);
const selectedFiles = ref([]);
const fileInputRef = ref(null);

watch(() => props.path, () => {
  selectedFiles.value = []; // 路径改变时，清空选择
});

const handleFileClick = (file, event) => {
  if (event.ctrlKey || event.metaKey) {
    const idx = selectedFiles.value.findIndex(f => f.path === file.path);
    if (idx > -1) selectedFiles.value.splice(idx, 1);
    else selectedFiles.value.push(file);
  } else {
    selectedFiles.value = [file];
  }
};

const handleFileDblClick = (file) => {
  if (file.isDirectory) {
    emit('fetch-list', file.path);
  }
};

const triggerUpload = () => {
  fileInputRef.value?.click();
};

const onFileSelectedForUpload = (event) => {
  const file = event.target.files[0];
  if (file) {
    emit('upload-file', file);
  }
  event.target.value = ''; // 允许再次选择相同文件
};

const downloadSelectedFiles = () => {
  const pathsToDownload = selectedFiles.value
      .filter(f => !f.isDirectory)
      .map(f => f.path);
  if(pathsToDownload.length > 0) {
    emit('download-files', pathsToDownload);
  }
};

// --- Helper Functions ---
function formatFileSize(size) {
  if (size == null) return '';
  if (size > 1024 * 1024 * 1024) return (size / (1024 ** 3)).toFixed(2) + ' GB';
  if (size > 1024 * 1024) return (size / (1024 ** 2)).toFixed(2) + ' MB';
  if (size > 1024) return (size / 1024).toFixed(1) + ' KB';
  return size + ' B';
}
function formatMtime(mtime) {
  if (!mtime) return '';
  const d = new Date(mtime * 1000);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}
</script>
