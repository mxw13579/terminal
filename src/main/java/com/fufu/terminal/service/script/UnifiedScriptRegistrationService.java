package com.fufu.terminal.service.script;

import com.fufu.terminal.command.impl.enhancement.ConfigureDockerMirrorCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureSystemMirrorsCommand;
import com.fufu.terminal.command.impl.environment.*;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一脚本注册初始化服务
 * 在应用启动时自动注册所有内置脚本和可配置脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedScriptRegistrationService implements ApplicationRunner {

    private final UnifiedScriptRegistry scriptRegistry;
    private final AtomicScriptService atomicScriptService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化统一脚本注册表...");

        try {
            // 注册内置脚本
            registerBuiltInScripts();

            // 注册可配置脚本
            registerConfigurableScripts();

            // 输出统计信息
            UnifiedScriptRegistry.ScriptRegistryStats stats = scriptRegistry.getStats();
            log.info("脚本注册表初始化完成: {}", stats);

        } catch (Exception e) {
            log.error("脚本注册表初始化失败", e);
            throw e;
        }
    }

    /**
     * 注册所有内置脚本
     */
    private void registerBuiltInScripts() {
        log.info("开始注册内置脚本...");

        // 预处理脚本
        scriptRegistry.registerBuiltInScript(
            new DetectOsCommand(),
            "detect_os",
            new String[]{"预处理", "系统检测", "操作系统"}
        );

        scriptRegistry.registerBuiltInScript(
            new DetectLocationCommand(),
            "detect_location",
            new String[]{"预处理", "地理位置", "网络"}
        );

        // 环境检查脚本
        scriptRegistry.registerBuiltInScript(
            new CheckCurlCommand(),
            "check_curl",
            new String[]{"环境检查", "网络工具", "curl"}
        );

        scriptRegistry.registerBuiltInScript(
            new CheckDockerCommand(),
            "check_docker",
            new String[]{"环境检查", "容器", "docker"}
        );

        scriptRegistry.registerBuiltInScript(
            new CheckGitCommand(),
            "check_git",
            new String[]{"环境检查", "版本控制", "git"}
        );

        scriptRegistry.registerBuiltInScript(
            new CheckUnzipCommand(),
            "check_unzip",
            new String[]{"环境检查", "压缩工具", "unzip"}
        );

        // 系统增强脚本
        scriptRegistry.registerBuiltInScript(
            new ConfigureDockerMirrorCommand(),
            "configure_docker_mirror",
            new String[]{"系统增强", "容器", "镜像配置", "docker"}
        );

        scriptRegistry.registerBuiltInScript(
            new ConfigureSystemMirrorsCommand(),
            "configure_system_mirrors",
            new String[]{"系统增强", "软件源", "镜像配置", "包管理"}
        );

        log.info("内置脚本注册完成，共注册 {} 个脚本", scriptRegistry.getBuiltInScripts().size());
    }

    /**
     * 注册所有可配置脚本
     */
    private void registerConfigurableScripts() {
        log.info("开始注册可配置脚本...");

        try {
            List<AtomicScript> activeScripts = atomicScriptService.getAtomicScriptsByStatus(AtomicScript.Status.ACTIVE);

            for (AtomicScript script : activeScripts) {
                scriptRegistry.registerConfigurableScript(script);
            }

            log.info("可配置脚本注册完成，共注册 {} 个脚本", activeScripts.size());

        } catch (Exception e) {
            log.error("注册可配置脚本失败", e);
            // 继续执行，不因为可配置脚本失败而影响整个初始化过程
        }
    }

    /**
     * 监听脚本变更事件并重新加载
     */
    @EventListener
    public void handleScriptChangeEvent(AtomicScriptService.ScriptChangeEvent event) {
        log.info("收到脚本变更事件，重新加载可配置脚本...");
        reloadConfigurableScripts();
    }

    /**
     * 重新加载可配置脚本
     * 当AtomicScript数据发生变化时调用
     */
    public void reloadConfigurableScripts() {
        log.info("重新加载可配置脚本...");

        try {
            List<AtomicScript> activeScripts = atomicScriptService.getAtomicScriptsByStatus(AtomicScript.Status.ACTIVE);
            scriptRegistry.reloadConfigurableScripts(activeScripts);

            log.info("可配置脚本重新加载完成，当前共 {} 个活跃脚本", activeScripts.size());

        } catch (Exception e) {
            log.error("重新加载可配置脚本失败", e);
        }
    }

    /**
     * 获取推荐的脚本组合
     * 用于快速初始化常用的脚本序列
     */
    public String[] getRecommendedScriptSequence() {
        return new String[]{
            "detect_os",              // 检测操作系统
            "detect_location",        // 检测地理位置
            "configure_system_mirrors", // 配置系统镜像源
            "check_curl",            // 检查curl
            "check_git",             // 检查git
            "check_docker"           // 检查docker
        };
    }

    /**
     * 获取系统管理相关的脚本
     */
    public List<String> getSystemManagementScripts() {
        return scriptRegistry.getScriptsByTag("系统增强").stream()
                .map(script -> script.getScriptId())
                .toList();
    }

    /**
     * 获取环境检查相关的脚本
     */
    public List<String> getEnvironmentCheckScripts() {
        return scriptRegistry.getScriptsByTag("环境检查").stream()
                .map(script -> script.getScriptId())
                .toList();
    }
}
