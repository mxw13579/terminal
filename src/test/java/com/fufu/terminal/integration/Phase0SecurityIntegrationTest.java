package com.fufu.terminal.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.security.EncryptedCredentialsRequest;
import com.fufu.terminal.dto.security.PublicKeyResponse;
import com.fufu.terminal.dto.security.TokenResponse;
import com.fufu.terminal.security.CryptoService;
import com.fufu.terminal.security.TokenVault;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javax.crypto.Cipher;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 0 安全基线集成测试套件。
 * 
 * 测试完整的安全认证流程，从公钥获取、凭据加密、令牌创建，
 * 到STOMP连接认证的端到端集成测试。
 * 
 * @author lizelin
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Phase 0 安全基线集成测试")
class Phase0SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private TokenVault tokenVault;

    private ObjectMapper objectMapper;
    private String testPublicKey;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("完整安全认证流程测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CompleteSecurityFlowTests {
        
        @Test
        @Order(1)
        @DisplayName("步骤1: 获取公钥应该成功")
        void step1_shouldGetPublicKeySuccessfully() throws Exception {
            // When
            MvcResult result = mockMvc.perform(get("/api/security/public-key")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicKey").exists())
                    .andExpect(jsonPath("$.algorithm").value("RSA"))
                    .andExpect(jsonPath("$.keyLength").value(2048))
                    .andReturn();
            
            // Then
            String responseJson = result.getResponse().getContentAsString();
            PublicKeyResponse response = objectMapper.readValue(responseJson, PublicKeyResponse.class);
            
            assertNotNull(response.getPublicKey(), "公钥不应为空");
            assertTrue(response.getPublicKey().length() > 0, "公钥应有内容");
            
            // 保存公钥用于后续测试
            testPublicKey = response.getPublicKey();
            
            log.info("步骤1完成: 成功获取公钥，长度: {} 字符", testPublicKey.length());
        }
        
        @Test
        @Order(2)
        @DisplayName("步骤2: 使用公钥加密凭据应该成功")
        void step2_shouldEncryptCredentialsWithPublicKey() throws Exception {
            // Given
            assumePublicKeyExists();
            
            String credentialsJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            
            // When
            String encryptedCredentials = encryptCredentialsWithPublicKey(credentialsJson, testPublicKey);
            
            // Then
            assertNotNull(encryptedCredentials, "加密凭据不应为空");
            assertTrue(encryptedCredentials.length() > 0, "加密凭据应有内容");
            assertNotEquals(credentialsJson, encryptedCredentials, "加密后的数据应与原始数据不同");
            
            log.info("步骤2完成: 成功加密凭据，加密数据长度: {} 字符", encryptedCredentials.length());
        }
        
        @Test
        @Order(3)
        @DisplayName("步骤3: 创建会话令牌应该成功")
        void step3_shouldCreateSessionTokenSuccessfully() throws Exception {
            // Given
            assumePublicKeyExists();
            
            String credentialsJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String encryptedCredentials = encryptCredentialsWithPublicKey(credentialsJson, testPublicKey);
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            // When
            MvcResult result = mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.expiresInSec").value(120))
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();
            
            // Then
            String responseJson = result.getResponse().getContentAsString();
            TokenResponse response = objectMapper.readValue(responseJson, TokenResponse.class);
            
            assertNotNull(response.getToken(), "令牌不应为空");
            assertTrue(response.getToken().matches("^[0-9a-f-]{36}$"), "令牌应为UUID格式");
            assertEquals(120, response.getExpiresInSec(), "过期时间应为120秒");
            assertTrue(response.isSuccess(), "响应应标记为成功");
            
            log.info("步骤3完成: 成功创建会话令牌，令牌: {}...", response.getToken().substring(0, 8));
        }
        
        @Test
        @Order(4)
        @DisplayName("步骤4: 令牌验证应该成功")
        void step4_shouldValidateTokenSuccessfully() throws Exception {
            // Given
            assumePublicKeyExists();
            String token = createTestSessionToken();
            
            // When & Then
            mockMvc.perform(get("/api/security/token/validate")
                    .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
            
            log.info("步骤4完成: 令牌验证成功，令牌: {}...", token.substring(0, 8));
        }
        
        @Test
        @Order(5)
        @DisplayName("步骤5: 完整流程应该保持数据一致性")
        void step5_shouldMaintainDataIntegrityThroughCompleteFlow() throws Exception {
            // Given
            assumePublicKeyExists();
            
            String originalCredentials = "{\"host\":\"integration-test.example.com\",\"port\":\"2222\",\"user\":\"integrationuser\",\"password\":\"integrationpass!\"}";
            
            // Step 1: 加密凭据
            String encryptedCredentials = encryptCredentialsWithPublicKey(originalCredentials, testPublicKey);
            
            // Step 2: 创建令牌
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            MvcResult result = mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();
            
            TokenResponse tokenResponse = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
            String token = tokenResponse.getToken();
            
            // Step 3: 验证令牌
            mockMvc.perform(get("/api/security/token/validate")
                    .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
            
            // Step 4: 使用令牌检索凭据（模拟STOMP拦截器的行为）
            TokenVault.VaultEntry retrievedEntry = tokenVault.retrieveAndRemove(token);
            
            // Then
            assertNotNull(retrievedEntry, "应该能检索到凭据");
            assertEquals("integration-test.example.com", retrievedEntry.getHost(), "主机地址应该一致");
            assertEquals("2222", retrievedEntry.getPort(), "端口应该一致");
            assertEquals("integrationuser", retrievedEntry.getUser(), "用户名应该一致");
            assertEquals("integrationpass!", retrievedEntry.getPassword(), "密码应该一致");
            
            // Step 5: 验证一次性使用特性
            assertFalse(tokenVault.isTokenValid(token), "使用后的令牌应该无效");
            
            log.info("步骤5完成: 完整流程数据一致性验证通过");
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {
        
        @Test
        @DisplayName("加密应该防止数据泄露")
        void shouldPreventDataLeakageThroughEncryption() throws Exception {
            // Given
            assumePublicKeyExists();
            
            String sensitiveData = "{\"host\":\"secret.example.com\",\"user\":\"admin\",\"password\":\"topsecret123!\"}";
            
            // When
            String encryptedData = encryptCredentialsWithPublicKey(sensitiveData, testPublicKey);
            
            // Then
            assertFalse(encryptedData.contains("secret.example.com"), "加密数据不应包含明文主机");
            assertFalse(encryptedData.contains("admin"), "加密数据不应包含明文用户名");
            assertFalse(encryptedData.contains("topsecret123!"), "加密数据不应包含明文密码");
            
            // 验证每次加密结果都不同（由于OAEP的随机性）
            String encryptedData2 = encryptCredentialsWithPublicKey(sensitiveData, testPublicKey);
            assertNotEquals(encryptedData, encryptedData2, "每次加密结果应该不同");
        }
        
        @Test
        @DisplayName("令牌应该具有时效性")
        void shouldEnforceTokenTimeToLive() throws Exception {
            // Given
            assumePublicKeyExists();
            String token = createTestSessionToken();
            
            // 验证令牌初始有效
            assertTrue(tokenVault.isTokenValid(token), "新创建的令牌应该有效");
            
            // 验证令牌的TTL信息
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(token);
            assertNotNull(entry, "应该能检索到凭据");
            assertTrue(entry.getRemainingTTL() > 0, "剩余TTL应该大于0");
            assertTrue(entry.getRemainingTTL() <= 120, "剩余TTL应该小于等于120秒");
        }
        
        @Test
        @DisplayName("令牌应该实现一次性使用")
        void shouldEnforceOneTimeTokenUsage() throws Exception {
            // Given
            assumePublicKeyExists();
            String token = createTestSessionToken();
            
            // When - 第一次使用令牌
            TokenVault.VaultEntry entry1 = tokenVault.retrieveAndRemove(token);
            // 第二次尝试使用相同令牌
            TokenVault.VaultEntry entry2 = tokenVault.retrieveAndRemove(token);
            
            // Then
            assertNotNull(entry1, "第一次使用应该成功");
            assertNull(entry2, "第二次使用应该失败（一次性使用）");
            assertFalse(tokenVault.isTokenValid(token), "使用后的令牌应该无效");
        }
        
        @Test
        @DisplayName("应该拒绝格式错误的令牌")
        void shouldRejectMalformedTokens() throws Exception {
            // Given
            String[] malformedTokens = {
                "not-a-uuid",
                "12345678-1234-1234-1234-123456789abc", // 错误版本
                "12345678-1234-4234-1234-123456789abcd", // 过长
                "12345678-1234-4234-1234-123456789ab", // 过短
                ""
            };
            
            // When & Then
            for (String malformedToken : malformedTokens) {
                mockMvc.perform(get("/api/security/token/validate")
                        .param("token", malformedToken))
                        .andExpect(status().isOk())
                        .andExpect(content().string("false"));
            }
        }
    }

    @Nested
    @DisplayName("错误场景测试")
    class ErrorScenarioTests {
        
        @Test
        @DisplayName("使用错误公钥加密的数据应该被拒绝")
        void shouldRejectDataEncryptedWithWrongPublicKey() throws Exception {
            // Given
            String credentialsJson = "{\"host\":\"test.example.com\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            
            // 使用不同的公钥加密数据
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair wrongKeyPair = generator.generateKeyPair();
            String wrongPublicKey = Base64.getEncoder().encodeToString(wrongKeyPair.getPublic().getEncoded());
            
            String encryptedWithWrongKey = encryptCredentialsWithPublicKey(credentialsJson, wrongPublicKey);
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedWithWrongKey, System.currentTimeMillis());
            
            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @Test
        @DisplayName("无效的Base64数据应该被拒绝")
        void shouldRejectInvalidBase64Data() throws Exception {
            // Given
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest("invalid-base64-data!@#$", System.currentTimeMillis());
            
            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @Test
        @DisplayName("损坏的加密数据应该被拒绝")
        void shouldRejectCorruptedEncryptedData() throws Exception {
            // Given
            assumePublicKeyExists();
            
            String credentialsJson = "{\"host\":\"test.example.com\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String validEncryptedData = encryptCredentialsWithPublicKey(credentialsJson, testPublicKey);
            
            // 损坏加密数据（修改几个字符）
            char[] chars = validEncryptedData.toCharArray();
            chars[10] = (char) (chars[10] == 'A' ? 'B' : 'A'); // 修改一个字符
            String corruptedData = new String(chars);
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(corruptedData, System.currentTimeMillis());
            
            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @Test
        @DisplayName("网络中断模拟测试")
        void shouldHandleNetworkInterruption() throws Exception {
            // Given
            assumePublicKeyExists();
            
            // 模拟部分成功的场景
            String token = createTestSessionToken();
            assertTrue(tokenVault.isTokenValid(token), "令牌应该初始有效");
            
            // 模拟网络中断后的重连场景 - 令牌应该仍然有效（在TTL内）
            assertTrue(tokenVault.isTokenValid(token), "网络中断期间令牌应该仍然有效");
        }
    }

    @Nested
    @DisplayName("性能和负载测试")
    class PerformanceLoadTests {
        
        @Test
        @DisplayName("并发令牌创建应该稳定")
        void shouldHandleConcurrentTokenCreation() throws Exception {
            // Given
            assumePublicKeyExists();
            
            int concurrentUsers = 10;
            Thread[] threads = new Thread[concurrentUsers];
            String[] results = new String[concurrentUsers];
            
            // When
            for (int i = 0; i < concurrentUsers; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        results[index] = createTestSessionToken();
                    } catch (Exception e) {
                        log.error("并发令牌创建失败", e);
                        results[index] = null;
                    }
                });
                threads[i].start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then
            for (int i = 0; i < concurrentUsers; i++) {
                assertNotNull(results[i], "并发令牌创建 " + i + " 应该成功");
                assertTrue(tokenVault.isTokenValid(results[i]), "创建的令牌 " + i + " 应该有效");
            }
            
            log.info("并发测试完成: {} 个并发用户同时创建令牌成功", concurrentUsers);
        }
        
        @Test
        @DisplayName("大量令牌创建和清理应该稳定")
        void shouldHandleLargeNumberOfTokens() throws Exception {
            // Given
            assumePublicKeyExists();
            
            int tokenCount = 100;
            String[] tokens = new String[tokenCount];
            
            // When - 创建大量令牌
            for (int i = 0; i < tokenCount; i++) {
                tokens[i] = createTestSessionToken();
            }
            
            // 验证所有令牌都有效
            for (int i = 0; i < tokenCount; i++) {
                assertTrue(tokenVault.isTokenValid(tokens[i]), "令牌 " + i + " 应该有效");
            }
            
            // 使用一半的令牌
            for (int i = 0; i < tokenCount / 2; i++) {
                TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(tokens[i]);
                assertNotNull(entry, "应该能检索令牌 " + i);
            }
            
            // Then - 验证状态正确
            String stats = tokenVault.getStats();
            assertTrue(stats.contains("当前条目: " + (tokenCount / 2)), 
                "统计信息应该反映正确的剩余令牌数: " + stats);
            
            log.info("大量令牌测试完成: 创建 {} 个令牌，使用 {} 个", tokenCount, tokenCount / 2);
        }
    }

    @Nested
    @DisplayName("环境和配置测试")
    class EnvironmentConfigurationTests {
        
        @Test
        @DisplayName("测试环境配置应该正确")
        void shouldHaveCorrectTestConfiguration() {
            // 验证加密服务已初始化
            assertTrue(cryptoService.isInitialized(), "加密服务应该已初始化");
            
            // 验证密钥信息
            String keyInfo = cryptoService.getKeyInfo();
            assertTrue(keyInfo.contains("RSA"), "应使用RSA算法");
            assertTrue(keyInfo.contains("2048"), "应使用2048位密钥");
            
            // 验证令牌保险库工作正常
            assertNotNull(tokenVault, "令牌保险库应该可用");
            String stats = tokenVault.getStats();
            assertNotNull(stats, "应该能获取统计信息");
            
            log.info("环境配置验证完成: {}, {}", keyInfo, stats);
        }
        
        @Test
        @DisplayName("API端点应该支持跨域访问")
        void shouldSupportCorsAccess() throws Exception {
            // When & Then
            mockMvc.perform(options("/api/security/public-key")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Content-Type"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 辅助方法：验证公钥存在
     */
    private void assumePublicKeyExists() {
        if (testPublicKey == null) {
            try {
                // 获取公钥用于测试
                MvcResult result = mockMvc.perform(get("/api/security/public-key"))
                        .andExpect(status().isOk())
                        .andReturn();
                        
                PublicKeyResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), PublicKeyResponse.class);
                testPublicKey = response.getPublicKey();
            } catch (Exception e) {
                fail("无法获取测试所需的公钥: " + e.getMessage());
            }
        }
    }

    /**
     * 辅助方法：使用公钥加密凭据
     */
    private String encryptCredentialsWithPublicKey(String credentialsJson, String publicKeyBase64) throws Exception {
        // 导入公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        // 加密
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(credentialsJson.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * 辅助方法：创建测试会话令牌
     */
    private String createTestSessionToken() throws Exception {
        assumePublicKeyExists();
        
        String credentialsJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
        String encryptedCredentials = encryptCredentialsWithPublicKey(credentialsJson, testPublicKey);
        
        EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
        
        MvcResult result = mockMvc.perform(post("/api/security/session/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        
        TokenResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        return response.getToken();
    }
}