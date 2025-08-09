/**
 * Performance optimization utilities
 */

/**
 * Debounce function to limit rate of function execution
 * @param {Function} func - Function to debounce
 * @param {number} wait - Delay in milliseconds
 * @param {boolean} immediate - Execute immediately on first call
 * @returns {Function} Debounced function
 */
export function debounce(func, wait, immediate = false) {
  let timeout
  let result

  const debounced = function(...args) {
    const context = this
    
    const later = function() {
      timeout = null
      if (!immediate) {
        result = func.apply(context, args)
      }
    }

    const callNow = immediate && !timeout
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)

    if (callNow) {
      result = func.apply(context, args)
    }

    return result
  }

  debounced.cancel = function() {
    clearTimeout(timeout)
    timeout = null
  }

  debounced.flush = function() {
    if (timeout) {
      clearTimeout(timeout)
      timeout = null
      return func.apply(this, arguments)
    }
  }

  return debounced
}

/**
 * Throttle function to limit rate of function execution
 * @param {Function} func - Function to throttle
 * @param {number} wait - Delay in milliseconds
 * @param {Object} options - Options
 * @returns {Function} Throttled function
 */
export function throttle(func, wait, options = {}) {
  let timeout
  let previous = 0
  let result

  const throttled = function(...args) {
    const context = this
    const now = Date.now()
    
    if (!previous && options.leading === false) {
      previous = now
    }

    const remaining = wait - (now - previous)

    if (remaining <= 0 || remaining > wait) {
      if (timeout) {
        clearTimeout(timeout)
        timeout = null
      }
      previous = now
      result = func.apply(context, args)
    } else if (!timeout && options.trailing !== false) {
      timeout = setTimeout(() => {
        previous = options.leading === false ? 0 : Date.now()
        timeout = null
        result = func.apply(context, args)
      }, remaining)
    }

    return result
  }

  throttled.cancel = function() {
    clearTimeout(timeout)
    timeout = null
    previous = 0
  }

  return throttled
}

/**
 * Request animation frame based throttle for smooth animations
 * @param {Function} func - Function to throttle
 * @returns {Function} RAF throttled function
 */
export function rafThrottle(func) {
  let rafId = null
  let latestArgs = null

  return function(...args) {
    latestArgs = args
    
    if (rafId === null) {
      rafId = requestAnimationFrame(() => {
        func.apply(this, latestArgs)
        rafId = null
        latestArgs = null
      })
    }
  }
}

/**
 * Memoization for expensive function calls
 * @param {Function} func - Function to memoize
 * @param {Function} keyResolver - Function to resolve cache key
 * @returns {Function} Memoized function
 */
export function memoize(func, keyResolver) {
  const cache = new Map()

  const memoized = function(...args) {
    const key = keyResolver ? keyResolver(...args) : JSON.stringify(args)
    
    if (cache.has(key)) {
      return cache.get(key)
    }

    const result = func.apply(this, args)
    cache.set(key, result)
    return result
  }

  memoized.cache = cache
  memoized.clear = () => cache.clear()
  memoized.delete = (key) => cache.delete(key)

  return memoized
}

/**
 * Lazy loading with Intersection Observer
 * @param {HTMLElement} element - Element to observe
 * @param {Function} callback - Callback when element is visible
 * @param {Object} options - Intersection Observer options
 * @returns {Function} Cleanup function
 */
export function lazyLoad(element, callback, options = {}) {
  const defaultOptions = {
    root: null,
    rootMargin: '50px',
    threshold: 0.1
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        callback(entry.target)
        observer.unobserve(entry.target)
      }
    })
  }, { ...defaultOptions, ...options })

  observer.observe(element)

  // Return cleanup function
  return () => observer.disconnect()
}

/**
 * Virtual scrolling helper for large lists
 */
export class VirtualScrollManager {
  constructor(options = {}) {
    this.itemHeight = options.itemHeight || 50
    this.containerHeight = options.containerHeight || 400
    this.buffer = options.buffer || 5
    this.items = options.items || []
    this.scrollTop = 0
    
    this.visibleCount = Math.ceil(this.containerHeight / this.itemHeight)
    this.totalHeight = this.items.length * this.itemHeight
  }

  updateScrollTop(scrollTop) {
    this.scrollTop = scrollTop
  }

