package com.fufu.terminal.command.util;

import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * SSH连接工具类
 * @author lizelin
 */
@Slf4j
public class SshConnectionUtil {
    
    // SSH configuration constants
    private static final String SSH_CONFIG_STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";
    private static final String SSH_CONFIG_NO = "no";
    private static final String SSH_CHANNEL_SHELL = "shell";
    private static final String SSH_PTY_TYPE_XTERM = "xterm";
    
    /**
     * 根据配置创建SSH连接
     */
    public static SshConnection createConnection(SshConnectionConfig config) throws Exception {
        if (!config.isValid()) {
            throw new IllegalArgumentException("SSH配置无效");
        }
        
        log.info("创建SSH连接: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
        
        try {
            // 创建JSch实例
            JSch jsch = new JSch();
            
            // 创建会话
            Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());
            session.setConfig(SSH_CONFIG_STRICT_HOST_KEY_CHECKING, SSH_CONFIG_NO);
            session.connect(config.getConnectTimeout());
            
            // 创建Shell通道
            ChannelShell channelShell = (ChannelShell) session.openChannel(SSH_CHANNEL_SHELL);
            channelShell.setPtyType(SSH_PTY_TYPE_XTERM);
            
            // 获取输入输出流
            InputStream inputStream = channelShell.getInputStream();
            OutputStream outputStream = channelShell.getOutputStream();
            
            // 连接通道
            channelShell.connect(config.getChannelTimeout());
            
            // 创建SshConnection对象
            SshConnection connection = new SshConnection(jsch, session, channelShell, inputStream, outputStream);
            
            log.info("SSH连接创建成功");
            return connection;
            
        } catch (Exception e) {
            log.error("创建SSH连接失败: {}", e.getMessage());
            throw new Exception("SSH连接失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 安全关闭SSH连接
     */
    public static void closeConnection(SshConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
                log.info("SSH连接已关闭");
            } catch (Exception e) {
                log.warn("关闭SSH连接时发生异常", e);
            }
        }
    }
    
    /**
     * 测试SSH连接
     */
    public static boolean testConnection(SshConnectionConfig config) {
        try {
            SshConnection connection = createConnection(config);
            closeConnection(connection);
            return true;
        } catch (Exception e) {
            log.error("SSH连接测试失败", e);
            return false;
        }
    }
}