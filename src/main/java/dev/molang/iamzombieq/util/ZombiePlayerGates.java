package dev.molang.iamzombieq.util;

import net.minecraft.world.entity.player.Player;

/**
 * Single definition of the mod's "is this player subject to the zombie-player rules?" gate.
 *
 * <p>Historically every gameplay class carried its own {@code private static boolean isZombiePlayer(Player p)}
 * (or an inline {@code !p.isSpectator()}), all agreeing that a creative player is still a zombie and
 * only a spectator is excluded. This util is that one shared truth so the polarity lives in exactly one place.
 *
 * <p>Gameplay admission checks delegate here, either directly or through thin forwarders. NOTE the polarity: this
 * is an <i>inclusion</i> predicate (true = subject to zombie rules), so an exclusion context uses
 * {@code !isZombiePlayer(p)}. Direct spectator checks that serve other semantics — such as general-entity filters,
 * rendering-rule parameters, or creative-plus-spectator exclusions — are not zombie-player admission definitions.
 *
 * <p>This is a plain {@code util/} class: it must never import any client-only type
 * ({@code net.minecraft.client.*} / {@code net.neoforged.neoforge.client.*}), so the dedicated server can load it.
 */
public final class ZombiePlayerGates {

    private ZombiePlayerGates() {
    }

    /**
     * True iff {@code player} is subject to the zombie-player rules: a non-spectator, including creative players.
     * Behaviour-identical to the previous {@code !player.isSpectator()} inclusion gates.
     */
    public static boolean isZombiePlayer(Player player) {
        return !player.isSpectator();
    }

    /**
     * The server-side variant used where the zombie-player gate is combined with a {@code !isClientSide()} guard:
     * {@code !player.level().isClientSide() && !player.isSpectator()}. Behaviour-identical to that inline form.
     */
    public static boolean isServerZombiePlayer(Player player) {
        return !player.level().isClientSide() && isZombiePlayer(player);
    }
}
