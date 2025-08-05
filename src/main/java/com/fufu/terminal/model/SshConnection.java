package com.fufu.terminal.model;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Data;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * 封装 JSch SSH 连接信息的实体类，管理 Shell 和 SFTP 通道，并支持监控任务管理。
 * 线程安全性：监控任务和 SFTP 通道的操作使用同步和 volatile 保证线程安全。
 * <p>
 * 用法示例：
 * <pre>
 *     SshConnection conn = new SshConnection(jsch, session, shell, in, out);
 *     ChannelSftp sftp = conn.getOrCreateSftpChannel();
 *     // ... 使用 sftp ...
 *     conn.disconnect();
 * </pre>
 * </p>
 *
 * @author lizelin
 */
@Data
public class SshConnection {
    /**
     * JSch 实例
     */
    private final JSch jsch;

    /**
     * SSH 会话
     */
    private final Session session;

    /**
     * Shell 通道
     */
    private final ChannelShell channelShell;

    /**
     * Shell 的输入流
     */
    private final InputStream inputStream;

    /**
     * Shell 的输出流
     */
    private final OutputStream outputStream;

    /**
     * SFTP 通道，懒加载
     */
    private volatile ChannelSftp channelSftp;

    /**
     * 监控任务 Future
     */
    private volatile Future<?> monitoringTask;

    /**
     * 缓存的最新监控数据
     * -- GETTER --
     * 获取缓存的最新监控数据。
     * <p>
     * <p>
     * -- SETTER --
     * 设置缓存的最新监控数据。
     *
     * @return 最新的监控数据 Map
     * @param lastMonitorStats 最新的监控数据 Map
     */
    private volatile Map<String, Object> lastMonitorStats;

    /**
     * 构造 SSH 连接对象。
     *
     * @param jsch         JSch 实例
     * @param session      SSH 会话
     * @param channelShell Shell 通道
     * @param inputStream  Shell 输入流
     * @param outputStream Shell 输出流
     */
    public SshConnection(JSch jsch, Session session, ChannelShell channelShell, InputStream inputStream, OutputStream outputStream) {
        this.jsch = jsch;
        this.session = session;
        this.channelShell = channelShell;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * 获取 JSch 会话。
     *
     * @return JSch 会话实例
     */
    public Session getJschSession() {
        return session;
    }

    /**
     * 获取 SFTP 通道。如果不存在或已关闭，则创建一个新的。
     * 线程安全，避免并发创建多个 SFTP 通道。
     *
     * @return 可用的 ChannelSftp 实例
     * @throws JSchException 如果创建通道失败
     */
    public ChannelSftp getOrCreateSftpChannel() throws JSchException {
        ChannelSftp sftp = channelSftp;
        if (sftp == null || sftp.isClosed()) {
            synchronized (this) {
                if (channelSftp == null || channelSftp.isClosed()) {
                    channelSftp = (ChannelSftp) session.openChannel("sftp");
                    channelSftp.connect();
                }
                sftp = channelSftp;
            }
        }
        return sftp;
    }

    /**
     * 关闭所有连接和通道，释放资源。
     * 包括监控任务、SFTP 通道、Shell 通道和 Session。
     */
    public void disconnect() {
        cancelMonitoringTask();
        if (channelSftp != null) {
            try {
                if (channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        if (channelShell != null) {
            try {
                if (channelShell.isConnected()) {
                    channelShell.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        if (session != null) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 设置监控任务。
     * 会自动取消之前的监控任务。
     *
     * @param monitoringTask 监控任务 Future 对象
     */
    public synchronized void setMonitoringTask(Future<?> monitoringTask) {
        cancelMonitoringTask();
        this.monitoringTask = monitoringTask;
    }

    /**
     * 取消当前监控任务（如有）。
     */
    public synchronized void cancelMonitoringTask() {
        if (this.monitoringTask != null && !this.monitoringTask.isDone()) {
            this.monitoringTask.cancel(true);
        }
        this.monitoringTask = null;
    }

}
