package com.fufu.terminal.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.service.AtomicScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminAtomicScriptController 集成测试
 * 测试管理员原子脚本控制器的REST API端点
 */
@WebMvcTest(AdminAtomicScriptController.class)
class AdminAtomicScriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AtomicScriptService atomicScriptService;

    @MockBean
    private com.fufu.terminal.service.script.UnifiedScriptRegistry unifiedScriptRegistry;

    @MockBean
    private com.fufu.terminal.service.script.UnifiedScriptRegistrationService registrationService;

    @Autowired
    private ObjectMapper objectMapper;

    private AtomicScript testScript;
    private List<AtomicScript> testScriptList;

    @BeforeEach
    void setUp() {
        testScript = createTestAtomicScript(1L, "Test Script", "echo 'test'", ScriptType.BASH);
        
        AtomicScript script2 = createTestAtomicScript(2L, "Python Script", "print('test')", ScriptType.PYTHON);
        testScriptList = Arrays.asList(testScript, script2);
    }

    private AtomicScript createTestAtomicScript(Long id, String name, String content, ScriptType type) {
        AtomicScript script = new AtomicScript();
        script.setId(id);
        script.setName(name);
        script.setScriptContent(content);
        script.setScriptType(type);
        script.setStatus(AtomicScript.Status.ACTIVE);
        script.setCreatedAt(LocalDateTime.now());
        script.setCreatedBy(1L);
        script.setEstimatedDuration(30);
        return script;
    }

    @Test
    void testGetAllAtomicScripts_Success() throws Exception {
        // Arrange
        when(atomicScriptService.getAllAtomicScripts()).thenReturn(testScriptList);

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("Test Script")))
                .andExpect(jsonPath("$[0].scriptType", is("BASH")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].name", is("Python Script")))
                .andExpect(jsonPath("$[1].scriptType", is("PYTHON")));

        verify(atomicScriptService).getAllAtomicScripts();
    }

    @Test
    void testGetAllAtomicScripts_ServiceException() throws Exception {
        // Arrange
        when(atomicScriptService.getAllAtomicScripts()).thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(atomicScriptService).getAllAtomicScripts();
    }

    @Test
    void testGetAtomicScriptById_Success() throws Exception {
        // Arrange
        Long scriptId = 1L;
        when(atomicScriptService.getAtomicScriptById(scriptId)).thenReturn(testScript);

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Test Script")))
                .andExpect(jsonPath("$.scriptContent", is("echo 'test'")))
                .andExpect(jsonPath("$.scriptType", is("BASH")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        verify(atomicScriptService).getAtomicScriptById(scriptId);
    }

    @Test
    void testGetAtomicScriptById_NotFound() throws Exception {
        // Arrange
        Long scriptId = 999L;
        when(atomicScriptService.getAtomicScriptById(scriptId))
                .thenThrow(new RuntimeException("Atomic script not found"));

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(atomicScriptService).getAtomicScriptById(scriptId);
    }

    @Test
    void testCreateAtomicScript_Success() throws Exception {
        // Arrange
        AtomicScript newScript = createTestAtomicScript(null, "New Script", "echo 'new'", ScriptType.BASH);
        AtomicScript savedScript = createTestAtomicScript(3L, "New Script", "echo 'new'", ScriptType.BASH);
        
        when(atomicScriptService.createAtomicScript(any(AtomicScript.class))).thenReturn(savedScript);

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newScript)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(3)))
                .andExpect(jsonPath("$.name", is("New Script")))
                .andExpect(jsonPath("$.scriptContent", is("echo 'new'")))
                .andExpect(jsonPath("$.scriptType", is("BASH")));

        verify(atomicScriptService).createAtomicScript(any(AtomicScript.class));
    }

    @Test
    void testCreateAtomicScript_InvalidInput_EmptyName() throws Exception {
        // Arrange
        AtomicScript invalidScript = createTestAtomicScript(null, "", "echo 'test'", ScriptType.BASH);

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidScript)))
                .andExpect(status().isBadRequest());

        verify(atomicScriptService, never()).createAtomicScript(any());
    }

    @Test
    void testCreateAtomicScript_InvalidInput_EmptyContent() throws Exception {
        // Arrange
        AtomicScript invalidScript = createTestAtomicScript(null, "Valid Name", "", ScriptType.BASH);

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidScript)))
                .andExpect(status().isBadRequest());

        verify(atomicScriptService, never()).createAtomicScript(any());
    }

    @Test
    void testCreateAtomicScript_DangerousCommand() throws Exception {
        // Arrange
        AtomicScript dangerousScript = createTestAtomicScript(null, "Dangerous Script", "rm -rf /", ScriptType.BASH);

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dangerousScript)))
                .andExpect(status().isBadRequest());

        verify(atomicScriptService, never()).createAtomicScript(any());
    }

    @Test
    void testCreateAtomicScript_ServiceException() throws Exception {
        // Arrange
        AtomicScript newScript = createTestAtomicScript(null, "New Script", "echo 'new'", ScriptType.BASH);
        when(atomicScriptService.createAtomicScript(any(AtomicScript.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newScript)))
                .andExpect(status().isInternalServerError());

        verify(atomicScriptService).createAtomicScript(any(AtomicScript.class));
    }

    @Test
    void testUpdateAtomicScript_Success() throws Exception {
        // Arrange
        Long scriptId = 1L;
        testScript.setName("Updated Script");
        when(atomicScriptService.updateAtomicScript(any(AtomicScript.class))).thenReturn(testScript);

        // Act & Assert
        mockMvc.perform(put("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testScript)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Updated Script")));

        verify(atomicScriptService).updateAtomicScript(any(AtomicScript.class));
    }

    @Test
    void testUpdateAtomicScript_NotFound() throws Exception {
        // Arrange
        Long scriptId = 999L;
        when(atomicScriptService.updateAtomicScript(any(AtomicScript.class)))
                .thenThrow(new RuntimeException("Atomic script not found"));

        // Act & Assert
        mockMvc.perform(put("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testScript)))
                .andExpect(status().isNotFound());

        verify(atomicScriptService).updateAtomicScript(any(AtomicScript.class));
    }

    @Test
    void testDeleteAtomicScript_Success() throws Exception {
        // Arrange
        Long scriptId = 1L;
        doNothing().when(atomicScriptService).deleteAtomicScript(scriptId);

        // Act & Assert
        mockMvc.perform(delete("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(atomicScriptService).deleteAtomicScript(scriptId);
    }

    @Test
    void testDeleteAtomicScript_NotFound() throws Exception {
        // Arrange
        Long scriptId = 999L;
        doThrow(new RuntimeException("Atomic script not found"))
                .when(atomicScriptService).deleteAtomicScript(scriptId);

        // Act & Assert
        mockMvc.perform(delete("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(atomicScriptService).deleteAtomicScript(scriptId);
    }

    @Test
    void testGetAtomicScriptsByStatus_Success() throws Exception {
        // Arrange
        AtomicScript.Status status = AtomicScript.Status.ACTIVE;
        when(atomicScriptService.getAtomicScriptsByStatus(status)).thenReturn(testScriptList);

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts/status/{status}", status)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$[1].status", is("ACTIVE")));

        verify(atomicScriptService).getAtomicScriptsByStatus(status);
    }

    @Test
    void testReloadRegistry_Success() throws Exception {
        // Arrange
        doNothing().when(registrationService).reloadConfigurableScripts();

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts/registry/reload")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("统一脚本注册表已重新加载"));

        verify(registrationService).reloadConfigurableScripts();
    }

    @Test
    void testReloadRegistry_ServiceException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Registry reload failed"))
                .when(registrationService).reloadConfigurableScripts();

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts/registry/reload")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("注册表重新加载失败")));

        verify(registrationService).reloadConfigurableScripts();
    }

    @Test
    void testGetRegisteredScripts_Success() throws Exception {
        // Arrange
        List<com.fufu.terminal.service.script.UnifiedAtomicScript> mockScripts = Arrays.asList();
        when(unifiedScriptRegistry.getAllScripts()).thenReturn(mockScripts);

        // Act & Assert
        mockMvc.perform(get("/api/admin/atomic-scripts/registry")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(unifiedScriptRegistry).getAllScripts();
    }

    @Test
    void testScriptContentTooLong() throws Exception {
        // Arrange
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 10001; i++) {
            longContent.append("a");
        }
        AtomicScript longScript = createTestAtomicScript(null, "Long Script", longContent.toString(), ScriptType.BASH);

        // Act & Assert
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longScript)))
                .andExpect(status().isBadRequest());

        verify(atomicScriptService, never()).createAtomicScript(any());
    }

    @Test
    void testCreateAtomicScript_AllDangerousCommands() throws Exception {
        String[] dangerousCommands = {
            "rm -rf /home", "format c:", "del /f /q c:\\*", "deltree c:\\", 
            "shutdown -h now", "reboot", "dd if=/dev/zero of=/dev/sda", 
            "mkfs.ext4 /dev/sda1", "fdisk /dev/sda", "parted /dev/sda rm 1",
            "chmod 777 /etc/passwd", "chown root:root /etc/shadow"
        };

        for (String dangerousCommand : dangerousCommands) {
            AtomicScript dangerousScript = createTestAtomicScript(null, "Dangerous Script", dangerousCommand, ScriptType.BASH);

            mockMvc.perform(post("/api/admin/atomic-scripts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dangerousScript)))
                    .andExpect(status().isBadRequest());
        }

        verify(atomicScriptService, never()).createAtomicScript(any());
    }
}