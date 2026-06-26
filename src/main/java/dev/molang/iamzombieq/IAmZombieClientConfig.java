package dev.molang.iamzombieq;

import dev.molang.iamzombieq.rules.ZombiePlayerSkinMode;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class IAmZombieClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<ZombiePlayerSkinMode> PLAYER_SKIN_MODE = BUILDER
            .comment("How zombie players are skinned in third person. MONSTER_TEXTURE uses vanilla zombie/drowned/husk textures on the player model; PLAYER_SKIN keeps the player's own skin.")
            .defineEnum("playerSkinMode", ZombiePlayerSkinMode.MONSTER_TEXTURE);

    public static final ModConfigSpec.EnumValue<ZombiePlayerSkinMode> FIRST_PERSON_ARM_SKIN_MODE = BUILDER
            .comment("How first-person arms are skinned. MONSTER_TEXTURE uses the current zombie form texture; PLAYER_SKIN keeps vanilla player arms.")
            .defineEnum("firstPersonArmSkinMode", ZombiePlayerSkinMode.MONSTER_TEXTURE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IAmZombieClientConfig() {
    }
}
