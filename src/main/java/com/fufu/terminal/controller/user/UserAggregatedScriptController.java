package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.service.AggregatedScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/aggregated-scripts")
@RequiredArgsConstructor
public class UserAggregatedScriptController {
    
    private final AggregatedScriptService aggregatedScriptService;
    
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<AggregatedScript>> getActiveAggregatedScriptsByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(aggregatedScriptService.getActiveAggregatedScriptsByGroup(groupId));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AggregatedScript> getAggregatedScript(@PathVariable Long id) {
        return ResponseEntity.ok(aggregatedScriptService.getAggregatedScriptById(id));
    }
}