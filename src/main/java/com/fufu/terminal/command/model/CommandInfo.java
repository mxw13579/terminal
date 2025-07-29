package com.fufu.terminal.command.model;

import com.fufu.terminal.command.Command;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令信息封装类
 * 用于可视化脚本编辑器中的命令展示和管理
 * @author lizelin
 */
@Data
@AllArgsConstructor
public class CommandInfo {
    /**
     * 命令唯一标识
     */
    private String id;
    
    /**
     * 命令实例
     */
    private Command command;
    
    /**
     * 命令分类（前置处理、环境检查、安装增强）
     */
    private String category;
    
    /**
     * 命令显示名称
     */
    private String name;
    
    /**
     * 命令描述
     */
    private String description;
    
    /**
     * 依赖的其他命令ID列表
     */
    private List<String> dependencies;
    
    /**
     * 命令图标样式类
     */
    private String icon;
    
    /**
     * 是否为必须执行的命令
     */
    private boolean required;
    
    public CommandInfo() {
        this.dependencies = new ArrayList<>();
    }
    
    public CommandInfo(String id, Command command, String category, String name, String description) {
        this.id = id;
        this.command = command;
        this.category = category;
        this.name = name;
        this.description = description;
        this.dependencies = new ArrayList<>();
        this.required = false;
    }
}