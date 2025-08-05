package com.fufu.terminal.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 * 为SillyTavern数据导入提供文件上传支持
 */
@Slf4j
@RestController
@RequestMapping("/api/sillytavern")
@RequiredArgsConstructor
public class FileUploadController {
    
    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;
    
    @Value("${sillytavern.data.max-upload-size:5368709120}") // 5GB
    private long maxUploadSize;
    
    /**
     * 处理文件上传请求
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 验证文件大小
            if (file.getSize() > maxUploadSize) {
                response.put("success", false);
                response.put("message", "文件大小超过限制: " + formatFileSize(maxUploadSize));
                return ResponseEntity.badRequest().body(response);
            }
            
            // 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
                response.put("success", false);
                response.put("message", "只支持ZIP文件格式");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 创建临时目录
            Path tempDirPath = Paths.get(tempDirectory);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
            
            // 生成唯一文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "sillytavern_import_" + timestamp + "_" + originalFilename;
            Path filePath = tempDirPath.resolve(filename);
            
            // 保存文件
            file.transferTo(filePath.toFile());
            
            log.info("文件上传成功: {} (大小: {} bytes)", filename, file.getSize());
            
            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("filename", filename);
            response.put("size", file.getSize());
            response.put("originalName", originalFilename);
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取文件上传状态
     */
    @GetMapping("/upload/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path tempDirPath = Paths.get(tempDirectory);
            long freeSpace = Files.getFileStore(tempDirPath).getUsableSpace();
            
            response.put("success", true);
            response.put("maxUploadSize", maxUploadSize);
            response.put("maxUploadSizeFormatted", formatFileSize(maxUploadSize));
            response.put("availableSpace", freeSpace);
            response.put("availableSpaceFormatted", formatFileSize(freeSpace));
            response.put("tempDirectory", tempDirectory);
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("获取上传状态失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取上传状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 删除临时文件
     */
    @DeleteMapping("/upload/{filename}")
    public ResponseEntity<Map<String, Object>> deleteUploadedFile(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path filePath = Paths.get(tempDirectory, filename);
            
            if (!Files.exists(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.notFound().build();
            }
            
            Files.delete(filePath);
            log.info("临时文件已删除: {}", filename);
            
            response.put("success", true);
            response.put("message", "文件删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("删除文件失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除文件失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes == 0) return "0 Bytes";
        
        String[] units = {"Bytes", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log(bytes) / Math.log(1024));
        double size = bytes / Math.pow(1024, unitIndex);
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
}