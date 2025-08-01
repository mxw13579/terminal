package com.fufu.terminal.performance;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.executor.AtomicScriptExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSH Terminal Management System 性能测试
 * 测试系统在高并发和大负载情况下的性能表现
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.com.fufu.terminal=WARN" // Reduce logging during performance tests
})
class SshTerminalPerformanceTest {

    @Autowired
    private AtomicScriptService atomicScriptService;

    @Autowired
    private AtomicScriptExecutor atomicScriptExecutor;

    @Test
    void testConcurrentScriptCreation() throws Exception {
        int numberOfThreads = 10;
        int scriptsPerThread = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Instant startTime = Instant.now();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < scriptsPerThread; j++) {
                    AtomicScript script = createTestScript(
                        String.format("Performance Test Script T%d-S%d", threadId, j),
                        "echo 'Performance test " + threadId + "-" + j + "'"
                    );
                    
                    AtomicScript created = atomicScriptService.createAtomicScript(script);
                    assertNotNull(created.getId());
                }
            }, executorService);
            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        
        // Assert performance requirements
        assertTrue(duration.toMillis() < 10000, "Concurrent script creation took too long: " + duration.toMillis() + "ms");
        
        // Verify all scripts were created
        List<AtomicScript> allScripts = atomicScriptService.getAllAtomicScripts();
        assertTrue(allScripts.size() >= numberOfThreads * scriptsPerThread, 
                   "Not all scripts were created: expected >= " + (numberOfThreads * scriptsPerThread) + ", got " + allScripts.size());
        
        executorService.shutdown();
    }

    @Test
    void testConcurrentScriptExecution() throws Exception {
        // Create test scripts first
        List<AtomicScript> testScripts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AtomicScript script = createTestScript(
                "Concurrent Execution Test " + i,
                "echo 'Concurrent execution test " + i + "'; sleep 1"
            );
            testScripts.add(atomicScriptService.createAtomicScript(script));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        Instant startTime = Instant.now();

        // Execute all scripts concurrently
        for (int i = 0; i < testScripts.size(); i++) {
            final int index = i;
            final AtomicScript script = testScripts.get(index);
            
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String sessionId = "perf-test-session-" + index;
                    return atomicScriptExecutor.execute(sessionId, (long) index, script);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService);
            futures.add(future);
        }

        // Wait for all executions to complete
        List<Object> results = new ArrayList<>();
        for (CompletableFuture<Object> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Assert performance requirements
        assertTrue(duration.toSeconds() < 30, "Concurrent script execution took too long: " + duration.toSeconds() + "s");
        
        // Verify all executions succeeded
        assertEquals(testScripts.size(), results.size(), "Not all scripts executed successfully");
        results.forEach(result -> assertNotNull(result, "Script execution result should not be null"));
        
        executorService.shutdown();
    }

    @Test
    void testDatabasePerformanceUnderLoad() throws Exception {
        int batchSize = 100;
        List<AtomicScript> scripts = new ArrayList<>();
        
        // Create a large batch of scripts
        Instant startTime = Instant.now();
        
        for (int i = 0; i < batchSize; i++) {
            AtomicScript script = createTestScript(
                "DB Performance Test " + i,
                "echo 'Database performance test " + i + "'"
            );
            scripts.add(atomicScriptService.createAtomicScript(script));
        }
        
        Instant creationEndTime = Instant.now();
        Duration creationDuration = Duration.between(startTime, creationEndTime);
        
        // Read all scripts
        Instant readStartTime = Instant.now();
        List<AtomicScript> allScripts = atomicScriptService.getAllAtomicScripts();
        Instant readEndTime = Instant.now();
        Duration readDuration = Duration.between(readStartTime, readEndTime);
        
        // Update all scripts
        Instant updateStartTime = Instant.now();
        for (AtomicScript script : scripts) {
            script.setDescription("Updated description for performance test");
            atomicScriptService.updateAtomicScript(script);
        }
        Instant updateEndTime = Instant.now();
        Duration updateDuration = Duration.between(updateStartTime, updateEndTime);
        
        // Delete all scripts
        Instant deleteStartTime = Instant.now();
        for (AtomicScript script : scripts) {
            atomicScriptService.deleteAtomicScript(script.getId());
        }
        Instant deleteEndTime = Instant.now();
        Duration deleteDuration = Duration.between(deleteStartTime, deleteEndTime);
        
        // Assert performance requirements (these are generous limits for testing)
        assertTrue(creationDuration.toMillis() < 30000, "Batch creation took too long: " + creationDuration.toMillis() + "ms");
        assertTrue(readDuration.toMillis() < 5000, "Batch read took too long: " + readDuration.toMillis() + "ms");
        assertTrue(updateDuration.toMillis() < 30000, "Batch update took too long: " + updateDuration.toMillis() + "ms");
        assertTrue(deleteDuration.toMillis() < 30000, "Batch delete took too long: " + deleteDuration.toMillis() + "ms");
        
        // Verify operations
        assertTrue(allScripts.size() >= batchSize, "Not all scripts were created and read");
        
        System.out.println("Performance Test Results:");
        System.out.println("Creation: " + creationDuration.toMillis() + "ms for " + batchSize + " scripts");
        System.out.println("Read: " + readDuration.toMillis() + "ms for " + allScripts.size() + " scripts");
        System.out.println("Update: " + updateDuration.toMillis() + "ms for " + batchSize + " scripts");
        System.out.println("Delete: " + deleteDuration.toMillis() + "ms for " + batchSize + " scripts");
    }

    @Test
    void testMemoryUsageUnderLoad() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Get baseline memory usage
        System.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        List<AtomicScript> scripts = new ArrayList<>();
        
        // Create many scripts to test memory usage
        for (int i = 0; i < 1000; i++) {
            AtomicScript script = createTestScript(
                "Memory Test Script " + i,
                "echo 'Memory test script " + i + "'; echo 'This is a longer script content to test memory usage under load conditions with multiple lines and commands'; ls -la; pwd; whoami"
            );
            scripts.add(atomicScriptService.createAtomicScript(script));
            
            // Check memory usage every 100 scripts
            if (i % 100 == 0) {
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryIncrease = currentMemory - baselineMemory;
                
                // Memory increase should be reasonable (less than 100MB for 1000 scripts)
                assertTrue(memoryIncrease < 100 * 1024 * 1024, 
                          "Memory usage increased too much: " + (memoryIncrease / 1024 / 1024) + "MB after " + (i + 1) + " scripts");
            }
        }
        
        // Cleanup
        for (AtomicScript script : scripts) {
            atomicScriptService.deleteAtomicScript(script.getId());
        }
        
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalMemoryIncrease = finalMemory - baselineMemory;
        
        System.out.println("Memory Test Results:");
        System.out.println("Baseline memory: " + (baselineMemory / 1024 / 1024) + "MB");
        System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + "MB");
        System.out.println("Memory increase: " + (totalMemoryIncrease / 1024 / 1024) + "MB");
    }

    @Test
    void testScriptSearchPerformance() throws Exception {
        // Create scripts with various names for searching
        List<AtomicScript> scripts = new ArrayList<>();
        String[] categories = {"docker", "mysql", "redis", "nginx", "apache", "java", "python", "nodejs"};
        
        for (String category : categories) {
            for (int i = 0; i < 25; i++) {
                AtomicScript script = createTestScript(
                    category + " script " + i,
                    "echo 'This is a " + category + " related script for testing search performance'"
                );
                script.setDescription("A comprehensive " + category + " script for installation and configuration");
                scripts.add(atomicScriptService.createAtomicScript(script));
            }
        }
        
        // Test search performance
        Instant searchStartTime = Instant.now();
        
        for (String category : categories) {
            List<AtomicScript> searchResults = atomicScriptService.searchAtomicScripts(category);
            assertTrue(searchResults.size() >= 25, "Search should find at least 25 " + category + " scripts");
        }
        
        Instant searchEndTime = Instant.now();
        Duration searchDuration = Duration.between(searchStartTime, searchEndTime);
        
        // Search should be fast even with many scripts
        assertTrue(searchDuration.toMillis() < 5000, "Search took too long: " + searchDuration.toMillis() + "ms");
        
        // Cleanup
        for (AtomicScript script : scripts) {
            atomicScriptService.deleteAtomicScript(script.getId());
        }
        
        System.out.println("Search Performance Results:");
        System.out.println("Searched " + categories.length + " terms across " + scripts.size() + " scripts");
        System.out.println("Total search time: " + searchDuration.toMillis() + "ms");
        System.out.println("Average search time: " + (searchDuration.toMillis() / categories.length) + "ms per search");
    }

    private AtomicScript createTestScript(String name, String content) {
        AtomicScript script = new AtomicScript();
        script.setName(name);
        script.setScriptContent(content);
        script.setScriptType(ScriptType.BASH);
        script.setStatus(AtomicScript.Status.ACTIVE);
        script.setCreatedBy(1L);
        script.setEstimatedDuration(30);
        return script;
    }
}