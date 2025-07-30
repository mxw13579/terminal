package com.fufu.terminal.entity.interaction;

import lombok.Data;

/**
 * 交互响应实体
 */
@Data
public class InteractionResponse {
    private String interactionId;
    private Object response;  // 用户的响应数据
    private long responseTime;  // 响应时间戳
}