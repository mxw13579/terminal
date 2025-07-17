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
 * 通用的SSH命令执行服务.
 * @author lizelin
 */
@Slf4j
@Service
public class SshCommandService {

    private static final int COMMAND_CONNECT_TIMEOUT_MS = 5000;
    private static final int COMMAND_POLL_INTERVAL_MS = 50;

    /**
     * 在远程主机上执行一条shell命令.
     * <p>
     * 该方法会处理中断异常，允许上层任务（如ScheduledFuture）被正确取消。
     *
     * @param session JSch会话对象
     * @param command 要执行的命令
     * @return 包含退出码、标准输出和标准错误的CommandResult对象
     * @throws InterruptedException 如果在等待命令完成时线程被中断
     */
    public CommandResult executeCommand(Session session, String command) throws InterruptedException {
        if (session == null || !session.isConnected()) {
            log.warn("SSH session 未连接，无法执行命令：{}", command);
            return new CommandResult(-1, "", "Session not connected");
        }

        ChannelExec channel = null;
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
             ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            channel = (ChannelExec) session.openChannel("exec");
            // 使用 sh -c "..." 来确保复杂命令（含引号、管道等）的正确执行
            String wrappedCmd = String.format("sh -c \"%s\"", command.replace("\"", "\\\""));
            channel.setCommand(wrappedCmd);
            channel.setInputStream(null);
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(COMMAND_CONNECT_TIMEOUT_MS);

            // 等待命令执行完成，同时周期性检查线程中断状态
            while (!channel.isClosed()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("命令执行在等待时被中断: " + command);
                }
                // 使用短暂休眠避免CPU空转
                TimeUnit.MILLISECONDS.sleep(COMMAND_POLL_INTERVAL_MS);
            }

            String outStr = stdout.toString(StandardCharsets.UTF_8).trim();
            String errStr = stderr.toString(StandardCharsets.UTF_8).trim();
            return new CommandResult(channel.getExitStatus(), outStr, errStr);

        } catch (JSchException e) {
            // 如果JSch异常的根本原因是中断，则将其转换为InterruptedException并抛出
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 重新设置中断状态
                throw new InterruptedException("JSch 操作被中断。");
            }
            log.warn("打开或连接 exec 通道失败 cmd={}：{}", command, e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        } catch (IOException e) {
            log.error("读取命令输出流时发生I/O错误", e);
            return new CommandResult(-1, "", "IO Error: " + e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
}
