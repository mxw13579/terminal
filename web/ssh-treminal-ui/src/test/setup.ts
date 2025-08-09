import { config } from '@vue/test-utils'
import { beforeEach } from 'vitest'

// Global test setup
beforeEach(() => {
  // Reset any global state before each test
})

// Mock WebSocket for testing
global.WebSocket = class MockWebSocket {
  constructor(url: string) {
    // Mock WebSocket implementation
  }
  
  close() {}
  send(data: string) {}
  
  onopen: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  
  readyState: number = WebSocket.CONNECTING
  
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
}

// Mock STOMP client
vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(() => ({
    activate: vi.fn(),
    deactivate: vi.fn(),
    publish: vi.fn(),
    subscribe: vi.fn(),
    onConnect: vi.fn(),
    onDisconnect: vi.fn(),
    onStompError: vi.fn()
  }))
}))

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn()
}))

// Setup global config for Vue Test Utils
config.global.mocks = {
  $t: (key: string) => key // Mock i18n if needed
}