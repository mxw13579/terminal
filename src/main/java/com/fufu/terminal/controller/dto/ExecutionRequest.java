package com.fufu.terminal.controller.dto;

import com.fufu.terminal.command.model.SshConnectionConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for script execution
 * Supports all script types with unified parameter handling
 */
@Data
@Builder
public class ExecutionRequest {

    @Valid
    @NotNull(message = "SSH connection configuration is required")
    private SshConnectionConfig sshConfig;

    private Map<String, Object> parameters;
    private Map<String, Object> variables;
    private boolean autoOptimize;
    private Integer timeoutSeconds;

    public boolean isValid() {
        return sshConfig != null && sshConfig.isValid();
    }
}
