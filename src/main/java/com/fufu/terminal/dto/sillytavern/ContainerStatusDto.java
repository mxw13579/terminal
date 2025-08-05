package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 容器状态信息DTO
 * 提供SillyTavern容器状态的详细信息
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerStatusDto {
    
    /**
     * 容器是否正在运行
     * 标识容器当前是否处于运行状态，默认为false
     */
    private Boolean running = false;
    
    /**
     * 容器运行时间（秒）
     * 容器持续运行的时间（秒），如果容器未运行则为null
     */
    private Long uptimeSeconds;
    
    /**
     * 内存使用量（MB）
     * 容器当前使用的内存量，单位为兆字节
     */
    private Long memoryUsageMB;
    
    /**
     * CPU使用率百分比
     * 容器当前CPU使用率的百分比
     */
    private Double cpuUsagePercent;
    
    /**
     * 容器暴露的端口
     * 容器对外暴露的服务端口号
     */
    private Integer port;
    
    /**
     * 容器状态字符串
     * 从Docker获取的容器状态描述字符串
     */
    private String status;
    
    /**
     * 容器健康状态
     * 容器的健康检查状态
     */
    private String health;
    
    /**
     * 状态最后更新时间
     * 容器状态信息最后一次更新的时间
     */
    private LocalDateTime lastUpdated;
    
    /**
     * 容器ID
     * Docker分配的容器唯一标识符
     */
    private String containerId;
    
    /**
     * 容器名称
     * 容器的用户定义名称
     */
    private String containerName;
    
    /**
     * Docker镜像
     * 容器当前使用的Docker镜像名称
     */
    private String image;
    
    /**
     * 容器是否存在
     * 标识容器是否存在（但可能未运行），默认为false
     */
    private Boolean exists = false;
    
    /**
     * 错误信息
     * 获取容器状态时出现问题的错误消息
     */
    private String error;
    
    /**
     * 创建容器不存在的状态DTO
     * 用于表示容器不存在的情况
     * 
     * @return 容器不存在的状态DTO
     */
    public static ContainerStatusDto notExists() {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(false);
        status.setRunning(false);
        status.setStatus("Container not found");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
    
    /**
     * 创建Docker不可用的状态DTO
     * 用于表示Docker服务不可用的情况
     * 
     * @return Docker不可用的状态DTO
     */
    public static ContainerStatusDto dockerNotAvailable() {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(false);
        status.setRunning(false);
        status.setStatus("Docker not available");
        status.setError("Docker is not installed or not accessible. Please install Docker first.");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
    
    /**
     * 创建容器已停止的状态DTO
     * 用于表示容器已停止运行的情况
     * 
     * @param containerName 容器名称
     * @return 容器已停止的状态DTO
     */
    public static ContainerStatusDto stopped(String containerName) {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(true);
        status.setRunning(false);
        status.setContainerName(containerName);
        status.setStatus("Stopped");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
}