package dev.molang.iamzombieq.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired BEFORE a zombie player infects/transforms another entity (design §5.a), e.g. a villager
 * -&gt; zombie villager or a pig/piglin -&gt; zombified piglin conversion. Cancel it to veto the infection.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. The {@code attacker}
 * and {@code victim} are live entity references — treat them as read-only within the listener. {@code resultType}
 * is the entity type the victim is converting into.
 *
 * <p>NOTE (Phase-1): fired by the infection handler AFTER the existing infection gates (RNG chance +
 * {@code EventHooks.canLivingConvert}) but BEFORE the conversion, in both the villager and the pig/piglin path;
 * cancelling it aborts that infection.
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
