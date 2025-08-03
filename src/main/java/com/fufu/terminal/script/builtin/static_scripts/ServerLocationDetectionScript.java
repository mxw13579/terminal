package com.fufu.terminal.script.builtin.static_scripts;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.entity.enums.VariableScope;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.context.ExecutionContext;
import com.fufu.terminal.script.model.ScriptParameter;
import com.fufu.terminal.script.model.ScriptResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Server Location Detection Script
 * Static Built-in Script that automatically detects server geographic location
 * Outputs location variables for use by configurable scripts (mirror selection, etc.)
 */
@Component
@Slf4j
public class ServerLocationDetectionScript implements ExecutableScript {
    
    @Override
    public String getId() {
        return "server-location-detection";
    }
    
    @Override
    public String getName() {
        return "Server Location Detection";
    }
    
    @Override
    public String getDescription() {
        return "Automatically detect server geographic location for intelligent mirror optimization and regional configuration";
    }
    
    @Override
    public String getCategory() {
        return "System Information";
    }
    
    @Override
    public ScriptType getType() {
        return ScriptType.STATIC_BUILTIN;
    }
    
    @Override
    public List<ScriptParameter> getParameters() {
        return Collections.emptyList(); // Static scripts have no parameters
    }
    
    @Override
    public Set<String> getRequiredVariables() {
        return Collections.emptySet();
    }
    
    @Override
    public Set<String> getOutputVariables() {
        return Set.of("server_location", "server_country", "server_region", "server_ip", 
                     "server_timezone", "network_latency_china", "network_latency_us");
    }
    
    @Override
    public Optional<Integer> getEstimatedExecutionTime() {
        return Optional.of(15); // 15 seconds
    }
    
    @Override
    public Set<String> getTags() {
        return Set.of("location", "geographic", "detection", "network", "system");
    }
    
