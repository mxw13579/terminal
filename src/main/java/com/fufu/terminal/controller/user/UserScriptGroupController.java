package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.service.ScriptGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/script-groups")
@RequiredArgsConstructor
public class UserScriptGroupController {
    
    private final ScriptGroupService scriptGroupService;
    
    @GetMapping
    public ResponseEntity<List<ScriptGroup>> getActiveGroups() {
        return ResponseEntity.ok(scriptGroupService.getActiveGroups());
    }
}