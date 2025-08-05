package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * SFTP上传进度DTO
 * 用于传输文件上传的进度信息
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpUploadProgressDto {
    
    /**
     * 文件名
     * 正在上传的文件名称
     */
    private String filename;
    
    /**
     * 分片索引
     * 当前上传的分片索引，从0开始
     */
    private int chunkIndex;
    
    /**
     * 总分片数
     * 文件被分割的总分片数量
     */
    private int totalChunks;
    
    /**
     * 完成百分比
     * 文件上传完成的百分比（0-100）
     */
    private double percentComplete;
    
    /**
     * 上传状态
     * 当前上传状态，可能值为："uploading"（上传中）、"complete"（已完成）
     */
    private String status;
    
    /**
     * 构造函数（文件名、分片索引、总分片数）
     * 创建包含基本信息的上传进度DTO，自动计算完成百分比和状态
     * 
     * @param filename 文件名
     * @param chunkIndex 分片索引
     * @param totalChunks 总分片数
     */
    public SftpUploadProgressDto(String filename, int chunkIndex, int totalChunks) {
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.percentComplete = totalChunks > 0 ? (double) (chunkIndex + 1) / totalChunks * 100 : 0;
        this.status = chunkIndex + 1 == totalChunks ? "complete" : "uploading";
    }
    
    /**
     * 构造函数（完整参数）
     * 创建包含完整信息的上传进度DTO
     * 
     * @param filename 文件名
     * @param chunkIndex 分片索引
     * @param totalChunks 总分片数
     * @param percentComplete 完成百分比
     * @param status 上传状态
     */
    public SftpUploadProgressDto(String filename, int chunkIndex, int totalChunks, double percentComplete, String status) {
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.percentComplete = percentComplete;
        this.status = status;
    }
}