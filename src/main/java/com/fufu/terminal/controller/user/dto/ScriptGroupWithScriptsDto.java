package com.fufu.terminal.controller.user.dto;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.entity.enums.ScriptGroupType;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for representing a ScriptGroup with its contained AggregatedScripts.
 */
@Data
public class ScriptGroupWithScriptsDto {

    private Long id;
    private String name;
    private String description;
    private String icon;
    private ScriptGroupType type;
    private List<AggregatedScriptDto> aggregatedScripts;

    public ScriptGroupWithScriptsDto(ScriptGroup scriptGroup) {
        this.id = scriptGroup.getId();
        this.name = scriptGroup.getName();
        this.description = scriptGroup.getDescription();
        this.icon = scriptGroup.getIcon();
        this.type = scriptGroup.getType();
        this.aggregatedScripts = scriptGroup.getAggregateRelations().stream()
                .map(relation -> new AggregatedScriptDto(relation.getAggregatedScript()))
                .collect(Collectors.toList());
    }

    /**
     * A simplified DTO for AggregatedScript to be nested within the group.
     */
    @Data
    public static class AggregatedScriptDto {
        private Long id;
        private String name;
        private String description;

        public AggregatedScriptDto(AggregatedScript aggregatedScript) {
            this.id = aggregatedScript.getId();
            this.name = aggregatedScript.getName();
            this.description = aggregatedScript.getDescription();
        }
    }
}
