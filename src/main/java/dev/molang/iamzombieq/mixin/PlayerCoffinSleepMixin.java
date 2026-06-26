package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.gameplay.CoffinNapManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bug #1: a zombie player napping in a coffin during the day was woken every tick by the vanilla per-tick bed-rule
 * check in {@link Player#tick()} (it calls {@code stopSleepInBed(false, true)} whenever the dimension bed-rule forbids
 * sleeping right now, i.e. daytime). {@code CoffinNapManager} enters the nap via the low-level {@code startSleeping},
 * which bypasses the ENTRY day-check but NOT this per-tick re-check, so the nap was dropped on the next tick (black
 * flash, no sleep, no time skip). This redirect suppresses ONLY that one daytime auto-wake and ONLY for an active
 * coffin napper; the CoffinNapManager vote then runs normally and advances the clock to night. Every other player and
 * sleep path stays byte-for-byte vanilla.
 */
@Mixin(Player.class)
abstract class PlayerCoffinSleepMixin {
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"))
    private void iamzombieq$keepCoffinNapperAsleepDuringDay(Player player, boolean forcefulWakeUp, boolean updateLevelList) {
        if (player instanceof ServerPlayer serverPlayer && CoffinNapManager.isNapping(serverPlayer.getUUID())) {
            return; // active coffin nap: skip the vanilla daytime auto-wake
        }
        player.stopSleepInBed(forcefulWakeUp, updateLevelList);
    }
}
