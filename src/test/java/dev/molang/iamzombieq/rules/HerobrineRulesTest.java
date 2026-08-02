package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter.Phase;
import org.junit.jupiter.api.Test;

class HerobrineRulesTest {
    @Test
    void caveSpawnSearchParametersRetainTheirGameplayValues() {
        assertEquals(8, HerobrineRules.CAVE_SPAWN_SEA_LEVEL_OFFSET);
        assertEquals(16, HerobrineRules.CAVE_SPAWN_ATTEMPTS);
        assertEquals(12, HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE);
        assertEquals(3, HerobrineRules.CAVE_SPAWN_VERTICAL_OFFSET_RADIUS);
        assertEquals(4, HerobrineRules.CAVE_SPAWN_VERTICAL_SEARCH_RADIUS);
    }

    @Test
    void herobrineOnlyUsesVeryRareCaveSpawnRolls() {
        assertTrue(HerobrineRules.shouldAttemptCaveSpawn(0.0, true, true));
        assertFalse(HerobrineRules.shouldAttemptCaveSpawn(0.0, false, true));
        assertFalse(HerobrineRules.shouldAttemptCaveSpawn(0.0, true, false));
        assertFalse(HerobrineRules.shouldAttemptCaveSpawn(0.001, true, true));
    }

    @Test
    void lookingNearlyStraightAtHerobrineTriggersTheEncounter() {
        // Loosened to a ~±10° cone (dot >= 0.985) so looking near Herobrine reliably triggers.
        assertTrue(HerobrineRules.isGazingAtHerobrine(0.985, true, 24.0));
        assertFalse(HerobrineRules.isGazingAtHerobrine(0.984, true, 24.0));
        assertFalse(HerobrineRules.isGazingAtHerobrine(0.985, false, 24.0));
        assertFalse(HerobrineRules.isGazingAtHerobrine(0.985, true, 24.1));
    }

    @Test
    void herobrineIsIntangibleAndOnlyCreativeCanObtainTheHead() {
        assertFalse(HerobrineRules.hasCollisionBox());
        assertFalse(HerobrineRules.canInteract());
        assertFalse(HerobrineRules.canRideMinecarts());
        assertFalse(HerobrineRules.canSurvivalObtainHead());
        assertTrue(HerobrineRules.canCreativeObtainHead());
    }

