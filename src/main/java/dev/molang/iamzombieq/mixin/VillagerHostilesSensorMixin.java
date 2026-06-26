package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.rules.DisguiseRules;
import dev.molang.iamzombieq.rules.VillagerFearRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bug #3 (villagers): vanilla villagers flee Zombie/Husk/Drowned ENTITIES via {@link VillagerHostilesSensor}, which
 * sets {@code NEAREST_HOSTILE} and drives the panic / run-away / calm-down behaviors. A zombie PLAYER is not such an
 * entity, so villagers ignored it. This mixin makes the sensor treat an undisguised zombie player within
 * {@code VillagerFearRules.FLEE_DISTANCE} blocks as hostile, reusing the entire vanilla panic/flee/calm pipeline.
 * Cancelling at HEAD also avoids vanilla {@code isClose()} NPEing on a Player type absent from the hostile-distance
 * map. Disguised (mask) or spectator players fall through to vanilla -> no flee.
 */
@Mixin(VillagerHostilesSensor.class)
abstract class VillagerHostilesSensorMixin {
    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$treatUndisguisedZombiePlayerAsHostile(
            ServerLevel level, LivingEntity body, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof ServerPlayer player
                && VillagerFearRules.shouldFleeFromZombiePlayer(
                        !player.isSpectator(),
                        DisguiseRules.isDisguisedAsHuman(player.getItemBySlot(EquipmentSlot.HEAD)))
                && body.distanceToSqr(target) <= VillagerFearRules.FLEE_DISTANCE * VillagerFearRules.FLEE_DISTANCE) {
            cir.setReturnValue(true);
        }
    }
}
