import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SshConsole from '@/components/SshConsole.vue'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'

// Mock xterm modules
vi.mock('xterm', () => ({
  Terminal: vi.fn(() => ({
    open: vi.fn(),
    write: vi.fn(),
    clear: vi.fn(),
    dispose: vi.fn(),
    onData: vi.fn(),
    onResize: vi.fn(),
    focus: vi.fn()
  }))
}))

vi.mock('xterm-addon-fit', () => ({
  FitAddon: vi.fn(() => ({
    activate: vi.fn(),
    fit: vi.fn()
  }))
}))

describe('SshConsole', () => {
  let wrapper
  let mockTerminal
  let mockFitAddon

  beforeEach(() => {
    mockTerminal = {
      open: vi.fn(),
      write: vi.fn(),
      clear: vi.fn(),
      dispose: vi.fn(),
      onData: vi.fn(),
      onResize: vi.fn(),
      focus: vi.fn(),
      loadAddon: vi.fn()
    }

    mockFitAddon = {
      activate: vi.fn(),
      fit: vi.fn()
    }

    Terminal.mockReturnValue(mockTerminal)
    FitAddon.mockReturnValue(mockFitAddon)

    wrapper = mount(SshConsole, {
      props: {
        connectionId: 'test-connection-123'
      }
    })
  })

  it('renders correctly', () => {
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.ssh-console').exists()).toBe(true)
    expect(wrapper.find('.terminal-container').exists()).toBe(true)
  })

  it('initializes terminal on mount', () => {
    expect(Terminal).toHaveBeenCalledWith({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
      theme: {
        background: '#1e1e1e',
        foreground: '#d4d4d4'
      }
    })

    expect(mockTerminal.loadAddon).toHaveBeenCalledWith(mockFitAddon)
    expect(mockTerminal.open).toHaveBeenCalled()
  })

  it('handles terminal data input', async () => {
    const testData = 'ls -la\r'
    
    // Simulate user input
    const dataHandler = mockTerminal.onData.mock.calls[0][0]
    dataHandler(testData)

    // Should emit terminal-input event
    expect(wrapper.emitted('terminal-input')).toBeTruthy()
    expect(wrapper.emitted('terminal-input')[0][0]).toBe(testData)
  })

  it('writes data to terminal when receiving output', async () => {
    const testOutput = 'total 0\ndrwxr-xr-x 2 user user 4096 Jan 1 12:00 test\n'
    
    await wrapper.vm.writeToTerminal(testOutput)
    
    expect(mockTerminal.write).toHaveBeenCalledWith(testOutput)
  })

  it('clears terminal when clear method is called', async () => {
    await wrapper.vm.clearTerminal()
    
    expect(mockTerminal.clear).toHaveBeenCalled()
  })

  it('fits terminal to container size', async () => {
    await wrapper.vm.fitTerminal()
    
    expect(mockFitAddon.fit).toHaveBeenCalled()
  })

  it('handles resize events', async () => {
    const resizeHandler = mockTerminal.onResize.mock.calls[0][0]
    const newSize = { cols: 80, rows: 24 }
    
    resizeHandler(newSize)
    
    expect(wrapper.emitted('terminal-resize')).toBeTruthy()
    expect(wrapper.emitted('terminal-resize')[0][0]).toEqual(newSize)
  })

  it('focuses terminal when focus method is called', async () => {
    await wrapper.vm.focusTerminal()
    
    expect(mockTerminal.focus).toHaveBeenCalled()
  })

  it('disposes terminal on unmount', () => {
    wrapper.unmount()
    
    expect(mockTerminal.dispose).toHaveBeenCalled()
  })

  it('handles connection status changes', async () => {
    await wrapper.setProps({ connected: false })
    
    expect(wrapper.find('.connection-status.disconnected').exists()).toBe(true)
    expect(wrapper.text()).toContain('Disconnected')
    
    await wrapper.setProps({ connected: true })
    
    expect(wrapper.find('.connection-status.connected').exists()).toBe(true)
    expect(wrapper.text()).toContain('Connected')
  })

  it('shows loading state when connecting', async () => {
    await wrapper.setProps({ connecting: true })
    
    expect(wrapper.find('.loading-overlay').exists()).toBe(true)
    expect(wrapper.text()).toContain('Connecting...')
  })

  it('handles terminal theme switching', async () => {
    await wrapper.vm.setTheme('light')
    
    // Should update terminal theme
    expect(mockTerminal.options).toBeDefined()
  })

  it('handles font size changes', async () => {
    await wrapper.vm.setFontSize(16)
    
    expect(wrapper.vm.fontSize).toBe(16)
    expect(mockFitAddon.fit).toHaveBeenCalled()
  })

  it('emits connection events correctly', async () => {
    // Simulate connection established
    await wrapper.vm.onConnectionEstablished()
    
    expect(wrapper.emitted('connection-established')).toBeTruthy()
    
    // Simulate connection lost
    await wrapper.vm.onConnectionLost()
    
    expect(wrapper.emitted('connection-lost')).toBeTruthy()
  })

  it('handles terminal error states', async () => {
    const errorMessage = 'Connection failed'
    
    await wrapper.vm.showError(errorMessage)
    
    expect(wrapper.find('.error-message').exists()).toBe(true)
    expect(wrapper.text()).toContain(errorMessage)
  })

  it('manages terminal history', async () => {
    const commands = ['ls -la', 'pwd', 'whoami']
    
    for (const command of commands) {
      await wrapper.vm.addToHistory(command)
    }
    
    expect(wrapper.vm.commandHistory).toEqual(commands)
    expect(wrapper.vm.commandHistory.length).toBe(3)
  })

  it('handles copy and paste operations', async () => {
    // Mock clipboard API
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(),
        readText: vi.fn().mockResolvedValue('test content')
      }
    })
    
    // Test copy
    await wrapper.vm.copySelection()
    expect(navigator.clipboard.writeText).toHaveBeenCalled()
    
    // Test paste
    await wrapper.vm.pasteFromClipboard()
    expect(navigator.clipboard.readText).toHaveBeenCalled()
  })

  it('handles keyboard shortcuts', async () => {
    const terminalElement = wrapper.find('.terminal-container')
    
    // Test Ctrl+C
    await terminalElement.trigger('keydown', {
      key: 'c',
      ctrlKey: true
    })
    
    expect(wrapper.emitted('terminal-input')).toBeTruthy()
    
    // Test Ctrl+V
    await terminalElement.trigger('keydown', {
      key: 'v',
      ctrlKey: true
    })
    
    // Should trigger paste operation
    expect(wrapper.vm.pasteFromClipboard).toBeDefined()
  })
})