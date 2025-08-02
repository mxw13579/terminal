package com.fufu.terminal.service.script.strategy.registry;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * 内置脚本注册服务
 * 在应用启动时自动注册所有内置脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltInScriptRegistrationService {

    private final ScriptTypeRegistry scriptTypeRegistry;
    private final ApplicationContext applicationContext;

    /**
     * 应用启动时注册所有内置脚本
     */
    @PostConstruct
    public void registerAllBuiltInScripts() {
        log.info("开始注册内置脚本...");

        try {
            // 获取所有实现了 BuiltInScriptMetadata 接口的 Bean
            Map<String, BuiltInScriptMetadata> metadataBeans = applicationContext.getBeansOfType(BuiltInScriptMetadata.class);
            
            int registeredCount = 0;
            int failedCount = 0;

            for (Map.Entry<String, BuiltInScriptMetadata> entry : metadataBeans.entrySet()) {
                String beanName = entry.getKey();
                BuiltInScriptMetadata metadata = entry.getValue();

                try {
                    // 验证该Bean是否也实现了AtomicScriptCommand接口
                    if (!(metadata instanceof AtomicScriptCommand)) {
                        log.warn("跳过注册脚本 {}: 未实现 AtomicScriptCommand 接口", beanName);
                        continue;
                    }

                    AtomicScriptCommand command = (AtomicScriptCommand) metadata;
                    
                    // 注册脚本
                    scriptTypeRegistry.registerBuiltInScript(metadata, command);
                    registeredCount++;
                    
                    log.debug("成功注册内置脚本: {} ({}), 类型: {}", 
                        metadata.getName(), metadata.getScriptId(), metadata.getType());
                        
                } catch (Exception e) {
                    failedCount++;
                    log.error("注册内置脚本失败: {} ({})", metadata.getName(), beanName, e);
                }
            }

            log.info("内置脚本注册完成，成功: {}, 失败: {}, 总计: {}", 
                registeredCount, failedCount, metadataBeans.size());

            // 记录注册的脚本详情
            if (log.isInfoEnabled()) {
                logRegisteredScriptsDetails();
            }

        } catch (Exception e) {
            log.error("注册内置脚本过程中发生异常", e);
        }
    }

    /**
     * 记录已注册脚本的详细信息
     */
    private void logRegisteredScriptsDetails() {
        try {
            var staticScripts = scriptTypeRegistry.getBuiltInScriptsByType(
                com.fufu.terminal.service.script.strategy.BuiltInScriptType.STATIC);
            var dynamicScripts = scriptTypeRegistry.getBuiltInScriptsByType(
                com.fufu.terminal.service.script.strategy.BuiltInScriptType.DYNAMIC);

            log.info("已注册静态脚本 ({} 个):", staticScripts.size());
            for (var script : staticScripts) {
                log.info("  - {} ({}): {}", script.getName(), script.getScriptId(), script.getDescription());
            }

            log.info("已注册动态脚本 ({} 个):", dynamicScripts.size());
            for (var script : dynamicScripts) {
                log.info("  - {} ({}): {}, 参数数量: {}", 
                    script.getName(), script.getScriptId(), script.getDescription(), 
                    script.getParameters().size());
            }

        } catch (Exception e) {
            log.warn("记录脚本详情时发生异常", e);
        }
    }

    /**
     * 手动重新注册所有脚本（主要用于测试或热更新）
     */
    public void reregisterAllScripts() {
        log.info("手动重新注册所有内置脚本");
        scriptTypeRegistry.clearAll();
        registerAllBuiltInScripts();
    }

    /**
     * 获取注册统计信息
     */
    public RegistrationStats getRegistrationStats() {
        int totalBuiltIn = scriptTypeRegistry.getBuiltInScriptCount();
        int staticCount = scriptTypeRegistry.getBuiltInScriptsByType(
            com.fufu.terminal.service.script.strategy.BuiltInScriptType.STATIC).size();
        int dynamicCount = scriptTypeRegistry.getBuiltInScriptsByType(
            com.fufu.terminal.service.script.strategy.BuiltInScriptType.DYNAMIC).size();

        return new RegistrationStats(totalBuiltIn, staticCount, dynamicCount);
    }

    /**
     * 注册统计信息
     */
    public record RegistrationStats(int total, int staticScripts, int dynamicScripts) {
        public boolean isHealthy() {
            return total > 0 && (staticScripts + dynamicScripts) == total;
        }
    }
}