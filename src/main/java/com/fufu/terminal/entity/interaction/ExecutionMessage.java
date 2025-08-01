package com.fufu.terminal.entity.interaction;

import com.fufu.terminal.entity.enums.ExecutionStatus;
import com.fufu.terminal.entity.enums.MessageType;
import lombok.Data;

/**
 * 执行消息实体
 */
@Data
public class ExecutionMessage {
    private String stepId;
    private String stepName;
    private ExecutionStatus status;
    private MessageType messageType;
    private String message;
    private String output;  // 命令输出
    private Integer progress;  // 进度百分比 0-100
    private Integer estimatedTime;  // 预估剩余时间（秒）
    private long timestamp;
    private InteractionRequest interaction;  // 如果需要交互

//    // 常用的静态MessageType引用，方便使用
//    public static class MessageType {
//        public static final com.fufu.terminal.entity.enums.MessageType INFO = com.fufu.terminal.entity.enums.MessageType.INFO;
//        public static final com.fufu.terminal.entity.enums.MessageType SUCCESS = com.fufu.terminal.entity.enums.MessageType.SUCCESS;
//        public static final com.fufu.terminal.entity.enums.MessageType WARNING = com.fufu.terminal.entity.enums.MessageType.WARNING;
//        public static final com.fufu.terminal.entity.enums.MessageType ERROR = com.fufu.terminal.entity.enums.MessageType.ERROR;
//        public static final com.fufu.terminal.entity.enums.MessageType PROGRESS = com.fufu.terminal.entity.enums.MessageType.PROGRESS;
//        public static final com.fufu.terminal.entity.enums.MessageType INTERACTION_REQUEST = com.fufu.terminal.entity.enums.MessageType.INTERACTION_REQUEST;
//        public static final com.fufu.terminal.entity.enums.MessageType STEP_START = com.fufu.terminal.entity.enums.MessageType.STEP_START;
//        public static final com.fufu.terminal.entity.enums.MessageType STEP_COMPLETE = com.fufu.terminal.entity.enums.MessageType.STEP_COMPLETE;
//        public static final com.fufu.terminal.entity.enums.MessageType EXECUTION_START = com.fufu.terminal.entity.enums.MessageType.EXECUTION_START;
//        public static final com.fufu.terminal.entity.enums.MessageType EXECUTION_COMPLETE = com.fufu.terminal.entity.enums.MessageType.EXECUTION_COMPLETE;
//        public static final com.fufu.terminal.entity.enums.MessageType DEBUG = com.fufu.terminal.entity.enums.MessageType.DEBUG;
//    }

    /**
     * 设置交互请求
     */
    public void setInteractionRequest(InteractionRequest interactionRequest) {
        this.interaction = interactionRequest;
    }
}
