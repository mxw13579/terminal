package com.fufu.terminal.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 * <p>
 * 为 SillyTavern 数据导入提供文件上传、状态查询和临时文件删除接口。
 * </p>
 *
 * @author
 */
@Slf4j
@RestController
@RequestMapping("/api/sillytavern")
@RequiredArgsConstructor
public class FileUploadController {

    /**
     * 临时文件存储目录
     */
    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;

    /**
     * 最大上传文件大小（字节）
     */
    @Value("${sillytavern.data.max-upload-size:5368709120}") // 5GB
    private long maxUploadSize;

    /**
     * 处理文件上传请求，仅支持 ZIP 格式。
     *
     * @param file 上传的文件
     * @return 上传结果及相关信息
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        // 统一响应体
        Map<String, Object> response = new HashMap<>();

        // 1. 校验文件是否为空
        if (file.isEmpty()) {
            return buildErrorResponse("文件不能为空", response, 400);
        }

        // 2. 校验文件大小
        if (file.getSize() > maxUploadSize) {
            return buildErrorResponse("文件大小超过限制: " + formatFileSize(maxUploadSize), response, 400);
        }

        // 3. 校验文件类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            return buildErrorResponse("只支持ZIP文件格式", response, 400);
        }

        try {
            // 4. 创建临时目录（如不存在）
            Path tempDirPath = Paths.get(tempDirectory);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }

            // 5. 生成唯一文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String filename = String.format("sillytavern_import_%s_%s.zip", timestamp, uuid);
            Path filePath = tempDirPath.resolve(filename);

            // 6. 保存文件
            file.transferTo(filePath);

            log.info("文件上传成功: {} (原始名: {}, 大小: {} bytes)", filename, originalFilename, file.getSize());

            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("filename", filename);
            response.put("size", file.getSize());
            response.put("originalName", originalFilename);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return buildErrorResponse("文件上传失败: " + e.getMessage(), response, 500);
        }
    }

    /**
     * 获取当前上传配置及临时目录剩余空间信息。
     *
     * @return 上传状态信息
     */
    @GetMapping("/upload/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            Path tempDirPath = Paths.get(tempDirectory);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
            long freeSpace = Files.getFileStore(tempDirPath).getUsableSpace();

            response.put("success", true);
            response.put("maxUploadSize", maxUploadSize);
            response.put("maxUploadSizeFormatted", formatFileSize(maxUploadSize));
            response.put("availableSpace", freeSpace);
            response.put("availableSpaceFormatted", formatFileSize(freeSpace));
            response.put("tempDirectory", tempDirectory);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("获取上传状态失败: {}", e.getMessage(), e);
            return buildErrorResponse("获取上传状态失败: " + e.getMessage(), response, 500);
        }
    }

    /**
     * 删除指定的临时上传文件。
     *
     * @param filename 文件名
     * @return 删除结果
     */
    @DeleteMapping("/upload/{filename}")
    public ResponseEntity<Map<String, Object>> deleteUploadedFile(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();
        try {
            Path filePath = Paths.get(tempDirectory).resolve(filename);

            if (!Files.exists(filePath)) {
                return buildErrorResponse("文件不存在", response, 404);
            }

            Files.delete(filePath);
            log.info("临时文件已删除: {}", filename);

            response.put("success", true);
            response.put("message", "文件删除成功");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
            return buildErrorResponse("删除文件失败: " + e.getMessage(), response, 500);
        }
    }

    /**
     * 格式化文件大小为可读字符串。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    private String formatFileSize(long bytes) {
        if (bytes == 0) return "0 Bytes";
        String[] units = {"Bytes", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log(bytes) / Math.log(1024));
        double size = bytes / Math.pow(1024, unitIndex);
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 构建错误响应体并返回。
     *
     * @param message  错误信息
     * @param response 响应体 map
     * @param status   HTTP 状态码
     * @return ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, Map<String, Object> response, int status) {
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
