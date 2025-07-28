package com.fufu.terminal.command;

import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelExec;

import java.io.ByteArrayOutputStream;

/**
 * SSH 命令执行工具类
 */
public class SshCommandUtil {

    /**
     * 在远程服务器上执行一个命令并返回其封装的结果
     *
     * @param sshConnection SSH 连接对象
     * @param command       要执行的命令
     * @return 命令执行的封装结果，包含stdout, stderr, 和exit status
     * @throws Exception 如果连接或执行过程中出现IO等严重错误
     */
    public static CommandResult executeCommand(SshConnection sshConnection, String command) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) sshConnection.getJschSession().openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);
            // 5秒连接超时
            channel.connect(5000);

            // 等待命令执行完成
            while (!channel.isClosed()) {
                //noinspection BusyWait
                Thread.sleep(100);
            }

            return new CommandResult(outputStream.toString(), errorStream.toString(), channel.getExitStatus());

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
}
