package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ZombieMobTargetingRulesSourceTest {
    @Test
    void legacyBooleanEntryOnlyBridgesToTheTypedCore() throws IOException {
        String source = SourceScan.stripComments(
                SourceScan.mainJava("dev/molang/iamzombieq/rules/ZombieMobTargetingRules.java"));
        String legacy = SourceScan.compact(SourceScan.methodBody(source, "boolean retaliating,"));
        String expectedBridge = "returnshouldIgnore(kind,form,"
                + "newTargetingOverrides(retaliating,angeredNeutral));";

        assertEquals(1, SourceScan.countOccurrences(legacy, expectedBridge),
                "the legacy rules entry should only name and forward its two override inputs");
        assertEquals("{" + expectedBridge + "}", legacy.substring(legacy.indexOf('{')),
                "the legacy rules entry should contain no behavior beyond the compatibility bridge");
        assertFalse(legacy.contains("attacksZombiePlayer("),
                "the legacy rules entry should not duplicate the targeting decision");

        String core = SourceScan.compact(SourceScan.methodBody(source, "TargetingOverrides overrides"));
        int overrides = core.indexOf("overrides.retaliating()||overrides.angeredNeutral()");
        int matrix = core.indexOf("attacksZombiePlayer(kind,form)");
        assertTrue(overrides >= 0 && matrix > overrides,
                "the typed core should apply overrides before consulting the attacker matrix");

        String contextSource = SourceScan.compact(
                SourceScan.mainJava("dev/molang/iamzombieq/rules/TargetingOverrides.java"));
        assertTrue(contextSource.contains(
                        "publicrecordTargetingOverrides(booleanretaliating,booleanangeredNeutral){}"),
                "the pure context should retain the two named boolean components in order");
        assertFalse(contextSource.contains("net.minecraft"),
                "the targeting override context must not depend on Minecraft types");
        assertFalse(contextSource.contains("net.neoforged"),
                "the targeting override context must not depend on NeoForge types");
    }
}
