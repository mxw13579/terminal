package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for service control actions.
 * Used to start, stop, restart, upgrade, or delete containers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceActionDto {
    
    @NotBlank(message = "Action is required")
    private String action;  // "start", "stop", "restart", "upgrade", "delete"
    
    private String containerName = "sillytavern";
    
    private Boolean force = false;  // For force stop/delete operations
    
    private Boolean removeData = false;  // For delete operation - whether to remove data directory
    
    public static ServiceActionDto start() {
        return new ServiceActionDto("start", "sillytavern", false, false);
    }
    
    public static ServiceActionDto stop() {
        return new ServiceActionDto("stop", "sillytavern", false, false);
    }
    
    public static ServiceActionDto restart() {
        return new ServiceActionDto("restart", "sillytavern", false, false);
    }
    
    public static ServiceActionDto upgrade() {
        return new ServiceActionDto("upgrade", "sillytavern", false, false);
    }
    
    public static ServiceActionDto delete(boolean removeData) {
        return new ServiceActionDto("delete", "sillytavern", false, removeData);
    }
}