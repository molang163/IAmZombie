package dev.molang.iamzombieq.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Observer event fired AFTER a zombie player has infected/transformed another entity (design §5.a). Not
 * cancellable; the {@code attacker}/{@code victim} are live entity references to be read, not mutated.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}.
 *
 * <p>NOTE (Phase-1): fired by the infection handler AFTER each successful conversion, in both the villager and the
 * pig/piglin path.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieInfectedEvent extends Event {

    private final ServerPlayer attacker;
    private final LivingEntity victim;
    private final EntityType<?> resultType;

    public ZombieInfectedEvent(@NotNull ServerPlayer attacker, @NotNull LivingEntity victim,
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
