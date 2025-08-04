package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * DTO for SillyTavern deployment requests.
 * Contains all configuration needed to deploy a new SillyTavern container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentRequestDto {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    @Min(value = 1024, message = "Port must be at least 1024")
    @Max(value = 65535, message = "Port must be at most 65535")
    private Integer port = 8000;
    
    private String dataPath = "./sillytavern-data";
    
    private String dockerImage = "ghcr.io/sillytavern/sillytavern:latest";
    
    private String containerName = "sillytavern";
}