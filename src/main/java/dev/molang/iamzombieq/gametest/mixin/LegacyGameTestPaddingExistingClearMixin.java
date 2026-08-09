package dev.molang.iamzombieq.gametest.mixin;

import dev.molang.iamzombieq.gametest.LegacyGameTestPaddingBridge;

import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the corrected 1.21.10/1.21.11 StructureUtils clear path with the later padded bounding box. */
@Mixin(TestInstanceBlockEntity.class)
abstract class LegacyGameTestPaddingExistingClearMixin {
    @Redirect(
            method = "placeStructure(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;"
                            + "getStructureBoundingBox()Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
    private BoundingBox iamzombieq$useLegacyGameTestBlockClearBounds(TestInstanceBlockEntity testBlock) {
        return LegacyGameTestPaddingBridge.testBoundingBox(testBlock);
    }
}
