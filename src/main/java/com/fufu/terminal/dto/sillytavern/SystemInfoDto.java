package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 系统信息和需求验证DTO
 * 用于检查系统是否能够运行SillyTavern容器
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoDto {
    
    /**
     * 操作系统类型
     * 系统运行的操作系统类型（如Linux、Windows、macOS等）
     */
    private String osType;
    
    /**
     * Docker是否已安装且可访问
     * 标识系统是否已安装Docker并可正常使用，默认为false
     */
    private Boolean dockerInstalled = false;
    
    /**
     * Docker守护进程是否正在运行
     * 标识Docker服务是否已启动并运行，默认为false
     */
    private Boolean dockerRunning = false;
    
    /**
     * Docker版本信息
     * 如果Docker可用，则显示Docker的版本号
     */
    private String dockerVersion;
    
    /**
     * 当前用户是否具有sudo权限
     * 标识当前用户是否具有管理员权限，默认为false
     */
    private Boolean hasRootAccess = false;
    
    /**
     * 是否有足够的磁盘空间
     * 标识系统是否有足够的空间用于部署容器，默认为false
     */
    private Boolean sufficientDiskSpace = false;
    
    /**
     * 是否有网络连接
     * 标识系统是否能访问互联网，默认为true
     */
    private Boolean hasInternetAccess = true;
    
    /**
     * 容器可用端口范围
     * 可用于容器端口映射的端口范围字符串
     */
    private String availablePortRange;
    
    /**
     * 可用磁盘空间（MB）
     * 系统可用磁盘空间的大小，单位为兆字节
     */
    private Long availableDiskSpaceMB;
    
    /**
     * 系统总内存（MB）
     * 系统总内存大小，单位为兆字节
     */
    private Long totalMemoryMB;
    
    /**
     * 可用内存（MB）
     * 系统可用内存大小，单位为兆字节
     */
    private Long availableMemoryMB;
    
    /**
     * CPU核心数
     * 系统的CPU核心数量
     */
    private Integer cpuCores;
    
    /**
     * 系统是否满足最低要求
     * 标识系统是否满足运行SillyTavern的最低要求，默认为false
     */
    private Boolean meetsRequirements = false;
    
    /**
     * 需求检查结果列表
     * 包含各项系统需求检查的结果信息
     */
    private java.util.List<String> requirementChecks;
    
    /**
     * 系统配置警告信息
     * 包含系统配置相关的警告信息列表
     */
    private java.util.List<String> warnings;
    
    /**
     * 端口可用性检查结果
     * 端口号到可用性状态的映射表
     */
    private java.util.Map<Integer, Boolean> portAvailability;
    
    /**
     * 创建不满足系统需求的系统信息DTO
     * 用于当系统不满足运行要求时返回相应的检查结果
     * 
     * @param checks 需求检查结果列表
     * @return 不满足需求的系统信息DTO
     */
    public static SystemInfoDto requirementsNotMet(java.util.List<String> checks) {
        SystemInfoDto info = new SystemInfoDto();
        info.setMeetsRequirements(false);
        info.setRequirementChecks(checks);
        return info;
    }
}