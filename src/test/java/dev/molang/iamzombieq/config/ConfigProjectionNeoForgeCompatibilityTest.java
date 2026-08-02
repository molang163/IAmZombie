package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.molang.iamzombieq.IAmZombiePreferencesConfig;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigProjectionNeoForgeCompatibilityTest {
    @Test
    void legacyProjectionsAreImmediatelyCorrectForRegisteredNeoForgeSpecs()
            throws Exception {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        LegacyConfigParser.Parsed legacy =
                LegacyConfigParser.parse(LegacyConfigParserTest.fixtureBytes());

        assertNeoForgeCorrect(
                MigrationTarget.SERVER,
                ConfigProjection.project(
                        MigrationTarget.SERVER, legacy, schema),
                schema,
                IAmZombieServerConfig.SPEC);
        assertNeoForgeCorrect(
                MigrationTarget.PREFERENCES,
                ConfigProjection.project(
                        MigrationTarget.PREFERENCES, legacy, schema),
                schema,
                IAmZombiePreferencesConfig.SPEC);
    }

    private static void assertNeoForgeCorrect(
            MigrationTarget target,
            Map<String, Object> projection,
            ConfigSchemaCatalog schema,
            net.neoforged.neoforge.common.ModConfigSpec spec) {
        String encoded = ConfigProjectionCodec.encode(
                target, projection, schema);
        CommentedConfig parsed =
                new TomlParser().parse(new StringReader(encoded));
        assertTrue(
                spec.isCorrect(parsed),
                () -> target + " migration output would be corrected by NeoForge");
    }
}
