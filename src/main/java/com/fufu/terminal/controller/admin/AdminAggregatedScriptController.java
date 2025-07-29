package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.service.AggregatedScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/aggregated-scripts")
@RequiredArgsConstructor
public class AdminAggregatedScriptController {
    
    private final AggregatedScriptService aggregatedScriptService;
    
    @GetMapping
    public ResponseEntity<List<AggregatedScript>> getAllAggregatedScripts() {
        return ResponseEntity.ok(aggregatedScriptService.getAllAggregatedScripts());
    }
    
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<AggregatedScript>> getAggregatedScriptsByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(aggregatedScriptService.getAggregatedScriptsByGroup(groupId));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AggregatedScript> getAggregatedScript(@PathVariable Long id) {
        return ResponseEntity.ok(aggregatedScriptService.getAggregatedScriptById(id));
    }
    
    @PostMapping
    public ResponseEntity<AggregatedScript> createAggregatedScript(@RequestBody AggregatedScript aggregatedScript) {
        return ResponseEntity.ok(aggregatedScriptService.createAggregatedScript(aggregatedScript));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<AggregatedScript> updateAggregatedScript(@PathVariable Long id, @RequestBody AggregatedScript aggregatedScript) {
        aggregatedScript.setId(id);
        return ResponseEntity.ok(aggregatedScriptService.updateAggregatedScript(aggregatedScript));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAggregatedScript(@PathVariable Long id) {
        aggregatedScriptService.deleteAggregatedScript(id);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/sort-order")
    public ResponseEntity<Void> updateSortOrder(@PathVariable Long id, @RequestParam Integer sortOrder) {
        aggregatedScriptService.updateSortOrder(id, sortOrder);
        return ResponseEntity.ok().build();
    }
}