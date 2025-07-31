package com.fufu.terminal.controller.user;

import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.command.util.SshConnectionUtil;
import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * SSH连接测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ssh")
@RequiredArgsConstructor
public class SshConnectionController {
    
    /**
     * 测试SSH连接
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody SshConnectionConfig connectionConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证配置
            if (!connectionConfig.isValid()) {
                result.put("success", false);
                result.put("message", "SSH配置信息不完整");
                return ResponseEntity.badRequest().body(result);
            }
            
            log.info("测试SSH连接: {}@{}:{}", connectionConfig.getUsername(), 
                connectionConfig.getHost(), connectionConfig.getPort());
            
            // 尝试建立连接
            boolean isConnected = SshConnectionUtil.testConnection(connectionConfig);
            
            if (isConnected) {
                result.put("success", true);
                result.put("message", "SSH连接测试成功");
                
                log.info("SSH连接测试成功: {}@{}", connectionConfig.getUsername(), connectionConfig.getHost());
                return ResponseEntity.ok(result);
                
            } else {
                result.put("success", false);
                result.put("message", "无法建立SSH连接");
                return ResponseEntity.ok(result);
            }
            
        } catch (Exception e) {
            log.error("SSH连接测试失败", e);
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}