package com.fufu.terminal.entity.enums;

/**
 * 变量作用域枚举
 */
public enum VariableScope {
    GLOBAL("全局变量"),      // 全局变量，整个聚合脚本执行期间有效
    SESSION("会话变量"),     // 会话变量，用户会话期间有效
    LOCAL("局部变量");       // 局部变量，单个原子脚本内有效

    private final String description;

    VariableScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}