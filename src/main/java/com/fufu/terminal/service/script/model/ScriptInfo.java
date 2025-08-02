package com.fufu.terminal.service.script.model;

import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一脚本信息响应模型
 * 包含脚本的基本信息和类型标识，用于前端条件渲染
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptInfo {

    /**
     * 脚本唯一标识符
     */
    private String id;

    /**
     * 脚本显示名称
     */
    private String name;

    /**
     * 脚本详细描述
     */
    private String description;

    /**
     * 脚本来源类型
     * BUILT_IN_STATIC: 内置静态脚本 - 立即执行
     * BUILT_IN_DYNAMIC: 内置动态脚本 - 参数收集后执行
     * USER_DEFINED: 用户定义脚本 - 数据库驱动
     */
    private ScriptSourceType sourceType;

    /**
     * 脚本所需参数列表
     * 静态脚本为空列表
     */
    private List<ScriptParameter> parameters;

    /**
     * 是否需要参数收集
     * 便于前端快速判断是否显示参数表单
     */
    private boolean requiresParameters;

    /**
     * 脚本标签
     * 用于分类和搜索
     */
    private String[] tags;

    /**
     * 脚本版本
     */
    private String version;

    /**
     * 脚本作者
     */
    private String author;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 更新时间
     */
    private String updatedAt;

    /**
     * 扩展属性
     * 用于存储脚本特定的额外信息
     */
    private java.util.Map<String, Object> metadata;

    /**
     * 便捷方法：判断是否为内置脚本
     */
    public boolean isBuiltIn() {
        return sourceType != null && sourceType.isBuiltIn();
    }

    /**
     * 便捷方法：判断是否为静态脚本
     */
    public boolean isStaticScript() {
        return ScriptSourceType.BUILT_IN_STATIC == sourceType;
    }

    /**
     * 便捷方法：判断是否为动态脚本
     */
    public boolean isDynamicScript() {
        return ScriptSourceType.BUILT_IN_DYNAMIC == sourceType || 
               ScriptSourceType.USER_DEFINED == sourceType;
    }

    /**
     * 便捷方法：设置是否需要参数（基于参数列表）
     */
    public void updateRequiresParameters() {
        this.requiresParameters = parameters != null && !parameters.isEmpty();
    }
}