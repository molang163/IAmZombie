package dev.molang.iamzombieq.api.extension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Registry of addon extension hooks (design §5.b). Addons register their providers from their constructor /
 * mod-setup; the mod's handlers query the registered lists on the server thread, first-non-null-wins.
 *
 * <p><b>Neutral-when-empty (PLAN A2):</b> both lists are {@code new CopyOnWriteArrayList<>()} initialized EMPTY,
 * with no static initializer and no self-registration anywhere in the base mod's Phase-1 code. Only addons (and
 * tests) call the registration entry points. With no addon present every hook-query loop is empty and falls
 * through to the built-in behavior — so Phase-1 wiring is behavior-identical to before.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class IZombieExtensions {

    private static final CopyOnWriteArrayList<IFoodRuleProvider> FOOD = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<IAttackerHook> ATTACKER = new CopyOnWriteArrayList<>();

    private IZombieExtensions() {
    }

    /** Registers a food-rule provider. Call from an addon's setup; thread-safe. */
    public static void register(@NotNull IFoodRuleProvider provider) {
        FOOD.add(provider);
    }

    /** Registers an attacker hook. Call from an addon's setup; thread-safe. */
    public static void register(@NotNull IAttackerHook hook) {
        ATTACKER.add(hook);
    }

    /** The registered food-rule providers, in registration order. Internal: consumed by the food handler. */
    @ApiStatus.Internal
    @NotNull
    public static List<IFoodRuleProvider> foodRuleProviders() {
        return FOOD;
    }

    /**
     * The registered attacker hooks, in registration order. Internal: intended to be consumed by the targeting
     * handler. The hooks are {@link org.jetbrains.annotations.ApiStatus.Experimental @Experimental} and
     * enum-based ({@link AttackerDecision}); their wiring is DEFERRED to Phase-2, so no handler queries this list
     * yet.
     */
    @ApiStatus.Internal
    @NotNull
    public static List<IAttackerHook> attackerHooks() {
        return ATTACKER;
    }
}
