package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.ProjectConfig;
import com.fufu.terminal.service.ProjectConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/project-configs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminProjectConfigController {
    
    private final ProjectConfigService projectConfigService;

    /**
     * 获取所有项目配置
     */
    @GetMapping
    public ResponseEntity<List<ProjectConfig>> getAllProjectConfigs() {
        try {
            List<ProjectConfig> configs = projectConfigService.findAll();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("获取项目配置列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据项目类型获取配置
     */
    @GetMapping("/type/{projectType}")
    public ResponseEntity<List<ProjectConfig>> getConfigsByType(@PathVariable String projectType) {
        try {
            List<ProjectConfig> configs = projectConfigService.findByProjectType(projectType);
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("根据类型获取项目配置失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取项目配置详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectConfig> getProjectConfig(@PathVariable Long id) {
        try {
            ProjectConfig config = projectConfigService.findById(id);
            if (config == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("获取项目配置详情失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建项目配置
     */
    @PostMapping
    public ResponseEntity<ProjectConfig> createProjectConfig(@RequestBody ProjectConfig projectConfig) {
        try {
            ProjectConfig savedConfig = projectConfigService.save(projectConfig);
            return ResponseEntity.ok(savedConfig);
        } catch (Exception e) {
            log.error("创建项目配置失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新项目配置
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectConfig> updateProjectConfig(
            @PathVariable Long id, 
            @RequestBody ProjectConfig projectConfig) {
        try {
            projectConfig.setId(id);
            ProjectConfig updatedConfig = projectConfigService.save(projectConfig);
            return ResponseEntity.ok(updatedConfig);
        } catch (Exception e) {
            log.error("更新项目配置失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除项目配置
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProjectConfig(@PathVariable Long id) {
        try {
            projectConfigService.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除项目配置失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}