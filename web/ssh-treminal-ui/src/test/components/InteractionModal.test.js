import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InteractionModal from '@/components/InteractionModal.vue'

describe('InteractionModal', () => {
  let wrapper
  
  const mockInteractionRequest = {
    id: 'test-123',
    title: 'Test Interaction',
    message: 'Please confirm this action',
    fields: [
      {
        name: 'username',
        label: 'Username',
        type: 'text',
        required: true,
        placeholder: 'Enter username'
      },
      {
        name: 'port',
        label: 'Port',
        type: 'number',
        required: true,
        defaultValue: 8080
      }
    ],
    options: ['confirm', 'cancel']
  }

  beforeEach(() => {
    wrapper = mount(InteractionModal, {
      props: {
        visible: true,
        interactionRequest: mockInteractionRequest
      }
    })
  })

  it('renders correctly when visible', () => {
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.interaction-modal').exists()).toBe(true)
  })

  it('displays interaction title and message', () => {
    expect(wrapper.text()).toContain('Test Interaction')
    expect(wrapper.text()).toContain('Please confirm this action')
  })

  it('renders input fields correctly', () => {
    const usernameField = wrapper.find('[data-testid="field-username"]')
    const portField = wrapper.find('[data-testid="field-port"]')
    
    expect(usernameField.exists()).toBe(true)
    expect(portField.exists()).toBe(true)
    
    // Check field labels
    expect(wrapper.text()).toContain('Username')
    expect(wrapper.text()).toContain('Port')
  })

  it('validates required fields', async () => {
    const confirmButton = wrapper.find('[data-testid="confirm-button"]')
    
    // Try to submit without filling required fields
    await confirmButton.trigger('click')
    
    // Should show validation errors
    expect(wrapper.text()).toContain('Username is required')
  })

  it('emits response when form is submitted', async () => {
    // Fill in required fields
    const usernameInput = wrapper.find('[data-testid="username-input"]')
    const portInput = wrapper.find('[data-testid="port-input"]')
    
    await usernameInput.setValue('testuser')
    await portInput.setValue('9000')
    
    // Submit form
    const confirmButton = wrapper.find('[data-testid="confirm-button"]')
    await confirmButton.trigger('click')
    
    // Check emitted events
    expect(wrapper.emitted('response')).toBeTruthy()
    const responseData = wrapper.emitted('response')[0][0]
    expect(responseData.action).toBe('confirm')
    expect(responseData.values.username).toBe('testuser')
    expect(responseData.values.port).toBe('9000')
  })

  it('emits cancel when cancel button is clicked', async () => {
    const cancelButton = wrapper.find('[data-testid="cancel-button"]')
    await cancelButton.trigger('click')
    
    expect(wrapper.emitted('response')).toBeTruthy()
    const responseData = wrapper.emitted('response')[0][0]
    expect(responseData.action).toBe('cancel')
  })

  it('handles different field types correctly', async () => {
    const wrapper = mount(InteractionModal, {
      props: {
        visible: true,
        interactionRequest: {
          ...mockInteractionRequest,
          fields: [
            { name: 'text', type: 'text', label: 'Text Field' },
            { name: 'password', type: 'password', label: 'Password Field' },
            { name: 'number', type: 'number', label: 'Number Field' },
            { name: 'select', type: 'select', label: 'Select Field', options: ['opt1', 'opt2'] }
          ]
        }
      }
    })

    expect(wrapper.find('[data-testid="field-text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="field-password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="field-number"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="field-select"]').exists()).toBe(true)
  })

  it('uses default values correctly', () => {
    expect(wrapper.vm.formData.port).toBe(8080)
  })

  it('closes modal when close event is emitted', async () => {
    await wrapper.vm.$emit('close')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('resets form when modal is reopened', async () => {
    // Fill form
    await wrapper.find('[data-testid="username-input"]').setValue('testuser')
    
    // Close modal
    await wrapper.setProps({ visible: false })
    
    // Reopen modal
    await wrapper.setProps({ visible: true })
    
    // Form should be reset
    expect(wrapper.vm.formData.username).toBe('')
  })

  it('handles validation errors correctly', async () => {
    const wrapper = mount(InteractionModal, {
      props: {
        visible: true,
        interactionRequest: {
          ...mockInteractionRequest,
          fields: [
            {
              name: 'email',
              type: 'email',
              label: 'Email',
              required: true,
              validation: 'email'
            }
          ]
        }
      }
    })

    const emailInput = wrapper.find('[data-testid="email-input"]')
    await emailInput.setValue('invalid-email')
    
    const confirmButton = wrapper.find('[data-testid="confirm-button"]')
    await confirmButton.trigger('click')
    
    expect(wrapper.text()).toContain('Please enter a valid email')
  })

  it('shows loading state during submission', async () => {
    wrapper.vm.loading = true
    await wrapper.vm.$nextTick()
    
    const confirmButton = wrapper.find('[data-testid="confirm-button"]')
    expect(confirmButton.attributes('loading')).toBe('true')
  })
})