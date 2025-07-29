package com.fufu.terminal.command.impl.enhancement;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.impl.environment.CheckDockerCommand;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigureDockerMirrorCommand 真机集成测试
 */
@Slf4j
class ConfigureDockerMirrorCommandTest {

    /**
     * 使用真实服务器连接进行测试
     */
    @Test
    void testExecute_WithRealServerConnection() throws Exception {
        // 从环境变量或配置读取真实服务器信息
        String host = "156.233.233.40";
        int port = 22;
        String user = "root";
        String password = "vxwpYXDJ6295";

        // 检查参数
        assertNotNull(host, "请设置SSH_HOST环境变量");
        assertNotNull(user, "请设置SSH_USER环境变量");
        assertNotNull(password, "请设置SSH_PASSWORD环境变量");

        // 创建JSch及相关连接
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30000);

        ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
        channelShell.setPtyType("xterm");
        InputStream inputStream = channelShell.getInputStream();
        OutputStream outputStream = channelShell.getOutputStream();
        channelShell.connect(3000);

        log.info("SSH连接已建立: {}@{}:{}", user, host, port);

        SshConnection connection = new SshConnection(jsch, session, channelShell, inputStream, outputStream);
        CommandContext realContext = new CommandContext(connection, null);

        // 设置测试环境的条件
        realContext.setProperty(DetectLocationCommand.USE_CHINA_MIRROR_KEY, true);
        realContext.setProperty(CheckDockerCommand.DOCKER_INSTALLED_KEY, true);

        ConfigureDockerMirrorCommand command = new ConfigureDockerMirrorCommand();

        try {
            // 首先检查是否应该执行
            boolean shouldExecute = command.shouldExecute(realContext);
            log.info("是否应该执行Docker镜像配置: {}", shouldExecute);

            if (shouldExecute) {
                // 执行命令
                command.execute(realContext);

                // 校验结果
                Boolean configured = (Boolean) realContext.getProperty(ConfigureDockerMirrorCommand.DOCKER_MIRROR_CONFIGURED_KEY);

                log.info("Docker镜像加速器配置结果: {}", configured);
                assertNotNull(configured, "配置结果不应为null");
            } else {
                log.info("跳过Docker镜像配置（条件不满足）");
            }

        } finally {
            // 关闭连接，防止资源泄漏
            connection.disconnect();
        }
    }
}
