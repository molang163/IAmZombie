package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoffinNapManagerSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/CoffinNapManager.java");

    private static String stripped(Path path) throws IOException {
        return SourceScan.stripComments(Files.readString(path));
    }

    @Test
    void drivenByServerPlayerTickPostEvent() throws IOException {
        String source = stripped(SOURCE);

        assertTrue(source.contains("@SubscribeEvent"), "the nap manager should subscribe to NeoForge events");
        assertTrue(source.contains("PlayerTickEvent.Post"), "the driver should run on the per-tick PlayerTickEvent.Post");
        assertTrue(source.contains("instanceof ServerPlayer player"), "the driver should only run server-side for players");
    }

    @Test
    void beginsARealMultiTickSleep() throws IOException {
        String source = stripped(SOURCE);

        assertTrue(source.contains("public static boolean beginNap"), "beginNap should be the public entry point used by the coffin block");
        assertTrue(source.contains("player.startSleeping(headPos)"), "beginNap should enter a real sleep via startSleeping (not startSleepInBed)");
        assertTrue(source.contains("CoffinBlock.setCoffinRespawn"), "lying down should set the coffin respawn point like a vanilla bed");
        assertTrue(source.contains("player.isSleepingLongEnough()"), "the deep-sleep vote should require sleeping long enough");
    }

    @Test
    void votesPerDimensionUsingSleepRulesAndGamerule() throws IOException {
        String source = stripped(SOURCE);
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                        .contains(executingNode),
                "Gradle must inject one of the five frozen Stonecutter nodes");
        boolean genericGameRules = !executingNode.equals("1.21.10")
                && !executingNode.equals("1.21.8");
        String rules = "Game" + "Rules";
        String levelRules = "level.get" + rules + "()";
        String modernVote = levelRules + ".get(" + rules + ".PLAYERS_"
                + "SLEEPING_PERCENTAGE)";
        String legacyVote = levelRules + ".getInt(" + rules + ".RULE_PLAYERS_"
                + "SLEEPING_PERCENTAGE)";
        String eligible = SourceScan.compact(
                SourceScan.methodBody(source, "private static int countEligibleZombies"));

        assertEquals(1, SourceScan.countOccurrences(source, genericGameRules ? modernVote : legacyVote),
                "the vote should use the node-native players-sleeping-percentage getter and key");
        assertEquals(0, SourceScan.countOccurrences(source, genericGameRules ? legacyVote : modernVote),
                "the vote must not mix players-sleeping-percentage API generations");
        assertTrue(source.contains("ZombieSleepRules.enoughCoffinSleepers"), "the vote should reuse the pure ZombieSleepRules math");
        assertTrue(source.contains("ZombieSleepRules.coffinSleepersNeeded"), "the progress message should reuse the pure needed-count math");
        assertTrue(eligible.contains("if(ZombiePlayerGates.isZombiePlayer(p)){n++;}"),
                "eligible zombies should use the shared positive admission gate");
        assertFalse(eligible.contains("if(!ZombiePlayerGates.isZombiePlayer(p))"),
                "the eligible-player count must not invert the shared admission gate");
        assertFalse(eligible.contains(".isSpectator("),
                "the eligible-player count should not duplicate the canonical spectator check");
        assertFalse(eligible.contains("isCreative") || eligible.contains("ZombieForm"),
                "creative players remain eligible and zombie form must not affect the vote");
    }

    @Test
    void advancesToNightThroughTheSleepFinishedHook() throws IOException {
        String source = stripped(SOURCE);
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                        .contains(executingNode),
                "Gradle must inject one of the five frozen Stonecutter nodes");
        boolean worldClockApi = executingNode.equals("26.2.x") || executingNode.equals("26.1.x");
        boolean genericGameRules = !executingNode.equals("1.21.10")
                && !executingNode.equals("1.21.8");

        assertTrue(source.contains("EventHooks.onSleepFinished"), "time advance should go through onSleepFinished for mod compatibility");
        assertTrue(source.contains("resetWeatherCycle"), "advancing to night should reset rain when advance_weather is on, like vanilla beds");
        String rules = "Game" + "Rules";
        String modernAdvanceTime = rules + ".ADVANCE_" + "TIME";
        String legacyAdvanceTime = rules + ".RULE_" + "DAYLIGHT";
        assertTrue(source.contains(genericGameRules ? modernAdvanceTime : legacyAdvanceTime),
                "time advance should respect the node-native daylight-cycle gamerule");
        if (worldClockApi) {
            assertTrue(source.contains("ClockTimeMarkers.NIGHT"),
                    "26.x should advance to the native NIGHT clock marker");
            assertTrue(source.contains("ClockAdjustment.Marker"),
                    "26.x should use a marker-based clock adjustment");
            assertFalse(source.contains("nextLegacyNight"),
                    "26.x must not use the flat legacy day-time bridge");
        } else {
            assertTrue(source.contains("nextLegacyNight"),
                    "1.21.x should calculate the next flat day-time NIGHT tick");
            assertTrue(source.contains("EventHooks.onSleepFinished(level, target, current)"),
                    "1.21.x should use the node-native long sleep-finished hook");
            assertTrue(source.contains("level.setDayTime(adjusted)"),
                    "1.21.x should apply the hook-adjusted day time");
            assertFalse(source.contains("ClockAdjustment") || source.contains("ClockTimeMarkers"),
                    "1.21.x must not reference the absent world-clock API");
        }
    }

    @Test
    void interruptsWakeWithoutSkippingTime() throws IOException {
        String source = stripped(SOURCE);

        // Damage / external wake / broken coffin all stop the sleep with no time skip.
        assertTrue(source.contains("player.getHealth() < nap.lastHealth"), "taking damage should interrupt the nap");
        assertTrue(source.contains("!player.isSleeping() || player.getSleepingPos().isEmpty()"),
                "an external wake or lost sleeping pos should drop the nap");
        assertTrue(source.contains("!headState.isBed("), "a coffin that is no longer a bed should interrupt the nap");
        assertTrue(source.contains("MAX_WAIT_TICKS"), "a hardcoded max-wait timeout should prevent deadlock when others never sleep");
    }

    @Test
    void wakesIfAProactiveAttackerWandersUpMidNap() throws IOException {
        String source = stripped(SOURCE);

        // Mid-nap consistency: the driver re-runs the coffin's entry-time hostile predicate so a proactive attacker
        // that wanders up while the zombie is asleep wakes it (not only damage / a broken coffin).
        assertTrue(source.contains("CoffinBlock.hasHostileNearby"),
                "the driver should re-check the coffin's hostile predicate mid-nap");
    }

    @Test
    void votingProgressMessageIsThrottledButVoteAndTimeoutChecksStayPerTick() throws IOException {
        String tickBody = SourceScan.methodBody(stripped(SOURCE), "public static void onPlayerTick");
        assertTrue(tickBody.contains("if (!ZombieSleepRules.enoughCoffinSleepers(deep, eligible, percentage))"),
                "enoughCoffinSleepers must stay the (unthrottled) gate for the whole not-enough branch");

        // Layer 1: the not-enough-sleepers branch itself, brace-balanced (not a hand-picked text window).
        String notEnoughBlock = SourceScan.blockBody(tickBody,
                "if (!ZombieSleepRules.enoughCoffinSleepers(deep, eligible, percentage))");
        assertTrue(notEnoughBlock.contains("level.getGameTime() - nap.startTick > DEEP_SLEEP_TICKS + MAX_WAIT_TICKS"),
                "the anti-deadlock timeout check must live inside the not-enough-sleepers branch");

        // Layer 2: the throttle gate nested inside it, also brace-balanced.
        int throttleGateStart = notEnoughBlock.indexOf("if (ZombieSleepRules.shouldSendCoffinVoteProgress(");
        assertTrue(throttleGateStart >= 0, "the throttle gate must exist inside the not-enough-sleepers branch");
        String throttleBlock = SourceScan.blockBody(notEnoughBlock,
                "if (ZombieSleepRules.shouldSendCoffinVoteProgress(level.getGameTime(), nap.startTick))");
        int throttleGateEnd = throttleGateStart + throttleBlock.length();

        assertFalse(throttleBlock.contains("DEEP_SLEEP_TICKS + MAX_WAIT_TICKS"),
                "the anti-deadlock timeout check must run every tick, OUTSIDE the throttle gate");
        assertTrue(throttleBlock.contains("int needed = ZombieSleepRules.coffinSleepersNeeded(eligible, percentage);"),
                "the needed-count calculation must live INSIDE the throttle gate");
        assertTrue(SourceScan.compact(throttleBlock).contains(SourceScan.compact(
                        "player.sendSystemMessage(Component.translatable("
                                + "ZombieSleepRules.coffinVoteProgressMessageKey(), deep, needed), true);")),
                "the progress overlay send must live INSIDE the throttle gate, routed through"
                        + " coffinVoteProgressMessageKey(), with (deep, needed) in that order");

        // The progress key call must be sent from exactly this one call site in the whole method.
        assertEquals(1, SourceScan.countOccurrences(tickBody, "ZombieSleepRules.coffinVoteProgressMessageKey()"),
                "the progress key call must appear exactly once in onPlayerTick");

        // The not-enough branch must still end with an unconditional return that sits AFTER the throttle gate closes
        // (i.e. outside it), and the throttle gate itself must not have snuck in its own early return.
        assertEquals(0, SourceScan.countOccurrences(throttleBlock, "return;"),
                "the throttle gate must not contain a return of its own");
        int trailingReturn = notEnoughBlock.indexOf("return;", throttleGateEnd);
        assertTrue(trailingReturn >= 0,
                "the not-enough branch must still end with an unconditional return, textually after the throttle gate closes");
    }

    @Test
    void votingProgressMessageKeepsDeepAndNeededArgumentOrder() throws IOException {
        String body = SourceScan.methodBody(stripped(SOURCE), "public static void onPlayerTick");

        assertTrue(SourceScan.compact(body).contains(SourceScan.compact(
                        "Component.translatable("
                                + "ZombieSleepRules.coffinVoteProgressMessageKey(), deep, needed)")),
                "the progress message must keep sending (deep, needed) in that exact order");
    }

    @Test
    void noInlineCoffinMessageLiteralsRemainInTheManager() throws IOException {
        String source = stripped(SOURCE);

        assertFalse(source.contains("iamzombieq.message.coffin."),
                "all coffin lifecycle message keys must be selected via ZombieSleepRules, not inlined here");
    }

    @Test
    void allThreeDisturbedWakesAndTheTimeoutWakeUseNapWakeReason() throws IOException {
        String tickBody = SourceScan.methodBody(stripped(SOURCE), "public static void onPlayerTick");

        // Each of the four wake-triggering ifs is sliced individually (brace-balanced) and checked on its own, so a
        // reason mixed up between two branches (e.g. the timeout path accidentally sending DISTURBED) cannot hide
        // behind an aggregate count that only proves "3 DISTURBED + 1 NOT_ENOUGH_TIMEOUT exist somewhere".
        String brokenCoffinBlock = SourceScan.blockBody(tickBody, "if (!headState.isBed(level, nap.headPos, player))");
        assertTrue(brokenCoffinBlock.contains("wake(player, NapWakeReason.DISTURBED);"),
                "a broken/no-longer-a-bed coffin must wake with NapWakeReason.DISTURBED");

        String damageBlock = SourceScan.blockBody(tickBody, "if (player.getHealth() < nap.lastHealth)");
        assertTrue(damageBlock.contains("wake(player, NapWakeReason.DISTURBED);"),
                "taking damage must wake with NapWakeReason.DISTURBED");

        String hostileBlock = SourceScan.blockBody(tickBody,
                "if ((level.getGameTime() - nap.startTick) % 20L == 0L && CoffinBlock.hasHostileNearby(level, player, nap.headPos))");
        assertTrue(hostileBlock.contains("wake(player, NapWakeReason.DISTURBED);"),
                "a proactive attacker wandering up mid-nap must wake with NapWakeReason.DISTURBED");

        String timeoutBlock = SourceScan.blockBody(tickBody,
                "if (player.isSleepingLongEnough() && level.getGameTime() - nap.startTick > DEEP_SLEEP_TICKS + MAX_WAIT_TICKS)");
        assertTrue(timeoutBlock.contains("wake(player, NapWakeReason.NOT_ENOUGH_TIMEOUT);"),
                "the anti-deadlock timeout must wake with NapWakeReason.NOT_ENOUGH_TIMEOUT");

        // Whole-method counts confirm no fifth/leaked wake(..., DISTURBED/NOT_ENOUGH_TIMEOUT) call exists elsewhere.
        assertEquals(3, SourceScan.countOccurrences(tickBody, "wake(player, NapWakeReason.DISTURBED);"),
                "exactly the three disturbed paths should use NapWakeReason.DISTURBED, no more no less");
        assertEquals(1, SourceScan.countOccurrences(tickBody, "wake(player, NapWakeReason.NOT_ENOUGH_TIMEOUT);"),
                "exactly the timeout path should use NapWakeReason.NOT_ENOUGH_TIMEOUT, no more no less");
    }

    @Test
    void wakeMethodRoutesThroughNapWakeMessageKeyAndSendsOverlayOnce() throws IOException {
        String wakeBody = SourceScan.methodBody(stripped(SOURCE),
                "private static void wake(ServerPlayer player, NapWakeReason reason)");

        assertTrue(wakeBody.contains("ZombieSleepRules.napWakeMessageKey(reason)"),
                "wake must select its message key through ZombieSleepRules.napWakeMessageKey");
        assertEquals(1, SourceScan.countOccurrences(
                        SourceScan.compact(wakeBody),
                        SourceScan.compact(
                                "sendSystemMessage(Component.translatable("
                                        + "ZombieSleepRules.napWakeMessageKey(reason)), true);")),
                "wake must still send exactly one overlay message");

        int stopSleeping = wakeBody.indexOf("player.stopSleeping();");
        int napsRemove = wakeBody.indexOf("NAPS.remove(player.getUUID());");
        int sendOverlay = wakeBody.indexOf("sendSystemMessage(");
        assertTrue(stopSleeping >= 0 && napsRemove >= 0 && sendOverlay >= 0,
                "stopSleeping, NAPS.remove, and the overlay=true system packet should all exist in wake");
        assertTrue(stopSleeping < napsRemove && napsRemove < sendOverlay,
                "order must stay: stopSleeping -> NAPS.remove -> overlay message");
    }

    @Test
    void wakeAllInLevelMapsSkippedToVotePassedReasonsAndKeepsSoundAndSendCounts() throws IOException {
        String body = SourceScan.methodBody(stripped(SOURCE),
                "private static void wakeAllInLevel(ServerLevel level, boolean skipped)");

        assertTrue(body.contains("skipped ? NapWakeReason.VOTE_PASSED : NapWakeReason.VOTE_PASSED_NO_SKIP"),
                "skipped must map to VOTE_PASSED and !skipped to VOTE_PASSED_NO_SKIP");
        assertTrue(body.contains("ZombieSleepRules.napWakeMessageKey(reason)"),
                "wakeAllInLevel must select its message key through ZombieSleepRules.napWakeMessageKey");

        // Slice the per-player loop itself (brace-balanced) so the overlay send and the wake sound are confirmed
        // to live INSIDE the loop, not merely somewhere in the method.
        String loopBody = SourceScan.blockBody(body, "for (UUID id : new ArrayList<>(NAPS.keySet()))");
        assertEquals(1, SourceScan.countOccurrences(
                        SourceScan.compact(loopBody),
                        SourceScan.compact(
                                "p.sendSystemMessage(Component.translatable("
                                        + "ZombieSleepRules.napWakeMessageKey(reason)), true);")),
                "wakeAllInLevel must send exactly one overlay message per player, from one call site inside the loop");
        assertTrue(loopBody.contains(
                        "level.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.6F);"),
                "the exact wool-place wake sound call must remain unchanged, inside the loop");
    }

    @Test
    void voteCompletionKeepsAdvanceThenWakeAllThenC2LogOrder() throws IOException {
        String tickBody = SourceScan.methodBody(stripped(SOURCE), "public static void onPlayerTick");

        int advance = tickBody.indexOf("boolean skipped = advanceToNight(level);");
        int wakeAll = tickBody.indexOf("wakeAllInLevel(level, skipped);");
        int log = tickBody.indexOf("ZombieLog.debug(");
        assertTrue(advance >= 0 && wakeAll >= 0 && log >= 0,
                "advanceToNight, wakeAllInLevel, and the debug log call should all exist");
        assertTrue(advance < wakeAll && wakeAll < log,
                "order must stay: advanceToNight -> wakeAllInLevel -> ZombieLog.debug");
    }

    @Test
    void cleansUpTheNapMapOnLogoutAndServerStop() throws IOException {
        String source = stripped(SOURCE);

        assertTrue(source.contains("NAPS"), "the driver should track naps per UUID");
        assertTrue(source.contains("NAPS.remove(player.getUUID())") || source.contains("NAPS.remove(event.getEntity().getUUID())"),
                "naps should be removed when a player wakes or logs out");
        assertTrue(source.contains("PlayerEvent.PlayerLoggedOutEvent"), "logout should clear the player's nap");
        assertTrue(source.contains("ServerStoppedEvent"), "server stop should clear the whole nap map");
        assertTrue(source.contains("NAPS.clear()"), "server stop should clear the whole nap map");
    }
}
