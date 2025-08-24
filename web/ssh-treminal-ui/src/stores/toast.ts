import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface ToastItem {
  id: string
  type: 'success' | 'error' | 'warning' | 'info' | 'loading'
  title: string
  message?: string
  duration?: number
  persistent?: boolean
  action?: {
    label: string
    handler: () => void
  }
  createdAt: number
}

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastItem[]>([])

  // 计算属性
  const activeToasts = computed(() => 
    toasts.value.filter(toast => !toast.persistent || Date.now() - toast.createdAt < (toast.duration || 5000))
  )

  // 添加toast
  const addToast = (toast: Omit<ToastItem, 'id' | 'createdAt'>): string => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    const newToast: ToastItem = {
      id,
      createdAt: Date.now(),
      duration: 5000,
      ...toast
    }
    
    toasts.value.push(newToast)
    
    // 自动移除非持久化的toast
    if (!newToast.persistent && newToast.duration && newToast.duration > 0) {
      setTimeout(() => {
        removeToast(id)
      }, newToast.duration)
    }
    
    return id
  }

  // 移除toast
  const removeToast = (id: string) => {
    const index = toasts.value.findIndex(t => t.id === id)
    if (index > -1) {
      toasts.value.splice(index, 1)
    }
  }

  // 清空所有toast
  const clearAll = () => {
    toasts.value = []
  }

  // 便捷方法
  const success = (title: string, message?: string, options?: Partial<ToastItem>) => {
    return addToast({ type: 'success', title, message, ...options })
  }

  const error = (title: string, message?: string, options?: Partial<ToastItem>) => {
    return addToast({ type: 'error', title, message, persistent: true, ...options })
  }

  const warning = (title: string, message?: string, options?: Partial<ToastItem>) => {
    return addToast({ type: 'warning', title, message, ...options })
  }

  const info = (title: string, message?: string, options?: Partial<ToastItem>) => {
    return addToast({ type: 'info', title, message, ...options })
  }

  const loading = (title: string, message?: string, options?: Partial<ToastItem>) => {
    return addToast({ type: 'loading', title, message, persistent: true, ...options })
  }

  return {
    toasts,
    activeToasts,
    addToast,
    removeToast,
    clearAll,
    success,
    error,
    warning,
    info,
    loading
  }
})

// 便捷的组合式API
export const useToast = () => {
  const store = useToastStore()
  return {
    toast: store.addToast,
    success: store.success,
    error: store.error,
    warning: store.warning,
    info: store.info,
    loading: store.loading,
    remove: store.removeToast,
    clear: store.clearAll
  }
}