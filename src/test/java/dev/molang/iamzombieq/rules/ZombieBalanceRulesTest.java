package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
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
    void innateArmorMatchesCapturedChoices() {
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.NORMAL));
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.DROWNED));
        assertEquals(4, ZombieBalanceRules.innateArmor(ZombieForm.HUSK));
        assertEquals(2, ZombieBalanceRules.innateArmor(ZombieForm.ZOMBIFIED_PIGLIN));
    }

    @Test
    void villagerAndHorseInfectionChanceMatchesDifficulty() {
        assertEquals(0.25, ZombieBalanceRules.infectionChance(GameDifficulty.EASY));
        assertEquals(0.50, ZombieBalanceRules.infectionChance(GameDifficulty.NORMAL));
        assertEquals(1.00, ZombieBalanceRules.infectionChance(GameDifficulty.HARD));
        assertEquals(0.00, ZombieBalanceRules.infectionChance(GameDifficulty.PEACEFUL));
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
    void giantFormUsesGiantScaleHealthReachStepAndAttackDefaults() {
        assertEquals(100.0, ZombieBalanceRules.maxHealth(ZombieForm.GIANT), EPSILON);
        // Reach/step are scaled to the 6x body as their own explicit targets (设计指南 §2.4): block 4.5x6=27,
        // entity 3.0x6=18, step 0.6x6=3.6. Attack is a flat (not difficulty-scaled) value, bumped to 55 in the
        // strengthening pass to stay slightly above the Warden (vanilla melee 30); the stomp aura is 5.0/10.0.
        assertEquals(27.0, ZombieBalanceRules.giantBlockInteractionRange(), EPSILON);
        assertEquals(18.0, ZombieBalanceRules.giantEntityInteractionRange(), EPSILON);
        assertEquals(3.6, ZombieBalanceRules.giantStepHeight(), EPSILON);
        assertEquals(3.0, ZombieBalanceRules.giantSafeFallBonus(), EPSILON);
        assertEquals(55.0, ZombieBalanceRules.giantAttackDamage(), EPSILON);
        assertEquals(5.0, ZombieBalanceRules.giantAutoDamageRadius(), EPSILON);
        assertEquals(10.0, ZombieBalanceRules.giantAutoDamageAmount(), EPSILON);
        assertEquals(3, ZombieBalanceRules.giantBlockDestructionRadius());
    }

    @Test
    void giantSwingDestructionDefaultsAreBoundedAndCoolDown() {
        assertEquals(17, ZombieBalanceRules.giantSwingCubeEdge());
        assertEquals(200, ZombieBalanceRules.giantSwingMaxBlocks());
        assertEquals(25L, ZombieBalanceRules.giantSwingCooldownTicks());
        assertEquals(256, ZombieBalanceRules.giantPassiveDestroyCapPerTick());
    }

    @Test
    void giantPassiveReachConstantsAreTheWiderTallerFootprint() {
        // The strengthening pass widens (X/Z) and heightens (Y) the passive walk-destruction footprint so the giant
        // razes a bigger swath as it strides. The foot layer and below are still protected by giantDestroysBlockLayer.
        assertEquals(2.0, ZombieBalanceRules.giantPassiveReachHorizontal(), EPSILON);
        assertEquals(2.0, ZombieBalanceRules.giantPassiveReachVertical(), EPSILON);
    }

    @Test
    void giantContactDestructionPreservesTheFootLayerAndBelow() {
        // Foot (bounding-box min Y) at 64.0 → block layer 64 and anything below is preserved; layers above destroy.
        double footY = 64.0;
        assertFalse(ZombieBalanceRules.giantDestroysBlockLayer(63, footY), "below-foot layer is preserved");
        assertFalse(ZombieBalanceRules.giantDestroysBlockLayer(64, footY), "foot layer itself is preserved");
        assertTrue(ZombieBalanceRules.giantDestroysBlockLayer(65, footY), "body layers above the feet are destroyed");
        assertTrue(ZombieBalanceRules.giantDestroysBlockLayer(70, footY), "upper body layers are destroyed");

        // A fractional foot Y still preserves the cell that contains it.
        assertFalse(ZombieBalanceRules.giantDestroysBlockLayer(64, 64.3), "fractional foot Y keeps its own cell");
        assertTrue(ZombieBalanceRules.giantDestroysBlockLayer(65, 64.3));
    }

    @Test
    void giantCrushKernelRespectsTagsBlacklistAndHardnessFallback() {
        // (isAir, hasBlockEntity, isFluid, isSoftTag, isImmuneTag, destroySpeed, maxHardness)
        float swing = ZombieBalanceRules.GIANT_SWING_MAX_HARDNESS;
        float passive = ZombieBalanceRules.GIANT_PASSIVE_MAX_HARDNESS;
        assertFalse(ZombieBalanceRules.giantCanCrush(true, false, false, false, false, 0.0F, swing), "air is never crushed");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, true, false, true, false, 0.0F, swing), "block entities (containers) are preserved even if soft-tagged");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, false, true, true, false, 0.0F, swing), "fluids are never crushed");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, false, false, true, true, 0.0F, swing), "GIANT_IMMUNE blacklist wins over the soft whitelist");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, -1.0F, swing), "unbreakable bedrock (negative hardness) is preserved");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, swing + 1.0F, swing), "very hard blocks (obsidian) are preserved");
        // Soft-tagged blocks are always crushed regardless of hardness fallback.
        assertTrue(ZombieBalanceRules.giantCanCrush(false, false, false, true, false, 100.0F, passive), "GIANT_SOFT whitelist is always crushable");
        // The passive cap is now stone-tier: the WALKING giant razes stone (1.5) and cobblestone/stone-brick (2.0),
        // but deepslate (3.0) and harder still stop it. The active swing breaks an even higher tier.
        assertTrue(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, 1.5F, passive), "stone (1.5) is razed by the WALKING giant (stone-tier passive cap)");
        assertTrue(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, 2.0F, passive), "cobblestone/stone-brick (2.0) is razed by the WALKING giant (boundary, <=)");
        assertFalse(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, 3.0F, passive), "deepslate (3.0) still stops the WALKING giant");
        assertTrue(ZombieBalanceRules.giantCanCrush(false, false, false, false, false, 1.5F, swing), "untagged stone is also broken by the giant's SWING (high cap)");
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
