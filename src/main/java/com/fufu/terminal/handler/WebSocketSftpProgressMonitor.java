package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.SftpProgressMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket SFTP进度监控器，实现{@link SftpProgressMonitor}接口。
 * <p>
 * 该监控器用于在SFTP文件传输过程中，通过WebSocket实时推送进度信息到前端。
 * </p>
 * <ul>
 *     <li>支持百分比进度与实时速度推送</li>
 *     <li>自动处理WebSocket连接状态</li>
 *     <li>支持多次初始化保护</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
public class WebSocketSftpProgressMonitor implements SftpProgressMonitor {
    /** WebSocket会话 */
    private final WebSocketSession session;
    /** JSON对象映射器 */
    private final ObjectMapper objectMapper;
    /** 文件总大小（字节） */
    private long totalSize;
    /** 已传输字节数 */
    private long transferred = 0;
    /** 上次发送进度的时间戳 */
    private long lastTime;
    /** 上次发送的进度百分比 */
    private int lastPercent = -1;
    /** 上次发送时已传输字节数 */
    private long lastTransferred = 0;
    /** 是否已初始化 */
    private boolean initialized = false;

    /**
     * 构造WebSocket SFTP进度监控器。
     *
     * @param session      WebSocket会话
     * @param objectMapper JSON对象映射器
     */
    public WebSocketSftpProgressMonitor(WebSocketSession session, ObjectMapper objectMapper) {
        this.session = session;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化进度监控器。
     *
     * @param op   操作类型
     * @param src  源文件路径
     * @param dest 目标文件路径
     * @param max  文件总大小（字节），-1表示未知
     */
    @Override
    public void init(int op, String src, String dest, long max) {
        if (initialized || max == -1) {
            log.warn("SFTP进度监控器已初始化或文件大小无效，忽略本次初始化 (max={})", max);
            return;
        }
        this.totalSize = max;
        this.lastTime = System.currentTimeMillis();
        this.initialized = true;

        log.info("SFTP传输开始. op={}, src={}, dest={}, max={}", op, src, dest, max);
        if (this.totalSize > 0) {
            sendProgressUpdate(0, 0);
        }
    }

    /**
     * 统计已传输的字节数，并推送进度更新。
     *
     * @param count 本次传输的字节数
     * @return WebSocket会话是否仍然打开
     */
    @Override
    public boolean count(long count) {
        transferred += count;
        long currentTime = System.currentTimeMillis();

        // 避免 totalSize 为0时除零错误
        if (totalSize <= 0) return session.isOpen();

        int currentPercent = (int) (((double) transferred / totalSize) * 100);
        if (currentPercent > lastPercent) {
            long elapsedTime = currentTime - lastTime;
            long bytesSinceLastUpdate = transferred - lastTransferred;
            double speed = (elapsedTime > 0) ? (double) bytesSinceLastUpdate / (elapsedTime / 1000.0) : 0;

            sendProgressUpdate(currentPercent, speed);

            this.lastTime = currentTime;
            this.lastTransferred = transferred;
        }

        return session.isOpen();
    }

    /**
     * 传输结束时调用，确保发送100%进度。
     */
    @Override
    public void end() {
        // 确保最后发送一个100%的状态
        if (lastPercent < 100 && totalSize > 0) {
            sendProgressUpdate(100, 0);
        }
        log.info("SFTP传输结束. 总共传输字节数: {}", transferred);
    }

    /**
     * 发送进度更新到WebSocket客户端。
     *
     * @param percent 当前进度百分比
     * @param speed   当前传输速度（字节/秒）
     */
    private void sendProgressUpdate(int percent, double speed) {
        // 仅在进度提升或达到100%时发送
        if (percent <= lastPercent && percent < 100) return;

        Map<String, Object> progressUpdate = new HashMap<>();
        progressUpdate.put("type", "sftp_remote_progress");
        progressUpdate.put("progress", percent);
        progressUpdate.put("speed", speed);

        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(progressUpdate)));
                this.lastPercent = percent;
            }
        } catch (IOException e) {
            log.error("发送SFTP进度更新失败", e);
        }
    }
}
