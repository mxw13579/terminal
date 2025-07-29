package com.fufu.terminal.service.script;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.entity.AtomicScript;

/**
 * 统一原子脚本执行接口
 * 将内置脚本和可配置脚本统一处理
 */
public interface UnifiedAtomicScript {
    
    /**
     * 获取脚本ID（用于标识）
     */
    String getScriptId();
    
    /**
     * 获取脚本名称
     */
    String getName();
    
    /**
     * 获取脚本描述
     */
    String getDescription();
    
    /**
     * 获取脚本类型
     */
    ScriptType getScriptType();
    
    /**
     * 获取脚本标签（用于分类）
     */
    String[] getTags();
    
    /**
     * 检查是否应该执行此脚本
     */
    boolean shouldExecute(CommandContext context);
    
    /**
     * 执行脚本
     */
    ScriptExecutionResult execute(CommandContext context) throws Exception;
    
    /**
     * 获取输入参数定义
     */
    ScriptParameter[] getInputParameters();
    
    /**
     * 获取输出参数定义
     */
    ScriptParameter[] getOutputParameters();
    
    /**
     * 脚本类型枚举
     */
    enum ScriptType {
        BUILT_IN("内置脚本"),
        CONFIGURABLE("可配置脚本");
        
        private final String displayName;
        
        ScriptType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}