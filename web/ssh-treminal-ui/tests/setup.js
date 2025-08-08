/**
 * Jest测试环境设置
 * 
 * 配置测试环境，包括全局mocks、工具方法等
 */

// Mock console methods to reduce test noise
global.console = {
  ...console,
  debug: jest.fn(),
  log: jest.fn(),
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn()
};

// Mock performance API
global.performance = {
  now: jest.fn(() => Date.now())
};

// Mock URL methods
global.URL = {
  createObjectURL: jest.fn(() => 'blob:mock-url'),
  revokeObjectURL: jest.fn()
};

// Setup fetch mock defaults
beforeEach(() => {
  // Reset all mocks before each test
  jest.clearAllMocks();
  
  // Clear any existing timers
  jest.clearAllTimers();
});

// Cleanup after each test
afterEach(() => {
  jest.restoreAllMocks();
});