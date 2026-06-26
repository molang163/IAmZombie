package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.food.FoodRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Observer event fired AFTER a zombie player's food has been handled (design §5.a; the design's
 * {@code ZombieEatedEvent}), carrying the eaten item and the applied {@link FoodRule}. Not cancellable; the eaten
 * stack is an immutable snapshot.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}.
 *
 * <p>NOTE (Phase-1): fired by the food handler from the item-eat path ({@code onItemUseFinished}) after a
 * successful zombie-food eat, carrying the real eaten {@link ItemStack}. (The cake block-eat path, which has no
 * clean {@code ItemStack}, does not fire it.)
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieAteEvent extends Event {

    private final ServerPlayer player;
    private final ItemStack eaten;
    private final FoodRule rule;

    public ZombieAteEvent(@NotNull ServerPlayer player, @NotNull ItemStack eaten, @NotNull FoodRule rule) {
        this.player = player;
        this.eaten = eaten.copy();
        this.rule = rule;
    }

    @NotNull
    public ServerPlayer player() {
        return player;
    }

    /** An immutable snapshot copy of the eaten stack. */
    @NotNull
    public ItemStack eaten() {
        return eaten.copy();
    }

    @NotNull
    public FoodRule rule() {
        return rule;
    }
}
