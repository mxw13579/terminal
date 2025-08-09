/**
 * Accessibility utilities
 */

/**
 * Generate a unique ID for form elements
 * @param {string} prefix - Prefix for the ID
 * @returns {string} Unique ID
 */
export function generateId(prefix = 'element') {
  return `${prefix}-${Math.random().toString(36).substr(2, 9)}`
}

/**
 * Check if an element is focusable
 * @param {HTMLElement} element - Element to check
 * @returns {boolean} True if element is focusable
 */
export function isFocusable(element) {
  if (!element || element.disabled || element.hidden) {
    return false
  }

  const focusableSelectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    '[contenteditable="true"]'
  ]

  return focusableSelectors.some(selector => {
    try {
      return element.matches(selector)
    } catch (e) {
      return false
    }
  })
}

/**
 * Get all focusable elements within a container
 * @param {HTMLElement} container - Container element
 * @returns {HTMLElement[]} Array of focusable elements
 */
export function getFocusableElements(container) {
  if (!container) return []

  const selector = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    '[contenteditable="true"]'
  ].join(', ')

  const elements = Array.from(container.querySelectorAll(selector))
  return elements.filter(element => {
    return isFocusable(element) && isVisible(element)
  })
}

/**
 * Check if an element is visible
 * @param {HTMLElement} element - Element to check
 * @returns {boolean} True if element is visible
 */
export function isVisible(element) {
  if (!element) return false

  const style = window.getComputedStyle(element)
  return (
    style.display !== 'none' &&
    style.visibility !== 'hidden' &&
    style.opacity !== '0' &&
    element.offsetWidth > 0 &&
    element.offsetHeight > 0
  )
}

/**
 * Focus trap for modals and dropdowns
 * @param {KeyboardEvent} event - Keyboard event
 * @param {HTMLElement} container - Container element
 */
export function trapFocus(event, container) {
  if (event.key !== 'Tab') return

  const focusableElements = getFocusableElements(container)
  if (focusableElements.length === 0) return

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]

  if (event.shiftKey) {
    if (document.activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
    }
  } else {
    if (document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }
}

/**
 * Announce text to screen readers
 * @param {string} message - Message to announce
 * @param {string} priority - Priority level ('polite' or 'assertive')
 */
export function announceToScreenReader(message, priority = 'polite') {
  const announcer = document.createElement('div')
  announcer.setAttribute('aria-live', priority)
  announcer.setAttribute('aria-atomic', 'true')
  announcer.setAttribute('class', 'sr-only')
  announcer.textContent = message

  document.body.appendChild(announcer)

  // Remove after a short delay
  setTimeout(() => {
    document.body.removeChild(announcer)
  }, 1000)
}

/**
 * Get accessible color contrast ratio
 * @param {string} foreground - Foreground color (hex)
 * @param {string} background - Background color (hex)
 * @returns {number} Contrast ratio
 */
export function getContrastRatio(foreground, background) {
  const getLuminance = (color) => {
    // Convert hex to RGB
    const hex = color.replace('#', '')
    const r = parseInt(hex.substr(0, 2), 16) / 255
    const g = parseInt(hex.substr(2, 2), 16) / 255
    const b = parseInt(hex.substr(4, 2), 16) / 255

    // Calculate relative luminance
    const sRGB = [r, g, b].map(c => {
      if (c <= 0.03928) {
        return c / 12.92
      }
      return Math.pow((c + 0.055) / 1.055, 2.4)
    })

    return 0.2126 * sRGB[0] + 0.7152 * sRGB[1] + 0.0722 * sRGB[2]
  }

  const fgLum = getLuminance(foreground)
  const bgLum = getLuminance(background)
  const lighter = Math.max(fgLum, bgLum)
  const darker = Math.min(fgLum, bgLum)

  return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Check if color combination meets WCAG contrast requirements
 * @param {string} foreground - Foreground color (hex)
 * @param {string} background - Background color (hex)
 * @param {string} level - WCAG level ('AA' or 'AAA')
 * @param {string} size - Text size ('normal' or 'large')
 * @returns {boolean} True if contrast meets requirements
 */
export function meetsContrastRequirement(foreground, background, level = 'AA', size = 'normal') {
  const ratio = getContrastRatio(foreground, background)
  
  const requirements = {
    AA: { normal: 4.5, large: 3 },
    AAA: { normal: 7, large: 4.5 }
  }

  return ratio >= requirements[level][size]
}

/**
 * Detect user preferences
 */
export class AccessibilityPreferences {
  static prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  }

  static prefersHighContrast() {
    return window.matchMedia('(prefers-contrast: high)').matches
  }

  static prefersDark() {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  static onPreferenceChange(callback) {
    const mediaQueries = [
      '(prefers-reduced-motion: reduce)',
      '(prefers-contrast: high)',
      '(prefers-color-scheme: dark)'
    ]

    const listeners = mediaQueries.map(query => {
      const mq = window.matchMedia(query)
      const handler = () => callback(query, mq.matches)
      mq.addEventListener('change', handler)
      return { mq, handler }
    })

    // Return cleanup function
    return () => {
      listeners.forEach(({ mq, handler }) => {
        mq.removeEventListener('change', handler)
      })
    }
  }
}

/**
 * Keyboard navigation helpers
 */
export const KeyCodes = {
  ESCAPE: 'Escape',
  ENTER: 'Enter',
  SPACE: ' ',
  TAB: 'Tab',
  ARROW_UP: 'ArrowUp',
  ARROW_DOWN: 'ArrowDown',
  ARROW_LEFT: 'ArrowLeft',
  ARROW_RIGHT: 'ArrowRight',
  HOME: 'Home',
  END: 'End',
  PAGE_UP: 'PageUp',
  PAGE_DOWN: 'PageDown'
}

/**
 * Handle arrow key navigation in lists
 * @param {KeyboardEvent} event - Keyboard event
 * @param {HTMLElement[]} items - Array of items to navigate
 * @param {number} currentIndex - Current focused item index
 * @param {boolean} loop - Whether to loop around at ends
 * @returns {number} New index
 */
export function handleArrowNavigation(event, items, currentIndex, loop = true) {
  if (!items.length) return currentIndex

  let newIndex = currentIndex

  switch (event.key) {
    case KeyCodes.ARROW_UP:
      event.preventDefault()
      newIndex = currentIndex > 0 ? currentIndex - 1 : (loop ? items.length - 1 : 0)
      break
    case KeyCodes.ARROW_DOWN:
      event.preventDefault()
      newIndex = currentIndex < items.length - 1 ? currentIndex + 1 : (loop ? 0 : items.length - 1)
      break
    case KeyCodes.HOME:
      event.preventDefault()
      newIndex = 0
      break
    case KeyCodes.END:
      event.preventDefault()
      newIndex = items.length - 1
      break
  }

  if (newIndex !== currentIndex && items[newIndex]) {
    items[newIndex].focus()
  }

  return newIndex
}

/**
 * Create accessible live region for dynamic content
 * @param {string} id - Unique ID for the live region
 * @param {'polite'|'assertive'} priority - Announcement priority
 * @returns {HTMLElement} Live region element
 */
export function createLiveRegion(id, priority = 'polite') {
  let region = document.getElementById(id)
  
  if (!region) {
    region = document.createElement('div')
    region.id = id
    region.setAttribute('aria-live', priority)
    region.setAttribute('aria-atomic', 'true')
    region.className = 'sr-only'
    document.body.appendChild(region)
  }

  return region
}