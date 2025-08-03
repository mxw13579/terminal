package com.fufu.terminal.controller.dto;

import com.fufu.terminal.entity.enums.ScriptType;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * DTO for script type information
 * Provides detailed information about each script type and its capabilities
 */
@Data
@Builder
public class ScriptTypeInfo {
    private ScriptType type;
    private String name;
    private String description;
    private Set<ScriptType.Feature> supportedFeatures;
    private boolean requiresParameters;
    private boolean supportsInteraction;
    private boolean isBuiltIn;
}