package com.fufu.terminal.service;

import com.fufu.terminal.model.CommandResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 通用SSH命令执行服务，支持在远程主机上执行Shell命令并获取结果。
 * <p>
 * 该服务封装了命令执行、超时控制、输出捕获及异常处理等功能。
 * </p>
 *
 * @author lizelin
 * @since 1.0
 */
@Slf4j
@Service
public class SshCommandService {

    /**
     * SSH命令连接超时时间（毫秒），默认5秒。
     */
    private static final int COMMAND_CONNECT_TIMEOUT_MS = 5000;

    /**
     * 命令执行状态轮询间隔（毫秒），默认50毫秒。
     */
    private static final int COMMAND_POLL_INTERVAL_MS = 50;

    /**
     * 在远程主机上通过SSH执行一条Shell命令，并返回执行结果。
     * <p>
     * 该方法会处理中断异常，允许上层任务（如ScheduledFuture）被正确取消。
     * </p>
     *
     * @param session 已建立连接的JSch会话对象，不能为空且必须已连接
     * @param command 待执行的Shell命令字符串
     * @return {@link CommandResult} 包含命令的退出码、标准输出和标准错误
     * @throws InterruptedException 如果在等待命令完成期间线程被中断
     */
    public CommandResult executeCommand(Session session, String command) throws InterruptedException {
        if (session == null || !session.isConnected()) {
            log.warn("SSH session未连接，无法执行命令：{}", command);
            return new CommandResult(-1, "", "Session not connected");
        }

        ChannelExec channel = null;
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
             ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            channel = (ChannelExec) session.openChannel("exec");
            // 使用 sh -c "..." 包裹命令，保证复杂命令（如含引号、管道等）能被正确解析
            String wrappedCmd = String.format("sh -c \"%s\"", command.replace("\"", "\\\""));
            channel.setCommand(wrappedCmd);
            channel.setInputStream(null);
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(COMMAND_CONNECT_TIMEOUT_MS);
            log.debug("已连接exec通道，主机：{}，命令：{}", session.getHost(), command);

            // 轮询等待命令执行完成，同时检测线程中断
            while (!channel.isClosed()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("命令执行期间线程被中断: " + command);
                }
                TimeUnit.MILLISECONDS.sleep(COMMAND_POLL_INTERVAL_MS);
            }

            String outStr = stdout.toString(StandardCharsets.UTF_8).trim();
            String errStr = stderr.toString(StandardCharsets.UTF_8).trim();
            int exitStatus = channel.getExitStatus();

            log.debug("命令执行完成，主机：{}，命令：{}，退出码：{}", session.getHost(), command, exitStatus);

            return new CommandResult(exitStatus, outStr, errStr);

        } catch (JSchException e) {
            // 若JSch异常的根本原因为中断，转换为InterruptedException抛出
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("JSch操作被中断。");
            }
            log.warn("打开或连接exec通道失败，主机：{}，命令：{}，异常：{}",
                    session.getHost(), command, e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        } catch (IOException e) {
            log.error("读取命令输出流时发生I/O错误，主机：{}，命令：{}", session.getHost(), command, e);
            return new CommandResult(-1, "", "IO Error: " + e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
}
