package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the localization invariant that {@code en_us.json} and {@code zh_cn.json} expose identical key sets
 * assertion (not a per-key spot check): a key added to one locale but not the other renders as a raw
 * translation key in game and previously had no automated guard at all.
 *
 * <p>Lang files are flat one-key-per-line JSON, so a line-based extraction is exact here; no JSON
 * library is on the test classpath by design (JUnit-only, see build.gradle).
 */
class LangParityDataTest {
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");

    @Test
    void modLocalesHaveIdenticalKeySets() throws IOException {
        Set<String> en = keysOf(Path.of("src/main/resources/assets/iamzombieq/lang/en_us.json"));
        Set<String> zh = keysOf(Path.of("src/main/resources/assets/iamzombieq/lang/zh_cn.json"));
        assertFalse(en.isEmpty(), "en_us.json should contain translation keys");
        assertEquals(en, zh, "iamzombieq en_us/zh_cn keySets must be identical");
    }

    @Test
    void vanillaOverrideLocalesHaveIdenticalKeySets() throws IOException {
        Path root = Path.of("src/main/resources/assets/minecraft/lang");
        if (!Files.isDirectory(root)) {
            return; // no vanilla-key overrides shipped; nothing to compare
        }
        Set<String> en = keysOf(root.resolve("en_us.json"));
        Set<String> zh = keysOf(root.resolve("zh_cn.json"));
        assertEquals(en, zh, "vanilla-override en_us/zh_cn keySets must be identical");
    }

    @Test
    void noDuplicateKeysWithinEitherLocale() throws IOException {
        for (String name : new String[] {"en_us", "zh_cn"}) {
            Path file = Path.of("src/main/resources/assets/iamzombieq/lang/" + name + ".json");
            long lines = Files.readAllLines(file).stream().filter(l -> KEY.matcher(l).find()).count();
            assertEquals(keysOf(file).size(), lines,
                    name + ".json must not declare the same key twice (later entry silently wins)");
        }
    }

    private static Set<String> keysOf(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing lang file: " + file);
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            Matcher m = KEY.matcher(line);
            if (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }
}
