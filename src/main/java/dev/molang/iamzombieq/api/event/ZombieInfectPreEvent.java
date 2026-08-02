package dev.molang.iamzombieq.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired before a zombie player infects or transforms another entity, for example a villager
 * -&gt; zombie villager, a pig/piglin -&gt; zombified piglin, a horse -&gt; zombie horse, or a nautilus -&gt; zombie
 * nautilus conversion. Cancel it to veto the infection.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. The {@code attacker}
 * and {@code victim} are live entity references — treat them as read-only within the listener. {@code resultType}
 * is the entity type the victim is converting into.
 *
 * <p>Fired by the infection handler after the existing infection gates (RNG chance and
 * {@code EventHooks.canLivingConvert}) but BEFORE the conversion, in all four infection paths (villager,
 * pig/piglin, horse, nautilus); cancelling it aborts that infection.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieInfectPreEvent extends Event implements ICancellableEvent {

    private final ServerPlayer attacker;
    private final LivingEntity victim;
    private final EntityType<?> resultType;

    public ZombieInfectPreEvent(@NotNull ServerPlayer attacker, @NotNull LivingEntity victim,
            @NotNull EntityType<?> resultType) {
        this.attacker = attacker;
        this.victim = victim;
        this.resultType = resultType;
    }

    @NotNull
    public ServerPlayer attacker() {
        return attacker;
    }

    @NotNull
    public LivingEntity victim() {
        return victim;
    }

    @NotNull
    public EntityType<?> resultType() {
        return resultType;
    }
}
