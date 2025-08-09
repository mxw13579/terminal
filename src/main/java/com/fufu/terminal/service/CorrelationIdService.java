package com.fufu.terminal.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 关联 ID 服务类。
 * <p>
 * 提供分布式追踪中的关联 ID 生成和管理功能，
 * 支持跨线程和跨服务的请求追踪。
 * </p>
 * 
 * <p><strong>功能特性：</strong></p>
 * <ul>
 *     <li>生成唯一的关联 ID</li>
 *     <li>MDC（Mapped Diagnostic Context）集成</li>
 *     <li>线程安全的 ID 传播</li>
 *     <li>敏感信息脱敏支持</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class CorrelationIdService {

    /**
     * MDC 中关联 ID 的键名。
     */
    public static final String CORRELATION_ID_KEY = "correlationId";
    
    /**
     * MDC 中会话 ID 的键名。
     */
    public static final String SESSION_ID_KEY = "sessionId";
    
    /**
     * MDC 中用户 ID 的键名。
     */
    public static final String USER_ID_KEY = "userId";
    
    /**
     * MDC 中操作类型的键名。
     */
    public static final String OPERATION_TYPE_KEY = "operationType";

    /**
     * 生成新的关联 ID。
     * 
     * @return 唯一的关联 ID
     */
    public String generateCorrelationId() {
        // 使用 UUID + 时间戳后4位 + 随机数 确保唯一性
        String uuid = UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        
        return String.format("%s-%d-%d", uuid.substring(0, 8), timestamp % 10000, random);
    }

    /**
     * 设置当前线程的关联 ID。
     * 
     * @param correlationId 关联 ID
     */
    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
            log.debug("设置关联ID: {}", correlationId);
        }
    }

    /**
     * 获取当前线程的关联 ID。
     * 
     * @return 关联 ID，如果不存在则返回 null
     */
    public String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * 生成并设置新的关联 ID。
     * 
     * @return 新生成的关联 ID
     */
    public String generateAndSetCorrelationId() {
        String correlationId = generateCorrelationId();
        setCorrelationId(correlationId);
        return correlationId;
    }

    /**
     * 设置会话 ID。
     * 
     * @param sessionId 会话 ID
     */
    public void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            // 脱敏处理：只显示前8位和后4位
            String maskedSessionId = maskSensitiveData(sessionId);
            MDC.put(SESSION_ID_KEY, maskedSessionId);
            log.debug("设置会话ID: {}", maskedSessionId);
        }
    }

    /**
     * 设置用户 ID（脱敏处理）。
     * 
     * @param userId 用户 ID
     */
    public void setUserId(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            // 脱敏处理：只显示前2位和后2位
            String maskedUserId = maskSensitiveData(userId, 2, 2);
            MDC.put(USER_ID_KEY, maskedUserId);
            log.debug("设置用户ID: {}", maskedUserId);
        }
    }

    /**
     * 设置操作类型。
     * 
     * @param operationType 操作类型（如：ssh_connect, command_execute, sftp_upload）
     */
    public void setOperationType(String operationType) {
        if (operationType != null && !operationType.trim().isEmpty()) {
            MDC.put(OPERATION_TYPE_KEY, operationType);
            log.debug("设置操作类型: {}", operationType);
        }
    }

    /**
     * 设置完整的上下文信息。
     * 
     * @param correlationId 关联 ID
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param operationType 操作类型
     */
    public void setFullContext(String correlationId, String sessionId, String userId, String operationType) {
        setCorrelationId(correlationId);
        setSessionId(sessionId);
        setUserId(userId);
        setOperationType(operationType);
        
        log.debug("设置完整上下文: correlationId={}, sessionId={}, userId={}, operationType={}", 
                 correlationId, maskSensitiveData(sessionId), 
                 maskSensitiveData(userId, 2, 2), operationType);
    }

    /**
     * 清除当前线程的所有 MDC 上下文。
     */
    public void clearContext() {
        String correlationId = getCorrelationId();
        MDC.clear();
        if (correlationId != null) {
            log.debug("清除上下文: correlationId={}", correlationId);
        }
    }

    /**
     * 清除指定的 MDC 键。
     * 
     * @param key 要清除的键名
     */
    public void clearContextKey(String key) {
        String value = MDC.get(key);
        MDC.remove(key);
        if (value != null) {
            log.debug("清除上下文键: {}={}", key, value);
        }
    }

    /**
     * 获取所有 MDC 上下文信息的字符串表示。
     * 
     * @return 上下文信息字符串
     */
    public String getContextInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("correlationId=").append(getCorrelationId());
        
        String sessionId = MDC.get(SESSION_ID_KEY);
        if (sessionId != null) {
            sb.append(", sessionId=").append(sessionId);
        }
        
        String userId = MDC.get(USER_ID_KEY);
        if (userId != null) {
            sb.append(", userId=").append(userId);
        }
        
        String operationType = MDC.get(OPERATION_TYPE_KEY);
        if (operationType != null) {
            sb.append(", operationType=").append(operationType);
        }
        
        return sb.toString();
    }

    /**
     * 脱敏处理敏感数据。
     * 默认显示前8位和后4位。
     * 
     * @param data 原始数据
     * @return 脱敏后的数据
     */
    public String maskSensitiveData(String data) {
        return maskSensitiveData(data, 8, 4);
    }

    /**
     * 脱敏处理敏感数据。
     * 
     * @param data 原始数据
     * @param prefixLength 保留前缀长度
     * @param suffixLength 保留后缀长度
     * @return 脱敏后的数据
     */
    public String maskSensitiveData(String data, int prefixLength, int suffixLength) {
        if (data == null || data.length() <= prefixLength + suffixLength) {
            return data;
        }
        
        String prefix = data.substring(0, Math.min(prefixLength, data.length()));
        String suffix = data.length() > suffixLength ? 
                       data.substring(data.length() - suffixLength) : "";
        
        return prefix + "***" + suffix;
    }

    /**
     * 使用给定的关联 ID 执行操作。
     * 操作完成后会清除上下文。
     * 
     * @param correlationId 关联 ID
     * @param operation 要执行的操作
     */
    public void executeWithCorrelationId(String correlationId, Runnable operation) {
        String previousCorrelationId = getCorrelationId();
        try {
            setCorrelationId(correlationId);
            operation.run();
        } finally {
            if (previousCorrelationId != null) {
                setCorrelationId(previousCorrelationId);
            } else {
                clearContextKey(CORRELATION_ID_KEY);
            }
        }
    }
}