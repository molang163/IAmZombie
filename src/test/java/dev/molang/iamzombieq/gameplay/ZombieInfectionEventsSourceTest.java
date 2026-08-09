package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-scan pinning (no Minecraft bootstrap) of the Phase-1 API infection-event wiring in
 * {@link ZombieInfectionEvents}. Every platform-available infection path delegates to one
 * shared pipeline shell, which must fire a cancellable {@code ZombieInfectPreEvent}
 * AFTER the existing gates (RNG chance + {@code EventHooks.canLivingConvert}) but BEFORE the conversion, and a
 * {@code ZombieInfectedEvent} observer AFTER each successful conversion — with the advancement awarded first,
 * then the observer, then the death-event cancel (the original villager-path order). The Pre fire must
 * short-circuit (return) when canceled so the conversion is aborted.
 */
class ZombieInfectionEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");

    private static String villagerPath(String src) {
        return SourceScan.methodBody(src, "private static void tryInfectVillager");
    }

    private static String piglinPath(String src) {
        return SourceScan.methodBody(src, "private static void tryInfectIntoZombifiedPiglin");
    }

    private static String horsePath(String src) {
        return SourceScan.methodBody(src, "private static void tryInfectHorse");
    }

    private static String nautilusPath(String src) {
        return SourceScan.methodBody(src, "private static void tryInfectNautilus");
    }

    private static String pipeline(String src) {
        return SourceScan.methodBody(src, "private static void runInfectionPipeline");
    }

    @Test
    void infectionHandlerFiresPreAndInfectedEventsThroughThePublisher() throws IOException {
        String src = Files.readString(SOURCE);
        assertTrue(src.contains("import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;"),
                "the infection handler should post through the isolation-wrapped publisher");
        assertTrue(src.contains("ZombieInfectPreEvent") && src.contains("ZombieInfectedEvent"),
                "both the Pre and the Infected event types should be referenced");
    }

    /**
     * The available paths must not re-implement the shell — each contributes exactly its victim, result type,
     * unchanged conversion method, and (nullable) advancement to the ONE shared pipeline, so the Pre/Infected
     * API events and the gate ordering hold for every path, including horse and nautilus conversions.
     */
    @Test
    void allFourInfectionPathsDelegateToTheSharedPipeline() throws IOException {
        String src = SourceScan.stripComments(Files.readString(SOURCE));
        String entityType = StonecutterCapabilityMatrix.activeEntityTypeHolder();

        String villager = villagerPath(src);
        assertTrue(villager.contains("runInfectionPipeline(event, level, villager, player, "
                        + entityType + ".ZOMBIE_VILLAGER,"),
                "the villager path should delegate to the shared pipeline with the ZOMBIE_VILLAGER result type");
        assertTrue(villager.contains("convertVillagerToZombieVillager(level, villager, player)"),
                "the villager path should pass its existing conversion method unchanged");
        assertTrue(villager.contains("IAmZombieAdvancements.INFECTION"),
                "the villager path should award the INFECTION advancement");

        String piglin = piglinPath(src);
        assertTrue(piglin.contains("runInfectionPipeline(event, level, victim, player, "
                        + entityType + ".ZOMBIFIED_PIGLIN,"),
                "the pig/piglin path should delegate to the shared pipeline with the ZOMBIFIED_PIGLIN result type");
        assertTrue(piglin.contains("convertToZombifiedPiglin(level, victim, player)"),
                "the pig/piglin path should pass its existing conversion method unchanged");
        assertTrue(piglin.contains("IAmZombieAdvancements.INFECTION"),
                "the pig/piglin path should award the INFECTION advancement");

        String horse = horsePath(src);
        assertTrue(horse.contains("runInfectionPipeline(event, level, horse, player, "
                        + entityType + ".ZOMBIE_HORSE,"),
                "the horse path should delegate to the shared pipeline with the ZOMBIE_HORSE result type");
        assertTrue(horse.contains("convertHorseToZombieHorse(level, horse, player, pendingHorseHealthRatio)"),
                "the horse path should pass its existing conversion method (with the pending health ratio) unchanged");
        assertTrue(horse.contains("IAmZombieAdvancements.HORSE_INFECTION"),
                "the horse path should award the HORSE_INFECTION advancement");

        if (StonecutterCapabilityMatrix.hasNautilusEntityApi()) {
            String nautilus = nautilusPath(src);
            assertTrue(nautilus.contains(
                            "runInfectionPipeline(event, level, nautilus, player, "
                                    + entityType + ".ZOMBIE_NAUTILUS,"),
                    "the nautilus path should delegate to the shared pipeline with the ZOMBIE_NAUTILUS result type");
            assertTrue(nautilus.contains("convertNautilusToZombieNautilus(level, nautilus, player)"),
                    "the nautilus path should pass its existing conversion method unchanged");
            assertTrue(nautilus.contains("null);"),
                    "the nautilus path has no advancement, so it should pass null to the pipeline");
            assertFalse(nautilus.contains("IAmZombieAdvancements"),
                    "the nautilus path must not award any advancement (none exists for it)");
        } else {
            assertFalse(src.contains("tryInfectNautilus"));
            assertFalse(src.contains("convertNautilusToZombieNautilus"));
            assertFalse(src.contains("ZombieNautilus"));
        }
    }

    @Test
    void pipelineFiresCancellablePreAfterGatesThenInfectedAfterConversion() throws IOException {
        String body = pipeline(Files.readString(SOURCE));
        // PRE is fired AFTER the RNG + canLivingConvert gates (both gate-returns precede the Pre fire).
        int chanceGate = body.indexOf("ZombieInfectionRules.shouldInfect");
        int convertGate = body.indexOf("EventHooks.canLivingConvert");
        int preFire = body.indexOf("postCancelable(");
        assertTrue(chanceGate >= 0 && convertGate >= 0 && preFire >= 0, "the gates and the Pre fire should all exist");
        assertTrue(chanceGate < preFire && convertGate < preFire,
                "the cancellable Pre must be fired AFTER the RNG + canLivingConvert gates");
        assertTrue(body.contains("new ZombieInfectPreEvent(serverPlayer, victim, resultType)"),
                "the Pre event should carry the victim + result type of the current path");
        // The Pre fire must short-circuit the infection when canceled.
        int preFireToReturn = body.indexOf("return;", preFire);
        assertTrue(preFireToReturn >= 0 && preFireToReturn < body.indexOf("conversion.getAsBoolean()"),
                "a canceled Pre must abort the infection (return before the conversion)");
        // The Infected observer is fired AFTER a successful conversion.
        assertTrue(body.contains("ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, victim, resultType))"),
                "a successful conversion should fire the ZombieInfectedEvent observer");
    }

    /**
     * Pipeline ordering must preserve the original villager-path sequence —
     * conversion, then the advancement (nullable), then the Infected observer, then the death-event cancel.
     * The award must NOT be moved after the observer; it is awarded before posting.
     */
    @Test
    void pipelineAwardsAdvancementThenFiresInfectedThenCancels() throws IOException {
        String body = pipeline(Files.readString(SOURCE));
        int conversion = body.indexOf("conversion.getAsBoolean()");
        int award = body.indexOf("IAmZombieAdvancements.award");
        int post = body.indexOf("ZombieEventPublisher.post(");
        int cancel = body.indexOf("event.setCanceled(true)");
        assertTrue(conversion >= 0 && award >= 0 && post >= 0 && cancel >= 0,
                "the conversion call, the award, the Infected observer, and the cancel should all exist");
        assertTrue(conversion < award && award < post && post < cancel,
                "the shell order must be conversion -> advancement -> Infected observer -> cancel"
                        + " (the original villager-path order)");
    }

    /**
     * Infection conversions must NOT seed the killing player as the converted mob's last attacker. Doing so
     * (the old {@code setLastHurtByMob(attacker)}) faked a retaliation that defeated the undead-kin targeting
     * immunity, so a freshly-infected zombie villager / zombified piglin attacked the very zombie player that
     * infected it. The whole file must therefore be free of {@code setLastHurtByMob}; genuine retaliation still
     * works because vanilla re-sets it on an actual later strike.
     */
    @Test
    void infectionConversionsSeedNoAttackerSoKinStaysIgnored() throws IOException {
        String src = Files.readString(SOURCE);
        assertFalse(src.contains("setLastHurtByMob"),
                "infection conversions must not seed the player as the converted mob's attacker");
    }
}
