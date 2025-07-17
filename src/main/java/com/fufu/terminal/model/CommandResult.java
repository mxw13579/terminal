package com.fufu.terminal.model;

/**
 * 封装远程命令执行结果.
 *
 * @param exitStatus 退出状态码
 * @param stdout     标准输出
 * @param stderr     标准错误
 */
public record CommandResult(int exitStatus, String stdout, String stderr) {

    /**
     * 检查命令是否成功执行 (退出码为 0).
     *
     * @return 如果成功则为 true, 否则为 false.
     */
    public boolean isSuccess() {
        return exitStatus == 0;
    }
}
