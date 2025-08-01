package com.fufu.terminal.validation.sanitizer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 输入内容清理器
 * 清理和转义危险字符
 * @author lizelin
 */
@Slf4j
@Component
public class InputSanitizer {
    
    // HTML实体转义映射
    private static final String[][] HTML_ENTITIES = {
        {"&", "&amp;"},
        {"<", "&lt;"},
        {">", "&gt;"},
        {"\"", "&quot;"},
        {"'", "&#x27;"},
        {"/", "&#x2F;"}
    };
    
    // SQL特殊字符
    private static final Pattern SQL_INJECTION_PATTERN = 
        Pattern.compile("('|(\\-\\-)|(;)|(\\|)|(\\*)|(%))", Pattern.CASE_INSENSITIVE);
    
    /**
     * HTML转义
     */
    public String escapeHtml(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String result = input;
        for (String[] entity : HTML_ENTITIES) {
            result = result.replace(entity[0], entity[1]);
        }
        return result;
    }
    
    /**
     * SQL转义
     */
    public String escapeSql(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        return input.replace("'", "''")
                   .replace("\\", "\\\\")
                   .replace("\0", "\\0")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\"", "\\\"")
                   .replace("\032", "\\Z");
    }
    
    /**
     * Shell命令转义
     */
    public String escapeShell(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        // 转义shell特殊字符
        return input.replace("$", "\\$")
                   .replace("`", "\\`")
                   .replace("!", "\\!")
                   .replace("\"", "\\\"")
                   .replace("'", "\\'")
                   .replace(";", "\\;")
                   .replace("&", "\\&")
                   .replace("|", "\\|")
                   .replace(">", "\\>")
                   .replace("<", "\\<")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("*", "\\*")
                   .replace("?", "\\?");
    }
    
    /**
     * 移除危险字符
     */
    public String removeDangerousChars(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        // 移除null字节和控制字符（保留换行、回车、制表符）
        return input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }
    
    /**
     * 清理文件名
     */
    public String sanitizeFileName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }
        
        // 移除或替换文件名中的危险字符
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_")
                      .replaceAll("\\.\\.+", ".")  // 防止目录遍历
                      .trim();
    }
    
    /**
     * 清理路径
     */
    public String sanitizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        
        // 防止目录遍历攻击
        return path.replaceAll("\\.\\./", "")
                  .replaceAll("\\.\\\\", "")
                  .replaceAll("//+", "/")
                  .trim();
    }
    
    /**
     * 通用清理方法
     */
    public String sanitize(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        try {
            // 1. 移除危险字符
            String cleaned = removeDangerousChars(input);
            
            // 2. HTML转义
            cleaned = escapeHtml(cleaned);
            
            // 3. 限制长度
            if (cleaned.length() > 1000) {
                cleaned = cleaned.substring(0, 1000);
                log.warn("Input truncated due to length limit: {}", input.substring(0, 50) + "...");
            }
            
            return cleaned;
            
        } catch (Exception e) {
            log.error("Error sanitizing input: {}", input, e);
            return "";
        }
    }
}