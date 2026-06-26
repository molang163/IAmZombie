package dev.molang.iamzombieq.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Ground-mounted Herobrine head. A fixed-type {@link SkullBlock} (so it uses {@code simpleCodec} like
 * {@code WitherSkullBlock}); inherits ROTATION/POWERED, placement rotation segments, shape, and the shared
 * {@code SkullBlockEntity} from vanilla. Rendering is handled by the vanilla {@code SkullBlockRenderer} once
 * this block is added to {@code BlockEntityTypes.SKULL} and the {@code HEROBRINE} model+texture are registered.
 */
public class HerobrineHeadBlock extends SkullBlock {
    public static final MapCodec<HerobrineHeadBlock> CODEC = simpleCodec(HerobrineHeadBlock::new);

    public HerobrineHeadBlock(BlockBehaviour.Properties properties) {
        super(HerobrineHeadType.HEROBRINE, properties);
    }

    @Override
    public MapCodec<HerobrineHeadBlock> codec() {
        return CODEC;
    }
}
