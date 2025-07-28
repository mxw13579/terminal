package com.fufu.terminal.command;

import com.fufu.terminal.command.impl.DetectOsCommand;
import org.springframework.stereotype.Component;

/**
 * 命令链工厂，根据任务名称动态创建和组装命令链
 */
@Component
public class CommandChainFactory {

    /**
     * 根据任务名称创建对应的命令链
     * @param taskName 任务的名称
     * @param context 上下文，可能影响链的构建
     * @return 组装好的命令链
     */
    public CommandChain createCommandChain(String taskName, CommandContext context) {
        CommandChain chain = new CommandChain();

        // 这里是工厂的核心逻辑，未来会根据 taskName 返回不同的链
        // 例如: if ("initialize_environment".equals(taskName)) { ... }
        // 目前，我们先创建一个用于演示的简单链
        if ("initialize_environment".equals(taskName)) {
            // 1. 添加具体的命令实现
            chain.addCommand(new DetectOsCommand());
            // chain.addCommand(new CheckGitInstalledCommand());
            // ...
        } else {
            throw new IllegalArgumentException("Unknown task name: " + taskName);
        }

        return chain;
    }
}
