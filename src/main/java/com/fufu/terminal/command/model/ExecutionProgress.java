package com.fufu.terminal.command.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本执行进度信息
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionProgress {
    /**
     * 当前执行步骤
     */
    private int current;
    
    /**
     * 总步骤数
     */
    private int total;
    
    /**
     * 当前执行的命令名称
     */
    private String commandName;
    
    /**
     * 当前命令ID
     */
    private String commandId;
    
    /**
     * 执行状态：executing(执行中)、completed(完成)、failed(失败)、skipped(跳过)
     */
    private String status;
    
    /**
     * 状态消息
     */
    private String message;
    
    /**
     * 执行开始时间
     */
    private long startTime;
    
    /**
     * 执行结束时间
     */
    private long endTime;
    
    /**
     * 计算执行进度百分比
     */
    public int getPercentage() {
        return total > 0 ? Math.round((float) current / total * 100) : 0;
    }
    
    /**
     * 计算执行耗时（毫秒）
     */
    public long getDuration() {
        return endTime > startTime ? endTime - startTime : 0;
    }
}