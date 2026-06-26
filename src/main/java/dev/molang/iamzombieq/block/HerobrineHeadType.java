package dev.molang.iamzombieq.block;

import net.minecraft.world.level.block.SkullBlock;

/**
 * Custom {@link SkullBlock.Type} for the Herobrine head, mirroring how vanilla heads are typed (skeleton,
 * zombie, ...). Unlike the vanilla {@link SkullBlock.Types} enum entries it self-registers into the shared
 * {@link SkullBlock.Type#TYPES} map so the {@code kind} codec (block NBT + the {@code minecraft:head} item model)
 * can resolve "herobrine", and so the client skull renderer/model lookup accepts it. The skin is fixed (no
 * player profile), so there is nothing else to carry here.
 */
public final class HerobrineHeadType implements SkullBlock.Type {
    public static final HerobrineHeadType HEROBRINE = register();

    private HerobrineHeadType() {
    }

    private static HerobrineHeadType register() {
        HerobrineHeadType type = new HerobrineHeadType();
        SkullBlock.Type.TYPES.put("herobrine", type);
        return type;
    }

    @Override
    public String getSerializedName() {
        return "herobrine";
    }
}
