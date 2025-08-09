package com.fufu.terminal.config;

/**
 * STOMP 目标地址配置常量。
 * <p>
 * 定义了标准化的 STOMP 消息路由地址，确保整个应用程序使用一致的消息路由模式。
 * 所有用户相关的消息都应使用 {@code /user/queue/*} 模式以实现正确的用户会话隔离。
 * </p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 发送终端数据到特定用户
 * messagingTemplate.convertAndSendToUser(
 *     sessionId, 
 *     StompDestinationConfig.USER_TERMINAL, 
 *     terminalData
 * );
 * 
 * // 前端订阅示例
 * stompClient.subscribe('/user/queue/terminal', callback);
 * }</pre>
 * 
 * <h3>架构说明：</h3>
 * <ul>
 *     <li><strong>应用前缀</strong>: {@code /app} - 客户端发送消息的前缀</li>
 *     <li><strong>用户队列</strong>: {@code /user/queue/*} - 服务端发送给特定用户的消息</li>
 *     <li><strong>会话隔离</strong>: Spring STOMP 自动处理用户会话隔离</li>
 *     <li><strong>安全性</strong>: 每个用户只能接收自己的消息</li>
 * </ul>
 * 
 * @author lizelin
 * @since v2.0
 * @see org.springframework.messaging.simp.SimpMessagingTemplate#convertAndSendToUser
 */
public final class StompDestinationConfig {
    
    // 防止实例化
    private StompDestinationConfig() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * 应用程序消息前缀 - 客户端发送消息时使用
     * <p>示例: 客户端发送到 "/app/terminal/input"</p>
     */
    public static final String APP_PREFIX = "/app";
    
    /**
     * 终端相关消息队列
     * <p>
     * 用于终端输入/输出数据传输。
     * </p>
     * <ul>
     *     <li>终端输出数据</li>
     *     <li>终端状态变更通知</li>
     *     <li>终端连接成功/失败消息</li>
     * </ul>
     */
    public static final String USER_TERMINAL = "/queue/terminal";
    
    /**
     * SFTP 文件传输消息队列
     * <p>
     * 用于文件传输相关的消息通知。
     * </p>
     * <ul>
     *     <li>文件上传/下载进度</li>
     *     <li>文件操作结果通知</li>
     *     <li>目录列表响应</li>
     * </ul>
     */
    public static final String USER_SFTP = "/queue/sftp";
    
    /**
     * 系统监控消息队列
     * <p>
     * 用于系统资源监控数据推送。
     * </p>
     * <ul>
     *     <li>CPU、内存使用率</li>
     *     <li>磁盘使用情况</li>
     *     <li>网络流量统计</li>
     * </ul>
     */
    public static final String USER_MONITOR = "/queue/monitor";
    
    /**
     * 错误消息队列
     * <p>
     * 用于向用户推送错误信息和异常通知。
     * </p>
     * <ul>
     *     <li>SSH 连接错误</li>
     *     <li>命令执行失败</li>
     *     <li>系统异常通知</li>
     * </ul>
     */
    public static final String USER_ERRORS = "/queue/errors";
    
    /**
     * SillyTavern 部署相关消息队列
     * <p>
     * 用于 SillyTavern 容器部署和管理的消息通知。
     * </p>
     */
    public static final String USER_SILLYTAVERN = "/queue/sillytavern";
    
    // =================== 应用端点映射 ===================
    
    /**
     * 终端输入命令映射路径
     */
    public static final String APP_TERMINAL_INPUT = APP_PREFIX + "/terminal/input";
    
    /**
     * 终端窗口大小调整映射路径  
     */
    public static final String APP_TERMINAL_RESIZE = APP_PREFIX + "/terminal/resize";
    
    /**
     * SFTP 文件列表请求映射路径
     */
    public static final String APP_SFTP_LIST = APP_PREFIX + "/sftp/list";
    
    /**
     * SFTP 文件上传映射路径
     */
    public static final String APP_SFTP_UPLOAD = APP_PREFIX + "/sftp/upload";
    
    /**
     * SFTP 文件下载映射路径
     */
    public static final String APP_SFTP_DOWNLOAD = APP_PREFIX + "/sftp/download";
    
    /**
     * 系统监控启动映射路径
     */
    public static final String APP_MONITOR_START = APP_PREFIX + "/monitor/start";
    
    /**
     * 系统监控停止映射路径
     */
    public static final String APP_MONITOR_STOP = APP_PREFIX + "/monitor/stop";
    
    // =================== 验证方法 ===================
    
    /**
     * 验证目标地址是否为用户队列格式
     * 
     * @param destination 目标地址
     * @return 如果是有效的用户队列地址返回 true
     */
    public static boolean isUserQueue(String destination) {
        if (destination == null) {
            return false;
        }
        return destination.startsWith("/queue/");
    }
    
    /**
     * 验证应用端点映射是否有效
     * 
     * @param destination 应用端点
     * @return 如果是有效的应用端点返回 true
     */
    public static boolean isAppDestination(String destination) {
        if (destination == null) {
            return false;
        }
        return destination.startsWith(APP_PREFIX + "/");
    }
}