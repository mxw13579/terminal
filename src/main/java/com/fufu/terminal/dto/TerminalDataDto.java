package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 简单的终端数据消息DTO
 * 
 * @author lizelin
 */
@Data
public class TerminalDataDto {
    @NotBlank
    private String data;
}