package com.fufu.terminal.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
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
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";

    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    /**
     * 初始化RSA密钥对。
     * 在服务启动时自动执行，生成2048位RSA密钥对用于加密解密操作。
     */
    @PostConstruct
    public void generateKeys() {
        try {
            log.info("开始生成RSA-{} 密钥对...", KEY_SIZE);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);

            this.keyPair = generator.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

            log.info("RSA密钥对生成成功，算法: {}，密钥长度: {} bits",
                    publicKey.getAlgorithm(), KEY_SIZE);
            log.debug("公钥格式: {}", publicKey.getFormat());

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
     * @param encryptedData Base64编码的加密数据
     * @return 解密后的凭据JSON字符串
     * @throws IllegalArgumentException 如果加密数据无效
     * @throws IllegalStateException 如果解密过程失败
     */
    public String decryptCredentials(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            throw new IllegalArgumentException("加密数据不能为空");
        }

        if (privateKey == null) {
            throw new IllegalStateException("RSA私钥尚未初始化");
        }

        try {
            log.debug("开始解密凭据数据，数据长度: {} 字符", encryptedData.length());

            // Base64解码
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            log.debug("解码后的加密数据长度: {} 字节", encryptedBytes.length);

            // RSA解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            String decryptedData = new String(decryptedBytes, "UTF-8");
            log.debug("凭据解密成功，解密后数据长度: {} 字符", decryptedData.length());

            // 注意：不记录解密后的内容，避免敏感信息泄露到日志
            return decryptedData;

        } catch (IllegalArgumentException e) {
            log.warn("Base64解码失败: {}", e.getMessage());
            throw new IllegalArgumentException("无效的加密数据格式", e);
        } catch (Exception e) {
            log.error("解密凭据失败: {}", e.getMessage());
            throw new IllegalStateException("凭据解密过程出错", e);
        }
    }

    /**
     * 验证密钥是否已正确初始化。
     *
     * @return 如果密钥对已初始化则返回true
     */
    public boolean isInitialized() {
        return keyPair != null && publicKey != null && privateKey != null;
    }

    /**
     * 获取密钥算法信息（用于调试和监控）。
     *
     * @return 包含密钥算法和长度信息的字符串
     */
    public String getKeyInfo() {
        if (!isInitialized()) {
            return "密钥未初始化";
        }
        return String.format("算法: %s, 长度: %d bits, 格式: %s",
                publicKey.getAlgorithm(), KEY_SIZE, publicKey.getFormat());
    }
}
