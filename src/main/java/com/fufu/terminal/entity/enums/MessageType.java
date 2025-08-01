package com.fufu.terminal.entity.enums;

/**
 * 消息类型枚举
 */
public enum MessageType {
    INFO,               // 信息消息
    SUCCESS,            // 成功消息
    WARNING,            // 警告消息
    ERROR,              // 错误消息
    PROGRESS,           // 进度消息
    INTERACTION_REQUEST, // 交互请求
    STEP_START,         // 步骤开始
    STEP_COMPLETE,      // 步骤完成
    EXECUTION_START,    // 执行开始
    EXECUTION_COMPLETE, // 执行完成
    DEBUG               // 调试消息
}