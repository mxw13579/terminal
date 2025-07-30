package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.Script;
import com.fufu.terminal.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/scripts")
@RequiredArgsConstructor
public class UserScriptController {
    
    private final ScriptService scriptService;
    
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Script>> getActiveScriptsByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(scriptService.getActiveScriptsByGroup(groupId));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Script> getScript(@PathVariable Long id) {
        return ResponseEntity.ok(scriptService.getScriptById(id));
    }
}