package com.fufu.terminal.security;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoService功能测试套件。
 * 
 * 测试RSA加密服务的核心功能，包括密钥生成、公钥获取、
 * 凭据加密解密、错误处理等关键安全功能。
 * 
 * @author lizelin
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RSA加密服务测试")
class CryptoServiceTest {

    private CryptoService cryptoService;
    
    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        cryptoService.generateKeys();
    }

    @Nested
    @DisplayName("密钥生成和初始化测试")
    class KeyGenerationTests {
        
        @Test
        @DisplayName("应该成功生成RSA密钥对")
        void shouldGenerateRSAKeyPairSuccessfully() {
            // Given & When - setUp已执行密钥生成
            
            // Then
            assertTrue(cryptoService.isInitialized(), "密钥应该已初始化");
            assertNotNull(cryptoService.getPublicKeyBase64(), "公钥不应为空");
            assertTrue(cryptoService.getPublicKeyBase64().length() > 0, "公钥长度应大于0");
        }
        
        @Test
        @DisplayName("密钥信息应该包含正确的算法和长度")
        void shouldProvideCorrectKeyInfo() {
            // When
            String keyInfo = cryptoService.getKeyInfo();
            
            // Then
            assertNotNull(keyInfo, "密钥信息不应为空");
            assertTrue(keyInfo.contains("RSA"), "应包含RSA算法信息");
            assertTrue(keyInfo.contains("2048"), "应包含2048位密钥长度信息");
        }
        
        @Test
        @DisplayName("未初始化时应该返回未初始化状态")
        void shouldReturnUninitializedStatusWhenNotInitialized() {
            // Given
            CryptoService uninitializedService = new CryptoService();
            
            // When & Then
            assertFalse(uninitializedService.isInitialized(), "未初始化的服务应返回false");
            assertEquals("密钥未初始化", uninitializedService.getKeyInfo(), "应返回未初始化信息");
        }
        
        @Test
        @DisplayName("未初始化时获取公钥应该抛出异常")
        void shouldThrowExceptionWhenGettingPublicKeyWithoutInitialization() {
            // Given
            CryptoService uninitializedService = new CryptoService();
            
            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class, 
                uninitializedService::getPublicKeyBase64,
                "应抛出IllegalStateException"
            );
            assertEquals("RSA密钥尚未初始化", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("公钥获取测试")
    class PublicKeyTests {
        
        @Test
        @DisplayName("公钥应该是有效的Base64格式")
        void shouldReturnValidBase64PublicKey() {
            // When
            String publicKeyBase64 = cryptoService.getPublicKeyBase64();
            
            // Then
            assertNotNull(publicKeyBase64, "公钥不应为空");
            assertDoesNotThrow(() -> {
                byte[] decoded = Base64.getDecoder().decode(publicKeyBase64);
                assertTrue(decoded.length > 0, "解码后的公钥应有内容");
            }, "公钥应该是有效的Base64格式");
        }
        
        @Test
        @DisplayName("多次获取公钥应该返回相同结果")
        void shouldReturnConsistentPublicKey() {
            // When
            String publicKey1 = cryptoService.getPublicKeyBase64();
            String publicKey2 = cryptoService.getPublicKeyBase64();
            
            // Then
            assertEquals(publicKey1, publicKey2, "多次获取应返回相同的公钥");
        }
        
        @Test
        @DisplayName("公钥长度应该符合RSA-2048标准")
        void shouldReturnCorrectPublicKeyLength() {
            // When
            String publicKeyBase64 = cryptoService.getPublicKeyBase64();
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            
            // Then
            // RSA-2048公钥的DER编码长度通常在294字节左右
            assertTrue(publicKeyBytes.length > 250 && publicKeyBytes.length < 400, 
                "公钥字节长度应在合理范围内: " + publicKeyBytes.length);
        }
    }

    @Nested
    @DisplayName("凭据解密测试")
    class DecryptionTests {
        
        @Test
        @DisplayName("应该成功解密有效的加密凭据")
        void shouldDecryptValidCredentialsSuccessfully() throws Exception {
            // Given
            String originalJson = "{\"host\":\"test.example.com\",\"port\":\"22\",\"user\":\"testuser\",\"password\":\"testpass\"}";
            String encryptedData = encryptWithPublicKey(originalJson, cryptoService.getPublicKeyBase64());
            
            // When
            String decryptedJson = cryptoService.decryptCredentials(encryptedData);
            
            // Then
            assertNotNull(decryptedJson, "解密结果不应为空");
            assertEquals(originalJson, decryptedJson, "解密后的内容应与原始内容一致");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("空或无效加密数据应该抛出异常")
        void shouldThrowExceptionForInvalidEncryptedData(String invalidData) {
            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cryptoService.decryptCredentials(invalidData),
                "应抛出IllegalArgumentException"
            );
            assertEquals("加密数据不能为空", exception.getMessage());
        }
        
        @Test
        @DisplayName("无效的Base64数据应该抛出异常")
        void shouldThrowExceptionForInvalidBase64Data() {
            // Given
            String invalidBase64 = "这不是有效的Base64数据!@#$%";
            
            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cryptoService.decryptCredentials(invalidBase64),
                "应抛出IllegalArgumentException"
            );
            assertEquals("无效的加密数据格式", exception.getMessage());
        }
        
        @Test
        @DisplayName("使用错误密钥加密的数据应该抛出解密异常")
        void shouldThrowExceptionForWronglyEncryptedData() throws Exception {
            // Given - 使用不同的密钥对加密数据
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            PublicKey wrongPublicKey = generator.generateKeyPair().getPublic();
            
            String originalJson = "{\"host\":\"test\",\"user\":\"test\",\"password\":\"test\"}";
            String wronglyEncryptedData = encryptWithPublicKey(originalJson, 
                Base64.getEncoder().encodeToString(wrongPublicKey.getEncoded()));
            
            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> cryptoService.decryptCredentials(wronglyEncryptedData),
                "应抛出IllegalStateException"
            );
            assertEquals("凭据解密过程出错", exception.getMessage());
        }
        
        @Test
        @DisplayName("未初始化时解密应该抛出异常")
        void shouldThrowExceptionWhenDecryptingWithoutInitialization() {
            // Given
            CryptoService uninitializedService = new CryptoService();
            String someEncryptedData = "dGVzdA=="; // base64 encoded "test"
            
            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> uninitializedService.decryptCredentials(someEncryptedData),
                "应抛出IllegalStateException"
            );
            assertEquals("RSA私钥尚未初始化", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("加密解密往返测试")
    class RoundTripTests {
        
        @Test
        @DisplayName("完整的加密解密流程应该保持数据一致性")
        void shouldMaintainDataIntegrityThroughEncryptionDecryptionCycle() throws Exception {
            // Given
            String originalJson = "{\"host\":\"192.168.1.100\",\"port\":\"2222\",\"user\":\"admin\",\"password\":\"secret123!\"}";
            
            // When - 加密然后解密
            String encryptedData = encryptWithPublicKey(originalJson, cryptoService.getPublicKeyBase64());
            String decryptedJson = cryptoService.decryptCredentials(encryptedData);
            
            // Then
            assertEquals(originalJson, decryptedJson, "往返加密解密应保持数据一致性");
        }
        
        @Test
        @DisplayName("应该能够处理包含特殊字符的凭据")
        void shouldHandleSpecialCharactersInCredentials() throws Exception {
            // Given - 包含特殊字符的凭据
            String jsonWithSpecialChars = "{\"host\":\"test-server.example.com\",\"port\":\"22\"," +
                "\"user\":\"user@domain.com\",\"password\":\"P@ssw0rd!#$%^&*()_+\"}";
            
            // When
            String encryptedData = encryptWithPublicKey(jsonWithSpecialChars, cryptoService.getPublicKeyBase64());
            String decryptedJson = cryptoService.decryptCredentials(encryptedData);
            
            // Then
            assertEquals(jsonWithSpecialChars, decryptedJson, "应能正确处理特殊字符");
        }
        
        @Test
        @DisplayName("应该能够处理Unicode字符")
        void shouldHandleUnicodeCharacters() throws Exception {
            // Given - 包含Unicode字符的凭据
            String jsonWithUnicode = "{\"host\":\"测试服务器.example.com\",\"port\":\"22\"," +
                "\"user\":\"用户名\",\"password\":\"密码123\"}";
            
            // When
            String encryptedData = encryptWithPublicKey(jsonWithUnicode, cryptoService.getPublicKeyBase64());
            String decryptedJson = cryptoService.decryptCredentials(encryptedData);
            
            // Then
            assertEquals(jsonWithUnicode, decryptedJson, "应能正确处理Unicode字符");
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("多线程同时获取公钥应该是安全的")
        void shouldBeSafeForConcurrentPublicKeyAccess() throws InterruptedException {
            // Given
            int threadCount = 10;
            String[] results = new String[threadCount];
            Thread[] threads = new Thread[threadCount];
            
            // When - 多线程同时获取公钥
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = cryptoService.getPublicKeyBase64();
                });
                threads[i].start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then - 所有结果应该相同
            String firstResult = results[0];
            for (int i = 1; i < threadCount; i++) {
                assertEquals(firstResult, results[i], "并发获取公钥应返回一致结果");
            }
        }
        
        @Test
        @DisplayName("多线程同时解密应该是安全的")
        void shouldBeSafeForConcurrentDecryption() throws Exception {
            // Given
            String originalJson = "{\"host\":\"concurrent-test\",\"user\":\"test\",\"password\":\"test\"}";
            String encryptedData = encryptWithPublicKey(originalJson, cryptoService.getPublicKeyBase64());
            
            int threadCount = 10;
            String[] results = new String[threadCount];
            Thread[] threads = new Thread[threadCount];
            
            // When - 多线程同时解密
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = cryptoService.decryptCredentials(encryptedData);
                });
                threads[i].start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then - 所有解密结果应该相同
            for (int i = 0; i < threadCount; i++) {
                assertEquals(originalJson, results[i], "并发解密应返回一致结果");
            }
        }
    }

    /**
     * 辅助方法：使用公钥加密数据（模拟前端加密过程）
     */
    private String encryptWithPublicKey(String data, String publicKeyBase64) throws Exception {
        // 导入公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        // 加密
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}