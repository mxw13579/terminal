import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { ThemeConfig, A11yConfig } from '@/types'

export type ThemeMode = 'light' | 'dark' | 'auto'

export const useThemeStore = defineStore('theme', () => {
  // Theme state
  const mode = ref<ThemeMode>('auto')
  const systemPrefersDark = ref(false)
  
  // Accessibility preferences
  const a11yConfig = ref<A11yConfig>({
    focusVisible: true,
    highContrast: false,
    reducedMotion: false,
    screenReader: false
  })

  // Computed theme
  const currentTheme = computed(() => {
    if (mode.value === 'auto') {
      return systemPrefersDark.value ? 'dark' : 'light'
    }
    return mode.value
  })

  const isDark = computed(() => currentTheme.value === 'dark')

  // Theme configuration
  const themeConfig = computed((): ThemeConfig => ({
    name: currentTheme.value,
    colors: {
      primary: isDark.value ? 'var(--color-primary-400)' : 'var(--color-primary-600)',
      secondary: isDark.value ? 'var(--color-secondary-400)' : 'var(--color-secondary-600)',
      accent: isDark.value ? 'var(--color-accent-400)' : 'var(--color-accent-500)',
      background: isDark.value ? '#0f172a' : '#ffffff',
      surface: isDark.value ? '#1e293b' : '#f8fafc',
      error: isDark.value ? 'var(--color-error-400)' : 'var(--color-error-600)',
      warning: isDark.value ? 'var(--color-warning-400)' : 'var(--color-warning-500)',
      success: isDark.value ? 'var(--color-success-400)' : 'var(--color-success-600)',
      info: isDark.value ? 'var(--color-primary-400)' : 'var(--color-primary-600)',
      text: {
        primary: isDark.value ? 'var(--color-secondary-100)' : 'var(--color-secondary-900)',
        secondary: isDark.value ? 'var(--color-secondary-300)' : 'var(--color-secondary-700)',
        disabled: isDark.value ? 'var(--color-secondary-500)' : 'var(--color-secondary-400)'
      }
    },
    spacing: {
      xs: 'var(--space-1)',
      sm: 'var(--space-2)',
      md: 'var(--space-4)',
      lg: 'var(--space-6)',
      xl: 'var(--space-8)'
    },
    typography: {
      fontFamily: 'var(--font-family-sans)',
      fontSize: {
        xs: 'var(--font-size-xs)',
        sm: 'var(--font-size-sm)',
        md: 'var(--font-size-base)',
        lg: 'var(--font-size-lg)',
        xl: 'var(--font-size-xl)'
      }
    }
  }))

  // Initialize system theme detection
  const initSystemTheme = () => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      systemPrefersDark.value = mediaQuery.matches
      
      // Listen for system theme changes
      mediaQuery.addEventListener('change', (e) => {
        systemPrefersDark.value = e.matches
      })
      
      // Check for reduced motion preference
      const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
      a11yConfig.value.reducedMotion = reducedMotionQuery.matches
      reducedMotionQuery.addEventListener('change', (e) => {
        a11yConfig.value.reducedMotion = e.matches
      })
      
      // Check for high contrast preference
      const highContrastQuery = window.matchMedia('(prefers-contrast: high)')
      a11yConfig.value.highContrast = highContrastQuery.matches
      highContrastQuery.addEventListener('change', (e) => {
        a11yConfig.value.highContrast = e.matches
      })
    }
  }

  // Apply theme to document
  const applyTheme = () => {
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', currentTheme.value)
      
      // Apply accessibility classes
      const classes = []
      if (a11yConfig.value.reducedMotion) classes.push('reduce-motion')
      if (a11yConfig.value.highContrast) classes.push('high-contrast')
      if (a11yConfig.value.focusVisible) classes.push('focus-visible-enabled')
      
      document.documentElement.className = classes.join(' ')
    }
  }

  // Persist theme preference
  const persistTheme = () => {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('theme-mode', mode.value)
      localStorage.setItem('a11y-config', JSON.stringify(a11yConfig.value))
    }
  }

  // Load persisted theme
  const loadPersistedTheme = () => {
    if (typeof localStorage !== 'undefined') {
      const savedMode = localStorage.getItem('theme-mode') as ThemeMode
      if (savedMode && ['light', 'dark', 'auto'].includes(savedMode)) {
        mode.value = savedMode
      }
      
      const savedA11y = localStorage.getItem('a11y-config')
      if (savedA11y) {
        try {
          const parsed = JSON.parse(savedA11y)
          a11yConfig.value = { ...a11yConfig.value, ...parsed }
        } catch (e) {
          console.warn('Failed to parse saved accessibility config:', e)
        }
      }
    }
  }

  // Actions
  const setTheme = (newMode: ThemeMode) => {
    mode.value = newMode
  }

  const toggleTheme = () => {
    if (mode.value === 'light') {
      mode.value = 'dark'
    } else if (mode.value === 'dark') {
      mode.value = 'auto'
    } else {
      mode.value = 'light'
    }
  }

  const setA11yConfig = (config: Partial<A11yConfig>) => {
    a11yConfig.value = { ...a11yConfig.value, ...config }
  }

  const resetToSystem = () => {
    mode.value = 'auto'
  }

  // Initialize theme system
  const initialize = () => {
    initSystemTheme()
    loadPersistedTheme()
    applyTheme()
  }

  // Watch for changes and apply them
  watch(currentTheme, applyTheme)
  watch(mode, persistTheme)
  watch(a11yConfig, () => {
    persistTheme()
    applyTheme()
  }, { deep: true })

  return {
    // State
    mode,
    systemPrefersDark,
    a11yConfig,
    
    // Computed
    currentTheme,
    isDark,
    themeConfig,
    
    // Actions
    setTheme,
    toggleTheme,
    setA11yConfig,
    resetToSystem,
    initialize,
    
    // Utilities
    applyTheme
  }
})