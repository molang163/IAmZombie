package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ZombieBalanceRulesTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void namedPlayerBalanceConstantsRetainTheirGameplayValues() {
        assertEquals(220, ZombieBalanceRules.EFFECT_REFRESH_MARGIN_TICKS);
        assertEquals(300, ZombieBalanceRules.HUSK_MELEE_HUNGER_DURATION_TICKS);
        assertEquals(8.0F, ZombieBalanceRules.SUNLIGHT_BURN_DURATION_SECONDS);
        assertEquals(0.5F, ZombieBalanceRules.EVOLUTION_RESPAWN_HEALTH_FRACTION);
    }

    @Test
    void innateArmorMatchesCapturedChoices() {
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.NORMAL));
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.DROWNED));
        assertEquals(4, ZombieBalanceRules.innateArmor(ZombieForm.HUSK));
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.ZOMBIFIED_PIGLIN));
    }

    @Test
    void steveHeadDropRatesMatchDefaults() {
        assertEquals(0.025, ZombieBalanceRules.normalSteveHeadDropChance(0));
        assertEquals(0.035, ZombieBalanceRules.normalSteveHeadDropChance(1));
        assertEquals(0.01, ZombieBalanceRules.matchboxSteveHeadDropChance(0));
        assertEquals(0.02, ZombieBalanceRules.matchboxSteveHeadDropChance(1));
        assertEquals(0.30, ZombieBalanceRules.strongSteveHeadDropChance(0));
        assertEquals(0.35, ZombieBalanceRules.strongSteveHeadDropChance(1));
    }

    @Test
    void zombifiedPiglinFormHasFireResistanceAndConsumesGoldMoreSlowly() {
        assertEquals(true, ZombieBalanceRules.hasFireResistance(ZombieForm.ZOMBIFIED_PIGLIN));
        assertEquals(false, ZombieBalanceRules.hasFireResistance(ZombieForm.NORMAL));
        assertEquals(0.25, ZombieBalanceRules.goldDurabilityConsumptionMultiplier(ZombieForm.ZOMBIFIED_PIGLIN), EPSILON);
        assertEquals(1.0, ZombieBalanceRules.goldDurabilityConsumptionMultiplier(ZombieForm.HUSK), EPSILON);
        assertEquals(true, ZombieBalanceRules.zombifiedPiglinsDefendPlayer(ZombieForm.ZOMBIFIED_PIGLIN));
    }

    @Test
    void scaledDurabilityDamageMatchesTheAuthoritativeBoundaryTable() {
        // M4 boundary table (authoritative, C3): scale by goldDurabilityConsumptionMultiplier(form) (ZOMBIFIED_PIGLIN
        // 0.25, others 1.0), truncate, add 1 only when rd < the fractional remainder, then clamp to [0, amount].
        // 4 * 0.25 = 1.0 → trunc 1, fraction 0.0; rd 0.999 < 0.0 false → 1; clamp[0,4] → 1
        assertEquals(1, ZombieBalanceRules.scaledDurabilityDamage(4, ZombieForm.ZOMBIFIED_PIGLIN, 0.999));
        // 5 * 0.25 = 1.25 → trunc 1, fraction 0.25; rd 0.2 < 0.25 true → 2; clamp[0,5] → 2
        assertEquals(2, ZombieBalanceRules.scaledDurabilityDamage(5, ZombieForm.ZOMBIFIED_PIGLIN, 0.2));
        // 5 * 0.25 = 1.25 → trunc 1, fraction 0.25; rd 0.3 < 0.25 false → 1; clamp[0,5] → 1
        assertEquals(1, ZombieBalanceRules.scaledDurabilityDamage(5, ZombieForm.ZOMBIFIED_PIGLIN, 0.3));
        // 0 * 0.25 = 0.0 → trunc 0, fraction 0.0; rd 0.5 < 0.0 false → 0; clamp[0,0] → 0
        assertEquals(0, ZombieBalanceRules.scaledDurabilityDamage(0, ZombieForm.ZOMBIFIED_PIGLIN, 0.5));
        // 1 * 0.25 = 0.25 → trunc 0, fraction 0.25; rd 0.0 < 0.25 true → 1; clamp[0,1] → 1
        assertEquals(1, ZombieBalanceRules.scaledDurabilityDamage(1, ZombieForm.ZOMBIFIED_PIGLIN, 0.0));
        // 1 * 0.25 = 0.25 → trunc 0, fraction 0.25; rd 0.3 < 0.25 false → 0; clamp[0,1] → 0
        assertEquals(0, ZombieBalanceRules.scaledDurabilityDamage(1, ZombieForm.ZOMBIFIED_PIGLIN, 0.3));
        // 2 * 1.0 = 2.0 → trunc 2, fraction 0.0; rd 0.5 < 0.0 false → 2; clamp[0,2] → 2
        assertEquals(2, ZombieBalanceRules.scaledDurabilityDamage(2, ZombieForm.NORMAL, 0.5));
        // 3 * 0.25 = 0.75 → trunc 0, fraction 0.75; rd 0.74 < 0.75 true → 1; clamp[0,3] → 1
        assertEquals(1, ZombieBalanceRules.scaledDurabilityDamage(3, ZombieForm.ZOMBIFIED_PIGLIN, 0.74));
        // 3 * 0.25 = 0.75 → trunc 0, fraction 0.75; rd 0.76 < 0.75 false → 0; clamp[0,3] → 0
        assertEquals(0, ZombieBalanceRules.scaledDurabilityDamage(3, ZombieForm.ZOMBIFIED_PIGLIN, 0.76));
    }

    @Test
    void emptyHandWoodenDoorBreakBoostIsGatedOnEmptyHandAndDoor() {
        // (mainHandEmpty, blockIsWoodenDoor)
        assertTrue(ZombieBalanceRules.shouldBoostWoodenDoorBreak(true, true), "bare-handed door break is boosted");
        assertFalse(ZombieBalanceRules.shouldBoostWoodenDoorBreak(false, true), "holding an item disables the door boost");
        assertFalse(ZombieBalanceRules.shouldBoostWoodenDoorBreak(true, false), "non-door blocks are not boosted");
        assertFalse(ZombieBalanceRules.shouldBoostWoodenDoorBreak(false, false));
        assertTrue(ZombieBalanceRules.WOODEN_DOOR_BREAK_MULTIPLIER > 1.0F, "the boost must speed up breaking");
    }

    @Test
    void huskFirstRewardBundleIsDeterministicForASeededRandomAndBounded() {
        List<ZombieBalanceRules.RewardEntry> first = ZombieBalanceRules.huskFirstRewardBundle(new Random(42L));
        List<ZombieBalanceRules.RewardEntry> second = ZombieBalanceRules.huskFirstRewardBundle(new Random(42L));
        assertEquals(first, second, "a seeded RandomGenerator must produce the same bundle");

        // Invariants: non-empty, bounded stack count, distinct ids, positive counts within the configured range.
        assertTrue(first.size() >= ZombieBalanceRules.HUSK_REWARD_MIN_STACKS, "bundle must not be empty");
        assertTrue(first.size() <= ZombieBalanceRules.HUSK_REWARD_MAX_STACKS, "bundle stack count is bounded");
        long distinctIds = first.stream().map(ZombieBalanceRules.RewardEntry::itemId).distinct().count();
        assertEquals(first.size(), distinctIds, "bundle entries are distinct items");
        for (ZombieBalanceRules.RewardEntry entry : first) {
            assertTrue(entry.count() > 0, "every reward stack has a positive count");
            assertTrue(entry.itemId().contains(":"), "ids are namespaced for registry lookup");
        }

        // Different seeds can produce different bundles, but all must still satisfy the invariants.
        for (long seed = 0; seed < 200; seed++) {
            List<ZombieBalanceRules.RewardEntry> bundle = ZombieBalanceRules.huskFirstRewardBundle(new Random(seed));
            assertTrue(bundle.size() >= ZombieBalanceRules.HUSK_REWARD_MIN_STACKS && bundle.size() <= ZombieBalanceRules.HUSK_REWARD_MAX_STACKS);
            for (ZombieBalanceRules.RewardEntry entry : bundle) {
                assertTrue(entry.count() > 0);
            }
        }
    }
}
