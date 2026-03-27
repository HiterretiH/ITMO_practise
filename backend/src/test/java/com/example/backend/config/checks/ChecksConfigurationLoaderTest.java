package com.example.backend.config.checks;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksConfigurationLoaderTest {

    @Test
    void loadClasspath_parsesChecksConfig() {
        ChecksConfigRoot root = ChecksConfigurationLoader.loadClasspath("checks-config.json");
        assertEquals(1, root.schemaVersion());
        assertEquals(18, root.rules().size());
        assertTrue(root.rules().stream().anyMatch(r -> "ft21".equals(r.id()) && r.enabled()));
    }

    @Test
    void ft21_hasParams() {
        ChecksConfigRoot root = ChecksConfigurationLoader.loadClasspath("checks-config.json");
        CheckRuleDefinition ft21 =
                root.rules().stream().filter(r -> "ft21".equals(r.id())).findFirst().orElseThrow();
        assertEquals(1.25, JsonRuleParams.doubleValue(ft21.params(), "indentCmExpected", 0));
    }

    @Test
    void duplicateId_throws() {
        ChecksConfigRoot bad =
                new ChecksConfigRoot(
                        1,
                        List.of(
                                new CheckRuleDefinition("ft4", true, "a", null, null, null, null),
                                new CheckRuleDefinition("ft4", true, "b", null, null, null, null)));
        assertThrows(IllegalStateException.class, () -> ChecksConfigurationLoader.validate(bad));
    }

    @Test
    void unknownId_throws() {
        ChecksConfigRoot bad =
                new ChecksConfigRoot(1, List.of(new CheckRuleDefinition("ft99", true, "x", null, null, null, null)));
        assertThrows(IllegalStateException.class, () -> ChecksConfigurationLoader.validate(bad));
    }
}
