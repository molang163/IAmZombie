package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieForm;

public final class ZombieRenderRules {
    private ZombieRenderRules() {
    }

    public static String monsterTexturePath(ZombieForm form) {
        return switch (form) {
            case DROWNED -> "minecraft:textures/entity/zombie/drowned.png";
            case HUSK -> "minecraft:textures/entity/zombie/husk.png";
            case ZOMBIFIED_PIGLIN -> "minecraft:textures/entity/piglin/zombified_piglin.png";
            case GIANT -> "minecraft:textures/entity/zombie/zombie.png";
            case NORMAL -> "minecraft:textures/entity/zombie/zombie.png";
        };
    }

    public static ZombieRenderPlan monsterBodyPlan(ZombieState state) {
        boolean baby = state.size() == ZombieSize.BABY;
        return switch (state.form()) {
            case DROWNED -> new ZombieRenderPlan(
                    baby ? ZombieMonsterBody.DROWNED_BABY : ZombieMonsterBody.DROWNED,
                    "minecraft:drowned",
                    baby ? "minecraft:textures/entity/zombie/drowned_baby.png" : monsterTexturePath(ZombieForm.DROWNED)
            );
            case HUSK -> new ZombieRenderPlan(
                    baby ? ZombieMonsterBody.HUSK_BABY : ZombieMonsterBody.HUSK,
                    "minecraft:husk",
                    baby ? "minecraft:textures/entity/zombie/husk_baby.png" : monsterTexturePath(ZombieForm.HUSK)
            );
            case ZOMBIFIED_PIGLIN -> new ZombieRenderPlan(
                    baby ? ZombieMonsterBody.ZOMBIFIED_PIGLIN_BABY : ZombieMonsterBody.ZOMBIFIED_PIGLIN,
                    "minecraft:zombified_piglin",
                    monsterTexturePath(ZombieForm.ZOMBIFIED_PIGLIN)
            );
            case GIANT -> new ZombieRenderPlan(
                    ZombieMonsterBody.GIANT,
                    "minecraft:giant",
                    monsterTexturePath(ZombieForm.GIANT)
            );
            case NORMAL -> new ZombieRenderPlan(
                    baby ? ZombieMonsterBody.ZOMBIE_BABY : ZombieMonsterBody.ZOMBIE,
                    "minecraft:zombie",
                    baby ? "minecraft:textures/entity/zombie/zombie_baby.png" : monsterTexturePath(ZombieForm.NORMAL)
            );
        };
    }

    public static boolean usesMonsterTexture(ZombiePlayerSkinMode mode) {
        return mode == ZombiePlayerSkinMode.MONSTER_TEXTURE;
    }

    public static boolean shouldUseZombieVisuals(boolean spectator, boolean creative, ZombieForm form) {
        return !spectator;
    }
}
