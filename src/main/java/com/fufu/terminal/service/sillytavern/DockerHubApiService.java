package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Docker Hub API服务
 *
 * @author lizelin
 */
@Slf4j
@Service
public class DockerHubApiService {

    // 默认的 Docker Hub API 响应数据，用于在 API 请求失败时作为回退。
    private static final String DEFAULT_DOCKER_HUB_RESPONSE_JSON = """
            {
              "results": [
                {"name": "stable", "last_updated": "2025-07-29T21:57:00.064537Z", "full_size": 201934172},
                {"name": "1.13.2", "last_updated": "2025-07-29T21:54:39.607579Z", "full_size": 201934172},
                {"name": "1.13.1", "last_updated": "2025-06-24T18:55:06.901868Z", "full_size": 200996293},
                {"name": "1.13.0", "last_updated": "2025-06-09T21:07:34.761786Z", "full_size": 198186870},
                {"name": "1.12.14", "last_updated": "2025-05-02T18:14:11.162645Z", "full_size": 204249032}
              ]
            }""";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DockerHubApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
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
     * 获取指定 Docker 仓库的多个版本信息。
     * <p>
     * 该方法会尝试从 Docker Hub API 获取数据。如果请求失败（例如网络问题、超时或服务器错误），
     * 它将使用预定义的默认 JSON 数据作为回退，以确保总能返回有效的版本列表。
     *
     * @param repository 仓库名称 (例如: "goolashe/sillytavern")
     * @param pageSize   要获取的版本数量
     * @return 版本信息 DTO 列表
     */
    public List<DockerHubVersionDto> getLatestVersions(String repository, int pageSize) {
        try {
            log.info("开始获取 Docker Hub 版本信息: {}, 数量: {}", repository, pageSize);
            String url = String.format("https://hub.docker.com/v2/repositories/%s/tags/?page_size=%d", repository, pageSize);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SillyTavern-Manager/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("成功从 Docker Hub API 获取版本信息。");
                return parseResponse(response.body());
            } else {
                log.warn("Docker Hub API 返回错误状态码: {}。将使用默认数据。", response.statusCode());
                return parseResponse(DEFAULT_DOCKER_HUB_RESPONSE_JSON);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("获取 Docker Hub 版本信息时发生网络错误或中断，将使用默认数据。");
            return parseResponse(DEFAULT_DOCKER_HUB_RESPONSE_JSON);
        }
    }

    /**
     * 解析包含版本信息的 JSON 字符串，并将其转换为 DTO 列表。
     *
     * @param jsonResponse JSON 格式的字符串
     * @return 版本信息 DTO 列表
     */
    private List<DockerHubVersionDto> parseResponse(String jsonResponse) {
        try {
            DockerHubResponse response = objectMapper.readValue(jsonResponse, DockerHubResponse.class);
            return Optional.ofNullable(response.results())
                    .map(List::stream)
                    .orElseGet(Stream::empty)
                    .map(this::convertToDto)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("解析 JSON 响应失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将从 API 获取的单个标签结果转换为应用程序内部使用的 DTO。
     *
     * @param tagResult 从 API 获取的原始标签数据
     * @return 转换后的版本信息 DTO
     */
    private DockerHubVersionDto convertToDto(TagResult tagResult) {
        if (tagResult == null || tagResult.name() == null) {
            return null;
        }
        String formattedSize = formatBytes(tagResult.fullSize());
        String lastPushedFormatted = Optional.ofNullable(tagResult.lastUpdated())
                .map(date -> date.format(DATE_TIME_FORMATTER))
                .orElse("未知");
        return DockerHubVersionDto.builder()
                .tagName(tagResult.name())
                .imageSizeBytes(tagResult.fullSize())
                .imageSize(formattedSize)
                .lastPushed(Optional.ofNullable(tagResult.lastUpdated()).map(OffsetDateTime::toLocalDateTime).orElse(null))
                .lastPushedFormatted(lastPushedFormatted)
                .isLatest("latest".equalsIgnoreCase(tagResult.name()))
                .build();
    }

    /**
     * 仓库可访问性探测（指定超时时间，单位秒）
     */
    private boolean isRepositoryAccessibleWithTimeout(String repository, int timeoutSeconds) {
        try {
            String url = String.format("https://hub.docker.com/v2/repositories/%s/", repository);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("快速仓库可达性检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 使用默认JSON填充版本列表
     */
    private void parseDefaultJson(List<DockerHubVersionDto> versions, String defaultJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(defaultJson);
            JsonNode resultsNode = rootNode.get("results");
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode tagNode : resultsNode) {
                    DockerHubVersionDto version = parseVersionInfo(tagNode);
                    if (version != null) {
                        versions.add(version);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("默认JSON解析失败: {}", ex.getMessage(), ex);
        }
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
     * 将字节大小格式化为更易读的字符串 (B, KB, MB, GB)。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
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

    /**
     * 用于映射 Docker Hub API 响应的顶层结构。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DockerHubResponse(List<TagResult> results) {
    }

    /**
     * 用于映射 'results' 数组中的每个标签对象。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TagResult(
            String name,
            @JsonProperty("last_updated") OffsetDateTime lastUpdated,
            @JsonProperty("full_size") long fullSize
    ) {
    }
}
