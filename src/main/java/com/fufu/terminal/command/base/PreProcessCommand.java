package com.fufu.terminal.command.base;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;

/**
 * 通用前置处理命令抽象基类
 * 前置处理命令必须执行，为后续命令提供必要的环境信息
 * @author lizelin
 */
public abstract class PreProcessCommand implements Command {

    /**
     * 前置处理命令必须执行
     * @param context 命令执行的上下文环境
     * @return 总是返回 true
     */
    @Override
    public final boolean shouldExecute(CommandContext context) {
        return true;
    }
}