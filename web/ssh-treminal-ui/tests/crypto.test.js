/**
 * CryptoService前端测试套件
 * 
 * 测试前端RSA加密服务的核心功能，包括公钥获取、凭据加密、
 * 错误处理、浏览器兼容性等关键功能。
 * 
 * 使用Jest测试框架。
 * 
 * @author lizelin
 */

import { CryptoService } from '../src/services/crypto.js';

// Mock Web Crypto API for testing
global.crypto = {
  subtle: {
    importKey: jest.fn(),
    encrypt: jest.fn()
  }
};

// Mock fetch API
global.fetch = jest.fn();
global.window = {
  crypto: global.crypto,
  atob: jest.fn(),
  btoa: jest.fn()
};

describe('CryptoService', () => {
  beforeEach(() => {
    // 清除所有mock调用
    jest.clearAllMocks();
    CryptoService.clearCache();
    
    // 设置默认的成功响应
    global.fetch.mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue({
        publicKey: 'test-public-key-base64',
        algorithm: 'RSA',
        keyLength: 2048
      })
    });
  });

  describe('浏览器支持检查', () => {
    test('应该检测到支持的浏览器环境', () => {
      // Given
      global.window.crypto = {
        subtle: {
          importKey: jest.fn(),
          encrypt: jest.fn()
        }
      };

      // When
      const isSupported = CryptoService.isSupported();

      // Then
      expect(isSupported).toBe(true);
    });

    test('应该检测到不支持的浏览器环境', () => {
      // Given
      global.window.crypto = null;

      // When
      const isSupported = CryptoService.isSupported();

      // Then
      expect(isSupported).toBe(false);
    });

    test('应该检测到缺少subtle crypto的浏览器', () => {
      // Given
      global.window.crypto = {};

      // When
      const isSupported = CryptoService.isSupported();

      // Then
      expect(isSupported).toBe(false);
    });
  });

  describe('公钥获取功能', () => {
    test('应该成功获取公钥', async () => {
      // Given
      const mockPublicKey = 'mock-public-key-base64';
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockResolvedValue({
          publicKey: mockPublicKey,
          algorithm: 'RSA',
          keyLength: 2048
        })
      });

      const mockCryptoKey = { type: 'public' };
      global.window.crypto.subtle.importKey.mockResolvedValue(mockCryptoKey);
      global.window.atob.mockReturnValue('binary-data');

      // When
      const result = await CryptoService.getPublicKey();

      // Then
      expect(result).toBe(mockCryptoKey);
      expect(global.fetch).toHaveBeenCalledWith('/api/security/public-key', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      expect(global.window.crypto.subtle.importKey).toHaveBeenCalledWith(
        'spki',
        expect.any(ArrayBuffer),
        {
          name: 'RSA-OAEP',
          hash: 'SHA-256'
        },
        false,
        ['encrypt']
      );
    });

    test('应该使用缓存的公钥', async () => {
      // Given
      const mockCryptoKey = { type: 'public' };
      global.window.crypto.subtle.importKey.mockResolvedValue(mockCryptoKey);
      global.window.atob.mockReturnValue('binary-data');

      // 第一次调用获取公钥
      await CryptoService.getPublicKey();

      // When - 第二次调用
      const result = await CryptoService.getPublicKey();

      // Then
      expect(result).toBe(mockCryptoKey);
      expect(global.fetch).toHaveBeenCalledTimes(1); // 只调用一次API
    });

    test('过期缓存应该重新获取公钥', async () => {
      // Given
      const mockCryptoKey = { type: 'public' };
      global.window.crypto.subtle.importKey.mockResolvedValue(mockCryptoKey);
      global.window.atob.mockReturnValue('binary-data');

      // 模拟缓存过期（设置一个很早的时间）
      CryptoService._keyFetchTime = Date.now() - (6 * 60 * 1000); // 6分钟前

      // When
      const result = await CryptoService.getPublicKey();

      // Then
      expect(result).toBe(mockCryptoKey);
      expect(global.fetch).toHaveBeenCalledTimes(1);
    });

    test('网络错误应该抛出异常', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error'
      });

      // When & Then
      await expect(CryptoService.getPublicKey()).rejects.toThrow('获取公钥失败: HTTP 500 Internal Server Error');
    });

    test('服务器响应缺少公钥数据应该抛出异常', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockResolvedValue({
          algorithm: 'RSA',
          keyLength: 2048
          // 缺少publicKey字段
        })
      });

      // When & Then
      await expect(CryptoService.getPublicKey()).rejects.toThrow('服务器响应中缺少公钥数据');
    });

    test('公钥导入失败应该抛出异常', async () => {
      // Given
      global.window.crypto.subtle.importKey.mockRejectedValue(new Error('导入失败'));
      global.window.atob.mockReturnValue('binary-data');

      // When & Then
      await expect(CryptoService.getPublicKey()).rejects.toThrow('RSA公钥获取失败: 导入失败');
    });
  });

  describe('凭据加密功能', () => {
    const mockPublicKey = { type: 'public' };
    const validCredentials = {
      host: 'test.example.com',
      port: '22',
      user: 'testuser',
      password: 'testpass'
    };

    beforeEach(() => {
      global.window.crypto.subtle.importKey.mockResolvedValue(mockPublicKey);
      global.window.atob.mockReturnValue('binary-data');
      global.window.btoa.mockReturnValue('encrypted-base64');
      
      // Mock TextEncoder
      global.TextEncoder = jest.fn().mockImplementation(() => ({
        encode: jest.fn().mockReturnValue(new Uint8Array([1, 2, 3, 4]))
      }));
    });

    test('应该成功加密有效凭据', async () => {
      // Given
      const mockEncryptedBytes = new ArrayBuffer(256);
      global.window.crypto.subtle.encrypt.mockResolvedValue(mockEncryptedBytes);

      // When
      const result = await CryptoService.encryptCredentials(validCredentials);

      // Then
      expect(result).toBe('encrypted-base64');
      expect(global.window.crypto.subtle.encrypt).toHaveBeenCalledWith(
        {
          name: 'RSA-OAEP',
          hash: 'SHA-256'
        },
        mockPublicKey,
        expect.any(Uint8Array)
      );
    });

    test('应该使用提供的公钥', async () => {
      // Given
      const customPublicKey = { type: 'custom-public' };
      const mockEncryptedBytes = new ArrayBuffer(256);
      global.window.crypto.subtle.encrypt.mockResolvedValue(mockEncryptedBytes);

      // When
      const result = await CryptoService.encryptCredentials(validCredentials, customPublicKey);

      // Then
      expect(result).toBe('encrypted-base64');
      expect(global.window.crypto.subtle.encrypt).toHaveBeenCalledWith(
        expect.anything(),
        customPublicKey,
        expect.any(Uint8Array)
      );
    });

    test('应该验证必需字段', async () => {
      // Given
      const invalidCredentials = [
        null,
        {},
        { host: 'test', user: 'user' }, // 缺少password
        { host: 'test', password: 'pass' }, // 缺少user
        { user: 'user', password: 'pass' } // 缺少host
      ];

      // When & Then
      for (const creds of invalidCredentials) {
        await expect(CryptoService.encryptCredentials(creds)).rejects.toThrow();
      }
    });

    test('应该验证字段长度', async () => {
      // Given
      const invalidCredentials = [
        {
          host: 'a'.repeat(300), // 超过255字符
          user: 'user',
          password: 'pass'
        },
        {
          host: 'host',
          user: 'a'.repeat(70), // 超过64字符
          password: 'pass'
        },
        {
          host: 'host',
          user: 'user',
          password: 'a'.repeat(300) // 超过255字符
        }
      ];

      // When & Then
      for (const creds of invalidCredentials) {
        await expect(CryptoService.encryptCredentials(creds)).rejects.toThrow();
      }
    });

    test('应该验证端口号', async () => {
      // Given
      const invalidPortCredentials = [
        {
          ...validCredentials,
          port: '0' // 无效端口
        },
        {
          ...validCredentials,
          port: '65536' // 端口超出范围
        },
        {
          ...validCredentials,
          port: 'not-a-number'
        }
      ];

      // When & Then
      for (const creds of invalidPortCredentials) {
        await expect(CryptoService.encryptCredentials(creds)).rejects.toThrow('无效的端口号');
      }
    });

    test('加密失败应该抛出异常', async () => {
      // Given
      global.window.crypto.subtle.encrypt.mockRejectedValue(new Error('加密失败'));

      // When & Then
      await expect(CryptoService.encryptCredentials(validCredentials)).rejects.toThrow('凭据加密失败: 加密失败');
    });
  });

  describe('工具方法测试', () => {
    test('_base64ToArrayBuffer应该正确转换', () => {
      // Given
      const base64String = 'VGVzdA=='; // 'Test' in base64
      global.window.atob.mockReturnValue('Test');

      // When
      const result = CryptoService._base64ToArrayBuffer(base64String);

      // Then
      expect(result).toBeInstanceOf(ArrayBuffer);
      expect(global.window.atob).toHaveBeenCalledWith(base64String);
    });

    test('_arrayBufferToBase64应该正确转换', () => {
      // Given
      const buffer = new ArrayBuffer(4);
      const view = new Uint8Array(buffer);
      view[0] = 84; // 'T'
      view[1] = 101; // 'e'
      view[2] = 115; // 's'
      view[3] = 116; // 't'
      
      global.window.btoa.mockReturnValue('VGVzdA==');

      // When
      const result = CryptoService._arrayBufferToBase64(buffer);

      // Then
      expect(result).toBe('VGVzdA==');
      expect(global.window.btoa).toHaveBeenCalledWith('Test');
    });
  });

  describe('缓存管理', () => {
    test('clearCache应该清除缓存', () => {
      // Given
      CryptoService._cachedPublicKey = { type: 'cached' };
      CryptoService._keyFetchTime = Date.now();

      // When
      CryptoService.clearCache();

      // Then
      expect(CryptoService._cachedPublicKey).toBeNull();
      expect(CryptoService._keyFetchTime).toBeNull();
    });

    test('getStatus应该返回正确状态', () => {
      // Given
      const now = Date.now();
      CryptoService._cachedPublicKey = { type: 'cached' };
      CryptoService._keyFetchTime = now;
      
      global.window.crypto = {
        subtle: {
          importKey: jest.fn(),
          encrypt: jest.fn()
        }
      };

      // When
      const status = CryptoService.getStatus();

      // Then
      expect(status).toEqual({
        supported: true,
        hasCachedKey: true,
        keyAge: expect.any(Number),
        algorithm: 'RSA-OAEP',
        hash: 'SHA-256'
      });
      expect(status.keyAge).toBeGreaterThanOrEqual(0);
    });

    test('getStatus应该处理无缓存情况', () => {
      // Given
      CryptoService.clearCache();

      // When
      const status = CryptoService.getStatus();

      // Then
      expect(status).toEqual({
        supported: true,
        hasCachedKey: false,
        keyAge: null,
        algorithm: 'RSA-OAEP',
        hash: 'SHA-256'
      });
    });
  });

  describe('错误处理', () => {
    test('网络请求失败应该清除缓存', async () => {
      // Given
      global.fetch.mockRejectedValue(new Error('网络错误'));
      CryptoService._cachedPublicKey = { type: 'cached' };
      CryptoService._keyFetchTime = Date.now();

      // When
      await expect(CryptoService.getPublicKey()).rejects.toThrow();

      // Then
      expect(CryptoService._cachedPublicKey).toBeNull();
      expect(CryptoService._keyFetchTime).toBeNull();
    });

    test('应该处理fetch返回的非JSON响应', async () => {
      // Given
      global.fetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockRejectedValue(new Error('不是JSON'))
      });

      // When & Then
      await expect(CryptoService.getPublicKey()).rejects.toThrow();
    });
  });

  describe('边界条件测试', () => {
    test('应该处理空字符串凭据字段', async () => {
      // Given
      const credentialsWithEmptyFields = {
        host: '',
        user: '',
        password: ''
      };

      // When & Then
      await expect(CryptoService.encryptCredentials(credentialsWithEmptyFields))
        .rejects.toThrow('缺少必需的凭据字段');
    });

    test('应该处理包含特殊字符的凭据', async () => {
      // Given
      const specialCharCredentials = {
        host: 'test-server.example.com',
        port: '22',
        user: 'user@domain.com',
        password: 'P@ssw0rd!#$%^&*()'
      };

      const mockEncryptedBytes = new ArrayBuffer(256);
      global.window.crypto.subtle.encrypt.mockResolvedValue(mockEncryptedBytes);

      // When & Then
      await expect(CryptoService.encryptCredentials(specialCharCredentials))
        .resolves.toBe('encrypted-base64');
    });

    test('应该处理Unicode字符', async () => {
      // Given
      const unicodeCredentials = {
        host: '测试服务器.example.com',
        port: '22',
        user: '用户名',
        password: '密码123'
      };

      const mockEncryptedBytes = new ArrayBuffer(256);
      global.window.crypto.subtle.encrypt.mockResolvedValue(mockEncryptedBytes);

      // When & Then
      await expect(CryptoService.encryptCredentials(unicodeCredentials))
        .resolves.toBe('encrypted-base64');
    });
  });
});