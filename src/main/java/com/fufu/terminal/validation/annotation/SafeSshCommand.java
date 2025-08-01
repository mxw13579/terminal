package com.fufu.terminal.validation.annotation;

import com.fufu.terminal.validation.validator.SshCommandValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * SSH命令验证注解
 * 防止危险的SSH命令执行
 * @author lizelin
 */
@Documented
@Constraint(validatedBy = SshCommandValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeSshCommand {
    
    String message() default "SSH command contains dangerous operations";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 是否允许管道操作
     */
    boolean allowPipe() default true;
    
    /**
     * 是否允许重定向
     */
    boolean allowRedirection() default true;
    
    /**
     * 是否允许后台执行
     */
    boolean allowBackground() default false;
}