package com.fufu.terminal.constants;

/**
 * SillyTavern相关常量定义
 * 统一管理Docker容器、部署路径、命令等常量，避免重复定义
 * 
 * @author lizelin
 * @since 1.0
 */
public final class SillyTavernConstants {
    
    // 防止实例化
    private SillyTavernConstants() {
        throw new AssertionError("常量类不允许实例化");
    }
    
    /**
     * 默认容器名称
     */
    public static final String DEFAULT_CONTAINER_NAME = "sillytavern";
    
    /**
     * 默认部署路径
     */
    public static final String DEPLOYMENT_PATH = "/data/docker/sillytavern";
    
    /**
     * Docker版本检查命令
     */
    public static final String DOCKER_VERSION_COMMAND = "docker --version";
    
    /**
     * 默认端口
     */
    public static final String DEFAULT_PORT = "8000";
    
    /**
     * 配置文件名
     */
    public static final String CONFIG_FILE_NAME = "config.yaml";
    
    /**
     * Docker Compose文件名
     */
    public static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
}