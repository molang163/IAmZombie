package dev.molang.iamzombieq.api.extension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Registry of addon extension hooks (design §5.b). The two {@link #register(IFoodRuleProvider)} and
 * {@link #register(IAttackerHook)} overloads are the supported addon entry points; the list accessors are
 * library-internal consumption seams.
 *
 * <p><b>Lifecycle contract:</b> registration is setup-only and process/classloader scoped. Register each provider or
 * hook once during addon construction or a single setup callback. World, server, datapack, and config reloads do not
 * unload mods, clear this registry, or justify registering again. This registry does not support runtime unregister.
 * Duplicate registrations participate repeatedly wherever the corresponding list is consumed; do not register from
 * reload or tick paths. Passing null violates this contract; the API does not specify a particular failure point, so
 * callers must not depend on the current storage implementation's null behavior.
 *
 * <p><b>Ordering:</b> food providers are queried in actual registration completion order and the first non-null result
 * wins. Parallel addon setup does not guarantee cross-addon order, so addons that race registration must not depend on
 * which provider wins.
 *
 * <p><b>Neutral-when-empty (PLAN A2):</b> both lists are {@code new CopyOnWriteArrayList<>()} initialized EMPTY,
 * with no static initializer and no self-registration anywhere in the base mod's Phase-1 code. Only addons (and
 * tests) call the registration entry points. With no addon present every hook-query loop is empty and falls
 * through to the built-in behavior — so Phase-1 wiring is behavior-identical to before.
 *
 * <p>Part of the STABLE public API surface (semver 1.x), at each referenced type's declared stability. The class-level
 * STABLE contract does not include its {@code @Internal accessors} or the
 * {@link org.jetbrains.annotations.ApiStatus.Experimental Experimental attacker API}.
 */
public final class IZombieExtensions {

    private static final CopyOnWriteArrayList<IFoodRuleProvider> FOOD = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<IAttackerHook> ATTACKER = new CopyOnWriteArrayList<>();

    private IZombieExtensions() {
    }

    /**
     * Supported addon entry point for a food-rule provider. Call once from addon setup; thread-safe.
     *
     * @param provider the non-null provider to append to the process-scoped registry
     */
    public static void register(@NotNull IFoodRuleProvider provider) {
        FOOD.add(provider);
    }

    /**
     * Supported addon entry point for an
     * {@link org.jetbrains.annotations.ApiStatus.Experimental experimental} attacker hook. Call once from addon setup;
     * thread-safe.
     *
     * @param hook the non-null hook to append to the process-scoped registry
     */
    public static void register(@NotNull IAttackerHook hook) {
        ATTACKER.add(hook);
    }

    /**
     * Library-internal consumption seam for the food handler.
     *
     * <p>Addons must not call this method, modify the returned list, retain its reference, or depend on its
     * implementation type.
     *
     * @return registered food-rule providers in actual registration order
     */
    @ApiStatus.Internal
    @NotNull
    public static List<IFoodRuleProvider> foodRuleProviders() {
        return FOOD;
    }

    /**
     * Library-internal consumption seam intended for the targeting handler. The hooks are
     * {@link org.jetbrains.annotations.ApiStatus.Experimental @Experimental} and enum-based
     * ({@link AttackerDecision}); their wiring is DEFERRED to Phase-2, so no handler queries this list yet.
     *
     * <p>Addons must not call this method, modify the returned list, retain its reference, or depend on its
     * implementation type.
     *
     * @return registered attacker hooks in actual registration order
     */
    @ApiStatus.Internal
    @NotNull
    public static List<IAttackerHook> attackerHooks() {
        return ATTACKER;
    }
}
