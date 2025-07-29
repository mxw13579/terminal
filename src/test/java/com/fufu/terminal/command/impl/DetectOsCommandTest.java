//package com.fufu.terminal.command.impl;
//
//import com.alibaba.fastjson2.JSON;
//import com.fufu.terminal.command.CommandContext;
//import com.fufu.terminal.command.CommandResult;
//import com.fufu.terminal.command.SshCommandUtil;
//import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
//import com.fufu.terminal.model.SshConnection;
//import com.jcraft.jsch.ChannelShell;
//import com.jcraft.jsch.JSch;
//import com.jcraft.jsch.Session;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.MockedStatic;
//import org.mockito.Mockito;
//import org.mockito.MockitoAnnotations;
//
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//
///**
// * DetectOsCommand 单元测试
// * 通过模拟 SshCommandUtil 的静态方法，验证不同场景下操作系统识别逻辑的正确性
// */
//@Slf4j
//class DetectOsCommandTest {
//
//    private SshConnection sshConnection;
//
//    private CommandContext context;
//    private MockedStatic<SshCommandUtil> sshUtilMock;
//
////    /**
////     * 每个测试用例前初始化测试环境和静态方法Mock
////     */
////    @BeforeEach
////    void setUp() {
////        MockitoAnnotations.openMocks(this);
////        // 创建命令上下文，WebSocketSession 对本测试无影响
////        context = new CommandContext(sshConnection, null);
////        detectOsCommand = new DetectOsCommand();
////
////        // Mock SshCommandUtil.executeCommand 静态方法
////        sshUtilMock = Mockito.mockStatic(SshCommandUtil.class);
////    }
//
////    /**
////     * 每个测试用例后关闭静态方法Mock，防止污染其他测试
////     */
////    @AfterEach
////    void tearDown() {
////        sshUtilMock.close();
////    }
//
//    /**
//     * 测试场景：/etc/os-release 文件存在且内容完整，能够直接识别出操作系统信息
//     * 断言：ID、VERSION_ID、PRETTY_NAME 字段正确解析
//     */
//    @Test
//    void testExecute_SuccessWithOsRelease() throws Exception {
//        // Arrange: 模拟 os-release 文件存在且内容有效
//        String osReleaseContent = "PRETTY_NAME=\"Ubuntu 22.04.1 LTS\"\nID=ubuntu\nVERSION_ID=\"22.04\"";
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("cat /etc/os-release")))
//                .thenReturn(new CommandResult(osReleaseContent, "", 0));
//
//        // Act: 执行操作系统检测命令
//        detectOsCommand.execute(context);
//
//        // Assert: 校验 osInfo 字段正确
//        Map<String, String> osInfo = context.getProperty(DetectOsCommand.OS_INFO_KEY, Map.class);
//        assertNotNull(osInfo);
//        assertEquals("ubuntu", osInfo.get("ID"));
//        assertEquals("22.04", osInfo.get("VERSION_ID"));
//        assertEquals("Ubuntu 22.04.1 LTS", osInfo.get("PRETTY_NAME"));
//    }
//
//    /**
//     * 测试场景：/etc/os-release 文件不存在，lsb_release 命令可用
//     * 断言：能正确回退到 lsb_release 并解析出操作系统信息
//     */
//    @Test
//    void testExecute_FallbackToLsbRelease() throws Exception {
//        // Arrange: 模拟 os-release 不存在，lsb_release -a 返回有效内容
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("cat /etc/os-release")))
//                .thenReturn(new CommandResult("", "No such file or directory", 1));
//        String lsbReleaseContent = "Distributor ID:\tDebian\nDescription:\tDebian GNU/Linux 11 (bullseye)\nRelease:\t11\nCodename:\tbullseye";
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("lsb_release -a")))
//                .thenReturn(new CommandResult(lsbReleaseContent, "", 0));
//
//        // Act: 执行检测
//        detectOsCommand.execute(context);
//
//        // Assert: 校验回退到 lsb_release 并正确解析
//        Map<String, String> osInfo = context.getProperty(DetectOsCommand.OS_INFO_KEY, Map.class);
//        assertNotNull(osInfo);
//        assertEquals("debian", osInfo.get("ID"));
//        assertEquals("11", osInfo.get("VERSION_ID"));
//        assertEquals("Debian GNU/Linux 11 (bullseye)", osInfo.get("PRETTY_NAME"));
//    }
//
//    /**
//     * 测试场景：/etc/os-release 和 lsb_release 均不可用，/etc/redhat-release 可用
//     * 断言：能正确回退到 redhat-release 并解析出 CentOS 信息
//     */
//    @Test
//    void testExecute_FallbackToRedhatRelease() throws Exception {
//        // Arrange: 模拟前两个方法失败，redhat-release 文件存在
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("cat /etc/os-release")))
//                .thenReturn(new CommandResult("", "No such file or directory", 1));
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("lsb_release -a")))
//                .thenReturn(new CommandResult("", "command not found", 127));
//        String redhatReleaseContent = "CentOS Linux release 7.9.2009 (Core)";
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), eq("cat /etc/redhat-release")))
//                .thenReturn(new CommandResult(redhatReleaseContent, "", 0));
//
//        // Act: 执行检测
//        detectOsCommand.execute(context);
//
//        // Assert: 校验回退到 redhat-release 并正确解析
//        Map<String, String> osInfo = context.getProperty(DetectOsCommand.OS_INFO_KEY, Map.class);
//        assertNotNull(osInfo);
//        assertEquals("centos", osInfo.get("ID"));
//        assertEquals("CentOS", osInfo.get("NAME"));
//        assertEquals("CentOS Linux release 7.9.2009 (Core)", osInfo.get("PRETTY_NAME"));
//    }
//
//    /**
//     * 测试场景：所有检测方法都失败
//     * 断言：抛出 RuntimeException，提示无法识别操作系统
//     */
//    @Test
//    void testExecute_AllMethodsFail() {
//        // Arrange: 模拟所有命令都失败
//        sshUtilMock.when(() -> SshCommandUtil.executeCommand(any(SshConnection.class), any(String.class)))
//                .thenReturn(new CommandResult("", "failure", 1));
//
//        // Act & Assert: 断言抛出异常，且异常信息正确
//        Exception exception = assertThrows(RuntimeException.class, () -> {
//            detectOsCommand.execute(context);
//        });
//        // 这里根据实际 DetectOsCommand 的异常信息调整断言
//        assertEquals("无法确定操作系统，所有检测方法均失败。", exception.getMessage());
//    }
//
//    /**
//     * 测试场景：使用真实服务器连接（需填写实际IP、端口、账号、密码）
//     * 注意：当前测试中服务器信息留空，实际使用时请替换为有效值
//     * 断言：连接失败时抛出运行时异常
//     */
//    @Test
//    void testExecute_WithRealServerConnection() throws Exception {
//        // 从环境变量或配置文件读取真实服务器信息
//        String host = "156.233.233.40";
//        int port = 22;
//        String user = "root";
//        String password = "vxwpYXDJ6295";
//
//        // 检查参数
//        assertNotNull(host, "请设置SSH_HOST环境变量");
//        assertNotNull(user, "请设置SSH_USER环境变量");
//        assertNotNull(password, "请设置SSH_PASSWORD环境变量");
//
//        // 创建JSch及相关连接
//        JSch jsch = new JSch();
//        Session session = jsch.getSession(user, host, port);
//        session.setPassword(password);
//        session.setConfig("StrictHostKeyChecking", "no");
//        session.connect(30000);
//
//        ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
//        channelShell.setPtyType("xterm");
//        InputStream inputStream = channelShell.getInputStream();
//        OutputStream outputStream = channelShell.getOutputStream();
//        channelShell.connect(3000);
//
//        log.info("SSH连接已建立: {}@{}:{}", user, host, port);
//
//        SshConnection connection = new SshConnection(jsch, session, channelShell, inputStream, outputStream);
//        CommandContext context = new CommandContext(connection, null);
//        DetectOsCommand command = new DetectOsCommand();
//
//        try {
//            command.execute(context);
//            Object osInfo = context.getProperty("os_info");
//            log.info("OS Info: {}", JSON.toJSONString(osInfo));
//            assertNotNull(osInfo, "os_info 应该不为null");
//        } finally {
//            // 关闭连接，防止资源泄漏
//            connection.disconnect();
//        }
//    }
//}
