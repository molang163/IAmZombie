package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the five {@code state.*} debug-log insertion points wired through {@code ZombieLog.debug}, each on
 * its success path only, in the required call order relative to the surrounding state mutation.
 */
class DebugLoggingSourceTest {
    private static final Path HEROBRINE_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/HerobrineEvents.java");
    private static final Path INFECTION_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");
    private static final Path PLAYER_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");
    private static final Path COFFIN_NAP_MANAGER = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/CoffinNapManager.java");
    private static final Path ZOMBIE_LOG = Path.of("src/main/java/dev/molang/iamzombieq/internal/logging/ZombieLog.java");
    private static final Path CONFIG = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path SERVER_CONFIG =
            Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieServerConfig.java");
    private static final Path KEY_CATALOG =
            Path.of("src/main/java/dev/molang/iamzombieq/config/ConfigKeyCatalog.java");

    private static String stripped(Path path) throws IOException {
        return SourceScan.stripComments(Files.readString(path));
    }

    @Test
    void herobrineSnapshotLogsBetweenDurableSetDataAndInventoryClear() throws IOException {
        String body = SourceScan.methodBody(stripped(HEROBRINE_EVENTS), "private static void triggerEncounterDeath");

        int put = body.indexOf("PENDING_RESPAWNS.put(player.getUUID(), pending);");
        int durableSetData = body.indexOf("player.setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, toSnapshot(pending));");
        int log = body.indexOf("ZombieLog.debug(");
        int clear = body.indexOf("player.getInventory().clearContent();");
        assertTrue(put >= 0 && durableSetData >= 0 && log >= 0 && clear >= 0,
                "the map write, the durable setData, the log call, and the inventory clear should all exist");
        assertTrue(put < durableSetData && durableSetData < log && log < clear,
                "order must be: PENDING_RESPAWNS.put -> durable setData -> ZombieLog.debug -> inventory clear");
        assertEquals(1, SourceScan.countOccurrences(body, "ZombieLog.debug("), "exactly one log call in triggerEncounterDeath");
        assertTrue(body.contains("state.herobrine_snapshot"), "the snapshot log should use the state.herobrine_snapshot tag");
    }

    @Test
    void herobrineRestoreLogsAfterInventoryAndExperienceRestore() throws IOException {
        String body = SourceScan.methodBody(stripped(HEROBRINE_EVENTS), "public static void onPlayerClone");

        int restoreInventory = body.indexOf("restoreInventory(newPlayer, pending);");
        int restoreExperience = body.indexOf("restoreExperience(newPlayer, pending);");
        int log = body.indexOf("ZombieLog.debug(");
        assertTrue(restoreInventory >= 0 && restoreExperience >= 0 && log >= 0,
                "restoreInventory, restoreExperience, and the log call should all exist");
        assertTrue(restoreInventory < restoreExperience && restoreExperience < log,
                "order must be: restoreInventory -> restoreExperience -> ZombieLog.debug");
        assertEquals(1, SourceScan.countOccurrences(body, "ZombieLog.debug("), "exactly one log call in onPlayerClone");
        assertTrue(body.contains("state.herobrine_restore"), "the restore log should use the state.herobrine_restore tag");
    }

    @Test
    void infectionLogsInsideTheSuccessfulConversionBranchAfterCancel() throws IOException {
        String body = SourceScan.methodBody(stripped(INFECTION_EVENTS), "private static void runInfectionPipeline");
        assertEquals(1, SourceScan.countOccurrences(body, "ZombieLog.debug("), "exactly one log call in runInfectionPipeline");

        String successBlock = SourceScan.blockBody(body, "if (conversion.getAsBoolean())");
        assertTrue(successBlock.contains("ZombieLog.debug("),
                "the one log call must live inside the conversion-success branch, not the RNG/gate/Pre-event checks above it");

        int cancel = successBlock.indexOf("event.setCanceled(true);");
        int log = successBlock.indexOf("ZombieLog.debug(");
        assertTrue(cancel >= 0 && log >= 0, "event.setCanceled(true) and the log call should both exist inside the success branch");
        assertTrue(cancel < log, "the log call must come after event.setCanceled(true)");
        assertTrue(body.contains("state.infection"), "the infection log should use the state.infection tag");
    }

