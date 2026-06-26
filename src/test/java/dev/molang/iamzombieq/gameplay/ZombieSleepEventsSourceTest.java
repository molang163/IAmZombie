package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieSleepEventsSourceTest {
    private static final Path SLEEP_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieSleepEvents.java");
    private static final Path CONFIG_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");

    @Test
    void bedExplosionUsesConfigValues() throws IOException {
        String source = Files.readString(SLEEP_SOURCE);
        String config = Files.readString(CONFIG_SOURCE);

        assertTrue(config.contains("BED_EXPLOSION_POWER"), "bed explosion power should be configurable");
        assertTrue(config.contains("BED_EXPLOSION_CAUSES_FIRE"), "bed explosion fire behavior should be configurable");
        assertTrue(source.contains("ZombieSleepRules.bedExplosionSettings"), "runtime should build bed explosion settings from config");
        assertTrue(source.contains("settings.power()"), "explosion should use configured power");
        assertTrue(source.contains("settings.causesFire()"), "explosion should use configured fire behavior");
    }
}
