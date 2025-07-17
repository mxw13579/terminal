<template>
  <aside class="monitor-panel" :class="{ 'monitor-panel-visible': isVisible }">
    <div class="panel-header">
      <h4>
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
        <span>服务器监控</span>
      </h4>
    </div>
    <div v-if="isLoading && !stats" class="panel-loader">
      <div class="spinner"></div>
      <p>正在连接并获取监控数据...</p>
    </div>
    <div v-else-if="!stats" class="panel-loader">
      <p>监控已停止或无法获取数据。</p>
    </div>
    <div v-else class="panel-body">
      <!-- 基本数据 -->
      <section class="stats-section">
        <div class="section-title">基本数据</div>
        <div class="stat-item">
          <span class="stat-label">CPU 型号</span>
          <span class="stat-value" :title="stats.cpuModel">{{ stats.cpuModel }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">运行时长</span>
          <span class="stat-value">{{ stats.uptime }}</span>
        </div>
        <div class="stat-item progress-item">
          <span class="stat-label">CPU 使用率</span>
          <div class="progress-bar">
            <div class="progress-bar-inner" :style="{ width: stats.cpuUsage + '%' }"></div>
          </div>
          <span class="stat-percent">{{ stats.cpuUsage.toFixed(2) }}%</span>
        </div>
        <div class="stat-item progress-item">
          <span class="stat-label">内存使用率</span>
          <div class="progress-bar">
            <div class="progress-bar-inner" :style="{ width: stats.memUsage + '%' }"></div>
          </div>
          <span class="stat-percent">{{ stats.memUsage.toFixed(2) }}%</span>
        </div>
        <div class="stat-item progress-item">
          <span class="stat-label">硬盘使用率 (/)</span>
          <div class="progress-bar">
            <div class="progress-bar-inner" :style="{ width: stats.diskUsage + '%' }"></div>
          </div>
          <span class="stat-percent">{{ stats.diskUsage.toFixed(2) }}%</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">网络 I/O</span>
          <span class="stat-value small-text">接收: {{ stats.netRx }} | 发送: {{ stats.netTx }}</span>
        </div>
      </section>

      <!-- Docker 容器 -->
      <section v-if="dockerContainers && dockerContainers.length" class="stats-section docker-section">
        <div class="section-title">Docker 容器</div>
        <div class="docker-list">
          <div v-for="container in dockerContainers" :key="container.id" class="docker-item">
            <div class="docker-item-header">
              <span class="docker-name" :title="container.name">{{ container.name }}</span>
              <span class="docker-status" :class="container.status.includes('Up') ? 'up' : 'exited'">
                {{ container.status.split(' ')[0] }}
              </span>
            </div>
            <div class="docker-item-body">
              <span>CPU: {{ container.cpuPerc }}</span>
              <span>内存: {{ container.memPerc }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  isVisible: Boolean,
  isLoading: Boolean,
  stats: { type: Object, default: null },
  dockerContainers: { type: Array, default: () => [] },
});
</script>

<style scoped>
/* 使用 scoped 以免污染全局样式 */
.monitor-panel {
  width: 0;
  opacity: 0;
  transform: translateX(-20px);
  transition: width 0.3s ease, opacity 0.3s ease, transform 0.3s ease;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.monitor-panel-visible {
  width: 300px; /* 监控面板宽度 */
  opacity: 1;
  transform: translateX(0);
}

.panel-header {
  padding: 15px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.panel-header h4 {
  margin: 0;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-loader {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #999;
  padding: 20px;
  text-align: center;
}

.spinner {
  border: 4px solid rgba(255, 255, 255, 0.1);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border-left-color: var(--glow-color);
  animation: spin 1s ease infinite;
  margin-bottom: 15px;
}
@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.panel-body {
  padding: 15px;
  overflow-y: auto;
  flex-grow: 1;
  scrollbar-width: thin;
  scrollbar-color: var(--glow-color) rgba(255, 255, 255, 0.08);
}
.panel-body::-webkit-scrollbar {
  width: 6px;
}
.panel-body::-webkit-scrollbar-thumb {
  background-color: var(--glow-color);
  border-radius: 6px;
}

.stats-section {
  margin-bottom: 20px;
}
.section-title {
  font-weight: 600;
  color: #e0e0e0;
  margin-bottom: 12px;
  padding-bottom: 5px;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  margin-bottom: 10px;
  gap: 10px;
}
.stat-label {
  color: #aaa;
  white-space: nowrap;
}
.stat-value {
  color: #ddd;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-value.small-text {
  font-size: 0.75rem;
}

.progress-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
}
.progress-bar {
  width: 100%;
  height: 8px;
  background-color: rgba(0, 0, 0, 0.3);
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar-inner {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #6366f1);
  transition: width 0.5s ease-in-out;
}
.stat-percent {
  font-size: 0.8rem;
  color: #ccc;
  width: 50px;
  text-align: right;
}
.docker-section {
  max-height: 40vh;
  display: flex;
  flex-direction: column;
}

.docker-list {
  overflow-y: auto;
  margin-right: -10px;
  padding-right: 10px;
}

.docker-item {
  background-color: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 0.8rem;
}

.docker-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.docker-name {
  font-weight: 500;
  color: #c4b5fd;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.docker-status {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.7rem;
}
.docker-status.up {
  background-color: rgba(52, 211, 153, 0.2);
  color: #6ee7b7;
}
.docker-status.exited {
  background-color: rgba(209, 213, 219, 0.2);
  color: #9ca3af;
}

.docker-item-body {
  display: flex;
  justify-content: space-between;
  color: #999;
  font-size: 0.75rem;
}

</style>
