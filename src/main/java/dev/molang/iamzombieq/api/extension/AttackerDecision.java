package dev.molang.iamzombieq.api.extension;

import org.jetbrains.annotations.ApiStatus;

/**
 * An addon {@link IAttackerHook}'s verdict on whether a mob should target a zombie player (design §5.b). Replaces
 * the old {@code @Nullable Boolean} return so the "no opinion" case is explicit rather than encoded as {@code null}.
 *
 * <p><b>Wiring is DEFERRED to Phase-2:</b> no handler consults this yet; the enum + hook ship so addons can compile
 * against the stable shape. Marked {@link org.jetbrains.annotations.ApiStatus.Experimental @Experimental} alongside
 * {@link IAttackerHook} because the targeting integration may still evolve. NOT part of the stable 1.x contract until
 * wired in Phase-2.
 */
@ApiStatus.Experimental
public enum AttackerDecision {

    /** Force the mob to actively target the zombie player. */
    FORCE_TARGET,

    /** Target the zombie player only if provoked (retaliating against an attack), never unprovoked. */
    ALLOW_IF_PROVOKED,

    /** Never let the mob target the zombie player. */
    IGNORE,

    /** No opinion: defer to the next registered hook and ultimately the built-in targeting matrix. */
    DEFAULT
}
