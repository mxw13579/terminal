package com.fufu.terminal.controller;

import com.fufu.terminal.dto.ErrorDto;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;

import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * WebSocket全局异常处理器（STOMP消息）
 * <p>
 * 负责捕获STOMP消息处理过程中的各类异常，并将其转换为标准化的错误消息对象，推送给前端用户。
 * 所有错误信息均为中文，便于终端用户理解。
 * </p>
 * <p>
 * 支持的异常类型包括：通用异常、SSH/SFTP异常、I/O异常、参数校验异常、绑定异常、非法参数异常、
 * SillyTavern业务异常、安全异常、中断异常等。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Controller
public class StompGlobalExceptionHandler {

    /**
     * 处理所有未捕获的通用异常，返回用户友好的错误信息。
     *
     * @param e 异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/error")
    public ErrorDto handleGenericException(Exception e) {
        log.error("STOMP消息处理错误", e);
        String userFriendlyMessage = getUserFriendlyMessage(e);
        return new ErrorDto(
                "PROCESSING_ERROR",
                userFriendlyMessage,
                getStackTraceAsString(e)
        );
    }

    /**
     * 处理SSH连接相关异常，返回中文错误信息。
     *
     * @param e JSch异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(JSchException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleSshException(JSchException e) {
        log.error("SSH连接错误", e);

        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String userMessage;

        if (message.contains("auth fail")) {
            userMessage = "用户名或密码错误，请检查登录凭据";
        } else if (message.contains("connection refuse")) {
            userMessage = "无法连接到服务器，请检查服务器地址和端口";
        } else if (message.contains("timeout")) {
            userMessage = "连接超时，请检查网络连接";
        } else if (message.contains("host key")) {
            userMessage = "服务器主机密钥验证失败";
        } else {
            userMessage = "SSH连接失败: " + e.getMessage();
        }

        return new ErrorDto("SSH_ERROR", userMessage);
    }

    /**
     * 处理SFTP操作相关异常，返回中文错误信息。
     *
     * @param e Sftp异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(SftpException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleSftpException(SftpException e) {
        log.error("SFTP操作错误", e);
        String errorMessage = switch (e.id) {
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> "文件或目录不存在";
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "权限不足，无法访问文件";
            case ChannelSftp.SSH_FX_BAD_MESSAGE -> "无效的SFTP请求";
            case ChannelSftp.SSH_FX_NO_CONNECTION -> "SFTP连接丢失";
            case ChannelSftp.SSH_FX_CONNECTION_LOST -> "SFTP连接中断";
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED -> "不支持的操作";
            default -> "SFTP操作失败: " + e.getMessage();
        };

        return new ErrorDto("SFTP_ERROR", errorMessage);
    }

    /**
     * 处理I/O异常（如文件操作或网络错误），返回中文错误信息。
     *
     * @param e IOException对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(IOException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleIOException(IOException e) {
        log.error("STOMP消息处理过程中I/O错误", e);

        String message = e.getMessage();
        String userMessage;

        if (message != null) {
            if (message.contains("No space left")) {
                userMessage = "磁盘空间不足，无法完成操作";
            } else if (message.contains("Permission denied")) {
                userMessage = "文件权限不足，无法访问文件";
            } else if (message.contains("Connection reset")) {
                userMessage = "网络连接中断，请重试";
            } else {
                userMessage = "I/O操作失败: " + message;
            }
        } else {
            userMessage = "I/O操作失败";
        }

        return new ErrorDto("IO_ERROR", userMessage);
    }

    /**
     * 处理参数校验异常，返回详细的中文错误信息。
     *
     * @param e 约束校验异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(ConstraintViolationException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleValidationException(ConstraintViolationException e) {
        log.warn("STOMP消息验证错误", e);
        StringBuilder errorMsg = new StringBuilder("参数验证失败: ");
        e.getConstraintViolations().forEach(violation ->
                errorMsg.append(violation.getPropertyPath())
                        .append(" ")
                        .append(violation.getMessage())
                        .append("; ")
        );
        return new ErrorDto("VALIDATION_ERROR", errorMsg.toString());
    }

    /**
     * 处理数据绑定异常（如消息反序列化错误），返回详细的中文错误信息。
     *
     * @param e 绑定异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(BindException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleBindException(BindException e) {
        log.warn("消息绑定错误", e);
        StringBuilder errorMsg = new StringBuilder("消息格式错误: ");
        e.getBindingResult().getFieldErrors().forEach(error ->
                errorMsg.append(error.getField())
                        .append(" ")
                        .append(error.getDefaultMessage())
                        .append("; ")
        );
        return new ErrorDto("BINDING_ERROR", errorMsg.toString());
    }

    /**
     * 处理非法参数异常，返回中文错误信息。
     *
     * @param e 非法参数异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("STOMP消息中的非法参数", e);
        return new ErrorDto(
                "INVALID_ARGUMENT",
                "无效的请求参数: " + e.getMessage()
        );
    }

    /**
     * 处理SillyTavern相关的业务异常，返回友好的中文错误信息。
     *
     * @param e 运行时异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(RuntimeException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleRuntimeException(RuntimeException e) {
        log.error("SillyTavern业务异常", e);
        String message = e.getMessage();
        String userMessage = getSillyTavernFriendlyMessage(message);
        return new ErrorDto("SILLYTAVERN_ERROR", userMessage);
    }

    /**
     * 处理安全相关异常，返回中文错误信息。
     *
     * @param e 安全异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(SecurityException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleSecurityException(SecurityException e) {
        log.error("STOMP消息处理安全错误", e);

        String message = e.getMessage();
        String userMessage;

        if (message != null && message.contains("不允许执行的命令")) {
            userMessage = "安全限制: 不允许执行此命令";
        } else {
            userMessage = "访问被拒绝: " + (message != null ? message : "权限不足");
        }

        return new ErrorDto("SECURITY_ERROR", userMessage);
    }

    /**
     * 处理中断操作（如用户主动取消任务），返回中文提示。
     *
     * @param e 中断异常对象
     * @return 标准化错误信息
     */
    @MessageExceptionHandler(InterruptedException.class)
    @SendToUser("/queue/error")
    public ErrorDto handleInterruptedException(InterruptedException e) {
        log.info("操作被中断", e);
        Thread.currentThread().interrupt(); // 恢复中断状态
        return new ErrorDto(
                "OPERATION_CANCELLED",
                "操作已取消: " + (e.getMessage() != null ? e.getMessage() : "用户取消操作")
        );
    }

