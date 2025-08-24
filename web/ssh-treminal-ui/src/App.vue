<template>
  <div id="app">
    <!-- 主要内容区域 -->
    <main id="main-content" tabindex="-1">
      <router-view />
    </main>
    
    <!-- 全局组件 -->
    <ToastManager />
  </div>
</template>

<script setup>
// 主应用组件 - 简洁的路由容器
import { onMounted, onUnmounted } from 'vue'
import { useThemeStore } from './stores/theme'

const themeStore = useThemeStore()

// 处理系统主题变化
const handleSystemThemeChange = (e) => {
  if (themeStore.mode === 'auto') {
    themeStore.applyTheme()
  }
}

onMounted(() => {
  // 监听系统主题变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', handleSystemThemeChange)
  
  // 清理函数
  return () => {
    mediaQuery.removeEventListener('change', handleSystemThemeChange)
  }
})

// 页面可见性变化处理（性能优化）
const handleVisibilityChange = () => {
  if (document.hidden) {
    // 页面不可见时暂停一些不必要的操作
    console.log('Page is hidden')
  } else {
    // 页面可见时恢复操作
    console.log('Page is visible')
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style>
/* 全局应用样式 - 简化版本 */
#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

#main-content {
  flex: 1;
  outline: none;
}

/* 确保滚动容器正确工作 */
html,
body,
#app {
  height: 100%;
  overflow: visible;
}

/* 全局滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: var(--bg-secondary);
}

::-webkit-scrollbar-thumb {
  background: var(--border-secondary);
  border-radius: var(--radius-base);
}

::-webkit-scrollbar-thumb:hover {
  background: var(--border-primary);
}

/* Firefox滚动条样式 */
* {
  scrollbar-width: thin;
  scrollbar-color: var(--border-secondary) var(--bg-secondary);
}

/* 焦点管理 */
*:focus {
  outline: 2px solid var(--border-focus);
  outline-offset: 2px;
}

*:focus:not(:focus-visible) {
  outline: none;
}

/* 选择文本样式 */
::selection {
  background-color: var(--color-primary);
  color: white;
}

::-moz-selection {
  background-color: var(--color-primary);
  color: white;
}

/* 图片懒加载占位符 */
img[loading="lazy"] {
  background: var(--bg-secondary);
}

/* 表单元素一致性 */
input,
select,
textarea,
button {
  font-family: inherit;
}

/* 表格样式重置 */
table {
  border-collapse: collapse;
  border-spacing: 0;
}

/* 移除按钮默认样式 */
button {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

/* 链接样式重置 */
a {
  color: inherit;
  text-decoration: none;
}

a:focus-visible {
  outline: 2px solid var(--border-focus);
  outline-offset: 2px;
  border-radius: var(--radius-base);
}

/* 列表样式重置 */
ul,
ol {
  list-style: none;
  padding: 0;
  margin: 0;
}

/* 标题样式重置 */
h1,
h2,
h3,
h4,
h5,
h6 {
  margin: 0;
  font-weight: inherit;
}

/* 段落样式重置 */
p {
  margin: 0;
}

/* 响应式媒体 */
img,
video,
canvas,
svg {
  max-width: 100%;
  height: auto;
}

/* 打印样式 */
@media print {
  * {
    background: white !important;
    color: black !important;
    box-shadow: none !important;
  }
  
  a,
  a:visited {
    text-decoration: underline;
  }
  
  img {
    max-width: 100% !important;
  }
  
  .no-print {
    display: none !important;
  }
}

/* 减少动画设置 */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  * {
    border-color: currentColor !important;
  }
}
</style>
