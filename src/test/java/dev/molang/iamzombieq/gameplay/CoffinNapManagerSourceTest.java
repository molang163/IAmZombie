package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoffinNapManagerSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/CoffinNapManager.java");

    @Test
    void drivenByServerPlayerTickPostEvent() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("@SubscribeEvent"), "the nap manager should subscribe to NeoForge events");
        assertTrue(source.contains("PlayerTickEvent.Post"), "the driver should run on the per-tick PlayerTickEvent.Post");
        assertTrue(source.contains("instanceof ServerPlayer player"), "the driver should only run server-side for players");
    }

    @Test
    void beginsARealMultiTickSleep() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("public static boolean beginNap"), "beginNap should be the public entry point used by the coffin block");
        assertTrue(source.contains("player.startSleeping(headPos)"), "beginNap should enter a real sleep via startSleeping (not startSleepInBed)");
        assertTrue(source.contains("CoffinBlock.setCoffinRespawn"), "lying down should set the coffin respawn point like a vanilla bed");
        assertTrue(source.contains("player.isSleepingLongEnough()"), "the deep-sleep vote should require sleeping long enough");
    }

    @Test
    void votesPerDimensionUsingSleepRulesAndGamerule() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("GameRules.PLAYERS_SLEEPING_PERCENTAGE"), "the vote should use the players-sleeping-percentage gamerule");
        assertTrue(source.contains("ZombieSleepRules.enoughCoffinSleepers"), "the vote should reuse the pure ZombieSleepRules math");
        assertTrue(source.contains("ZombieSleepRules.coffinSleepersNeeded"), "the progress message should reuse the pure needed-count math");
        assertTrue(source.contains("!p.isSpectator()"), "eligible zombies should be the non-spectator players (never form != NORMAL)");
    }

    @Test
    void advancesToNightThroughTheSleepFinishedHook() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("ClockTimeMarkers.NIGHT"), "the coffin should advance the clock to NIGHT (mirror of vanilla WAKE_UP_FROM_SLEEP)");
        assertTrue(source.contains("EventHooks.onSleepFinished"), "time advance should go through onSleepFinished for mod compatibility");
        assertTrue(source.contains("ClockAdjustment.Marker"), "time advance should use a marker-based clock adjustment");
        assertTrue(source.contains("GameRules.ADVANCE_TIME"), "time advance should respect the advance_time gamerule");
        assertTrue(source.contains("resetWeatherCycle"), "advancing to night should reset rain when advance_weather is on, like vanilla beds");
    }

    @Test
    void interruptsWakeWithoutSkippingTime() throws IOException {
        String source = Files.readString(SOURCE);

        // Damage / external wake / broken coffin all stop the sleep with no time skip.
        assertTrue(source.contains("player.getHealth() < nap.lastHealth"), "taking damage should interrupt the nap");
        assertTrue(source.contains("!player.isSleeping() || player.getSleepingPos().isEmpty()"),
                "an external wake or lost sleeping pos should drop the nap");
        assertTrue(source.contains("!headState.isBed("), "a coffin that is no longer a bed should interrupt the nap");
        assertTrue(source.contains("MAX_WAIT_TICKS"), "a hardcoded max-wait timeout should prevent deadlock when others never sleep");
    }

    @Test
    void wakesIfAProactiveAttackerWandersUpMidNap() throws IOException {
        String source = Files.readString(SOURCE);

        // Mid-nap consistency: the driver re-runs the coffin's entry-time hostile predicate so a proactive attacker
        // that wanders up while the zombie is asleep wakes it (not only damage / a broken coffin).
        assertTrue(source.contains("CoffinBlock.hasHostileNearby"),
                "the driver should re-check the coffin's hostile predicate mid-nap");
    }

    @Test
    void cleansUpTheNapMapOnLogoutAndServerStop() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("NAPS"), "the driver should track naps per UUID");
        assertTrue(source.contains("NAPS.remove(player.getUUID())") || source.contains("NAPS.remove(event.getEntity().getUUID())"),
                "naps should be removed when a player wakes or logs out");
        assertTrue(source.contains("PlayerEvent.PlayerLoggedOutEvent"), "logout should clear the player's nap");
        assertTrue(source.contains("ServerStoppedEvent"), "server stop should clear the whole nap map");
        assertTrue(source.contains("NAPS.clear()"), "server stop should clear the whole nap map");
    }
}
