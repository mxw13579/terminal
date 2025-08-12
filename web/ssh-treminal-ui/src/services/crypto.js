/**
 * RSA加密服务，用于在前端安全加密SSH凭据。
 * 
 * 该服务使用Web Crypto API提供的RSA-OAEP算法，
 * 与后端的RSA私钥解密功能配合使用，确保敏感凭据的安全传输。
 * 
 * 主要功能：
 * - 从后端获取RSA公钥
 * - 使用公钥加密凭据对象
 * - 返回Base64编码的加密数据
 * 
 * @author lizelin
 */

/**
 * RSA加密服务类
 */
export class CryptoService {
    // RSA算法配置
    static ALGORITHM = 'RSA-OAEP';
    static HASH = 'SHA-256';
    static KEY_FORMAT = 'spki';
    
    // 缓存的公钥
    static _cachedPublicKey = null;
    static _keyFetchTime = null;
    static KEY_CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存时间

    /**
     * 检查Web Crypto API是否可用
     * @returns {boolean} 是否支持Web Crypto API
     */
    static isWebCryptoSupported() {
        return (
            typeof window !== 'undefined' &&
            window.crypto &&
            window.crypto.subtle &&
            typeof window.crypto.subtle.importKey === 'function' &&
            typeof window.crypto.subtle.encrypt === 'function'
        );
    }

    /**
     * 检查当前环境是否安全（HTTPS或localhost）
     * @returns {boolean} 当前环境是否安全
     */
    static isSecureContext() {
        return (
            typeof window !== 'undefined' &&
            (
                window.location.protocol === 'https:' ||
                window.location.hostname === 'localhost' ||
                window.location.hostname === '127.0.0.1' ||
                window.location.hostname === '::1'
            )
        );
    }

    /**
     * 从后端获取RSA公钥。
     * 
     * 实现了简单的缓存机制，避免频繁请求后端公钥接口。
     * 
     * @returns {Promise<CryptoKey>} RSA公钥对象
     * @throws {Error} 网络请求或密钥解析失败
     */
    static async getPublicKey() {
        // 检查Web Crypto API支持
        if (!this.isWebCryptoSupported()) {
            const details = [];
            if (typeof window === 'undefined') details.push('非浏览器环境');
            if (!window.crypto) details.push('crypto对象不存在');
            if (!window.crypto?.subtle) details.push('crypto.subtle不可用');
            if (!this.isSecureContext()) details.push('非安全上下文(需要HTTPS或localhost)');
            
            throw new Error(`Web Crypto API不可用: ${details.join(', ')}\n当前URL: ${window?.location?.href || 'unknown'}`);
        }

        // 检查缓存的公钥是否仍然有效
        const now = Date.now();
        if (this._cachedPublicKey && this._keyFetchTime && 
            (now - this._keyFetchTime) < this.KEY_CACHE_DURATION) {
            console.debug('使用缓存的RSA公钥');
            return this._cachedPublicKey;
        }

        try {
            console.debug('从后端获取RSA公钥');
            
            // 请求后端公钥接口
            const response = await fetch('/api/security/public-key', {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`获取公钥失败: HTTP ${response.status} ${response.statusText}`);
            }

            const data = await response.json();
            
            if (!data.publicKey) {
                throw new Error('服务器响应中缺少公钥数据');
            }

            console.debug('公钥获取成功，算法:', data.algorithm, '长度:', data.keyLength);

            // 将Base64公钥转换为ArrayBuffer
            const publicKeyData = this._base64ToArrayBuffer(data.publicKey);
            
            // 导入RSA公钥
            const cryptoKey = await window.crypto.subtle.importKey(
                this.KEY_FORMAT,
                publicKeyData,
                {
                    name: this.ALGORITHM,
                    hash: this.HASH
                },
                false, // 不可提取
                ['encrypt'] // 仅用于加密
            );

            // 缓存公钥
            this._cachedPublicKey = cryptoKey;
            this._keyFetchTime = now;
            
            console.debug('RSA公钥导入成功');
            return cryptoKey;

        } catch (error) {
            console.error('获取或导入RSA公钥失败:', error);
            // 清除可能损坏的缓存
            this._cachedPublicKey = null;
            this._keyFetchTime = null;
            throw new Error(`RSA公钥获取失败: ${error.message}`);
        }
    }

