package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Source-level wiring guard for the Peaceful policy. Mixin application and NeoForge event registration remain runtime
 * concerns validated by smoke runs; the selectable-array behavior itself is covered by {@link PeacefulGuardTest}.
 * This asserts the wiring that would silently disable the feature if dropped: the chokepoint mixin is registered and
 * targets the one difficulty entry point, the startup correction is subscribed and registered, and every path shares
 * the single {@code PeacefulGuard} predicate.
 */
class PeacefulGuardSourceTest {
    @Test
    void chokepointMixinIsRegisteredAndTargetsSetDifficulty() throws IOException {
        assertTrue(SourceScan.resource("iamzombieq.mixins.json").contains("\"MinecraftServerMixin\""),
                "MinecraftServerMixin must be registered in the common mixins list");

        String mixin = SourceScan.mainJava("dev/molang/iamzombieq/mixin/MinecraftServerMixin.java");
        assertTrue(mixin.contains("@Mixin(MinecraftServer.class)"), "targets MinecraftServer");
        assertTrue(mixin.contains("setDifficulty(Lnet/minecraft/world/Difficulty;Z)V"),
                "coerces the single setDifficulty chokepoint");
        assertTrue(mixin.contains("@ModifyVariable"), "modifies the difficulty argument");
        assertTrue(mixin.contains("PeacefulGuard.sanitize"), "routes through the shared guard");
    }

    @Test
    void startupCorrectionIsSubscribedAndRegistered() throws IOException {
        String events = SourceScan.mainJava("dev/molang/iamzombieq/gameplay/DifficultyGuardEvents.java");
        assertTrue(events.contains("@SubscribeEvent"), "is an event subscriber");
        assertTrue(events.contains("ServerStartedEvent"), "fires on server started (player list ready, no players)");
        assertTrue(events.contains("PeacefulGuard.enforce"), "applies the startup correction");

        assertTrue(SourceScan.mainJava("dev/molang/iamzombieq/IAmZombieMod.java").contains("DifficultyGuardEvents.class"),
                "DifficultyGuardEvents must be registered on the game event bus");
    }

    @Test
    void everyPathSharesTheSinglePeacefulPredicate() throws IOException {
        String guard = SourceScan.mainJava("dev/molang/iamzombieq/gameplay/PeacefulGuard.java");
        assertTrue(guard.contains("Difficulty.PEACEFUL"), "the guard defines the forbidden difficulty");
        assertTrue(guard.contains("FALLBACK = Difficulty.EASY"), "Peaceful is replaced with Easy");
        assertTrue(guard.contains("getWorldData().getDifficulty()"), "enforce inspects the stored difficulty");
        assertTrue(guard.contains("setDifficulty(FALLBACK"), "enforce routes the correction back through setDifficulty");

        assertTrue(SourceScan.mainJava("dev/molang/iamzombieq/mixin/DifficultyCommandMixin.java")
                        .contains("PeacefulGuard.isForbidden"),
                "the command rejection reuses the shared predicate");
    }

    @Test
    void selectableDifficultiesAreDerivedFromTheSharedForbiddenPredicate() throws IOException {
        String guard = SourceScan.mainJava("dev/molang/iamzombieq/gameplay/PeacefulGuard.java");
        assertTrue(guard.contains("public static Difficulty[] selectableDifficulties()"),
                "PeacefulGuard should expose the one gameplay-package difficulty selection helper");
        String helper = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(guard, "public static Difficulty[] selectableDifficulties")));
        assertEquals("publicstaticDifficulty[]selectableDifficulties(){"
                        + "returnArrays.stream(Difficulty.values())"
                        + ".filter(difficulty->!isForbidden(difficulty))"
                        + ".toArray(Difficulty[]::new);}", helper,
                "the helper must derive, filter through the shared predicate, and materialize a fresh array only");
        assertFalse(helper.contains("newDifficulty[]{"),
                "the helper must not hard-code EASY/NORMAL/HARD");
    }
}
