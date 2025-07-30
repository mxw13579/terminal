package com.fufu.terminal.entity.enums;

/**
 * 脚本分组类型枚举
 */
public enum ScriptGroupType {
    PROJECT_DIMENSION("项目维度分组"),      // 项目维度分组，如MySQL管理、Redis管理
    FUNCTION_DIMENSION("功能维度分组");    // 功能维度分组，如环境初始化、监控运维

    private final String description;

    ScriptGroupType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}