package com.fufu.terminal.entity.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the 4-Type Script Classification System
 * Tests the enhanced ScriptType enum with feature capabilities
 */
class ScriptTypeTest {

    @Test
    @DisplayName("Should have exactly 4 script types defined")
    void shouldHaveExactlyFourScriptTypes() {
        ScriptType[] types = ScriptType.values();
        assertThat(types).hasSize(4);
        assertThat(types).containsExactlyInAnyOrder(
            ScriptType.STATIC_BUILTIN,
            ScriptType.CONFIGURABLE_BUILTIN,
            ScriptType.INTERACTIVE_BUILTIN,
            ScriptType.USER_SCRIPT
        );
    }

    @Test
    @DisplayName("Built-in script types should be correctly identified")
    void shouldIdentifyBuiltInScriptTypes() {
        assertThat(ScriptType.STATIC_BUILTIN.isBuiltIn()).isTrue();
        assertThat(ScriptType.CONFIGURABLE_BUILTIN.isBuiltIn()).isTrue();
        assertThat(ScriptType.INTERACTIVE_BUILTIN.isBuiltIn()).isTrue();
        assertThat(ScriptType.USER_SCRIPT.isBuiltIn()).isFalse();
    }

    @Test
    @DisplayName("Script types requiring parameters should be correctly identified")
    void shouldIdentifyParameterRequirements() {
        assertThat(ScriptType.STATIC_BUILTIN.requiresParameters()).isFalse();
        assertThat(ScriptType.CONFIGURABLE_BUILTIN.requiresParameters()).isTrue();
        assertThat(ScriptType.INTERACTIVE_BUILTIN.requiresParameters()).isFalse();
        assertThat(ScriptType.USER_SCRIPT.requiresParameters()).isTrue();
    }

    @Test
    @DisplayName("Interactive script types should be correctly identified")
    void shouldIdentifyInteractiveCapabilities() {
        assertThat(ScriptType.STATIC_BUILTIN.supportsInteraction()).isFalse();
        assertThat(ScriptType.CONFIGURABLE_BUILTIN.supportsInteraction()).isFalse();
        assertThat(ScriptType.INTERACTIVE_BUILTIN.supportsInteraction()).isTrue();
        assertThat(ScriptType.USER_SCRIPT.supportsInteraction()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ScriptType.class)
    @DisplayName("All script types should have valid display names")
    void shouldHaveValidDisplayNames(ScriptType type) {
        assertThat(type.getDisplayName())
            .isNotNull()
            .isNotBlank()
            .doesNotContainOnlyWhitespace();
    }

    @ParameterizedTest
    @EnumSource(ScriptType.class)
    @DisplayName("All script types should have valid descriptions")
    void shouldHaveValidDescriptions(ScriptType type) {
        assertThat(type.getDescription())
            .isNotNull()
            .isNotBlank()
            .doesNotContainOnlyWhitespace();
    }

    @ParameterizedTest
    @EnumSource(ScriptType.class)
    @DisplayName("All script types should have non-empty supported features")
    void shouldHaveSupportedFeatures(ScriptType type) {
        assertThat(type.getSupportedFeatures())
            .isNotNull()
            .isNotEmpty();
    }

    @Test
    @DisplayName("Static built-in scripts should have correct features")
    void staticBuiltInShouldHaveCorrectFeatures() {
        ScriptType type = ScriptType.STATIC_BUILTIN;
        
        assertThat(type.hasFeature(ScriptType.Feature.QUICK_EXECUTION)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.NO_PARAMS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.CODE_MANAGED)).isTrue();
        
        // Should not have these features
        assertThat(type.hasFeature(ScriptType.Feature.PARAMETERS)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.REAL_TIME_INTERACTION)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.DATABASE_STORED)).isFalse();
    }

    @Test
    @DisplayName("Configurable built-in scripts should have correct features")
    void configurableBuiltInShouldHaveCorrectFeatures() {
        ScriptType type = ScriptType.CONFIGURABLE_BUILTIN;
        
        assertThat(type.hasFeature(ScriptType.Feature.PARAMETERS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.INTELLIGENT_DECISIONS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.GEOGRAPHIC_AWARENESS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.CODE_MANAGED)).isTrue();
        
        // Should not have these features
        assertThat(type.hasFeature(ScriptType.Feature.NO_PARAMS)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.REAL_TIME_INTERACTION)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.DATABASE_STORED)).isFalse();
    }

    @Test
    @DisplayName("Interactive built-in scripts should have correct features")
    void interactiveBuiltInShouldHaveCorrectFeatures() {
        ScriptType type = ScriptType.INTERACTIVE_BUILTIN;
        
        assertThat(type.hasFeature(ScriptType.Feature.REAL_TIME_INTERACTION)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.DYNAMIC_PROMPTS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.USER_INPUT)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.CODE_MANAGED)).isTrue();
        
        // Should not have these features
        assertThat(type.hasFeature(ScriptType.Feature.NO_PARAMS)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.DATABASE_STORED)).isFalse();
    }

    @Test
    @DisplayName("User scripts should have correct features")
    void userScriptShouldHaveCorrectFeatures() {
        ScriptType type = ScriptType.USER_SCRIPT;
        
        assertThat(type.hasFeature(ScriptType.Feature.ADMIN_CONFIGURABLE)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.CUSTOM_PARAMETERS)).isTrue();
        assertThat(type.hasFeature(ScriptType.Feature.DATABASE_STORED)).isTrue();
        
        // Should not have these features
        assertThat(type.hasFeature(ScriptType.Feature.CODE_MANAGED)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.NO_PARAMS)).isFalse();
        assertThat(type.hasFeature(ScriptType.Feature.QUICK_EXECUTION)).isFalse();
    }

    @Test
    @DisplayName("Feature enum should have all expected values")
    void featureEnumShouldHaveAllExpectedValues() {
        ScriptType.Feature[] features = ScriptType.Feature.values();
        
        assertThat(features).contains(
            ScriptType.Feature.QUICK_EXECUTION,
            ScriptType.Feature.NO_PARAMS,
            ScriptType.Feature.PARAMETERS,
            ScriptType.Feature.CUSTOM_PARAMETERS,
            ScriptType.Feature.INTELLIGENT_DECISIONS,
            ScriptType.Feature.GEOGRAPHIC_AWARENESS,
            ScriptType.Feature.REAL_TIME_INTERACTION,
            ScriptType.Feature.DYNAMIC_PROMPTS,
            ScriptType.Feature.USER_INPUT,
            ScriptType.Feature.ADMIN_CONFIGURABLE,
            ScriptType.Feature.CODE_MANAGED,
            ScriptType.Feature.DATABASE_STORED
        );
    }

    @Test
    @DisplayName("Each script type should have mutually exclusive management features")
    void shouldHaveMutuallyExclusiveManagementFeatures() {
        for (ScriptType type : ScriptType.values()) {
            boolean isCodeManaged = type.hasFeature(ScriptType.Feature.CODE_MANAGED);
            boolean isDatabaseStored = type.hasFeature(ScriptType.Feature.DATABASE_STORED);
            
            // Each script should be either code-managed OR database-stored, not both
            assertThat(isCodeManaged ^ isDatabaseStored)
                .withFailMessage("Script type %s should be either code-managed OR database-stored", type)
                .isTrue();
        }
    }
}