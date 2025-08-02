package com.fufu.terminal.entity.interaction;

import lombok.Data;

/**
 * 交互响应实体
 * 
 * 此实体遵循数据传输对象(DTO)的设计原则，只包含数据字段，
 * 不包含业务逻辑。JSON序列化逻辑由服务层处理，保持架构清洁。
 */
@Data
public class InteractionResponse {
    private String interactionId;
    private Object response;  // 用户的响应数据
    private long responseTime;  // 响应时间戳
}