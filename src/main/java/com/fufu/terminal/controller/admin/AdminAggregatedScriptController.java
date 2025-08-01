package com.fufu.terminal.controller.admin;

import com.fufu.terminal.controller.admin.dto.AggregatedScriptCreateRequest;
import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.service.AggregatedScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for managing Aggregated Scripts (已重构).
 */
@RestController
@RequestMapping("/api/admin/aggregated-scripts")
@RequiredArgsConstructor
public class AdminAggregatedScriptController {

    private final AggregatedScriptService aggregatedScriptService;

    /**
     * Creates a new aggregated script from a builder request.
     */
    @PostMapping
    public ResponseEntity<AggregatedScript> createAggregatedScript(@RequestBody AggregatedScriptCreateRequest request) {
        AggregatedScript createdScript = aggregatedScriptService.createAggregatedScript(request);
        return ResponseEntity.ok(createdScript);
    }

    @GetMapping
    public ResponseEntity<List<AggregatedScript>> getAllAggregatedScripts() {
        return ResponseEntity.ok(aggregatedScriptService.getAllAggregatedScripts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AggregatedScript> getAggregatedScript(@PathVariable Long id) {
        return ResponseEntity.ok(aggregatedScriptService.getAggregatedScriptById(id));
    }

    // Note: The update endpoint would likely also need a DTO for a complete implementation.
    @PutMapping("/{id}")
    public ResponseEntity<AggregatedScript> updateAggregatedScript(@PathVariable Long id, @RequestBody AggregatedScript aggregatedScript) {
        // This is a simplified update. A real implementation would use a DTO
        // and more sophisticated logic in the service layer.
        aggregatedScript.setId(id);
        // The old service method is being used here, this would need a refactor for a full feature.
        // return ResponseEntity.ok(aggregatedScriptService.updateAggregatedScript(aggregatedScript));
        return ResponseEntity.status(501).build(); // Not Implemented
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAggregatedScript(@PathVariable Long id) {
        aggregatedScriptService.deleteAggregatedScript(id);
        return ResponseEntity.ok().build();
    }
}
