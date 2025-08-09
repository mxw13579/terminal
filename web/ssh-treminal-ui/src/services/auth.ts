/**
 * 认证服务，负责会话令牌的创建和管理。
 * 
 * 该服务与CryptoService协作，实现安全的凭据传输和令牌认证流程：
 * 1. 使用RSA加密凭据
 * 2. 向后端请求会话令牌
 * 3. 管理令牌的生命周期和状态
 * 
 * 主要功能：
 * - 创建和验证会话令牌
 * - 管理令牌过期状态
 * - 提供连接重试机制
 * 
 * @author lizelin
 */

import type { 
  SshCredentials, 
  TokenResponse, 
  AuthStatus, 
  ConnectionHeaders 
} from '@/types';
import { CryptoService } from './crypto';

/**
 * 认证服务类
 */
export class AuthService {
    // 令牌存储键名
    private static readonly TOKEN_STORAGE_KEY = 'ssh_terminal_token';
    private static readonly TOKEN_EXPIRY_KEY = 'ssh_terminal_token_expiry';
    
    // 当前令牌信息
    private static _currentToken: string | null = null;
    private static _tokenExpiry: number | null = null;
    private static _connectionRetryCount = 0;
    private static readonly MAX_RETRY_ATTEMPTS = 3;

    /**
     * 获取会话令牌。
     * 
     * 使用RSA加密的凭据向后端请求会话令牌，
     * 令牌用于后续的WebSocket STOMP连接认证。
     * 
     * @param credentials - 凭据对象 {host, port, user, password}
     * @returns 令牌响应对象 {token, expiresInSec, success}
     * @throws 令牌获取失败
     */
    static async getSessionToken(credentials: SshCredentials): Promise<TokenResponse> {
        try {
            console.debug('开始获取会话令牌');
            
            // 验证浏览器支持
            if (!CryptoService.isSupported()) {
                throw new Error('浏览器不支持所需的加密功能，请使用现代浏览器');
            }

            // RSA加密凭据
            console.debug('正在加密凭据...');
            const encryptedCredentials = await CryptoService.encryptCredentials(credentials);
            
            // 构造请求体
            const requestBody = {
                encryptedCredentials,
                clientTimestamp: Date.now()
            };

            console.debug('向后端请求会话令牌');
            
            // 发送令牌请求
            const response = await fetch('/api/security/session/token', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`令牌请求失败: HTTP ${response.status} ${response.statusText} - ${errorText}`);
            }

            const tokenResponse: TokenResponse = await response.json();
            
            if (!tokenResponse.success) {
                throw new Error('服务器拒绝了令牌请求');
            }

            if (!tokenResponse.token) {
                throw new Error('服务器响应中缺少令牌');
            }

            // 计算令牌过期时间
            const expiryTime = Date.now() + (tokenResponse.expiresInSec * 1000);
            
            // 存储令牌信息
            this._currentToken = tokenResponse.token;
            this._tokenExpiry = expiryTime;
            
            // 持久化到sessionStorage（仅当前会话有效）
            this._storeToken(tokenResponse.token, expiryTime);
            
            console.info('会话令牌获取成功，有效期:', tokenResponse.expiresInSec, '秒');
            console.debug('令牌前缀:', tokenResponse.token.substring(0, 8) + '...');
            
            // 重置重试计数
            this._connectionRetryCount = 0;
            
            return tokenResponse;

        } catch (error) {
            console.error('获取会话令牌失败:', error);
            
            // 清理可能的残留数据
            this.clearToken();
            
            // 提供用户友好的错误信息
            let userMessage = error instanceof Error ? error.message : String(error);
            if (userMessage.includes('HTTP 400')) {
                userMessage = '凭据格式无效，请检查输入信息';
            } else if (userMessage.includes('HTTP 401')) {
                userMessage = '认证失败，请检查用户名和密码';
            } else if (userMessage.includes('HTTP 500')) {
                userMessage = '服务器内部错误，请稍后重试';
            } else if (userMessage.includes('Failed to fetch')) {
                userMessage = '网络连接失败，请检查网络连接';
            }
            
            throw new Error(userMessage);
        }
    }

    /**
     * 获取当前有效的令牌。
     * 
     * 会自动检查令牌是否过期，过期的令牌将被清理。
     * 
     * @returns 有效的令牌，如果无有效令牌则返回null
     */
    static getCurrentToken(): string | null {
        // 首先尝试从内存获取
        if (this._currentToken && this._tokenExpiry) {
            if (Date.now() < this._tokenExpiry) {
                return this._currentToken;
            } else {
                console.debug('内存中的令牌已过期');
                this.clearToken();
                return null;
            }
        }

        // 尝试从sessionStorage恢复
        try {
            const storedToken = sessionStorage.getItem(this.TOKEN_STORAGE_KEY);
            const storedExpiry = sessionStorage.getItem(this.TOKEN_EXPIRY_KEY);
            
            if (storedToken && storedExpiry) {
                const expiryTime = parseInt(storedExpiry);
                if (Date.now() < expiryTime) {
                    console.debug('从sessionStorage恢复令牌');
                    this._currentToken = storedToken;
                    this._tokenExpiry = expiryTime;
                    return storedToken;
                } else {
                    console.debug('sessionStorage中的令牌已过期');
                    this.clearToken();
                }
            }
        } catch (error) {
            console.warn('从sessionStorage读取令牌失败:', error);
        }

        return null;
    }

    /**
     * 检查令牌是否有效且未过期。
     * 
     * @param token - 要检查的令牌，如果不提供则检查当前令牌
     * @returns 如果令牌有效且未过期则返回true
     */
    static isTokenValid(token?: string | null): boolean {
        const tokenToCheck = token || this.getCurrentToken();
        
        if (!tokenToCheck) {
            return false;
        }
        
        // 检查格式（UUID v4格式的简单验证）
        const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
        if (!uuidPattern.test(tokenToCheck)) {
            console.warn('令牌格式无效');
            return false;
        }
        
        // 检查过期时间
        if (this._tokenExpiry && Date.now() >= this._tokenExpiry) {
            console.debug('令牌已过期');
            return false;
        }
        
        return true;
    }

    /**
     * 获取令牌的剩余有效时间（秒）。
     * 
     * @returns 剩余秒数，如果令牌无效则返回0
     */
    static getTokenRemainingTime(): number {
        if (!this.isTokenValid()) {
            return 0;
        }
        
        const remaining = Math.floor((this._tokenExpiry! - Date.now()) / 1000);
        return Math.max(0, remaining);
    }

    /**
     * 清除当前令牌。
     * 
     * 清理内存和持久化存储中的令牌信息。
     */
    static clearToken(): void {
        console.debug('清除会话令牌');
        
        // 清理内存
        this._currentToken = null;
        this._tokenExpiry = null;
        this._connectionRetryCount = 0;
        
        // 清理持久化存储
        try {
            sessionStorage.removeItem(this.TOKEN_STORAGE_KEY);
            sessionStorage.removeItem(this.TOKEN_EXPIRY_KEY);
        } catch (error) {
            console.warn('清理sessionStorage失败:', error);
        }
    }

    /**
     * 准备WebSocket连接的Authorization头。
     * 
     * @returns 连接头对象，如果无有效令牌则返回null
     */
    static getConnectionHeaders(): ConnectionHeaders | null {
        const token = this.getCurrentToken();
        
        if (!token) {
            console.warn('无有效令牌，无法创建连接头');
            return null;
        }
        
        return {
            Authorization: `Bearer ${token}`
        };
    }

    /**
     * 处理连接重试逻辑。
     * 
     * @param credentials - 原始凭据（用于重新获取令牌）
     * @returns 新的连接头，如果超出重试次数则返回null
     */
    static async handleConnectionRetry(credentials: SshCredentials): Promise<ConnectionHeaders | null> {
        this._connectionRetryCount++;
        
        if (this._connectionRetryCount > this.MAX_RETRY_ATTEMPTS) {
            console.error('连接重试次数已达上限:', this.MAX_RETRY_ATTEMPTS);
            this.clearToken();
            return null;
        }
        
        console.info(`连接重试 ${this._connectionRetryCount}/${this.MAX_RETRY_ATTEMPTS}`);
        
        try {
            // 清除旧令牌
            this.clearToken();
            
            // 添加重试延迟（指数退避）
            const delay = Math.min(1000 * Math.pow(2, this._connectionRetryCount - 1), 5000);
            console.debug(`重试延迟: ${delay}ms`);
            await new Promise(resolve => setTimeout(resolve, delay));
            
            // 获取新令牌
            await this.getSessionToken(credentials);
            
            return this.getConnectionHeaders();
            
        } catch (error) {
            console.error(`连接重试 ${this._connectionRetryCount} 失败:`, error);
            return null;
        }
    }

    /**
     * 将令牌存储到sessionStorage。
     * 
     * @param token - 令牌
     * @param expiryTime - 过期时间戳
     */
    private static _storeToken(token: string, expiryTime: number): void {
        try {
            sessionStorage.setItem(this.TOKEN_STORAGE_KEY, token);
            sessionStorage.setItem(this.TOKEN_EXPIRY_KEY, expiryTime.toString());
        } catch (error) {
            console.warn('存储令牌到sessionStorage失败:', error);
            // 不抛出异常，因为令牌已经存储在内存中
        }
    }

    /**
     * 获取认证服务的状态信息（用于调试）。
     * 
     * @returns 状态信息对象
     */
    static getStatus(): AuthStatus {
        return {
            hasToken: !!this._currentToken,
            tokenValid: this.isTokenValid(),
            remainingTime: this.getTokenRemainingTime(),
            retryCount: this._connectionRetryCount,
            maxRetries: this.MAX_RETRY_ATTEMPTS,
            cryptoSupported: CryptoService.isSupported()
        };
    }

    /**
     * 重置重试计数器（用于手动重置）。
     */
    static resetRetryCount(): void {
        this._connectionRetryCount = 0;
        console.debug('连接重试计数器已重置');
    }

    /**
     * 兼容性方法 - 获取令牌
     * 保持与旧代码的兼容性
     */
    static getToken(): string | null {
        return this.getCurrentToken();
    }
}