package com.fufu.terminal.entity.enums;

/**
 * 聚合脚本类型枚举
 */
public enum AggregateScriptType {
    GENERIC_TEMPLATE("通用模板聚合脚本"),      // 通用模板聚合脚本，可复用
    PROJECT_SPECIFIC("项目特定聚合脚本");     // 项目特定聚合脚本，针对特定项目

    private final String description;

    AggregateScriptType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}