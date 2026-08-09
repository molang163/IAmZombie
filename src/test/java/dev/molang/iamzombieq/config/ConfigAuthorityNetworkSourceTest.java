package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigAuthorityNetworkSourceTest {
    private static final Path CONFIG_SOURCES = Path.of(
            "src/main/java/dev/molang/iamzombieq/config");

    @Test
    void authorityDeadlineClockUsesTheNodeNativeUtilPackage()
            throws Exception {
        String relative =
                "dev/molang/iamzombieq/config/ConfigAuthorityConfigurationTask.java";
        String source = Files.readString(Path.of("src/main/java").resolve(relative));
        String node = StonecutterCapabilityMatrix.nodeId();
        String generated = Files.readString(Path.of("versions", node,
                "build/generated/stonecutter/main/java").resolve(relative));
        String active = SourceScan.compact(SourceScan.stripComments(generated));
        boolean movedUtil = Set.of("26.2.x", "26.1.x", "1.21.11")
                .contains(node);

        assertEquals(1, SourceScan.countOccurrences(
                source, "//? if >=1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(
                source, "import net.minecraft.util.Util;"));
        assertEquals(1, SourceScan.countOccurrences(
                source, "import net.minecraft.Util;"));
        assertEquals(movedUtil ? 1 : 0,
                SourceScan.countOccurrences(
                        active, "importnet.minecraft.util.Util;"));
        assertEquals(movedUtil ? 0 : 1,
                SourceScan.countOccurrences(
                        active, "importnet.minecraft.Util;"));
        assertEquals(1, SourceScan.countOccurrences(
                active, "this(snapshot,Util::getMillis);"));
    }

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

    @Test
    void legacyAuthorityTickSchedulerIsExactAndLowNodeOnly()
            throws Exception {
        String task = Files.readString(CONFIG_SOURCES.resolve(
                "ConfigAuthorityConfigurationTask.java"));
        String runtime = Files.readString(CONFIG_SOURCES.resolve(
                "ConfigAuthorityRuntime.java"));
        String mixin = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/mixin/"
                        + "ServerConfigurationPacketListenerMixin.java"));
        String node = StonecutterCapabilityMatrix.nodeId();
        Path generatedRoot = Path.of(
                "versions", node, "build/generated/stonecutter/main/java");
        String generatedTask = Files.readString(generatedRoot.resolve(
                "dev/molang/iamzombieq/config/"
                        + "ConfigAuthorityConfigurationTask.java"));
        String generatedRuntime = Files.readString(generatedRoot.resolve(
                "dev/molang/iamzombieq/config/ConfigAuthorityRuntime.java"));
        String generatedMixin = Files.readString(generatedRoot.resolve(
                "dev/molang/iamzombieq/mixin/"
                        + "ServerConfigurationPacketListenerMixin.java"));
        String activeTask = SourceScan.compact(
                SourceScan.stripComments(generatedTask));
        String activeRuntime = SourceScan.compact(
                SourceScan.stripComments(generatedRuntime));
        String activeMixin = SourceScan.compact(
                SourceScan.stripComments(generatedMixin));
        boolean legacyNode = node.equals("1.21.8");

        String activeTickOverride =
                "//? if >=1.21.10 {\n    @Override";
        String inactiveTickOverride =
                "//? if >=1.21.10 {\n    /*@Override";
        assertTrue(
                task.contains(activeTickOverride)
                        || task.contains(inactiveTickOverride),
                "the reversible source must retain the tick override boundary "
                        + "regardless of the active node");
        assertEquals(1, SourceScan.countOccurrences(
                activeTask, "publicbooleantick()"));
        assertEquals(legacyNode ? 0 : 1, SourceScan.countOccurrences(
                activeTask, "@Overridepublicbooleantick()"));

        assertTrue(mixin.contains("//? if <1.21.10 {"));
        assertTrue(mixin.contains(
                "@Shadow private ConfigurationTask currentTask"));
        assertTrue(mixin.contains("method = \"tick()V\""));
        assertTrue(mixin.contains("at = @At(\"RETURN\")"));
        assertTrue(mixin.contains("require = 1"));
        assertTrue(mixin.contains("\"tickLegacyServerConfiguration\""));
        assertTrue(SourceScan.compact(mixin).contains(
                "invokeExact(currentTask,listener)"));

        assertTrue(runtime.contains(
                "static void tickLegacyServerConfiguration("));
        assertTrue(runtime.contains(
                "instanceof ConfigAuthorityConfigurationTask authorityTask"));
        assertEquals(1, SourceScan.countOccurrences(
                runtime, "authorityTask.tick()"));
        int legacyStart = runtime.indexOf(
                "static void tickLegacyServerConfiguration(");
        int legacyEnd = runtime.indexOf("\n    /**", legacyStart);
        assertTrue(legacyStart >= 0 && legacyEnd > legacyStart);
        String legacyBridge = runtime.substring(legacyStart, legacyEnd);
        int clear = legacyBridge.indexOf(
                "ConfigAuthorityConnections.clear(connection)");
        int disconnect = legacyBridge.indexOf("listener.disconnect(");
        assertTrue(clear >= 0 && disconnect > clear,
                "legacy task failures must clear the exact session before disconnect");
        assertFalse(legacyBridge.substring(clear, disconnect)
                .contains("finishCurrentTask"));
        assertFalse(runtime.contains("getDeclaredField"));
        assertFalse(runtime.contains("setAccessible"));

        assertEquals(legacyNode ? 1 : 0, SourceScan.countOccurrences(
                activeMixin, "method=\"tick()V\""));
        assertEquals(legacyNode ? 1 : 0, SourceScan.countOccurrences(
                activeRuntime, "staticvoidtickLegacyServerConfiguration("));
    }
}
