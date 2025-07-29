package com.fufu.terminal.command.engine;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.model.CommandInfo;
import com.fufu.terminal.command.model.ExecutionProgress;
import com.fufu.terminal.command.registry.CommandRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 脚本执行引擎
 * 负责按顺序执行命令列表，并实时反馈执行进度
 * @author lizelin
 */
@Component
@Slf4j
public class ScriptEngine {
    
    @Autowired
    private CommandRegistry commandRegistry;
    
    /**
     * 执行脚本
     * @param commandIds 要执行的命令ID列表
     * @param context 命令执行上下文
     * @param progressCallback 进度回调函数
     */
    public void executeScript(List<String> commandIds, CommandContext context, 
                            Consumer<ExecutionProgress> progressCallback) {
        
        log.info("开始执行脚本，共 {} 个命令", commandIds.size());
        
        int total = commandIds.size();
        int current = 0;
        
        for (String commandId : commandIds) {
            current++;
            
            CommandInfo commandInfo = commandRegistry.getCommand(commandId);
            if (commandInfo == null) {
                log.warn("未找到命令: {}", commandId);
                sendProgress(progressCallback, current, total, commandId, "未知命令", 
                           "failed", "命令不存在", 0, 0);
                continue;
            }
            
            long startTime = System.currentTimeMillis();
            
            try {
                // 发送开始执行状态
                sendProgress(progressCallback, current, total, commandId, commandInfo.getName(), 
                           "executing", "正在执行...", startTime, 0);
                
                // 检查是否应该执行
                if (!commandInfo.getCommand().shouldExecute(context)) {
                    long endTime = System.currentTimeMillis();
                    sendProgress(progressCallback, current, total, commandId, commandInfo.getName(), 
                               "skipped", "条件不满足，已跳过", startTime, endTime);
                    log.info("命令 {} 被跳过：条件不满足", commandId);
                    continue;
                }
                
                log.info("执行命令: {} - {}", commandId, commandInfo.getName());
                
                // 执行命令
                commandInfo.getCommand().execute(context);
                
                long endTime = System.currentTimeMillis();
                sendProgress(progressCallback, current, total, commandId, commandInfo.getName(), 
                           "completed", "执行完成", startTime, endTime);
                
                log.info("命令 {} 执行完成，耗时 {}ms", commandId, endTime - startTime);
                
            } catch (Exception e) {
                long endTime = System.currentTimeMillis();
                String errorMessage = e.getMessage() != null ? e.getMessage() : "执行异常";
                
                sendProgress(progressCallback, current, total, commandId, commandInfo.getName(), 
                           "failed", errorMessage, startTime, endTime);
                
                log.error("执行命令 {} 失败，耗时 {}ms", commandId, endTime - startTime, e);
                
                // 根据命令是否为必须执行决定是否中断
                if (commandInfo.isRequired()) {
                    log.error("必须执行的命令 {} 失败，中断脚本执行", commandId);
                    break;
                }
            }
        }
        
        log.info("脚本执行完成");
    }
    
    /**
     * 发送执行进度
     */
    private void sendProgress(Consumer<ExecutionProgress> progressCallback, 
                            int current, int total, String commandId, String commandName,
                            String status, String message, long startTime, long endTime) {
        
        ExecutionProgress progress = new ExecutionProgress();
        progress.setCurrent(current);
        progress.setTotal(total);
        progress.setCommandId(commandId);
        progress.setCommandName(commandName);
        progress.setStatus(status);
        progress.setMessage(message);
        progress.setStartTime(startTime);
        progress.setEndTime(endTime);
        
        try {
            progressCallback.accept(progress);
        } catch (Exception e) {
            log.error("发送进度更新失败", e);
        }
    }
    
    /**
     * 验证脚本的有效性
     */
    public boolean validateScript(List<String> commandIds) {
        if (commandIds == null || commandIds.isEmpty()) {
            log.warn("脚本为空");
            return false;
        }
        
        // 检查所有命令是否存在
        for (String commandId : commandIds) {
            if (!commandRegistry.hasCommand(commandId)) {
                log.warn("脚本包含无效命令: {}", commandId);
                return false;
            }
        }
        
        // 验证依赖关系
        return commandRegistry.validateDependencies(commandIds);
    }
    
    /**
     * 自动优化脚本（添加缺失依赖、去重、排序）
     */
    public List<String> optimizeScript(List<String> commandIds) {
        log.info("优化脚本，原始命令数: {}", commandIds.size());
        
        // 添加缺失的依赖
        List<String> optimized = commandRegistry.addMissingDependencies(commandIds);
        
        log.info("优化完成，最终命令数: {}", optimized.size());
        return optimized;
    }
}