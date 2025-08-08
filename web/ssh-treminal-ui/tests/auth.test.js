/**
 * AuthService前端测试套件
 * 
 * 测试前端认证服务的核心功能，包括令牌管理、重试机制、
 * 会话存储、错误处理等关键功能。
 * 
 * 使用Jest测试框架。
 * 
 * @author lizelin
 */

import { AuthService } from '../src/services/auth.js';
import { CryptoService } from '../src/services/crypto.js';

// Mock CryptoService
jest.mock('../src/services/crypto.js');

// Mock fetch API
global.fetch = jest.fn();

// Mock sessionStorage
const mockSessionStorage = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn()
};
global.sessionStorage = mockSessionStorage;

describe('AuthService', () => {
  const validCredentials = {
    host: 'test.example.com',
    port: '22',
    user: 'testuser',
    password: 'testpass'
  };

  const mockTokenResponse = {
    token: '12345678-1234-4234-8234-123456789abc',
    expiresInSec: 120,
    success: true,
    timestamp: Date.now()
  };

  beforeEach(() => {
    // 清除所有mock调用
    jest.clearAllMocks();
    
    // 清除AuthService状态
    AuthService.clearToken();
    
    // 设置默认成功的mock
    CryptoService.isSupported.mockReturnValue(true);
    CryptoService.encryptCredentials.mockResolvedValue('encrypted-credentials');
    
    global.fetch.mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue(mockTokenResponse),
      text: jest.fn().mockResolvedValue('')
    });
  });

  describe('会话令牌获取', () => {
    test('应该成功获取会话令牌', async () => {
      // When
      const result = await AuthService.getSessionToken(validCredentials);

      // Then
      expect(result).toEqual(mockTokenResponse);
      expect(CryptoService.encryptCredentials).toHaveBeenCalledWith(validCredentials);
      expect(global.fetch).toHaveBeenCalledWith('/api/security/session/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({
          encryptedCredentials: 'encrypted-credentials',
          clientTimestamp: expect.any(Number)
        })
      });
      expect(mockSessionStorage.setItem).toHaveBeenCalledWith(
        AuthService.TOKEN_STORAGE_KEY,
        mockTokenResponse.token
      );
    });

    test('不支持加密的浏览器应该抛出异常', async () => {
      // Given
      CryptoService.isSupported.mockReturnValue(false);

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('浏览器不支持所需的加密功能');
    });

    test('加密失败应该抛出异常', async () => {
      // Given
      CryptoService.encryptCredentials.mockRejectedValue(new Error('加密失败'));

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('加密失败');
    });

    test('HTTP 400错误应该提供用户友好的错误信息', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: jest.fn().mockResolvedValue('Invalid credentials')
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('凭据格式无效，请检查输入信息');
    });

    test('HTTP 401错误应该提供用户友好的错误信息', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        text: jest.fn().mockResolvedValue('Unauthorized')
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('认证失败，请检查用户名和密码');
    });

    test('HTTP 500错误应该提供用户友好的错误信息', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: jest.fn().mockResolvedValue('Server error')
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('服务器内部错误，请稍后重试');
    });

    test('网络连接失败应该提供用户友好的错误信息', async () => {
      // Given
      global.fetch.mockRejectedValue(new Error('Failed to fetch'));

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('网络连接失败，请检查网络连接');
    });

    test('服务器返回失败状态应该抛出异常', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockResolvedValue({
          ...mockTokenResponse,
          success: false
        })
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('服务器拒绝了令牌请求');
    });

    test('服务器响应缺少令牌应该抛出异常', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockResolvedValue({
          success: true,
          expiresInSec: 120
          // 缺少token字段
        })
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow('服务器响应中缺少令牌');
    });
  });

  describe('令牌管理', () => {
    test('应该存储和检索当前令牌', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);

      // When
      const currentToken = AuthService.getCurrentToken();

      // Then
      expect(currentToken).toBe(mockTokenResponse.token);
    });

    test('过期令牌应该被自动清理', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);
      
      // 模拟令牌过期
      AuthService._tokenExpiry = Date.now() - 1000; // 1秒前过期

      // When
      const currentToken = AuthService.getCurrentToken();

      // Then
      expect(currentToken).toBeNull();
      expect(mockSessionStorage.removeItem).toHaveBeenCalledWith(AuthService.TOKEN_STORAGE_KEY);
    });

    test('应该从sessionStorage恢复令牌', () => {
      // Given
      const storedToken = '87654321-4321-4321-4321-210987654321';
      const futureExpiry = Date.now() + 60000; // 1分钟后过期
      
      mockSessionStorage.getItem.mockImplementation((key) => {
        if (key === AuthService.TOKEN_STORAGE_KEY) return storedToken;
        if (key === AuthService.TOKEN_EXPIRY_KEY) return futureExpiry.toString();
        return null;
      });

      // When
      const currentToken = AuthService.getCurrentToken();

      // Then
      expect(currentToken).toBe(storedToken);
    });

    test('sessionStorage中的过期令牌应该被清理', () => {
      // Given
      const expiredToken = '87654321-4321-4321-4321-210987654321';
      const pastExpiry = Date.now() - 60000; // 1分钟前过期
      
      mockSessionStorage.getItem.mockImplementation((key) => {
        if (key === AuthService.TOKEN_STORAGE_KEY) return expiredToken;
        if (key === AuthService.TOKEN_EXPIRY_KEY) return pastExpiry.toString();
        return null;
      });

      // When
      const currentToken = AuthService.getCurrentToken();

      // Then
      expect(currentToken).toBeNull();
      expect(mockSessionStorage.removeItem).toHaveBeenCalledWith(AuthService.TOKEN_STORAGE_KEY);
    });

    test('sessionStorage读取错误应该被安全处理', () => {
      // Given
      mockSessionStorage.getItem.mockImplementation(() => {
        throw new Error('Storage error');
      });

      // When & Then
      expect(() => AuthService.getCurrentToken()).not.toThrow();
      expect(AuthService.getCurrentToken()).toBeNull();
    });

    test('clearToken应该清理所有令牌相关数据', () => {
      // Given
      AuthService._currentToken = 'test-token';
      AuthService._tokenExpiry = Date.now() + 60000;

      // When
      AuthService.clearToken();

      // Then
      expect(AuthService._currentToken).toBeNull();
      expect(AuthService._tokenExpiry).toBeNull();
      expect(mockSessionStorage.removeItem).toHaveBeenCalledWith(AuthService.TOKEN_STORAGE_KEY);
      expect(mockSessionStorage.removeItem).toHaveBeenCalledWith(AuthService.TOKEN_EXPIRY_KEY);
    });

    test('sessionStorage清理错误应该被安全处理', () => {
      // Given
      mockSessionStorage.removeItem.mockImplementation(() => {
        throw new Error('Storage error');
      });

      // When & Then
      expect(() => AuthService.clearToken()).not.toThrow();
    });
  });

  describe('令牌验证', () => {
    test('有效令牌应该通过验证', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);

      // When & Then
      expect(AuthService.isTokenValid()).toBe(true);
    });

    test('应该验证外部提供的令牌', () => {
      // Given
      const validToken = '12345678-1234-4234-8234-123456789abc';
      AuthService._tokenExpiry = Date.now() + 60000;

      // When & Then
      expect(AuthService.isTokenValid(validToken)).toBe(true);
    });

    test('无效格式的令牌应该验证失败', () => {
      // Given
      const invalidTokens = [
        'not-a-uuid',
        '12345678-1234-1234-1234-123456789abc', // 错误版本
        '12345678-1234-4234-1234-123456789abcd', // 过长
        '12345678-1234-4234-1234-123456789ab', // 过短
        ''
      ];

      // When & Then
      invalidTokens.forEach(token => {
        expect(AuthService.isTokenValid(token)).toBe(false);
      });
    });

    test('null或undefined令牌应该验证失败', () => {
      // When & Then
      expect(AuthService.isTokenValid(null)).toBe(false);
      expect(AuthService.isTokenValid(undefined)).toBe(false);
    });

    test('过期令牌应该验证失败', () => {
      // Given
      const token = '12345678-1234-4234-8234-123456789abc';
      AuthService._tokenExpiry = Date.now() - 1000; // 1秒前过期

      // When & Then
      expect(AuthService.isTokenValid(token)).toBe(false);
    });
  });

  describe('令牌剩余时间', () => {
    test('应该计算剩余时间', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);

      // When
      const remainingTime = AuthService.getTokenRemainingTime();

      // Then
      expect(remainingTime).toBeGreaterThan(0);
      expect(remainingTime).toBeLessThanOrEqual(120);
    });

    test('无效令牌应该返回0剩余时间', () => {
      // Given
      AuthService.clearToken();

      // When
      const remainingTime = AuthService.getTokenRemainingTime();

      // Then
      expect(remainingTime).toBe(0);
    });

    test('过期令牌应该返回0剩余时间', () => {
      // Given
      AuthService._tokenExpiry = Date.now() - 1000;

      // When
      const remainingTime = AuthService.getTokenRemainingTime();

      // Then
      expect(remainingTime).toBe(0);
    });
  });

  describe('连接头管理', () => {
    test('应该生成正确的连接头', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);

      // When
      const headers = AuthService.getConnectionHeaders();

      // Then
      expect(headers).toEqual({
        'Authorization': `Bearer ${mockTokenResponse.token}`
      });
    });

    test('无有效令牌时应该返回null', () => {
      // Given
      AuthService.clearToken();

      // When
      const headers = AuthService.getConnectionHeaders();

      // Then
      expect(headers).toBeNull();
    });
  });

  describe('连接重试机制', () => {
    test('应该成功处理连接重试', async () => {
      // Given
      const newToken = '87654321-4321-4321-4321-210987654321';
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: jest.fn().mockResolvedValue({
          ...mockTokenResponse,
          token: newToken
        })
      });

      // When
      const headers = await AuthService.handleConnectionRetry(validCredentials);

      // Then
      expect(headers).toEqual({
        'Authorization': `Bearer ${newToken}`
      });
      expect(AuthService._connectionRetryCount).toBe(1);
    });

    test('超出最大重试次数应该返回null', async () => {
      // Given
      AuthService._connectionRetryCount = AuthService.MAX_RETRY_ATTEMPTS;

      // When
      const headers = await AuthService.handleConnectionRetry(validCredentials);

      // Then
      expect(headers).toBeNull();
      expect(AuthService._connectionRetryCount).toBe(AuthService.MAX_RETRY_ATTEMPTS + 1);
    });

    test('重试失败应该返回null', async () => {
      // Given
      global.fetch.mockRejectedValue(new Error('网络错误'));

      // When
      const headers = await AuthService.handleConnectionRetry(validCredentials);

      // Then
      expect(headers).toBeNull();
    });

    test('应该实现指数退避延迟', async () => {
      // Given
      jest.spyOn(global, 'setTimeout').mockImplementation((callback) => callback());
      
      AuthService._connectionRetryCount = 0;
      
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockResolvedValue(mockTokenResponse)
      });

      // When
      await AuthService.handleConnectionRetry(validCredentials);

      // Then
      expect(global.setTimeout).toHaveBeenCalled();
      
      global.setTimeout.mockRestore();
    });

    test('resetRetryCount应该重置重试计数器', () => {
      // Given
      AuthService._connectionRetryCount = 5;

      // When
      AuthService.resetRetryCount();

      // Then
      expect(AuthService._connectionRetryCount).toBe(0);
    });
  });

  describe('状态信息', () => {
    test('应该返回正确的状态信息', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);

      // When
      const status = AuthService.getStatus();

      // Then
      expect(status).toEqual({
        hasToken: true,
        tokenValid: true,
        remainingTime: expect.any(Number),
        retryCount: 0,
        maxRetries: AuthService.MAX_RETRY_ATTEMPTS,
        cryptoSupported: true
      });
    });

    test('无令牌时应该返回正确状态', () => {
      // Given
      AuthService.clearToken();

      // When
      const status = AuthService.getStatus();

      // Then
      expect(status).toEqual({
        hasToken: false,
        tokenValid: false,
        remainingTime: 0,
        retryCount: 0,
        maxRetries: AuthService.MAX_RETRY_ATTEMPTS,
        cryptoSupported: true
      });
    });
  });

  describe('边界条件和错误处理', () => {
    test('应该处理sessionStorage存储失败', async () => {
      // Given
      mockSessionStorage.setItem.mockImplementation(() => {
        throw new Error('Storage quota exceeded');
      });

      // When & Then - 不应抛出异常
      await expect(AuthService.getSessionToken(validCredentials)).resolves.toBeDefined();
    });

    test('应该处理JSON解析错误', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockRejectedValue(new Error('Invalid JSON'))
      });

      // When & Then
      await expect(AuthService.getSessionToken(validCredentials))
        .rejects.toThrow();
    });

    test('应该清理错误状态', async () => {
      // Given
      AuthService._currentToken = 'old-token';
      global.fetch.mockRejectedValue(new Error('Request failed'));

      // When
      try {
        await AuthService.getSessionToken(validCredentials);
      } catch (error) {
        // 忽略错误，检查清理状态
      }

      // Then
      expect(AuthService._currentToken).toBeNull();
      expect(AuthService._tokenExpiry).toBeNull();
    });
  });

  describe('并发安全', () => {
    test('并发获取令牌应该安全', async () => {
      // Given
      const promises = [];
      
      // When
      for (let i = 0; i < 5; i++) {
        promises.push(AuthService.getSessionToken(validCredentials));
      }
      
      const results = await Promise.all(promises);

      // Then
      results.forEach(result => {
        expect(result).toEqual(mockTokenResponse);
      });
    });

    test('并发令牌验证应该安全', async () => {
      // Given
      await AuthService.getSessionToken(validCredentials);
      const promises = [];

      // When
      for (let i = 0; i < 5; i++) {
        promises.push(AuthService.isTokenValid());
      }
      
      const results = await Promise.all(promises);

      // Then
      results.forEach(result => {
        expect(result).toBe(true);
      });
    });
  });
});