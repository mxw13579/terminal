package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.DockerHubVersionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Docker Hub API服务
 *
 * @author lizelin
 */
@Slf4j
@Service
public class DockerHubApiService {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public DockerHubApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 获取指定仓库的最新5个版本信息
     *
     * @param repository 仓库名称（如：goolashe/sillytavern）
     * @return 版本信息列表
     */
    public List<DockerHubVersionDto> getLatestVersions(String repository) {
        return getLatestVersions(repository, 5);
    }
    
    /**
     * 获取指定仓库的版本信息
     *
     * @param repository 仓库名称（如：goolashe/sillytavern）
     * @param pageSize 获取版本数量
     * @return 版本信息列表
     */
    public List<DockerHubVersionDto> getLatestVersions(String repository, int pageSize) {
        List<DockerHubVersionDto> versions = new ArrayList<>();
        
        try {
            log.info("开始获取Docker Hub版本信息: {}", repository);
            
            String url = String.format("https://hub.docker.com/v2/repositories/%s/tags/?page_size=%d", 
                                     repository, pageSize);
            
            log.debug("请求URL: {}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SillyTavern-Manager/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                    
            log.debug("发送HTTP请求...");
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            log.debug("收到响应，状态码: {}", response.statusCode());
                    
            if (response.statusCode() == 200) {
                log.debug("解析响应数据...");
                JsonNode rootNode = objectMapper.readTree(response.body());
                JsonNode resultsNode = rootNode.get("results");
                
                if (resultsNode != null && resultsNode.isArray()) {
                    log.debug("找到 {} 个标签", resultsNode.size());
                    
                    for (JsonNode tagNode : resultsNode) {
                        DockerHubVersionDto version = parseVersionInfo(tagNode);
                        if (version != null) {
                            versions.add(version);
                            log.debug("解析版本: {}", version.getTagName());
                        }
                    }
                } else {
                    log.warn("响应中未找到results节点或不是数组");
                }
                
                log.info("成功获取 {} 个版本信息", versions.size());
            } else {
                String errorMsg = String.format("Docker Hub API返回错误状态码 %d", response.statusCode());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (IOException e) {
            String errorMsg = "网络连接失败: " + e.getMessage();
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (InterruptedException e) {
            String errorMsg = "请求被中断: " + e.getMessage();
            log.error(errorMsg, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "获取版本信息时发生未知错误: " + e.getMessage();
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
        
        return versions;
    }
    
    /**
     * 解析单个标签的版本信息
     */
    private DockerHubVersionDto parseVersionInfo(JsonNode tagNode) {
        try {
            String tagName = tagNode.get("name").asText();
            String lastUpdated = tagNode.get("last_updated").asText();
            
            log.debug("解析标签: {}, 最后更新: {}", tagName, lastUpdated);
            
            // 解析镜像大小 - 使用full_size字段
            long totalSize = 0;
            String formattedSize = "未知";
            
            // 先尝试从full_size获取
            if (tagNode.has("full_size")) {
                totalSize = tagNode.get("full_size").asLong();
                formattedSize = formatBytes(totalSize);
                log.debug("从full_size获取大小: {} bytes", totalSize);
            } else {
                // 备选方案：从images数组获取
                JsonNode imagesNode = tagNode.get("images");
                if (imagesNode != null && imagesNode.isArray() && imagesNode.size() > 0) {
                    JsonNode firstImage = imagesNode.get(0);
                    if (firstImage.has("size")) {
                        totalSize = firstImage.get("size").asLong();
                        formattedSize = formatBytes(totalSize);
                        log.debug("从images[0].size获取大小: {} bytes", totalSize);
                    }
                }
            }
            
            // 解析时间
            LocalDateTime lastPushed = null;
            String lastPushedFormatted = "未知";
            if (lastUpdated != null && !lastUpdated.isEmpty()) {
                try {
                    // Docker Hub时间格式：2025-07-29T21:57:00.064537Z
                    String timeStr = lastUpdated;
                    if (timeStr.endsWith("Z")) {
                        timeStr = timeStr.substring(0, timeStr.length() - 1);
                    }
                    
                    // 处理不同的微秒精度
                    if (timeStr.contains(".")) {
                        String[] parts = timeStr.split("\\.");
                        if (parts.length == 2) {
                            String microseconds = parts[1];
                            // 截断或填充到6位
                            if (microseconds.length() > 6) {
                                microseconds = microseconds.substring(0, 6);
                            } else if (microseconds.length() < 6) {
                                microseconds = String.format("%-6s", microseconds).replace(' ', '0');
                            }
                            timeStr = parts[0] + "." + microseconds;
                        }
                    }
                    
                    lastPushed = LocalDateTime.parse(timeStr, 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));
                    lastPushedFormatted = lastPushed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    log.debug("解析时间成功: {}", lastPushedFormatted);
                } catch (Exception e) {
                    log.warn("时间解析失败 '{}': {}", lastUpdated, e.getMessage());
                    // 备选方案：简单格式化
                    if (lastUpdated.length() >= 10) {
                        lastPushedFormatted = lastUpdated.substring(0, 10) + " " + lastUpdated.substring(11, 16);
                    }
                }
            }
            
            boolean isLatest = "latest".equals(tagName);
            
            DockerHubVersionDto version = DockerHubVersionDto.builder()
                    .tagName(tagName)
                    .imageSizeBytes(totalSize)
                    .imageSize(formattedSize)
                    .lastPushed(lastPushed)
                    .lastPushedFormatted(lastPushedFormatted)
                    .isLatest(isLatest)
                    .build();
                    
            log.debug("版本信息解析完成: {} - {} - {}", tagName, formattedSize, lastPushedFormatted);
            return version;
                    
        } catch (Exception e) {
            log.warn("解析标签信息失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 检查仓库是否可访问
     */
    public boolean isRepositoryAccessible(String repository) {
        try {
            String url = String.format("https://hub.docker.com/v2/repositories/%s/", repository);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                    
            return response.statusCode() == 200;
            
        } catch (Exception e) {
            log.warn("Failed to check repository accessibility: {}", e.getMessage());
            return false;
        }
    }
}