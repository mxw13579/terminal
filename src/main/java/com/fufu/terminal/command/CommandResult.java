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
}
