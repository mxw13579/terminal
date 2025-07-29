package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.service.AggregatedScriptService;
import com.fufu.terminal.service.ScriptGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserScriptGroupController {
    
    private final ScriptGroupService scriptGroupService;
    private final AggregatedScriptService aggregatedScriptService;
    
    @GetMapping("/script-groups")
    public ResponseEntity<List<ScriptGroup>> getAllActiveGroups() {
        // 使用service方法获取激活状态的脚本分组
        List<ScriptGroup> activeGroups = scriptGroupService.getActiveGroups();
        return ResponseEntity.ok(activeGroups);
    }
    
    @GetMapping("/script-groups/{id}")
    public ResponseEntity<ScriptGroup> getGroup(@PathVariable Long id) {
        ScriptGroup group = scriptGroupService.getGroupById(id);
        if (group != null && ScriptGroup.Status.ACTIVE.equals(group.getStatus())) {
            return ResponseEntity.ok(group);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/aggregated-scripts/group/{groupId}")
    public ResponseEntity<List<AggregatedScript>> getAggregatedScriptsByGroup(@PathVariable Long groupId) {
        try {
            // 首先验证分组是否存在且为活跃状态
            ScriptGroup group = scriptGroupService.getGroupById(groupId);
            if (group == null || !ScriptGroup.Status.ACTIVE.equals(group.getStatus())) {
                return ResponseEntity.notFound().build();
            }
            
            // 获取指定分组下的聚合脚本列表，只返回活跃状态的脚本
            List<AggregatedScript> aggregatedScripts = aggregatedScriptService.getActiveAggregatedScriptsByGroup(groupId);
            return ResponseEntity.ok(aggregatedScripts);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/script-groups/{id}/aggregated-scripts")
    public ResponseEntity<List<AggregatedScript>> getGroupAggregatedScripts(@PathVariable Long id) {
        // 重定向到主要的端点实现
        return getAggregatedScriptsByGroup(id);
    }
}