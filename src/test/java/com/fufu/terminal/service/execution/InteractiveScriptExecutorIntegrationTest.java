package com.fufu.terminal.service.execution;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.ScriptExecutionSession;
import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.repository.ScriptExecutionSessionRepository;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.ScriptInteractionService;
import com.fufu.terminal.service.validation.ScriptValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for InteractiveScriptExecutor
 * Tests the enhanced thread pool management and execution capabilities
 */
@ExtendWith(MockitoExtension.class)
class InteractiveScriptExecutorIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private AtomicScriptService atomicScriptService;
    
    @Mock
    private ScriptExecutionSessionRepository sessionRepository;
    
    @Mock
    private ScriptInteractionService interactionService;
    
    @Mock
    private ScriptValidationService validationService;

    private InteractiveScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new InteractiveScriptExecutor(
            messagingTemplate,
            atomicScriptService,
            sessionRepository,
            interactionService,
            validationService
        );
        
        // Initialize the thread pool through PostConstruct
        try {
            java.lang.reflect.Method initMethod = InteractiveScriptExecutor.class.getDeclaredMethod("initializeThreadPool");
            initMethod.setAccessible(true);
            initMethod.invoke(executor);
        } catch (Exception e) {
            fail("Failed to initialize thread pool: " + e.getMessage());
        }
    }

    @Test
    void testThreadPoolInitialization() throws Exception {
        // Access the private executorService field
        java.lang.reflect.Field executorField = InteractiveScriptExecutor.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorField.get(executor);
        
        assertNotNull(threadPool);
        assertEquals(5, threadPool.getCorePoolSize());
        assertEquals(20, threadPool.getMaximumPoolSize());
        assertEquals(60, threadPool.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(100, threadPool.getQueue().remainingCapacity() + threadPool.getQueue().size());
        assertFalse(threadPool.isShutdown());
    }

    @Test
    void testExecutionWithMockedDependencies() {
        // Setup mock data
        AggregatedScript script = new AggregatedScript();
        script.setId(1L);
        script.setName("Test Script");
        
        ScriptExecutionSession session = new ScriptExecutionSession();
        session.setId("test-session");
        session.setContextData("{}");
        
        when(sessionRepository.findById("test-session")).thenReturn(java.util.Optional.of(session));
        when(sessionRepository.save(any(ScriptExecutionSession.class))).thenReturn(session);
        
        // Test that execution can be initiated without throwing exceptions
        assertDoesNotThrow(() -> {
            executor.executeAggregateScript("test-session", script);
        });
    }

    @Test
    void testThreadPoolShutdown() throws Exception {
        // Access the private executorService field
        java.lang.reflect.Field executorField = InteractiveScriptExecutor.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorField.get(executor);
        
        // Invoke the shutdown method
        java.lang.reflect.Method shutdownMethod = InteractiveScriptExecutor.class.getDeclaredMethod("shutdownThreadPool");
        shutdownMethod.setAccessible(true);
        shutdownMethod.invoke(executor);
        
        // Verify the thread pool is shutdown
        assertTrue(threadPool.isShutdown());
    }

    @Test
    void testConfirmationTypeDetection() throws Exception {
        // Access the private isConfirmationType method
        java.lang.reflect.Method method = InteractiveScriptExecutor.class.getDeclaredMethod("isConfirmationType", 
            com.fufu.terminal.entity.enums.InteractionType.class);
        method.setAccessible(true);
        
        // Test confirmation types
        assertTrue((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.CONFIRMATION));
        assertTrue((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.CONFIRM_YES_NO));
        assertTrue((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.CONFIRM_RECOMMENDATION));
        
        // Test non-confirmation types
        assertFalse((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.INPUT_TEXT));
        assertFalse((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.INPUT_PASSWORD));
        assertFalse((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.INPUT_FORM));
        assertFalse((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.SELECT_OPTION));
        assertFalse((Boolean) method.invoke(executor, com.fufu.terminal.entity.enums.InteractionType.FILE_UPLOAD));
    }
}