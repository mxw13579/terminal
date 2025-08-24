<template>
  <div class="skeleton-terminal">
    <div class="skeleton-terminal-header">
      <div class="skeleton-terminal-controls">
        <div class="skeleton-control-button" />
        <div class="skeleton-control-button" />
        <div class="skeleton-control-button" />
      </div>
      <div class="skeleton-terminal-title" />
    </div>
    <div class="skeleton-terminal-content">
      <div 
        v-for="n in lines" 
        :key="n" 
        class="skeleton-terminal-line"
        :style="{ width: getLineWidth(n) }"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  lines: {
    type: Number,
    default: 12
  }
})

const getLineWidth = (lineNumber) => {
  // 生成随机宽度，模拟真实终端输出
  const widths = ['40%', '60%', '80%', '90%', '70%', '50%', '85%', '45%', '75%', '65%']
  return widths[lineNumber % widths.length]
}
</script>

<style scoped>
.skeleton-terminal {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #000;
  color: #00ff00;
  overflow: hidden;
}

.skeleton-terminal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #333;
  border-bottom: 1px solid var(--color-border);
}

.skeleton-terminal-controls {
  display: flex;
  gap: 6px;
}

.skeleton-control-button {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-border);
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.skeleton-terminal-title {
  width: 120px;
  height: 12px;
  background: var(--color-border);
  border-radius: 4px;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.skeleton-terminal-content {
  padding: 16px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  min-height: 300px;
}

.skeleton-terminal-line {
  height: 18px;
  background: #00ff0033;
  border-radius: 2px;
  margin-bottom: 4px;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.skeleton-terminal-line:nth-child(odd) {
  animation-delay: 0.2s;
}

.skeleton-terminal-line:nth-child(even) {
  animation-delay: 0.4s;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
</style>