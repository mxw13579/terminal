// 优化的状态管理 Composable
import { ref, computed, reactive, watch, onUnmounted } from 'vue'

/**
 * 创建响应式状态管理器
 * @param {Object} initialState 初始状态
 * @param {Object} options 配置选项
 */
export function createReactiveState(initialState = {}, options = {}) {
  const {
    persist = false,
    persistKey = 'app-state',
    validateState = null,
    onStateChange = null
  } = options

  // 从localStorage恢复状态
  const loadPersistedState = () => {
    if (!persist) return initialState
    
    try {
      const saved = localStorage.getItem(persistKey)
      if (!saved) return initialState
      
      const parsed = JSON.parse(saved)
      
      // 状态验证
      if (typeof validateState === 'function') {
        return validateState(parsed) ? { ...initialState, ...parsed } : initialState
      }
      
      return { ...initialState, ...parsed }
    } catch (error) {
      console.warn(`Failed to load persisted state for ${persistKey}:`, error)
      return initialState
    }
  }

  // 创建响应式状态
  const state = reactive(loadPersistedState())

  // 状态持久化
  const persistState = () => {
    if (!persist) return
    
    try {
      localStorage.setItem(persistKey, JSON.stringify(state))
    } catch (error) {
      console.warn(`Failed to persist state for ${persistKey}:`, error)
    }
  }

  // 监听状态变化
  if (persist) {
    watch(state, persistState, { deep: true })
  }

  if (typeof onStateChange === 'function') {
    watch(state, onStateChange, { deep: true })
  }

  // 状态管理方法
  const setState = (newState) => {
    Object.assign(state, newState)
  }

  const resetState = () => {
    Object.assign(state, initialState)
  }

  const mergeState = (partialState) => {
    Object.keys(partialState).forEach(key => {
      if (typeof partialState[key] === 'object' && partialState[key] !== null) {
        if (typeof state[key] === 'object' && state[key] !== null) {
          Object.assign(state[key], partialState[key])
        } else {
          state[key] = partialState[key]
        }
      } else {
        state[key] = partialState[key]
      }
    })
  }

  return {
    state,
    setState,
    resetState,
    mergeState
  }
}

/**
 * 创建异步状态管理器
 * @param {Function} asyncFunction 异步函数
 * @param {Object} options 配置选项
 */
export function useAsyncState(asyncFunction, options = {}) {
  const {
    immediate = false,
    resetOnExecute = true,
    shallow = false,
    delay = 0,
    timeout = 0,
    retry = 0,
    retryDelay = 1000
  } = options

  const state = reactive({
    data: null,
    error: null,
    loading: false,
    executed: false
  })

  const retryCount = ref(0)

  // 执行异步函数
  const execute = async (...args) => {
    if (state.loading) return state.data

    if (resetOnExecute) {
      state.data = null
      state.error = null
    }

    state.loading = true
    state.executed = true

    let result = null

    try {
      // 延迟执行
      if (delay > 0) {
        await new Promise(resolve => setTimeout(resolve, delay))
      }

      // 超时处理
      if (timeout > 0) {
        const timeoutPromise = new Promise((_, reject) => {
          setTimeout(() => reject(new Error('Request timeout')), timeout)
        })
        result = await Promise.race([asyncFunction(...args), timeoutPromise])
      } else {
        result = await asyncFunction(...args)
      }

      state.data = shallow ? result : JSON.parse(JSON.stringify(result))
      state.error = null
      retryCount.value = 0

    } catch (error) {
      console.error('Async state error:', error)
      
      // 重试逻辑
      if (retryCount.value < retry) {
        retryCount.value++
        await new Promise(resolve => setTimeout(resolve, retryDelay))
        return execute(...args)
      }

      state.error = error
      state.data = null
    } finally {
      state.loading = false
    }

    return state.data
  }

  // 重置状态
  const reset = () => {
    state.data = null
    state.error = null
    state.loading = false
    state.executed = false
    retryCount.value = 0
  }

  // 立即执行
  if (immediate) {
    execute()
  }

  return {
    ...state,
    execute,
    reset,
    isReady: computed(() => state.executed && !state.loading),
    isSuccess: computed(() => state.executed && !state.loading && !state.error),
    isError: computed(() => state.executed && !state.loading && !!state.error)
  }
}

/**
 * 创建缓存管理器
 * @param {Object} options 配置选项
 */
