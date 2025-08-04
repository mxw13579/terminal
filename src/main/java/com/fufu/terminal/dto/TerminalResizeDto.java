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
    @Min(1)
    private int cols;
    
    @Min(1)
    private int rows;
}