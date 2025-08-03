package com.fufu.terminal.controller.dto;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.model.ScriptParameter;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * DTO for script information
 * Provides comprehensive script metadata for UI display
 */
@Data
@Builder
public class ScriptInfo {
    private String id;
    private String name;
    private String description;
    private String category;
    private ScriptType type;
    private String version;
    private List<ScriptParameter> parameters;
    private Set<String> requiredVariables;
    private Set<String> outputVariables;
    private Set<String> tags;
    private Integer estimatedExecutionTime;
    private boolean requiresElevatedPrivileges;
    private Set<String> supportedOperatingSystems;
}