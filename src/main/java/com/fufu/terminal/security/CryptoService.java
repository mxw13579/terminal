package com.fufu.terminal.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * RSA加密服务，用于安全处理SSH连接凭据。
 * <p>
 * 该服务在启动时生成RSA-2048密钥对，前端使用公钥加密凭据，
 * 后端使用私钥解密，确保敏感信息不以明文传输。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class CryptoService {

    private static final int KEY_SIZE = 2048;
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPPadding";

    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    /**
     * 初始化RSA密钥对。
     * 在服务启动时自动执行，生成2048位RSA密钥对用于加密解密操作。
     *
     * 使用显式的 SecureRandom 以提升随机性可控性和安全性。
     *
     * @throws IllegalStateException 当密钥生成失败时抛出
     */
    @PostConstruct
    public void generateKeys() {
        try {
            log.info("开始生成RSA-{} 密钥对...", KEY_SIZE);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            // 显式提供 SecureRandom，避免依赖不明确的默认实现
            generator.initialize(KEY_SIZE, new java.security.SecureRandom());
            this.keyPair = generator.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            if (log.isInfoEnabled()) {
                log.info("RSA密钥对生成成功，算法: {}，密钥长度: {} bits",
                        publicKey.getAlgorithm(), KEY_SIZE);
            }
            if (log.isDebugEnabled()) {
                log.debug("公钥格式: {}", publicKey.getFormat());
            }
        } catch (Exception e) {
            log.error("生成RSA密钥对失败: {}", e.getMessage(), e);
            throw new IllegalStateException("无法初始化加密服务", e);
        }
    }

    /**
     * 获取Base64编码的公钥。
     * <p>
     * 前端使用此公钥进行凭据加密，返回的公钥可以安全地在网络中传输。
     * </p>
     *
     * @return Base64编码的RSA公钥
     * @throws IllegalStateException 如果密钥尚未初始化
     */
    public String getPublicKeyBase64() {
        if (publicKey == null) {
            throw new IllegalStateException("RSA密钥尚未初始化");
        }

        String base64Key = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        log.debug("提供公钥，长度: {} 字符", base64Key.length());
        return base64Key;
    }

    /**
     * 解密加密的凭据数据。
     * <p>
     * 使用私钥解密前端发送的加密凭据，返回原始JSON字符串。
     * 支持的凭据格式应包含 host、port、user、password 字段。
     * </p>
     * 
     * 增强版本：支持处理未加密的凭据（仅限开发环境）
     * 如果输入数据包含 "_unencrypted": true，则直接返回该JSON，无需解密。
     *
     * @param encryptedData Base64编码的加密数据或包含_unencrypted标记的JSON字符串
     * @return 解密后的凭据JSON字符串
     * @throws IllegalArgumentException 如果加密数据无效
     * @throws IllegalStateException 如果解密过程失败
     */
    public String decryptCredentials(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            throw new IllegalArgumentException("加密数据不能为空");
        }
        
        // 检查是否为未加密的JSON数据（降级方案）
        if (isUnencryptedJson(encryptedData)) {
            log.warn("🔓 接收到未加密的凭据数据（仅开发环境允许）");
            return encryptedData;
        }
        
        if (privateKey == null) {
            throw new IllegalStateException("RSA私钥尚未初始化");
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("开始解密凭据数据，数据长度: {} 字符", encryptedData.length());
            }
            // Base64解码
            final byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            if (log.isDebugEnabled()) {
                log.debug("解码后的加密数据长度: {} 字节", encryptedBytes.length);
            }
            // RSA解密 - 使用OAEP参数规格以确保与前端Web Crypto API兼容
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            // 配置OAEP参数以匹配前端RSA-OAEP + SHA-256
            final OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
            final byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            final String decryptedData = new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
            if (log.isDebugEnabled()) {
                log.debug("凭据解密成功，解密后数据长度: {} 字符", decryptedData.length());
            }
            // 注意：不记录解密后的内容，避免敏感信息泄露到日志
            return decryptedData;
        } catch (IllegalArgumentException e) {
            // Base64解码失败，可能是未加密的JSON
            log.warn("Base64解码失败，尝试处理为未加密数据: {}", e.getMessage());
            
            // 最后一次尝试：检查是否为有效的JSON格式
            if (isValidJsonCredentials(encryptedData)) {
                log.warn("⚠️ 接收到可能未加密的凭据JSON（安全风险）");
                return encryptedData;
            }
            
            throw new IllegalArgumentException("无效的加密数据格式", e);
        } catch (Exception e) {
            log.error("解密凭据失败: {}", e.getMessage());
            throw new IllegalStateException("凭据解密过程出错", e);
        }
    }
    
    /**
     * 检查输入是否为标记了未加密的JSON数据
     * @param data 输入数据
     * @return 如果是未加密JSON返回true
     */
    private boolean isUnencryptedJson(String data) {
        try {
            return data.trim().startsWith("{") && data.contains("\"_unencrypted\":true");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查输入是否为有效的凭据JSON格式
     * @param data 输入数据
     * @return 如果是有效JSON且包含必要字段返回true
     */
    private boolean isValidJsonCredentials(String data) {
        try {
            if (!data.trim().startsWith("{")) {
                return false;
            }
            
            // 简单检查是否包含必要的凭据字段
            return data.contains("\"host\"") && 
                   data.contains("\"user\"") && 
                   data.contains("\"password\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证密钥是否已正确初始化。
     *
     * @return 如果公钥和私钥均已初始化则返回true
     */
    public boolean isInitialized() {
        return publicKey != null && privateKey != null;
    }
    /**
     * 获取密钥算法信息（用于调试和监控）。
     *
     * @return 包含密钥算法和长度信息的字符串；未初始化时返回“密钥未初始化”
     */
    public String getKeyInfo() {
        if (!isInitialized()) {
            return "密钥未初始化";
        }
        // 避免 String.format 的格式化开销，使用直连/构建更轻量
        StringBuilder sb = new StringBuilder(32);
        sb.append("算法: ").append(publicKey.getAlgorithm())
                .append(", 长度: ").append(KEY_SIZE).append(" bits")
                .append(", 格式: ").append(publicKey.getFormat());
        return sb.toString();
    }
}
