package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the five-node Stonecutter scaffold without treating generated node directories as canonical source.
 */
class StonecutterScaffoldSourceTest {
    private static final Path SETTINGS = Path.of("settings.gradle");
    private static final Path CONTROLLER = Path.of("stonecutter.gradle.kts");
    private static final Path CENTRAL_PROPERTIES = Path.of("stonecutter.properties.toml");
    private static final Set<String> NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");

    @Test
    void settingsDeclareExactlyTheFrozenFiveNodeTopology() throws IOException {
        assertTrue(Files.isRegularFile(SETTINGS));
        String source = SourceScan.stripComments(Files.readString(SETTINGS));
        String settings = SourceScan.compact(source);

        assertTrue(settings.contains("id'dev.kikugie.stonecutter'version'0.9.6'"));
        assertTrue(settings.contains("centralScript='build.gradle'"));
        assertTrue(settings.contains("kotlinController=true"));
        Matcher versions = Pattern.compile(
                        "\\bversion\\s*\\(?\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)?")
                .matcher(source);
        Map<String, String> topology = new LinkedHashMap<>();
        while (versions.find()) {
            assertFalse(topology.containsKey(versions.group(1)), "duplicate node " + versions.group(1));
            topology.put(versions.group(1), versions.group(2));
        }
        assertEquals(
                Map.of(
                        "26.2.x", "26.2",
                        "26.1.x", "26.1",
                        "1.21.11", "1.21.11",
                        "1.21.10", "1.21.10",
                        "1.21.8", "1.21.8"),
                topology,
                "the topology must contain exactly the five frozen nodes");
        assertTrue(settings.contains("vcsVersion='26.2.x'"), "26.2.x must remain the canonical VCS node");
    }

    @Test
    void centralPropertiesPinTheFrozenCoordinatesRangesAndJavaLevels() throws IOException {
        Map<String, Map<String, String>> sections = parseToml(CENTRAL_PROPERTIES);
        assertEquals(NODES, sections.keySet().stream().filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet()));

