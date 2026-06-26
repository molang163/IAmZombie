package dev.molang.iamzombieq.api.extension;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Addon hook for overriding whether a mob should attack a zombie player (design §5.b). Register an implementation
 * via the {@link IZombieExtensions} registration entry point.
 *
 * <p>The intended call site iterates registered hooks and the FIRST non-{@link AttackerDecision#DEFAULT DEFAULT}
 * result wins; returning {@link AttackerDecision#DEFAULT} means "no opinion", deferring to the next hook and
 * ultimately the built-in targeting matrix. Queried on the server thread.
 *
 * <p>NOTE (Phase-1): this interface ships but is NOT yet wired into the targeting handler — the attacker hook
 * call site is DEFERRED to Phase-2 to avoid entangling the existing target-change logic (PLAN A3). Only the food
 * hook is wired in Phase-1.
 *
 * <p>Marked {@link org.jetbrains.annotations.ApiStatus.Experimental @Experimental}: the enum-based decision and the
 * eventual targeting integration may still evolve before they are wired. NOT part of the stable 1.x contract until
 * wired in Phase-2.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface IAttackerHook {

    /**
     * Whether {@code mob} should attack {@code zombiePlayer}. Return {@link AttackerDecision#FORCE_TARGET},
     * {@link AttackerDecision#ALLOW_IF_PROVOKED}, or {@link AttackerDecision#IGNORE} to force the outcome, or
     * {@link AttackerDecision#DEFAULT} to defer to the next hook / the built-in matrix.
     *
     * @param mob          the potential attacker
     * @param zombiePlayer the zombie player being evaluated as a target (may be a FakePlayer)
     * @param form         the zombie player's current form
     * @return the hook's decision; {@link AttackerDecision#DEFAULT} means no opinion (defer)
     */
    @NotNull
    AttackerDecision shouldAttack(@NotNull LivingEntity mob, @NotNull ServerPlayer zombiePlayer, @NotNull ZombieForm form);
}
