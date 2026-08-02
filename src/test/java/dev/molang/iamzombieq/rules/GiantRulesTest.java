package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.giant.BlockCrushQuery;
import dev.molang.iamzombieq.rules.giant.GiantRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Giant-form balance assertions, moved out of {@code ZombieBalanceRulesTest} alongside the R3 GiantRules split. */
class GiantRulesTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void giantFormUsesGiantScaleHealthReachStepAndAttackDefaults() {
        assertEquals(100.0, ZombieBalanceRules.maxHealth(ZombieForm.GIANT), EPSILON);
        // Reach/step are scaled to the 6x body as their own explicit targets (设计指南 §2.4): block 4.5x6=27,
        // entity 3.0x6=18, step 0.6x6=3.6. Attack is a flat (not difficulty-scaled) value, bumped to 55 in the
        // strengthening pass to stay slightly above the Warden (vanilla melee 30); the stomp aura is 5.0/10.0.
        assertEquals(27.0, GiantRules.giantBlockInteractionRange(), EPSILON);
        assertEquals(18.0, GiantRules.giantEntityInteractionRange(), EPSILON);
        assertEquals(3.6, GiantRules.giantStepHeight(), EPSILON);
        assertEquals(3.0, GiantRules.giantSafeFallBonus(), EPSILON);
        assertEquals(55.0, GiantRules.giantAttackDamage(), EPSILON);
        assertEquals(5.0, GiantRules.giantAutoDamageRadius(), EPSILON);
        assertEquals(10.0, GiantRules.giantAutoDamageAmount(), EPSILON);
        assertEquals(3, GiantRules.giantBlockDestructionRadius());
    }

    @Test
    void giantSwingDestructionDefaultsAreBoundedAndCoolDown() {
        assertEquals(17, GiantRules.giantSwingCubeEdge());
        assertEquals(200, GiantRules.giantSwingMaxBlocks());
        assertEquals(25L, GiantRules.giantSwingCooldownTicks());
        assertEquals(256, GiantRules.giantPassiveDestroyCapPerTick());
    }

    @Test
    void giantPassiveReachConstantsAreTheWiderTallerFootprint() {
        // The strengthening pass widens (X/Z) and heightens (Y) the passive walk-destruction footprint so the giant
        // razes a bigger swath as it strides. The foot layer and below are still protected by giantDestroysBlockLayer.
        assertEquals(2.0, GiantRules.giantPassiveReachHorizontal(), EPSILON);
        assertEquals(2.0, GiantRules.giantPassiveReachVertical(), EPSILON);
    }

    @Test
    void giantContactDestructionPreservesTheFootLayerAndBelow() {
        // Foot (bounding-box min Y) at 64.0 → block layer 64 and anything below is preserved; layers above destroy.
        double footY = 64.0;
        assertFalse(GiantRules.giantDestroysBlockLayer(63, footY), "below-foot layer is preserved");
        assertFalse(GiantRules.giantDestroysBlockLayer(64, footY), "foot layer itself is preserved");
        assertTrue(GiantRules.giantDestroysBlockLayer(65, footY), "body layers above the feet are destroyed");
        assertTrue(GiantRules.giantDestroysBlockLayer(70, footY), "upper body layers are destroyed");

        // A fractional foot Y still preserves the cell that contains it.
        assertFalse(GiantRules.giantDestroysBlockLayer(64, 64.3), "fractional foot Y keeps its own cell");
        assertTrue(GiantRules.giantDestroysBlockLayer(65, 64.3));
    }

    @Test
    void giantCrushKernelRespectsTagsBlacklistAndHardnessFallback() {
        // BlockCrushQuery(isAir, hasBlockEntity, isFluid, isSoftTag, isImmuneTag, destroySpeed, maxHardness)
        float swing = GiantRules.GIANT_SWING_MAX_HARDNESS;
        float passive = GiantRules.GIANT_PASSIVE_MAX_HARDNESS;
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(true, false, false, false, false, 0.0F, swing)), "air is never crushed");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, true, false, true, false, 0.0F, swing)), "block entities (containers) are preserved even if soft-tagged");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, true, true, false, 0.0F, swing)), "fluids are never crushed");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, true, true, 0.0F, swing)), "GIANT_IMMUNE blacklist wins over the soft whitelist");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, -1.0F, swing)), "unbreakable bedrock (negative hardness) is preserved");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, swing + 1.0F, swing)), "very hard blocks (obsidian) are preserved");
        // Soft-tagged blocks are always crushed regardless of hardness fallback.
        assertTrue(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, true, false, 100.0F, passive)), "GIANT_SOFT whitelist is always crushable");
        // The passive cap is now stone-tier: the WALKING giant razes stone (1.5) and cobblestone/stone-brick (2.0),
        // but deepslate (3.0) and harder still stop it. The active swing breaks an even higher tier.
        assertTrue(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, 1.5F, passive)), "stone (1.5) is razed by the WALKING giant (stone-tier passive cap)");
        assertTrue(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, 2.0F, passive)), "cobblestone/stone-brick (2.0) is razed by the WALKING giant (boundary, <=)");
        assertFalse(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, 3.0F, passive)), "deepslate (3.0) still stops the WALKING giant");
        assertTrue(GiantRules.giantCanCrush(new BlockCrushQuery(false, false, false, false, false, 1.5F, swing)), "untagged stone is also broken by the giant's SWING (high cap)");
    }
}
