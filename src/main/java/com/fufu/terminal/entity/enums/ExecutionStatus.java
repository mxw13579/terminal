package com.fufu.terminal.entity.enums;

/**
 * 执行状态枚举
 */
public enum ExecutionStatus {
    PREPARING("准备中"),
    EXECUTING("执行中"),
    WAITING_INPUT("等待用户输入"),
    WAITING_CONFIRM("等待用户确认"),
    COMPLETED("已完成"),
    FAILED("执行失败"),
    SKIPPED("已跳过"),
    CANCELLED("已取消"),
    PAUSED("已暂停");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}