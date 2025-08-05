package com.fufu.terminal.controller;

import com.fufu.terminal.dto.TerminalDataDto;
import com.fufu.terminal.dto.TerminalResizeDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStream;

/**
 * STOMP 控制器，用于处理 SSH 终端相关操作。
 * 包括终端数据输入、终端尺寸调整和输出转发启动。
 *
 * <p>所有方法均通过 STOMP 消息映射进行调用。</p>
 *
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SshTerminalStompController {

    private final StompSessionManager sessionManager;

    /**
     * 处理来自客户端的终端数据输入。
     *
     * @param message        终端数据传输对象，包含输入的数据
     * @param headerAccessor 消息头访问器，用于获取会话ID
     */
    @MessageMapping("/terminal/data")
    public void handleTerminalData(
            @Valid TerminalDataDto message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理终端数据，sessionId: {}", sessionId);

        SshConnection connection = getValidatedConnection(sessionId);
        if (connection == null) {
            return;
        }

        try {
            OutputStream outputStream = connection.getOutputStream();
            if (outputStream != null) {
                outputStream.write(message.getData().getBytes());
                outputStream.flush();
                log.debug("向终端发送 {} 字节，sessionId: {}", message.getData().length(), sessionId);
            } else {
                log.error("输出流为 null，sessionId: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "终端输出流不可用");
            }
        } catch (IOException e) {
            log.error("发送数据到终端失败，sessionId: {}，错误: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "发送数据到终端失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理终端数据时发生异常，sessionId: {}，错误: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "终端处理异常: " + e.getMessage());
        }
    }

    /**
     * 处理终端尺寸调整请求。
     *
     * @param message        终端尺寸调整数据对象，包含行列数
     * @param headerAccessor 消息头访问器，用于获取会话ID
     */
    @MessageMapping("/terminal/resize")
    public void handleTerminalResize(
            @Valid TerminalResizeDto message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理终端尺寸调整，sessionId: {}，cols: {}，rows: {}", sessionId, message.getCols(), message.getRows());

        SshConnection connection = getValidatedConnection(sessionId);
        if (connection == null) {
            return;
        }

        try {
            if (connection.getChannelShell() != null) {
                connection.getChannelShell().setPtySize(
                        message.getCols(),
                        message.getRows(),
                        message.getCols() * 8,
                        message.getRows() * 8
                );
                log.debug("终端尺寸已调整，sessionId: {}，{}x{}", sessionId, message.getCols(), message.getRows());
            } else {
                log.error("Channel shell 为 null，sessionId: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "终端通道不可用");
            }
        } catch (Exception e) {
            log.error("调整终端尺寸失败，sessionId: {}，错误: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "调整终端尺寸失败: " + e.getMessage());
        }
    }

    /**
     * 启动终端输出转发（在 SSH 连接建立后调用）。
     *
     * @param headerAccessor 消息头访问器，用于获取会话ID
     */
    @MessageMapping("/terminal/start-forwarding")
    public void startOutputForwarding(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("启动终端输出转发，sessionId: {}", sessionId);

        SshConnection connection = getValidatedConnection(sessionId);
        if (connection == null) {
            return;
        }

        try {
            sessionManager.startTerminalOutputForwarder(sessionId);
            log.info("已启动输出转发，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("启动输出转发失败，sessionId: {}，错误: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "启动输出转发失败: " + e.getMessage());
        }
    }

    /**
     * 获取并校验 SSH 连接，若不存在则发送错误消息。
     *
     * @param sessionId 会话ID
     * @return 若存在则返回 SshConnection，否则返回 null
     */
    private SshConnection getValidatedConnection(String sessionId) {
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("未找到 SSH 连接，sessionId: {}", sessionId);
            sessionManager.sendErrorMessage(sessionId, "SSH 连接尚未建立");
            return null;
        }
        return connection;
    }
}
