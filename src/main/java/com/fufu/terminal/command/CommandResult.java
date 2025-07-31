package com.fufu.terminal.command;

import lombok.Getter;

/**
 * 封装SSH命令执行的结果
 */
@Getter
public class CommandResult {
    private final String stdout;
    private final String stderr;
    private final int exitStatus;

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
}
