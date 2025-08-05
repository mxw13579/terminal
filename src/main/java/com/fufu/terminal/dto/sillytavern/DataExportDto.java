package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 数据导出操作DTO
 * 提供导出数据的下载URL和元数据信息
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportDto {
    
    /**
     * 容器名称
     * 数据导出对应的容器名称
     */
    private String containerName;
    
    /**
     * 下载URL
     * 用于下载导出数据的URL链接
     */
    private String downloadUrl;
    
    /**
     * 文件名
     * 导出数据的文件名
     */
    private String filename;
    
    /**
     * 兼容性文件名属性
     * 文件名的替代属性名称（用于兼容性）
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     * 导出文件的字节大小
     */
    private Long sizeBytes;
    
    /**
     * 压缩后文件大小（字节）
     * 压缩或最终文件的字节大小
     */
    private Long compressedSize;
    
    /**
     * 导出路径
     * 服务器上的导出路径
     */
    private String exportPath;
    
    /**
     * 过期时间
     * 导出数据过期并将被自动删除的时间
     */
    private LocalDateTime expiresAt;
    
    /**
     * 创建时间
     * 导出创建的时间戳
     */
    private LocalDateTime createdAt;
    
    /**
     * 导出进度
     * 如果仍在进行中，显示导出进度（0-100），默认为100
     */
    private Integer progress = 100;
    
    /**
     * 是否完成
     * 标识导出是否已完成，默认为true
     */
    private Boolean completed = true;
    
    /**
     * 错误信息
     * 如果导出失败，显示错误消息
     */
    private String error;
    
    /**
     * 格式化文件大小
     * 人类可读的文件大小格式
     */
    private String formattedSize;
    
    /**
     * 获取兼容性文件名
     * 为了兼容性提供的文件名getter方法
     * 
     * @return 文件名
     */
    public String getFileName() {
        return fileName != null ? fileName : filename;
    }
    
    /**
     * 设置兼容性文件名
     * 为了兼容性提供的文件名setter方法
     * 
     * @param fileName 文件名
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
        if (this.filename == null) {
            this.filename = fileName;
        }
    }
    
    /**
     * 创建进行中的导出DTO
     * 创建一个表示导出进行中的DTO对象
     * 
     * @param progress 当前进度（0-100）
     * @param message 状态消息
     * @return 进行中的导出DTO
     */
    public static DataExportDto inProgress(int progress, String message) {
        DataExportDto dto = new DataExportDto();
        dto.setProgress(progress);
        dto.setCompleted(false);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
    
    /**
     * 创建错误状态的导出DTO
     * 创建一个表示导出失败的DTO对象
     * 
     * @param error 错误消息
     * @return 错误状态的导出DTO
     */
    public static DataExportDto error(String error) {
        DataExportDto dto = new DataExportDto();
        dto.setError(error);
        dto.setCompleted(true);
        dto.setProgress(0);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
}