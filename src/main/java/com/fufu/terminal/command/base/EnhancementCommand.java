package com.fufu.terminal.command.base;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;

/**
 * 安装/增强命令抽象基类
 * 支持条件执行和系统差异化处理
 * @author lizelin
 */
@Slf4j
public abstract class EnhancementCommand implements Command {

    /**
     * 获取增强目标的名称（用于日志记录）
     * @return 增强目标名称
     */
    protected abstract String getEnhancementTargetName();

    /**
     * 判断是否需要执行增强操作
     * @param context 命令上下文
     * @return 是否需要执行
     */
    protected abstract boolean shouldEnhance(CommandContext context);

    /**
     * 执行具体的增强逻辑
     * @param context 命令上下文
     * @return 增强是否成功
     * @throws Exception 增强过程中的异常
     */
    protected abstract boolean performEnhancement(CommandContext context) throws Exception;

    @Override
    public final boolean shouldExecute(CommandContext context) {
        return shouldEnhance(context);
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        String targetName = getEnhancementTargetName();
        log.info("开始执行{}增强操作...", targetName);
        
        boolean success = performEnhancement(context);
        
        if (success) {
            log.info("{}增强操作执行成功", targetName);
        } else {
            log.warn("{}增强操作执行失败", targetName);
        }
        
        // 设置增强结果到上下文
        setEnhancementResult(context, success);
    }

    /**
     * 将增强结果设置到上下文中，子类需实现具体的key命名
     * @param context 命令上下文
     * @param success 是否成功
     */
    protected abstract void setEnhancementResult(CommandContext context, boolean success);

    /**
     * 获取操作系统信息的便捷方法
     * @param context 命令上下文
     * @return 操作系统信息，如果未检测到则返回null
     */
    protected OsInfo getOsInfo(CommandContext context) {
        return (OsInfo) context.getProperty("os_info");
    }

    /**
     * 执行SSH命令的便捷方法
     * @param context 命令上下文
     * @param command 要执行的命令
     * @return 命令执行结果
     * @throws Exception SSH命令执行异常
     */
    protected CommandResult executeCommand(CommandContext context, String command) throws Exception {
        return SshCommandUtil.executeCommand(context.getSshConnection(), command);
    }

    /**
     * 根据操作系统类型执行不同的增强逻辑
     * @param context 命令上下文
     * @param ubuntuDebian Ubuntu/Debian系统的增强逻辑
     * @param redhatCentos RHEL/CentOS/Fedora系统的增强逻辑
     * @param arch Arch Linux系统的增强逻辑
     * @param alpine Alpine Linux系统的增强逻辑
     * @param suse SUSE系统的增强逻辑
     * @param defaultAction 默认增强逻辑
     * @return 增强是否成功
     * @throws Exception 增强过程中的异常
     */
    protected boolean executeBySystemType(CommandContext context,
                                        SystemTypeAction ubuntuDebian,
                                        SystemTypeAction redhatCentos,
                                        SystemTypeAction arch,
                                        SystemTypeAction alpine,
                                        SystemTypeAction suse,
                                        SystemTypeAction defaultAction) throws Exception {
        OsInfo osInfo = getOsInfo(context);
        if (osInfo == null) {
            log.warn("无法获取操作系统信息，使用默认处理逻辑");
            return defaultAction.execute(context);
        }

        SystemType systemType = osInfo.getSystemType();
        log.debug("根据系统类型 {} 执行相应的增强逻辑", systemType);
        
        return switch (systemType) {
            case UBUNTU, DEBIAN -> ubuntuDebian.execute(context);
            case REDHAT, CENTOS, FEDORA -> redhatCentos.execute(context);
            case ARCH -> arch.execute(context);
            case ALPINE -> alpine.execute(context);
            case SUSE -> suse.execute(context);
            default -> {
                log.warn("未知的系统类型 {}，使用默认处理逻辑", systemType);
                yield defaultAction.execute(context);
            }
        };
    }

    /**
     * 系统类型相关的操作接口
     */
    @FunctionalInterface
    protected interface SystemTypeAction {
        boolean execute(CommandContext context) throws Exception;
    }

    /**
     * 检查是否需要中国镜像的便捷方法
     * @param context 命令上下文
     * @return 是否需要使用中国镜像
     */
    protected boolean needsChinaMirror(CommandContext context) {
        Boolean useChinaMirror = (Boolean) context.getProperty("use_china_mirror");
        return useChinaMirror != null && useChinaMirror;
    }
}