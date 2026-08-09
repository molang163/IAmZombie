package dev.molang.iamzombieq.gametest.mixin;

import dev.molang.iamzombieq.gametest.LegacyGameTestPaddingBridge;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Clears scheduled block ticks and block events across the same padded box used by 26.1+ GameTests. */
@Mixin(GameTestInfo.class)
abstract class LegacyGameTestPaddingInfoMixin {
    @Redirect(
            method = "placeStructure()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;"
                            + "getStructureBoundingBox()Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
    private BoundingBox iamzombieq$useLegacyGameTestScheduledStateClearBounds(
            TestInstanceBlockEntity testBlock) {
        return LegacyGameTestPaddingBridge.testBoundingBox(testBlock);
    }
}
