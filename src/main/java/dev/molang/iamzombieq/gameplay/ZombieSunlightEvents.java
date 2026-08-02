package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.util.ModIds;

import dev.molang.iamzombieq.rules.HeadProtection;
import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.ZombieDamageRules;
import dev.molang.iamzombieq.rules.ZombieSunlightRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Sunlight fire re-attribution for zombie players: relabels vanilla on-fire ticks to the custom {@code sunlight}
 * damage type within a per-player sun-fire window, and marks that window when a sun-burn ignites. Driven by the core
 * coordinators ({@code onIncomingDamage} -> {@link #replaceSunlightFireDamage}, per-tick sun-burn ->
 * {@link #igniteSunlightBurn}). Owns the {@code SUNLIGHT_FIRE_UNTIL} window map, self-cleaning it on logout / server
 * stop, and clearing it on clone/death via {@link #clearSunFireWindow} (invoked from the core clone/death handlers).
 */
public final class ZombieSunlightEvents {
    // Per-player game-time until which the player's fire is sun-sourced; on-fire ticks within this window are
    // re-attributed to the sunlight death type. Refreshed each sun-burn tick. Server-side only; cleared on stop.
    private static final Map<UUID, Long> SUNLIGHT_FIRE_UNTIL = new HashMap<>();
    private static final ResourceKey<DamageType> SUNLIGHT_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ModIds.id("sunlight")
    );

    private ZombieSunlightEvents() {
    }

    static boolean replaceSunlightFireDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ZombiePlayerEvents.shouldApplyZombieRules(player)
                || event.getAmount() <= 0.0F) {
            return false;
        }

        boolean sourceIsOnFire = event.getSource().is(DamageTypes.ON_FIRE);
        Long sunlightFireUntil = SUNLIGHT_FIRE_UNTIL.get(player.getUUID());
        boolean withinSunlightFireWindow = sunlightFireUntil != null && player.level().getGameTime() <= sunlightFireUntil;
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        HeadProtection headProtection = ZombiePlayerEvents.classifyHeadProtection(player.getItemBySlot(EquipmentSlot.HEAD));
        boolean formBurnsInSunlight = ZombieSunlightRules.shouldBurn(data.state().form(), true, headProtection);
        if (!ZombieDamageRules.shouldConvertOnFireDamageToSunlight(sourceIsOnFire, withinSunlightFireWindow, formBurnsInSunlight)) {
            return false;
        }

        // Re-attribute this vanilla on-fire tick to the sunlight death type: cancel it and re-deal the same amount
        // as iamzombieq:sunlight (which is in minecraft:no_knockback, so no knockback / directional hurt indicator).
        event.setCanceled(true);
        player.hurtServer(player.level(), player.damageSources().source(SUNLIGHT_DAMAGE), event.getAmount());
        return true;
    }

    static void igniteSunlightBurn(ServerPlayer player) {
        player.igniteForSeconds(ZombieBalanceRules.SUNLIGHT_BURN_DURATION_SECONDS);
        // Mark the resulting fire as sun-sourced for as long as it will burn, so its on-fire ticks are re-attributed
        // to sunlight. Refreshed every sun-burn tick; vanilla fire handles the actual burn timing.
        SUNLIGHT_FIRE_UNTIL.put(player.getUUID(), player.level().getGameTime() + player.getRemainingFireTicks());
    }

    /**
     * Clear the transient sun-fire window for a player on clone (death respawn / dimension change) or death: it is
     * keyed to the old entity's fire ticks, and a stale window carried onto the fresh entity would mis-attribute the
     * first ordinary fire after respawn (lava/campfire) to the sunlight death type (#8). Invoked from the core
     * clone/death handlers.
     */
    public static void clearSunFireWindow(UUID uuid) {
        SUNLIGHT_FIRE_UNTIL.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop the player's sun-fire window on disconnect so it can't accumulate in the map for the server's
        // lifetime, nor mis-attribute a fresh (non-sun) fire to sunlight if they reconnect while it's still open.
        SUNLIGHT_FIRE_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SUNLIGHT_FIRE_UNTIL.clear();
    }
}
