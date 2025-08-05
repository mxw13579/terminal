package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 终端数据消息DTO
 * 用于在WebSocket连接中传输终端的输入输出数据
 * 
 * @author lizelin
 */
@Data
public class TerminalDataDto {
    
    /**
     * 终端数据内容
     * 终端输入或输出的数据内容，允许空字符串但不允许null值
     */
    @NotNull(message = "终端数据不能为null")
    private String data;
    
    /**
     * 构造函数
     * 创建包含终端数据的DTO
     * 
     * @param data 终端数据内容
     */
    public TerminalDataDto(String data) {
        this.data = data;
    }
    
    /**
     * 默认构造函数
     * 创建空的终端数据DTO
     */
    public TerminalDataDto() {
        // 默认构造器
    }
}