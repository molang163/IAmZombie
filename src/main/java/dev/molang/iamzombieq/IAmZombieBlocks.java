package dev.molang.iamzombieq;

import dev.molang.iamzombieq.block.CoffinBlock;
import dev.molang.iamzombieq.block.HerobrineHeadBlock;
import dev.molang.iamzombieq.block.HerobrineWallHeadBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IAmZombieBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IAmZombieMod.MOD_ID);

    // A single wood-agnostic coffin block; any wood crafts it (see recipes) and it always renders the
    // bespoke coffin textures (the CoffinBlock model derives from the shared template_coffin*).
    public static final DeferredBlock<CoffinBlock> COFFIN = BLOCKS.registerBlock(
            "coffin",
            CoffinBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()
    );

    // The Herobrine head as a proper vanilla-style skull: a placeable floor + wall block pair sharing the
    // vanilla SkullBlockEntity/renderer. Properties copied from the skeleton skull so it behaves exactly like a
    // vanilla head (strength, sound, push reaction, no occlusion).
    public static final DeferredBlock<HerobrineHeadBlock> HEROBRINE_HEAD = BLOCKS.registerBlock(
            "herobrine_head",
            HerobrineHeadBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.SKELETON_SKULL)
    );

    public static final DeferredBlock<HerobrineWallHeadBlock> HEROBRINE_WALL_HEAD = BLOCKS.registerBlock(
            "herobrine_wall_head",
            HerobrineWallHeadBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.SKELETON_WALL_SKULL)
    );

    private IAmZombieBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        modEventBus.addListener(IAmZombieBlocks::addSkullBlocks);
    }

    // SkullBlockEntity hard-codes BlockEntityTypes.SKULL in its constructor, so our head blocks must join that
    // existing block-entity type (both are AbstractSkullBlock subclasses, satisfying the common-superclass rule).
    // This makes their block entities save/load correctly and renders them via the already-registered vanilla
    // SkullBlockRenderer — no custom block entity, block-entity type, or BER registration needed.
    private static void addSkullBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.SKULL, HEROBRINE_HEAD.get(), HEROBRINE_WALL_HEAD.get());
    }
}
