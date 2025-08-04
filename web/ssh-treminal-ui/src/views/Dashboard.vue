<template>
  <div class="dashboard">
    <NavigationHeader />
    
    <main class="dashboard-main">
      <div class="dashboard-hero">
        <h1 class="dashboard-title">终端管理中心</h1>
        <p class="dashboard-subtitle">统一管理您的服务器和应用程序</p>
      </div>
      
      <!-- 连接管理器 -->
      <div class="connection-section">
        <ConnectionManager />
      </div>
      
      <!-- 服务卡片 -->
      <div class="services-section">
        <h2 class="section-title">选择服务</h2>
        <div class="service-cards">
          <!-- SillyTavern 卡片 -->
          <div class="dashboard-card sillytavern-card" :class="{ 'card-disabled': !connectionState.isConnected }">
            <div class="card-header">
              <div class="card-icon">🤖</div>
              <div class="card-title-section">
                <h3 class="card-title">SillyTavern 控制台</h3>
                <p class="card-subtitle">AI 角色扮演与对话平台</p>
              </div>
            </div>
            
            <div class="card-content">
              <div class="card-features">
                <div class="feature-item">
                  <span class="feature-icon">🚀</span>
                  <span class="feature-text">一键部署</span>
                </div>
                <div class="feature-item">
                  <span class="feature-icon">⚙️</span>
                  <span class="feature-text">在线配置</span>
                </div>
                <div class="feature-item">
                  <span class="feature-icon">📊</span>
                  <span class="feature-text">实时监控</span>
                </div>
                <div class="feature-item">
                  <span class="feature-icon">📁</span>
                  <span class="feature-text">数据管理</span>
                </div>
              </div>
              
              <div class="card-description">
                <p>轻松部署和管理 SillyTavern AI 对话平台，无需命令行操作，支持在线配置修改、数据备份恢复和实时状态监控。</p>
              </div>
            </div>
            
            <div class="card-actions">
              <button 
                @click="navigateToSillyTavern" 
                :disabled="!connectionState.isConnected"
                class="card-button primary"
              >
                <span class="button-icon">🎯</span>
                <span v-if="connectionState.isConnected">进入控制台</span>
                <span v-else>需要连接服务器</span>
              </button>
            </div>
          </div>
          
          <!-- SSH 终端卡片 -->
          <div class="dashboard-card terminal-card" :class="{ 'card-disabled': !connectionState.isConnected }">
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
              <button 
                @click="navigateToTerminal" 
                :disabled="!connectionState.isConnected"
                class="card-button primary"
              >
                <span class="button-icon">🚀</span>
                <span v-if="connectionState.isConnected">打开终端</span>
                <span v-else>需要连接服务器</span>
              </button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 连接提示 -->
      <div v-if="!connectionState.isConnected && !connectionState.connecting" class="connection-hint">
        <div class="hint-content">
          <div class="hint-icon">
            <i class="fas fa-info-circle"></i>
          </div>
          <div class="hint-text">
            <h3>开始使用</h3>
            <p v-if="!connectionState.error">请先连接到您的服务器，然后选择需要的服务进行管理</p>
            <div v-else class="error-details">
              <h4>连接失败</h4>
              <div class="error-message">{{ connectionState.error }}</div>
              <button @click="connectionState.error = null" class="btn btn-secondary btn-sm retry-button">
                <i class="fas fa-redo"></i>
                重新尝试
              </button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 添加额外内容来确保页面有足够高度触发滚动 -->
      <div class="footer-spacer">
        <div class="feature-highlights">
          <h2 class="section-title">平台特色</h2>
          <div class="highlight-grid">
            <div class="highlight-item">
              <div class="highlight-icon">🛡️</div>
              <h3>安全可靠</h3>
              <p>采用SSH加密连接，确保数据传输安全</p>
            </div>
            <div class="highlight-item">
              <div class="highlight-icon">🚀</div>
              <h3>简单易用</h3>
              <p>无需命令行知识，一键式操作管理</p>
            </div>
            <div class="highlight-item">
              <div class="highlight-icon">📊</div>
              <h3>实时监控</h3>
              <p>实时查看服务状态和系统资源使用情况</p>
            </div>
            <div class="highlight-item">
              <div class="highlight-icon">🔧</div>
              <h3>完整管理</h3>
              <p>从部署到配置，提供完整的管理解决方案</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import NavigationHeader from '../components/NavigationHeader.vue'
import ConnectionManager from '../components/ConnectionManager.vue'
import useConnectionManager from '../composables/useConnectionManager'

const router = useRouter()
const { connectionState } = useConnectionManager()

const navigateToSillyTavern = () => {
  if (connectionState.isConnected) {
    router.push('/sillytavern')
  }
}

const navigateToTerminal = () => {
  if (connectionState.isConnected) {
    router.push('/terminal')
  }
}
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: visible !important; /* 强制允许内容溢出和滚动 */
}

.dashboard::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: 
    radial-gradient(circle at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(120, 119, 198, 0.2) 0%, transparent 50%);
  pointer-events: none; /* 确保不阻止交互 */
  z-index: 0; /* 确保在内容下方 */
}

.dashboard-main {
  position: relative;
  z-index: 1;
  padding: 40px 20px 80px; /* 增加更多底部内边距 */
  max-width: 1400px;
  margin: 0 auto;
  /* 移除任何高度限制，让内容自然流动 */
  overflow: visible !important; /* 确保内容可以溢出 */
}

.dashboard-hero {
  text-align: center;
  margin-bottom: 40px;
}

