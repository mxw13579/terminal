package com.fufu.terminal.service;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.repository.AtomicScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AtomicScriptService 单元测试
 * 测试原子脚本服务的CRUD操作和业务逻辑
 */
@ExtendWith(MockitoExtension.class)
class AtomicScriptServiceTest {

    @Mock
    private AtomicScriptRepository atomicScriptRepository;

    @InjectMocks
    private AtomicScriptService atomicScriptService;

    private AtomicScript testScript;
    private List<AtomicScript> testScriptList;

    @BeforeEach
    void setUp() {
        testScript = createTestAtomicScript(1L, "Test Script", "echo 'test'", ScriptType.BASH);
        
        AtomicScript script2 = createTestAtomicScript(2L, "Python Script", "print('test')", ScriptType.PYTHON);
        AtomicScript script3 = createTestAtomicScript(3L, "SQL Script", "SELECT 1", ScriptType.SQL);
        
        testScriptList = Arrays.asList(testScript, script2, script3);
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
        return script;
    }

    @Test
    void testGetAllAtomicScripts_Success() {
        // Arrange
        when(atomicScriptRepository.findAll()).thenReturn(testScriptList);

        // Act
        List<AtomicScript> result = atomicScriptService.getAllAtomicScripts();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Test Script", result.get(0).getName());
        verify(atomicScriptRepository).findAll();
    }

    @Test
    void testGetAllAtomicScripts_EmptyList() {
        // Arrange
        when(atomicScriptRepository.findAll()).thenReturn(Arrays.asList());

        // Act
        List<AtomicScript> result = atomicScriptService.getAllAtomicScripts();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(atomicScriptRepository).findAll();
    }

    @Test
    void testGetAtomicScriptById_Success() {
        // Arrange
        Long scriptId = 1L;
        when(atomicScriptRepository.findById(scriptId)).thenReturn(Optional.of(testScript));

        // Act
        AtomicScript result = atomicScriptService.getAtomicScriptById(scriptId);

        // Assert
        assertNotNull(result);
        assertEquals(scriptId, result.getId());
        assertEquals("Test Script", result.getName());
        verify(atomicScriptRepository).findById(scriptId);
    }

    @Test
    void testGetAtomicScriptById_NotFound() {
        // Arrange
        Long scriptId = 999L;
        when(atomicScriptRepository.findById(scriptId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            atomicScriptService.getAtomicScriptById(scriptId);
        });

        assertEquals("Atomic script not found", exception.getMessage());
        verify(atomicScriptRepository).findById(scriptId);
    }

    @Test
    void testCreateAtomicScript_Success() {
        // Arrange
        AtomicScript newScript = createTestAtomicScript(null, "New Script", "echo 'new'", ScriptType.BASH);
        AtomicScript savedScript = createTestAtomicScript(4L, "New Script", "echo 'new'", ScriptType.BASH);
        
        when(atomicScriptRepository.save(any(AtomicScript.class))).thenReturn(savedScript);

        // Act
        AtomicScript result = atomicScriptService.createAtomicScript(newScript);

        // Assert
        assertNotNull(result);
        assertEquals(4L, result.getId());
        assertEquals("New Script", result.getName());
        verify(atomicScriptRepository).save(newScript);
    }

