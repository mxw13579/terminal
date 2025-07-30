package com.fufu.terminal.entity.enums;

/**
 * 交互模式枚举
 */
public enum InteractionMode {
    SILENT("静默执行"),               // 静默执行，无交互
    CONFIRMATION("需要确认"),         // 需要用户确认
    INPUT_REQUIRED("需要输入"),       // 需要用户输入
    CONDITIONAL("条件交互"),          // 条件交互（基于上下文决定是否交互）
    REALTIME_OUTPUT("实时输出");     // 实时输出（长时间运行的脚本）

    private final String description;

    InteractionMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}