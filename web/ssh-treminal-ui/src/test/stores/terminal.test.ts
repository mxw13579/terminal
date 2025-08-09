import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { useTerminalStore } from '@/stores/terminal'

describe('Terminal Store', () => {
  let store: ReturnType<typeof useTerminalStore>
  
  beforeEach(() => {
    const app = createApp({})
    app.use(createPinia())
    store = useTerminalStore()
  })

  it('should initialize with default state', () => {
    expect(store.isConnected).toBe(false)
    expect(store.connectionInfo).toBeNull()
    expect(store.terminalOutput).toBe('')
  })

  it('should handle connection establishment', () => {
    const mockConnectionInfo = {
      host: 'localhost',
      port: 22,
      username: 'test'
    }
    
    store.setConnectionInfo(mockConnectionInfo)
    expect(store.connectionInfo).toEqual(mockConnectionInfo)
  })

  it('should handle terminal output', () => {
    const output = 'Hello, terminal!'
    store.appendOutput(output)
    expect(store.terminalOutput).toContain(output)
  })

  it('should clear output', () => {
    store.appendOutput('Some output')
    store.clearOutput()
    expect(store.terminalOutput).toBe('')
  })
})