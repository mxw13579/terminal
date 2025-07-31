package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, Long> {
    
    List<ProjectConfig> findByProjectTypeAndStatus(String projectType, ProjectConfig.Status status);
    
    List<ProjectConfig> findByStatus(ProjectConfig.Status status);
    
    List<ProjectConfig> findByEnvironmentAndStatus(String environment, ProjectConfig.Status status);
}