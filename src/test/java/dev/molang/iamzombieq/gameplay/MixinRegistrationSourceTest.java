package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
        Set<String> registered = new TreeSet<>();
        for (String entry : jsonArray("client")) {
            assertTrue(entry.startsWith("client."),
                    "client[] entries must use the client. package prefix, got: " + entry);
            registered.add(entry.substring("client.".length()));
        }
        assertEquals(registered, sourceFiles(MIXIN_ROOT.resolve("client")),
                "mixin/client/ *.java files and client[] entries must match 1:1");
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
        String json = Files.readString(MIXIN_JSON);
        Matcher array = Pattern.compile("\"" + arrayName + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        assertTrue(array.find(), "iamzombieq.mixins.json must declare a \"" + arrayName + "\" array");
        Set<String> entries = new TreeSet<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"").matcher(array.group(1));
        while (entry.find()) {
            entries.add(entry.group(1));
        }
        return entries;
    }
}
