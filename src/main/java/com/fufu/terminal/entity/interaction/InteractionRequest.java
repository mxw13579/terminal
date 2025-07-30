package com.fufu.terminal.entity.interaction;

import com.fufu.terminal.entity.enums.InteractionType;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 交互请求实体
 */
@Data
public class InteractionRequest {
    private String interactionId;
    private InteractionType type;
    private String prompt;
    private List<String> options;  // 用于选择类型的交互
    private Map<String, InputField> inputFields;  // 用于表单类型的交互
    private Object defaultValue;
    private boolean required = true;
    private String condition;  // 触发条件表达式
}