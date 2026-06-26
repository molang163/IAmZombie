package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.food.FoodRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired BEFORE a zombie player's food is resolved/applied (design §5.a), carrying the eaten
 * item and the resolved {@link FoodRule}. Cancel it to veto the zombie-food handling.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. The eaten stack is
 * kept as an immutable snapshot ({@link ItemStack#copy()}), so listeners cannot mutate the live stack through it.
 *
 * <p>NOTE (Phase-1): fired by the food handler from the item-eat path ({@code onItemUseFinished}) BEFORE the
 * zombie-food effects apply; cancelling it skips the whole zombie-food handling for that eat. (The cake block-eat
 * path, which has no clean {@code ItemStack}, does not fire it.) Per-item food-rule extension is additionally
 * provided via {@code IFoodRuleProvider}.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieEatPreEvent extends Event implements ICancellableEvent {

    private final ServerPlayer player;
    private final ItemStack eaten;
    private final FoodRule rule;

    public ZombieEatPreEvent(@NotNull ServerPlayer player, @NotNull ItemStack eaten, @NotNull FoodRule rule) {
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
