package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieRenderRulesTest {
    @Test
    void monsterTextureFollowsCurrentZombieForm() {
        assertEquals("minecraft:textures/entity/zombie/zombie.png", ZombieRenderRules.monsterTexturePath(ZombieForm.NORMAL));
        assertEquals("minecraft:textures/entity/zombie/drowned.png", ZombieRenderRules.monsterTexturePath(ZombieForm.DROWNED));
        assertEquals("minecraft:textures/entity/zombie/husk.png", ZombieRenderRules.monsterTexturePath(ZombieForm.HUSK));
        assertEquals("minecraft:textures/entity/piglin/zombified_piglin.png",
                ZombieRenderRules.monsterTexturePath(ZombieForm.ZOMBIFIED_PIGLIN));
    }

    @Test
    void babyMonsterRenderPlansUseNodeNativeTexturesWithoutChangingTheirShapes() {
        boolean distinctBabyTextures =
                StonecutterCapabilityMatrix.hasDistinctBabyMonsterTextures();

        assertBabyPlan(
                ZombieForm.NORMAL,
                ZombieMonsterBody.ZOMBIE_BABY,
                "minecraft:zombie",
                distinctBabyTextures
                        ? "minecraft:textures/entity/zombie/zombie_baby.png"
                        : "minecraft:textures/entity/zombie/zombie.png");
        assertBabyPlan(
                ZombieForm.DROWNED,
                ZombieMonsterBody.DROWNED_BABY,
                "minecraft:drowned",
                distinctBabyTextures
                        ? "minecraft:textures/entity/zombie/drowned_baby.png"
                        : "minecraft:textures/entity/zombie/drowned.png");
        assertBabyPlan(
                ZombieForm.HUSK,
                ZombieMonsterBody.HUSK_BABY,
                "minecraft:husk",
                distinctBabyTextures
                        ? "minecraft:textures/entity/zombie/husk_baby.png"
                        : "minecraft:textures/entity/zombie/husk.png");
    }

    @Test
    void renderModeDecidesWhetherToReplacePlayerSkin() {
        assertTrue(ZombieRenderRules.usesMonsterTexture(ZombiePlayerSkinMode.MONSTER_TEXTURE));
        assertFalse(ZombieRenderRules.usesMonsterTexture(ZombiePlayerSkinMode.PLAYER_SKIN));
    }

    @Test
    void creativePlayersStillUseZombieVisuals() {
        assertTrue(ZombieRenderRules.shouldUseZombieVisuals(false, true, ZombieForm.NORMAL));
        assertTrue(ZombieRenderRules.shouldUseZombieVisuals(false, true, ZombieForm.GIANT));
        assertFalse(ZombieRenderRules.shouldUseZombieVisuals(true, false, ZombieForm.GIANT));
        assertTrue(ZombieRenderRules.shouldUseZombieVisuals(false, false, ZombieForm.NORMAL));
    }

    private static void assertBabyPlan(
            ZombieForm form,
            ZombieMonsterBody expectedBody,
            String expectedEntityType,
            String expectedTexture) {
        ZombieRenderPlan plan =
                ZombieRenderRules.monsterBodyPlan(new ZombieState(form, ZombieSize.BABY));

        assertEquals(expectedBody, plan.body());
        assertEquals(expectedEntityType, plan.entityTypeId());
        assertEquals(expectedTexture, plan.texturePath());
    }
}
