package com.fufu.terminal.entity.enums;

/**
 * 脚本类型枚举
 */
public enum ScriptType {
    BUILT_IN_STATIC("内置静态脚本"),      // 内置静态脚本，不需要参数
    BUILT_IN_PARAM("内置参数化脚本"),     // 内置参数化脚本，需要参数
    USER_SIMPLE("用户简单脚本"),         // 用户简单脚本
    USER_TEMPLATE("用户模板脚本");       // 用户模板脚本

    private final String description;

    ScriptType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}