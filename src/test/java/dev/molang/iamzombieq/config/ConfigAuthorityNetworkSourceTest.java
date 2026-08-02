package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigAuthorityNetworkSourceTest {
    private static final Path CONFIG_SOURCES = Path.of(
            "src/main/java/dev/molang/iamzombieq/config");

    @Test
    void facadeRegistersOnlyNonOptionalConfigurationPayloadsAndNeverBypassesMemoryConnections()
            throws Exception {
        Path sourcePath = CONFIG_SOURCES.resolve(
                "ConfigAuthorityRuntime.java");
        assertTrue(Files.isRegularFile(sourcePath));
        String source = Files.readString(sourcePath);

        assertTrue(source.contains("final class ConfigAuthorityRuntime"));
        assertFalse(source.contains(
                "public final class ConfigAuthorityRuntime"));
        assertFalse(Pattern.compile(
                "(?m)^\\s*public\\s+(?:static\\s+)?"
                        + "(?:void|boolean|int|float|List<)")
                .matcher(source)
                .find());
        assertTrue(source.contains("configurationToClient"));
        assertTrue(source.contains("configurationToServer"));
        assertTrue(source.contains("beginServerConfiguration"));
        assertFalse(source.contains("RegisterConfigurationTasksEvent"));
        assertTrue(source.contains("finishCurrentTask"));
        assertFalse(source.contains(".optional()"));
        assertFalse(source.contains("isMemoryConnection"));
        assertFalse(source.contains("FMLEnvironment"));
        assertFalse(source.contains("mod_version"));
        assertFalse(source.contains("1.0.3"));
    }

    @Test
    void publicRuntimeBridgeMutantIsForbiddenAndMigrationBootstrapIsTheOnlyRawPublicType()
            throws Exception {
        List<String> rawPublicTypes;
        try (Stream<Path> files = Files.list(CONFIG_SOURCES)) {
            rawPublicTypes = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Pattern.compile(
                                    "(?m)^public\\s+(?:final\\s+)?"
                                            + "(?:class|interface|record|enum)\\s+")
                                    .matcher(Files.readString(path))
                                    .find();
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertEquals(
                List.of("ConfigMigrationBootstrap.java"),
                rawPublicTypes);
    }

    @Test
    void fingerprintImplementationHashesTheVersionControlledAuthorityEntries()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/config/ConfigAuthorityProtocol.java"));
        assertTrue(source.contains("ConfigSchemaCatalog.load()"));
        assertTrue(source.contains(".entries()"));
        assertTrue(source.contains("MessageDigest"));
        assertFalse(source.contains("ModList"));
        assertFalse(source.contains("ModContainer"));
        assertFalse(source.contains("IAmZombieMod"));
    }

    @Test
    void missingAckDeadlineReusesVanillaAndCanNeverSelfFinish()
            throws Exception {
        String task = Files.readString(CONFIG_SOURCES.resolve(
                "ConfigAuthorityConfigurationTask.java"));
        String runtime = Files.readString(CONFIG_SOURCES.resolve(
                "ConfigAuthorityRuntime.java"));

        assertTrue(task.contains(
                "ServerCommonPacketListenerImpl.LATENCY_CHECK_INTERVAL"));
        assertTrue(task.contains("Util::getMillis"));
        assertTrue(task.contains("LongSupplier"));
        assertTrue(task.contains(
                "throw new ConfigAuthorityProtocolException("));
        assertTrue(task.contains("return false;"));
        assertFalse(task.contains("return true;"),
                "a task timeout must disconnect, never finish without ACK");
        assertFalse(task.contains("finishCurrentTask"));
        assertFalse(Pattern.compile("\\b15000L?\\b").matcher(task).find(),
                "the deadline must not duplicate vanilla's magic value");

        int validate = runtime.indexOf(
                "ConfigAuthorityConnections.acceptServerAck(");
        int finish = runtime.indexOf("context.finishCurrentTask(");
        assertTrue(validate >= 0 && finish > validate,
                "only an exact current ACK may finish the authority task");
    }
}
