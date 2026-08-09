package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationJavaRuntimeMatrixTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedMatrixMatchesTheNodeToolchainAndClassFile() throws IOException {
        String nodeId = requiredProperty("iamzombieq.test.build.nodeId");
        int requiredJava = Integer.parseInt(requiredProperty(
                "iamzombieq.test.build.requiredJava"));
        int runtimeJava = Integer.parseInt(requiredProperty(
                "iamzombieq.test.runtimeJavaFeature"));
        Set<Integer> expectedFeatures = featureSet(requiredProperty(
                "iamzombieq.test.migrationJavaFeatures"));
        Set<Integer> frozenFeatures = Set.of("1.21.8", "1.21.10")
                        .contains(nodeId)
                ? Set.of(22, 25)
                : Set.of(25);

        assertEquals(runtimeJava, Runtime.version().feature());
        assertTrue(expectedFeatures.contains(runtimeJava));
        assertEquals(frozenFeatures, expectedFeatures);
        assertTrue(expectedFeatures.contains(requiredJava));
        assertEquals(expectedFeatures, MigrationJavaRuntimeMatrix.runtimeFeatures());

        InputStream raw = MigrationJavaRuntimeMatrix.class
                .getResourceAsStream("MigrationJavaRuntimeMatrix.class");
        assertNotNull(raw);
        try (DataInputStream classFile = new DataInputStream(raw)) {
            assertEquals(0xCAFEBABE, classFile.readInt());
            classFile.readUnsignedShort();
            assertEquals(requiredJava + 44, classFile.readUnsignedShort());
        }
    }

    @Test
    void productionEntryPointsGateTheRuntimeBeforeOpeningTheirPorts()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/config/"
                        + "ProductionConfigMigration.java"));
        String gate = "MigrationJavaRuntimeMatrix.requireSupported(";
        int serverGate = source.indexOf(gate);
        int serverPort = source.indexOf("ProductionPort.server(");
        int preferencesGate = source.indexOf(gate, serverGate + gate.length());
        int preferencesPort = source.indexOf("ProductionPort.preferences(");

        assertTrue(serverGate >= 0 && serverGate < serverPort);
        assertTrue(preferencesGate > serverGate
                && preferencesGate < preferencesPort);
        assertEquals(
                -1,
                source.indexOf(gate, preferencesGate + gate.length()),
                "the runtime policy must have exactly two production entry gates");
    }

    @Test
    void metadataPublishesTheExactDisjointRuntimeRange() throws Exception {
        String expectedRange = requiredProperty(
                "iamzombieq.test.migrationJavaVersionRange");
        VersionRange parsed = VersionRange.createFromVersionSpec(expectedRange);
        for (int allowed : MigrationJavaRuntimeMatrix.runtimeFeatures()) {
            assertTrue(parsed.containsVersion(
                    new DefaultArtifactVersion(Integer.toString(allowed))));
        }
        for (int rejected : new int[] {21, 23, 24}) {
            assertFalse(parsed.containsVersion(
                    new DefaultArtifactVersion(Integer.toString(rejected))));
        }
        try (InputStream stream = MigrationJavaRuntimeMatrixTest.class
                .getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(stream);
            String metadata = new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("[features.iamzombieq]"));
            assertTrue(metadata.contains(
                    "javaVersion=\"" + expectedRange + "\""));
        }
    }

    @Test
    void unsupportedFeaturesFailBeforeAnyMigrationPathAccess() {
        Path global = temporaryDirectory.resolve("config")
                .toAbsolutePath()
                .normalize();
        Path world = temporaryDirectory.resolve("world/serverconfig")
                .toAbsolutePath()
                .normalize();
        Path legacy = global.resolve(ActualTargetResolver.LEGACY_BASENAME);
        Path target = world.resolve(ActualTargetResolver.SERVER_BASENAME);
        Set<Integer> allowed = MigrationJavaRuntimeMatrix.runtimeFeatures();

        for (int feature : new int[] {21, 23, 24}) {
            assertFalse(allowed.contains(feature));
            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> MigrationJavaRuntimeMatrix.requireSupported(
                            feature, legacy, target));
            assertEquals(legacy, failure.legacy());
            assertEquals(target, failure.target());
            assertEquals(
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    failure.phase());
            assertEquals("java-runtime", failure.artifact());
            assertEquals("runtime-java-policy", failure.operation());
            assertTrue(failure.reason().contains(
                    "Java feature " + feature));
            assertTrue(failure.reason().contains(
                    "before metadata read, lock, stage, or target access"));
            assertFalse(failure.synthetic());
        }

        for (int feature : allowed) {
            MigrationJavaRuntimeMatrix.requireSupported(
                    feature, legacy, target);
        }
        assertFalse(Files.exists(global));
        assertFalse(Files.exists(world));
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        assertNotNull(value, "Gradle must inject " + name);
        return value;
    }

    private static Set<Integer> featureSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toUnmodifiableSet());
    }
}
