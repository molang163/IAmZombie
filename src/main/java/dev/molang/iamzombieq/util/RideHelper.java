package dev.molang.iamzombieq.util;

import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared ridden-mount logic, adapted from the "controlling-client authority" riding technique
 * (neoforge-26.2-rideable-mounts-impl.md, PI-4).
 *
 * <p>This mod cannot subclass the vanilla mounts (Spider/Chicken/Zombie/Strider/horses/nautilus), so the
 * doc's per-entity overrides are applied through mixins on {@code Mob#getControllingPassenger} and
 * {@code LivingEntity#getRiddenInput}/{@code getRiddenSpeed}/{@code tickRidden}. The doc's
 * {@code SaddleAccess} / {@code mobInteract} bits are intentionally dropped: this mod gates riding via
 * {@code EntityMountEvent} (ZombieMountRules.canMount) and ownership attachments, not via {@code Saddleable}.
 *
 * <p>The doc's no-despawn-while-ridden and don't-attack-rider helpers are NOT reproduced here: because the
 * mounts are vanilla classes the mod cannot override {@code removeWhenFarAway}/{@code canAttack}, and those
 * concerns are already handled at the event layer (tamed spiders get {@code setPersistenceRequired()};
 * {@code ZombieMountEvents#onLivingChangeTarget} blocks a mount from targeting its own rider). Adding mixins
 * for them would expand scope and is unnecessary.
 *
 * <p>DEDICATED-SERVER CORRECTNESS: nothing here calls {@code setDeltaMovement}/{@code move} to steer. When
 * a mount reports a Player as its controlling passenger (via the MobMixin), vanilla's
 * {@code LivingEntity.travelRidden} pulls {@link #riddenInput} / the ridden speed and runs the normal
 * {@code travel} path on the controlling client, which reports the result to the server via the vanilla
 * {@code ServerboundMoveVehiclePacket}. Server-only motion hacks are the bug this technique avoids.
 */
public final class RideHelper {

    private RideHelper() {
    }

    /**
     * Modern ridden input (WASD), mirroring {@code AbstractHorse#getRiddenInput}: raw strafe/forward in
     * INPUT space (the mount's own {@code travel} applies the yaw rotation). Do NOT pre-rotate by yaw here
     * (sin/cos) or movement double-rotates.
     *
     * <p>Verified against the decompiled 26.2 jar: {@code AbstractHorse#getRiddenInput} uses
     * {@code xxa * 0.5}, {@code zza}, and {@code forward *= 0.25} when {@code forward <= 0}.
     */
    public static Vec3 riddenInput(Player controller) {
        float strafe = controller.xxa * 0.5F;
        float forward = controller.zza;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        return new Vec3(strafe, 0.0, forward);
    }

    /**
     * Per-tick rotation: the mount faces where the rider looks, mirroring {@code AbstractHorse#tickRidden}
     * (setRot + yRotO/yBodyRot/yHeadRot follow yaw; pitch is the rider's pitch halved). Verified against the
     * decompiled 26.2 jar (AbstractHorse line ~721-726).
     */
    public static void tickRiddenRotation(Mob mob, Player controller) {
        // setYRot/setXRot (both public) fully set the rotation; Entity#setRot is protected and would only re-apply
        // the same clamped values, so it is intentionally omitted (the rider's pitch*0.5 is always within range).
        mob.setYRot(controller.getYRot());
        mob.yRotO = mob.getYRot();
        mob.setXRot(controller.getXRot() * 0.5F);
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.yBodyRot;
    }

    /**
     * Per-tick jump: mirror the rider's jump key onto the mount so it performs its NATIVE vanilla jump (no custom
     * height). We write the mount's {@code jumping} flag to the rider's live key state; the mount's own
     * {@code LivingEntity#aiStep} "jump" block (decompiled 26.2 jar LivingEntity.java line ~3070-3084) consumes the
     * flag and calls {@code jumpFromGround()} (line ~2369), which uses the mount's own {@code getJumpPower()} -- i.e.
     * the chicken's / zombie's native jump strength. We MUST set the flag every tick (true while held, FALSE when
     * released): a mob has no input-driven reset of {@code jumping} (only a LocalPlayer rewrites it from the key each
     * tick; a mob clears it only when immobile, LivingEntity line ~3059), so only ever setting it true left it stuck
     * and the mount re-jumped every {@code noJumpDelay} (~10 ticks) forever after the rider let go. This runs only
     * while the mount is a controlled mod-mount (the LivingEntityMixin gate), so an unridden mount's own AI jumping
     * is never touched.
     *
     * <p>The controller's live jump-key state is read via {@code controller.isJumping()} (LivingEntity line
     * ~3160): {@code LocalPlayer#applyInput} (line ~700) sets {@code this.jumping = input.keyPresses.jump()}
     * even while a passenger, so on the controlling client this reflects the held jump key.
     *
     * <p>DEDICATED-SERVER CORRECTNESS: gated on {@code isLocalInstanceAuthoritative()} (the same authority
     * gate AbstractHorse#tickRidden uses, line ~726) so the held-jump intent is applied on the instance that
     * owns the mount's motion -- the controlling client for a client-authoritative mount -- and reported to
     * the server via the vanilla vehicle-move packet, mirroring the rest of this class.
     */
    public static void tickRiddenJump(Mob mount, Player controller) {
        if (mount.isLocalInstanceAuthoritative()) {
            mount.setJumping(controller.isJumping());
        }
    }

    /**
     * Gate for the baby-player-driven mounts (chicken, big zombie). A non-spectator player whose synced
     * zombie state is BABY may drive. Mirrors ZombieMountEvents#isBabyZombiePlayer; runs identically on the
     * controlling client and the server because PLAYER_ZOMBIE is a synced attachment.
     */
    public static boolean isBabyZombieRider(Player rider) {
        if (rider.isSpectator()) {
            return false;
        }
        return rider.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().size() == ZombieSize.BABY;
    }

    /**
     * A full-size (non-baby, non-villager) zombie that the mod treats as a BIG_ZOMBIE mount. Mirrors
     * ZombieMountEvents#isRideableBigZombie so the riding mixin gates on the same classification.
     */
    public static boolean isRideableBigZombie(Zombie zombie) {
        return !(zombie instanceof ZombieVillager)
                && ZombieMountRules.mountKindForZombieEntityId(
                        BuiltInRegistries.ENTITY_TYPE.getKey(zombie.getType()).toString(), zombie.isBaby())
                == MountKind.BIG_ZOMBIE;
    }

    /**
     * The driving rider for a chicken/big-zombie mount: the first passenger if it is a baby-zombie player,
     * else null. Used by MobMixin#getControllingPassenger so the controlling client emits vehicle-move
     * packets, and by LivingEntityMixin to scope getRiddenInput/getRiddenSpeed to that rider.
     */
    public static Player babyPlayerRiderFor(Mob mount) {
        if (mount.getFirstPassenger() instanceof Player rider && isBabyZombieRider(rider)) {
            return rider;
        }
        return null;
    }
}
