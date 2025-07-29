package com.fufu.terminal.command.base;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;

/**
 * 基础环境检查命令抽象基类
 * 提供常用的检查工具方法，子类可以专注于具体的检查逻辑
 * @author lizelin
 */
@Slf4j
public abstract class EnvironmentCheckCommand implements Command {

    /**
     * 获取检查目标的名称（用于日志记录）
     * @return 检查目标名称
     */
    protected abstract String getCheckTargetName();

    /**
     * 执行具体的检查逻辑
     * @param context 命令上下文
     * @return 检查结果
     * @throws Exception 检查过程中的异常
     */
    protected abstract boolean performCheck(CommandContext context) throws Exception;

    /**
     * 获取目标版本信息（可选实现）
     * @param context 命令上下文
     * @return 版本信息，如果不支持则返回null
     * @throws Exception 获取版本信息过程中的异常
     */
    protected String getVersion(CommandContext context) throws Exception {
        return null;
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        String targetName = getCheckTargetName();
        log.info("开始检查{}安装状态...", targetName);
        
        boolean installed = performCheck(context);
        String version = null;
        
        if (installed) {
            version = getVersion(context);
            log.info("{}已安装，版本: {}", targetName, version != null ? version : "未知");
        } else {
            log.info("{}未安装", targetName);
        }
        
        // 设置检查结果到上下文
        setCheckResult(context, installed, version);
    }

    /**
     * 将检查结果设置到上下文中，子类需实现具体的key命名
     * @param context 命令上下文
     * @param installed 是否已安装
     * @param version 版本信息
     */
    protected abstract void setCheckResult(CommandContext context, boolean installed, String version);

    /**
     * 使用command -v检查命令是否存在的通用方法
     * @param context 命令上下文
     * @param commandName 要检查的命令名称
     * @return 是否存在
     * @throws Exception SSH命令执行异常
     */
    protected boolean checkCommandExists(CommandContext context, String commandName) throws Exception {
        try {
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(),
                String.format("command -v %s >/dev/null 2>&1 && echo 'installed' || echo 'not_installed'", commandName)
            );
            
            if (result.isSuccess()) {
                String output = result.getStdout().trim();
                return "installed".equals(output);
            }
        } catch (Exception e) {
            log.debug("检查{}命令存在性失败: {}", commandName, e.getMessage());
        }
        return false;
    }

    /**
     * 获取操作系统信息的便捷方法
     * @param context 命令上下文
     * @return 操作系统信息，如果未检测到则返回null
     */
    protected OsInfo getOsInfo(CommandContext context) {
        return (OsInfo) context.getProperty("os_info");
    }

    /**
     * 根据操作系统类型执行不同的检查逻辑
     * @param context 命令上下文
     * @param ubuntuDebian Ubuntu/Debian系统的检查逻辑
     * @param redhatCentos RHEL/CentOS/Fedora系统的检查逻辑
     * @param arch Arch Linux系统的检查逻辑
     * @param alpine Alpine Linux系统的检查逻辑
     * @param suse SUSE系统的检查逻辑
     * @param defaultAction 默认检查逻辑
     * @return 检查结果
     * @throws Exception 检查过程中的异常
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
            return defaultAction.execute(context);
        }

        SystemType systemType = osInfo.getSystemType();
        return switch (systemType) {
            case UBUNTU, DEBIAN -> ubuntuDebian.execute(context);
            case REDHAT, CENTOS, FEDORA -> redhatCentos.execute(context);
            case ARCH -> arch.execute(context);
            case ALPINE -> alpine.execute(context);
            case SUSE -> suse.execute(context);
            default -> defaultAction.execute(context);
        };
    }

    /**
     * 系统类型相关的操作接口
     */
    @FunctionalInterface
    protected interface SystemTypeAction {
        boolean execute(CommandContext context) throws Exception;
    }
}