    @Test
    void encounterWalksThroughTheDreadArcWithDefaultThresholds() {
        // Defaults: escalation at 2 sightings, lethal at 2 + 1 = 3.
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseFor(0, false));
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseFor(1, false));
        assertEquals(Phase.ESCALATION, HerobrineEncounter.phaseFor(2, false));
        assertEquals(Phase.LETHAL, HerobrineEncounter.phaseFor(3, false));
        assertEquals(Phase.LETHAL, HerobrineEncounter.phaseFor(10, false));
        // A veteran who already died once is immediately lethal again.
        assertEquals(Phase.LETHAL, HerobrineEncounter.phaseFor(0, true));
    }

    @Test
    void guaranteesAtLeastOneNonLethalSightingBeforeLethal() {
        // With escalation > 0 there is always at least one OBSERVATION sighting before lethal.
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseFor(0, false, 2, 1));
        assertFalse(HerobrineEncounter.isLethal(HerobrineEncounter.phaseFor(0, false, 2, 1)));
        // Zeroing escalation collapses to legacy instant-kill on the first encounter.
        assertEquals(Phase.LETHAL, HerobrineEncounter.phaseFor(0, false, 0, 0));
    }

    @Test
    void onlyLethalPhaseKillsTheRestVanish() {
        assertTrue(HerobrineEncounter.shouldVanishOnGaze(Phase.OBSERVATION));
        assertTrue(HerobrineEncounter.shouldVanishOnGaze(Phase.ESCALATION));
        assertFalse(HerobrineEncounter.shouldVanishOnGaze(Phase.LETHAL));
        assertTrue(HerobrineEncounter.isLethal(Phase.LETHAL));
        assertFalse(HerobrineEncounter.isLethal(Phase.ESCALATION));
    }

    @Test
    void sightingMemoryExpiresOutsideTheWindow() {
        assertFalse(HerobrineEncounter.isSightingExpired(100L, 50L, 100L));
        assertTrue(HerobrineEncounter.isSightingExpired(200L, 50L, 100L));
        // A non-positive window never expires (config "never forget").
        assertFalse(HerobrineEncounter.isSightingExpired(1_000_000L, 0L, 0L));
    }

    @Test
    void lethalCooldownBlocksRepeatKills() {
        assertTrue(HerobrineEncounter.isOnLethalCooldown(100L, 50L, 100L));
        assertFalse(HerobrineEncounter.isOnLethalCooldown(200L, 50L, 100L));
        // Never lethal before (-1) or zero cooldown means no cooldown.
        assertFalse(HerobrineEncounter.isOnLethalCooldown(100L, -1L, 100L));
        assertFalse(HerobrineEncounter.isOnLethalCooldown(100L, 50L, 0L));
    }

    @Test
    void omenIntensityScalesWithPhase() {
        HerobrineEncounter.OmenIntensity observation = HerobrineEncounter.omenIntensityFor(Phase.OBSERVATION);
        HerobrineEncounter.OmenIntensity escalation = HerobrineEncounter.omenIntensityFor(Phase.ESCALATION);
        HerobrineEncounter.OmenIntensity lethal = HerobrineEncounter.omenIntensityFor(Phase.LETHAL);
        assertTrue(observation.litBlocks() < escalation.litBlocks());
        assertTrue(escalation.litBlocks() < lethal.litBlocks());
        assertTrue(observation.footsteps() < escalation.footsteps());
        assertTrue(escalation.footsteps() < lethal.footsteps());
        assertTrue(observation.durationTicks() < lethal.durationTicks());
    }

    @Test
    void heartbeatIsSilentUntilEscalationAndFasterCloserAndLethal() {
        // OBSERVATION keeps the dead silence (period 0 = no beat).
        assertEquals(0, HerobrineEncounter.heartbeatPeriodTicks(Phase.OBSERVATION, 12.0));
        // ESCALATION onsets; closer is faster (smaller period) than farther.
        int escClose = HerobrineEncounter.heartbeatPeriodTicks(Phase.ESCALATION, 12.0);
        int escFar = HerobrineEncounter.heartbeatPeriodTicks(Phase.ESCALATION, 28.0);
        assertTrue(escClose > 0);
        assertTrue(escClose < escFar);
        // LETHAL is faster than ESCALATION at the same distance.
        int lethalClose = HerobrineEncounter.heartbeatPeriodTicks(Phase.LETHAL, 12.0);
        assertTrue(lethalClose < escClose);
    }

    @Test
    void resolveEncounterWalksTheDreadArcEndToEnd() {
        // Thresholds: ESCALATION at 2 sightings, LETHAL at 2 + 1 = 3; memory window 100 ticks,
        // lethal cooldown 50 ticks. Mirrors handleEncounter's per-encounter resolution exactly.
        int escalation = 2;
        int lethal = 1;
        long window = 100L;
        long cooldown = 50L;

        // 1st sighting at t=100: a fresh player stays in OBSERVATION; the sighting is recorded, no cue.
        HerobrineEncounter.Resolution first = HerobrineEncounter.resolveEncounter(
                new HerobrineEncounter.Snapshot(0, Long.MIN_VALUE, -1L, false),
                100L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.CONTINUE, first.action());
        assertEquals(Phase.OBSERVATION, first.phase());
        assertNull(first.cue());
        assertEquals(new HerobrineEncounter.Snapshot(1, 100L, -1L, false), first.nextSnapshot());

        // 2nd sighting at t=110 (inside the window): upgrades into ESCALATION with the BREATHING cue.
        HerobrineEncounter.Resolution second = HerobrineEncounter.resolveEncounter(
                first.nextSnapshot(), 110L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.CONTINUE, second.action());
        assertEquals(Phase.ESCALATION, second.phase());
        assertEquals(HerobrineEncounter.TransitionCue.BREATHING, second.cue());
        assertEquals(new HerobrineEncounter.Snapshot(2, 110L, -1L, false), second.nextSnapshot());

        // 3rd sighting at t=120: the deciding phase is still ESCALATION (non-lethal), but recording
        // the sighting upgrades the player into LETHAL and emits the WATCHED cue.
        HerobrineEncounter.Resolution third = HerobrineEncounter.resolveEncounter(
                second.nextSnapshot(), 120L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.CONTINUE, third.action());
        assertEquals(Phase.LETHAL, third.phase());
        assertEquals(HerobrineEncounter.TransitionCue.WATCHED, third.cue());
        assertEquals(new HerobrineEncounter.Snapshot(3, 120L, -1L, false), third.nextSnapshot());

        // 4th encounter at t=130: LETHAL. No sighting is recorded (count and lastSightingTick stay
        // untouched), the lethal tick is stamped, the veteran flag becomes permanent, no cue.
        HerobrineEncounter.Resolution kill = HerobrineEncounter.resolveEncounter(
                third.nextSnapshot(), 130L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.LETHAL, kill.action());
        assertEquals(Phase.LETHAL, kill.phase());
        assertNull(kill.cue());
        assertEquals(new HerobrineEncounter.Snapshot(3, 120L, 130L, true), kill.nextSnapshot());

        // t=150 (20 ticks after the kill, cooldown 50): lethality is suppressed by the cooldown —
        // the encounter downgrades to a recorded non-lethal sighting, no cue (LETHAL → LETHAL is
        // not an upgrade).
        HerobrineEncounter.Resolution suppressed = HerobrineEncounter.resolveEncounter(
                kill.nextSnapshot(), 150L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.CONTINUE, suppressed.action());
        assertEquals(Phase.LETHAL, suppressed.phase());
        assertNull(suppressed.cue());
        assertEquals(new HerobrineEncounter.Snapshot(4, 150L, 130L, true), suppressed.nextSnapshot());

        // t=190 (60 ticks after the kill): the cooldown has expired, so the veteran dies again.
        HerobrineEncounter.Resolution again = HerobrineEncounter.resolveEncounter(
                suppressed.nextSnapshot(), 190L, escalation, lethal, window, cooldown);
        assertEquals(HerobrineEncounter.Action.LETHAL, again.action());
        assertEquals(Phase.LETHAL, again.phase());
        assertEquals(new HerobrineEncounter.Snapshot(4, 150L, 190L, true), again.nextSnapshot());
    }

    @Test
    void resolveEncounterDecaysExpiredSightingsButNeverTheVeteranFlag() {
        // A non-veteran whose sightings aged out of the window restarts the arc: the stale count
        // decays to zero and THIS encounter is recorded as the first sighting again (0 + 1).
        HerobrineEncounter.Resolution decayed = HerobrineEncounter.resolveEncounter(
                new HerobrineEncounter.Snapshot(2, 100L, -1L, false),
                300L, 2, 1, 100L, 50L);
        assertEquals(HerobrineEncounter.Action.CONTINUE, decayed.action());
        assertEquals(Phase.OBSERVATION, decayed.phase());
        assertNull(decayed.cue());
        assertEquals(new HerobrineEncounter.Snapshot(1, 300L, -1L, false), decayed.nextSnapshot());

        // A veteran's count decays too, but escalatedBefore keeps the encounter lethal (once the
        // cooldown has passed) — decay never clears the veteran flag.
        HerobrineEncounter.Resolution veteran = HerobrineEncounter.resolveEncounter(
                new HerobrineEncounter.Snapshot(4, 150L, 190L, true),
                400L, 2, 1, 100L, 50L);
        assertEquals(HerobrineEncounter.Action.LETHAL, veteran.action());
        assertEquals(Phase.LETHAL, veteran.phase());
        assertEquals(new HerobrineEncounter.Snapshot(0, 150L, 400L, true), veteran.nextSnapshot());
    }

    @Test
    void phaseAfterDecayIsAReadOnlyPhaseQuery() {
        HerobrineEncounter.Snapshot two = new HerobrineEncounter.Snapshot(2, 100L, -1L, false);
        // Inside the memory window the accumulated sightings stand: ESCALATION (thresholds 2/1).
        assertEquals(Phase.ESCALATION, HerobrineEncounter.phaseAfterDecay(two, 150L, 2, 1, 100L));
        // Outside the window the count decays: back to OBSERVATION.
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseAfterDecay(two, 300L, 2, 1, 100L));
        // A non-positive window never expires (config "never forget").
        assertEquals(Phase.ESCALATION, HerobrineEncounter.phaseAfterDecay(two, 1_000_000L, 2, 1, 0L));
        // Veterans read as LETHAL regardless of decay.
        assertEquals(Phase.LETHAL, HerobrineEncounter.phaseAfterDecay(
                new HerobrineEncounter.Snapshot(0, Long.MIN_VALUE, 190L, true), 300L, 2, 1, 100L));
        // Pure query: it never records a sighting, so asking repeatedly cannot advance the arc.
        HerobrineEncounter.Snapshot one = new HerobrineEncounter.Snapshot(1, 100L, -1L, false);
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseAfterDecay(one, 110L, 2, 1, 100L));
        assertEquals(Phase.OBSERVATION, HerobrineEncounter.phaseAfterDecay(one, 111L, 2, 1, 100L));
    }

    @Test
    void transitionCuesOnlyFireOnUpgrades() {
        assertEquals(HerobrineEncounter.TransitionCue.BREATHING,
                HerobrineEncounter.phaseTransitionCue(Phase.OBSERVATION, Phase.ESCALATION));
        assertEquals(HerobrineEncounter.TransitionCue.WATCHED,
                HerobrineEncounter.phaseTransitionCue(Phase.ESCALATION, Phase.LETHAL));
        // No cue when the phase does not advance.
        assertNull(HerobrineEncounter.phaseTransitionCue(Phase.ESCALATION, Phase.ESCALATION));
        assertNull(HerobrineEncounter.phaseTransitionCue(Phase.LETHAL, Phase.OBSERVATION));
        // Each cue maps to a herobrine subtitle key.
        assertTrue(HerobrineEncounter.TransitionCue.BREATHING.subtitleKey().contains("herobrine"));
    }
}
