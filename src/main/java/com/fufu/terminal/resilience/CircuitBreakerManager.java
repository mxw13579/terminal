package com.fufu.terminal.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit Breaker Manager for resilient SSH connections
 * 
 * Manages circuit breakers for SSH connections to prevent cascading failures
 * and provide automatic recovery mechanisms.
 */
@Component
@Slf4j
public class CircuitBreakerManager {
    
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    /**
     * Get or create a circuit breaker for the given name
     */
    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakers.computeIfAbsent(name, this::createCircuitBreaker);
    }
    
    /**
     * Create a new circuit breaker with default configuration
     */
    private CircuitBreaker createCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // 50% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30 seconds before retry
            .slidingWindowSize(10) // Consider last 10 calls
            .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
            .slowCallRateThreshold(80) // 80% slow call rate threshold
            .slowCallDurationThreshold(Duration.ofSeconds(10)) // Consider calls > 10s as slow
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                // These exceptions should be recorded as failures
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                com.jcraft.jsch.JSchException.class
            )
            .ignoreExceptions(
                // These exceptions should not trigger circuit breaker
                IllegalArgumentException.class,
                SecurityException.class
            )
            .build();
            
        CircuitBreaker circuitBreaker = CircuitBreaker.of(name, config);
        
        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Circuit breaker '{}' transitioned from {} to {}", 
                    name, event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState()));
        
        circuitBreaker.getEventPublisher()
            .onCallNotPermitted(event -> 
                log.warn("Circuit breaker '{}' rejected call - current state: {}", 
                    name, circuitBreaker.getState()));
        
        circuitBreaker.getEventPublisher()
            .onFailureRateExceeded(event ->
                log.warn("Circuit breaker '{}' failure rate exceeded: {}%", 
                    name, event.getFailureRate()));
        
        circuitBreaker.getEventPublisher()
            .onSlowCallRateExceeded(event ->
                log.warn("Circuit breaker '{}' slow call rate exceeded: {}%", 
                    name, event.getSlowCallRate()));
        
        log.info("Created circuit breaker '{}' with config: failureRateThreshold={}%, " +
                "waitDuration={}s, slidingWindowSize={}", 
                name, config.getFailureRateThreshold(), 
                config.getWaitDurationInOpenState().getSeconds(),
                config.getSlidingWindowSize());
        
        return circuitBreaker;
    }
    
    /**
     * Get circuit breaker state information
     */
    public CircuitBreakerState getState(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb == null) {
            return CircuitBreakerState.NOT_FOUND;
        }
        
        return switch (cb.getState()) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            case METRICS_ONLY -> CircuitBreakerState.METRICS_ONLY;
        };
    }
    
    /**
     * Get circuit breaker metrics
     */
    public CircuitBreakerMetrics getMetrics(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb == null) {
            return null;
        }
        
        var metrics = cb.getMetrics();
        return CircuitBreakerMetrics.builder()
            .name(name)
            .state(getState(name))
            .failureRate(metrics.getFailureRate())
            .slowCallRate(metrics.getSlowCallRate())
            .numberOfCalls(metrics.getNumberOfCalls())
            .numberOfFailedCalls(metrics.getNumberOfFailedCalls())
            .numberOfSlowCalls(metrics.getNumberOfSlowCalls())
            .numberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls())
            .build();
    }
    
    /**
     * Reset a circuit breaker to CLOSED state
     */
    public void reset(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb != null) {
            cb.reset();
            log.info("Reset circuit breaker '{}'", name);
        }
    }
    
    /**
     * Force a circuit breaker to OPEN state
     */
    public void forceOpen(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb != null) {
            cb.transitionToForcedOpenState();
            log.info("Forced circuit breaker '{}' to OPEN state", name);
        }
    }
    
    /**
     * Disable a circuit breaker
     */
    public void disable(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb != null) {
            cb.transitionToDisabledState();
            log.info("Disabled circuit breaker '{}'", name);
        }
    }
    
    /**
     * Get all circuit breaker names
     */
    public java.util.Set<String> getCircuitBreakerNames() {
        return circuitBreakers.keySet();
    }
    
    /**
     * Circuit Breaker State Enumeration
     */
    public enum CircuitBreakerState {
        CLOSED,
        OPEN,
        HALF_OPEN,
        DISABLED,
        FORCED_OPEN,
        METRICS_ONLY,
        NOT_FOUND
    }
    
    /**
     * Circuit Breaker Metrics
     */
    @lombok.Data
    @lombok.Builder
    public static class CircuitBreakerMetrics {
        private String name;
        private CircuitBreakerState state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfCalls;
        private int numberOfFailedCalls;
        private int numberOfSlowCalls;
        private int numberOfSuccessfulCalls;
    }
}