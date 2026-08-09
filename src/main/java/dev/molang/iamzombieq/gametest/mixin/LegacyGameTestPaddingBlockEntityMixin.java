package dev.molang.iamzombieq.gametest.mixin;

import dev.molang.iamzombieq.gametest.LegacyGameTestPaddingBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Backports the padded structure origin and entity-clear bounds used by 26.1+ GameTests. */
@Mixin(TestInstanceBlockEntity.class)
abstract class LegacyGameTestPaddingBlockEntityMixin {
    @Inject(
            method = "getStructurePos()Lnet/minecraft/core/BlockPos;",
            at = @At("RETURN"),
            cancellable = true)
    private void iamzombieq$offsetLegacyGameTestOrigin(CallbackInfoReturnable<BlockPos> callback) {
        TestInstanceBlockEntity self = (TestInstanceBlockEntity) (Object) this;
        callback.setReturnValue(LegacyGameTestPaddingBridge.structurePos(self, callback.getReturnValue()));
    }

    @Redirect(
            method = "removeEntities()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;"
                            + "getStructureBounds()Lnet/minecraft/world/phys/AABB;"))
    private AABB iamzombieq$useLegacyGameTestEntityClearBounds(TestInstanceBlockEntity testBlock) {
        return LegacyGameTestPaddingBridge.testBounds(testBlock);
    }
}
