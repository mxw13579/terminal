package com.fufu.terminal.model;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.Getter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * 用于封装JSch连接信息的实体类，同时管理 Shell 和 SFTP 通道。
 * @author lizelin
 */
@Data
public class SshConnection {
    private final JSch jsch;
    private final Session session;
    /**
     *  Shell 通道
     */
    private final ChannelShell channelShell;
    /**
     * Shell 的输入流
     */
    private final InputStream inputStream;
    @Getter
    private final OutputStream outputStream;
    // SFTP通道，使用时再创建（懒加载）
    private ChannelSftp channelSftp;
    /**
     * 监控
     */
    private volatile Future<?> monitoringTask;
    /**
     * 新增: 用于缓存最新的监控数据
     */
    private volatile Map<String, Object> lastMonitorStats;

    public SshConnection(JSch jsch, Session session, ChannelShell channelShell, InputStream inputStream, OutputStream outputStream) {
        this.jsch = jsch;
        this.session = session;
        this.channelShell = channelShell;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }


    public Session getJschSession() {
        return session;
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
        // 在断开连接时取消监控任务
        cancelMonitoringTask();
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

    // 新增方法
    public synchronized void setMonitoringTask(Future<?> monitoringTask) {
        cancelMonitoringTask(); // 取消任何旧任务
        this.monitoringTask = monitoringTask;
    }

    public synchronized void cancelMonitoringTask() {
        if (this.monitoringTask != null && !this.monitoringTask.isDone()) {
            this.monitoringTask.cancel(true);
        }
        this.monitoringTask = null;
    }
}
