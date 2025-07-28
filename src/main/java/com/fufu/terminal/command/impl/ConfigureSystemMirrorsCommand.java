package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置系统软件镜像源的命令
 * 根据操作系统类型和地理位置配置相应的软件包管理器镜像源
 * @author lizelin
 */
@Slf4j
public class ConfigureSystemMirrorsCommand implements Command {

    public static final String SYSTEM_MIRRORS_CONFIGURED_KEY = "system_mirrors_configured";

    @Override
    public String getName() {
        return "Configure System Mirrors";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        log.info("开始配置系统镜像源...");
        
        // 检查是否需要配置中国镜像
        Boolean useChinaMirror = (Boolean) context.getProperty(DetectLocationCommand.USE_CHINA_MIRROR_KEY);
        if (useChinaMirror == null || !useChinaMirror) {
            log.info("跳过系统镜像源配置（不在中国大陆）");
            context.setProperty(SYSTEM_MIRRORS_CONFIGURED_KEY, false);
            return;
        }

        // 获取操作系统信息
        OsInfo osInfo = (OsInfo) context.getProperty(DetectOsCommand.OS_INFO_KEY);
        if (osInfo == null || osInfo.getSystemType() == SystemType.UNKNOWN) {
            log.warn("无法配置系统镜像源：操作系统信息未知");
            context.setProperty(SYSTEM_MIRRORS_CONFIGURED_KEY, false);
            return;
        }

        boolean configured = false;
        SystemType systemType = osInfo.getSystemType();
        
        try {
            switch (systemType) {
                case DEBIAN -> configured = configureDebianMirrors(context, osInfo);
                case UBUNTU -> configured = configureUbuntuMirrors(context, osInfo);
                case REDHAT, CENTOS -> configured = configureRhelMirrors(context, osInfo);
                case FEDORA -> configured = configureFedoraMirrors(context, osInfo);
                case ARCH -> configured = configureArchMirrors(context);
                case ALPINE -> configured = configureAlpineMirrors(context);
                default -> {
                    log.warn("当前操作系统 {} 的系统镜像源自动配置暂不支持", systemType);
                    configured = false;
                }
            }
        } catch (Exception e) {
            log.error("配置系统镜像源时发生错误", e);
            configured = false;
        }

        context.setProperty(SYSTEM_MIRRORS_CONFIGURED_KEY, configured);
        log.info("系统镜像源配置完成，结果: {}", configured ? "成功" : "失败");
    }

    /**
     * 配置Debian系统镜像源
     */
    private boolean configureDebianMirrors(CommandContext context, OsInfo osInfo) throws Exception {
        log.info("配置Debian系统镜像源...");
        
        // 检查是否已使用国内镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q -E \"aliyun|tuna|ustc|163\" /etc/apt/sources.list && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 /etc/apt/sources.list 已使用国内镜像，跳过替换");
            return updateAptCache(context);
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak");

        // 配置阿里云镜像
        String codename = osInfo.getVersionCodename();
        String mirrorUrl = "https://mirrors.aliyun.com/debian";
        String securityMirrorUrl = "https://mirrors.aliyun.com/debian-security";
        
        String sourcesContent = String.format(
            "deb %s/ %s main contrib non-free\\n" +
            "deb-src %s/ %s main contrib non-free\\n" +
            "deb %s/ %s-security main contrib non-free\\n" +
            "deb-src %s/ %s-security main contrib non-free\\n" +
            "deb %s/ %s-updates main contrib non-free\\n" +
            "deb-src %s/ %s-updates main contrib non-free\\n" +
            "deb %s/ %s-backports main contrib non-free\\n" +
            "deb-src %s/ %s-backports main contrib non-free",
            mirrorUrl, codename, mirrorUrl, codename,
            securityMirrorUrl, codename, securityMirrorUrl, codename,
            mirrorUrl, codename, mirrorUrl, codename,
            mirrorUrl, codename, mirrorUrl, codename
        );

        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            String.format("sudo tee /etc/apt/sources.list > /dev/null <<EOF\\n%s\\nEOF", sourcesContent)
        );

