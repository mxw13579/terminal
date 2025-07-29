package com.fufu.terminal.command.impl.environment;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.SshCommandUtil;
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
 * CheckUnzipCommand 单元测试
 * 仅保留真机测试用例
 */
@Slf4j
class CheckUnzipCommandTest {

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
        CheckUnzipCommand command = new CheckUnzipCommand();

        try {
            // 执行命令
            command.execute(realContext);

            // 断言并输出结果
            Boolean unzipInstalled = (Boolean) realContext.getProperty(CheckUnzipCommand.UNZIP_INSTALLED_KEY);
            String unzipVersion = (String) realContext.getProperty(CheckUnzipCommand.UNZIP_VERSION_KEY);

            log.info("Unzip安装状态: {}", unzipInstalled);
            log.info("Unzip版本: {}", unzipVersion);

            assertNotNull(unzipInstalled, "Unzip安装状态不应为null");

            if (unzipInstalled) {
                log.info("Unzip已安装，版本: {}", unzipVersion != null ? unzipVersion : "未知");
            } else {
                log.info("Unzip未安装");
                assertNull(unzipVersion, "Unzip未安装时版本应为null");
            }

        } finally {
            // 关闭连接，防止资源泄漏
            connection.disconnect();
        }
    }
}
