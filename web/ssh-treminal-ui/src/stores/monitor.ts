import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { 
  SystemMetrics,
  MonitorMessage,
  LoadingState,
  ErrorState,
  EmptyState
} from '@/types'
import { useTerminalStore } from './terminal'

export const useMonitorStore = defineStore('monitor', () => {
  // Panel visibility
  const isVisible = ref(false)
  
  // Monitoring state
  const isMonitoring = ref(false)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  
  // System data
  const systemStats = ref<SystemMetrics | null>(null)
  const dockerContainers = ref<any[]>([])
  const networkInterfaces = ref<any[]>([])
  const processes = ref<any[]>([])
  
  // Historical data for charts (optional)
  const cpuHistory = ref<number[]>([])
  const memoryHistory = ref<number[]>([])
  const networkHistory = ref<{ rx: number[], tx: number[] }>({ rx: [], tx: [] })
  
  // Chart settings
  const maxHistoryPoints = ref(60) // Keep 60 data points for charts
  const updateInterval = ref(2000) // Default update interval in ms
  
  // Computed states
  const loadingState = computed((): LoadingState => ({
    isLoading: isLoading.value,
    message: isLoading.value ? '正在获取系统信息...' : undefined
  }))

  const errorState = computed((): ErrorState => ({
    hasError: !!error.value,
    error: error.value,
    canRetry: !isMonitoring.value
  }))

  const emptyState = computed((): EmptyState => ({
    isEmpty: !systemStats.value && !isLoading.value && !error.value,
    message: '暂无系统监控数据',
    actionText: '开始监控',
    onAction: startMonitoring
  }))

  // System metrics computed properties
  const cpuUsage = computed(() => systemStats.value?.cpu.usage || 0)
  const memoryUsage = computed(() => {
    if (!systemStats.value?.memory) return 0
    const { used, total } = systemStats.value.memory
    return total > 0 ? (used / total) * 100 : 0
  })
  const diskUsage = computed(() => {
    if (!systemStats.value?.disk) return 0
    const { used, total } = systemStats.value.disk
    return total > 0 ? (used / total) * 100 : 0
  })

  const formattedMemory = computed(() => {
    if (!systemStats.value?.memory) return { used: '0', total: '0', free: '0' }
    const { used, total, free } = systemStats.value.memory
    return {
      used: formatBytes(used),
      total: formatBytes(total),
      free: formatBytes(free)
    }
  })

  const formattedDisk = computed(() => {
    if (!systemStats.value?.disk) return { used: '0', total: '0', free: '0' }
    const { used, total, free } = systemStats.value.disk
    return {
      used: formatBytes(used),
      total: formatBytes(total),
      free: formatBytes(free)
    }
  })

  const topProcesses = computed(() => {
    if (!systemStats.value?.processes) return []
    return systemStats.value.processes
      .sort((a, b) => b.cpu - a.cpu)
      .slice(0, 10)
  })

  // Utility functions
  const formatBytes = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    if (bytes === 0) return '0 B'
    const i = Math.floor(Math.log(bytes) / Math.log(1024))
    return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i]
  }

  const addToHistory = (value: number, history: number[]) => {
    history.push(value)
    if (history.length > maxHistoryPoints.value) {
      history.shift()
    }
  }

  // Message handlers
  const handleMonitorUpdate = (data: any) => {
    if (data.type === 'monitor_update') {
      isMonitoring.value = true
      isLoading.value = false
      error.value = null
      systemStats.value = data.payload
      dockerContainers.value = data.payload.dockerContainers || []
      networkInterfaces.value = data.payload.network?.interfaces || []
      processes.value = data.payload.processes || []
      
      // Update historical data
      if (data.payload.cpu) {
        addToHistory(data.payload.cpu.usage, cpuHistory.value)
      }
      if (data.payload.memory) {
        const memUsage = (data.payload.memory.used / data.payload.memory.total) * 100
        addToHistory(memUsage, memoryHistory.value)
      }
      if (data.payload.network?.interfaces?.length > 0) {
        const totalRx = data.payload.network.interfaces.reduce((sum: number, iface: any) => sum + (iface.rxBytes || 0), 0)
        const totalTx = data.payload.network.interfaces.reduce((sum: number, iface: any) => sum + (iface.txBytes || 0), 0)
        addToHistory(totalRx, networkHistory.value.rx)
        addToHistory(totalTx, networkHistory.value.tx)
      }
    }
  }

  const handleMonitorError = (data: any) => {
    isLoading.value = false
    isMonitoring.value = false
    setError(`Monitor error: ${data.message}`)
  }

  // Actions
  const show = () => {
    isVisible.value = true
    if (!systemStats.value && !isMonitoring.value) {
      startMonitoring()
    }
  }

  const hide = () => {
    isVisible.value = false
    if (isMonitoring.value) {
      stopMonitoring()
    }
  }

  const toggle = () => {
    if (isVisible.value) {
      hide()
    } else {
      show()
    }
  }

  const startMonitoring = () => {
    const terminalStore = useTerminalStore()
    const stompClient = (terminalStore as any).stompClient
    
    if (stompClient?.connected) {
      if (!systemStats.value) {
        isLoading.value = true
      }
      error.value = null
      
      stompClient.publish({
        destination: '/app/monitor/start',
        body: JSON.stringify({
          interval: updateInterval.value
        })
      })
    } else {
      setError('SSH连接未建立')
    }
  }

  const stopMonitoring = () => {
    const terminalStore = useTerminalStore()
    const stompClient = (terminalStore as any).stompClient
    
    if (stompClient?.connected) {
      stompClient.publish({
        destination: '/app/monitor/stop',
        body: JSON.stringify({})
      })
    }
    
    isMonitoring.value = false
    isLoading.value = false
  }

  const refreshData = () => {
    if (isMonitoring.value) {
      stopMonitoring()
      setTimeout(() => {
        startMonitoring()
      }, 500)
    } else {
      startMonitoring()
    }
  }

  const setUpdateInterval = (interval: number) => {
    updateInterval.value = interval
    if (isMonitoring.value) {
      // Restart monitoring with new interval
      stopMonitoring()
      setTimeout(() => {
        startMonitoring()
      }, 500)
    }
  }

  const clearHistory = () => {
    cpuHistory.value = []
    memoryHistory.value = []
    networkHistory.value = { rx: [], tx: [] }
  }

  const setError = (message: string) => {
    error.value = message
  }

  const clearError = () => {
    error.value = null
  }

  const reset = () => {
    isVisible.value = false
    isMonitoring.value = false
    isLoading.value = false
    error.value = null
    systemStats.value = null
    dockerContainers.value = []
    networkInterfaces.value = []
    processes.value = []
    clearHistory()
  }

  // Watch visibility changes to auto-start/stop monitoring
  watch(isVisible, (newValue) => {
    if (newValue && !isMonitoring.value) {
      startMonitoring()
    } else if (!newValue && isMonitoring.value) {
      stopMonitoring()
    }
  })

  return {
    // State
    isVisible,
    isMonitoring,
    isLoading,
    error,
    systemStats,
    dockerContainers,
    networkInterfaces,
    processes,
    cpuHistory,
    memoryHistory,
    networkHistory,
    maxHistoryPoints,
    updateInterval,
    
    // Computed
    loadingState,
    errorState,
    emptyState,
    cpuUsage,
    memoryUsage,
    diskUsage,
    formattedMemory,
    formattedDisk,
    topProcesses,
    
    // Actions
    show,
    hide,
    toggle,
    startMonitoring,
    stopMonitoring,
    refreshData,
    setUpdateInterval,
    clearHistory,
    setError,
    clearError,
    reset,
    
    // Message handlers (for STOMP integration)
    handleMonitorUpdate,
    handleMonitorError,
    
    // Utilities
    formatBytes
  }
})