    /**
     * 使用RSA公钥加密凭据对象。
     * 
     * 将凭据对象序列化为JSON，然后使用RSA-OAEP算法加密，
     * 最后返回Base64编码的加密数据。
     * 如果Web Crypto API不可用，则使用明文传输并发出警告。
     * 
     * @param {Object} credentials - 凭据对象，包含host、port、user、password等字段
     * @param {CryptoKey} [publicKey] - RSA公钥，如果不提供则自动获取
     * @returns {Promise<string>} Base64编码的加密数据或JSON字符串
     * @throws {Error} 加密过程失败
     */
    static async encryptCredentials(credentials, publicKey = null) {
        try {
            // 验证凭据对象
            this._validateCredentials(credentials);
            
            // 检查Web Crypto API支持
            if (!this.isWebCryptoSupported()) {
                console.warn('⚠️ Web Crypto API不可用，使用明文传输凭据（不安全）');
                console.warn('建议使用HTTPS或确保在localhost环境下运行');
                
                // 降级方案：返回JSON字符串，后端需要能够处理
                const credentialsJson = JSON.stringify({
                    ...credentials,
                    _unencrypted: true // 标记为未加密
                });
                
                return credentialsJson;
            }
            
            // 获取公钥（如果未提供）
            if (!publicKey) {
                publicKey = await this.getPublicKey();
            }

            // 将凭据序列化为JSON字符串
            const credentialsJson = JSON.stringify(credentials);
            console.debug('准备加密凭据，JSON长度:', credentialsJson.length);

            // 转换为UTF-8字节数组
            const encoder = new TextEncoder();
            const credentialsBytes = encoder.encode(credentialsJson);

            // RSA加密
            const encryptedBytes = await window.crypto.subtle.encrypt(
                {
                    name: this.ALGORITHM,
                    hash: this.HASH
                },
                publicKey,
                credentialsBytes
            );

            // 转换为Base64
            const encryptedBase64 = this._arrayBufferToBase64(encryptedBytes);
            
            console.debug('凭据加密成功，加密数据长度:', encryptedBase64.length);
            return encryptedBase64;

        } catch (error) {
            console.error('凭据加密失败:', error);
            
            // 如果是因为Web Crypto API问题，尝试降级
            if (error.message.includes('Web Crypto API不可用')) {
                console.warn('🔓 加密失败，降级为明文传输（仅开发环境）');
                
                if (!this.isSecureContext()) {
                    throw new Error('在非安全环境下无法加密凭据，请使用HTTPS或localhost');
                }
                
                return JSON.stringify({
                    ...credentials,
                    _unencrypted: true,
                    _fallbackReason: error.message
                });
            }
            
            throw new Error(`凭据加密失败: ${error.message}`);
        }
    }

    /**
     * 验证凭据对象的完整性。
     * 
     * @param {Object} credentials - 要验证的凭据对象
     * @throws {Error} 凭据验证失败
     * @private
     */
    static _validateCredentials(credentials) {
        if (!credentials || typeof credentials !== 'object') {
            throw new Error('凭据对象不能为空');
        }

        const required = ['host', 'user', 'password'];
        const missing = required.filter(field => !credentials[field]);
        
        if (missing.length > 0) {
            throw new Error(`缺少必需的凭据字段: ${missing.join(', ')}`);
        }

        // 验证字段长度
        if (credentials.host.length > 255) {
            throw new Error('主机地址过长（最大255字符）');
        }
        
        if (credentials.user.length > 64) {
            throw new Error('用户名过长（最大64字符）');
        }
        
        if (credentials.password.length > 255) {
            throw new Error('密码过长（最大255字符）');
        }
        
        // 端口号验证（如果提供）
        if (credentials.port) {
            const port = parseInt(credentials.port);
            if (isNaN(port) || port < 1 || port > 65535) {
                throw new Error('无效的端口号（1-65535）');
            }
        }
    }

    /**
     * 将Base64字符串转换为ArrayBuffer。
     * 
     * @param {string} base64 - Base64字符串
     * @returns {ArrayBuffer} 转换后的ArrayBuffer
     * @private
     */
    static _base64ToArrayBuffer(base64) {
        const binaryString = window.atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        
        return bytes.buffer;
    }

    /**
     * 将ArrayBuffer转换为Base64字符串。
     * 
     * @param {ArrayBuffer} buffer - 要转换的ArrayBuffer
     * @returns {string} Base64字符串
     * @private
     */
    static _arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let binaryString = '';
        
        for (let i = 0; i < bytes.byteLength; i++) {
            binaryString += String.fromCharCode(bytes[i]);
        }
        
        return window.btoa(binaryString);
    }

    /**
     * 检查浏览器是否支持所需的加密功能。
     * 
     * @returns {boolean} 如果支持则返回true
     */
    static isSupported() {
        return !!(window.crypto && 
                 window.crypto.subtle && 
                 window.crypto.subtle.importKey &&
                 window.crypto.subtle.encrypt);
    }

    /**
     * 清除缓存的公钥（用于调试或强制刷新）。
     */
    static clearCache() {
        this._cachedPublicKey = null;
        this._keyFetchTime = null;
        console.debug('RSA公钥缓存已清除');
    }

    /**
     * 获取加密服务的状态信息（用于调试）。
     * 
     * @returns {Object} 状态信息对象
     */
    static getStatus() {
        return {
            supported: this.isSupported(),
            hasCachedKey: !!this._cachedPublicKey,
            keyAge: this._keyFetchTime ? Date.now() - this._keyFetchTime : null,
            algorithm: this.ALGORITHM,
            hash: this.HASH
        };
    }
}