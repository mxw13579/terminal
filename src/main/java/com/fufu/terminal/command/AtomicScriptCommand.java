package com.fufu.terminal.command;

/**
 * 原子脚本命令接口
 * 专门用于内置原子脚本的接口定义
 */
public interface AtomicScriptCommand {

    /**
     * 获取命令的名称
     * @return 命令名称
     */
    String getName();

    /**
     * 获取命令的描述
     * @return 命令描述
     */
    String getDescription();

    /**
     * 执行原子脚本命令
     * @param context 命令执行的上下文环境
     * @return 命令执行结果
     */
    CommandResult execute(CommandContext context);
}