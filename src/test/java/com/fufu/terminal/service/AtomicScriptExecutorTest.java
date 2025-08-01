package com.fufu.terminal.service;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.model.ScriptExecutionProgress;
import com.fufu.terminal.service.ProgressManagerService;
import com.fufu.terminal.service.executor.AtomicScriptExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AtomicScriptExecutor 单元测试
 * 测试原子脚本执行器的核心功能
 */
@ExtendWith(MockitoExtension.class)
class AtomicScriptExecutorTest {

    @Mock
    private ProgressManagerService progressManager;

    @InjectMocks
    private AtomicScriptExecutor atomicScriptExecutor;

    private AtomicScript testScript;
    private String sessionId;
    private Long executionId;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-123";
        executionId = 1L;
        
        testScript = new AtomicScript();
        testScript.setId(1L);
        testScript.setName("Test Script");
        testScript.setScriptContent("echo 'Hello World'");
        testScript.setScriptType(ScriptType.BASH);
        testScript.setExecutionTimeout(30);

        // Mock progress manager behavior
        ScriptExecutionProgress mockProgress = new ScriptExecutionProgress();
        when(progressManager.initializeProgress(eq(sessionId), eq(executionId), eq(3)))
            .thenReturn(mockProgress);
    }

    @Test
    void testExecuteAsync_Success() throws Exception {
        // Arrange
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());
        doNothing().when(progressManager).setResultData(anyString(), any());

        // Act
        CompletableFuture<Object> future = atomicScriptExecutor.executeAsync(sessionId, executionId, testScript);
        Object result = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof String);
        
        // Verify progress manager interactions
        verify(progressManager).initializeProgress(sessionId, executionId, 3);
        verify(progressManager, times(3)).updateCurrentStep(eq(sessionId), anyString(), anyString());
        verify(progressManager, atLeast(3)).updateStepProgress(eq(sessionId), anyInt(), anyString());
        verify(progressManager, times(3)).completeCurrentStep(eq(sessionId), anyString());
        verify(progressManager).setResultData(eq(sessionId), any());
        verify(progressManager, never()).setExecutionFailed(anyString(), anyString());
    }

    @Test
    void testExecute_BashScript_Success() throws Exception {
        // Arrange
        testScript.setScriptContent("echo 'Test successful'");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());

        // Act
        Object result = atomicScriptExecutor.execute(sessionId, executionId, testScript);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof String);
        String output = (String) result;
        assertTrue(output.contains("Test successful") || output.trim().equals("Test successful"));

        // Verify all steps were executed
        verify(progressManager).updateCurrentStep(sessionId, "准备执行环境", "正在验证脚本内容和依赖项...");
        verify(progressManager).updateCurrentStep(sessionId, "执行脚本", "正在执行脚本内容...");
        verify(progressManager).updateCurrentStep(sessionId, "清理资源", "正在清理执行环境...");
    }

    @Test
    void testExecute_PythonScript_Success() throws Exception {
        // Arrange
        testScript.setScriptType(ScriptType.PYTHON);
        testScript.setScriptContent("print('Python test successful')");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());

        // Act
        Object result = atomicScriptExecutor.execute(sessionId, executionId, testScript);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof String);
        String output = (String) result;
        assertTrue(output.contains("Python test successful") || output.trim().equals("Python test successful"));
    }

    @Test
    void testExecute_EmptyScriptContent_ThrowsException() {
        // Arrange
        testScript.setScriptContent("");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            atomicScriptExecutor.execute(sessionId, executionId, testScript);
        });

        assertTrue(exception.getMessage().contains("脚本内容不能为空"));
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("执行失败"));
    }

    @Test
    void testExecute_NullScriptContent_ThrowsException() {
        // Arrange
        testScript.setScriptContent(null);
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            atomicScriptExecutor.execute(sessionId, executionId, testScript);
        });

        assertTrue(exception.getMessage().contains("脚本内容不能为空"));
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("执行失败"));
    }

    @Test
    void testExecute_InvalidTimeout_ThrowsException() {
        // Arrange
        testScript.setExecutionTimeout(-1);
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            atomicScriptExecutor.execute(sessionId, executionId, testScript);
        });

        assertTrue(exception.getMessage().contains("执行超时时间必须大于0"));
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("执行失败"));
    }

    @Test
    void testExecute_UnsupportedScriptType() throws Exception {
        // Arrange - using SQL as unsupported type
        testScript.setScriptType(ScriptType.SQL);
        testScript.setScriptContent("SELECT 1;");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            atomicScriptExecutor.execute(sessionId, executionId, testScript);
        });

        assertTrue(exception.getMessage().contains("不支持的脚本类型"));
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("执行失败"));
    }

    @Test
    void testExecuteAsync_Exception_SetsExecutionFailed() {
        // Arrange
        testScript.setScriptContent(null); // This will cause validation to fail
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        CompletableFuture<Object> future = atomicScriptExecutor.executeAsync(sessionId, executionId, testScript);
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        assertNotNull(exception.getCause());
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("脚本执行异常"));
    }

    @Test
    void testExecute_ProgressUpdates() throws Exception {
        // Arrange
        testScript.setScriptContent("echo 'line1'; echo 'line2'; echo 'line3'");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());

        // Act
        atomicScriptExecutor.execute(sessionId, executionId, testScript);

        // Assert - verify progress updates for each step
        verify(progressManager, times(3)).updateCurrentStep(eq(sessionId), anyString(), anyString());
        verify(progressManager, atLeast(6)).updateStepProgress(eq(sessionId), anyInt(), anyString());
        verify(progressManager, times(3)).completeCurrentStep(eq(sessionId), anyString());
    }

    @Test
    void testExecute_WithCustomTimeout() throws Exception {
        // Arrange
        testScript.setExecutionTimeout(60);
        testScript.setScriptContent("echo 'Custom timeout test'");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());

        // Act
        Object result = atomicScriptExecutor.execute(sessionId, executionId, testScript);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof String);
        
        // Verify that custom timeout was used (indirectly through successful execution)
        verify(progressManager, times(3)).completeCurrentStep(eq(sessionId), anyString());
    }

    @Test
    void testExecute_ScriptExecutionFailure() throws Exception {
        // Arrange - script that will fail
        testScript.setScriptContent("exit 1");
        doNothing().when(progressManager).updateCurrentStep(anyString(), anyString(), anyString());
        doNothing().when(progressManager).updateStepProgress(anyString(), anyInt(), anyString());
        doNothing().when(progressManager).completeCurrentStep(anyString(), anyString());
        doNothing().when(progressManager).setExecutionFailed(anyString(), anyString());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            atomicScriptExecutor.execute(sessionId, executionId, testScript);
        });

        assertTrue(exception.getMessage().contains("脚本执行失败"));
        verify(progressManager).setExecutionFailed(eq(sessionId), contains("执行失败"));
    }
}