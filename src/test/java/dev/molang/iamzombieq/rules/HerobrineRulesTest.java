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
