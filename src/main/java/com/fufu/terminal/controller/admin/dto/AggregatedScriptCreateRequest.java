package com.fufu.terminal.controller.admin.dto;

import com.fufu.terminal.entity.enums.AggregateScriptType;
import lombok.Data;

import java.util.List;

/**
 * DTO for creating a new Aggregated Script from the script builder.
 */
@Data
public class AggregatedScriptCreateRequest {

    private String name;
    private String description;
    private AggregateScriptType type;
    private List<StepDto> steps;

    @Data
    public static class StepDto {
        private Long atomicScriptId;
        private int executionOrder;
        private String conditionExpression;
        private String variableMapping; // JSON string
    }
}
