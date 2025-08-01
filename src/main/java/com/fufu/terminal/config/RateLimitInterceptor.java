package com.fufu.terminal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流拦截器
 * 实现基于IP和用户的请求限流
 * @author lizelin
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Value("${app.security.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;
    
    @Value("${app.security.rate-limit.requests-per-hour:1000}")
    private int requestsPerHour;
    
    @Value("${app.security.rate-limit.burst-size:10}")
    private int burstSize;
    
    // IP级别的限流统计
    private final ConcurrentHashMap<String, RateLimitInfo> ipRateLimits = new ConcurrentHashMap<>();
    
    // 用户级别的限流统计
    private final ConcurrentHashMap<String, RateLimitInfo> userRateLimits = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String clientIp = getClientIp(request);
            String userId = getUserId(request);
            
            // IP级别限流检查
            if (!checkRateLimit(clientIp, ipRateLimits, "IP: " + clientIp)) {
                response.setStatus(429); // Too Many Requests
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"code\":429}");
                return false;
            }
            
            // 用户级别限流检查（如果已登录）
            if (userId != null && !checkRateLimit(userId, userRateLimits, "User: " + userId)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"error\":\"User rate limit exceeded\",\"code\":429}");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error in rate limiting", e);
            return true; // 出错时不阻止请求
        }
    }

    private boolean checkRateLimit(String key, ConcurrentHashMap<String, RateLimitInfo> rateLimits, String identifier) {
        long currentTime = System.currentTimeMillis();
        
        RateLimitInfo info = rateLimits.computeIfAbsent(key, k -> new RateLimitInfo(currentTime));
        
        // 清理过期的计数器
        info.cleanup(currentTime);
        
        // 检查分钟级限流
        if (info.getMinuteCount(currentTime) >= requestsPerMinute) {
            log.warn("Rate limit exceeded for {} - minute limit", identifier);
            return false;
        }
        
        // 检查小时级限流
        if (info.getHourCount(currentTime) >= requestsPerHour) {
            log.warn("Rate limit exceeded for {} - hour limit", identifier);
            return false;
        }
        
        // 检查突发限流
        if (info.getBurstCount() >= burstSize) {
            log.warn("Rate limit exceeded for {} - burst limit", identifier);
            return false;
        }
        
        // 记录请求
        info.recordRequest(currentTime);
        
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String getUserId(HttpServletRequest request) {
        // 尝试从请求头或session中获取用户ID
        String userId = request.getHeader("X-User-ID");
        if (userId == null) {
            Object sessionUserId = request.getSession(false) != null ? 
                request.getSession().getAttribute("userId") : null;
            if (sessionUserId != null) {
                userId = sessionUserId.toString();
            }
        }
        return userId;
    }

    /**
     * 限流信息类
     */
    private static class RateLimitInfo {
        private final AtomicLong lastResetTime;
        private final AtomicInteger minuteCount;
        private final AtomicInteger hourCount;
        private final AtomicInteger burstCount;
        private final AtomicLong lastBurstTime;
        
        public RateLimitInfo(long currentTime) {
            this.lastResetTime = new AtomicLong(currentTime);
            this.minuteCount = new AtomicInteger(0);
            this.hourCount = new AtomicInteger(0);
            this.burstCount = new AtomicInteger(0);
            this.lastBurstTime = new AtomicLong(currentTime);
        }
        
        public void cleanup(long currentTime) {
            long lastReset = lastResetTime.get();
            
            // 每分钟重置分钟计数器
            if (currentTime - lastReset > 60_000) {
                minuteCount.set(0);
                lastResetTime.set(currentTime);
            }
            
            // 每小时重置小时计数器
            if (currentTime - lastReset > 3600_000) {
                hourCount.set(0);
            }
            
            // 重置突发计数器（每10秒）
            if (currentTime - lastBurstTime.get() > 10_000) {
                burstCount.set(0);
                lastBurstTime.set(currentTime);
            }
        }
        
        public int getMinuteCount(long currentTime) {
            cleanup(currentTime);
            return minuteCount.get();
        }
        
        public int getHourCount(long currentTime) {
            cleanup(currentTime);
            return hourCount.get();
        }
        
        public int getBurstCount() {
            return burstCount.get();
        }
        
        public void recordRequest(long currentTime) {
            minuteCount.incrementAndGet();
            hourCount.incrementAndGet();
            burstCount.incrementAndGet();
        }
    }
}