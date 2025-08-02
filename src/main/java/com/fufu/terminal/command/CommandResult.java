package com.fufu.terminal.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 封装SSH命令执行的结果
 */
@Getter
@Setter
public class CommandResult {
    private final String stdout;
    private final String stderr;
    private final int exitStatus;
    
    /**
     * 是否需要用户交互
     */
    private boolean requiresUserInteraction = false;

    /**
     * 交互数据（用于前端显示交互界面）
     */
    private Map<String, Object> interactionData;

    public CommandResult(String stdout, String stderr, int exitStatus) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitStatus = exitStatus;
    }

    public boolean isSuccess() {
        return exitStatus == 0;
    }

    /**
     * 创建成功的命令结果
     */
    public static CommandResult success(String output) {
        return new CommandResult(output, "", 0);
    }

    /**
     * 创建失败的命令结果
     */
    public static CommandResult failure(String errorMessage) {
        return new CommandResult("", errorMessage, 1);
    }

    /**
     * 获取错误消息
     */
    public String getErrorMessage() {
        return stderr;
    }

    /**
     * 获取输出内容
     */
    public String getOutput() {
        return stdout;
    }

    public boolean isRequiresUserInteraction() {
        return requiresUserInteraction;
    }

    public void setRequiresUserInteraction(boolean requiresUserInteraction) {
        this.requiresUserInteraction = requiresUserInteraction;
    }

    public Map<String, Object> getInteractionData() {
        return interactionData;
    }

    public void setInteractionData(Map<String, Object> interactionData) {
        this.interactionData = interactionData;
        this.requiresUserInteraction = true;
    }
}
