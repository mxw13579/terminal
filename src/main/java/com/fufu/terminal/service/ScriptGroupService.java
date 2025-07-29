package com.fufu.terminal.service;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.repository.ScriptGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScriptGroupService {
    
    private final ScriptGroupRepository scriptGroupRepository;
    
    public List<ScriptGroup> getAllGroups() {
        return scriptGroupRepository.findAll();
    }
    
    public List<ScriptGroup> getActiveGroups() {
        return scriptGroupRepository.findByStatusOrderBySortOrder(ScriptGroup.Status.ACTIVE);
    }
    
    public ScriptGroup getGroupById(Long id) {
        return scriptGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Script group not found"));
    }
    
    public ScriptGroup createGroup(ScriptGroup group) {
        return scriptGroupRepository.save(group);
    }
    
    public ScriptGroup updateGroup(ScriptGroup group) {
        return scriptGroupRepository.save(group);
    }
    
    public void deleteGroup(Long id) {
        scriptGroupRepository.deleteById(id);
    }
    
    public void updateSortOrder(Long id, Integer sortOrder) {
        ScriptGroup group = getGroupById(id);
        group.setSortOrder(sortOrder);
        scriptGroupRepository.save(group);
    }
}