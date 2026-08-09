package dev.molang.iamzombieq.gametest.mixin;

import dev.molang.iamzombieq.gametest.LegacyGameTestPaddingBridge;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Restores the padded AABB that drives the mandatory GameTestServer grid layout. */
@Mixin(StructureGridSpawner.class)
abstract class LegacyGameTestPaddingGridSpawnerMixin {
    @Redirect(
            method = "spawnStructure(Lnet/minecraft/gametest/framework/GameTestInfo;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;"
                            + "getStructureBounds()Lnet/minecraft/world/phys/AABB;"))
    private AABB iamzombieq$useLegacyGameTestGridBounds(
            TestInstanceBlockEntity testBlock, GameTestInfo testInfo) {
        return LegacyGameTestPaddingBridge.testBounds(testBlock);
    }
}
