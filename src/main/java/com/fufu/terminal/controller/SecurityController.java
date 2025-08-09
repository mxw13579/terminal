package com.fufu.terminal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.security.EncryptedCredentialsRequest;
import com.fufu.terminal.dto.security.PublicKeyResponse;
import com.fufu.terminal.dto.security.TokenResponse;
import com.fufu.terminal.security.CryptoService;
import com.fufu.terminal.security.TokenVault;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



/**
 * 安全控制器，提供RSA加密和令牌管理相关的API端点。
 * <p>
 * 该控制器实现了安全凭据传输的核心功能：
 * <ul>
 *     <li>提供RSA公钥供前端加密凭据</li>
 *     <li>接收加密凭据并生成短期访问令牌</li>
 *     <li>支持令牌验证和管理</li>
 * </ul>
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SecurityController {

    private final CryptoService cryptoService;
    private final TokenVault tokenVault;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取RSA公钥。
     * <p>
     * 前端调用此接口获取公钥用于凭据加密。
     * 公钥是安全的，可以在网络中明文传输。
     * </p>
     *
     * @return 包含Base64编码公钥的响应对象
     */
    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        try {
            log.debug("收到公钥获取请求");

            if (!cryptoService.isInitialized()) {
                log.error("加密服务未初始化");
                return ResponseEntity.internalServerError().build();
            }

            String publicKeyBase64 = cryptoService.getPublicKeyBase64();
            String keyInfo = cryptoService.getKeyInfo();

            PublicKeyResponse response = new PublicKeyResponse(publicKeyBase64);
            response.setAlgorithm("RSA");
            response.setKeyLength(2048);

            log.info("公钥获取成功，密钥信息: {}", keyInfo);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取公钥失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建会话令牌。
     * <p>
     * 接收前端发送的RSA加密凭据，解密后验证并生成短期访问令牌。
     * 令牌用于后续WebSocket连接的身份验证。
     * </p>
     *
     * @param request 包含加密凭据的请求对象
     * @return 包含会话令牌和过期信息的响应对象
     */
    @PostMapping("/session/token")
    public ResponseEntity<TokenResponse> createSessionToken(
            @Valid @RequestBody EncryptedCredentialsRequest request) {

        try {
            log.debug("收到令牌创建请求，加密数据长度: {} 字符",
                    request.getEncryptedCredentials().length());

            // 解密凭据
            String decryptedJson = cryptoService.decryptCredentials(request.getEncryptedCredentials());
            log.debug("凭据解密成功");

            // 解析凭据JSON
            JsonNode credentials = objectMapper.readTree(decryptedJson);

            // 提取凭据字段
            String host = getRequiredField(credentials, "host");
            String port = getOptionalField(credentials, "port", "22");
            String user = getRequiredField(credentials, "user");
            String password = getRequiredField(credentials, "password");

            log.debug("解析凭据成功，主机: {}, 端口: {}, 用户: {}", host, port, user);

            // 基本验证（更严格的验证可以在这里添加）
            validateCredentials(host, user, password);

            // 存储凭据到令牌保险库
            String token = tokenVault.storeCredentials(host, port, user, password);

            // 创建响应
            TokenResponse response = new TokenResponse(token, 120); // 120秒TTL

            log.info("会话令牌创建成功，令牌: {}...，主机: {}",
                    token.substring(0, 8), host);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("凭据验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new TokenResponse(null, 0) {{
                        setSuccess(false);
                    }});

        } catch (Exception e) {
            log.error("创建令牌失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new TokenResponse(null, 0) {{
                        setSuccess(false);
                    }});
        }
    }

    /**
     * 验证令牌有效性（可选端点，用于调试）。
     *
     * @param token 要验证的令牌
     * @return 验证结果
     */
    @GetMapping("/token/validate")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        try {
            boolean isValid = tokenVault.isTokenValid(token);
            log.debug("令牌验证: {}..., 结果: {}", token.substring(0, 8), isValid);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            log.error("令牌验证失败: {}", e.getMessage());
            return ResponseEntity.ok(false);
        }
    }

    /**
     * 获取令牌保险库统计信息（仅用于监控和调试）。
     *
     * @return 统计信息字符串
     */
    @GetMapping("/vault/stats")
    public ResponseEntity<String> getVaultStats() {
        try {
            String stats = tokenVault.getStats();
            log.debug("保险库统计信息: {}", stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("获取统计信息失败");
        }
    }

    /**
     * 从JSON节点中获取必需字段。
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @return 字段值
     * @throws IllegalArgumentException 如果字段不存在或为空
     */
    private String getRequiredField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().trim().isEmpty()) {
            throw new IllegalArgumentException("必需字段 '" + fieldName + "' 不存在或为空");
        }
        return field.asText().trim();
    }

    /**
     * 从JSON节点中获取可选字段。
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    private String getOptionalField(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().trim().isEmpty()) {
            return defaultValue;
        }
        return field.asText().trim();
    }

    /**
     * 验证凭据的基本格式。
     *
     * @param host 主机地址
     * @param user 用户名
     * @param password 密码
     * @throws IllegalArgumentException 如果凭据格式无效
     */
    private void validateCredentials(String host, String user, String password) {
        if (host.length() > 255) {
            throw new IllegalArgumentException("主机地址过长");
        }

        if (user.length() > 64) {
            throw new IllegalArgumentException("用户名过长");
        }

        if (password.length() > 255) {
            throw new IllegalArgumentException("密码过长");
        }

        // 可以添加更多验证规则，如IP地址格式验证等
        // 这里保持简单，实际部署时可以根据需要增强
    }
}
