package com.fufu.terminal.service.script.strategy.registry;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.BuiltInScriptType;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 脚本类型注册表服务
 * 管理内置脚本的注册、类型判断和元数据访问
 */
@Slf4j
@Service
public class ScriptTypeRegistry {

    /**
     * 内置脚本元数据存储
     */
    private final Map<String, BuiltInScriptMetadata> builtInScriptsMetadata = new ConcurrentHashMap<>();
    
    /**
     * 内置脚本命令存储
     */
    private final Map<String, AtomicScriptCommand> builtInScriptsCommands = new ConcurrentHashMap<>();

    /**
     * 注册内置脚本
     * 
     * @param metadata 脚本元数据
     * @param command 脚本命令实现
     */
    public void registerBuiltInScript(BuiltInScriptMetadata metadata, AtomicScriptCommand command) {
        if (metadata == null || command == null) {
            throw new IllegalArgumentException("脚本元数据和命令不能为空");
        }

        String scriptId = metadata.getScriptId();
        if (scriptId == null || scriptId.trim().isEmpty()) {
            throw new IllegalArgumentException("脚本ID不能为空");
        }

        builtInScriptsMetadata.put(scriptId, metadata);
        builtInScriptsCommands.put(scriptId, command);
        
        log.info("注册内置脚本: {} ({}), 类型: {}", 
            metadata.getName(), scriptId, metadata.getType());
    }

    /**
     * 判断脚本类型
     * 
     * @param scriptId 脚本ID
     * @return 脚本来源类型
     */
    public ScriptSourceType determineScriptType(String scriptId) {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            return null;
        }

        BuiltInScriptMetadata metadata = builtInScriptsMetadata.get(scriptId);
        if (metadata != null) {
            switch (metadata.getType()) {
                case STATIC:
                    return ScriptSourceType.BUILT_IN_STATIC;
                case DYNAMIC:
                    return ScriptSourceType.BUILT_IN_DYNAMIC;
                case INTERACTIVE:
                    return ScriptSourceType.BUILT_IN_INTERACTIVE;
                default:
                    return ScriptSourceType.BUILT_IN_STATIC; // 默认处理
            }
        }

        // 默认认为是用户定义脚本
        return ScriptSourceType.USER_DEFINED;
    }

    /**
     * 检查是否为静态内置脚本
     * 
     * @param scriptId 脚本ID
     * @return 是否为静态内置脚本
     */
    public boolean isStaticBuiltInScript(String scriptId) {
        BuiltInScriptMetadata metadata = builtInScriptsMetadata.get(scriptId);
        return metadata != null && metadata.getType() == BuiltInScriptType.STATIC;
    }

    /**
     * 检查是否为动态内置脚本
     * 
     * @param scriptId 脚本ID
     * @return 是否为动态内置脚本
     */
    public boolean isDynamicBuiltInScript(String scriptId) {
        BuiltInScriptMetadata metadata = builtInScriptsMetadata.get(scriptId);
        return metadata != null && metadata.getType() == BuiltInScriptType.DYNAMIC;
    }

    /**
     * 检查是否为交互内置脚本
     * 
     * @param scriptId 脚本ID
     * @return 是否为交互内置脚本
     */
    public boolean isInteractiveBuiltInScript(String scriptId) {
        BuiltInScriptMetadata metadata = builtInScriptsMetadata.get(scriptId);
        return metadata != null && metadata.getType() == BuiltInScriptType.INTERACTIVE;
    }

    /**
     * 检查是否为内置脚本
     * 
     * @param scriptId 脚本ID
     * @return 是否为内置脚本
     */
    public boolean isBuiltInScript(String scriptId) {
        return builtInScriptsMetadata.containsKey(scriptId);
    }

    /**
     * 获取内置脚本元数据
     * 
     * @param scriptId 脚本ID
     * @return 脚本元数据，不存在返回null
     */
    public BuiltInScriptMetadata getBuiltInScriptMetadata(String scriptId) {
        return builtInScriptsMetadata.get(scriptId);
    }

    /**
     * 获取内置脚本命令
     * 
     * @param scriptId 脚本ID
     * @return 脚本命令，不存在返回null
     */
    public AtomicScriptCommand getBuiltInScriptCommand(String scriptId) {
        return builtInScriptsCommands.get(scriptId);
    }

    /**
     * 获取所有内置脚本ID列表
     * 
     * @return 内置脚本ID列表
     */
    public List<String> getAllBuiltInScriptIds() {
        return builtInScriptsMetadata.keySet().stream()
            .collect(Collectors.toList());
    }

    /**
     * 获取所有内置脚本元数据
     * 
     * @return 内置脚本元数据列表
     */
    public List<BuiltInScriptMetadata> getAllBuiltInScriptsMetadata() {
        return builtInScriptsMetadata.values().stream()
            .collect(Collectors.toList());
    }

    /**
     * 根据类型获取内置脚本
     * 
     * @param type 脚本类型
     * @return 指定类型的内置脚本元数据列表
     */
    public List<BuiltInScriptMetadata> getBuiltInScriptsByType(BuiltInScriptType type) {
        return builtInScriptsMetadata.values().stream()
            .filter(metadata -> metadata.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * 获取注册的内置脚本数量
     * 
     * @return 内置脚本数量
     */
    public int getBuiltInScriptCount() {
        return builtInScriptsMetadata.size();
    }

    /**
     * 清空所有注册的内置脚本（主要用于测试）
     */
    public void clearAll() {
        builtInScriptsMetadata.clear();
        builtInScriptsCommands.clear();
        log.warn("清空所有注册的内置脚本");
    }
}