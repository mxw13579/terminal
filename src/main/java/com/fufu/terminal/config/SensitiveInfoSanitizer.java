package com.fufu.terminal.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Logback敏感信息脱敏转换器
 * <p>
 * 该转换器用于在日志输出时自动脱敏敏感信息，如密码、密钥、令牌等，
 * 防止敏感数据泄露到日志文件中。
 * </p>
 * 
 * @author lizelin
 */
public class SensitiveInfoSanitizer extends ClassicConverter {
    
    /**
     * 敏感信息匹配模式，用于识别和脱敏敏感内容
     */
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // 密码相关
        Pattern.compile("(?i)(password[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(passwd[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(pwd[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(-p\\s+)[\\S]+"),
        Pattern.compile("(--password\\s+)[\\S]+"),
        
        // 密钥和令牌相关
        Pattern.compile("(?i)(secret[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(token[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(key[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(apikey[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(auth[=:\\s]*)[\\S]+", Pattern.CASE_INSENSITIVE),
        
        // SSH私钥内容
        Pattern.compile("(-----BEGIN [A-Z ]+PRIVATE KEY-----)[\\s\\S]+(-----END [A-Z ]+PRIVATE KEY-----)"),
        
        // Base64编码的长字符串（可能是密钥或令牌）
        Pattern.compile("([A-Za-z0-9+/]{50,}={0,2})"),
        
        // 类似JWT的token格式
        Pattern.compile("([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)"),
        
        // 信用卡号等
        Pattern.compile("(\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b)"),
        
        // 邮箱地址的部分脱敏
        Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")
    };
    
    /**
     * 脱敏替换字符串
     */
    private static final String MASK = "***";
    
    @Override
    public String convert(ILoggingEvent event) {
        String originalMessage = event.getFormattedMessage();
        if (originalMessage == null) {
            return null;
        }
        
        return sanitizeMessage(originalMessage);
    }
    
    /**
     * 对消息内容进行敏感信息脱敏处理
     * 
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    private String sanitizeMessage(String message) {
        String sanitized = message;
        
        // 应用所有敏感信息匹配模式
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            // 根据不同模式使用不同的替换策略
            if (pattern.pattern().contains("email") || pattern.pattern().contains("@")) {
                // 邮箱地址部分脱敏：user***@domain.com
                sanitized = pattern.matcher(sanitized).replaceAll("$1***@$2");
            } else if (pattern.pattern().contains("BEGIN") && pattern.pattern().contains("PRIVATE KEY")) {
                // SSH私钥完全脱敏
                sanitized = pattern.matcher(sanitized).replaceAll("$1\n[PRIVATE KEY CONTENT MASKED]\n$2");
            } else if (pattern.pattern().contains("\\b\\d{4}")) {
                // 信用卡号脱敏：****-****-****-1234
                sanitized = pattern.matcher(sanitized).replaceAll("****-****-****-****");
            } else {
                // 其他敏感信息替换为 group1 + MASK
                sanitized = pattern.matcher(sanitized).replaceAll("$1" + MASK);
            }
        }
        
        return sanitized;
    }
}