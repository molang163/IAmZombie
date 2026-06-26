package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.sleep.SleepAction;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
