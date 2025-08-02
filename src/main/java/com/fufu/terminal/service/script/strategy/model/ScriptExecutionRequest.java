package com.fufu.terminal.service.script.strategy.model;

import com.fufu.terminal.command.CommandContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 脚本执行请求模型
 * 包含执行脚本所需的所有信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecutionRequest {

    /**
     * 脚本ID
     */
    private String scriptId;

    /**
     * 脚本来源类型
     */
    private com.fufu.terminal.service.script.strategy.ScriptSourceType sourceType;

    /**
     * 执行参数（键值对形式）
     */
    private Map<String, Object> parameters;

    /**
     * 命令执行上下文
     */
    private CommandContext commandContext;

    /**
     * 执行超时时间（毫秒）
     */
    private Long timeoutMs;

    /**
     * 是否异步执行
     */
    private boolean async;

    /**
     * 执行环境标识
     */
    private String environment;

    /**
     * 用户ID（用于权限验证）
     */
    private String userId;

    /**
     * 会话ID（用于WebSocket通信）
     */
    private String sessionId;

    /**
     * 验证请求的有效性
     */
    public boolean isValid() {
        return scriptId != null && !scriptId.trim().isEmpty() &&
               sourceType != null &&
               commandContext != null;
    }
}