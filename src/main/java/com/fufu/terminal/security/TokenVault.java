package com.fufu.terminal.security;

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 令牌保险库，用于安全存储临时凭据。
 * <p>
 * 该组件实现了基于TTL（生存时间）的临时凭据存储机制，
 * 确保敏感凭据不会长期驻留在内存中，提高系统安全性。
 * </p>
 *
 * <ul>
 *     <li>TTL机制：默认2分钟过期，防止凭据泄露</li>
 *     <li>自动清理：定时任务清理过期条目</li>
 *     <li>一次性使用：取出即删除，防止重放攻击</li>
 *     <li>并发安全：使用ConcurrentHashMap保证线程安全</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
public class TokenVault {

    /** 默认TTL：2分钟（120秒） */
    private static final long DEFAULT_TTL_SECONDS = 120;

    /** 清理任务执行间隔：30秒 */
    private static final long CLEANUP_INTERVAL_MS = 30_000;

    /** 最大存储条目数，防止内存耗尽 */
    private static final int MAX_ENTRIES = 10_000;
    /**
     * 维护当前有效条目数
     */
    private final AtomicInteger currentSize = new AtomicInteger(0);


    /** 凭据存储映射表 */
    private final ConcurrentHashMap<String, VaultEntry> vault = new ConcurrentHashMap<>();

    /** 统计计数器 */
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalExpired = new AtomicInteger(0);
    private final AtomicInteger totalRetrieved = new AtomicInteger(0);


    /**
     * 确保参数非空
     * @param value 参数值
     * @param name 参数名
     */
    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * 存储凭据并生成访问令牌。
     * <p>
     * 将SSH连接凭据安全存储在内存中，生成唯一令牌用于后续访问。
     * 存储的凭据将在指定时间后自动过期。
     * </p>
     *
     * @param host SSH主机地址
     * @param port SSH端口号
     * @param user SSH用户名
     * @param password SSH密码
     * @return 生成的访问令牌
     * @throws IllegalStateException 如果存储空间已满
     * @throws IllegalArgumentException 如果必要参数为空
     */
    public String storeCredentials(String host, String port, String user, String password) {
        requireNonBlank(host, "SSH主机地址");
        requireNonBlank(user, "SSH用户名");
        if (password == null) {
            throw new IllegalArgumentException("SSH密码不能为 null");
        }
        if (currentSize.get() >= MAX_ENTRIES) {
            log.warn("令牌保险库已满，拒绝新的存储请求");
            throw new IllegalStateException("存储空间已满，请稍后重试");
        }
        String token = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + DEFAULT_TTL_SECONDS * 1_000;
        VaultEntry entry = new VaultEntry(
                host.trim(), port == null ? "22" : port.trim(),
                user.trim(), password, expiryTime
        );
        vault.put(token, entry);
        totalCreated.incrementAndGet();
        currentSize.incrementAndGet();
        log.info("存储成功，令牌 {}...，过期于 {}，当前数量 {}",
                token.substring(0,8),
                Instant.ofEpochMilli(expiryTime),
                currentSize.get());
        return token;
    }

    /**
     * 根据令牌检索并移除凭据。
     * <p>
     * 这是一次性操作，检索后凭据将从保险库中移除，
     * 实现一次性使用的安全模式。
     * </p>
     *
     * @param token 访问令牌
     * @return 凭据条目，如果令牌无效或已过期则返回null
     */
    public VaultEntry retrieveAndRemove(String token) {
        if (token == null || token.isBlank()) return null;
        VaultEntry entry = vault.remove(token);
        if (entry == null) return null;
        currentSize.decrementAndGet();
        if (entry.isExpired()) {
            totalExpired.incrementAndGet();
            log.debug("令牌已过期 {}", token.substring(0,8));
            return null;
        }
        totalRetrieved.incrementAndGet();
        log.info("检索成功 {}...，剩余 {}", token.substring(0,8), currentSize.get());
        return entry;
    }

    /**
     * 检查令牌是否有效且未过期。
     *
     * @param token 要检查的令牌
     * @return 如果令牌有效且未过期则返回true
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        VaultEntry entry = vault.get(token);
        if (entry == null) {
            return false;
        }

        return Instant.now().toEpochMilli() <= entry.getExpiryTime();
    }

    /**
     * 定时清理过期的凭据条目。
     * <p>
     * 每30秒执行一次，移除所有过期的凭据，
     * 防止内存泄漏并及时释放敏感信息。
     * </p>
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        AtomicInteger cleaned = new AtomicInteger();
        vault.entrySet().removeIf(e -> {
            if (e.getValue().isExpired()) {
                cleaned.incrementAndGet();
                totalExpired.incrementAndGet();
                return true;
            }
            return false;
        });
        int c = cleaned.get();
        if (c > 0) {
            currentSize.addAndGet(-c);
            log.info("清理过期 {} 条，剩余 {}", c, currentSize.get());
        } else {
            log.debug("无过期条目，当前 {}", currentSize.get());
        }
    }

    /**
     * 获取保险库统计信息。
     *
     * @return 包含各项统计数据的字符串
     */
    public String getStats() {
        return String.format(
                "TokenVault统计 - 当前条目: %d, 创建总数: %d, 检索总数: %d, 过期总数: %d, 最大容量: %d",
                vault.size(),
                totalCreated.get(),
                totalRetrieved.get(),
                totalExpired.get(),
                MAX_ENTRIES
        );
    }

    /**
     * 应用关闭时清理所有凭据。
     */
    @PreDestroy
    public void cleanup() {
        int size = vault.size();
        vault.clear();
        log.info("应用关闭，清理所有凭据，清理数量: {}", size);
    }

    /**
     * 凭据条目内部类。
     * <p>
     * 存储SSH连接所需的所有参数以及过期时间。
     * 使用不可变设计确保数据安全。
     * </p>
     */
    @Getter
    @AllArgsConstructor
    public static  class VaultEntry {
        private final String host;
        private final String port;
        private final String user;
        private final String password;
        private final long expiryTime;

        /**
         * 检查条目是否已过期。
         *
         * @return 如果已过期则返回true
         */
        public boolean isExpired() {
            return Instant.now().toEpochMilli() > expiryTime;
        }

        /**
         * 获取剩余生存时间（秒）。
         *
         * @return 剩余秒数，如果已过期则返回0
         */
        public long getRemainingTTL() {
            long remaining = (expiryTime - Instant.now().toEpochMilli()) / 1000;
            return Math.max(0, remaining);
        }
    }
}
