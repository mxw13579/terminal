package com.fufu.terminal.command.impl.enhancement;

import com.fufu.terminal.command.base.EnhancementCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置系统软件镜像源的命令
 * 根据操作系统类型和地理位置配置相应的软件包管理器镜像源
 * 按系统类型分离不同的实现方法，避免耦合
 * @author lizelin
 */
@Slf4j
public class ConfigureSystemMirrorsCommand extends EnhancementCommand {

    public static final String SYSTEM_MIRRORS_CONFIGURED_KEY = "system_mirrors_configured";

    @Override
    public String getName() {
        return "Configure System Mirrors";
    }

    @Override
    protected String getEnhancementTargetName() {
        return "系统镜像源";
    }

    @Override
    protected boolean shouldEnhance(CommandContext context) {
        // 检查是否需要配置中国镜像
        return needsChinaMirror(context);
    }

    @Override
    protected boolean performEnhancement(CommandContext context) throws Exception {
        OsInfo osInfo = getOsInfo(context);
        if (osInfo == null || osInfo.getSystemType() == SystemType.UNKNOWN) {
            log.warn("无法配置系统镜像源：操作系统信息未知");
            return false;
        }

        return executeBySystemType(context,
            this::configureDebianUbuntuMirrors,    // Ubuntu/Debian
            this::configureRedhatFedoraMirrors,    // RHEL/CentOS/Fedora
            this::configureArchMirrors,            // Arch Linux
            this::configureAlpineMirrors,          // Alpine Linux
            this::configureSuseMirrors,            // SUSE
            this::configureDefaultMirrors          // 默认处理
        );
    }

    @Override
    protected void setEnhancementResult(CommandContext context, boolean success) {
        context.setProperty(SYSTEM_MIRRORS_CONFIGURED_KEY, success);
    }

    /**
     * 配置Debian/Ubuntu系统镜像源
     */
    private boolean configureDebianUbuntuMirrors(CommandContext context) throws Exception {
        OsInfo osInfo = getOsInfo(context);
        SystemType systemType = osInfo.getSystemType();

        log.info("配置{}系统镜像源...", systemType == SystemType.DEBIAN ? "Debian" : "Ubuntu");

        // 检查是否已使用国内镜像
        if (isAlreadyUsingChinaMirror(context, "/etc/apt/sources.list")) {
            log.info("检测到 /etc/apt/sources.list 已使用国内镜像，跳过替换");
            return updateAptCache(context);
        }

        // 备份原文件
        executeCommand(context, "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak");

        // 根据系统类型生成不同的源配置
        String sourcesContent;
        if (systemType == SystemType.DEBIAN) {
            sourcesContent = generateDebianSources(osInfo);
        } else {
            sourcesContent = generateUbuntuSources(osInfo);
        }

        CommandResult result = executeCommand(context,
            String.format("sudo tee /etc/apt/sources.list > /dev/null <<EOF\\n%s\\nEOF", sourcesContent));

        if (result.isSuccess()) {
            log.info("{}镜像源已替换为阿里云镜像", systemType == SystemType.DEBIAN ? "Debian" : "Ubuntu");
            return updateAptCache(context);
        }
        return false;
    }

    /**
     * 配置RHEL/CentOS/Fedora系统镜像源
     */
    private boolean configureRedhatFedoraMirrors(CommandContext context) throws Exception {
        OsInfo osInfo = getOsInfo(context);
        SystemType systemType = osInfo.getSystemType();

        log.info("配置{}系统镜像源...", systemType);

        // 检查是否已使用国内镜像
        if (isAlreadyUsingChinaMirror(context, "/etc/yum.repos.d/*.repo")) {
            log.info("检测到 /etc/yum.repos.d/ 已使用国内镜像，跳过替换");
            return updateYumDnfCache(context, systemType);
        }

        // 备份原文件
        executeCommand(context, "sudo mkdir -p /etc/yum.repos.d/bak");
        executeCommand(context, "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true");

        // 根据系统类型下载不同的镜像配置
        boolean downloadSuccess;
        if (systemType == SystemType.FEDORA) {
            downloadSuccess = downloadFedoraMirrorConfig(context);
        } else {
            downloadSuccess = downloadRhelCentosMirrorConfig(context, osInfo);
        }

        if (downloadSuccess) {
            log.info("{}镜像源已替换为阿里云镜像", systemType);
            return updateYumDnfCache(context, systemType);
        }
        return false;
    }

