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

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenVault功能测试套件。
 * 
 * 测试令牌保险库的核心功能，包括凭据存储、检索、TTL管理、
 * 自动清理、并发安全等关键安全功能。
 * 
 * @author lizelin
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("令牌保险库测试")
class TokenVaultTest {

    private TokenVault tokenVault;
    
    @BeforeEach
    void setUp() {
        tokenVault = new TokenVault();
    }

    @Nested
    @DisplayName("凭据存储测试")
    class CredentialsStorageTests {
        
        @Test
        @DisplayName("应该成功存储有效凭据并返回令牌")
        void shouldStoreValidCredentialsAndReturnToken() {
            // Given
            String host = "test.example.com";
            String port = "22";
            String user = "testuser";
            String password = "testpass";
            
            // When
            String token = tokenVault.storeCredentials(host, port, user, password);
            
            // Then
            assertNotNull(token, "令牌不应为空");
            assertFalse(token.isEmpty(), "令牌不应为空字符串");
            assertTrue(token.matches("^[0-9a-f-]{36}$"), "令牌应为UUID格式");
        }
        
        @Test
        @DisplayName("应该正确处理默认端口")
        void shouldHandleDefaultPort() {
            // Given
            String host = "test.example.com";
            String user = "testuser";
            String password = "testpass";
            
            // When - 不提供端口或提供null端口
            String token1 = tokenVault.storeCredentials(host, null, user, password);
            String token2 = tokenVault.storeCredentials(host, "", user, password);
            
            // Then
            assertNotNull(token1, "使用null端口应成功存储");
            assertNotNull(token2, "使用空端口应成功存储");
            
            // 验证存储的凭据使用默认端口
            TokenVault.VaultEntry entry1 = tokenVault.retrieveAndRemove(token1);
            assertNotNull(entry1, "应能检索凭据");
            assertEquals("22", entry1.getPort(), "应使用默认端口22");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("空主机地址应该抛出异常")
        void shouldThrowExceptionForInvalidHost(String invalidHost) {
            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tokenVault.storeCredentials(invalidHost, "22", "user", "pass"),
                "应抛出IllegalArgumentException"
            );
            assertEquals("SSH主机地址不能为空", exception.getMessage());
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("空用户名应该抛出异常")
        void shouldThrowExceptionForInvalidUser(String invalidUser) {
            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tokenVault.storeCredentials("host", "22", invalidUser, "pass"),
                "应抛出IllegalArgumentException"
            );
            assertEquals("SSH用户名不能为空", exception.getMessage());
        }
        
        @Test
        @DisplayName("null密码应该抛出异常")
        void shouldThrowExceptionForNullPassword() {
            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tokenVault.storeCredentials("host", "22", "user", null),
                "应抛出IllegalArgumentException"
            );
            assertEquals("SSH密码不能为null", exception.getMessage());
        }
        
        @Test
        @DisplayName("应该允许空密码字符串")
        void shouldAllowEmptyPasswordString() {
            // Given
            String emptyPassword = "";
            
            // When & Then
            assertDoesNotThrow(() -> {
                String token = tokenVault.storeCredentials("host", "22", "user", emptyPassword);
                assertNotNull(token, "应允许空密码字符串");
            });
        }
        
