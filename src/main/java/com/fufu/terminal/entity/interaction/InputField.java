package com.fufu.terminal.entity.interaction;

import lombok.Data;

/**
 * 输入字段定义
 */
@Data
public class InputField {
    private String name;
    private String type;  // text, password, number, email, select等
    private String label;
    private Object defaultValue;
    private boolean required = false;
    private String validation;  // 验证规则，如正则表达式
    private String description;
    private String placeholder;
}