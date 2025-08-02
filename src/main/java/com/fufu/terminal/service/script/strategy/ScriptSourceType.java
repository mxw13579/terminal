package com.fufu.terminal.service.script.strategy;

/**
 * 脚本来源类型枚举
 * 用于前端条件渲染和策略选择
 */
public enum ScriptSourceType {
    
    /**
     * 内置静态脚本：无参数，立即执行
     */
    BUILT_IN_STATIC("内置静态脚本", "系统预定义的无参数脚本"),
    
    /**
     * 内置动态脚本：有参数，收集后执行
     */
    BUILT_IN_DYNAMIC("内置动态脚本", "系统预定义的参数化脚本"),
    
    /**
     * 用户定义脚本：数据库驱动，完全可定制
     */
    USER_DEFINED("用户定义脚本", "用户自定义的数据库存储脚本");
    
    private final String displayName;
    private final String description;
    
    ScriptSourceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断是否为内置脚本
     */
    public boolean isBuiltIn() {
        return this == BUILT_IN_STATIC || this == BUILT_IN_DYNAMIC;
    }
    
    /**
     * 判断是否需要参数
     */
    public boolean requiresParameters() {
        return this == BUILT_IN_DYNAMIC || this == USER_DEFINED;
    }
}