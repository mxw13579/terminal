package com.fufu.terminal.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.fufu.terminal.entity.User;
import com.fufu.terminal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {
    
    private final UserService userService;
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        // 验证用户名密码
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 检查用户状态
        if (user.getStatus() != User.Status.ACTIVE) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "账户已被禁用");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 检查用户权限（只允许管理员登录管理端）
        if (user.getRole() != User.Role.ADMIN) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "权限不足");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 登录
        StpUtil.login(user.getId());
        
        // 返回用户信息和token
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "登录成功");
        result.put("token", StpUtil.getTokenValue());
        result.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole().name()
        ));
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        StpUtil.logout();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "退出成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.findById(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole().name()
        ));
        
        return ResponseEntity.ok(result);
    }
    
    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}