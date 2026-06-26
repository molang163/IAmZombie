package dev.molang.iamzombieq.util;

import java.util.List;
import java.util.Optional;

import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Tier-3 capability registry (plan §2 Tier-3 / RPA2): a single place that classifies the mod-DRIVEN mounts
 * (the ones steered through the controlling-passenger riding flow -- spider, chicken, big-zombie) and answers
 * the questions that were previously duplicated as scattered {@code instanceof} chains across
 * {@code MobMixin#getControllingPassenger}, {@code MobMixin#removeWhenFarAway},
 * {@code LivingEntityMixin#getRiddenInput/getRiddenSpeed/tickRidden}, and {@code RideHelper}:
 *
 * <ul>
 *   <li>given a mob, is it one of these mounts and, if so, which {@link MountKind}?</li>
 *   <li>given a mob + its current rider, is that rider the VALID controller of this mount?</li>
 *   <li>what ridden movement-speed should it use?</li>
 * </ul>
 *
 * <p>This is purely a refactor: each capability reproduces the exact predicate the old per-mount blocks used
 * (spider = owner-tamed by the rider; chicken/big-zombie = a baby-zombie-player rider; big-zombie additionally
 * gated by {@link RideHelper#isRideableBigZombie}). All public APIs (RideHelper, ZombieMountRules,
 * SpiderMountData) are preserved and now delegate here where natural; nothing about MountKind, serialization,
 * or translation keys changes.
 *
 * <p>Speed note: the spider keeps its config override, so {@link #riddenSpeed} takes the resolved spider speed
 * as an argument (the config is not read here to keep this class free of config/IO concerns and unit-testable).
 */
public enum MountCapability {
    SPIDER(MountKind.SPIDER) {
        @Override
        boolean matches(Mob mob) {
            return mob instanceof Spider;
        }

        @Override
        boolean isControlledBy(Mob mob, Player rider) {
            return mob instanceof Spider spider
                    && spider.getData(IAmZombieAttachments.SPIDER_MOUNT).isOwnedBy(rider.getUUID());
        }
    },
    CHICKEN(MountKind.CHICKEN) {
        @Override
        boolean matches(Mob mob) {
            return mob instanceof Chicken;
        }

        @Override
        boolean isControlledBy(Mob mob, Player rider) {
            return mob instanceof Chicken && RideHelper.isBabyZombieRider(rider);
        }
    },
    BIG_ZOMBIE(MountKind.BIG_ZOMBIE) {
        @Override
        boolean matches(Mob mob) {
            return mob instanceof Zombie zombie && RideHelper.isRideableBigZombie(zombie);
        }

        @Override
        boolean isControlledBy(Mob mob, Player rider) {
            return matches(mob) && RideHelper.isBabyZombieRider(rider);
        }
    };

    private static final List<MountCapability> VALUES = List.of(values());

    private final MountKind mountKind;

    MountCapability(MountKind mountKind) {
        this.mountKind = mountKind;
    }

    /** The {@link MountKind} this capability represents. */
    public MountKind mountKind() {
        return mountKind;
    }

    /** Whether {@code mob} is (structurally) this kind of mod-driven mount. */
    abstract boolean matches(Mob mob);

    /** Whether {@code rider} is the valid controller of {@code mob} for this capability. */
    abstract boolean isControlledBy(Mob mob, Player rider);

    /**
     * The capability for a mob that is currently being validly driven by its first passenger, if any. This is
     * the single predicate behind the controlling-passenger hook, the despawn backstop, and the ridden-input
     * scoping: the mob must be one of the mod-driven kinds AND its first passenger must be its valid controller.
     */
    public static Optional<MountCapability> activeFor(Mob mob) {
        if (!(mob.getFirstPassenger() instanceof Player rider)) {
            return Optional.empty();
        }
        for (MountCapability capability : VALUES) {
            if (capability.isControlledBy(mob, rider)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    /** The valid controlling Player rider of {@code mob}, or {@code null} if it is not actively driven. */
    public static Player activeRider(Mob mob) {
        return activeFor(mob).isPresent() && mob.getFirstPassenger() instanceof Player rider ? rider : null;
    }

    /** Whether {@code rider} is the valid controller of {@code mob} for ANY mod-driven capability. */
    public static boolean isControllerOf(Mob mob, Player rider) {
        return controlledCapability(mob, rider).isPresent();
    }

    /**
     * The mod-driven capability for which {@code rider} is the valid controller of {@code mob}, if any. Unlike
     * {@link #activeFor(Mob)} this checks an explicitly supplied controller (used by the ridden-input/speed
     * mixin, where the controller is the method argument rather than necessarily the first passenger).
     */
    public static Optional<MountCapability> controlledCapability(Mob mob, Player rider) {
        for (MountCapability capability : VALUES) {
            if (capability.isControlledBy(mob, rider)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    /**
     * Ridden movement-speed for this capability. The spider value is passed in (resolved from config by the
     * caller via {@link ZombieMountRules#spiderRiddenSpeed(float)}); chicken/big-zombie use the rules default.
     */
    public float riddenSpeed(float resolvedSpiderSpeed) {
        return this == SPIDER ? resolvedSpiderSpeed : ZombieMountRules.riddenSpeedFor(mountKind);
    }
}
