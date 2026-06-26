package dev.molang.iamzombieq.internal.core;

import dev.molang.iamzombieq.api.core.IZombiePlayer;
import dev.molang.iamzombieq.api.event.ZombieEvolvePreEvent;
import dev.molang.iamzombieq.api.event.ZombieEvolvedEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformPreEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformedEvent;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.platform.Services;
import dev.molang.iamzombieq.rules.DeathOutcome;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Internal, server-authoritative implementation of {@link IZombiePlayer} over a {@link ServerPlayer} (design
 * §4.2). Centralizes the {@code setData -> syncData -> fire} lifecycle so no caller can forget to sync.
 *
 * <p>The form-changing mutators ({@code transformToForm}, {@code evolveFromDeath}, {@code resetAfterOrdinaryDeath})
 * follow: snapshot the before-state, post the cancellable Pre event (return {@code false} if canceled), write the
 * attachment, sync it, post the observer Post event, and return {@code true}. The non-form mutators
 * ({@code setSize}, {@code claimReward}) are NOT form changes, so they skip the transform events entirely — they
 * just write + sync and return {@code true}. All attachment and event operations go through the platform
 * {@link Services} + {@link ZombieEventPublisher} so listener exceptions are isolated and the bus is named in one
 * place.
 *
 * <p><b>FakePlayer-safe by construction (PLAN A6):</b> typed on {@code ServerPlayer} (a FakePlayer IS one) and it
 * NEVER reads the player's network connection. The {@code syncData} step is a no-op for a connectionless player,
 * so a FakePlayer roundtrip is well-defined. (A runtime FakePlayer GameTest is deferred to a future GameTest
 * harness; this design future-enables it.)
 */
@ApiStatus.Internal
public final class ServerZombiePlayer implements IZombiePlayer {

    private final ServerPlayer player;

    private ServerZombiePlayer(ServerPlayer player) {
        this.player = player;
    }

    /** The server-authoritative facade for {@code player}. */
    @NotNull
    public static ServerZombiePlayer of(@NotNull ServerPlayer player) {
        return new ServerZombiePlayer(player);
    }

    private PlayerZombieData data() {
        return Services.ATTACHMENTS.get(player, IAmZombieAttachments.PLAYER_ZOMBIE.get());
    }

    /** Writes {@code next} and pushes the network sync (the sync is a no-op for a connectionless player). */
    private void writeAndSync(PlayerZombieData next) {
        Services.ATTACHMENTS.set(player, IAmZombieAttachments.PLAYER_ZOMBIE.get(), next);
        Services.ATTACHMENTS.sync(player, IAmZombieAttachments.PLAYER_ZOMBIE.get());
    }

    // ---- reads ----

    @Override
    @NotNull
    public ZombieForm form() {
        return data().state().form();
    }

    @Override
    @NotNull
    public ZombieSize size() {
        return data().state().size();
    }

    @Override
    @NotNull
    public ZombieState state() {
        return data().state();
    }

    @Override
    public boolean isZombie() {
        // In 1.x every player is innately a zombie, so this is true by design (the value does not vary); it is a
        // reserved hook for a future human/zombie distinction.
        return true;
    }

    @Override
    public boolean hasReceivedFirstReward(@NotNull ZombieForm form) {
        PlayerZombieData data = data();
        return switch (form) {
            case DROWNED -> data.receivedFirstDrownedReward();
            case HUSK -> data.receivedFirstHuskReward();
            case ZOMBIFIED_PIGLIN -> data.receivedFirstZombifiedPiglinReward();
            case NORMAL, GIANT -> false;
        };
    }

    // ---- mutators ----

    @Override
    public boolean transformToForm(@NotNull ZombieForm to) {
        PlayerZombieData before = data();
        ZombieForm from = before.state().form();
        if (ZombieEventPublisher.postCancelable(new ZombieTransformPreEvent(player, from, to))) {
            return false;
        }
        writeAndSync(before.withState(new ZombieState(to, before.state().size())));
        ZombieEventPublisher.post(new ZombieTransformedEvent(player, from, to));
        return true;
    }

    @Override
    public boolean evolveFromDeath(@NotNull ZombieState next, @NotNull DeathOutcome outcome) {
        PlayerZombieData before = data();
        ZombieState beforeState = before.state();
        if (ZombieEventPublisher.postCancelable(new ZombieEvolvePreEvent(player, beforeState, next, outcome))) {
            return false;
        }
        writeAndSync(before.withState(next));
        ZombieEventPublisher.post(new ZombieEvolvedEvent(player, beforeState, next, outcome));
        return true;
    }

    @Override
    public boolean resetAfterOrdinaryDeath() {
        PlayerZombieData before = data();
        ZombieForm from = before.state().form();
        if (ZombieEventPublisher.postCancelable(new ZombieTransformPreEvent(player, from, ZombieForm.NORMAL))) {
            return false;
        }
        // Preserve the existing ordinary-death reset semantics: form/size -> DEFAULT, reward flags preserved. This
        // IS a real form change (form -> NORMAL), so it fires the transform Pre/Post events.
        writeAndSync(before.resetStateForOrdinaryDeath());
        ZombieEventPublisher.post(new ZombieTransformedEvent(player, from, ZombieForm.NORMAL));
        return true;
    }

    @Override
    public boolean setSize(@NotNull ZombieSize size) {
        // A size change is NOT a form change, so it does NOT fire the transform events (which would carry
        // from==to). Just write the new size + sync; the write is unconditional.
        PlayerZombieData before = data();
        ZombieForm form = before.state().form();
        writeAndSync(before.withState(new ZombieState(form, size)));
        return true;
    }

    @Override
    public boolean claimReward(@NotNull ZombieForm form) {
        // Claiming a first-evolution reward is a bookkeeping flag, not a form change, so it does NOT fire the
        // transform events. Just write the flag + sync; the write is unconditional.
        writeAndSync(withRewardClaimed(data(), form));
        return true;
    }

    private static PlayerZombieData withRewardClaimed(PlayerZombieData data, ZombieForm form) {
        return switch (form) {
            case DROWNED -> data.withFirstDrownedRewardClaimed();
            case HUSK -> data.withFirstHuskRewardClaimed();
            case ZOMBIFIED_PIGLIN -> data.withFirstZombifiedPiglinRewardClaimed();
            case NORMAL, GIANT -> data;
        };
    }
}
