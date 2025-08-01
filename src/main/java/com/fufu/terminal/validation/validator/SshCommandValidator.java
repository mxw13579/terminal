package com.fufu.terminal.validation.validator;

import com.fufu.terminal.validation.annotation.SafeSshCommand;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SSH命令验证器
 * 防止危险的SSH命令执行
 * @author lizelin
 */
@Slf4j
public class SshCommandValidator implements ConstraintValidator<SafeSshCommand, String> {
    
    private boolean allowPipe;
    private boolean allowRedirection;
    private boolean allowBackground;
    
    // 危险命令列表
    private static final List<String> DANGEROUS_COMMANDS = Arrays.asList(
        "rm", "rmdir", "del", "format", "fdisk", "mkfs", "dd",
        "shutdown", "reboot", "halt", "poweroff", "init",
        "killall", "pkill", "kill -9",
        "chmod 777", "chown", "su", "sudo",
        "wget", "curl", "nc", "netcat", "telnet",
        "crontab", "at", "batch",
        "iptables", "firewall", "ufw"
    );
    
    // 危险模式
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
        Pattern.compile("rm\\s+-rf\\s+/", Pattern.CASE_INSENSITIVE),
        Pattern.compile(":\\(\\)\\{\\s*:\\|:\\&\\s*\\};:", Pattern.CASE_INSENSITIVE), // fork bomb
        Pattern.compile("\\$\\(.*\\)", Pattern.CASE_INSENSITIVE), // command substitution
        Pattern.compile("`.*`", Pattern.CASE_INSENSITIVE), // backtick execution
        Pattern.compile("eval\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("exec\\s+", Pattern.CASE_INSENSITIVE)
    );
    
    @Override
    public void initialize(SafeSshCommand constraintAnnotation) {
        this.allowPipe = constraintAnnotation.allowPipe();
        this.allowRedirection = constraintAnnotation.allowRedirection();
        this.allowBackground = constraintAnnotation.allowBackground();
    }
    
    @Override
    public boolean isValid(String command, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(command)) {
            return true;
        }
        
        try {
            String cleanCommand = command.trim().toLowerCase();
            
            // 检查危险命令
            if (containsDangerousCommand(cleanCommand)) {
                addViolation(context, "Command contains dangerous operations");
                return false;
            }
            
            // 检查危险模式
            if (containsDangerousPattern(command)) {
                addViolation(context, "Command contains dangerous patterns");
                return false;
            }
            
            // 检查管道操作
            if (!allowPipe && command.contains("|")) {
                addViolation(context, "Pipe operations are not allowed");
                return false;
            }
            
            // 检查重定向
            if (!allowRedirection && (command.contains(">") || command.contains("<"))) {
                addViolation(context, "Redirection operations are not allowed");
                return false;
            }
            
            // 检查后台执行
            if (!allowBackground && command.endsWith("&")) {
                addViolation(context, "Background execution is not allowed");
                return false;
            }
            
            // 检查命令注入
            if (containsCommandInjection(command)) {
                addViolation(context, "Command injection detected");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating SSH command: {}", command, e);
            addViolation(context, "Command validation failed");
            return false;
        }
    }
    
    private boolean containsDangerousCommand(String command) {
        return DANGEROUS_COMMANDS.stream()
                .anyMatch(dangerous -> command.contains(dangerous.toLowerCase()));
    }
    
    private boolean containsDangerousPattern(String command) {
        return DANGEROUS_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(command).find());
    }
    
    private boolean containsCommandInjection(String command) {
        // 检查命令分隔符
        return command.contains(";") || 
               command.contains("&&") || 
               command.contains("||") ||
               command.contains("\n") ||
               command.contains("\r");
    }
    
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}