        assertEquals(
                Map.of(
                        "mod.id", "iamzombieq",
                        "mod.name", "I Am Zombie?",
                        "mod.license", "MIT",
                        "mod.version", "1.1.3",
                        "mod.group", "dev.molang.iamzombieq"),
                sections.get(""));
        assertNode(
                sections,
                "26.2.x",
                "26.2",
                "[26.2,26.3)",
                "26.2.0.47-beta",
                "[26.2.0.12-beta,26.2.1-alpha)",
                "25",
                "25",
                "PRESENT",
                "PRESENT",
                "PRESENT");
        assertNode(
                sections,
                "26.1.x",
                "26.1",
                "[26.1,26.2)",
                "26.1.2.76",
                "[26.1.2.76,)",
                "25",
                "25",
                "PRESENT",
                "PRESENT",
                "PRESENT");
        assertNode(
                sections,
                "1.21.11",
                "1.21.11",
                "[1.21.11,1.21.12)",
                "21.11.45",
                "[21.11.44,)",
                "25",
                "25",
                "PRESENT",
                "N/A_PLATFORM_ABSENT",
                "PRESENT");
        assertNode(
                sections,
                "1.21.10",
                "1.21.10",
                "[1.21.10,1.21.11)",
                "21.10.64",
                "[21.10.64,)",
                "22",
                "22,25",
                "N/A_PLATFORM_ABSENT",
                "N/A_PLATFORM_ABSENT",
                "PRESENT");
        assertNode(
                sections,
                "1.21.8",
                "1.21.8",
                "[1.21.8,1.21.9)",
                "21.8.52",
                "[21.8.52,)",
                "22",
                "22,25",
                "N/A_PLATFORM_ABSENT",
                "N/A_PLATFORM_ABSENT",
                "N/A_PLATFORM_ABSENT");
    }

    @Test
    void controllerMatchesTheExecutingNodeAndAggregatesOnlyBoundedArtifactTasks() throws IOException {
        assertTrue(Files.isRegularFile(CONTROLLER));
        String controller = SourceScan.compact(SourceScan.stripComments(Files.readString(CONTROLLER)));
        String executingNode = System.getProperty("iamzombieq.test.nodeId");

        assertNotNull(executingNode, "Gradle must inject the executing Stonecutter node");
        assertTrue(NODES.contains(executingNode), "the executing node must belong to the frozen matrix");
        assertTrue(controller.contains("stonecutteractive\"" + executingNode + "\""),
                "source-reading tests must run only after switching the active project");
        assertTrue(controller.contains("tasks.register(\"chiseledBuild\")"));
        assertTrue(controller.contains("stonecutter.tasks.named(\"buildAndCollect\")"));
        assertFalse(controller.contains("runClient"));
        assertFalse(controller.contains("runServer"));
        assertFalse(controller.contains("runGameTestServer"));
    }

    @Test
    void nodeBuildConsumesCentralPropertiesWithoutChangingTheModVersion() throws IOException {
        String build = SourceScan.compact(SourceScan.stripComments(Files.readString(Path.of("build.gradle"))));

        for (String key : new String[] {
                "mod.id",
                "mod.name",
                "mod.license",
                "mod.version",
                "mod.group",
                "deps.minecraft",
                "mod.mc_compat",
                "deps.neo_loader",
                "deps.neo_range",
                "build.java",
                "migration.java_features",
                "platform.nautilus",
                "platform.humanoid_baby_equipment_layer",
                "platform.submit_node_collector_render_pipeline"
        }) {
            assertTrue(build.contains("'" + key + "'"), "build.gradle must consume central property " + key);
        }
        assertTrue(build.contains("version=modVersion"),
                "the Gradle and metadata version must come from the central release coordinate");
        assertFalse(build.contains("$modVersion+"), "the node identity must not be appended to the mod version");
        assertTrue(build.contains("archiveFileName=\"${modId}-${modVersion}+mc${minecraftVersion}.jar\""),
                "release JAR names must distinguish the exact Minecraft target without changing mod.version");
        assertTrue(build.contains("JavaLanguageVersion.of(requiredJava)"));
        assertTrue(build.contains("version=neoVersion"));
        assertTrue(build.contains("workingDir=rootProject.projectDir"),
                "all source-reading tests must execute against the active canonical root source");
        assertTrue(build.contains("rootProject.file('src/generated/resources')"));
        assertTrue(build.contains("rootProject.file('src/main/templates')"));
        assertTrue(build.contains("dependsOn('stonecutterGenerate')"));
        assertTrue(build.contains("register('buildAndCollect',Copy)"));
        assertTrue(build.contains("stonecutter.current.project"),
                "same-version JARs must be collected into distinct node destinations");

        // Current mainline gates must survive the scaffold conversion.
        assertTrue(build.contains("addModdingDependenciesTo(sourceSets.test)"));
        assertTrue(build.contains("configureDevClientRuntime"));
        assertTrue(build.contains("VerifyManualTestFunctionPackaging"));
        assertTrue(build.contains("VerifyEntityInteractAbi"));
        assertTrue(build.contains("dependsOn(verifyManualTestFunctionPackaging,verifyEntityInteractAbi)"));
        assertTrue(build.contains("abstractProperty<String>getExecutingNode()"));
        assertTrue(build.contains("executingNode.set(nodeId)"));
        assertTrue(build.contains("PlayerInteractEvent$EntityInteractSpecific"));
        assertTrue(build.contains("PlayerInteractEvent$EntityInteract;)V"));
        assertTrue(build.contains("PlayerInteractEvent$EntityInteractSpecific;)V"));
        assertTrue(build.contains("input.readInt()!=0xCAFEBABEasint"));
        assertTrue(build.contains("intmethodCount=input.readUnsignedShort()"));
        assertTrue(build.contains("name=='onEntityInteract'&&descriptor==GENERAL_HANDLER_DESCRIPTOR"));
        assertTrue(build.contains("name=='onEntityInteractSpecific'&&descriptor==SPECIFIC_HANDLER_DESCRIPTOR"));
        assertTrue(build.contains("!splitEvents&&!specificSymbols.isEmpty()"));
        assertTrue(build.contains("generalhandlers=1"));
        assertTrue(build.contains("EntityInteractSpecifichandlers=${expectedSpecificHandlers}"));
    }

    @Test
    void generatedMetadataCarriesTheExactReleaseVersion() throws IOException {
        assertEquals("1.1.3", parseToml(CENTRAL_PROPERTIES).get("").get("mod.version"));
        try (InputStream input = StonecutterScaffoldSourceTest.class
                .getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(input, "generated NeoForge metadata must be present on the test runtime");
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("version=\"1.1.3\""),
                    "generated metadata must carry the frozen 1.1.3 release coordinate");
            assertFalse(metadata.contains("${mod_version}"),
                    "release metadata must not retain an unexpanded version placeholder");
        }
    }

    @Test
    void mutexSerializesMinecraftArtifactCreationAndNoGeneratedNodeSourceIsTracked() throws IOException {
        Path mutex = Path.of("buildSrc", "src", "main", "kotlin", "neoforge-mutex.gradle.kts");
        assertTrue(Files.isRegularFile(Path.of("buildSrc", "build.gradle.kts")));
        assertTrue(Files.isRegularFile(mutex));
        String source = SourceScan.compact(SourceScan.stripComments(Files.readString(mutex)));
        assertTrue(source.contains("maxParallelUsages.set(1)"));
        assertTrue(source.contains("it==\"createMinecraftArtifacts\""));
        assertTrue(source.contains("usesService(mutex)"));

        for (String node : NODES) {
            assertFalse(Files.isDirectory(Path.of("versions", node, "src")),
                    "versions/" + node + "/src is generated state, never canonical source");
        }
    }

    private static void assertNode(
            Map<String, Map<String, String>> sections,
            String node,
            String minecraft,
            String minecraftRange,
            String neoForge,
            String neoForgeRange,
            String java,
            String migrationJavaFeatures,
            String nautilusCapability,
            String humanoidBabyEquipmentLayerCapability,
            String submitNodeCollectorRenderPipelineCapability) {
        assertEquals(
                Map.of(
                        "deps.minecraft", minecraft,
                        "mod.mc_compat", minecraftRange,
                        "deps.neo_loader", neoForge,
                        "deps.neo_range", neoForgeRange,
                        "build.java", java,
                        "migration.java_features", migrationJavaFeatures,
                        "platform.nautilus", nautilusCapability,
                        "platform.humanoid_baby_equipment_layer",
                                humanoidBabyEquipmentLayerCapability,
                        "platform.submit_node_collector_render_pipeline",
                                submitNodeCollectorRenderPipelineCapability),
                sections.get(node),
                "central properties mismatch for " + node);
    }

    static Map<String, Map<String, String>> parseToml(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "central Stonecutter properties must exist");
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String section = "";
        sections.put(section, new LinkedHashMap<>());
        for (String raw : Files.readAllLines(path)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[\"") && line.endsWith("\"]")) {
                section = line.substring(2, line.length() - 2);
                assertFalse(sections.containsKey(section), "duplicate TOML section " + section);
                sections.put(section, new LinkedHashMap<>());
                continue;
            }
            int equals = line.indexOf('=');
            assertTrue(equals > 0, "unsupported central TOML line: " + raw);
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            assertFalse(sections.get(section).containsKey(key), "duplicate key " + section + ":" + key);
            sections.get(section).put(key, value);
        }
        return sections;
    }
}
