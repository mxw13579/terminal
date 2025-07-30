package com.fufu.terminal.entity.interaction;

import com.fufu.terminal.entity.enums.ExecutionStatus;
import lombok.Data;

/**
 * 执行消息实体
 */
@Data
public class ExecutionMessage {
    private String stepId;
    private String stepName;
    private ExecutionStatus status;
    private String message;
    private String output;  // 命令输出
    private Integer progress;  // 进度百分比 0-100
    private Integer estimatedTime;  // 预估剩余时间（秒）
    private long timestamp;
    private InteractionRequest interaction;  // 如果需要交互
}