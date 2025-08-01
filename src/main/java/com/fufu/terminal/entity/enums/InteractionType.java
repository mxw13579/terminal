package com.fufu.terminal.entity.enums;

/**
 * 交互类型枚举
 */
public enum InteractionType {
    CONFIRMATION("确认操作"),             // 通用确认操作
    CONFIRM_YES_NO("是否确认"),           // 是/否 确认
    CONFIRM_RECOMMENDATION("建议确认"),    // 基于检测结果的建议确认
    INPUT_TEXT("文本输入"),               // 单个文本输入
    INPUT_PASSWORD("密码输入"),           // 密码输入（隐藏显示）
    INPUT_FORM("表单输入"),               // 多字段表单输入
    SELECT_OPTION("选项选择"),            // 单选或多选
    FILE_UPLOAD("文件上传");              // 配置文件上传

    private final String description;

    InteractionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}