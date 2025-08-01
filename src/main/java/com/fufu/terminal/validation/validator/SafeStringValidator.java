package com.fufu.terminal.validation.validator;

import com.fufu.terminal.validation.annotation.SafeString;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全字符串验证器
 * 防止XSS和SQL注入攻击
 * @author lizelin
 */
@Slf4j
public class SafeStringValidator implements ConstraintValidator<SafeString, String> {
    
    private boolean allowHtml;
    private int maxLength;
    private boolean allowSqlKeywords;
    
    // XSS攻击模式
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<embed[^>]*>.*?</embed>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onerror\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onclick\\s*=", Pattern.CASE_INSENSITIVE)
    );
    
    // SQL注入关键字
    private static final List<String> SQL_KEYWORDS = Arrays.asList(
        "DROP", "DELETE", "TRUNCATE", "INSERT", "UPDATE", "ALTER", "CREATE",
        "EXEC", "EXECUTE", "UNION", "SELECT", "DECLARE", "CAST", "CONVERT",
        "SCRIPT", "COOKIE", "VBSCRIPT", "ONLOAD", "ONERROR"
    );
    
    @Override
    public void initialize(SafeString constraintAnnotation) {
        this.allowHtml = constraintAnnotation.allowHtml();
        this.maxLength = constraintAnnotation.maxLength();
        this.allowSqlKeywords = constraintAnnotation.allowSqlKeywords();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return true; // null和空字符串由@NotNull和@NotEmpty处理
        }
        
        try {
            // 长度检查
            if (value.length() > maxLength) {
                addViolation(context, "Input exceeds maximum length of " + maxLength);
                return false;
            }
            
            // XSS检查
            if (!allowHtml && containsXssPattern(value)) {
                addViolation(context, "Input contains potentially dangerous HTML/JavaScript");
                return false;
            }
            
            // SQL注入检查
            if (!allowSqlKeywords && containsSqlKeywords(value)) {
                addViolation(context, "Input contains SQL keywords");
                return false;
            }
            
            // 检查危险字符
            if (containsDangerousChars(value)) {
                addViolation(context, "Input contains dangerous characters");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating input: {}", value, e);
            addViolation(context, "Input validation failed");
            return false;
        }
    }
    
    private boolean containsXssPattern(String value) {
        String lowerValue = value.toLowerCase();
        return XSS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(lowerValue).find());
    }
    
    private boolean containsSqlKeywords(String value) {
        String upperValue = value.toUpperCase();
        return SQL_KEYWORDS.stream().anyMatch(upperValue::contains);
    }
    
    private boolean containsDangerousChars(String value) {
        // 检查null字节和控制字符
        return value.contains("\0") || 
               value.chars().anyMatch(c -> c < 32 && c != 9 && c != 10 && c != 13);
    }
    
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}