    @Test
    void testCreateAtomicScript_NullInput() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            atomicScriptService.createAtomicScript(null);
        });

        assertEquals("AtomicScript cannot be null", exception.getMessage());
        verify(atomicScriptRepository, never()).save(any());
    }

    @Test
    void testCreateAtomicScript_InvalidName() {
        // Arrange
        AtomicScript invalidScript = createTestAtomicScript(null, "", "echo 'test'", ScriptType.BASH);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            atomicScriptService.createAtomicScript(invalidScript);
        });

        assertEquals("Script name cannot be empty", exception.getMessage());
        verify(atomicScriptRepository, never()).save(any());
    }

    @Test
    void testCreateAtomicScript_InvalidContent() {
        // Arrange
        AtomicScript invalidScript = createTestAtomicScript(null, "Valid Name", "", ScriptType.BASH);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            atomicScriptService.createAtomicScript(invalidScript);
        });

        assertEquals("Script content cannot be empty", exception.getMessage());
        verify(atomicScriptRepository, never()).save(any());
    }

    @Test
    void testUpdateAtomicScript_Success() {
        // Arrange
        testScript.setName("Updated Script");
        when(atomicScriptRepository.existsById(testScript.getId())).thenReturn(true);
        when(atomicScriptRepository.save(testScript)).thenReturn(testScript);

        // Act
        AtomicScript result = atomicScriptService.updateAtomicScript(testScript);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Script", result.getName());
        verify(atomicScriptRepository).existsById(testScript.getId());
        verify(atomicScriptRepository).save(testScript);
    }

    @Test
    void testUpdateAtomicScript_NotFound() {
        // Arrange
        when(atomicScriptRepository.existsById(testScript.getId())).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            atomicScriptService.updateAtomicScript(testScript);
        });

        assertEquals("Atomic script not found", exception.getMessage());
        verify(atomicScriptRepository).existsById(testScript.getId());
        verify(atomicScriptRepository, never()).save(any());
    }

    @Test
    void testDeleteAtomicScript_Success() {
        // Arrange
        Long scriptId = 1L;
        when(atomicScriptRepository.existsById(scriptId)).thenReturn(true);
        doNothing().when(atomicScriptRepository).deleteById(scriptId);

        // Act
        atomicScriptService.deleteAtomicScript(scriptId);

        // Assert
        verify(atomicScriptRepository).existsById(scriptId);
        verify(atomicScriptRepository).deleteById(scriptId);
    }

    @Test
    void testDeleteAtomicScript_NotFound() {
        // Arrange
        Long scriptId = 999L;
        when(atomicScriptRepository.existsById(scriptId)).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            atomicScriptService.deleteAtomicScript(scriptId);
        });

        assertEquals("Atomic script not found", exception.getMessage());
        verify(atomicScriptRepository).existsById(scriptId);
        verify(atomicScriptRepository, never()).deleteById(any());
    }

    @Test
    void testGetAtomicScriptsByStatus_Success() {
        // Arrange
        AtomicScript.Status status = AtomicScript.Status.ACTIVE;
        List<AtomicScript> activeScripts = Arrays.asList(testScript);
        when(atomicScriptRepository.findByStatusOrderBySortOrderAsc(status)).thenReturn(activeScripts);

        // Act
        List<AtomicScript> result = atomicScriptService.getAtomicScriptsByStatus(status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(AtomicScript.Status.ACTIVE, result.get(0).getStatus());
        verify(atomicScriptRepository).findByStatusOrderBySortOrderAsc(status);
    }

    @Test
    void testGetAtomicScriptsByType_Success() {
        // Arrange
        ScriptType scriptType = ScriptType.BASH;
        List<AtomicScript> bashScripts = Arrays.asList(testScript);
        when(atomicScriptRepository.findByScriptTypeAndStatusOrderBySortOrderAsc(scriptType, AtomicScript.Status.ACTIVE))
            .thenReturn(bashScripts);

        // Act
        List<AtomicScript> result = atomicScriptService.getAtomicScriptsByType(scriptType);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ScriptType.BASH, result.get(0).getScriptType());
        verify(atomicScriptRepository).findByScriptTypeAndStatusOrderBySortOrderAsc(scriptType, AtomicScript.Status.ACTIVE);
    }

    @Test
    void testGetAtomicScriptsPaginated_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<AtomicScript> scriptPage = new PageImpl<>(testScriptList, pageable, testScriptList.size());
        when(atomicScriptRepository.findAll(pageable)).thenReturn(scriptPage);

        // Act
        Page<AtomicScript> result = atomicScriptService.getAtomicScriptsPaginated(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getTotalElements());
        assertEquals(3, result.getContent().size());
        verify(atomicScriptRepository).findAll(pageable);
    }

    @Test
    void testActivateAtomicScript_Success() {
        // Arrange
        Long scriptId = 1L;
        testScript.setStatus(AtomicScript.Status.DRAFT);
        when(atomicScriptRepository.findById(scriptId)).thenReturn(Optional.of(testScript));
        when(atomicScriptRepository.save(testScript)).thenReturn(testScript);

        // Act
        AtomicScript result = atomicScriptService.activateAtomicScript(scriptId);

        // Assert
        assertNotNull(result);
        assertEquals(AtomicScript.Status.ACTIVE, result.getStatus());
        verify(atomicScriptRepository).findById(scriptId);
        verify(atomicScriptRepository).save(testScript);
    }

    @Test
    void testDeactivateAtomicScript_Success() {
        // Arrange
        Long scriptId = 1L;
        when(atomicScriptRepository.findById(scriptId)).thenReturn(Optional.of(testScript));
        when(atomicScriptRepository.save(testScript)).thenReturn(testScript);

        // Act
        AtomicScript result = atomicScriptService.deactivateAtomicScript(scriptId);

        // Assert
        assertNotNull(result);
        assertEquals(AtomicScript.Status.INACTIVE, result.getStatus());
        verify(atomicScriptRepository).findById(scriptId);
        verify(atomicScriptRepository).save(testScript);
    }

    @Test
    void testSearchAtomicScripts_Success() {
        // Arrange
        String keyword = "test";
        when(atomicScriptRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword))
            .thenReturn(Arrays.asList(testScript));

        // Act
        List<AtomicScript> result = atomicScriptService.searchAtomicScripts(keyword);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getName().toLowerCase().contains(keyword.toLowerCase()));
        verify(atomicScriptRepository).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword);
    }

    @Test
    void testSearchAtomicScripts_EmptyKeyword() {
        // Arrange
        String keyword = "";

        // Act
        List<AtomicScript> result = atomicScriptService.searchAtomicScripts(keyword);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(atomicScriptRepository, never()).findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(any(), any());
    }

    @Test
    void testGetAtomicScriptsByCreatedBy_Success() {
        // Arrange
        Long createdBy = 1L;
        when(atomicScriptRepository.findByCreatedByOrderByCreatedAtDesc(createdBy))
            .thenReturn(Arrays.asList(testScript));

        // Act
        List<AtomicScript> result = atomicScriptService.getAtomicScriptsByCreatedBy(createdBy);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(createdBy, result.get(0).getCreatedBy());
        verify(atomicScriptRepository).findByCreatedByOrderByCreatedAtDesc(createdBy);
    }
}