    @Override
    public CompletableFuture<ScriptResult> executeAsync(ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting server location detection for session: {}", context.getSessionId());
                
                long startTime = System.currentTimeMillis();
                
                // Step 1: Detect public IP
                String publicIp = detectPublicIp(context);
                log.debug("Detected public IP: {}", publicIp);
                
                // Step 2: Geolocate IP address
                LocationInfo location = geolocateIp(publicIp, context);
                log.debug("Detected location: {} ({})", location.getCountry(), location.getRegion());
                
                // Step 3: Perform network latency tests
                NetworkLatencyInfo latency = performLatencyTests(context);
                log.debug("Network latency - China: {}ms, US: {}ms", 
                    latency.getChinaLatency(), latency.getUsLatency());
                
                // Step 4: Detect timezone
                String timezone = detectTimezone(context);
                log.debug("Detected timezone: {}", timezone);
                
                // Combine results and set output variables
                Map<String, Object> outputs = new HashMap<>();
                outputs.put("server_location", location.getCountry());
                outputs.put("server_country", location.getCountry());
                outputs.put("server_region", location.getRegion());
                outputs.put("server_ip", publicIp);
                outputs.put("server_timezone", timezone);
                outputs.put("network_latency_china", latency.getChinaLatency());
                outputs.put("network_latency_us", latency.getUsLatency());
                
                // Set variables in session scope for use by other scripts
                context.setVariables(outputs, VariableScope.SESSION);
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                String summary = String.format("Server location detected: %s (%s), IP: %s, Timezone: %s", 
                    location.getCountry(), location.getRegion(), publicIp, timezone);
                
                return ScriptResult.builder()
                    .success(true)
                    .message(summary)
                    .outputVariables(outputs)
                    .executionTimeMs(executionTime)
                    .startTime(java.time.Instant.ofEpochMilli(startTime))
                    .endTime(java.time.Instant.now())
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .scriptVersion(getVersion())
                    .stdOut(generateLocationReport(location, latency, timezone))
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to detect server location for session: {}", context.getSessionId(), e);
                return ScriptResult.builder()
                    .success(false)
                    .errorMessage("Location detection failed: " + e.getMessage())
                    .errorCode("LOCATION_DETECTION_ERROR")
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .build();
            }
        });
    }
    
    /**
     * Detect public IP address using multiple services
     */
    private String detectPublicIp(ExecutionContext context) {
        String[] ipServices = {
            "curl -s --max-time 5 https://api.ipify.org",
            "curl -s --max-time 5 https://ifconfig.me",
            "curl -s --max-time 5 https://ipinfo.io/ip",
            "curl -s --max-time 5 https://icanhazip.com"
        };
        
        for (String service : ipServices) {
            try {
                ExecutionContext.CommandResult result = context.executeCommand(service, Duration.ofSeconds(10));
                if (result.getExitCode() == 0 && !result.getOutput().trim().isEmpty()) {
                    String ip = result.getOutput().trim();
                    if (isValidIpAddress(ip)) {
                        return ip;
                    }
                }
            } catch (Exception e) {
                log.debug("IP service failed: {}", service, e);
            }
        }
        
        throw new RuntimeException("Failed to detect public IP address from any service");
    }
    
    /**
     * Geolocate IP address
     */
    private LocationInfo geolocateIp(String ip, ExecutionContext context) {
        try {
            // Try multiple geolocation services
            String[] geoServices = {
                "curl -s --max-time 10 \"https://ipapi.co/" + ip + "/json\"",
                "curl -s --max-time 10 \"http://ip-api.com/json/" + ip + "\""
            };
            
            for (String service : geoServices) {
                try {
                    ExecutionContext.CommandResult result = context.executeCommand(service, Duration.ofSeconds(15));
                    if (result.getExitCode() == 0) {
                        return parseLocationResponse(result.getOutput());
                    }
                } catch (Exception e) {
                    log.debug("Geolocation service failed: {}", service, e);
                }
            }
            
            // Fallback: basic location detection based on IP ranges
            return detectLocationByIpRange(ip);
            
        } catch (Exception e) {
            log.warn("Geolocation failed, using fallback detection", e);
            return new LocationInfo("Unknown", "Unknown", "Unknown");
        }
    }
    
    /**
     * Perform network latency tests to China and US
     */
    private NetworkLatencyInfo performLatencyTests(ExecutionContext context) {
        try {
            // Test latency to Chinese servers
            int chinaLatency = testLatency(context, "baidu.com", 3);
            
            // Test latency to US servers  
            int usLatency = testLatency(context, "google.com", 3);
            
            return new NetworkLatencyInfo(chinaLatency, usLatency);
            
        } catch (Exception e) {
            log.warn("Latency tests failed", e);
            return new NetworkLatencyInfo(-1, -1); // -1 indicates failure
        }
    }
    
    /**
     * Test latency to a specific host
     */
    private int testLatency(ExecutionContext context, String host, int count) {
        try {
            String command = String.format("ping -c %d %s | tail -1 | awk -F'/' '{print $5}' | cut -d'.' -f1", count, host);
            ExecutionContext.CommandResult result = context.executeCommand(command, Duration.ofSeconds(30));
            
            if (result.getExitCode() == 0 && !result.getOutput().trim().isEmpty()) {
                try {
                    return Integer.parseInt(result.getOutput().trim());
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse ping result for {}: {}", host, result.getOutput());
                }
            }
        } catch (Exception e) {
            log.debug("Ping test failed for host: {}", host, e);
        }
        
        return -1; // Indicates failure
    }
    
    /**
     * Detect server timezone
     */
    private String detectTimezone(ExecutionContext context) {
        try {
            String[] timezoneCommands = {
                "timedatectl show --property=Timezone --value",
                "cat /etc/timezone",
                "date +%Z"
            };
            
            for (String command : timezoneCommands) {
                try {
                    ExecutionContext.CommandResult result = context.executeCommand(command, Duration.ofSeconds(5));
                    if (result.getExitCode() == 0 && !result.getOutput().trim().isEmpty()) {
                        return result.getOutput().trim();
                    }
                } catch (Exception e) {
                    log.debug("Timezone command failed: {}", command, e);
                }
            }
            
            return "UTC"; // Fallback
            
        } catch (Exception e) {
            log.warn("Timezone detection failed", e);
            return "UTC";
        }
    }
    
    /**
     * Parse location response from geolocation service
     */
    private LocationInfo parseLocationResponse(String response) {
        try {
            // Simple JSON parsing - in production, would use Jackson
            if (response.contains("\"country\"")) {
                String country = extractJsonValue(response, "country");
                String region = extractJsonValue(response, "region", "regionName");
                String city = extractJsonValue(response, "city");
                
                return new LocationInfo(country, region, city);
            }
        } catch (Exception e) {
            log.debug("Failed to parse location response: {}", response, e);
        }
        
        return new LocationInfo("Unknown", "Unknown", "Unknown");
    }
    
    /**
     * Simple JSON value extraction
     */
    private String extractJsonValue(String json, String... keys) {
        for (String key : keys) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "Unknown";
    }
    
    /**
     * Detect location by IP range (fallback method)
     */
    private LocationInfo detectLocationByIpRange(String ip) {
        // Simplified IP range detection for major regions
        String[] ipParts = ip.split("\\.");
        if (ipParts.length == 4) {
            try {
                int firstOctet = Integer.parseInt(ipParts[0]);
                
                // Simplified detection based on known IP ranges
                if (firstOctet >= 1 && firstOctet <= 126) {
                    return new LocationInfo("International", "Americas/Europe", "Unknown");
                } else if (firstOctet >= 128 && firstOctet <= 191) {
                    return new LocationInfo("International", "Global", "Unknown");
                } else {
                    return new LocationInfo("International", "Asia-Pacific", "Unknown");
                }
            } catch (NumberFormatException e) {
                log.debug("Invalid IP format: {}", ip);
            }
        }
        
        return new LocationInfo("Unknown", "Unknown", "Unknown");
    }
    
    /**
     * Validate IP address format
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Generate location report
     */
    private String generateLocationReport(LocationInfo location, NetworkLatencyInfo latency, String timezone) {
        StringBuilder report = new StringBuilder();
        report.append("=== Server Location Detection Report ===\n");
        report.append("Country: ").append(location.getCountry()).append("\n");
        report.append("Region: ").append(location.getRegion()).append("\n");
        report.append("City: ").append(location.getCity()).append("\n");
        report.append("Timezone: ").append(timezone).append("\n");
        report.append("\n=== Network Latency Tests ===\n");
        
        if (latency.getChinaLatency() > 0) {
            report.append("China latency: ").append(latency.getChinaLatency()).append("ms\n");
        } else {
            report.append("China latency: Failed to measure\n");
        }
        
        if (latency.getUsLatency() > 0) {
            report.append("US latency: ").append(latency.getUsLatency()).append("ms\n");
        } else {
            report.append("US latency: Failed to measure\n");
        }
        
        // Add recommendation
        report.append("\n=== Recommendations ===\n");
        if (latency.getChinaLatency() > 0 && latency.getUsLatency() > 0) {
            if (latency.getChinaLatency() < latency.getUsLatency()) {
                report.append("Recommend using Chinese mirror sources for better performance\n");
            } else {
                report.append("Recommend using international mirror sources for better performance\n");
            }
        } else {
            report.append("Based on location, automatic mirror selection will be applied\n");
        }
        
        return report.toString();
    }
    
    /**
     * Location information data class
     */
    private static class LocationInfo {
        private final String country;
        private final String region;
        private final String city;
        
        public LocationInfo(String country, String region, String city) {
            this.country = country;
            this.region = region;
            this.city = city;
        }
        
        public String getCountry() { return country; }
        public String getRegion() { return region; }
        public String getCity() { return city; }
    }
    
    /**
     * Network latency information data class
     */
    private static class NetworkLatencyInfo {
        private final int chinaLatency;
        private final int usLatency;
        
        public NetworkLatencyInfo(int chinaLatency, int usLatency) {
            this.chinaLatency = chinaLatency;
            this.usLatency = usLatency;
        }
        
        public int getChinaLatency() { return chinaLatency; }
        public int getUsLatency() { return usLatency; }
    }
}