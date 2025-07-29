package com.fufu.terminal.command.impl.preprocess;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
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
 * DetectLocationCommand 真机集成测试
 * 仅保留真实服务器连接的测试方法
 */
@Slf4j
class DetectLocationCommandTest {

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
        DetectLocationCommand command = new DetectLocationCommand();

        try {
            // 执行检测命令
            command.execute(realContext);

            // 断言检测结果
            String locationInfo = (String) realContext.getProperty(DetectLocationCommand.LOCATION_INFO_KEY);
            Boolean useChinaMirror = (Boolean) realContext.getProperty(DetectLocationCommand.USE_CHINA_MIRROR_KEY);

            log.info("检测到的地理位置信息: {}", locationInfo);
            log.info("是否使用中国镜像: {}", useChinaMirror);

            assertNotNull(useChinaMirror, "useChinaMirror标志不应为null");
            // locationInfo可能为null（如果所有API都失败），这是正常的

        } finally {
            // 关闭连接，防止资源泄漏
            connection.disconnect();
        }
    }
}
