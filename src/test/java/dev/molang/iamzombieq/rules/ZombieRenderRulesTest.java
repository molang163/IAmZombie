package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieForm;

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
    }

    @Test
    void babyDrownedRenderPlanUsesBabyDrownedEntityShape() {
        ZombieRenderPlan plan = ZombieRenderRules.monsterBodyPlan(new ZombieState(ZombieForm.DROWNED, ZombieSize.BABY));

        assertEquals(ZombieMonsterBody.DROWNED_BABY, plan.body());
        assertEquals("minecraft:drowned", plan.entityTypeId());
        assertEquals("minecraft:textures/entity/zombie/drowned_baby.png", plan.texturePath());
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
}
