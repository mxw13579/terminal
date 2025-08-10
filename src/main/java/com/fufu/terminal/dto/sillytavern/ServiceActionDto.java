package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 服务控制操作DTO
 * 用于启动、停止、重启、版本切换或删除容器
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceActionDto {
    
    /**
     * 操作类型
     * 服务控制操作类型，可选值："start", "stop", "restart", "switch-version", "delete"
     */
    @NotBlank(message = "操作类型不能为空")
    private String action;  // "start", "stop", "restart", "switch-version", "delete"
    
    /**
     * 容器名称
     * 要操作的目标容器名称，默认为"sillytavern"
     */
    private String containerName = "sillytavern";
    
    /**
     * 目标版本号
     * 用于版本切换操作，指定要切换到的版本
     */
    private String targetVersion;
    
    /**
     * 是否强制操作
     * 用于强制停止或删除操作，默认为false
     */
    private Boolean force = false;  // For force stop/delete operations
    
    /**
     * 是否删除数据
     * 对于删除操作，标识是否同时删除数据目录，默认为false
     */
    private Boolean removeData = false;  // For delete operation - whether to remove data directory
    
    /**
     * 创建启动服务的操作DTO
     * 创建一个用于启动服务的操作DTO
     * 
     * @return 启动服务的操作DTO
     */
    public static ServiceActionDto start() {
        return new ServiceActionDto("start", "sillytavern", null, false, false);
    }
    
    /**
     * 创建停止服务的操作DTO
     * 创建一个用于停止服务的操作DTO
     * 
     * @return 停止服务的操作DTO
     */
    public static ServiceActionDto stop() {
        return new ServiceActionDto("stop", "sillytavern", null, false, false);
    }
    
    /**
     * 创建重启服务的操作DTO
     * 创建一个用于重启服务的操作DTO
     * 
     * @return 重启服务的操作DTO
     */
    public static ServiceActionDto restart() {
        return new ServiceActionDto("restart", "sillytavern", null, false, false);
    }
    
    /**
     * 创建版本切换服务的操作DTO
     * 创建一个用于版本切换服务的操作DTO
     * 
     * @param targetVersion 目标版本
     * @return 版本切换服务的操作DTO
     */
    public static ServiceActionDto switchVersion(String targetVersion) {
        return new ServiceActionDto("switch-version", "sillytavern", targetVersion, false, false);
    }
    
    /**
     * 创建删除服务的操作DTO
     * 创建一个用于删除服务的操作DTO
     * 
     * @param removeData 是否删除数据
     * @return 删除服务的操作DTO
     */
    public static ServiceActionDto delete(boolean removeData) {
        return new ServiceActionDto("delete", "sillytavern", null, false, removeData);
    }
}