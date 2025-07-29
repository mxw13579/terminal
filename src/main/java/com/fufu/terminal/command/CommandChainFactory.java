package com.fufu.terminal.command;

import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.environment.CheckGitCommand;
import com.fufu.terminal.command.impl.environment.CheckDockerCommand;
import com.fufu.terminal.command.impl.environment.CheckCurlCommand;
import com.fufu.terminal.command.impl.environment.CheckUnzipCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureSystemMirrorsCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureDockerMirrorCommand;
import org.springframework.stereotype.Component;

/**
 * 命令链工厂，根据任务名称动态创建和组装命令链
 * 按照分类顺序构建命令链：前置处理 → 环境检查 → 安装增强
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

        switch (taskName) {
            // 初始化环境
            case "initialize_environment" -> buildInitializeEnvironmentChain(chain);
            // 检查环境
            case "check_environment" -> buildCheckEnvironmentChain(chain);
            // 镜像源配置
            case "configure_mirrors" -> buildConfigureMirrorsChain(chain);
            // 完整设置
            case "full_setup" -> buildFullSetupChain(chain);
            default -> throw new IllegalArgumentException("Unknown task name: " + taskName);
        }

        return chain;
    }

    /**
     * 构建完整的环境初始化命令链
     * 包含所有分类的命令：前置处理 → 环境检查 → 安装增强
     */
    private void buildFullSetupChain(CommandChain chain) {
        // 1. 前置处理命令（必须执行）
        addPreProcessCommands(chain);

        // 2. 环境检查命令
        addEnvironmentCheckCommands(chain);

        // 3. 安装/增强命令
        addEnhancementCommands(chain);
    }

    /**
     * 构建环境初始化命令链（兼容旧的任务名称）
     */
    private void buildInitializeEnvironmentChain(CommandChain chain) {
        buildFullSetupChain(chain);
    }

    /**
     * 构建环境检查命令链
     * 仅执行前置处理和环境检查，不执行安装/增强操作
     */
    private void buildCheckEnvironmentChain(CommandChain chain) {
        // 1. 前置处理命令
        addPreProcessCommands(chain);

        // 2. 环境检查命令
        addEnvironmentCheckCommands(chain);
    }

    /**
     * 构建镜像配置命令链
     * 执行前置处理和镜像配置操作
     */
    private void buildConfigureMirrorsChain(CommandChain chain) {
        // 1. 前置处理命令
        addPreProcessCommands(chain);

        // 2. 镜像配置命令
        chain.addCommand(new ConfigureSystemMirrorsCommand());
        chain.addCommand(new ConfigureDockerMirrorCommand());
    }

    /**
     * 添加前置处理命令
     * 这些命令必须最先执行，为后续命令提供必要信息
     */
    private void addPreProcessCommands(CommandChain chain) {
        // 操作系统检测（必须）
        chain.addCommand(new DetectOsCommand());

        // 地理位置检测（影响镜像配置）
        chain.addCommand(new DetectLocationCommand());
    }

    /**
     * 添加环境检查命令
     * 检查基础工具和软件的安装状态
     */
    private void addEnvironmentCheckCommands(CommandChain chain) {
        // 基础工具检查
        chain.addCommand(new CheckCurlCommand());
        chain.addCommand(new CheckUnzipCommand());

        // 开发工具检查
        chain.addCommand(new CheckGitCommand());
        chain.addCommand(new CheckDockerCommand());
    }

    /**
     * 添加安装/增强命令
     * 这些命令根据条件执行，优化系统配置
     */
    private void addEnhancementCommands(CommandChain chain) {
        // 镜像源配置
        chain.addCommand(new ConfigureSystemMirrorsCommand());
        chain.addCommand(new ConfigureDockerMirrorCommand());
    }
}
