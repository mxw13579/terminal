package com.fufu.terminal.command.impl.preprocess;

import com.alibaba.fastjson2.JSON;
import com.fufu.terminal.command.CommandContext;
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
 * DetectOsCommand 真机测试
 * 只保留真实服务器连接的单元测试
 */
@Slf4j
class DetectOsCommandTest {

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
        DetectOsCommand command = new DetectOsCommand();

        try {
            // 执行命令
            command.execute(realContext);

            // 断言
            OsInfo osInfo = (OsInfo) realContext.getProperty(DetectOsCommand.OS_INFO_KEY);
            log.info("检测到的操作系统信息: {}", JSON.toJSONString(osInfo));

            assertNotNull(osInfo, "操作系统信息不应为null");
            assertNotNull(osInfo.getId(), "系统ID不应为null");
            assertNotNull(osInfo.getSystemType(), "系统类型不应为null");
            assertNotEquals(SystemType.UNKNOWN, osInfo.getSystemType(), "应该能够识别系统类型");

            // 验证SSH连接也设置了OS信息
            assertEquals(osInfo, connection.getOsInfo());

        } finally {
            // 关闭连接，防止资源泄漏
            connection.disconnect();
        }
    }
}
