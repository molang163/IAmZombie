package dev.molang.iamzombieq.api.registry;

import dev.molang.iamzombieq.rules.ZombieMonsterBody;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * EXPERIMENTAL Phase-2 placeholder for a codec-driven, registry-backed zombie form (design §5.c). This is a
 * <b>draft data shape only and is NOT usable in 1.0</b>: 1.0 keeps the hard-coded {@code ZombieForm} enum, and
 * registry-backed forms (plus the matching sync-by-name network migration, design §6.2 / A2) are deferred to a
 * 2.0 MAJOR. No registry, codec, or wiring is provided here yet.
 *
 * <p>The {@code id} is modeled as a plain string in this draft to avoid committing to a registry/{@code Identifier}
 * shape before Phase-2; that will be finalized when the custom registry lands.
 *
 * @param id            the form's namespaced id (draft; e.g. {@code "iamzombieq:drowned"})
 * @param body          the monster body this form renders/behaves as
 * @param innateArmor   innate armor points for the form
 * @param burnsInSunlight whether the form burns in sunlight
 * @param fireResistant whether the form resists fire
 */
@ApiStatus.Experimental
public record ZombieFormSpec(
        @NotNull String id,
        @NotNull ZombieMonsterBody body,
        int innateArmor,
        boolean burnsInSunlight,
        boolean fireResistant
) {
}
