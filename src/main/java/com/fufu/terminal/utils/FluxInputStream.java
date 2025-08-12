package com.fufu.terminal.utils;

import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将Flux<byte[]>转换为InputStream的适配器
 * 用于Spring Boot的Resource响应，避免反应式流的字符编码问题
 * 
 * @author lizelin
 */
public class FluxInputStream extends InputStream {

    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean errored = new AtomicBoolean(false);
    private volatile Throwable error;
    
    private byte[] currentChunk;
    private int currentPosition = 0;
    
    public FluxInputStream(Flux<byte[]> flux) {
        flux.subscribe(
            chunk -> {
                if (chunk != null && chunk.length > 0) {
                    queue.offer(chunk);
                }
            },
            throwable -> {
                error = throwable;
                errored.set(true);
            },
            () -> completed.set(true)
        );
    }
    
    @Override
    public int read() throws IOException {
        if (currentChunk == null || currentPosition >= currentChunk.length) {
            if (!loadNextChunk()) {
                return -1; // EOF
            }
        }
        
        return currentChunk[currentPosition++] & 0xFF;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        
        int totalRead = 0;
        
        while (totalRead < len) {
            if (currentChunk == null || currentPosition >= currentChunk.length) {
                if (!loadNextChunk()) {
                    return totalRead > 0 ? totalRead : -1; // EOF
                }
            }
            
            int available = currentChunk.length - currentPosition;
            int toRead = Math.min(available, len - totalRead);
            
            System.arraycopy(currentChunk, currentPosition, b, off + totalRead, toRead);
            currentPosition += toRead;
            totalRead += toRead;
        }
        
        return totalRead;
    }
    
    private boolean loadNextChunk() throws IOException {
        if (errored.get()) {
            throw new IOException("Flux stream error", error);
        }
        
        while (true) {
            byte[] chunk = queue.poll();
            if (chunk != null) {
                currentChunk = chunk;
                currentPosition = 0;
                return true;
            }
            
            if (completed.get() && queue.isEmpty()) {
                return false; // EOF
            }
            
            if (errored.get()) {
                throw new IOException("Flux stream error", error);
            }
            
            // 等待更多数据
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for data", e);
            }
        }
    }
    
    @Override
    public void close() throws IOException {
        currentChunk = null;
        queue.clear();
        super.close();
    }
}