export function createCache(options = {}) {
  const {
    maxSize = 100,
    ttl = 5 * 60 * 1000, // 5分钟默认过期时间
    serialize = JSON.stringify,
    deserialize = JSON.parse
  } = options

  const cache = new Map()
  const timers = new Map()

  // 获取缓存
  const get = (key) => {
    const item = cache.get(key)
    if (!item) return null

    // 检查是否过期
    if (item.expires && Date.now() > item.expires) {
      remove(key)
      return null
    }

    return item.data
  }

  // 设置缓存
  const set = (key, data, customTtl = ttl) => {
    // 限制缓存大小
    if (cache.size >= maxSize) {
      const firstKey = cache.keys().next().value
      remove(firstKey)
    }

    const expires = customTtl > 0 ? Date.now() + customTtl : null

    cache.set(key, {
      data: serialize ? serialize(data) : data,
      expires,
      created: Date.now()
    })

    // 设置过期定时器
    if (expires) {
      const timer = setTimeout(() => remove(key), customTtl)
      timers.set(key, timer)
    }

    return data
  }

  // 移除缓存
  const remove = (key) => {
    cache.delete(key)
    
    const timer = timers.get(key)
    if (timer) {
      clearTimeout(timer)
      timers.delete(key)
    }
  }

  // 清空缓存
  const clear = () => {
    timers.forEach(timer => clearTimeout(timer))
    timers.clear()
    cache.clear()
  }

  // 检查是否存在
  const has = (key) => {
    const item = cache.get(key)
    if (!item) return false

    if (item.expires && Date.now() > item.expires) {
      remove(key)
      return false
    }

    return true
  }

  // 获取缓存统计信息
  const stats = computed(() => ({
    size: cache.size,
    keys: Array.from(cache.keys()),
    maxSize
  }))

  // 清理过期缓存
  const cleanup = () => {
    const now = Date.now()
    for (const [key, item] of cache.entries()) {
      if (item.expires && now > item.expires) {
        remove(key)
      }
    }
  }

  // 定期清理
  const cleanupInterval = setInterval(cleanup, 60000) // 每分钟清理一次

  onUnmounted(() => {
    clearInterval(cleanupInterval)
    clear()
  })

  return {
    get,
    set,
    remove,
    clear,
    has,
    stats,
    cleanup
  }
}

/**
 * 创建事件总线
 */
export function createEventBus() {
  const events = new Map()

  const on = (event, callback) => {
    if (!events.has(event)) {
      events.set(event, new Set())
    }
    events.get(event).add(callback)

    // 返回取消函数
    return () => off(event, callback)
  }

  const off = (event, callback) => {
    const callbacks = events.get(event)
    if (callbacks) {
      callbacks.delete(callback)
      if (callbacks.size === 0) {
        events.delete(event)
      }
    }
  }

  const emit = (event, ...args) => {
    const callbacks = events.get(event)
    if (callbacks) {
      callbacks.forEach(callback => {
        try {
          callback(...args)
        } catch (error) {
          console.error(`Event handler error for "${event}":`, error)
        }
      })
    }
  }

  const once = (event, callback) => {
    const wrappedCallback = (...args) => {
      off(event, wrappedCallback)
      callback(...args)
    }
    return on(event, wrappedCallback)
  }

  const clear = (event) => {
    if (event) {
      events.delete(event)
    } else {
      events.clear()
    }
  }

  return {
    on,
    off,
    emit,
    once,
    clear
  }
}

/**
 * 防抖Composable
 * @param {Function} fn 要防抖的函数
 * @param {Number} delay 延迟时间
 */
export function useDebounceFn(fn, delay = 300) {
  let timeoutId = null

  const debouncedFn = (...args) => {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn(...args), delay)
  }

  const cancel = () => {
    clearTimeout(timeoutId)
    timeoutId = null
  }

  const flush = (...args) => {
    cancel()
    fn(...args)
  }

  onUnmounted(cancel)

  return {
    debouncedFn,
    cancel,
    flush
  }
}

/**
 * 节流Composable
 * @param {Function} fn 要节流的函数
 * @param {Number} delay 节流间隔
 */
export function useThrottleFn(fn, delay = 300) {
  let lastExecTime = 0
  let timeoutId = null

  const throttledFn = (...args) => {
    const currentTime = Date.now()

    if (currentTime - lastExecTime > delay) {
      lastExecTime = currentTime
      fn(...args)
    } else if (!timeoutId) {
      timeoutId = setTimeout(() => {
        lastExecTime = Date.now()
        timeoutId = null
        fn(...args)
      }, delay - (currentTime - lastExecTime))
    }
  }

  const cancel = () => {
    clearTimeout(timeoutId)
    timeoutId = null
  }

  onUnmounted(cancel)

  return {
    throttledFn,
    cancel
  }
}