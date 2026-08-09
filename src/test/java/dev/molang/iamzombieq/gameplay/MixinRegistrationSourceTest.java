package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariant that every mixin must be registered in {@code iamzombieq.mixins.json} in both directions and
 * for EVERY mixin: before this test, only about half the mixins had scattered registration
 * assertions, and a mixin silently dropped from the json would fail no test at all. Direction 1:
 * every *Mixin.java under mixin/ (common) and mixin/client/ appears in the
 * matching array. Direction 2: every registered entry has a source file (no ghost entries).
 */
class MixinRegistrationSourceTest {
    private static final Path MIXIN_ROOT = Path.of("src/main/java/dev/molang/iamzombieq/mixin");
    private static final Path MIXIN_JSON = Path.of("src/main/resources/iamzombieq.mixins.json");

    @Test
    void everyCommonMixinFileIsRegisteredAndViceVersa() throws IOException {
        assertEquals(jsonArray("mixins"), sourceFiles(MIXIN_ROOT),
                "mixin/ *.java files and mixins[] entries must match 1:1 (common side)");
    }

    @Test
    void everyClientMixinFileIsRegisteredAndViceVersa() throws IOException {
        Set<String> candidateSources = sourceFiles(MIXIN_ROOT.resolve("client"));
        Set<String> declaredCandidates = new TreeSet<>();
        String rawMixinJson = Files.readString(MIXIN_JSON);
        Set<String> rawClientEntries = jsonArray(rawMixinJson, "client");
        assertEquals(candidateSources.size() - 1, jsonArrayEntryCount(rawMixinJson, "client"),
                "the renderer placeholder must represent exactly two candidates without duplicate entries");
        for (String entry : rawClientEntries) {
            if (entry.equals("${player_renderer_mixin}")) {
                declaredCandidates.add("AvatarRendererMixin");
                declaredCandidates.add("PlayerRendererMixin");
                continue;
            }
            assertTrue(entry.startsWith("client."),
                    "client[] entries must use the client. package prefix, got: " + entry);
            declaredCandidates.add(entry.substring("client.".length()));
        }
        assertEquals(declaredCandidates, candidateSources,
                "the canonical template must declare every client mixin candidate exactly once");

        String buildNode = System.getProperty("iamzombieq.test.build.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                        .contains(buildNode),
                "Gradle must identify the node whose processed resource is on the test classpath");
        Set<String> expectedProcessed = new TreeSet<>(candidateSources);
        expectedProcessed.remove("AvatarRendererMixin");
        expectedProcessed.remove("PlayerRendererMixin");
        expectedProcessed.add(buildNode.equals("1.21.8")
                ? "PlayerRendererMixin"
                : "AvatarRendererMixin");

        String processedJson = processedMixinJson();
        Set<String> processed = new TreeSet<>();
        assertEquals(expectedProcessed.size(), jsonArrayEntryCount(processedJson, "client"),
                "the processed client mixin array must not hide duplicate entries");
        for (String entry : jsonArray(processedJson, "client")) {
            assertTrue(entry.startsWith("client."),
                    "processed client[] entries must use the client. package prefix, got: " + entry);
            processed.add(entry.substring("client.".length()));
        }
        assertEquals(expectedProcessed, processed,
                "the processed client mixin list must select exactly the renderer available on its node");
    }

    @Test
    void arraysAreNonEmptyAndDisjoint() throws IOException {
        Set<String> common = jsonArray("mixins");
        Set<String> client = jsonArray("client");
        assertFalse(common.isEmpty(), "mixins[] must not be empty");
        assertFalse(client.isEmpty(), "client[] must not be empty");
        common.retainAll(client);
        assertTrue(common.isEmpty(), "an entry must not appear in both mixins[] and client[]");
    }

    /** Class names of the *.java files directly inside {@code dir} (subpackages not descended). */
    private static Set<String> sourceFiles(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "missing mixin package dir: " + dir);
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".java") && !n.equals("package-info.java"))
                    .forEach(n -> names.add(n.substring(0, n.length() - ".java".length())));
        }
        return names;
    }

    /** Quoted entries of the named top-level array in iamzombieq.mixins.json. */
    private static Set<String> jsonArray(String arrayName) throws IOException {
        return jsonArray(Files.readString(MIXIN_JSON), arrayName);
    }

    private static Set<String> jsonArray(String json, String arrayName) {
        Matcher array = Pattern.compile("\"" + arrayName + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        assertTrue(array.find(), "iamzombieq.mixins.json must declare a \"" + arrayName + "\" array");
        Set<String> entries = new TreeSet<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"").matcher(array.group(1));
        while (entry.find()) {
            entries.add(entry.group(1));
        }
        return entries;
    }

    private static int jsonArrayEntryCount(String json, String arrayName) {
        Matcher array = Pattern.compile("\"" + arrayName + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        assertTrue(array.find(), "iamzombieq.mixins.json must declare a \"" + arrayName + "\" array");
        int count = 0;
        Matcher entry = Pattern.compile("\"([^\"]+)\"").matcher(array.group(1));
        while (entry.find()) {
            count++;
        }
        return count;
    }

    private static String processedMixinJson() throws IOException {
        try (InputStream input = MixinRegistrationSourceTest.class.getClassLoader()
                .getResourceAsStream("iamzombieq.mixins.json")) {
            assertNotNull(input, "processed iamzombieq.mixins.json must be present on the test classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
