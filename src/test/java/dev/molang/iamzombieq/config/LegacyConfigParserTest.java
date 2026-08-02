package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LegacyConfigParserTest {
    private static final String FIXTURE =
            "/dev/molang/iamzombieq/config/migration/parser/legacy-complete.toml";

    @Test
    void parsesAllFiftyFiveRealLegacyKeysAndTheirNonDefaultTypedValues() throws IOException {
        LegacyConfigParser.Parsed parsed = LegacyConfigParser.parse(fixtureBytes());
        Map<String, Object> values = parsed.rawValues();
        Set<String> expected = ConfigKeyCatalog.entries().stream()
                .map(ConfigKeyCatalog.Entry::legacyTomlKey)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(55, values.size());
        assertEquals(expected, values.keySet());
        assertFalse(values.keySet().stream().anyMatch(key -> key.startsWith("legacy.")));
        assertEquals(Boolean.TRUE, values.get("debugLogging"));
        assertEquals(9L, values.get("startingRottenFlesh"));
        assertEquals(0.1D, values.get("easyInfectionChance"));
        assertEquals(
                List.of("minecraft:rotten_flesh", "iamzombieq:super_rotten_flesh"),
                values.get("zombieFoods"));
        assertEquals(Boolean.FALSE, values.get("herobrineJoltEnabled"));
        assertEquals(161L, values.get("t1CarrionStrengthDurationTicks"));
        assertEquals(161L, values.get("honeyNauseaDurationTicks"));
        assertTrue(parsed.comments().containsKey("debugLogging"));
        assertTrue(parsed.comments().containsKey("t1CarrionStrengthDurationTicks"));
    }

    @Test
    void strictUtf8TomlRejectsMalformedBytesSyntaxAndDuplicateKeys() {
        byte[] malformedUtf8 = {(byte) 0xc3, 0x28};
        assertThrows(IllegalArgumentException.class, () -> LegacyConfigParser.parse(malformedUtf8));
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyConfigParser.parse(utf8("value = [1,\n")));
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyConfigParser.parse(utf8("debugLogging = true\ndebugLogging = false\n")));
    }

    @Test
    void unknownLegacyDataAndCommentsRemainRawEvidenceForProjectionToDiscard() {
        LegacyConfigParser.Parsed parsed = LegacyConfigParser.parse(utf8(
                "# legacy operator note\n"
                        + "debugLogging = true\n"
                        + "# extension note\n"
                        + "thirdPartyExtension = \"raw-only\"\n"));

        assertEquals(Boolean.TRUE, parsed.rawValues().get("debugLogging"));
        assertEquals("raw-only", parsed.rawValues().get("thirdPartyExtension"));
        assertEquals("legacy operator note", parsed.comments().get("debugLogging"));
        assertEquals("extension note", parsed.comments().get("thirdPartyExtension"));
    }

    static byte[] fixtureBytes() throws IOException {
        try (InputStream input = LegacyConfigParserTest.class.getResourceAsStream(FIXTURE)) {
            if (input == null) {
                throw new AssertionError("missing " + FIXTURE);
            }
            return input.readAllBytes();
        }
    }

    static String fixtureText() throws IOException {
        return new String(fixtureBytes(), StandardCharsets.UTF_8);
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
