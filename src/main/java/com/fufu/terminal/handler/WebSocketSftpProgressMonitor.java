package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.SftpProgressMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * 进度监控器
 * @author lizelin
 */
@Slf4j
public class WebSocketSftpProgressMonitor implements SftpProgressMonitor {
    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private long totalSize;
    private long transferred = 0;
    private long lastTime;
    /**
     * 记录上一次发送的百分比
    */
    private int lastPercent = -1;
    private long lastTransferred = 0;
    private boolean initialized = false;


    public WebSocketSftpProgressMonitor(WebSocketSession session, ObjectMapper objectMapper) {
        this.session = session;
        this.objectMapper = objectMapper;
    }

    @Override
    public void init(int op, String src, String dest, long max) {
        if (initialized || max == -1) {
            log.warn("SFTP monitor init called again or with invalid max size, ignoring. (max={})", max);
            return;
        }

        this.totalSize = max;
        this.lastTime = System.currentTimeMillis();
        this.initialized = true;

        log.info("SFTP start. op={}, src={}, dest={}, max={}", op, src, dest, max);
        if (this.totalSize > 0) {
            sendProgressUpdate(0, 0);
        }
    }

    @Override
    public boolean count(long count) {
        transferred += count;
        long currentTime = System.currentTimeMillis();

        // 避免 totalSize 为0时除零错误
        if (totalSize <= 0) return session.isOpen();

        int currentPercent = (int) (((double) transferred / totalSize) * 100);
        if (currentPercent > lastPercent) {
            long elapsedTime = currentTime - lastTime;
            // 计算自上次更新以来的增量和速度
            long bytesSinceLastUpdate = transferred - lastTransferred;
            double speed = (elapsedTime > 0) ? (double) bytesSinceLastUpdate / (elapsedTime / 1000.0) : 0;

            sendProgressUpdate(currentPercent, speed);

            // 更新时间和已传输字节数记录
            this.lastTime = currentTime;
            this.lastTransferred = transferred;
        }

        return session.isOpen();
    }

    @Override
    public void end() {
        // --- 修改：确保最后发送一个100%的状态 ---
        if (lastPercent < 100 && totalSize > 0) {
            sendProgressUpdate(100, 0);
        }
        log.info("SFTP end. Total transferred: {}", transferred);
    }

    private void sendProgressUpdate(int percent, double speed) {
        if (percent <= lastPercent && percent < 100) return;

        Map<String, Object> progressUpdate = Map.of(
                "type", "sftp_remote_progress",
                "progress", percent,
                "speed", speed
        );
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(progressUpdate)));
                // 更新最后发送的百分比
                this.lastPercent = percent;
            }
        } catch (IOException e) {
            log.error("Failed to send SFTP progress update", e);
        }
    }
}
