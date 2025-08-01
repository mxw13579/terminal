package com.fufu.terminal.controller.user;

import com.fufu.terminal.controller.user.dto.ScriptGroupWithScriptsDto;
import com.fufu.terminal.service.ScriptGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User-facing controller for script groups (已重构).
 */
@RestController
@RequestMapping("/api/user/script-groups")
@RequiredArgsConstructor
public class UserScriptGroupController {

    private final ScriptGroupService scriptGroupService;

    /**
     * Gets all active script groups along with their aggregated scripts.
     * This is the primary endpoint for the user homepage view.
     * @return A list of DTOs, each representing a script group card with its scripts.
     */
    @GetMapping
    public ResponseEntity<List<ScriptGroupWithScriptsDto>> getAllActiveGroupsWithScripts() {
        List<ScriptGroupWithScriptsDto> activeGroups = scriptGroupService.getAllActiveGroupsWithScripts();
        return ResponseEntity.ok(activeGroups);
    }

    // The other endpoints for fetching individual groups or scripts are no longer needed
    // in this controller as the main endpoint provides all necessary data for the homepage.

    /**
     * Gets a single active script group with its scripts by ID.
     * Used by the script execution page to get all necessary data at once.
     * @param groupId The ID of the script group.
     * @return A DTO for the script group.
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<ScriptGroupWithScriptsDto> getActiveGroupWithScripts(@PathVariable Long groupId) {
        ScriptGroupWithScriptsDto group = scriptGroupService.getActiveGroupWithScripts(groupId);
        return ResponseEntity.ok(group);
    }
}
