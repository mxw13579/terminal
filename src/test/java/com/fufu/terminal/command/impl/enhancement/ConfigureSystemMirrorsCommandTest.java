package com.fufu.terminal.command.impl.enhancement;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
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
 * ConfigureSystemMirrorsCommand 单元测试
 * 只保留真机测试
 */
@Slf4j
class ConfigureSystemMirrorsCommandTest {

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

        // 设置测试环境的操作系统信息和位置信息
        OsInfo osInfo = new OsInfo();
        osInfo.setId("ubuntu"); // 假设是Ubuntu系统
        osInfo.setSystemType(SystemType.UBUNTU);
        osInfo.setVersionCodename("focal");
        realContext.setProperty(DetectOsCommand.OS_INFO_KEY, osInfo);
        realContext.setProperty(DetectLocationCommand.USE_CHINA_MIRROR_KEY, true);

        ConfigureSystemMirrorsCommand command = new ConfigureSystemMirrorsCommand();

        try {
            // 执行命令
            command.execute(realContext);

            // 断言配置结果
            Boolean configured = (Boolean) realContext.getProperty(ConfigureSystemMirrorsCommand.SYSTEM_MIRRORS_CONFIGURED_KEY);

            log.info("系统镜像源配置结果: {}", configured);
            assertNotNull(configured, "配置结果不应为null");

        } finally {
            // 关闭连接，防止资源泄漏
            connection.disconnect();
        }
    }
}