    @Test
    void evolutionLogsOnlyOnInPlaceRespawnPathAfterAllRestoresComplete() throws IOException {
        String body = SourceScan.methodBody(stripped(PLAYER_EVENTS), "public static void onLivingDeath");
        assertEquals(1, SourceScan.countOccurrences(body, "ZombieLog.debug("), "exactly one log call in onLivingDeath");

        int gate = body.indexOf("if (!result.inPlaceRespawn())");
        assertTrue(gate >= 0, "the inPlaceRespawn gate should exist");
        assertFalse(body.substring(0, gate).contains("ZombieLog.debug("),
                "the log call must not live in the GIANT-kill transform path above the inPlaceRespawn gate");
        String successPath = body.substring(gate);

        int setData = successPath.indexOf("player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);");
        int evolvedEvent = successPath.indexOf("ZombieEventPublisher.post(new ZombieEvolvedEvent(");
        int attributeRefresh = successPath.indexOf("ZombieFormAttributes.refreshFormAttributesForced(player, nextData);");
        int passiveAbilities = successPath.indexOf("applyPassiveFormAbilities(player, nextData);");
        int advancement = successPath.indexOf("awardEvolutionAdvancement(player, result);");
        int saturation = successPath.indexOf("player.getFoodData().setSaturation(0.0F);");
        int log = successPath.indexOf("ZombieLog.debug(");
        assertTrue(setData >= 0 && evolvedEvent >= 0 && attributeRefresh >= 0 && passiveAbilities >= 0
                        && advancement >= 0 && saturation >= 0 && log >= 0,
                "setData, the evolved event, attribute refresh, passive abilities, advancement, the final"
                        + " saturation reset, and the log call should all exist");
        assertTrue(setData < evolvedEvent && evolvedEvent < attributeRefresh && attributeRefresh < passiveAbilities
                        && passiveAbilities < advancement && advancement < saturation && saturation < log,
                "the log call must come after every restore step, ending with the food/saturation reset");
        assertTrue(body.contains("state.evolution"), "the evolution log should use the state.evolution tag");
    }

    @Test
    void coffinVoteLogsOnlyOnceEnoughSleepersAfterAdvanceAndWakeAll() throws IOException {
        String body = SourceScan.methodBody(stripped(COFFIN_NAP_MANAGER), "public static void onPlayerTick");
        assertEquals(1, SourceScan.countOccurrences(body, "ZombieLog.debug("), "exactly one log call in onPlayerTick");

        int notEnoughBlockStart = body.indexOf("if (!ZombieSleepRules.enoughCoffinSleepers(deep, eligible, percentage))");
        assertTrue(notEnoughBlockStart >= 0, "the not-enough-sleepers gate should exist");
        String notEnoughBlock = SourceScan.blockBody(body,
                "if (!ZombieSleepRules.enoughCoffinSleepers(deep, eligible, percentage))");
        assertFalse(notEnoughBlock.contains("ZombieLog.debug("),
                "the coffin vote log must not live in the not-enough-sleepers branch or the throttled per-tick path");

        String votePassedPath = body.substring(notEnoughBlockStart + notEnoughBlock.length());
        int advance = votePassedPath.indexOf("boolean skipped = advanceToNight(level);");
        int wakeAll = votePassedPath.indexOf("wakeAllInLevel(level, skipped);");
        int log = votePassedPath.indexOf("ZombieLog.debug(");
        assertTrue(advance >= 0 && wakeAll >= 0 && log >= 0,
                "advanceToNight, wakeAllInLevel, and the log call should all exist in the vote-passed path");
        assertTrue(advance < wakeAll && wakeAll < log,
                "order must be: advanceToNight -> wakeAllInLevel -> ZombieLog.debug");
        assertTrue(body.contains("state.coffin_vote"), "the vote-passed log should use the state.coffin_vote tag");
    }

    @Test
    void onlyZombieLogEverCallsLoggerDebugDirectly() throws IOException {
        // ZombieLog is the sole legitimate LOGGER.debug call site; every other production .java file (gameplay
        // hosts included, but not limited to them) must route through ZombieLog.debug instead. IAmZombieMod.java
        // is NOT excluded -- it only declares LOGGER, it has no debug( call, so it passes this scan unaided.
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.equals(ZOMBIE_LOG)) {
                    continue;
                }
                assertFalse(stripped(path).contains("LOGGER.debug("),
                        path + " must route debug logging through ZombieLog.debug, not call LOGGER.debug directly");
            }
        }
    }

    @Test
    void debugLoggingConfigIsReadOnlyFromZombieLog() throws IOException {
        assertTrue(stripped(ZOMBIE_LOG).contains("IAmZombieServerConfig.DEBUG_LOGGING.get()"),
                "ZombieLog must read the debugLogging config value");
        assertEquals(2, SourceScan.countOccurrences(stripped(CONFIG), "DEBUG_LOGGING"),
                "the compatibility facade must name DEBUG_LOGGING only as alias and canonical target");
        assertEquals(1, SourceScan.countOccurrences(stripped(SERVER_CONFIG), "DEBUG_LOGGING"),
                "the canonical SERVER holder may only declare DEBUG_LOGGING");
        assertEquals(1, SourceScan.countOccurrences(stripped(KEY_CATALOG), "DEBUG_LOGGING"),
                "the migration catalog may only name the DEBUG_LOGGING legacy field once");

        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.equals(ZOMBIE_LOG) || path.equals(CONFIG) || path.equals(SERVER_CONFIG)
                        || path.equals(KEY_CATALOG)) {
                    continue; // ZombieLog reads it; holder declarations/catalog metadata are asserted above.
                }
                assertFalse(stripped(path).contains("DEBUG_LOGGING"),
                        path + " must not read DEBUG_LOGGING directly; go through ZombieLog.debug");
            }
        }
    }
}
