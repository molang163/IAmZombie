package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlayerCoffinSleepMixinSourceTest {
    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void mixinRedirectsTheDaytimeWakeAndGuardsOnActiveNap() throws IOException {
        String src = read("src/main/java/dev/molang/iamzombieq/mixin/PlayerCoffinSleepMixin.java");
        assertTrue(src.contains("@Mixin(Player.class)"), "must target Player");
        assertTrue(src.contains("method = \"tick\""), "must hook Player.tick");
        assertTrue(src.contains("stopSleepInBed(ZZ)V"), "must intercept the stopSleepInBed invoke");
        assertTrue(src.contains("CoffinNapManager.isNapping"), "must guard on an active coffin nap");
    }

    @Test
    void coffinNapManagerExposesIsNapping() throws IOException {
        String src = read("src/main/java/dev/molang/iamzombieq/gameplay/CoffinNapManager.java");
        assertTrue(src.contains("public static boolean isNapping(UUID id)"), "CoffinNapManager must expose isNapping(UUID)");
    }
}
