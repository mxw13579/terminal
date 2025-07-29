package com.fufu.terminal.command.model;

import lombok.Data;

import java.util.List;

/**
 * 脚本执行请求
 * @author lizelin
 */
@Data
public class ScriptRequest {
    /**
     * 要执行的命令ID列表
     */
    private List<String> commandIds;
    
    /**
     * SSH连接配置
     */
    private SshConnectionConfig sshConfig;
    
    /**
     * 是否自动优化脚本（添加依赖等）
     */
    private boolean autoOptimize = true;
    
    /**
     * 执行超时时间（分钟），0表示不限制
     */
    private int timeoutMinutes = 30;
    
    /**
     * 验证请求的有效性
     */
    public boolean isValid() {
        return commandIds != null && !commandIds.isEmpty() &&
               sshConfig != null && sshConfig.isValid();
    }
}