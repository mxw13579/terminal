package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 简单的终端数据消息DTO
 * 
 * @author lizelin
 */
@Data
public class TerminalDataDto {
    
    /**
     * 终端数据内容
     * 允许空字符串但不允许null值
     */
    @NotNull  // 改为NotNull，允许空字符串但不允许null
    private String data;
}