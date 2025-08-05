package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;

/**
 * 简单的终端调整大小消息DTO
 * 
 * @author lizelin
 */
@Data
public class TerminalResizeDto {
    
    /**
     * 终端列数
     * 必须大于等于1
     */
    @Min(1)
    private int cols;
    
    /**
     * 终端行数
     * 必须大于等于1
     */
    @Min(1)
    private int rows;
}