package com.fufu.terminal.entity.context;

import lombok.Data;

/**
 * 待确认变量实体
 */
@Data
public class PendingConfirmation {
    private String variableName;
    private Object suggestedValue;  // 建议值
    private String reason;  // 建议原因
    private boolean confirmed = false;  // 是否已确认
    private Object userChoice;  // 用户最终选择
    private String conditionExpression;  // 触发条件
}