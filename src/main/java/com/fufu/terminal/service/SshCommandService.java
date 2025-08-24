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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 统一的SSH命令执行服务，支持策略控制、审计日志和多种执行模式。
 * <p>
 * 该服务提供了三种主要的API模式:
 * <ul>
 *     <li>{@code executeOrThrow()} - 执行命令，失败时抛出异常</li>
 *     <li>{@code execute()} - 执行命令，返回结果（包括错误情况）</li>
 *     <li>{@code stream()} - 流式执行命令，实时回调输出</li>
 * </ul>
 * </p>
 * <p>
 * 内置策略包括:
 * <ul>
 *     <li>超时控制 - 防止命令长时间阻塞</li>
 *     <li>输出限制 - 防止大量输出消耗内存</li>
 *     <li>命令黑名单 - 禁止执行危险命令</li>
 *     <li>速率限制 - 防止命令执行过于频繁</li>
 *     <li>审计日志 - 记录命令执行（含敏感信息脱敏）</li>
 * </ul>
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
     * 默认命令执行超时时间（毫秒），默认30秒。
     */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 默认输出大小限制（字节），默认1MB。
     */
    private static final int DEFAULT_OUTPUT_LIMIT_BYTES = 1024 * 1024;

    /**
     * 默认错误输出大小限制（字节），默认256KB。
     */
    private static final int DEFAULT_ERROR_LIMIT_BYTES = 256 * 1024;

    /**
     * 危险命令黑名单 - 这些命令将被拒绝执行
     */
    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
            "rm -rf /", "mkfs", "dd if=/dev/zero", ":(){ :|:& };:", "chmod -R 777 /",
            "chown -R root /", "shutdown", "reboot", "halt", "init 0", "init 6",
            "format", "fdisk", "parted", "gparted", "cfdisk"
    ));

    /**
     * 敏感信息匹配模式 - 用于日志脱敏
     */
    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("(?i)password[=:]\\s*\\S+"),
            Pattern.compile("(?i)passwd[=:]\\s*\\S+"),
            Pattern.compile("(?i)secret[=:]\\s*\\S+"),
            Pattern.compile("(?i)token[=:]\\s*\\S+"),
            Pattern.compile("(?i)key[=:]\\s*\\S+"),
            Pattern.compile("-p\\s+\\S+"), // -p password
            Pattern.compile("--password\\s+\\S+")
    };


    /**
     * 简单的速率限制映射 - sessionId -> 最后执行时间
     * 注意：这是一个简单的内存实现，生产环境可考虑使用分布式缓存
     */
    private final java.util.Map<String, Long> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 默认速率限制间隔（毫秒），默认100ms防止命令执行过于频繁
     */
    private static final long RATE_LIMIT_INTERVAL_MS = 100;

    /**
     * 执行命令并在失败时抛出异常（统一API - executeOrThrow模式）。
     * <p>
     * 该方法适用于必须成功的命令执行场景，任何非零退出码都会导致异常。
     * 内置所有安全策略和审计日志。
     * </p>
     *
     * @param session   已建立连接的JSch会话对象
     * @param command   待执行的Shell命令字符串
     * @return 命令的标准输出内容
     * @throws RuntimeException     命令执行失败或违反安全策略
     * @throws InterruptedException 命令执行被中断
     */
    public String executeOrThrow(Session session, String command) throws InterruptedException {
        return executeOrThrow(session, command, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 执行命令并在失败时抛出异常（统一API - executeOrThrow模式，支持超时控制）。
     *
     * @param session   已建立连接的JSch会话对象
     * @param command   待执行的Shell命令字符串
     * @param timeoutMs 超时时间（毫秒）
     * @return 命令的标准输出内容
     * @throws RuntimeException     命令执行失败或违反安全策略
     * @throws InterruptedException 命令执行被中断
     */
    public String executeOrThrow(Session session, String command, long timeoutMs) throws InterruptedException {
        CommandResult result = execute(session, command, timeoutMs);
        if (result.exitStatus() != 0) {
            String errorMsg = "命令执行失败，退出码 " + result.exitStatus() + ": " + result.stderr();
            throw new RuntimeException(errorMsg);
        }
        return result.stdout();
    }

    /**
     * 执行命令并返回完整结果（统一API - execute模式）。
     * <p>
     * 该方法适用于需要处理命令成功/失败情况的场景，不会抛出异常。
     * 内置所有安全策略和审计日志。
     * </p>
     *
     * @param session 已建立连接的JSch会话对象
     * @param command 待执行的Shell命令字符串
     * @return {@link CommandResult} 包含退出码、标准输出和标准错误
     * @throws InterruptedException 命令执行被中断
     */
    public CommandResult execute(Session session, String command) throws InterruptedException {
        return execute(session, command, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 执行命令并返回完整结果（统一API - execute模式，支持超时控制）。
     *
     * @param session   已建立连接的JSch会话对象
     * @param command   待执行的Shell命令字符串
     * @param timeoutMs 超时时间（毫秒）
     * @return {@link CommandResult} 包含退出码、标准输出和标准错误
     * @throws InterruptedException 命令执行被中断
     */
    public CommandResult execute(Session session, String command, long timeoutMs) throws InterruptedException {
        return executeWithPolicies(session, command, timeoutMs, DEFAULT_OUTPUT_LIMIT_BYTES, DEFAULT_ERROR_LIMIT_BYTES);
    }

    /**
     * 流式执行命令（统一API - stream模式）。
     * <p>
     * 该方法适用于长时间运行的命令，通过回调实时处理输出。
     * 注意：流式模式下输出限制策略不适用。
     * </p>
     *
     * @param session        已建立连接的JSch会话对象
     * @param command        待执行的Shell命令字符串
     * @param outputCallback 输出回调处理器
     * @return 命令退出码
     * @throws InterruptedException 命令执行被中断
     */
    public int stream(Session session, String command, Consumer<String> outputCallback) throws InterruptedException {
        return stream(session, command, DEFAULT_TIMEOUT_MS, outputCallback);
    }

    /**
     * 流式执行命令（统一API - stream模式，支持超时控制）。
     *
     * @param session        已建立连接的JSch会话对象
     * @param command        待执行的Shell命令字符串
     * @param timeoutMs      超时时间（毫秒）
     * @param outputCallback 输出回调处理器
     * @return 命令退出码
     * @throws InterruptedException 命令执行被中断
     */
    public int stream(Session session, String command, long timeoutMs, Consumer<String> outputCallback) throws InterruptedException {
        // 执行安全策略检查
        String sessionKey = getSessionKey(session);
        enforceSecurityPolicies(sessionKey, command);
        auditLog(sessionKey, command, "STREAM_START");

        if (session == null || !session.isConnected()) {
            log.warn("SSH session未连接，无法执行命令：{}", redactSensitiveInfo(command));
            return -1;
        }

        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            String wrappedCmd = String.format("sh -c \"%s\"", command.replace("\"", "\\\""));
            channel.setCommand(wrappedCmd);
            channel.setInputStream(null);

            // 直接从流读取并回调，不使用ByteArrayOutputStream
            java.io.InputStream stdout = channel.getInputStream();
            java.io.InputStream stderr = channel.getErrStream();

            channel.connect(COMMAND_CONNECT_TIMEOUT_MS);
            log.debug("已连接exec通道，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command));

            long startTime = System.currentTimeMillis();
            byte[] buffer = new byte[1024];

            // 流式处理输出
            while (!channel.isClosed()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("命令执行期间线程被中断: " + redactSensitiveInfo(command));
                }

                // 检查超时
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log.warn("命令执行超时，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command));
                    return -1;
                }

                // 读取stdout
                if (stdout.available() > 0) {
                    int bytesRead = stdout.read(buffer);
                    if (bytesRead > 0) {
                        String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        outputCallback.accept(output);
                    }
                }

                // 读取stderr
                if (stderr.available() > 0) {
                    int bytesRead = stderr.read(buffer);
                    if (bytesRead > 0) {
                        String error = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        outputCallback.accept("[STDERR] " + error);
                    }
                }

                TimeUnit.MILLISECONDS.sleep(COMMAND_POLL_INTERVAL_MS);
            }

            int exitStatus = channel.getExitStatus();
            log.debug("流式命令执行完成，主机：{}，命令：{}，退出码：{}", session.getHost(), redactSensitiveInfo(command), exitStatus);
            auditLog(sessionKey, command, "STREAM_COMPLETE:" + exitStatus);
            return exitStatus;

        } catch (JSchException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("JSch操作被中断。");
            }
            log.warn("打开或连接exec通道失败，主机：{}，命令：{}，异常：{}", session.getHost(), redactSensitiveInfo(command), e.getMessage());
            return -1;
        } catch (IOException e) {
            log.error("读取命令流时发生I/O错误，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command), e);
            return -1;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    /**
     * 内部服务专用命令执行 - 无安全检查
     * 用于后端服务内部调用，所有命令均为代码生成，无需安全审查
     */
    public CommandResult executeInternal(Session session, String command) throws Exception {
        return executeInternal(session, command, DEFAULT_TIMEOUT_MS, DEFAULT_OUTPUT_LIMIT_BYTES, DEFAULT_ERROR_LIMIT_BYTES);
    }

    /**
     * 内部服务专用命令执行 - 无安全检查（带参数）
     */
    public CommandResult executeInternal(Session session, String command, long timeoutMs, int outputLimitBytes, int errorLimitBytes) throws Exception {
        String sessionKey = session.getHost() + ":" + session.getPort() + "/" + session.getUserName();
        
        // 检查并确保SSH会话连接有效
        ensureSessionConnected(session, sessionKey);
        
        // 仅记录审计日志，不执行安全策略
        auditLog(sessionKey, command, "INTERNAL_EXECUTE_START");
        
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            
            channel.setOutputStream(output);
            channel.setErrStream(error);
            channel.connect((int) timeoutMs);

            // 等待命令执行完成
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitStatus = channel.getExitStatus();
            String stdout = output.toString("UTF-8");
            String stderr = error.toString("UTF-8");

            auditLog(sessionKey, command, "INTERNAL_EXECUTE_COMPLETE:" + exitStatus);
            return new CommandResult(exitStatus, stdout, stderr);

        } catch (Exception e) {
            auditLog(sessionKey, command, "INTERNAL_EXECUTE_ERROR:" + e.getMessage());
            throw e;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    /**
     * 在远程主机上通过SSH执行一条Shell命令，并返回执行结果。
     * <p>
     * 该方法会处理中断异常，允许上层任务（如ScheduledFuture）被正确取消。
     * <strong>注意：此方法保持向后兼容，建议使用新的统一API方法。</strong>
     * </p>
     *
     * @param session 已建立连接的JSch会话对象，不能为空且必须已连接
     * @param command 待执行的Shell命令字符串
     * @return {@link CommandResult} 包含命令的退出码、标准输出和标准错误
     * @throws InterruptedException 如果在等待命令完成期间线程被中断
     * @deprecated 建议使用 {@link #execute(Session, String)} 或 {@link #executeOrThrow(Session, String)} 替代
     */
    @Deprecated
    public CommandResult executeCommand(Session session, String command) throws InterruptedException {
        // 委托给新的统一API
        return execute(session, command);
    }

    // ================ 内部实现方法 ================

    /**
     * 带策略控制的命令执行核心方法。
     */
    private CommandResult executeWithPolicies(Session session, String command, long timeoutMs,
                                               int stdoutLimit, int stderrLimit) throws InterruptedException {
        // 执行安全策略检查
        String sessionKey = getSessionKey(session);
        enforceSecurityPolicies(sessionKey, command);
        auditLog(sessionKey, command, "EXECUTE_START");

        if (session == null || !session.isConnected()) {
            log.warn("SSH session未连接，无法执行命令：{}", redactSensitiveInfo(command));
            return new CommandResult(-1, "", "Session not connected");
        }

        ChannelExec channel = null;
        try (LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(stdoutLimit);
             LimitedByteArrayOutputStream stderr = new LimitedByteArrayOutputStream(stderrLimit)) {

            channel = (ChannelExec) session.openChannel("exec");
            String wrappedCmd = String.format("sh -c \"%s\"", command.replace("\"", "\\\""));
            channel.setCommand(wrappedCmd);
            channel.setInputStream(null);
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(COMMAND_CONNECT_TIMEOUT_MS);
            log.debug("已连接exec通道，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command));

            long startTime = System.currentTimeMillis();

            // 轮询等待命令执行完成，同时检测线程中断和超时
            while (!channel.isClosed()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("命令执行期间线程被中断: " + redactSensitiveInfo(command));
                }

                // 检查超时
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log.warn("命令执行超时，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command));
                    auditLog(sessionKey, command, "EXECUTE_TIMEOUT");
                    return new CommandResult(-1, stdout.toString(StandardCharsets.UTF_8), "Command execution timeout");
                }

                TimeUnit.MILLISECONDS.sleep(COMMAND_POLL_INTERVAL_MS);
            }

            String outStr = stdout.toString(StandardCharsets.UTF_8).trim();
            String errStr = stderr.toString(StandardCharsets.UTF_8).trim();
            int exitStatus = channel.getExitStatus();

            log.debug("命令执行完成，主机：{}，命令：{}，退出码：{}", session.getHost(), redactSensitiveInfo(command), exitStatus);
            auditLog(sessionKey, command, "EXECUTE_COMPLETE:" + exitStatus);
            return new CommandResult(exitStatus, outStr, errStr);

        } catch (JSchException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("JSch操作被中断。");
            }
            log.warn("打开或连接exec通道失败，主机：{}，命令：{}，异常：{}", session.getHost(), redactSensitiveInfo(command), e.getMessage());
            auditLog(sessionKey, command, "EXECUTE_ERROR:" + e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        } catch (IOException e) {
            log.error("读取命令输出流时发生I/O错误，主机：{}，命令：{}", session.getHost(), redactSensitiveInfo(command), e);
            auditLog(sessionKey, command, "EXECUTE_IO_ERROR:" + e.getMessage());
            return new CommandResult(-1, "", "IO Error: " + e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    /**
     * 执行安全策略检查。
     */
    private void enforceSecurityPolicies(String sessionKey, String command) {
        // 1. 危险命令检查
        String normalizedCommand = command.toLowerCase().trim();
        
        // 特殊处理：允许合法的Docker命令
        if (normalizedCommand.startsWith("docker ") || normalizedCommand.startsWith("sudo docker ")) {
            // 对于Docker命令，只检查真正危险的操作，不检查format等参数
            for (String dangerous : DANGEROUS_COMMANDS) {
                // 跳过format检查，因为docker inspect --format是合法命令
                if ("format".equals(dangerous)) {
                    continue;
                }
                if (normalizedCommand.contains(dangerous.toLowerCase())) {
                    throw new SecurityException("拒绝执行危险命令: " + dangerous);
                }
            }
        } else {
            // 对于非Docker命令，执行完整的安全检查
            for (String dangerous : DANGEROUS_COMMANDS) {
                if (normalizedCommand.contains(dangerous.toLowerCase())) {
                    throw new SecurityException("拒绝执行危险命令: " + dangerous);
                }
            }
        }

        // 2. 速率限制检查
        long now = System.currentTimeMillis();
        Long lastExecution = rateLimitMap.get(sessionKey);
        if (lastExecution != null && (now - lastExecution) < RATE_LIMIT_INTERVAL_MS) {
            throw new SecurityException("命令执行过于频繁，请稍后再试");
        }
        rateLimitMap.put(sessionKey, now);
    }

    /**
     * 审计日志记录（含敏感信息脱敏）。
     */
    private void auditLog(String sessionKey, String command, String action) {
        String redactedCommand = redactSensitiveInfo(command);
        log.info("[AUDIT] Session: {}, Action: {}, Command: {}", sessionKey, action, redactedCommand);
    }

    /**
     * 脱敏敏感信息。
     */
    private String redactSensitiveInfo(String command) {
        String result = command;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("***REDACTED***");
        }
        return result;
    }

    /**
     * 获取会话标识键。
     */
    private String getSessionKey(Session session) {
        return session.getHost() + ":" + session.getPort() + "/" + session.getUserName();
    }

    /**
     * 有大小限制的ByteArrayOutputStream，防止输出过大占用内存。
     */
    private static class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int maxSize;
        private boolean limitExceeded = false;

        public LimitedByteArrayOutputStream(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public void write(int b) {
            if (count < maxSize) {
                super.write(b);
            } else if (!limitExceeded) {
                limitExceeded = true;
                String truncateMsg = "\n[OUTPUT TRUNCATED - LIMIT EXCEEDED]\n";
                super.write(truncateMsg.getBytes(StandardCharsets.UTF_8), 0, truncateMsg.length());
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (count + len <= maxSize) {
                super.write(b, off, len);
            } else {
                int remaining = maxSize - count;
                if (remaining > 0) {
                    super.write(b, off, remaining);
                }
                if (!limitExceeded) {
                    limitExceeded = true;
                    String truncateMsg = "\n[OUTPUT TRUNCATED - LIMIT EXCEEDED]\n";
                    super.write(truncateMsg.getBytes(StandardCharsets.UTF_8), 0, truncateMsg.length());
                }
            }
        }
    }

    /**
     * 确保SSH会话连接有效，如果会话断开则记录警告日志
     * <p>
     * 注意：此方法不会重新连接会话，因为重连需要密码等敏感信息，
     * 应该由调用方（如StompSessionManager）负责重连逻辑。
     * </p>
     * 
     * @param session SSH会话
     * @param sessionKey 会话键，用于日志记录
     * @throws JSchException 当会话无效且无法使用时
     */
    private void ensureSessionConnected(Session session, String sessionKey) throws JSchException {
        if (session == null) {
            log.error("SSH会话为空，sessionKey: {}", sessionKey);
            throw new JSchException("SSH会话未初始化");
        }
        
        if (!session.isConnected()) {
            log.warn("检测到SSH会话已断开，sessionKey: {}，请检查网络连接或重新建立连接", sessionKey);
            throw new JSchException("SSH会话已断开，需要重新连接");
        }
        
        // 会话连接正常
        log.debug("SSH会话连接正常，sessionKey: {}", sessionKey);
    }
}
