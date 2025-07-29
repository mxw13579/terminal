package com.fufu.terminal.service;

import com.fufu.terminal.entity.Script;
import com.fufu.terminal.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lizelin
 */
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;

    public List<Script> getAllScripts() {
        return scriptRepository.findAll();
    }

    public List<Script> getScriptsByGroup(Long groupId) {
        return scriptRepository.findByGroupIdAndStatusOrderBySortOrder(groupId, Script.Status.ACTIVE);
    }

    public List<Script> getActiveScriptsByGroup(Long groupId) {
        return scriptRepository.findByGroupIdAndStatusOrderBySortOrder(groupId, Script.Status.ACTIVE);
    }

    public Script getScriptById(Long id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Script not found"));
    }

    public Script createScript(Script script) {
        return scriptRepository.save(script);
    }

    public Script updateScript(Script script) {
        return scriptRepository.save(script);
    }

    public void deleteScript(Long id) {
        scriptRepository.deleteById(id);
    }

    public void updateSortOrder(Long id, Integer sortOrder) {
        Script script = getScriptById(id);
        script.setSortOrder(sortOrder);
        scriptRepository.save(script);
    }
}
