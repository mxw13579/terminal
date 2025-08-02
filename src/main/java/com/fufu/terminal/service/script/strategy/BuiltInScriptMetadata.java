package com.fufu.terminal.service.script.strategy;

import com.fufu.terminal.service.script.ScriptParameter;

import java.util.List;

/**
 * 内置脚本元数据接口
 * 用于定义内置脚本的基本信息和参数
 */
public interface BuiltInScriptMetadata {

    /**
     * 获取脚本唯一标识符
     * 
     * @return 脚本ID，通常与Spring Bean名称一致
     */
    String getScriptId();

    /**
     * 获取脚本显示名称
     * 
     * @return 脚本名称
     */
    String getName();

    /**
     * 获取脚本详细描述
     * 
     * @return 脚本描述
     */
    String getDescription();

    /**
     * 获取脚本类型
     * 
     * @return STATIC或DYNAMIC
     */
    BuiltInScriptType getType();

    /**
     * 获取脚本所需参数列表
     * 静态脚本返回空列表
     * 
     * @return 参数列表
     */
    List<ScriptParameter> getParameters();

    /**
     * 获取脚本标签
     * 用于分类和搜索
     * 
     * @return 标签数组
     */
    String[] getTags();

    /**
     * 获取脚本版本
     * 
     * @return 版本号
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * 获取脚本作者
     * 
     * @return 作者信息
     */
    default String getAuthor() {
        return "System";
    }
}