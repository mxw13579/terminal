import { test, expect } from '@playwright/test'

test.describe('Terminal Application E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('should load the homepage', async ({ page }) => {
    await expect(page).toHaveTitle(/Terminal/)
    await expect(page.locator('h1')).toContainText('SSH Terminal')
  })

  test('should display connection form', async ({ page }) => {
    const hostInput = page.locator('input[name="host"]')
    const portInput = page.locator('input[name="port"]')
    const usernameInput = page.locator('input[name="username"]')
    
    await expect(hostInput).toBeVisible()
    await expect(portInput).toBeVisible()
    await expect(usernameInput).toBeVisible()
  })

  test('should validate connection form', async ({ page }) => {
    const connectButton = page.locator('button[type="submit"]')
    
    // Try to connect without filling the form
    await connectButton.click()
    
    // Should show validation errors
    await expect(page.locator('.error-message')).toBeVisible()
  })

  test('should handle connection attempt', async ({ page }) => {
    // Fill connection form
    await page.fill('input[name="host"]', 'localhost')
    await page.fill('input[name="port"]', '22')
    await page.fill('input[name="username"]', 'testuser')
    await page.fill('input[name="password"]', 'testpass')
    
    // Click connect button
    await page.click('button[type="submit"]')
    
    // Should show connection attempt
    await expect(page.locator('.connection-status')).toContainText('Connecting')
  })

  test('should display terminal when connected', async ({ page }) => {
    // Mock successful connection
    await page.evaluate(() => {
      // Mock the WebSocket connection
      window.mockConnection = true
    })
    
    // Fill and submit connection form
    await page.fill('input[name="host"]', 'localhost')
    await page.fill('input[name="port"]', '22')
    await page.fill('input[name="username"]', 'testuser')
    await page.fill('input[name="password"]', 'testpass')
    await page.click('button[type="submit"]')
    
    // Should show terminal interface
    await expect(page.locator('.xterm')).toBeVisible()
  })

  test('should navigate between tabs', async ({ page }) => {
    const terminalTab = page.locator('[data-tab="terminal"]')
    const sftpTab = page.locator('[data-tab="sftp"]')
    const monitorTab = page.locator('[data-tab="monitor"]')
    
    // Test tab switching
    await sftpTab.click()
    await expect(page.locator('.sftp-panel')).toBeVisible()
    
    await monitorTab.click()
    await expect(page.locator('.monitor-panel')).toBeVisible()
    
    await terminalTab.click()
    await expect(page.locator('.terminal-panel')).toBeVisible()
  })

  test('should be accessible', async ({ page }) => {
    // Check for basic accessibility
    const axeResults = await page.locator('body').evaluate(() => {
      // Basic accessibility checks
      const elements = document.querySelectorAll('button, input, textarea')
      let issues = []
      
      elements.forEach(el => {
        if (!el.hasAttribute('aria-label') && !el.textContent?.trim()) {
          issues.push(`Element missing accessible label: ${el.tagName}`)
        }
      })
      
      return issues
    })
    
    expect(axeResults).toHaveLength(0)
  })
})