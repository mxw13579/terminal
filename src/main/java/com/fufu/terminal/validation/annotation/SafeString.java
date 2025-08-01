package com.fufu.terminal.validation.annotation;

import com.fufu.terminal.validation.validator.SafeStringValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 安全字符串验证注解
 * 防止XSS和SQL注入攻击
 * @author lizelin
 */
@Documented
@Constraint(validatedBy = SafeStringValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeString {
    
    String message() default "Input contains potentially unsafe characters";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 是否允许HTML标签
     */
    boolean allowHtml() default false;
    
    /**
     * 最大长度
     */
    int maxLength() default 1000;
    
    /**
     * 是否允许SQL关键字
     */
    boolean allowSqlKeywords() default false;
}