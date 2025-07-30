<template>
  <div class="user-layout" :class="{ 'dark-theme': isDark }">
    <header class="user-header">
      <div class="header-left">
        <h1 class="logo-text">
          <span class="fufu-text">FuFu IDC</span>
          <span class="regulate-text">Regulate</span>
        </h1>
      </div>
      <div class="header-right">
        <el-button 
          @click="toggleTheme" 
          :icon="isDark ? 'Sunny' : 'Moon'"
          circle
          class="theme-toggle"
        />
        <el-button type="primary" plain size="small" @click="goToAdmin" class="admin-link">
          管理后台
        </el-button>
      </div>
    </header>
    
    <main class="user-main">
      <router-view />
    </main>

    <!-- 背景装饰 -->
    <div class="bg-decorations" v-if="isDark">
      <div class="ellipse-1"></div>
      <div class="ellipse-2"></div>
      <div class="ellipse-3"></div>
    </div>
  </div>
</template>

<script setup>
import { useTheme } from '@/composables/useTheme'
import { useRouter } from 'vue-router'

const { isDark, toggleTheme } = useTheme()
const router = useRouter()

const goToAdmin = () => {
  // 在当前窗口跳转到管理端
  window.location.href = '/admin'
}
</script>

<style scoped>
.user-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--bg-primary);
  transition: all 0.3s ease;
}

.user-header {
  height: 80px;
  background: transparent;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  position: relative;
  z-index: 10;
}

.logo-text {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  font-family: 'OPPO Sans 4.0', -apple-system, BlinkMacSystemFont, sans-serif;
  font-weight: 600;
  font-size: 32px;
  line-height: 38px;
}

.fufu-text {
  background: var(--text-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.regulate-text {
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.theme-toggle {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.admin-link {
  /* 移除text-decoration样式，因为现在是button而不是链接 */
}

.user-main {
  flex: 1;
  background: var(--bg-primary);
  overflow-y: auto;
  position: relative;
  z-index: 5;
}

/* 暗色主题背景装饰 */
.bg-decorations {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  z-index: 1;
}

.ellipse-1 {
  position: absolute;
  width: 507px;
  height: 315px;
  right: 20%;
  top: 40%;
  background: linear-gradient(180deg, #070713 0%, #1666F2 64.87%);
  mix-blend-mode: hard-light;
  opacity: 0.7;
  filter: blur(150px);
  transform: rotate(-24.23deg);
  border-radius: 50%;
}

.ellipse-2 {
  position: absolute;
  width: 804px;
  height: 500px;
  left: -100px;
  top: -50px;
  background: linear-gradient(249.38deg, #070713 5.34%, #1666F2 88.76%);
  mix-blend-mode: color-dodge;
  filter: blur(50px);
  border-radius: 50%;
}

.ellipse-3 {
  position: absolute;
  width: 1216px;
  height: 145px;
  left: -100px;
  top: -50px;
  background: linear-gradient(180deg, #070713 0%, #1666F2 64.87%);
  mix-blend-mode: hard-light;
  opacity: 0.7;
  filter: blur(150px);
  transform: rotate(-179.81deg);
  border-radius: 50%;
}

/* 白天主题样式 */
.user-layout:not(.dark-theme) .user-header {
  background: white;
  border-bottom: 1px solid #e0e0e0;
}

.user-layout:not(.dark-theme) .fufu-text {
  background: linear-gradient(311.51deg, #005DFF -9.67%, #409EFF 90.38%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.user-layout:not(.dark-theme) .regulate-text {
  color: #333;
}

.user-layout:not(.dark-theme) .user-main {
  background: #f5f5f5;
}
</style>