  getVisibleRange() {
    const startIndex = Math.floor(this.scrollTop / this.itemHeight)
    const endIndex = Math.min(
      startIndex + this.visibleCount + this.buffer,
      this.items.length
    )
    
    return {
      startIndex: Math.max(0, startIndex - this.buffer),
      endIndex,
      offsetY: Math.max(0, startIndex - this.buffer) * this.itemHeight
    }
  }

  getVisibleItems() {
    const { startIndex, endIndex } = this.getVisibleRange()
    return this.items.slice(startIndex, endIndex).map((item, index) => ({
      item,
      index: startIndex + index,
      key: startIndex + index
    }))
  }
}

/**
 * Image loading with progressive enhancement
 * @param {string} src - Image source URL
 * @param {Object} options - Loading options
 * @returns {Promise<HTMLImageElement>} Loaded image element
 */
export function loadImage(src, options = {}) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    
    if (options.crossOrigin) {
      img.crossOrigin = options.crossOrigin
    }

    img.onload = () => resolve(img)
    img.onerror = reject
    
    // Support for responsive images
    if (options.srcset) {
      img.srcset = options.srcset
    }
    
    if (options.sizes) {
      img.sizes = options.sizes
    }

    img.src = src
  })
}

/**
 * Preload resources for better performance
 * @param {string[]} urls - Array of URLs to preload
 * @param {string} type - Resource type ('image', 'script', 'style', etc.)
 * @returns {Promise<void[]>} Promise that resolves when all resources are loaded
 */
export function preloadResources(urls, type = 'image') {
  const promises = urls.map(url => {
    return new Promise((resolve, reject) => {
      const link = document.createElement('link')
      link.rel = 'preload'
      link.as = type
      link.href = url
      link.onload = resolve
      link.onerror = reject
      
      document.head.appendChild(link)
    })
  })

  return Promise.allSettled(promises)
}

/**
 * Chunk array for batch processing
 * @param {Array} array - Array to chunk
 * @param {number} size - Chunk size
 * @returns {Array[]} Array of chunks
 */
export function chunk(array, size) {
  const chunks = []
  for (let i = 0; i < array.length; i += size) {
    chunks.push(array.slice(i, i + size))
  }
  return chunks
}

/**
 * Process array in batches with delays to prevent blocking
 * @param {Array} items - Items to process
 * @param {Function} processor - Function to process each item
 * @param {Object} options - Processing options
 * @returns {Promise} Promise that resolves when all items are processed
 */
export async function processBatch(items, processor, options = {}) {
  const {
    batchSize = 10,
    delay = 0,
    onProgress = null
  } = options

  const chunks = chunk(items, batchSize)
  const results = []

  for (let i = 0; i < chunks.length; i++) {
    const chunkResults = await Promise.all(
      chunks[i].map(processor)
    )
    
    results.push(...chunkResults)
    
    if (onProgress) {
      onProgress({
        processed: (i + 1) * batchSize,
        total: items.length,
        percentage: Math.round(((i + 1) * batchSize / items.length) * 100)
      })
    }

    // Add delay between batches to prevent blocking
    if (delay > 0 && i < chunks.length - 1) {
      await new Promise(resolve => setTimeout(resolve, delay))
    }
  }

  return results
}

/**
 * Measure function execution time
 * @param {Function} func - Function to measure
 * @param {string} label - Label for the measurement
 * @returns {Function} Wrapped function that logs execution time
 */
export function measurePerformance(func, label) {
  return function(...args) {
    const start = performance.now()
    const result = func.apply(this, args)
    const end = performance.now()
    
    console.log(`${label} took ${end - start} milliseconds`)
    return result
  }
}

/**
 * Memory usage monitoring
 */
export class MemoryMonitor {
  static isSupported() {
    return 'memory' in performance
  }

  static getCurrentUsage() {
    if (!this.isSupported()) {
      return null
    }

    return {
      used: performance.memory.usedJSHeapSize,
      total: performance.memory.totalJSHeapSize,
      limit: performance.memory.jsHeapSizeLimit
    }
  }

  static formatBytes(bytes) {
    if (bytes === 0) return '0 B'
    
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  static logUsage(label = 'Memory Usage') {
    const usage = this.getCurrentUsage()
    if (usage) {
      console.log(`${label}:`, {
        used: this.formatBytes(usage.used),
        total: this.formatBytes(usage.total),
        limit: this.formatBytes(usage.limit),
        percentage: Math.round((usage.used / usage.total) * 100) + '%'
      })
    }
  }
}