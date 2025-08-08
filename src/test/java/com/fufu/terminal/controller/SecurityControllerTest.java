package com.fufu.terminal.controller;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SecurityController功能测试套件。
 * 
 * 测试安全控制器的REST API端点，包括公钥获取、令牌创建、
 * 令牌验证、错误处理等核心安全功能。
 * 
 * @author lizelin
 */
@Slf4j
@WebMvcTest(SecurityController.class)
@ActiveProfiles("test")
@DisplayName("安全控制器测试")
class SecurityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CryptoService cryptoService;

    @MockBean
    private TokenVault tokenVault;

    private ObjectMapper objectMapper;
    private String testPublicKeyBase64;
    private PublicKey testPublicKey;
    private PrivateKey testPrivateKey;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // 生成测试用的RSA密钥对
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        testPublicKey = keyPair.getPublic();
        testPrivateKey = keyPair.getPrivate();
        testPublicKeyBase64 = Base64.getEncoder().encodeToString(testPublicKey.getEncoded());
    }

    @Nested
    @DisplayName("公钥获取API测试")
    class PublicKeyApiTests {
        
        @Test
        @DisplayName("应该成功返回公钥")
        void shouldReturnPublicKeySuccessfully() throws Exception {
            // Given
            when(cryptoService.isInitialized()).thenReturn(true);
            when(cryptoService.getPublicKeyBase64()).thenReturn(testPublicKeyBase64);
            when(cryptoService.getKeyInfo()).thenReturn("算法: RSA, 长度: 2048 bits, 格式: X.509");

            // When & Then
            mockMvc.perform(get("/api/security/public-key")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.publicKey").value(testPublicKeyBase64))
                    .andExpect(jsonPath("$.algorithm").value("RSA"))
                    .andExpect(jsonPath("$.keyLength").value(2048))
                    .andExpect(jsonPath("$.timestamp").isNumber());
        }
        
        @Test
        @DisplayName("加密服务未初始化时应该返回500错误")
        void shouldReturn500WhenCryptoServiceNotInitialized() throws Exception {
            // Given
            when(cryptoService.isInitialized()).thenReturn(false);

            // When & Then
            mockMvc.perform(get("/api/security/public-key")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }
        
        @Test
        @DisplayName("加密服务抛出异常时应该返回500错误")
        void shouldReturn500WhenCryptoServiceThrowsException() throws Exception {
            // Given
            when(cryptoService.isInitialized()).thenReturn(true);
            when(cryptoService.getPublicKeyBase64()).thenThrow(new RuntimeException("密钥生成失败"));

            // When & Then
            mockMvc.perform(get("/api/security/public-key")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }
        
        @Test
        @DisplayName("应该支持CORS跨域请求")
        void shouldSupportCorsRequests() throws Exception {
            // Given
            when(cryptoService.isInitialized()).thenReturn(true);
            when(cryptoService.getPublicKeyBase64()).thenReturn(testPublicKeyBase64);
            when(cryptoService.getKeyInfo()).thenReturn("算法: RSA, 长度: 2048 bits");

            // When & Then - 预检请求
            mockMvc.perform(options("/api/security/public-key")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("令牌创建API测试")
    class TokenCreationApiTests {
        
        @Test
        @DisplayName("应该成功创建会话令牌")
        void shouldCreateSessionTokenSuccessfully() throws Exception {
            // Given
            String credentialsJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String encryptedCredentials = encryptCredentials(credentialsJson);
            String expectedToken = "12345678-1234-4234-8234-123456789abc";
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsJson);
            when(tokenVault.storeCredentials("test.example.com", "22", "testuser", "testpass"))
                    .thenReturn(expectedToken);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.token").value(expectedToken))
                    .andExpect(jsonPath("$.expiresInSec").value(120))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.timestamp").isNumber());
        }
        
        @Test
        @DisplayName("应该正确处理缺少端口的凭据")
        void shouldHandleCredentialsWithoutPort() throws Exception {
            // Given
            String credentialsJson = "{\"host\":\"test.example.com\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String encryptedCredentials = encryptCredentials(credentialsJson);
            String expectedToken = "12345678-1234-4234-8234-123456789abc";
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsJson);
            when(tokenVault.storeCredentials("test.example.com", "22", "testuser", "testpass"))
                    .thenReturn(expectedToken);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(expectedToken))
                    .andExpect(jsonPath("$.success").value(true));
        }
        
        @Test
        @DisplayName("空的加密凭据应该返回400错误")
        void shouldReturn400ForEmptyEncryptedCredentials() throws Exception {
            // Given
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest("", System.currentTimeMillis());

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("解密失败应该返回400错误")
        void shouldReturn400ForDecryptionFailure() throws Exception {
            // Given
            String invalidEncryptedCredentials = "invalid-encrypted-data";
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(invalidEncryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(invalidEncryptedCredentials))
                    .thenThrow(new IllegalArgumentException("解密失败"));

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "{\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}", // 缺少host
            "{\"host\":\"test.example.com\",\"port\":\"22\",\"password\":\"testpass\"}", // 缺少user
            "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\"}", // 缺少password
            "{\"host\":\"\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}", // 空host
            "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"\",\"password\":\"testpass\"}", // 空user
        })
        @DisplayName("无效凭据格式应该返回400错误")
        void shouldReturn400ForInvalidCredentialsFormat(String invalidCredentialsJson) throws Exception {
            // Given
            String encryptedCredentials = encryptCredentials(invalidCredentialsJson);
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(invalidCredentialsJson);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @Test
        @DisplayName("令牌存储失败应该返回500错误")
        void shouldReturn500ForTokenStorageFailure() throws Exception {
            // Given
            String credentialsJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String encryptedCredentials = encryptCredentials(credentialsJson);
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsJson);
            when(tokenVault.storeCredentials(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("存储失败"));

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
        
        @Test
        @DisplayName("应该验证凭据字段长度限制")
        void shouldValidateCredentialsFieldLengths() throws Exception {
            // Given - 创建超长字段的凭据
            String longHost = "a".repeat(300); // 超过255字符限制
            String credentialsJson = String.format("{\"host\":\"%s\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}", longHost);
            String encryptedCredentials = encryptCredentials(credentialsJson);
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsJson);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("令牌验证API测试")
    class TokenValidationApiTests {
        
        @Test
        @DisplayName("有效令牌应该返回true")
        void shouldReturnTrueForValidToken() throws Exception {
            // Given
            String validToken = "12345678-1234-4234-8234-123456789abc";
            when(tokenVault.isTokenValid(validToken)).thenReturn(true);

            // When & Then
            mockMvc.perform(get("/api/security/token/validate")
                    .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }
        
        @Test
        @DisplayName("无效令牌应该返回false")
        void shouldReturnFalseForInvalidToken() throws Exception {
            // Given
            String invalidToken = "invalid-token";
            when(tokenVault.isTokenValid(invalidToken)).thenReturn(false);

            // When & Then
            mockMvc.perform(get("/api/security/token/validate")
                    .param("token", invalidToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
        
        @Test
        @DisplayName("令牌验证异常应该返回false")
        void shouldReturnFalseForTokenValidationException() throws Exception {
            // Given
            String token = "test-token";
            when(tokenVault.isTokenValid(token)).thenThrow(new RuntimeException("验证失败"));

            // When & Then
            mockMvc.perform(get("/api/security/token/validate")
                    .param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
        
        @Test
        @DisplayName("缺少令牌参数应该返回false")
        void shouldReturnFalseForMissingTokenParameter() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/security/token/validate"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("保险库统计API测试")
    class VaultStatsApiTests {
        
        @Test
        @DisplayName("应该成功返回保险库统计信息")
        void shouldReturnVaultStatsSuccessfully() throws Exception {
            // Given
            String expectedStats = "TokenVault统计 - 当前条目: 5, 创建总数: 10, 检索总数: 3, 过期总数: 2, 最大容量: 10000";
            when(tokenVault.getStats()).thenReturn(expectedStats);

            // When & Then
            mockMvc.perform(get("/api/security/vault/stats"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(expectedStats));
        }
        
        @Test
        @DisplayName("统计信息获取异常应该返回500错误")
        void shouldReturn500ForStatsException() throws Exception {
            // Given
            when(tokenVault.getStats()).thenThrow(new RuntimeException("统计信息获取失败"));

            // When & Then
            mockMvc.perform(get("/api/security/vault/stats"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string("获取统计信息失败"));
        }
    }

    @Nested
    @DisplayName("安全性和边界测试")
    class SecurityBoundaryTests {
        
        @Test
        @DisplayName("超大请求体应该被拒绝")
        void shouldRejectOversizedRequestBody() throws Exception {
            // Given - 创建超大的请求体
            String largeData = "a".repeat(1024 * 1024); // 1MB数据
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(largeData, System.currentTimeMillis());

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }
        
        @Test
        @DisplayName("无效的Content-Type应该被拒绝")
        void shouldRejectInvalidContentType() throws Exception {
            // Given
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest("test", System.currentTimeMillis());

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }
        
        @Test
        @DisplayName("格式错误的JSON应该被拒绝")
        void shouldRejectMalformedJson() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json"))
                    .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("应该正确处理特殊字符")
        void shouldHandleSpecialCharacters() throws Exception {
            // Given
            String credentialsWithSpecialChars = "{\"host\":\"test-server.example.com\",\"port\":\"22\"," +
                "\"user\":\"user@domain.com\",\"password\":\"P@ssw0rd!#$%^&*()\"}";
            String encryptedCredentials = encryptCredentials(credentialsWithSpecialChars);
            String expectedToken = "12345678-1234-4234-8234-123456789abc";
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsWithSpecialChars);
            when(tokenVault.storeCredentials("test-server.example.com", "22", "user@domain.com", "P@ssw0rd!#$%^&*()"))
                    .thenReturn(expectedToken);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(expectedToken))
                    .andExpect(jsonPath("$.success").value(true));
        }
        
        @Test
        @DisplayName("应该正确处理Unicode字符")
        void shouldHandleUnicodeCharacters() throws Exception {
            // Given
            String credentialsWithUnicode = "{\"host\":\"测试服务器.example.com\",\"port\":\"22\"," +
                "\"user\":\"用户名\",\"password\":\"密码123\"}";
            String encryptedCredentials = encryptCredentials(credentialsWithUnicode);
            String expectedToken = "12345678-1234-4234-8234-123456789abc";
            
            EncryptedCredentialsRequest request = new EncryptedCredentialsRequest(encryptedCredentials, System.currentTimeMillis());
            
            when(cryptoService.decryptCredentials(encryptedCredentials)).thenReturn(credentialsWithUnicode);
            when(tokenVault.storeCredentials("测试服务器.example.com", "22", "用户名", "密码123"))
                    .thenReturn(expectedToken);

            // When & Then
            mockMvc.perform(post("/api/security/session/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(expectedToken))
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    /**
     * 辅助方法：使用测试公钥加密凭据数据
     */
    private String encryptCredentials(String credentialsJson) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, testPublicKey);
        byte[] encryptedBytes = cipher.doFinal(credentialsJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}