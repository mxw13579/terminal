package com.fufu.terminal.model;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 用于封装JSch连接信息的实体类，同时管理 Shell 和 SFTP 通道。
 * @author lizelin
 */
public class SshConnection {
    private final JSch jsch;
    private final Session session;
    private final ChannelShell channelShell;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    // SFTP通道，使用时再创建（懒加载）
    private ChannelSftp channelSftp;
    public SshConnection(JSch jsch, Session session, ChannelShell channelShell, InputStream inputStream, OutputStream outputStream) {
        this.jsch = jsch;
        this.session = session;
        this.channelShell = channelShell;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }
    /**
     * 获取 Shell 通道
     */
    public ChannelShell getChannelShell() {
        return channelShell;
    }
    /**
     * 获取 Shell 的输入流
     */
    public InputStream getInputStream() {
        return inputStream;
    }
    /**
     * 获取 Shell 的输出流
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }
    /**
     * 获取 SFTP 通道。如果不存在或已关闭，则创建一个新的。
     * @return 可用的 ChannelSftp 实例
     * @throws JSchException 如果创建通道失败
     */
    public ChannelSftp getOrCreateSftpChannel() throws JSchException {
        if (channelSftp == null || channelSftp.isClosed()) {
            // 从同一个 Session 中打开一个新的 sftp 通道
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(); // 默认超时
        }
        return channelSftp;
    }
    /**
     * 关闭所有连接和通道
     */
    public void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
        if (channelShell != null && channelShell.isConnected()) {
            channelShell.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
