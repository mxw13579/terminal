package com.fufu.terminal.integration;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.repository.AtomicScriptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SSH Terminal Management System 集成测试
 * 使用Testcontainers进行完整的数据库集成测试
 */
@SpringBootTest
@AutoConfigureWebMvc
@Testcontainers
@Transactional
class SshTerminalIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("terminal_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AtomicScriptRepository atomicScriptRepository;

    @Test
    void testAtomicScriptFullLifecycle() throws Exception {
        // 1. Create atomic script
        String createRequestBody = """
            {
                "name": "Test Integration Script",
                "description": "Integration test script",
                "scriptContent": "echo 'Integration test successful'",
                "scriptType": "BASH",
                "estimatedDuration": 30,
                "status": "ACTIVE"
            }
            """;

        String response = mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Test Integration Script")))
                .andExpect(jsonPath("$.scriptType", is("BASH")))
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID from response
        Long scriptId = Long.parseLong(response.split("\"id\":")[1].split(",")[0]);

        // 2. Read atomic script
        mockMvc.perform(get("/api/admin/atomic-scripts/{id}", scriptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Test Integration Script")))
                .andExpect(jsonPath("$.scriptContent", is("echo 'Integration test successful'")))
                .andExpect(jsonPath("$.scriptType", is("BASH")));

        // 3. Update atomic script
        String updateRequestBody = """
            {
                "id": %d,
                "name": "Updated Integration Script",
                "description": "Updated integration test script",
                "scriptContent": "echo 'Updated integration test successful'",
                "scriptType": "BASH",
                "estimatedDuration": 45,
                "status": "ACTIVE"
            }
            """.formatted(scriptId);

        mockMvc.perform(put("/api/admin/atomic-scripts/{id}", scriptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Integration Script")))
                .andExpect(jsonPath("$.scriptContent", is("echo 'Updated integration test successful'")));

        // 4. List all atomic scripts
        mockMvc.perform(get("/api/admin/atomic-scripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.id == %d)].name".formatted(scriptId), 
                    contains("Updated Integration Script")));

        // 5. Delete atomic script
        mockMvc.perform(delete("/api/admin/atomic-scripts/{id}", scriptId))
                .andExpect(status().isOk());

        // 6. Verify deletion
        mockMvc.perform(get("/api/admin/atomic-scripts/{id}", scriptId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAtomicScriptRepository() {
        // Create test script
        AtomicScript script = new AtomicScript();
        script.setName("Repository Test Script");
        script.setDescription("Testing repository operations");
        script.setScriptContent("echo 'Repository test'");
        script.setScriptType(ScriptType.BASH);
        script.setStatus(AtomicScript.Status.ACTIVE);
        script.setCreatedBy(1L);
        script.setCreatedAt(LocalDateTime.now());

        // Save script
        AtomicScript saved = atomicScriptRepository.save(script);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Repository Test Script");

        // Find by ID
        Optional<AtomicScript> found = atomicScriptRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Repository Test Script");

        // Find by status
        List<AtomicScript> activeScripts = atomicScriptRepository
            .findByStatusOrderBySortOrderAsc(AtomicScript.Status.ACTIVE);
        assertThat(activeScripts).hasSize(greaterThanOrEqualTo(1));
        assertThat(activeScripts).extracting(AtomicScript::getName)
            .contains("Repository Test Script");

        // Find by script type
        List<AtomicScript> bashScripts = atomicScriptRepository
            .findByScriptTypeAndStatusOrderBySortOrderAsc(ScriptType.BASH, AtomicScript.Status.ACTIVE);
        assertThat(bashScripts).hasSize(greaterThanOrEqualTo(1));
        assertThat(bashScripts).extracting(AtomicScript::getScriptType)
            .containsOnly(ScriptType.BASH);

        // Search by name
        List<AtomicScript> searchResults = atomicScriptRepository
            .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase("repository", "repository");
        assertThat(searchResults).hasSize(greaterThanOrEqualTo(1));
        assertThat(searchResults).extracting(AtomicScript::getName)
            .contains("Repository Test Script");

        // Delete script
        atomicScriptRepository.deleteById(saved.getId());
        Optional<AtomicScript> deletedScript = atomicScriptRepository.findById(saved.getId());
        assertThat(deletedScript).isEmpty();
    }

    @Test
    void testAtomicScriptValidation() throws Exception {
        // Test validation - empty name
        String invalidRequestBody1 = """
            {
                "name": "",
                "scriptContent": "echo 'test'",
                "scriptType": "BASH"
            }
            """;

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestBody1))
                .andExpect(status().isBadRequest());

        // Test validation - empty content
        String invalidRequestBody2 = """
            {
                "name": "Valid Name",
                "scriptContent": "",
                "scriptType": "BASH"
            }
            """;

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestBody2))
                .andExpect(status().isBadRequest());

        // Test validation - dangerous command
        String invalidRequestBody3 = """
            {
                "name": "Dangerous Script",
                "scriptContent": "rm -rf /",
                "scriptType": "BASH"
            }
            """;

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestBody3))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAtomicScriptsByStatus() throws Exception {
        // Create scripts with different statuses
        AtomicScript activeScript = createAndSaveScript("Active Script", AtomicScript.Status.ACTIVE);
        AtomicScript draftScript = createAndSaveScript("Draft Script", AtomicScript.Status.DRAFT);
        AtomicScript inactiveScript = createAndSaveScript("Inactive Script", AtomicScript.Status.INACTIVE);

        // Test get by ACTIVE status
        mockMvc.perform(get("/api/admin/atomic-scripts/status/ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].status", everyItem(is("ACTIVE"))));

        // Test get by DRAFT status
        mockMvc.perform(get("/api/admin/atomic-scripts/status/DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].status", everyItem(is("DRAFT"))));

        // Test get by INACTIVE status
        mockMvc.perform(get("/api/admin/atomic-scripts/status/INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].status", everyItem(is("INACTIVE"))));
    }

    @Test
    void testRegistryOperations() throws Exception {
        // Test reload registry
        mockMvc.perform(post("/api/admin/atomic-scripts/registry/reload"))
                .andExpect(status().isOk())
                .andExpect(content().string("统一脚本注册表已重新加载"));

        // Test get registry scripts
        mockMvc.perform(get("/api/admin/atomic-scripts/registry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testConcurrentScriptOperations() throws Exception {
        // This test simulates concurrent operations on atomic scripts
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String requestBody = """
                        {
                            "name": "Concurrent Script %d",
                            "scriptContent": "echo 'Concurrent test %d'",
                            "scriptType": "BASH",
                            "status": "ACTIVE"
                        }
                        """.formatted(index, index);

                    mockMvc.perform(post("/api/admin/atomic-scripts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify all scripts were created
        mockMvc.perform(get("/api/admin/atomic-scripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
    }

    @Test
    void testDatabaseTransactionRollback() throws Exception {
        // Get initial count
        long initialCount = atomicScriptRepository.count();

        try {
            // This should fail due to validation and rollback
            String invalidRequestBody = """
                {
                    "name": "Transaction Test",
                    "scriptContent": "rm -rf /",
                    "scriptType": "BASH"
                }
                """;

            mockMvc.perform(post("/api/admin/atomic-scripts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequestBody))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            // Expected to fail
        }

        // Verify count hasn't changed
        long finalCount = atomicScriptRepository.count();
        assertThat(finalCount).isEqualTo(initialCount);
    }

    private AtomicScript createAndSaveScript(String name, AtomicScript.Status status) {
        AtomicScript script = new AtomicScript();
        script.setName(name);
        script.setScriptContent("echo '" + name + "'");
        script.setScriptType(ScriptType.BASH);
        script.setStatus(status);
        script.setCreatedBy(1L);
        script.setCreatedAt(LocalDateTime.now());
        return atomicScriptRepository.save(script);
    }
}