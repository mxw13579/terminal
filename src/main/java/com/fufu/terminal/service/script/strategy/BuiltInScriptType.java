package com.fufu.terminal.service.script.strategy;

/**
 * 内置脚本类型枚举
 * 用于区分静态脚本和动态脚本
 */
public enum BuiltInScriptType {
    
    /**
     * 静态脚本：无参数，立即执行
     * 例如：系统信息查看
     */
    STATIC("静态脚本", "无参数立即执行的脚本"),
    
    /**
     * 动态脚本：需要参数，参数收集后执行  
     * 例如：Docker安装、MySQL安装
     */
    DYNAMIC("动态脚本", "需要参数配置的脚本"),
    
    /**
     * 交互脚本：需要用户实时交互确认
     * 例如：用户确认、条件判断
     */
    INTERACTIVE("交互脚本", "需要用户实时交互的脚本");
    
    private final String displayName;
    private final String description;
    
    BuiltInScriptType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}