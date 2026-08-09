package dev.molang.iamzombieq.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.ApiStatus;

/** Internal access limited to the legacy GameTest compatibility mixins. */
@ApiStatus.Internal
public final class LegacyGameTestPaddingBridge {
    private LegacyGameTestPaddingBridge() {
    }

    public static BlockPos structurePos(TestInstanceBlockEntity testBlock, BlockPos unpaddedPos) {
        return LegacyGameTestPadding.structurePos(testBlock, unpaddedPos);
    }

    public static BoundingBox testBoundingBox(TestInstanceBlockEntity testBlock) {
        return LegacyGameTestPadding.testBoundingBox(testBlock);
    }

    public static AABB testBounds(TestInstanceBlockEntity testBlock) {
        return LegacyGameTestPadding.testBounds(testBlock);
    }

    public static void clearExactTestBox(TestInstanceBlockEntity testBlock, ServerLevel level) {
        LegacyGameTestPadding.clearExactTestBox(testBlock, level);
    }
}
