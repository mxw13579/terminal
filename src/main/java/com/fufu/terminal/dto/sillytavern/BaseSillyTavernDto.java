package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SillyTavern相关DTO的基础抽象类
 * 包含所有SillyTavern DTO的公共字段和注解
 * 使用SuperBuilder支持继承链中的Builder模式
 * 
 * @author lizelin
 * @since 1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseSillyTavernDto {
    
    /**
     * 时间戳，记录DTO创建或最后更新时间
     */
    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}