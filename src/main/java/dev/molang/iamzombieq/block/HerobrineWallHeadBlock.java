package dev.molang.iamzombieq.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Wall-mounted Herobrine head, the {@link WallSkullBlock} counterpart of {@link HerobrineHeadBlock} (adds FACING
 * instead of ROTATION). Placed by the same {@code StandingAndWallBlockItem} as the floor variant. Fixed type, so
 * it uses {@code simpleCodec} like {@code WitherWallSkullBlock}.
 */
public class HerobrineWallHeadBlock extends WallSkullBlock {
    public static final MapCodec<HerobrineWallHeadBlock> CODEC = simpleCodec(HerobrineWallHeadBlock::new);

    public HerobrineWallHeadBlock(BlockBehaviour.Properties properties) {
        super(HerobrineHeadType.HEROBRINE, properties);
    }

    @Override
    public MapCodec<HerobrineWallHeadBlock> codec() {
        return CODEC;
    }
}
