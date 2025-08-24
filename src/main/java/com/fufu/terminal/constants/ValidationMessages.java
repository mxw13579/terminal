package com.fufu.terminal.constants;

/**
 * 验证消息常量类
 * 统一管理所有DTO验证注解的错误消息，提升国际化友好性和维护性
 * 
 * @author lizelin
 * @since 1.0
 */
public final class ValidationMessages {
    
    // 防止实例化
    private ValidationMessages() {
        throw new AssertionError("常量类不允许实例化");
    }
    
    // 通用验证消息
    public static final String NOT_BLANK = "不能为空";
    public static final String NOT_NULL = "不能为空";
    public static final String MIN_VALUE = "必须大于等于1";
    public static final String MAX_VALUE = "不能超过最大值";
    
    // 字段特定验证消息
    public static final String STEP_ID_NOT_BLANK = "步骤ID不能为空";
    public static final String STEP_NAME_NOT_BLANK = "步骤名称不能为空";
    public static final String STEP_STATUS_NOT_BLANK = "步骤状态不能为空";
    public static final String SESSION_ID_NOT_BLANK = "会话ID不能为空";
    public static final String DEPLOYMENT_MODE_NOT_BLANK = "部署模式不能为空";
    public static final String USER_OPERATION_NOT_BLANK = "用户操作不能为空";
    public static final String CONFIRMATION_MESSAGE_NOT_BLANK = "确认消息不能为空";
    public static final String OPTION_KEY_NOT_BLANK = "选项键不能为空";
    public static final String OPTION_LABEL_NOT_BLANK = "选项标签不能为空";
    public static final String STEP_LIST_NOT_NULL = "步骤列表不能为空";
    public static final String OPERATION_TYPE_NOT_BLANK = "操作类型不能为空";
    public static final String DIRECTORY_PATH_NOT_BLANK = "目录路径不能为空";
    public static final String ENCRYPTED_DATA_NOT_BLANK = "加密凭据数据不能为空";
    
    /**
     * 生成带字段名的通用验证消息
     * 
     * @param fieldName 字段名称
     * @return 完整的验证消息
     */
    public static String notBlank(String fieldName) {
        return fieldName + NOT_BLANK;
    }
    
    /**
     * 生成带字段名的空值验证消息
     * 
     * @param fieldName 字段名称
     * @return 完整的验证消息
     */
    public static String notNull(String fieldName) {
        return fieldName + NOT_NULL;
    }
    
    /**
     * 生成带最小值的验证消息
     * 
     * @param fieldName 字段名称
     * @param minValue 最小值
     * @return 完整的验证消息
     */
    public static String minValue(String fieldName, int minValue) {
        return fieldName + "必须大于等于" + minValue;
    }
}