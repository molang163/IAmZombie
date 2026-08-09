package dev.molang.iamzombieq.gametest.mixin;

import dev.molang.iamzombieq.gametest.LegacyGameTestPaddingBridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the exact later-version structure clear that 1.21.8 lacks without using its over-expanding helper. */
@Mixin(TestInstanceBlockEntity.class)
abstract class LegacyGameTestPadding1218ClearMixin {
    @Inject(
            method = "placeStructure(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;removeEntities()V"))
    private void iamzombieq$clearLegacyGameTestBlocks(
            ServerLevel level, StructureTemplate structure, CallbackInfo callback) {
        LegacyGameTestPaddingBridge.clearExactTestBox((TestInstanceBlockEntity) (Object) this, level);
    }
}
