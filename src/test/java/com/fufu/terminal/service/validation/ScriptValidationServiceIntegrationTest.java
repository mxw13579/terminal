package com.fufu.terminal.service.validation;

import com.fufu.terminal.entity.enums.InteractionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ScriptValidationService
 * Tests the enhanced CONFIRMATION handling and input validation
 */
@SpringBootTest
class ScriptValidationServiceIntegrationTest {

    private ScriptValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ScriptValidationService();
    }

    @Test
    void testConfirmationInputSanitization() {
        // Test various confirmation inputs
        assertEquals("yes", validationService.sanitizeUserInput("yes", InteractionType.CONFIRMATION));
        assertEquals("yes", validationService.sanitizeUserInput("YES", InteractionType.CONFIRMATION));
        assertEquals("yes", validationService.sanitizeUserInput("y", InteractionType.CONFIRMATION));
        assertEquals("yes", validationService.sanitizeUserInput("Y", InteractionType.CONFIRMATION));
        
        assertEquals("no", validationService.sanitizeUserInput("no", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("NO", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("n", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("N", InteractionType.CONFIRMATION));
        
        // Invalid inputs should default to "no"
        assertEquals("no", validationService.sanitizeUserInput("invalid", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("maybe", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("", InteractionType.CONFIRMATION));
    }

    @Test
    void testConfirmationInputValidation() {
        // Valid confirmation inputs
        ScriptValidationService.ValidationResult result = 
            validationService.validateUserInput("yes", InteractionType.CONFIRMATION);
        assertTrue(result.isValid());
        
        result = validationService.validateUserInput("no", InteractionType.CONFIRMATION);
        assertTrue(result.isValid());
        
        result = validationService.validateUserInput("y", InteractionType.CONFIRMATION);
        assertTrue(result.isValid());
        
        result = validationService.validateUserInput("n", InteractionType.CONFIRMATION);
        assertTrue(result.isValid());
        
        // Invalid confirmation inputs
        result = validationService.validateUserInput("invalid", InteractionType.CONFIRMATION);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Confirmation input must be yes/no/y/n", result.getErrors().get(0));
        
        result = validationService.validateUserInput("", InteractionType.CONFIRMATION);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Input cannot be empty", result.getErrors().get(0));
    }

    @Test
    void testTextInputValidation() {
        // Valid text input
        ScriptValidationService.ValidationResult result = 
            validationService.validateUserInput("valid text", InteractionType.INPUT_TEXT);
        assertTrue(result.isValid());
        
        // Empty text input
        result = validationService.validateUserInput("", InteractionType.INPUT_TEXT);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Input cannot be empty", result.getErrors().get(0));
        
        // Suspicious text input
        result = validationService.validateUserInput("<script>alert('xss')</script>", InteractionType.INPUT_TEXT);
        assertTrue(result.isValid()); // Should be valid but with warning
        assertEquals(1, result.getWarnings().size());
        assertEquals("Input contains potentially suspicious content", result.getWarnings().get(0));
    }

    @Test
    void testPasswordInputValidation() {
        // Valid password
        ScriptValidationService.ValidationResult result = 
            validationService.validateUserInput("mypassword123", InteractionType.INPUT_PASSWORD);
        assertTrue(result.isValid());
        
        // Empty password
        result = validationService.validateUserInput("", InteractionType.INPUT_PASSWORD);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Input cannot be empty", result.getErrors().get(0));
    }

    @Test
    void testScriptContentValidation() {
        // Valid script content
        ScriptValidationService.ValidationResult result = 
            validationService.validateScriptContent("echo 'Hello World'");
        assertTrue(result.isValid());
        
        // Empty script content
        result = validationService.validateScriptContent("");
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Script content cannot be empty", result.getErrors().get(0));
        
        result = validationService.validateScriptContent(null);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Script content cannot be empty", result.getErrors().get(0));
        
        // Dangerous script content
        result = validationService.validateScriptContent("rm -rf /");
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Script contains potentially dangerous commands", result.getErrors().get(0));
        
        // Script with unbalanced quotes (should have warning)
        result = validationService.validateScriptContent("echo 'unclosed quote");
        assertTrue(result.hasWarnings());
        assertEquals("Script syntax may be invalid", result.getWarnings().get(0));
    }

    @Test
    void testInputSanitization() {
        // HTML escaping for text input
        String sanitized = validationService.sanitizeUserInput("<script>alert('test')</script>", InteractionType.INPUT_TEXT);
        assertEquals("&lt;script&gt;alert(&#39;test&#39;)&lt;/script&gt;", sanitized);
        
        // Password should not be modified
        String password = "mypassword<>&\"'";
        String sanitizedPassword = validationService.sanitizeUserInput(password, InteractionType.INPUT_PASSWORD);
        assertEquals(password, sanitizedPassword);
        
        // Confirmation input normalization
        assertEquals("yes", validationService.sanitizeUserInput("  YES  ", InteractionType.CONFIRMATION));
        assertEquals("no", validationService.sanitizeUserInput("  NO  ", InteractionType.CONFIRMATION));
    }
}