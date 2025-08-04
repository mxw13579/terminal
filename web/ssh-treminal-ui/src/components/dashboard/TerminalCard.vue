<template>
  <div class="dashboard-card terminal-card">
    <div class="card-header">
      <div class="card-icon">💻</div>
      <div class="card-title-section">
        <h3 class="card-title">SSH 终端</h3>
        <p class="card-subtitle">远程服务器命令行管理</p>
      </div>
    </div>
    
    <div class="card-content">
      <div class="card-features">
        <div class="feature-item">
          <span class="feature-icon">🔒</span>
          <span class="feature-text">安全连接</span>
        </div>
        <div class="feature-item">
          <span class="feature-icon">📁</span>
          <span class="feature-text">文件传输</span>
        </div>
        <div class="feature-item">
          <span class="feature-icon">📊</span>
          <span class="feature-text">系统监控</span>
        </div>
        <div class="feature-item">
          <span class="feature-icon">⚡</span>
          <span class="feature-text">实时操作</span>
        </div>
      </div>
      
      <div class="card-description">
        <p>通过 Web 界面直接访问远程服务器终端，支持文件上传下载、系统状态监控和完整的命令行操作体验。</p>
      </div>
    </div>
    
    <div class="card-actions">
      <router-link to="/terminal" class="card-button primary">
        <span class="button-icon">🚀</span>
        打开终端
      </router-link>
    </div>
  </div>
</template>

<script setup>
// No reactive state needed for static card
</script>

<style scoped>
.dashboard-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.dashboard-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.terminal-card {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.card-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
}

.card-icon {
  font-size: 3rem;
  margin-right: 16px;
  opacity: 0.9;
}

.card-title-section {
  flex: 1;
}

.card-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: white;
}

.card-subtitle {
  font-size: 0.9rem;
  margin: 0;
  opacity: 0.8;
}

.card-content {
  margin-bottom: 24px;
}

.card-features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.1);
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 0.9rem;
}

.feature-icon {
  margin-right: 8px;
  font-size: 1rem;
}

.feature-text {
  font-weight: 500;
}

.card-description {
  opacity: 0.9;
  line-height: 1.6;
}

.card-description p {
  margin: 0;
  font-size: 0.95rem;
}

.card-actions {
  display: flex;
  gap: 12px;
}

.card-button {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  border-radius: 10px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  flex: 1;
  justify-content: center;
}

.card-button.primary {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.card-button.primary:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-2px);
}

.button-icon {
  margin-right: 8px;
  font-size: 1rem;
}

/* Responsive design */
@media (max-width: 500px) {
  .card-features {
    grid-template-columns: 1fr;
  }
  
  .card-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .card-icon {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>