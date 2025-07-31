package com.fufu.terminal.service;

import com.fufu.terminal.entity.ProjectConfig;
import com.fufu.terminal.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectConfigService {
    
    private final ProjectConfigRepository projectConfigRepository;

    /**
     * 获取所有项目配置
     */
    public List<ProjectConfig> findAll() {
        return projectConfigRepository.findAll();
    }

    /**
     * 根据项目类型获取配置
     */
    public List<ProjectConfig> findByProjectType(String projectType) {
        return projectConfigRepository.findByProjectTypeAndStatus(projectType, ProjectConfig.Status.ACTIVE);
    }

    /**
     * 根据ID获取配置
     */
    public ProjectConfig findById(Long id) {
        return projectConfigRepository.findById(id).orElse(null);
    }

    /**
     * 保存项目配置
     */
    public ProjectConfig save(ProjectConfig projectConfig) {
        return projectConfigRepository.save(projectConfig);
    }

    /**
     * 删除项目配置
     */
    public void deleteById(Long id) {
        projectConfigRepository.deleteById(id);
    }

    /**
     * 根据环境获取配置
     */
    public List<ProjectConfig> findByEnvironment(String environment) {
        return projectConfigRepository.findByEnvironmentAndStatus(environment, ProjectConfig.Status.ACTIVE);
    }
}