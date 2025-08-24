<!-- /components/SshConsole.vue -->
<template>
  <header class="workspace-header">
    <div class="connection-info">
      <span class="dot connected"></span>
      <span>{{ connectionInfo.user }}@{{ connectionInfo.host }}:{{ connectionInfo.port }}</span>
    </div>
    <div class="workspace-controls">
      <button type="button" class="btn btn-icon" :class="{active: monitorVisible}" @click="$emit('toggle-monitor')" title="服务器监控">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
      </button>
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
    <slot name="monitor-aside"></slot>

    <div class="terminal-container-main" ref="terminalContainerRef" @transitionend="safeFit">
      <div class="terminal-wrapper" ref="terminalRef"></div>
    </div>
    <slot name="aside"></slot>
  </main>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';

interface Props {
  connectionInfo: ConnectionConfig
  sftpVisible?: boolean
  monitorVisible?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  sftpVisible: false,
  monitorVisible: false
})

const emit = defineEmits(['disconnect', 'toggle-sftp',  'toggle-monitor', 'terminal-data', 'terminal-resize', 'terminal-ready', 'terminal-unmount']);

const terminalRef = ref(null);
const terminalContainerRef = ref(null);
let term;
let fitAddon;
let resizeObserver;

onMounted(async () => {
  await nextTick();
  
  // Wait for DOM to be fully rendered and styled
  setTimeout(async () => {
    initializeTerminal();
    setupResizeObserver();
    
    // Emit terminal ready event
    if (term) {
      emit('terminal-ready', term);
    }
    
    // Wait a bit more for CSS transitions to complete, then fit
    await nextTick();
    setTimeout(() => safeFit(), 100);
  }, 50);
});

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect();
  if (term) term.dispose();
  emit('terminal-unmount');
});

// 当SFTP面板显/隐时，容器尺寸会变化，需要重新fit
watch(() => [props.sftpVisible, props.monitorVisible], () => {
  // 等待CSS过渡动画结束后再fit，效果更平滑
  setTimeout(() => safeFit(), 350);
});

const initializeTerminal = () => {
  if (!terminalRef.value) {
    console.warn('Terminal ref not available for initialization');
    return false;
  }
  
  try {
    term = new Terminal({
      cursorBlink: true, 
      fontSize: 14, 
      fontFamily: '"Fira Code", Consolas, "Courier New", monospace',
      theme: { 
        background: 'rgba(0, 0, 0, 0)', 
        foreground: '#d4d4d4', 
        cursor: '#d4d4d4', 
        selectionBackground: '#264f78' 
      },
      allowTransparency: true,
      // Add scroll buffer
      scrollback: 1000,
      // Enable selection
      rightClickSelectsWord: true
    });
    
    fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    term.open(terminalRef.value);

    // Ensure terminal element has proper styling
    if (term.element) {
      term.element.style.width = '100%';
      term.element.style.height = '100%';
      term.element.style.display = 'block';
    }

    // Add data handler
    term.onData(data => {
      emit('terminal-data', data);
    });
    
    console.log('Terminal initialized successfully');
    return true;
  } catch (error) {
    console.error('Failed to initialize terminal:', error);
    return false;
  }
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
    if (!fitAddon || !term || !term.element) {
      console.warn('Terminal not ready for fitting');
      return;
    }
    
    // Check if element has dimensions
    const container = terminalContainerRef.value;
    if (!container) {
      console.warn('Terminal container ref not available');
      return;
    }
    
    const rect = container.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      console.warn('Terminal container has no dimensions:', { width: rect.width, height: rect.height });
      // Retry after a short delay
      setTimeout(() => safeFit(), 100);
      return;
    }
    
    // Perform fit
    fitAddon.fit();
    const { cols, rows } = term;
    emit('terminal-resize', { cols, rows });
    
    console.log('Terminal fitted:', { cols, rows, width: rect.width, height: rect.height });
  } catch (error) {
    console.error('Error during terminal fit:', error);
    // Retry after a delay in case of temporary issues
    setTimeout(() => safeFit(), 200);
  }
};
</script>
