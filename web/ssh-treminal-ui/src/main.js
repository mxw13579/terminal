
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 导入样式系统
import './styles/design-system.css'
import './styles/utilities.css'

// 导入全局组件
import ToastManager from './components/ui/ToastManager.vue'
import LoadingState from './components/ui/LoadingState.vue'
import ErrorBoundary from './components/ui/ErrorBoundary.vue'

// 创建应用实例
const app = createApp(App)
const pinia = createPinia()

// 注册全局组件
app.component('ToastManager', ToastManager)
app.component('LoadingState', LoadingState)
app.component('ErrorBoundary', ErrorBoundary)

// 全局错误处理
app.config.errorHandler = (error, instance, info) => {
  console.error('Global error:', error)
  console.error('Component instance:', instance)
  console.error('Error info:', info)
  
  // 在生产环境中可以发送错误报告到服务器
  if (import.meta.env.PROD) {
    // reportError(error, instance, info)
  }
}

// 全局警告处理（仅开发环境）
if (import.meta.env.DEV) {
  app.config.warnHandler = (msg, instance, trace) => {
    console.warn('Vue warning:', msg)
    console.warn('Component trace:', trace)
  }
}

// 应用性能监控
app.config.performance = import.meta.env.DEV

// 安装插件
app.use(pinia)
app.use(router)

// 挂载应用
app.mount('#app')

// 初始化主题系统
import { useThemeStore } from './stores/theme'
const themeStore = useThemeStore()
themeStore.initialize()

// 服务工作者注册（PWA支持）
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then((registration) => {
        console.log('SW registered: ', registration)
      })
      .catch((registrationError) => {
        console.log('SW registration failed: ', registrationError)
      })
  })
}

// 全局键盘快捷键
document.addEventListener('keydown', (event) => {
  // Alt + H: 显示帮助
  if (event.altKey && event.key === 'h') {
    event.preventDefault()
    // 显示键盘快捷键帮助
    console.log('Keyboard shortcuts help')
  }
  
  // Ctrl/Cmd + K: 全局搜索
  if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
    event.preventDefault()
    // 打开搜索框
    console.log('Open search')
  }
  
  // Escape: 关闭模态框或回到首页
  if (event.key === 'Escape') {
    // 可以在这里添加全局的Escape处理逻辑
    console.log('Global escape key pressed')
  }
})

// 全局无障碍功能初始化
const initAccessibility = () => {
  // 添加跳过链接
  const skipLink = document.createElement('a')
  skipLink.href = '#main-content'
  skipLink.className = 'skip-link'
  skipLink.textContent = '跳到主要内容'
  document.body.insertBefore(skipLink, document.body.firstChild)
  
  // 设置语言属性
  document.documentElement.lang = 'zh-CN'
  
  // 监听焦点变化，提供更好的键盘导航体验
  let lastFocusedElement = null
  document.addEventListener('focusin', (event) => {
    lastFocusedElement = event.target
  })
  
  // 模态框焦点管理
  window.trapFocus = (element) => {
    const focusableElements = element.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )
    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]
    
    element.addEventListener('keydown', (e) => {
      if (e.key === 'Tab') {
        if (e.shiftKey && document.activeElement === firstElement) {
          e.preventDefault()
          lastElement.focus()
        } else if (!e.shiftKey && document.activeElement === lastElement) {
          e.preventDefault()
          firstElement.focus()
        }
      }
    })
    
    firstElement?.focus()
  }
  
  // 恢复焦点的全局函数
  window.restoreFocus = () => {
    if (lastFocusedElement) {
      lastFocusedElement.focus()
    }
  }
}

// 初始化无障碍功能
initAccessibility()

// 开发环境工具
if (import.meta.env.DEV) {
  // 添加全局调试工具
  window.__VUE_APP__ = app
  window.__PINIA__ = pinia
  window.__ROUTER__ = router
  
  // 性能监控
  if (typeof window.performance !== 'undefined') {
    window.addEventListener('load', () => {
      const loadTime = window.performance.timing.loadEventEnd - window.performance.timing.navigationStart
      console.log(`Page load time: ${loadTime}ms`)
    })
  }
}
