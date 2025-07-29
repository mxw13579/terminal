package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.Script;
import com.fufu.terminal.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/scripts")
@RequiredArgsConstructor
public class AdminScriptController {
    
    private final ScriptService scriptService;
    
    @GetMapping
    public ResponseEntity<List<Script>> getAllScripts() {
        return ResponseEntity.ok(scriptService.getAllScripts());
    }
    
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Script>> getScriptsByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(scriptService.getScriptsByGroup(groupId));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Script> getScript(@PathVariable Long id) {
        return ResponseEntity.ok(scriptService.getScriptById(id));
    }
    
    @PostMapping
    public ResponseEntity<Script> createScript(@RequestBody Script script) {
        return ResponseEntity.ok(scriptService.createScript(script));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Script> updateScript(@PathVariable Long id, @RequestBody Script script) {
        script.setId(id);
        return ResponseEntity.ok(scriptService.updateScript(script));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScript(@PathVariable Long id) {
        scriptService.deleteScript(id);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/sort-order")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long id, @RequestParam Integer sortOrder) {
        scriptService.updateSortOrder(id, sortOrder);
        return ResponseEntity.ok().build();
    }
}