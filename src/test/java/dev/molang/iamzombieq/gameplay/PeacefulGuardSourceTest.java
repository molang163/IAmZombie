package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level wiring guard for the server-side Peaceful guard. These pieces have no pure-logic surface to unit-test
 * (mixin application + NeoForge event registration are runtime concerns validated by runGameTestServer), so this
 * asserts the wiring that would silently disable the feature if dropped: the chokepoint mixin is registered and
 * targets the one difficulty entry point, the startup correction is subscribed and registered, and every path shares
 * the single {@code PeacefulGuard} predicate.
 */
class PeacefulGuardSourceTest {
    private static final Path SRC = Path.of("src/main/java/dev/molang/iamzombieq");
    private static final Path RES = Path.of("src/main/resources");

    private static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void chokepointMixinIsRegisteredAndTargetsSetDifficulty() throws IOException {
        assertTrue(read(RES.resolve("iamzombieq.mixins.json")).contains("\"MinecraftServerMixin\""),
                "MinecraftServerMixin must be registered in the common mixins list");

        String mixin = read(SRC.resolve("mixin/MinecraftServerMixin.java"));
        assertTrue(mixin.contains("@Mixin(MinecraftServer.class)"), "targets MinecraftServer");
        assertTrue(mixin.contains("setDifficulty(Lnet/minecraft/world/Difficulty;Z)V"),
                "coerces the single setDifficulty chokepoint");
        assertTrue(mixin.contains("@ModifyVariable"), "modifies the difficulty argument");
        assertTrue(mixin.contains("PeacefulGuard.sanitize"), "routes through the shared guard");
    }

    @Test
    void startupCorrectionIsSubscribedAndRegistered() throws IOException {
        String events = read(SRC.resolve("gameplay/DifficultyGuardEvents.java"));
        assertTrue(events.contains("@SubscribeEvent"), "is an event subscriber");
        assertTrue(events.contains("ServerStartedEvent"), "fires on server started (player list ready, no players)");
        assertTrue(events.contains("PeacefulGuard.enforce"), "applies the startup correction");

        assertTrue(read(SRC.resolve("IAmZombieMod.java")).contains("DifficultyGuardEvents.class"),
                "DifficultyGuardEvents must be registered on the game event bus");
    }

    @Test
    void everyPathSharesTheSinglePeacefulPredicate() throws IOException {
        String guard = read(SRC.resolve("gameplay/PeacefulGuard.java"));
        assertTrue(guard.contains("Difficulty.PEACEFUL"), "the guard defines the forbidden difficulty");
        assertTrue(guard.contains("FALLBACK = Difficulty.EASY"), "Peaceful is replaced with Easy");
        assertTrue(guard.contains("getWorldData().getDifficulty()"), "enforce inspects the stored difficulty");
        assertTrue(guard.contains("setDifficulty(FALLBACK"), "enforce routes the correction back through setDifficulty");

        assertTrue(read(SRC.resolve("mixin/DifficultyCommandMixin.java")).contains("PeacefulGuard.isForbidden"),
                "the command rejection reuses the shared predicate");
    }
}
