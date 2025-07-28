package com.fufu.terminal.command;

/**
 * 命令接口，定义了责任链中每个操作的契约
 */
public interface Command {

    /**
     * 获取命令的名称，用于日志记录和向前端报告进度
     * @return 命令名称
     */
    String getName();

    /**
     * 执行命令的核心逻辑
     * @param context 命令执行的上下文环境
     * @throws Exception 执行过程中可能抛出异常
     */
    void execute(CommandContext context) throws Exception;

    /**
     * 判断当前命令是否应该被执行
     * 这允许我们实现条件执行的逻辑，例如 "如果未安装，则安装"
     * @param context 命令执行的上下文环境
     * @return 如果应该执行，返回 true，否则返回 false
     */
    default boolean shouldExecute(CommandContext context) {
        return true; // 默认总是执行
    }
}
