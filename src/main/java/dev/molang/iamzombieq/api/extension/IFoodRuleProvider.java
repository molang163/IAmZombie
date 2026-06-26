package dev.molang.iamzombieq.api.extension;

import dev.molang.iamzombieq.rules.food.FoodRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Addon hook for supplying a custom {@link FoodRule} for an item a zombie player eats (design §5.b). Register an
 * implementation via the {@link IZombieExtensions} registration entry point.
 *
 * <p>The food handler consults registered providers in order and the FIRST non-null result wins; returning
 * {@code null} means "I don't handle this item", and the handler falls back to the next provider and ultimately
 * to the built-in {@code ZombieFoodRules.ruleForStack(...)}. Queried on the server thread.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
@FunctionalInterface
public interface IFoodRuleProvider {

    /**
     * The {@link FoodRule} this provider assigns to {@code stack}, or {@code null} to defer to the next provider /
     * the built-in rules.
     *
     * @param eater  the zombie player eating (a {@code ServerPlayer}; may be a FakePlayer)
     * @param stack  the eaten stack
     * @param itemId the item's registry id (e.g. {@code "minecraft:rotten_flesh"})
     */
    @Nullable
    FoodRule ruleForStack(@NotNull ServerPlayer eater, @NotNull ItemStack stack, @NotNull String itemId);
}
