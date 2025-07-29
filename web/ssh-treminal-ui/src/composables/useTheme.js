import { ref, computed } from 'vue'

const isDark = ref(localStorage.getItem('theme') === 'dark')

export const useTheme = () => {
  const theme = computed(() => isDark.value ? 'dark' : 'light')
  
  const toggleTheme = () => {
    isDark.value = !isDark.value
    localStorage.setItem('theme', theme.value)
    updateCSSVariables()
  }

  const updateCSSVariables = () => {
    const root = document.documentElement
    
    if (isDark.value) {
      // 暗色主题样式变量
      root.style.setProperty('--bg-primary', '#0D0F1C')  // Blue gray/950
      root.style.setProperty('--bg-secondary', 'rgba(93, 151, 255, 0.1)')
      root.style.setProperty('--text-primary', '#FFFFFF')
      root.style.setProperty('--text-secondary', '#5D97FF')
      root.style.setProperty('--text-gradient', 'linear-gradient(311.51deg, #005DFF -9.67%, #BAD2FF 90.38%)')
      root.style.setProperty('--card-bg', 'rgba(93, 151, 255, 0.1)')
      root.style.setProperty('--border-color', 'rgba(93, 151, 255, 0.2)')
      root.style.setProperty('--shadow-color', 'rgba(0, 0, 0, 0.3)')
    } else {
      // 白天主题样式变量  
      root.style.setProperty('--bg-primary', '#f5f5f5')
      root.style.setProperty('--bg-secondary', '#ffffff')
      root.style.setProperty('--text-primary', '#333333')
      root.style.setProperty('--text-secondary', '#666666')
      root.style.setProperty('--text-gradient', 'linear-gradient(311.51deg, #005DFF -9.67%, #BAD2FF 90.38%)')
      root.style.setProperty('--card-bg', '#ffffff')
      root.style.setProperty('--border-color', '#e0e0e0')
      root.style.setProperty('--shadow-color', 'rgba(0, 0, 0, 0.1)')
    }
  }

  // 初始化主题
  updateCSSVariables()

  return {
    isDark,
    theme,
    toggleTheme
  }
}