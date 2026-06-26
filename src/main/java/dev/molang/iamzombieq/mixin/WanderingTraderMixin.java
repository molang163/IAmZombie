package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.rules.DisguiseRules;
import dev.molang.iamzombieq.rules.VillagerFearRules;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bug #3 (wandering traders): a wandering trader avoids hostile ENTITIES (Zombie, Evoker, ...) via goal-based
 * {@code AvoidEntityGoal}, but a zombie PLAYER is not a Zombie entity so nothing fires. This mixin appends an
 * {@code AvoidEntityGoal} that flees an undisguised zombie player at {@code VillagerFearRules.FLEE_DISTANCE} blocks
 * (mirroring the vanilla anti-zombie avoid). Disguised / spectator players are excluded by the predicate.
 */
@Mixin(WanderingTrader.class)
abstract class WanderingTraderMixin extends Mob {
    // Mixin discards this synthetic constructor at apply time; it exists only so the mixin can extend Mob and thereby
    // reach the inherited {@code protected final goalSelector} (declared in Mob, not WanderingTrader) without a
    // cross-class @Shadow, which does not resolve inherited fields in this dev environment.
    private WanderingTraderMixin(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void iamzombieq$avoidUndisguisedZombiePlayer(CallbackInfo ci) {
        WanderingTrader self = (WanderingTrader) (Object) this;
        Predicate<LivingEntity> isUndisguisedZombiePlayer = entity -> entity instanceof ServerPlayer player
                && VillagerFearRules.shouldFleeFromZombiePlayer(
                        !player.isSpectator(),
                        DisguiseRules.isDisguisedAsHuman(player.getItemBySlot(EquipmentSlot.HEAD)));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
                self, Player.class, isUndisguisedZombiePlayer,
                (float) VillagerFearRules.FLEE_DISTANCE, 0.5, 0.5, living -> true));
    }
}
