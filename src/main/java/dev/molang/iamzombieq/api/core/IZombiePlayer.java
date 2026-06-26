package dev.molang.iamzombieq.api.core;

import dev.molang.iamzombieq.rules.DeathOutcome;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import org.jetbrains.annotations.NotNull;

/**
 * Public, stable facade over a single player's zombie state (design §4.2). Obtain an instance via
 * {@link IZombiePlayerAPI#get}. The reads are safe to call on any thread that holds a coherent view of the
 * player's attachment (typically the server thread); the mutators are <b>server-thread-only</b> and
 * <b>server-authoritative</b>.
 *
 * <p><b>FakePlayer-safe:</b> every mutator is typed on a {@code ServerPlayer} (a FakePlayer is a
 * {@code ServerPlayer}) and never reads the player's network connection, so driving the facade with a
 * connectionless FakePlayer is well-defined — the network {@code syncData} step becomes a no-op. This design
 * future-enables FakePlayer-driven GameTests (a runtime FakePlayer roundtrip is deferred to a GameTest harness,
 * per PLAN A6).
 *
 * <p>The <i>form-changing</i> mutators ({@link #transformToForm} and {@link #resetAfterOrdinaryDeath}) follow a
 * Pre/Post lifecycle (design §4.2 / §5.a): post a cancellable {@code ZombieTransformPreEvent}; if a listener
 * cancels it, make no change and return {@code false}; otherwise write the attachment, sync it, post the observer
 * {@code ZombieTransformedEvent}, and return {@code true}. The non-form mutators ({@link #setSize},
 * {@link #claimReward}) are NOT form changes, so they do NOT fire the transform events — they simply write + sync
 * and always return {@code true}.
 *
 * <p>NOTE (Phase-1): the existing gameplay handlers do NOT yet route through this facade — migrating them is
 * deferred to Phase-2 (PLAN D1). This facade is fully functional today for addons, tests, and FakePlayer flows.
 *
 * <p>Part of the STABLE public API surface (semver 1.x): backward-compatible additions only within 1.x. (The
 * JetBrains {@code @ApiStatus} family has no {@code Stable} marker; "stable" here means simply not
 * {@code @Internal}/{@code @Experimental}.) The exposed model types this facade returns/accepts —
 * {@link dev.molang.iamzombieq.rules.core.ZombieForm}, {@link dev.molang.iamzombieq.rules.core.ZombieSize},
 * {@link dev.molang.iamzombieq.rules.core.ZombieState}, {@link dev.molang.iamzombieq.rules.DeathOutcome}, and
 * {@link dev.molang.iamzombieq.rules.food.FoodRule} — are likewise part of the frozen 1.x public surface (they
 * live outside {@code api/*} for packaging reasons but are exposed through it; 2.0 may revisit forms via a
 * registry).
 */
public interface IZombiePlayer {

    // ---- reads ----

    /** The player's current zombie form (e.g. NORMAL / DROWNED / GIANT). */
    @NotNull
    ZombieForm form();

    /** The player's current body size (ADULT / BABY). */
    @NotNull
    ZombieSize size();

    /** The player's full {@code (form, size)} state. */
    @NotNull
    ZombieState state();

    /**
     * Whether the player is treated as a zombie. In 1.x <b>every</b> player is innately a zombie, so this returns
     * {@code true} by design — the value does not vary. It is a reserved hook for a future human/zombie
     * distinction; until then it is a stable {@code true} and does not change the gameplay gates the handlers
     * already apply.
     */
    boolean isZombie();

    /** Whether the one-time first-evolution reward for {@code form} has already been claimed. */
    boolean hasReceivedFirstReward(@NotNull ZombieForm form);

    // ---- server-authoritative mutators (server thread only) ----

    /**
     * Actively changes the player's form (e.g. the giant-kill transform). Posts {@code ZombieTransformPreEvent}
     * (cancellable), then writes + syncs, then posts {@code ZombieTransformedEvent}.
     *
     * @return {@code true} if the change was applied; {@code false} if a listener canceled it.
     */
    boolean transformToForm(@NotNull ZombieForm to);

    /**
     * Applies a death-driven evolution ("向死而生"). Posts {@code ZombieEvolvePreEvent} (cancellable) carrying the
     * before/after state and the {@link DeathOutcome}, then writes + syncs, then posts {@code ZombieEvolvedEvent}.
     *
     * @return {@code true} if the evolution was applied; {@code false} if a listener canceled it.
     */
    boolean evolveFromDeath(@NotNull ZombieState next, @NotNull DeathOutcome outcome);

    /**
     * Resets the player to the default (normal-zombie) state on an ordinary death — the player stays a zombie; the
     * form/size revert to {@code NORMAL}/{@code ADULT} while the one-time first-evolution reward flags are
     * preserved (exactly the existing {@code resetStateForOrdinaryDeath()} semantics). This IS a real form change
     * (form -&gt; {@code NORMAL}), so it posts the transform Pre/Post events around the write and is cancellable.
     *
     * @return {@code true} if applied; {@code false} if a listener canceled it.
     */
    boolean resetAfterOrdinaryDeath();

    /**
     * Sets the player's body size (e.g. super-rotten-flesh baby -&gt; adult). A size change is NOT a form change,
     * so it does NOT post the transform events (and is therefore not cancellable via them): it simply writes the
     * new size and syncs.
     *
     * @return {@code true} (always; the size write is unconditional).
     */
    boolean setSize(@NotNull ZombieSize size);

    /**
     * Records that the one-time first-evolution reward for {@code form} has been claimed. This is a bookkeeping
     * flag, not a form change, so it does NOT post the transform events: it simply writes the flag and syncs.
     *
     * @return {@code true} (always; the flag write is unconditional).
     */
    boolean claimReward(@NotNull ZombieForm form);
}
