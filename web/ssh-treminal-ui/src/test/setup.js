// Test setup file
import { config } from '@vue/test-utils'

// Mock Element Plus components globally
config.global.stubs = {
  'el-button': true,
  'el-input': true,
  'el-table': true,
  'el-table-column': true,
  'el-dialog': true,
  'el-form': true,
  'el-form-item': true,
  'el-select': true,
  'el-option': true,
  'el-card': true,
  'el-tag': true,
  'el-loading': true,
  'el-message': true,
  'el-progress': true
}

// Mock global properties
config.global.mocks = {
  $message: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  },
  $loading: vi.fn(() => ({
    close: vi.fn()
  })),
  $confirm: vi.fn()
}

// Mock axios
vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
    })),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

// Mock WebSocket and STOMP
global.WebSocket = vi.fn()
vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(() => ({
    activate: vi.fn(),
    deactivate: vi.fn(),
    publish: vi.fn(),
    subscribe: vi.fn()
  }))
}))

// Mock xterm.js
vi.mock('xterm', () => ({
  Terminal: vi.fn(() => ({
    open: vi.fn(),
    write: vi.fn(),
    clear: vi.fn(),
    dispose: vi.fn(),
    onData: vi.fn(),
    onResize: vi.fn()
  }))
}))

vi.mock('xterm-addon-fit', () => ({
  FitAddon: vi.fn(() => ({
    activate: vi.fn(),
    fit: vi.fn()
  }))
}))