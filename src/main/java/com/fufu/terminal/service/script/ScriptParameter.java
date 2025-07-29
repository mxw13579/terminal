package com.fufu.terminal.service.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本参数定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptParameter {
    
    /**
     * 参数名称
     */
    private String name;
    
    /**
     * 参数类型
     */
    private ParameterType type;
    
    /**
     * 参数描述
     */
    private String description;
    
    /**
     * 是否必需
     */
    private boolean required;
    
    /**
     * 默认值
     */
    private Object defaultValue;
    
    /**
     * 参数验证规则
     */
    private String validationRule;
    
    /**
     * 参数类型枚举
     */
    public enum ParameterType {
        STRING("字符串"),
        INTEGER("整数"),
        BOOLEAN("布尔值"),
        ARRAY("数组"),
        OBJECT("对象");
        
        private final String displayName;
        
        ParameterType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}