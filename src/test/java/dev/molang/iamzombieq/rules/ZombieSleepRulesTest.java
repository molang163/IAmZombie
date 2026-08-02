package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.sleep.SleepAction;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules.NapWakeReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZombieSleepRulesTest {
    @Test
    void zombiePlayersExplodeBedsButNonZombieUsePassesThrough() {
        assertEquals(SleepAction.BED_EXPLODES, ZombieSleepRules.useBed(true));
        assertEquals(SleepAction.PASS_THROUGH, ZombieSleepRules.useBed(false));
    }

    @Test
    void zombieBedExplosionUsesConfigurableNetherStyleDefaults() {
        ZombieSleepRules.BedExplosionSettings settings = ZombieSleepRules.bedExplosionSettings(5.0F, true);

        assertEquals(5.0F, settings.power());
        assertTrue(settings.causesFire());
    }

    @Test
    void coffinsAreZombieOnlyAndRespectNearbyHostiles() {
        assertEquals(SleepAction.DENY_NOT_ZOMBIE, ZombieSleepRules.useCoffin(false, false, true));
        assertEquals(SleepAction.DENY_HOSTILE_NEARBY, ZombieSleepRules.useCoffin(true, true, true));
    }

    @Test
    void coffinsRestDuringDayAndSetRespawnAtNight() {
        assertEquals(SleepAction.REST_UNTIL_NIGHT, ZombieSleepRules.useCoffin(true, false, true));
        assertEquals(SleepAction.SET_RESPAWN, ZombieSleepRules.useCoffin(true, false, false));
    }

    @Test
    void coffinVoteSinglePlayerNeedsOne() {
        assertEquals(1, ZombieSleepRules.coffinSleepersNeeded(1, 100));
        assertTrue(ZombieSleepRules.enoughCoffinSleepers(1, 1, 100));
    }

    @Test
    void coffinVoteTwoPlayersFullNeedsTwo() {
        assertEquals(2, ZombieSleepRules.coffinSleepersNeeded(2, 100));
        assertFalse(ZombieSleepRules.enoughCoffinSleepers(1, 2, 100));
        assertTrue(ZombieSleepRules.enoughCoffinSleepers(2, 2, 100));
    }

    @Test
    void coffinVoteHalfPercentageRoundsUp() {
        // ceil(3 * 50 / 100) = ceil(1.5) = 2.
        assertEquals(2, ZombieSleepRules.coffinSleepersNeeded(3, 50));
        assertFalse(ZombieSleepRules.enoughCoffinSleepers(1, 3, 50));
        assertTrue(ZombieSleepRules.enoughCoffinSleepers(2, 3, 50));
    }

    @Test
    void coffinVoteClampsAndNeverZero() {
        assertEquals(1, ZombieSleepRules.coffinSleepersNeeded(4, 0));   // 0% still needs at least one.
        assertEquals(4, ZombieSleepRules.coffinSleepersNeeded(4, 999)); // >100 is clamped to 100.
        assertEquals(1, ZombieSleepRules.coffinSleepersNeeded(0, 100)); // no eligible zombies still needs one.
        assertEquals(1, ZombieSleepRules.coffinSleepersNeeded(-5, 100)); // negative eligible clamps to zero -> one.
    }

    @Test
    void coffinMessageKeyRoutesEachActionIncludingNightRespawnSet() {
        assertEquals("iamzombieq.message.coffin.zombie_only",
                ZombieSleepRules.coffinMessageKey(SleepAction.DENY_NOT_ZOMBIE, false));
        assertEquals("iamzombieq.message.coffin.not_safe",
                ZombieSleepRules.coffinMessageKey(SleepAction.DENY_HOSTILE_NEARBY, false));
        // REST_UNTIL_NIGHT: nap began -> lying_down; nap fell back -> respawn_set_only (the daytime "night never came").
        assertEquals("iamzombieq.message.coffin.lying_down",
                ZombieSleepRules.coffinMessageKey(SleepAction.REST_UNTIL_NIGHT, true));
        assertEquals("iamzombieq.message.coffin.respawn_set_only",
                ZombieSleepRules.coffinMessageKey(SleepAction.REST_UNTIL_NIGHT, false));
        // SET_RESPAWN (night / clockless dimension): the NEUTRAL respawn_set line, NOT the daytime respawn_set_only
        // whose "but night never came" is false there -- this is the just-shipped night-message fix, now guarded.
        assertEquals("iamzombieq.message.coffin.respawn_set",
                ZombieSleepRules.coffinMessageKey(SleepAction.SET_RESPAWN, false));
        // Message-less actions send no overlay.
        assertNull(ZombieSleepRules.coffinMessageKey(SleepAction.PASS_THROUGH, false));
        assertNull(ZombieSleepRules.coffinMessageKey(SleepAction.BED_EXPLODES, false));
    }

    @Test
    void bedExplosionSettingsClampNegativePowerAndCarryNoFire() {
        ZombieSleepRules.BedExplosionSettings clamped = ZombieSleepRules.bedExplosionSettings(-3.0F, false);
        assertEquals(0.0F, clamped.power());   // negative power clamps to 0.
        assertFalse(clamped.causesFire());     // causesFire carried through unchanged.
    }

    @Test
    void coffinVoteProgressAllowsNapStartThenThrottlesToOncePerSecond() {
        // Full-window enumeration (not just a few sample points) so a subtly wrong throttle -- e.g. the tempting but
        // wrong "delta == 0 || delta == 20" (which only fires once at tick 20 and never again) -- cannot slip through.
        List<Long> allowedTicks = new ArrayList<>();
        for (long elapsed = 0L; elapsed <= 301L; elapsed++) {
            if (ZombieSleepRules.shouldSendCoffinVoteProgress(elapsed, 0L)) {
                allowedTicks.add(elapsed);
            }
        }
        assertEquals(
                List.of(0L, 20L, 40L, 60L, 80L, 100L, 120L, 140L, 160L, 180L, 200L, 220L, 240L, 260L, 280L, 300L),
                allowedTicks,
                "the throttle should allow exactly every 20th tick from nap start through tick 300");
        assertFalse(ZombieSleepRules.shouldSendCoffinVoteProgress(301L, 0L), "tick 301 must not send");
    }

    @Test
    void coffinVoteProgressThrottleIsRelativeToNapStartNotAbsoluteGameTime() {
        long start = 137L;
        assertTrue(ZombieSleepRules.shouldSendCoffinVoteProgress(start, start));
        assertFalse(ZombieSleepRules.shouldSendCoffinVoteProgress(start + 19, start));
        assertTrue(ZombieSleepRules.shouldSendCoffinVoteProgress(start + 20, start));
        assertFalse(ZombieSleepRules.shouldSendCoffinVoteProgress(start + 21, start));
    }

    @Test
    void napWakeReasonHasExactlyFourValuesInThisOrder() {
        assertEquals(
                List.of(NapWakeReason.DISTURBED, NapWakeReason.NOT_ENOUGH_TIMEOUT,
                        NapWakeReason.VOTE_PASSED, NapWakeReason.VOTE_PASSED_NO_SKIP),
                List.of(NapWakeReason.values()),
                "NapWakeReason must be exactly these four values, in this order");
    }

    @Test
    void napWakeMessageKeyExhaustivelyMapsAllFourReasons() {
        assertEquals("iamzombieq.message.coffin.disturbed",
                ZombieSleepRules.napWakeMessageKey(NapWakeReason.DISTURBED));
        assertEquals("iamzombieq.message.coffin.not_enough",
                ZombieSleepRules.napWakeMessageKey(NapWakeReason.NOT_ENOUGH_TIMEOUT));
        assertEquals("iamzombieq.message.coffin.rested",
                ZombieSleepRules.napWakeMessageKey(NapWakeReason.VOTE_PASSED));
        assertEquals("iamzombieq.message.coffin.respawn_set_only",
                ZombieSleepRules.napWakeMessageKey(NapWakeReason.VOTE_PASSED_NO_SKIP));
    }

    @Test
    void coffinVoteProgressMessageKeyReturnsThePlayersSleepingKey() {
        assertEquals("iamzombieq.message.coffin.players_sleeping", ZombieSleepRules.coffinVoteProgressMessageKey());
    }
}
