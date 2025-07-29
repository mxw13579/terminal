package com.fufu.terminal.command.registry;

import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.environment.CheckGitCommand;
import com.fufu.terminal.command.impl.environment.CheckDockerCommand;
import com.fufu.terminal.command.impl.environment.CheckCurlCommand;
import com.fufu.terminal.command.impl.environment.CheckUnzipCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureSystemMirrorsCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureDockerMirrorCommand;
import com.fufu.terminal.command.model.CommandInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 命令注册器
 * 负责注册所有可用的命令，并提供命令查询功能
 * @author lizelin
 */
@Component
@Slf4j
public class CommandRegistry {

    private final Map<String, CommandInfo> commandMap = new LinkedHashMap<>();
    private final Map<String, List<CommandInfo>> categoryMap = new LinkedHashMap<>();

    @PostConstruct
    public void registerCommands() {
        log.info("开始注册命令...");

        // 前置处理命令
        register("detect-os", new DetectOsCommand(), "前置处理", "检测操作系统",
                "检测远程服务器的操作系统类型和版本信息", "el-icon-cpu", true);

        register("detect-location", new DetectLocationCommand(), "前置处理", "检测服务器位置",
                "检测服务器地理位置，判断是否需要使用中国镜像源", "el-icon-location", false);

        // 环境检查命令
        register("check-curl", new CheckCurlCommand(), "环境检查", "检查Curl工具",
                "检测Curl命令行工具是否已安装及版本信息", "el-icon-link", false);

        register("check-unzip", new CheckUnzipCommand(), "环境检查", "检查解压工具",
                "检测Unzip解压工具是否已安装，用于解压下载的软件包", "el-icon-document", false);

        register("check-git", new CheckGitCommand(), "环境检查", "检查Git环境",
                "检测Git版本控制工具是否已安装及版本信息", "el-icon-s-cooperation", false);

        register("check-docker", new CheckDockerCommand(), "环境检查", "检查Docker环境",
                "检测Docker容器引擎的安装状态和运行状态", "el-icon-box", false);

        // 安装增强命令
        register("config-mirrors", new ConfigureSystemMirrorsCommand(), "安装增强", "配置系统镜像源",
                "为系统包管理器配置国内镜像源，加速软件包下载", "el-icon-download", false,
                Arrays.asList("detect-os", "detect-location"));

        register("config-docker-mirror", new ConfigureDockerMirrorCommand(), "安装增强", "配置Docker镜像",
                "配置Docker镜像加速器，加速Docker镜像拉取", "el-icon-s-grid", false,
                Arrays.asList("check-docker", "detect-location"));

        // 构建分类映射
        buildCategoryMap();

        log.info("命令注册完成，共注册 {} 个命令", commandMap.size());
    }

    /**
     * 注册命令（不带依赖）
     */
    private void register(String id, com.fufu.terminal.command.Command command, String category,
                         String name, String description, String icon, boolean required) {
        register(id, command, category, name, description, icon, required, new ArrayList<>());
    }

    /**
     * 注册命令（带依赖）
     */
    private void register(String id, com.fufu.terminal.command.Command command, String category,
                         String name, String description, String icon, boolean required,
                         List<String> dependencies) {
        CommandInfo commandInfo = new CommandInfo();
        commandInfo.setId(id);
        commandInfo.setCommand(command);
        commandInfo.setCategory(category);
        commandInfo.setName(name);
        commandInfo.setDescription(description);
        commandInfo.setIcon(icon);
        commandInfo.setRequired(required);
        commandInfo.setDependencies(dependencies != null ? dependencies : new ArrayList<>());

        commandMap.put(id, commandInfo);
        log.debug("注册命令: {} - {}", id, name);
    }

    /**
     * 构建分类映射
     */
    private void buildCategoryMap() {
        categoryMap.clear();
        commandMap.values().forEach(commandInfo -> {
            categoryMap.computeIfAbsent(commandInfo.getCategory(), k -> new ArrayList<>())
                      .add(commandInfo);
        });
    }

    /**
     * 获取所有命令
     */
    public Map<String, CommandInfo> getAllCommands() {
        return new LinkedHashMap<>(commandMap);
    }

    /**
     * 获取按分类组织的命令
     */
    public Map<String, List<CommandInfo>> getCommandsByCategory() {
        return categoryMap.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> new ArrayList<>(entry.getValue()),
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
    }

    /**
     * 根据ID获取命令
     */
    public CommandInfo getCommand(String id) {
        return commandMap.get(id);
    }

    /**
     * 检查命令是否存在
     */
    public boolean hasCommand(String id) {
        return commandMap.containsKey(id);
    }

    /**
     * 获取指定分类的所有命令
     */
    public List<CommandInfo> getCommandsByCategory(String category) {
        return categoryMap.getOrDefault(category, new ArrayList<>());
    }

    /**
     * 获取所有分类名称
     */
    public Set<String> getAllCategories() {
        return new LinkedHashSet<>(categoryMap.keySet());
    }

    /**
     * 验证命令依赖关系
     */
    public boolean validateDependencies(List<String> commandIds) {
        Set<String> availableCommands = new HashSet<>();

        for (String commandId : commandIds) {
            CommandInfo commandInfo = getCommand(commandId);
            if (commandInfo == null) {
                log.warn("未找到命令: {}", commandId);
                return false;
            }

            // 检查依赖是否已满足
            for (String dependency : commandInfo.getDependencies()) {
                if (!availableCommands.contains(dependency)) {
                    log.warn("命令 {} 的依赖 {} 未满足", commandId, dependency);
                    return false;
                }
            }

            availableCommands.add(commandId);
        }

        return true;
    }

    /**
     * 自动添加缺失的依赖命令
     */
    public List<String> addMissingDependencies(List<String> commandIds) {
        List<String> result = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (String commandId : commandIds) {
            addCommandWithDependencies(commandId, result, added);
        }

        return result;
    }

    /**
     * 递归添加命令及其依赖
     */
    private void addCommandWithDependencies(String commandId, List<String> result, Set<String> added) {
        if (added.contains(commandId)) {
            return;
        }

        CommandInfo commandInfo = getCommand(commandId);
        if (commandInfo == null) {
            return;
        }

        // 先添加依赖
        for (String dependency : commandInfo.getDependencies()) {
            addCommandWithDependencies(dependency, result, added);
        }

        // 再添加当前命令
        result.add(commandId);
        added.add(commandId);
    }
}