        if (result.isSuccess()) {
            log.info("Debian镜像源已替换为阿里云镜像");
            return updateAptCache(context);
        }
        return false;
    }

    /**
     * 配置Ubuntu系统镜像源
     */
    private boolean configureUbuntuMirrors(CommandContext context, OsInfo osInfo) throws Exception {
        log.info("配置Ubuntu系统镜像源...");
        
        // 检查是否已使用国内镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q -E \"aliyun|tuna|ustc|163\" /etc/apt/sources.list && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 /etc/apt/sources.list 已使用国内镜像，跳过替换");
            return updateAptCache(context);
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak");

        // 配置阿里云镜像
        String codename = osInfo.getVersionCodename();
        String mirrorUrl = "https://mirrors.aliyun.com/ubuntu";
        
        String sourcesContent = String.format(
            "deb %s/ %s main restricted universe multiverse\\n" +
            "deb-src %s/ %s main restricted universe multiverse\\n" +
            "deb %s/ %s-updates main restricted universe multiverse\\n" +
            "deb-src %s/ %s-updates main restricted universe multiverse\\n" +
            "deb %s/ %s-backports main restricted universe multiverse\\n" +
            "deb-src %s/ %s-backports main restricted universe multiverse\\n" +
            "deb %s/ %s-security main restricted universe multiverse\\n" +
            "deb-src %s/ %s-security main restricted universe multiverse",
            mirrorUrl, codename, mirrorUrl, codename,
            mirrorUrl, codename, mirrorUrl, codename,
            mirrorUrl, codename, mirrorUrl, codename,
            mirrorUrl, codename, mirrorUrl, codename
        );

        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            String.format("sudo tee /etc/apt/sources.list > /dev/null <<EOF\\n%s\\nEOF", sourcesContent)
        );

        if (result.isSuccess()) {
            log.info("Ubuntu镜像源已替换为阿里云镜像");
            return updateAptCache(context);
        }
        return false;
    }

    /**
     * 配置RHEL/CentOS系统镜像源
     */
    private boolean configureRhelMirrors(CommandContext context, OsInfo osInfo) throws Exception {
        log.info("配置RHEL/CentOS系统镜像源...");
        
        // 检查是否已使用国内镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q -E \"aliyun|tuna|ustc|163\" /etc/yum.repos.d/*.repo && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 /etc/yum.repos.d/ 已使用国内镜像，跳过替换");
            return updateYumCache(context, "yum");
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo mkdir -p /etc/yum.repos.d/bak");
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true");

        // 下载阿里云镜像配置
        String versionId = osInfo.getVersionId();
        String repoUrl = String.format("https://mirrors.aliyun.com/repo/Centos-%s.repo", versionId);
        
        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl)
        );

        if (result.isSuccess()) {
            log.info("RHEL/CentOS镜像源已替换为阿里云镜像");
            return updateYumCache(context, "yum");
        }
        return false;
    }

    /**
     * 配置Fedora系统镜像源
     */
    private boolean configureFedoraMirrors(CommandContext context, OsInfo osInfo) throws Exception {
        log.info("配置Fedora系统镜像源...");
        
        // 检查是否已使用国内镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q -E \"aliyun|tuna|ustc|163\" /etc/yum.repos.d/*.repo && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 /etc/yum.repos.d/ 已使用国内镜像，跳过替换");
            return updateYumCache(context, "dnf");
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo mkdir -p /etc/yum.repos.d/bak");
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true");

        // 获取Fedora版本并下载阿里云镜像配置
        String repoUrl = "https://mirrors.aliyun.com/fedora/fedora-$(rpm -E %fedora).repo";
        
        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl)
        );

        if (result.isSuccess()) {
            log.info("Fedora镜像源已替换为阿里云镜像");
            return updateYumCache(context, "dnf");
        }
        return false;
    }

    /**
     * 配置Arch Linux系统镜像源
     */
    private boolean configureArchMirrors(CommandContext context) throws Exception {
        log.info("配置Arch Linux系统镜像源...");
        
        // 检查是否已包含清华大学镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q \"tuna.tsinghua.edu.cn\" /etc/pacman.d/mirrorlist && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 pacman mirrorlist 已包含清华大学镜像，跳过");
            return updatePacmanCache(context);
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak");

        // 添加清华大学镜像到列表顶部
        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo sed -i '1s|^|Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/\\$repo/os/\\$arch\\n|' /etc/pacman.d/mirrorlist"
        );

        if (result.isSuccess()) {
            log.info("Arch Linux镜像源已添加清华大学镜像");
            return updatePacmanCache(context);
        }
        return false;
    }

    /**
     * 配置Alpine Linux系统镜像源
     */
    private boolean configureAlpineMirrors(CommandContext context) throws Exception {
        log.info("配置Alpine Linux系统镜像源...");
        
        // 检查是否已使用国内镜像
        CommandResult checkResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "grep -q \"aliyun\" /etc/apk/repositories && echo 'exists' || echo 'not_exists'"
        );
        
        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 apk repositories 已使用国内镜像，跳过");
            return updateApkCache(context);
        }

        // 备份原文件
        SshCommandUtil.executeCommand(context.getSshConnection(), "sudo cp /etc/apk/repositories /etc/apk/repositories.bak");

        // 替换为阿里云镜像
        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories"
        );

        if (result.isSuccess()) {
            log.info("Alpine Linux镜像源已替换为阿里云镜像");
            return updateApkCache(context);
        }
        return false;
    }

    /**
     * 更新APT缓存
     */
    private boolean updateAptCache(CommandContext context) throws Exception {
        log.info("正在刷新APT缓存...");
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "sudo apt-get update");
        return result.isSuccess();
    }

    /**
     * 更新YUM/DNF缓存
     */
    private boolean updateYumCache(CommandContext context, String packageManager) throws Exception {
        log.info("正在刷新{}缓存...", packageManager.toUpperCase());
        CommandResult result = SshCommandUtil.executeCommand(
            context.getSshConnection(), 
            String.format("sudo %s clean all && sudo %s makecache", packageManager, packageManager)
        );
        return result.isSuccess();
    }

    /**
     * 更新Pacman缓存
     */
    private boolean updatePacmanCache(CommandContext context) throws Exception {
        log.info("正在刷新Pacman缓存...");
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "sudo pacman -Syy --noconfirm");
        return result.isSuccess();
    }

    /**
     * 更新APK缓存
     */
    private boolean updateApkCache(CommandContext context) throws Exception {
        log.info("正在刷新APK缓存...");
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "sudo apk update");
        return result.isSuccess();
    }
}