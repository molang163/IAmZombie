package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-scan pinning (no Minecraft bootstrap) of the Phase-1 API infection-event wiring in
 * {@link ZombieInfectionEvents}: the handler must fire a cancellable {@code ZombieInfectPreEvent} AFTER the
 * existing gates (RNG chance + {@code EventHooks.canLivingConvert}) but BEFORE the conversion, and a
 * {@code ZombieInfectedEvent} observer AFTER each successful conversion — in BOTH the villager and the pig/piglin
 * path. The Pre fire must short-circuit (return) when canceled so the conversion is aborted.
 */
class ZombieInfectionEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");

    private static String villagerPath(String src) {
        return src.substring(
                src.indexOf("private static void tryInfectVillager"),
                src.indexOf("private static void tryInfectIntoZombifiedPiglin"));
    }

    private static String piglinPath(String src) {
        return src.substring(
                src.indexOf("private static void tryInfectIntoZombifiedPiglin"),
                src.indexOf("private static void awardInfection"));
    }

    @Test
    void infectionHandlerFiresPreAndInfectedEventsThroughThePublisher() throws IOException {
        String src = Files.readString(SOURCE);
        assertTrue(src.contains("import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;"),
                "the infection handler should post through the isolation-wrapped publisher");
        assertTrue(src.contains("ZombieInfectPreEvent") && src.contains("ZombieInfectedEvent"),
                "both the Pre and the Infected event types should be referenced");
    }

    @Test
    void villagerPathFiresCancellablePreAfterGatesThenInfectedAfterConversion() throws IOException {
        String body = villagerPath(Files.readString(SOURCE));
        // PRE is fired AFTER the RNG + canLivingConvert gates (both gate-returns precede the Pre fire).
        int chanceGate = body.indexOf("ZombieInfectionRules.shouldInfect");
        int convertGate = body.indexOf("EventHooks.canLivingConvert");
        int preFire = body.indexOf("postCancelable(");
        assertTrue(chanceGate >= 0 && convertGate >= 0 && preFire >= 0, "the gates and the Pre fire should all exist");
        assertTrue(chanceGate < preFire && convertGate < preFire,
                "the cancellable Pre must be fired AFTER the RNG + canLivingConvert gates");
        assertTrue(body.contains("new ZombieInfectPreEvent(serverPlayer, villager, EntityTypes.ZOMBIE_VILLAGER)"),
                "the villager Pre event should carry the villager + ZOMBIE_VILLAGER result type");
        // The Pre fire must short-circuit the infection when canceled.
        int preFireToReturn = body.indexOf("return;", preFire);
        assertTrue(preFireToReturn >= 0 && preFireToReturn < body.indexOf("convertVillagerToZombieVillager"),
                "a canceled Pre must abort the infection (return before the conversion)");
        // The Infected observer is fired AFTER a successful conversion.
        assertTrue(body.contains("ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, villager, EntityTypes.ZOMBIE_VILLAGER))"),
                "a successful villager conversion should fire the ZombieInfectedEvent observer");
    }

    @Test
    void piglinPathFiresCancellablePreAfterGatesThenInfectedAfterConversion() throws IOException {
        String body = piglinPath(Files.readString(SOURCE));
        int chanceGate = body.indexOf("ZombieInfectionRules.shouldInfect");
        int convertGate = body.indexOf("EventHooks.canLivingConvert");
        int preFire = body.indexOf("postCancelable(");
        assertTrue(chanceGate >= 0 && convertGate >= 0 && preFire >= 0, "the gates and the Pre fire should all exist");
        assertTrue(chanceGate < preFire && convertGate < preFire,
                "the cancellable Pre must be fired AFTER the RNG + canLivingConvert gates");
        assertTrue(body.contains("new ZombieInfectPreEvent(serverPlayer, victim, EntityTypes.ZOMBIFIED_PIGLIN)"),
                "the pig/piglin Pre event should carry the victim + ZOMBIFIED_PIGLIN result type");
        int preFireToReturn = body.indexOf("return;", preFire);
        assertTrue(preFireToReturn >= 0 && preFireToReturn < body.indexOf("convertToZombifiedPiglin"),
                "a canceled Pre must abort the infection (return before the conversion)");
        assertTrue(body.contains("ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, victim, EntityTypes.ZOMBIFIED_PIGLIN))"),
                "a successful pig/piglin conversion should fire the ZombieInfectedEvent observer");
    }

    /**
     * RC4: the infection conversions must NOT seed the killing player as the converted mob's last attacker. Doing so
     * (the old {@code setLastHurtByMob(attacker)}) faked a retaliation that defeated the undead-kin targeting
     * immunity, so a freshly-infected zombie villager / zombified piglin attacked the very zombie player that
     * infected it. The whole file must therefore be free of {@code setLastHurtByMob}; genuine retaliation still
     * works because vanilla re-sets it on an actual later strike.
     */
    @Test
    void infectionConversionsSeedNoAttackerSoKinStaysIgnored() throws IOException {
        String src = Files.readString(SOURCE);
        assertFalse(src.contains("setLastHurtByMob"),
                "the infection conversions must not seed the player as the converted mob's attacker (RC4 kin-aggro fix)");
    }
}
