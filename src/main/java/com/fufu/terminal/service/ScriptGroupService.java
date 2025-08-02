package com.fufu.terminal.service;

import com.fufu.terminal.controller.user.dto.ScriptGroupWithScriptsDto;
import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.repository.ScriptGroupRepository;
import com.fufu.terminal.entity.ScriptGroup.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Script Groups (已重构).
 */
@Service
@RequiredArgsConstructor
public class ScriptGroupService {

    private final ScriptGroupRepository scriptGroupRepository;

    /**
     * Fetches all active script groups and their contained aggregated scripts,
     * formatted for the user-facing homepage view.
     * @return A list of DTOs representing each script group.
     */
    @Transactional(readOnly = true)
    public List<ScriptGroupWithScriptsDto> getAllActiveGroupsWithScripts() {
        List<ScriptGroup> activeGroups = scriptGroupRepository.findByStatusOrderByDisplayOrder(ScriptGroup.Status.ACTIVE);
        return activeGroups.stream()
                .map(ScriptGroupWithScriptsDto::new)
                .collect(Collectors.toList());
    }

    /**
     * Fetches a single active script group by its ID, including its aggregated scripts.
     * This is designed for the script execution page.
     * @param groupId The ID of the script group.
     * @return A DTO representing the script group.
     */
    @Transactional(readOnly = true)
    public ScriptGroupWithScriptsDto getActiveGroupWithScripts(Long groupId) {
        ScriptGroup group = scriptGroupRepository.findByIdAndStatus(groupId, ScriptGroup.Status.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Active script group not found with id: " + groupId));
        return new ScriptGroupWithScriptsDto(group);
    }

    public List<ScriptGroup> getAllGroups() {
        return scriptGroupRepository.findAll();
    }

    public ScriptGroup getGroupById(Long id) {
        return scriptGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Script group not found with id: " + id));
    }

    @Transactional
    public ScriptGroup createGroup(ScriptGroup group) {
        return scriptGroupRepository.save(group);
    }

    @Transactional
    public ScriptGroup updateGroup(ScriptGroup group) {
        return scriptGroupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        scriptGroupRepository.deleteById(id);
    }

    @Transactional
    public void updateSortOrder(Long id, Integer sortOrder) {
        ScriptGroup group = getGroupById(id);
        group.setDisplayOrder(sortOrder);
        scriptGroupRepository.save(group);
    }
}
