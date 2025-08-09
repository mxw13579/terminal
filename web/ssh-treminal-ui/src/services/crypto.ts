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

import type { 
  SshCredentials, 
  PublicKeyResponse, 
  CryptoStatus 
} from '@/types';

/**
 * RSA加密服务类
 */
export class CryptoService {
    // RSA算法配置
    private static readonly ALGORITHM = 'RSA-OAEP';
    private static readonly HASH = 'SHA-256';
    private static readonly KEY_FORMAT: KeyFormat = 'spki';
    
    // 缓存的公钥
    private static _cachedPublicKey: CryptoKey | null = null;
    private static _keyFetchTime: number | null = null;
    private static readonly KEY_CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存时间

    /**
     * 从后端获取RSA公钥。
     * 
     * 实现了简单的缓存机制，避免频繁请求后端公钥接口。
     * 
     * @returns RSA公钥对象
     * @throws 网络请求或密钥解析失败
     */
    static async getPublicKey(): Promise<CryptoKey> {
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

            const data: PublicKeyResponse = await response.json();
            
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
                } as RsaHashedImportParams,
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
            const message = error instanceof Error ? error.message : String(error);
            throw new Error(`RSA公钥获取失败: ${message}`);
        }
    }

    /**
     * 使用RSA公钥加密凭据对象。
     * 
     * 将凭据对象序列化为JSON，然后使用RSA-OAEP算法加密，
     * 最后返回Base64编码的加密数据。
     * 
     * @param credentials - 凭据对象，包含host、port、user、password等字段
     * @param publicKey - RSA公钥，如果不提供则自动获取
     * @returns Base64编码的加密数据
     * @throws 加密过程失败
     */
    static async encryptCredentials(
        credentials: SshCredentials, 
        publicKey?: CryptoKey | null
    ): Promise<string> {
        try {
            // 验证凭据对象
            this._validateCredentials(credentials);
            
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
                } as RsaOaepParams,
                publicKey,
                credentialsBytes
            );

            // 转换为Base64
            const encryptedBase64 = this._arrayBufferToBase64(encryptedBytes);
            
            console.debug('凭据加密成功，加密数据长度:', encryptedBase64.length);
            return encryptedBase64;

        } catch (error) {
            console.error('凭据加密失败:', error);
            const message = error instanceof Error ? error.message : String(error);
            throw new Error(`凭据加密失败: ${message}`);
        }
    }

    /**
     * 验证凭据对象的完整性。
     * 
     * @param credentials - 要验证的凭据对象
     * @throws 凭据验证失败
     */
    private static _validateCredentials(credentials: SshCredentials): void {
        if (!credentials || typeof credentials !== 'object') {
            throw new Error('凭据对象不能为空');
        }

        const required = ['host', 'user', 'password'] as const;
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
        if (credentials.port !== undefined) {
            const port = parseInt(String(credentials.port));
            if (isNaN(port) || port < 1 || port > 65535) {
                throw new Error('无效的端口号（1-65535）');
            }
        }
    }

    /**
     * 将Base64字符串转换为ArrayBuffer。
     * 
     * @param base64 - Base64字符串
     * @returns 转换后的ArrayBuffer
     */
    private static _base64ToArrayBuffer(base64: string): ArrayBuffer {
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
     * @param buffer - 要转换的ArrayBuffer
     * @returns Base64字符串
     */
    private static _arrayBufferToBase64(buffer: ArrayBuffer): string {
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
     * @returns 如果支持则返回true
     */
    static isSupported(): boolean {
        return !!(window.crypto && 
                 window.crypto.subtle && 
                 window.crypto.subtle.importKey &&
                 window.crypto.subtle.encrypt);
    }

    /**
     * 清除缓存的公钥（用于调试或强制刷新）。
     */
    static clearCache(): void {
        this._cachedPublicKey = null;
        this._keyFetchTime = null;
        console.debug('RSA公钥缓存已清除');
    }

    /**
     * 获取加密服务的状态信息（用于调试）。
     * 
     * @returns 状态信息对象
     */
    static getStatus(): CryptoStatus {
        return {
            supported: this.isSupported(),
            hasCachedKey: !!this._cachedPublicKey,
            keyAge: this._keyFetchTime ? Date.now() - this._keyFetchTime : null,
            algorithm: this.ALGORITHM,
            hash: this.HASH
        };
    }
}