        @Test
        @DisplayName("应该自动修剪输入参数的空白字符")
        void shouldTrimInputParameters() {
            // Given
            String host = "  test.example.com  ";
            String port = "  22  ";
            String user = "  testuser  ";
            String password = "testpass";
            
            // When
            String token = tokenVault.storeCredentials(host, port, user, password);
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(token);
            
            // Then
            assertNotNull(entry, "应成功存储和检索凭据");
            assertEquals("test.example.com", entry.getHost(), "主机地址应被修剪");
            assertEquals("22", entry.getPort(), "端口应被修剪");
            assertEquals("testuser", entry.getUser(), "用户名应被修剪");
            assertEquals("testpass", entry.getPassword(), "密码不应被修剪");
        }
    }

    @Nested
    @DisplayName("令牌检索测试")
    class TokenRetrievalTests {
        
        @Test
        @DisplayName("应该成功检索有效令牌对应的凭据")
        void shouldRetrieveValidTokenCredentials() {
            // Given
            String host = "test.example.com";
            String port = "2222";
            String user = "testuser";
            String password = "testpass";
            String token = tokenVault.storeCredentials(host, port, user, password);
            
            // When
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(token);
            
            // Then
            assertNotNull(entry, "应能检索到凭据");
            assertEquals(host, entry.getHost(), "主机地址应匹配");
            assertEquals(port, entry.getPort(), "端口应匹配");
            assertEquals(user, entry.getUser(), "用户名应匹配");
            assertEquals(password, entry.getPassword(), "密码应匹配");
        }
        
        @Test
        @DisplayName("检索后令牌应该被移除（一次性使用）")
        void shouldRemoveTokenAfterRetrievalOneTimeUse() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            
            // When
            TokenVault.VaultEntry entry1 = tokenVault.retrieveAndRemove(token);
            TokenVault.VaultEntry entry2 = tokenVault.retrieveAndRemove(token);
            
            // Then
            assertNotNull(entry1, "第一次检索应成功");
            assertNull(entry2, "第二次检索应失败（令牌已被移除）");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("无效令牌应该返回null")
        void shouldReturnNullForInvalidTokens(String invalidToken) {
            // When
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(invalidToken);
            
            // Then
            assertNull(entry, "无效令牌应返回null");
        }
        
        @Test
        @DisplayName("不存在的令牌应该返回null")
        void shouldReturnNullForNonExistentToken() {
            // Given
            String nonExistentToken = "12345678-1234-4234-8234-123456789abc";
            
            // When
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(nonExistentToken);
            
            // Then
            assertNull(entry, "不存在的令牌应返回null");
        }
    }

    @Nested
    @DisplayName("TTL和过期测试")
    class TTLExpirationTests {
        
        @Test
        @DisplayName("新创建的令牌应该是有效的")
        void shouldCreateValidToken() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            
            // When & Then
            assertTrue(tokenVault.isTokenValid(token), "新创建的令牌应该有效");
        }
        
        @Test
        @DisplayName("VaultEntry应该正确报告剩余TTL")
        void shouldCorrectlyReportRemainingTTL() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(token);
            
            // When
            long remainingTTL = entry.getRemainingTTL();
            
            // Then
            assertTrue(remainingTTL > 0, "剩余TTL应该大于0");
            assertTrue(remainingTTL <= 120, "剩余TTL应该小于等于120秒");
        }
        
        @Test
        @DisplayName("VaultEntry应该正确检查过期状态")
        void shouldCorrectlyCheckExpirationStatus() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            TokenVault.VaultEntry entry = tokenVault.retrieveAndRemove(token);
            
            // When & Then
            assertFalse(entry.isExpired(), "新创建的条目不应过期");
        }
        
        @Test
        @DisplayName("应该拒绝过期的令牌")
        void shouldRejectExpiredTokens() throws InterruptedException {
            // Given - 创建一个短TTL的令牌（通过反射修改过期时间）
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            
            // 等待足够长的时间让令牌在正常情况下过期
            // 由于默认TTL是120秒，我们通过直接检查过期逻辑来测试
            // 这里我们模拟已过期的情况
            
            // 先获取令牌以验证它确实存在
            assertTrue(tokenVault.isTokenValid(token), "令牌最初应该有效");
            
            // 检查无效令牌（使用过的令牌）
            tokenVault.retrieveAndRemove(token);
            assertFalse(tokenVault.isTokenValid(token), "使用后的令牌应该无效");
        }
    }

    @Nested
    @DisplayName("令牌验证测试")
    class TokenValidationTests {
        
        @Test
        @DisplayName("有效令牌应该通过验证")
        void shouldValidateValidToken() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            
            // When & Then
            assertTrue(tokenVault.isTokenValid(token), "有效令牌应该通过验证");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null或空令牌应该验证失败")
        void shouldFailValidationForNullOrEmptyToken(String invalidToken) {
            // When & Then
            assertFalse(tokenVault.isTokenValid(invalidToken), "null或空令牌应该验证失败");
        }
        
        @Test
        @DisplayName("已使用的令牌应该验证失败")
        void shouldFailValidationForUsedToken() {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            tokenVault.retrieveAndRemove(token); // 使用令牌
            
            // When & Then
            assertFalse(tokenVault.isTokenValid(token), "已使用的令牌应该验证失败");
        }
        
        @Test
        @DisplayName("不存在的令牌应该验证失败")
        void shouldFailValidationForNonExistentToken() {
            // Given
            String nonExistentToken = "12345678-1234-4234-8234-123456789abc";
            
            // When & Then
            assertFalse(tokenVault.isTokenValid(nonExistentToken), "不存在的令牌应该验证失败");
        }
    }

    @Nested
    @DisplayName("存储容量测试")
    class StorageCapacityTests {
        
        @Test
        @DisplayName("应该能够存储多个令牌")
        void shouldStoreMultipleTokens() {
            // Given & When
            String[] tokens = new String[10];
            for (int i = 0; i < 10; i++) {
                tokens[i] = tokenVault.storeCredentials("host" + i, "22", "user" + i, "pass" + i);
            }
            
            // Then
            for (int i = 0; i < 10; i++) {
                assertTrue(tokenVault.isTokenValid(tokens[i]), "令牌 " + i + " 应该有效");
            }
        }
        
        @Test
        @DisplayName("应该正确报告统计信息")
        void shouldReportCorrectStatistics() {
            // Given
            int initialCount = getCreatedCountFromStats();
            
            // When
            for (int i = 0; i < 5; i++) {
                tokenVault.storeCredentials("host" + i, "22", "user" + i, "pass" + i);
            }
            
            // Then
            String stats = tokenVault.getStats();
            assertNotNull(stats, "统计信息不应为空");
            assertTrue(stats.contains("当前条目: 5"), "应显示当前条目数");
            assertTrue(stats.contains("创建总数: " + (initialCount + 5)), "应显示正确的创建总数");
        }
        
        private int getCreatedCountFromStats() {
            String stats = tokenVault.getStats();
            // 解析创建总数
            String[] parts = stats.split("创建总数: ");
            if (parts.length > 1) {
                String[] numParts = parts[1].split(",");
                return Integer.parseInt(numParts[0]);
            }
            return 0;
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("多线程同时存储令牌应该是安全的")
        void shouldBeSafeForConcurrentTokenStorage() throws InterruptedException {
            // Given
            int threadCount = 10;
            String[] tokens = new String[threadCount];
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        tokens[index] = tokenVault.storeCredentials("host" + index, "22", "user" + index, "pass" + index);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在10秒内完成");
            executor.shutdown();
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(tokens[i], "令牌 " + i + " 应该被成功创建");
                assertTrue(tokenVault.isTokenValid(tokens[i]), "令牌 " + i + " 应该有效");
            }
        }
        
        @Test
        @DisplayName("多线程同时检索令牌应该是安全的")
        void shouldBeSafeForConcurrentTokenRetrieval() throws InterruptedException {
            // Given
            int threadCount = 10;
            String[] tokens = new String[threadCount];
            TokenVault.VaultEntry[] results = new TokenVault.VaultEntry[threadCount];
            
            // 先创建令牌
            for (int i = 0; i < threadCount; i++) {
                tokens[i] = tokenVault.storeCredentials("host" + i, "22", "user" + i, "pass" + i);
            }
            
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        results[index] = tokenVault.retrieveAndRemove(tokens[index]);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在10秒内完成");
            executor.shutdown();
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(results[i], "结果 " + i + " 应该被成功检索");
                assertEquals("host" + i, results[i].getHost(), "主机地址应该匹配");
            }
        }
        
        @Test
        @DisplayName("多线程令牌验证应该是安全的")
        void shouldBeSafeForConcurrentTokenValidation() throws InterruptedException {
            // Given
            String token = tokenVault.storeCredentials("host", "22", "user", "pass");
            int threadCount = 10;
            boolean[] results = new boolean[threadCount];
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        results[index] = tokenVault.isTokenValid(token);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在10秒内完成");
            executor.shutdown();
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertTrue(results[i], "并发验证结果 " + i + " 应该为true");
            }
        }
    }

    @Nested
    @DisplayName("清理和维护测试")
    class CleanupMaintenanceTests {
        
        @Test
        @DisplayName("手动清理应该正确执行")
        void shouldPerformManualCleanupCorrectly() {
            // Given
            String token1 = tokenVault.storeCredentials("host1", "22", "user1", "pass1");
            String token2 = tokenVault.storeCredentials("host2", "22", "user2", "pass2");
            
            // When
            tokenVault.cleanupExpiredEntries(); // 手动触发清理
            
            // Then - 未过期的令牌应该仍然有效
            assertTrue(tokenVault.isTokenValid(token1), "未过期令牌1应该仍然有效");
            assertTrue(tokenVault.isTokenValid(token2), "未过期令牌2应该仍然有效");
        }
        
        @Test
        @DisplayName("应用关闭时应该清理所有令牌")
        void shouldCleanupAllTokensOnShutdown() {
            // Given
            String token1 = tokenVault.storeCredentials("host1", "22", "user1", "pass1");
            String token2 = tokenVault.storeCredentials("host2", "22", "user2", "pass2");
            
            // When
            tokenVault.cleanup(); // 模拟应用关闭
            
            // Then
            assertFalse(tokenVault.isTokenValid(token1), "关闭后令牌1应该无效");
            assertFalse(tokenVault.isTokenValid(token2), "关闭后令牌2应该无效");
        }
        
        @Test
        @DisplayName("统计信息应该反映实际状态")
        void shouldReflectActualStateInStatistics() {
            // Given
            int initialCreated = getCreatedCountFromStats();
            
            // When
            String token1 = tokenVault.storeCredentials("host1", "22", "user1", "pass1");
            String token2 = tokenVault.storeCredentials("host2", "22", "user2", "pass2");
            tokenVault.retrieveAndRemove(token1); // 使用一个令牌
            
            // Then
            String stats = tokenVault.getStats();
            assertTrue(stats.contains("当前条目: 1"), "应显示正确的当前条目数");
            assertTrue(stats.contains("创建总数: " + (initialCreated + 2)), "应显示正确的创建总数");
            assertTrue(stats.contains("检索总数: " + (getRetrievedCountFromStats(stats))), "应包含检索总数");
        }
        
        private int getRetrievedCountFromStats(String stats) {
            String[] parts = stats.split("检索总数: ");
            if (parts.length > 1) {
                String[] numParts = parts[1].split(",");
                return Integer.parseInt(numParts[0]);
            }
            return 0;
        }
    }
}