    /**
     * 获取用户友好的通用错误信息（中文）。
     *
     * @param e 异常对象
     * @return 用户可读的错误信息
     */
    private String getUserFriendlyMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "系统出现未知错误，请联系管理员";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("timeout")) {
            return "操作超时，请检查网络连接后重试";
        } else if (lowerMessage.contains("connection")) {
            return "网络连接错误，请检查服务器状态";
        } else if (lowerMessage.contains("permission")) {
            return "权限不足，请检查用户权限设置";
        } else if (lowerMessage.contains("not found")) {
            return "请求的资源不存在";
        } else {
            return "系统处理错误: " + message;
        }
    }

    /**
     * 获取SillyTavern相关的友好错误信息（中文）。
     *
     * @param message 原始错误信息
     * @return 用户可读的错误信息
     */
    private String getSillyTavernFriendlyMessage(String message) {
        if (message == null) {
            return "SillyTavern操作失败";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("container") && lowerMessage.contains("not found")) {
            return "容器不存在，请先创建容器";
        } else if (lowerMessage.contains("container") && lowerMessage.contains("not running")) {
            return "容器未运行，请先启动容器";
        } else if (lowerMessage.contains("docker") && lowerMessage.contains("command not found")) {
            return "Docker未安装或配置不正确";
        } else if (lowerMessage.contains("port") && lowerMessage.contains("already in use")) {
            return "端口已被占用，请更换其他端口";
        } else if (lowerMessage.contains("image") && lowerMessage.contains("not found")) {
            return "镜像不存在，请检查镜像名称";
        } else if (lowerMessage.contains("正在进行升级操作")) {
            return "容器正在升级中，请稍后再试";
        } else if (lowerMessage.contains("配置验证失败")) {
            return "配置参数验证失败，请检查输入的配置项";
        } else {
            return message;
        }
    }

    /**
     * 获取堆栈跟踪信息作为字符串（仅在开发/调试模式下）。
     * 生产环境建议返回null以避免泄露敏感信息。
     *
     * @param e 异常对象
     * @return 堆栈跟踪字符串，或null
     */
    private String getStackTraceAsString(Exception e) {
        if (log.isDebugEnabled()) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString();
        }
        return null;
    }
}
