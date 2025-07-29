package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.service.ScriptGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/script-groups")
@RequiredArgsConstructor
public class AdminScriptGroupController {
    
    private final ScriptGroupService scriptGroupService;
    
    @GetMapping
    public ResponseEntity<List<ScriptGroup>> getAllGroups() {
        return ResponseEntity.ok(scriptGroupService.getAllGroups());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ScriptGroup> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(scriptGroupService.getGroupById(id));
    }
    
    @PostMapping
    public ResponseEntity<ScriptGroup> createGroup(@RequestBody ScriptGroup group) {
        return ResponseEntity.ok(scriptGroupService.createGroup(group));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ScriptGroup> updateGroup(@PathVariable Long id, @RequestBody ScriptGroup group) {
        group.setId(id);
        return ResponseEntity.ok(scriptGroupService.updateGroup(group));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        scriptGroupService.deleteGroup(id);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/sort-order")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long id, @RequestParam Integer sortOrder) {
        scriptGroupService.updateSortOrder(id, sortOrder);
        return ResponseEntity.ok().build();
    }
}