.dashboard-title {
  font-size: 3rem;
  font-weight: 800;
  background: linear-gradient(135deg, #ffffff 0%, #f0f0f0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
  text-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.dashboard-subtitle {
  font-size: 1.3rem;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  font-weight: 300;
}

.connection-section {
  margin-bottom: 48px;
}

.services-section {
  margin-bottom: 48px;
}

.section-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: white;
  text-align: center;
  margin-bottom: 32px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.service-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(450px, 1fr));
  gap: 32px;
  max-width: 1000px;
  margin: 0 auto;
}

/* 漂亮的卡片样式 */
.dashboard-card {
  background: white;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.15);
  transition: all 0.4s ease;
  border: 1px solid rgba(255, 255, 255, 0.2);
  position: relative;
  overflow: hidden;
}

.dashboard-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 6px;
  background: linear-gradient(90deg, #4299e1, #667eea);
  transform: translateX(-100%);
  transition: transform 0.6s ease;
}

.dashboard-card:hover::before {
  transform: translateX(0);
}

.dashboard-card:hover {
  transform: translateY(-10px) scale(1.02);
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.2);
}

.sillytavern-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.sillytavern-card::before {
  background: linear-gradient(90deg, #ffffff, #f0f0f0);
}

.terminal-card {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.terminal-card::before {
  background: linear-gradient(90deg, #ffffff, #e0f7ff);
}

.card-disabled {
  opacity: 0.6;
  transform: none !important;
  cursor: not-allowed;
}

.card-disabled:hover {
  transform: none !important;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.15) !important;
}

.card-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24px;
}

.card-icon {
  font-size: 3.5rem;
  margin-right: 20px;
  opacity: 0.9;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.card-title-section {
  flex: 1;
}

.card-title {
  font-size: 1.6rem;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: white;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.card-subtitle {
  font-size: 1rem;
  margin: 0;
  opacity: 0.85;
  font-weight: 300;
}

.card-content {
  margin-bottom: 28px;
}

.card-features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  margin-bottom: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 0.95rem;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.2s ease;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
}

.feature-icon {
  margin-right: 10px;
  font-size: 1.1rem;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

.feature-text {
  font-weight: 500;
}

.card-description {
  opacity: 0.9;
  line-height: 1.7;
}

.card-description p {
  margin: 0;
  font-size: 1rem;
}

.card-actions {
  display: flex;
  gap: 12px;
}

.card-button {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-radius: 12px;
  border: none;
  background: transparent;
  color: white;
  font-weight: 600;
  font-size: 1rem;
  transition: all 0.3s ease;
  flex: 1;
  justify-content: center;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.card-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
  transition: left 0.5s ease;
}

.card-button:hover::before {
  left: 100%;
}

.card-button.primary {
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.card-button.primary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

.card-button:disabled {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.card-button:disabled::before {
  display: none;
}

.button-icon {
  margin-right: 10px;
  font-size: 1.1rem;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

.connection-hint {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 40px;
  text-align: center;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  margin-top: 32px;
}

.hint-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.hint-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #4299e1, #667eea);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8px 25px rgba(66, 153, 225, 0.3);
}

.hint-icon i {
  font-size: 2.2rem;
  color: white;
}

.hint-text {
  text-align: left;
}

.hint-text h3 {
  margin: 0 0 12px 0;
  font-size: 1.4rem;
  font-weight: 700;
  color: #2d3748;
}

.hint-text p {
  margin: 0;
  color: #718096;
  line-height: 1.6;
  font-size: 1rem;
}

.error-details {
  text-align: left;
}

.error-details h4 {
  color: #dc2626;
  margin: 0 0 12px 0;
  font-size: 1.1rem;
}

.error-message {
  background: #fef2f2;
  color: #dc2626;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  margin-bottom: 16px;
  font-size: 0.9rem;
  line-height: 1.5;
  white-space: pre-line;
}

.retry-button {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.retry-button:hover {
  background: #2563eb;
}

/* 新增的页脚内容样式 */
.footer-spacer {
  margin-top: 60px;
  padding-top: 40px;
}

.feature-highlights {
  margin-bottom: 60px;
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.highlight-item {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 32px 24px;
  text-align: center;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s ease;
}

.highlight-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.highlight-icon {
  font-size: 3rem;
  margin-bottom: 16px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.highlight-item h3 {
  font-size: 1.25rem;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 12px 0;
}

.highlight-item p {
  color: #718096;
  line-height: 1.6;
  margin: 0;
  font-size: 0.95rem;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .service-cards {
    grid-template-columns: 1fr;
    max-width: 600px;
  }
}

@media (max-width: 768px) {
  .dashboard-main {
    padding: 20px 15px 40px;
  }
  
  .dashboard-title {
    font-size: 2.5rem;
  }
  
  .dashboard-subtitle {
    font-size: 1.1rem;
  }
  
  .section-title {
    font-size: 1.5rem;
  }
  
  .service-cards {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  
  .dashboard-card {
    padding: 24px;
  }
  
  .card-features {
    grid-template-columns: 1fr;
  }
  
  .hint-content {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }
  
  .hint-text {
    text-align: center;
  }
  
  .hint-icon {
    width: 70px;
    height: 70px;
  }
  
  .hint-icon i {
    font-size: 2rem;
  }
}

@media (max-width: 480px) {
  .dashboard-title {
    font-size: 2rem;
  }
  
  .dashboard-subtitle {
    font-size: 1rem;
  }
  
  .connection-hint {
    padding: 30px 20px;
  }
  
  .dashboard-card {
    padding: 20px;
  }
  
  .card-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
    margin-bottom: 20px;
  }
  
  .card-icon {
    margin-right: 0;
    margin-bottom: 12px;
  }
  
  .card-title {
    font-size: 1.4rem;
  }
  
  .card-subtitle {
    font-size: 0.9rem;
  }
}
</style>