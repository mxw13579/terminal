package com.fufu.terminal.controller.api;

import cn.dev33.satoken.stp.StpUtil;
import com.fufu.terminal.controller.dto.*;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.registry.BuiltinScriptRegistry;
import com.fufu.terminal.service.refactored.RefactoredScriptExecutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified Script Execution Controller
 * Replaces ScriptController, UserScriptExecutionController, and UnifiedScriptExecutionController
 * Provides single entry point for all script execution requests with type-agnostic handling
 */
@Slf4j
@RestController
@RequestMapping("/api/scripts")
@Validated
@RequiredArgsConstructor
public class ScriptExecutionController {

    private final RefactoredScriptExecutionService executionService;
    private final BuiltinScriptRegistry builtinRegistry;

    /**
     * Execute any script type with unified handling
     */
    @PostMapping("/execute/{scriptId}")
    public ResponseEntity<ExecutionResponse> executeScript(
            @PathVariable @NotBlank String scriptId,
            @RequestBody @Valid ExecutionRequest request) {

        try {
            String username = StpUtil.getLoginIdAsString();
            log.info("Executing script {} for user {}", scriptId, username);

            String sessionId = executionService.executeScript(
                scriptId, request, username);

            return ResponseEntity.ok(ExecutionResponse.started(sessionId));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid script execution request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ExecutionResponse.failed("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to execute script {}", scriptId, e);
            return ResponseEntity.internalServerError()
                    .body(ExecutionResponse.failed("Execution failed: " + e.getMessage()));
        }
    }

    /**
     * Get execution progress for any script type
     */
    @GetMapping("/progress/{sessionId}")
    public ResponseEntity<ExecutionProgress> getProgress(
            @PathVariable @NotBlank String sessionId) {

        try {
            String username = StpUtil.getLoginIdAsString();
            ExecutionProgress progress = executionService.getProgress(sessionId, username);

            if (progress != null) {
                return ResponseEntity.ok(progress);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get progress for session {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Handle user interaction during script execution
     */
    @PostMapping("/interact/{sessionId}")
    public ResponseEntity<InteractionResponse> handleInteraction(
            @PathVariable @NotBlank String sessionId,
            @RequestBody @Valid InteractionRequest request) {

        try {
            String username = StpUtil.getLoginIdAsString();
            InteractionResponse response = executionService.handleUserInteraction(
                sessionId, request, username);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid interaction request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(InteractionResponse.rejected(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to handle interaction for session {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(InteractionResponse.rejected("Internal error: " + e.getMessage()));
        }
    }

    /**
     * Get available script types with descriptions
     */
    @GetMapping("/types")
    public ResponseEntity<List<ScriptTypeInfo>> getScriptTypes() {
        try {
            List<ScriptTypeInfo> types = Arrays.stream(ScriptType.values())
                .map(type -> ScriptTypeInfo.builder()
                    .type(type)
                    .name(type.getDisplayName())
                    .description(type.getDescription())
                    .supportedFeatures(type.getSupportedFeatures())
                    .requiresParameters(type.requiresParameters())
                    .supportsInteraction(type.supportsInteraction())
                    .isBuiltIn(type.isBuiltIn())
                    .build())
                .collect(Collectors.toList());

            return ResponseEntity.ok(types);

        } catch (Exception e) {
            log.error("Failed to get script types", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get scripts by type with filtering
     */
    @GetMapping("/list/{type}")
    public ResponseEntity<List<ScriptInfo>> getScriptsByType(
            @PathVariable ScriptType type,
            @RequestParam(required = false) String category) {

        try {
            String username = StpUtil.getLoginIdAsString();
            List<ScriptInfo> scripts = executionService.getScriptsByType(
                type, category, username);

            return ResponseEntity.ok(scripts);

        } catch (Exception e) {
            log.error("Failed to get scripts by type {}", type, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all available scripts (built-in and user scripts)
     */
    @GetMapping("/list")
    public ResponseEntity<List<ScriptInfo>> getAllScripts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        try {
            String username = StpUtil.getLoginIdAsString();
            List<ScriptInfo> scripts = executionService.getAllScripts(
                category, search, username);

            return ResponseEntity.ok(scripts);

        } catch (Exception e) {
            log.error("Failed to get all scripts", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Cancel running script execution
     */
    @PostMapping("/cancel/{sessionId}")
    public ResponseEntity<Void> cancelExecution(
            @PathVariable @NotBlank String sessionId) {

        try {
            String username = StpUtil.getLoginIdAsString();
            executionService.cancelExecution(sessionId, username);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.warn("Cannot cancel execution: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to cancel execution for session {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get script details by ID
     */
    @GetMapping("/details/{scriptId}")
    public ResponseEntity<ScriptInfo> getScriptDetails(
            @PathVariable @NotBlank String scriptId) {

        try {
            String username = StpUtil.getLoginIdAsString();
            ScriptInfo scriptInfo = executionService.getScriptDetails(scriptId, username);

            if (scriptInfo != null) {
                return ResponseEntity.ok(scriptInfo);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get script details for {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validate script execution request
     */
    @PostMapping("/validate/{scriptId}")
    public ResponseEntity<ValidationResponse> validateScript(
            @PathVariable @NotBlank String scriptId,
            @RequestBody @Valid ExecutionRequest request) {

        try {
            String username = StpUtil.getLoginIdAsString();
            ValidationResponse validation = executionService.validateScriptExecution(
                scriptId, request, username);

            return ResponseEntity.ok(validation);

        } catch (Exception e) {
            log.error("Failed to validate script {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
