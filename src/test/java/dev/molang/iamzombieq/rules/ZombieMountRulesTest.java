package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.rules.core.ZombieSize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieMountRulesTest {
    @Test
    void zombiePlayersCannotRideNormalHorsesButCanRideUndeadHorses() {
        assertFalse(ZombieMountRules.canMount(true, MountKind.NORMAL_HORSE, false));
        assertTrue(ZombieMountRules.canMount(true, MountKind.ZOMBIE_HORSE, false));
        assertTrue(ZombieMountRules.canMount(true, MountKind.SKELETON_HORSE, false));
    }

    @Test
    void spiderMountRequiresZombiePlayerAndTamedSpider() {
        assertFalse(ZombieMountRules.canMount(true, MountKind.SPIDER, false));
        assertTrue(ZombieMountRules.canMount(true, MountKind.SPIDER, true));
        assertFalse(ZombieMountRules.canMount(false, MountKind.SPIDER, true));
    }

    @Test
    void otherMountsUseVanillaRules() {
        assertTrue(ZombieMountRules.canMount(true, MountKind.OTHER, false));
        assertTrue(ZombieMountRules.canMount(false, MountKind.NORMAL_HORSE, false));
    }

    @Test
    void babyZombieStateCanRideBigZombiesAndChickensButAdultsCannot() {
        assertTrue(ZombieMountRules.canMount(true, ZombieSize.BABY, MountKind.BIG_ZOMBIE, false));
        assertTrue(ZombieMountRules.canMount(true, ZombieSize.BABY, MountKind.CHICKEN, false));
        assertFalse(ZombieMountRules.canMount(true, ZombieSize.ADULT, MountKind.BIG_ZOMBIE, false));
        assertFalse(ZombieMountRules.canMount(true, ZombieSize.ADULT, MountKind.CHICKEN, false));
        assertFalse(ZombieMountRules.canMount(false, ZombieSize.BABY, MountKind.BIG_ZOMBIE, false));
    }

    @Test
    void zombieNautilusIsZombiePlayerMountForEitherSize() {
        assertTrue(ZombieMountRules.canMount(true, ZombieSize.ADULT, MountKind.ZOMBIE_NAUTILUS, false));
        assertTrue(ZombieMountRules.canMount(true, ZombieSize.BABY, MountKind.ZOMBIE_NAUTILUS, false));
        assertFalse(ZombieMountRules.canMount(false, ZombieSize.ADULT, MountKind.ZOMBIE_NAUTILUS, false));
    }

    @Test
    void bigZombieMountAutoAttacksNearbyTargets() {
        assertTrue(ZombieMountRules.bigZombieShouldAutoAttack(2.5));
        assertFalse(ZombieMountRules.bigZombieShouldAutoAttack(8.0));
    }

    @Test
    void bigZombieMountClassificationExcludesSmallOrSpecialZombieNamedMobs() {
        assertEquals(MountKind.BIG_ZOMBIE, ZombieMountRules.mountKindForZombieEntityId("minecraft:zombie", false));
        assertEquals(MountKind.BIG_ZOMBIE, ZombieMountRules.mountKindForZombieEntityId("minecraft:husk", false));
        assertEquals(MountKind.BIG_ZOMBIE, ZombieMountRules.mountKindForZombieEntityId("minecraft:drowned", false));
        assertEquals(MountKind.OTHER, ZombieMountRules.mountKindForZombieEntityId("minecraft:zombie", true));
        assertEquals(MountKind.OTHER, ZombieMountRules.mountKindForZombieEntityId("minecraft:zombie_villager", false));
        assertEquals(MountKind.OTHER, ZombieMountRules.mountKindForZombieEntityId("minecraft:zombified_piglin", false));
        assertEquals(MountKind.OTHER, ZombieMountRules.mountKindForZombieEntityId("minecraft:giant", false));
    }

    @Test
    void spiderTamingAndHealingUsesZombieFoods() {
        assertTrue(ZombieMountRules.isSpiderTamingFood("minecraft:rotten_flesh"));
        assertTrue(ZombieMountRules.isSpiderTamingFood("minecraft:spider_eye"));
        assertFalse(ZombieMountRules.isSpiderTamingFood("minecraft:wheat"));

        assertEquals(4.0F, ZombieMountRules.spiderHealAmount("minecraft:rotten_flesh"));
        assertEquals(6.0F, ZombieMountRules.spiderHealAmount("minecraft:spider_eye"));
        assertEquals(10.0F, ZombieMountRules.spiderHealAmount("iamzombieq:super_rotten_flesh"));
        assertEquals(0.0F, ZombieMountRules.spiderHealAmount("minecraft:wheat"));
    }

    @Test
    void modDrivenMountsHaveAPositiveRiddenSpeedAndOthersDefaultToZero() {
        // Spider/chicken/big-zombie are driven by the controlling-passenger flow (getRiddenSpeed), so they need
        // a movement-speed-attribute value; mounts the mod does not drive itself keep their vanilla ridden
        // speed (0). B3: the spider is now unified into riddenSpeedFor too (with a separate config override).
        assertEquals(ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED, ZombieMountRules.riddenSpeedFor(MountKind.SPIDER));
        assertEquals(ZombieMountRules.CHICKEN_MOUNT_SPEED, ZombieMountRules.riddenSpeedFor(MountKind.CHICKEN));
        assertEquals(ZombieMountRules.BIG_ZOMBIE_MOUNT_SPEED, ZombieMountRules.riddenSpeedFor(MountKind.BIG_ZOMBIE));
        assertTrue(ZombieMountRules.riddenSpeedFor(MountKind.SPIDER) > 0.0F);
        assertTrue(ZombieMountRules.riddenSpeedFor(MountKind.CHICKEN) > 0.0F);
        assertTrue(ZombieMountRules.riddenSpeedFor(MountKind.BIG_ZOMBIE) > 0.0F);

        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.STRIDER));
        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.ZOMBIE_HORSE));
        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.SKELETON_HORSE));
        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.ZOMBIE_NAUTILUS));
        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.NORMAL_HORSE));
        assertEquals(0.0F, ZombieMountRules.riddenSpeedFor(MountKind.OTHER));
    }

    @Test
    void spiderRiddenSpeedUsesConfigOverrideButFallsBackToTheRulesDefault() {
        // B3: spider speed is centralized; a positive config value overrides, else the rules default applies.
        assertEquals(ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED, ZombieMountRules.spiderRiddenSpeed(ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED));
        assertEquals(0.6F, ZombieMountRules.spiderRiddenSpeed(0.6F));
        // A non-positive (misconfigured) value must never freeze the mount: fall back to the default.
        assertEquals(ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED, ZombieMountRules.spiderRiddenSpeed(0.0F));
        assertEquals(ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED, ZombieMountRules.spiderRiddenSpeed(-1.0F));
    }

    @Test
    void allModDrivenMountSpeedsMatchTheirVanillaBaseSpeed() {
        // Ridden speed = each mount's vanilla base MOVEMENT_SPEED (official table): spider 0.3, chicken 0.25,
        // zombie 0.23. Guards against re-inflation (the chicken at 0.45 felt too fast in play).
        assertEquals(0.30F, ZombieMountRules.riddenSpeedFor(MountKind.SPIDER));
        assertEquals(0.25F, ZombieMountRules.riddenSpeedFor(MountKind.CHICKEN));
        assertEquals(0.23F, ZombieMountRules.riddenSpeedFor(MountKind.BIG_ZOMBIE));
        for (float s : new float[] {0.30F, 0.25F, 0.23F}) {
            assertTrue(s >= 0.2F && s <= 0.35F, "ridden speed should track a vanilla mob's base speed: " + s);
        }
    }

    @Test
    void spiderTamingProgressIsFoodDependentAndNotInstant() {
        // B1: one feed must NOT instantly tame; progress accrues per food and tames only at the threshold.
        assertEquals(20, ZombieMountRules.spiderTameProgressFor("minecraft:rotten_flesh"));
        assertEquals(35, ZombieMountRules.spiderTameProgressFor("minecraft:spider_eye"));
        assertEquals(60, ZombieMountRules.spiderTameProgressFor("iamzombieq:super_rotten_flesh"));
        assertEquals(0, ZombieMountRules.spiderTameProgressFor("minecraft:wheat"));

        // A single rotten-flesh feed is nowhere near the threshold (was the 100%-on-one-feed bug).
        int afterOne = ZombieMountRules.spiderTameProgressAfterFeed(0, "minecraft:rotten_flesh");
        assertEquals(20, afterOne);
        assertFalse(ZombieMountRules.spiderIsTamed(afterOne));

        // Even one super_rotten_flesh (the strongest) does not finish in a single feed.
        assertFalse(ZombieMountRules.spiderIsTamed(
                ZombieMountRules.spiderTameProgressAfterFeed(0, "iamzombieq:super_rotten_flesh")));
    }

    @Test
    void spiderTamingProgressAccumulatesAndClampsToThreshold() {
        // Five rotten flesh (5 * 20 = 100) reaches the threshold; progress never exceeds it.
        int p = 0;
        for (int i = 0; i < 4; i++) {
            p = ZombieMountRules.spiderTameProgressAfterFeed(p, "minecraft:rotten_flesh");
            assertFalse(ZombieMountRules.spiderIsTamed(p), "should not be tamed after " + (i + 1) + " rotten flesh");
        }
        p = ZombieMountRules.spiderTameProgressAfterFeed(p, "minecraft:rotten_flesh");
        assertTrue(ZombieMountRules.spiderIsTamed(p), "5x rotten flesh (100 progress) should tame the spider");
        assertEquals(ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD, p);

        // Overshoot is clamped to the threshold (no overflow / unbounded growth).
        int clamped = ZombieMountRules.spiderTameProgressAfterFeed(
                ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD, "iamzombieq:super_rotten_flesh");
        assertEquals(ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD, clamped);

        // A non-taming food adds nothing.
        assertEquals(40, ZombieMountRules.spiderTameProgressAfterFeed(40, "minecraft:wheat"));
        // Negative input is floored at 0 before adding.
        assertEquals(20, ZombieMountRules.spiderTameProgressAfterFeed(-50, "minecraft:rotten_flesh"));
    }

    // ---- M6 source-shape audit: the controlling-passenger riding flow is the only mount driver ----
    private static final Path EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMountEvents.java");
    private static final Path MOB_MIXIN = Path.of("src/main/java/dev/molang/iamzombieq/mixin/MobMixin.java");
    private static final Path LIVING_MIXIN = Path.of("src/main/java/dev/molang/iamzombieq/mixin/LivingEntityMixin.java");
    private static final Path RIDE_HELPER = Path.of("src/main/java/dev/molang/iamzombieq/util/RideHelper.java");

    @Test
    void noRideablePathDrivesMountsWithServerOnlySetDeltaMovementOrMove() throws IOException {
        // The whole point of the controlling-client authority technique: never steer a ridden mount by hacking
        // its motion server-side (that desyncs on a dedicated server). No mount-flow file may call
        // setDeltaMovement/move(MoverType...) and the old ad-hoc driveMount must be gone.
        for (Path p : new Path[] {EVENTS, MOB_MIXIN, LIVING_MIXIN, RIDE_HELPER}) {
            String code = stripComments(Files.readString(p));
            assertFalse(code.contains("setDeltaMovement"),
                    p + " must not steer a ridden mount with server-only setDeltaMovement");
            assertFalse(code.contains(".move(MoverType"),
                    p + " must not steer a ridden mount with a server-only move()");
            assertFalse(code.contains("driveMount"),
                    p + " must not retain the old ad-hoc driveMount path");
        }
    }

    @Test
    void chickenAndBigZombieAreDrivenByTheControllingPassengerFlow() throws IOException {
        String mob = Files.readString(MOB_MIXIN);
        // MobMixin makes the baby-player rider the controlling passenger so the client emits vehicle-move
        // packets. After the Tier-3 refactor the per-mount classification lives in MountCapability and the
        // mixin delegates to it (activeRider for the controlling passenger).
        assertTrue(mob.contains("getControllingPassenger"), "MobMixin must hook getControllingPassenger");
        assertTrue(mob.contains("MountCapability.activeRider"),
                "MobMixin must report the controlling rider via the MountCapability registry");

        // The chicken + big-zombie capabilities (and their valid-rider predicates) must exist in the registry.
        String capability = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/util/MountCapability.java"));
        assertTrue(capability.contains("CHICKEN") && capability.contains("BIG_ZOMBIE") && capability.contains("SPIDER"),
                "MountCapability must register the spider, chicken, and big-zombie mod-driven mounts");
        assertTrue(capability.contains("isBabyZombieRider") && capability.contains("isRideableBigZombie"),
                "the chicken/big-zombie capabilities must keep the baby-rider / rideable-big-zombie predicates");

        String living = Files.readString(LIVING_MIXIN);
        assertTrue(living.contains("getRiddenInput") && living.contains("getRiddenSpeed") && living.contains("tickRidden"),
                "LivingEntityMixin must drive ridden input, speed, and rotation");
        assertTrue(living.contains("RideHelper.riddenInput"),
                "ridden input must come from the shared RideHelper (no per-mount duplicate math)");
        assertTrue(living.contains("riddenSpeed("),
                "chicken/big-zombie/spider ridden speed must come from the MountCapability.riddenSpeed resolver");
    }

    @Test
    void bigZombieAutoAttackAndSpiderClimbSurviveTheRefactor() throws IOException {
        String events = Files.readString(EVENTS);
        assertTrue(events.contains("maybeAutoTargetForMountedBigZombie"),
                "the big-zombie auto-attack acquisition must be kept after removing driveMount");
        // B6: the ridden-spider climbing flag must be tracked in BOTH directions (resets to false when not
        // colliding), not only ever poked true. The actual climb motion comes from SpiderMixin.
        assertTrue(events.contains("spider.setClimbing(spider.horizontalCollision)"),
                "the ridden spider's climbing flag must track horizontalCollision (set true AND reset false)");
        assertFalse(events.contains("spider.setClimbing(true)"),
                "the one-directional setClimbing(true) (never reset) must be gone (B6)");
    }

    @Test
    void spiderClimbWhileRiddenIsDrivenByASpiderMixinOnOnClimbable() throws IOException {
        // B2: vanilla travel applies upward climb motion when (horizontalCollision || jumping) && onClimbable().
        // A mixin makes a ridden owner-spider's onClimbable() track local horizontalCollision so it climbs on
        // the controlling client and resets when not colliding.
        Path spiderMixin = Path.of("src/main/java/dev/molang/iamzombieq/mixin/SpiderMixin.java");
        assertTrue(Files.exists(spiderMixin), "a SpiderMixin must exist for the climb-while-ridden fix");
        String src = Files.readString(spiderMixin);
        assertTrue(src.contains("@Mixin(Spider.class)"), "SpiderMixin must target Spider");
        assertTrue(src.contains("method = \"onClimbable\""), "SpiderMixin must inject into onClimbable");
        assertTrue(src.contains("self.horizontalCollision"),
                "onClimbable for a ridden owner-spider must derive from local horizontalCollision (resets when not colliding)");
        assertTrue(src.contains("MountCapability.activeFor"),
                "the climb override must be gated via the MountCapability registry (owner-ridden spider)");

        String mixins = Files.readString(Path.of("src/main/resources/iamzombieq.mixins.json"));
        assertTrue(mixins.contains("SpiderMixin"), "SpiderMixin must be registered in iamzombieq.mixins.json");
    }

    @Test
    void rideHelperReturnsRawInputWithoutYawRotationToAvoidDoubleRotation() throws IOException {
        String helper = Files.readString(RIDE_HELPER);
        String input = helper.substring(helper.indexOf("public static Vec3 riddenInput"),
                helper.indexOf("}", helper.indexOf("public static Vec3 riddenInput")));
        // AbstractHorse-style raw input: strafe x0.5, backward x0.25, no sin/cos yaw rotation (travel rotates).
        assertTrue(input.contains("xxa * 0.5F"), "strafe must be halved like AbstractHorse");
        assertTrue(input.contains("0.25F"), "backward must be quartered like AbstractHorse");
        assertFalse(input.contains("sin") || input.contains("cos"),
                "riddenInput must NOT pre-rotate by yaw; the mount's travel() applies rotation (no double-rotation)");
    }

    private static String stripComments(String code) {
        return code
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