    /**
     * 配置Arch Linux系统镜像源
     */
    private boolean configureArchMirrors(CommandContext context) throws Exception {
        log.info("配置Arch Linux系统镜像源...");

        // 检查是否已包含清华大学镜像
        CommandResult checkResult = executeCommand(context,
            "grep -q \"tuna.tsinghua.edu.cn\" /etc/pacman.d/mirrorlist && echo 'exists' || echo 'not_exists'");

        if (checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim())) {
            log.info("检测到 pacman mirrorlist 已包含清华大学镜像，跳过");
            return updatePacmanCache(context);
        }

        // 备份原文件
        executeCommand(context, "sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak");

        // 添加清华大学镜像到列表顶部
        CommandResult result = executeCommand(context,
            "sudo sed -i '1s|^|Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/\\$repo/os/\\$arch\\n|' /etc/pacman.d/mirrorlist");

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
        if (isAlreadyUsingChinaMirror(context, "/etc/apk/repositories")) {
            log.info("检测到 apk repositories 已使用国内镜像，跳过");
            return updateApkCache(context);
        }

        // 备份原文件
        executeCommand(context, "sudo cp /etc/apk/repositories /etc/apk/repositories.bak");

        // 替换为阿里云镜像
        CommandResult result = executeCommand(context,
            "sudo sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories");

        if (result.isSuccess()) {
            log.info("Alpine Linux镜像源已替换为阿里云镜像");
            return updateApkCache(context);
        }
        return false;
    }

    /**
     * 配置SUSE系统镜像源
     */
    private boolean configureSuseMirrors(CommandContext context) throws Exception {
        log.info("配置SUSE系统镜像源...");
        log.warn("SUSE系统镜像源自动配置暂未实现");
        return false;
    }

    /**
     * 默认镜像源配置处理
     */
    private boolean configureDefaultMirrors(CommandContext context) throws Exception {
        OsInfo osInfo = getOsInfo(context);
        log.warn("当前操作系统 {} 的系统镜像源自动配置暂不支持",
                osInfo != null ? osInfo.getSystemType() : "UNKNOWN");
        return false;
    }

    // -------- 私有辅助方法 --------

    private boolean isAlreadyUsingChinaMirror(CommandContext context, String filePath) throws Exception {
        CommandResult checkResult = executeCommand(context,
            String.format("grep -q -E \"aliyun|tuna|ustc|163\" %s && echo 'exists' || echo 'not_exists'", filePath));
        return checkResult.isSuccess() && "exists".equals(checkResult.getStdout().trim());
    }

    private String generateDebianSources(OsInfo osInfo) {
        String codename = osInfo.getVersionCodename();
        String mirrorUrl = "https://mirrors.aliyun.com/debian";
        String securityMirrorUrl = "https://mirrors.aliyun.com/debian-security";

        return String.format(
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
    }

    private String generateUbuntuSources(OsInfo osInfo) {
        String codename = osInfo.getVersionCodename();
        String mirrorUrl = "https://mirrors.aliyun.com/ubuntu";

        return String.format(
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
    }

    private boolean downloadFedoraMirrorConfig(CommandContext context) throws Exception {
        String repoUrl = "https://mirrors.aliyun.com/fedora/fedora-$(rpm -E %fedora).repo";
        CommandResult result = executeCommand(context,
            String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl));
        return result.isSuccess();
    }

    private boolean downloadRhelCentosMirrorConfig(CommandContext context, OsInfo osInfo) throws Exception {
        String versionId = osInfo.getVersionId();
        String repoUrl = String.format("https://mirrors.aliyun.com/repo/Centos-%s.repo", versionId);
        CommandResult result = executeCommand(context,
            String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl));
        return result.isSuccess();
    }

    private boolean updateAptCache(CommandContext context) throws Exception {
        log.info("正在刷新APT缓存...");
        CommandResult result = executeCommand(context, "sudo apt-get update");
        return result.isSuccess();
    }

    private boolean updateYumDnfCache(CommandContext context, SystemType systemType) throws Exception {
        String packageManager = (systemType == SystemType.FEDORA) ? "dnf" : "yum";
        log.info("正在刷新{}缓存...", packageManager.toUpperCase());
        CommandResult result = executeCommand(context,
            String.format("sudo %s clean all && sudo %s makecache", packageManager, packageManager));
        return result.isSuccess();
    }

    private boolean updatePacmanCache(CommandContext context) throws Exception {
        log.info("正在刷新Pacman缓存...");
        CommandResult result = executeCommand(context, "sudo pacman -Syy --noconfirm");
        return result.isSuccess();
    }

    private boolean updateApkCache(CommandContext context) throws Exception {
        log.info("正在刷新APK缓存...");
        CommandResult result = executeCommand(context, "sudo apk update");
        return result.isSuccess();
    }
}
