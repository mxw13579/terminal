<!-- /components/SshConsole.vue -->
<template>
  <header class="workspace-header">
    <div class="connection-info">
      <span class="dot connected"></span>
      <span>{{ connectionInfo.user }}@{{ connectionInfo.host }}:{{ connectionInfo.port }}</span>
    </div>
    <div class="workspace-controls">
      <button type="button" class="btn btn-icon" :class="{active: sftpVisible}" @click="$emit('toggle-sftp')" title="SFTP 文件传输">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
      </button>
      <button type="button" class="btn btn-secondary" @click="$emit('disconnect')">
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        <span>断开连接</span>
      </button>
    </div>
  </header>

  <main class="workspace-content">
    <div class="terminal-container-main" ref="terminalContainerRef" @transitionend="safeFit">
      <div class="terminal-wrapper" ref="terminalRef"></div>
    </div>
    <!-- 插槽: 父组件将在这里插入SFTP面板 -->
    <slot name="aside"></slot>
  </main>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';

const props = defineProps({
  connectionInfo: { type: Object, required: true },
  sftpVisible: { type: Boolean, default: false },
});

const emit = defineEmits(['disconnect', 'toggle-sftp', 'terminal-data', 'terminal-resize', 'terminal-ready', 'terminal-unmount']);

const terminalRef = ref(null);
const terminalContainerRef = ref(null);
let term;
let fitAddon;
let resizeObserver;

onMounted(async () => {
  await nextTick();
  initializeTerminal();
  setupResizeObserver();
  // 通知父组件终端已就绪，并传递实例
  emit('terminal-ready', term);
  safeFit();
});

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect();
  if (term) term.dispose();
  emit('terminal-unmount');
});

// 当SFTP面板显/隐时，容器尺寸会变化，需要重新fit
watch(() => props.sftpVisible, () => {
  // 等待CSS过渡动画结束后再fit，效果更平滑
  // @transitionend事件处理也能达到目的
  setTimeout(() => safeFit(), 350);
});

const initializeTerminal = () => {
  if (!terminalRef.value) return;
  term = new Terminal({
    cursorBlink: true, fontSize: 14, fontFamily: '"Fira Code", Consolas, "Courier New", monospace',
    theme: { background: 'rgba(0, 0, 0, 0)', foreground: '#d4d4d4', cursor: '#d4d4d4', selectionBackground: '#264f78' },
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
    emit('terminal-data', data);
  });
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
    if (fitAddon && term?.element?.clientWidth > 0) {
      fitAddon.fit();
      const { cols, rows } = term;
      emit('terminal-resize', { cols, rows });
    }
  } catch (e) { /* 忽略 fit() 在隐藏元素上可能抛出的错误 */ }
};
</script>
