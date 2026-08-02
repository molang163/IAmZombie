package dev.molang.iamzombieq.gametest;

import java.util.List;
import java.util.UUID;

import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.SpiderMountData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * FakePlayer- and connected-ServerPlayer-driven bodies for the {@code iamzombieq} MOUNT GameTests (catalog
 * &sect;2.12 MNT), registered by {@link IAmZombieMountGameTests}.
 *
 * <p><b>Interaction seam.</b> The mod's mount logic lives in {@code ZombieMountEvents.onEntityInteract}, a
 * {@code @SubscribeEvent} handler on the NeoForge game bus listening for {@link PlayerInteractEvent.EntityInteract}.
 * Because {@code FakePlayer.tick()} is a no-op, these tests drive that handler the same way vanilla does: they post a
 * real {@link PlayerInteractEvent.EntityInteract} to {@link NeoForge#EVENT_BUS} &mdash; the exact event vanilla's
 * {@code CommonHooks.onInteractEntity} fires from {@code ServerGamePacketListenerImpl}/{@code Player.interactOn}.
 * Positive passenger tests instead call the connected player's real three-argument {@code interactOn}; this reaches
 * the same handler and then vanilla's {@code startRiding} implementation. The mod is loaded in the GameTest run, so
 * both paths exercise the production server-side code.
 *
 * <p>Batched tests share one level, so entity assertions and spawns use a tight radius / the test's own local origin
 * (which {@code padding} spaces well apart from neighbours).
 */
final class IAmZombieMountGameTestBodies {

    private IAmZombieMountGameTestBodies() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-001: spider taming progress accrues per feed via the real EntityInteract handler (not instant).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-001: feeding an untamed spider rotten flesh through the real interact handler adds exactly the
     * rotten-flesh taming increment (+20) to its {@code SPIDER_MOUNT} attachment and does NOT yet bind ownership
     * (one feed is far below the threshold of 100). The food is consumed.
     */
    static void spiderTameProgressRottenFlesh(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));

        ItemStack food = new ItemStack(Items.ROTTEN_FLESH, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        interact(player, spider);

        SpiderMountData data = spider.getData(IAmZombieAttachments.SPIDER_MOUNT);
        if (data.tameProgress() != ZombieMountRules.SPIDER_TAME_PROGRESS_ROTTEN_FLESH) {
            helper.fail("one rotten-flesh feed should add +" + ZombieMountRules.SPIDER_TAME_PROGRESS_ROTTEN_FLESH
                    + " taming progress, was " + data.tameProgress());
            return;
        }
        if (data.hasOwner()) {
            helper.fail("a single rotten-flesh feed must NOT instantly tame (taming is no longer instant)");
            return;
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("the taming feed should consume exactly one rotten flesh (2 -> 1)");
            return;
        }
        helper.succeed();
    }

    /**
     * MNT-001 (super food increment): feeding an untamed spider super_rotten_flesh adds the larger +60 increment.
     * Confirms the per-food graded progress (+60 for super vs +20 for rotten flesh) on the real path, and that even
     * the strongest single feed still does not reach the 100 threshold.
     */
    static void spiderTameProgressSuperRottenFlesh(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get()));
        interact(player, spider);

        SpiderMountData data = spider.getData(IAmZombieAttachments.SPIDER_MOUNT);
        if (data.tameProgress() != ZombieMountRules.SPIDER_TAME_PROGRESS_SUPER_ROTTEN_FLESH) {
            helper.fail("one super_rotten_flesh feed should add +" + ZombieMountRules.SPIDER_TAME_PROGRESS_SUPER_ROTTEN_FLESH
                    + " taming progress, was " + data.tameProgress());
            return;
        }
        if (data.hasOwner()) {
            helper.fail("even one super_rotten_flesh feed (the strongest) must not reach the tame threshold");
            return;
        }
        helper.succeed();
    }

    /**
     * MNT-001 (threshold -> ownership): feeding an already-near-threshold spider crosses 100 and binds ownership to
     * the player. We pre-seed the attachment to 80 progress (un-owned), then one super (+60, clamped) tames it.
     * Confirms the full B1 progress-to-ownership flow on the real interact path.
     */
    static void spiderTameReachesThresholdBindsOwner(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));
        // 80 progress, still un-owned (one more strong feed crosses the 100 threshold).
        spider.setData(IAmZombieAttachments.SPIDER_MOUNT, SpiderMountData.DEFAULT.withProgress(80));

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get()));
        interact(player, spider);

        SpiderMountData data = spider.getData(IAmZombieAttachments.SPIDER_MOUNT);
        if (!data.isOwnedBy(player.getUUID())) {
            helper.fail("crossing the taming threshold should bind the spider's owner to the player");
            return;
        }
        if (data.tameProgress() != ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD) {
            helper.fail("a tamed spider's progress should be clamped to the threshold (" + data.tameProgress() + ")");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-002: feeding an owned, damaged spider heals it (graded by food).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-002: a spider already owned by the player, below max health, fed super_rotten_flesh through the real
     * interact handler heals by 10.0 (clamped to max) and consumes the food. Pre-owning the spider routes the feed
     * into the heal branch (not the taming branch).
     */
    static void ownedSpiderHealsWhenFedSuperRottenFlesh(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));
        spider.setData(IAmZombieAttachments.SPIDER_MOUNT, SpiderMountData.ownedBy(player.getUUID()));

        float max = spider.getMaxHealth();
        // Damage it well below max so a 10-point heal cannot overshoot the cap (so we can assert the gain exactly).
        float start = Math.max(1.0F, max - 12.0F);
        spider.setHealth(start);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get(), 2));
        interact(player, spider);

        float expected = Math.min(max, start + ZombieMountRules.spiderHealAmount(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID));
        if (Math.abs(spider.getHealth() - expected) > 0.001F) {
            helper.fail("feeding super_rotten_flesh to an owned damaged spider should heal +10.0 (to "
                    + expected + "), was " + spider.getHealth());
            return;
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("a successful spider heal should consume one super_rotten_flesh (2 -> 1)");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-013: undead horses (zombie/skeleton) auto-tame + set owner on an empty-hand interact.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-013 (zombie horse): a zombie player interacting empty-handed with a wild (untamed) ZombieHorse auto-tames
     * it (bypassing the saddle/breeding requirement) and sets the player as owner, so vanilla can then mount it / open
     * its inventory. Driven through the real interact handler.
     */
    static void wildZombieHorseAutoTamesOnInteract(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ZombieHorse horse = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 1));
        if (horse.isTamed()) {
            helper.fail("precondition: a freshly spawned zombie horse should start untamed");
            return;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        interact(player, horse);

        if (!horse.isTamed()) {
            helper.fail("a zombie player's empty-hand interact should auto-tame a wild zombie horse");
            return;
        }
        UUID ownerUuid = horse.getOwnerReference() == null ? null : horse.getOwnerReference().getUUID();
        if (!player.getUUID().equals(ownerUuid)) {
            helper.fail("the auto-tamed zombie horse should be owned by the interacting zombie player");
            return;
        }
        helper.succeed();
    }

    /**
     * MNT-013 (skeleton horse): the same auto-tame-on-empty-hand-interact applies to a wild SkeletonHorse (the
     * second undead-horse branch). Confirms the {@code instanceof SkeletonHorse} arm of the auto-tame block.
     */
    static void wildSkeletonHorseAutoTamesOnInteract(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        SkeletonHorse horse = helper.spawn(EntityTypes.SKELETON_HORSE, new BlockPos(1, 2, 1));
        if (horse.isTamed()) {
            helper.fail("precondition: a freshly spawned skeleton horse should start untamed");
            return;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        interact(player, horse);

        if (!horse.isTamed()) {
            helper.fail("a zombie player's empty-hand interact should auto-tame a wild skeleton horse");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-014/015: feeding an undead (zombie) horse heals it; at full health it refuses + keeps the food.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-014: feeding a damaged ZombieHorse super_rotten_flesh through the real interact handler heals it by 10.0
     * and consumes one food. (Plain rotten flesh would heal 4.0; the handler picks 10.0 for super.)
     */
    static void damagedZombieHorseHealsWhenFed(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ZombieHorse horse = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 1));

        float max = horse.getMaxHealth();
        float start = Math.max(1.0F, max - 12.0F);
        horse.setHealth(start);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get(), 2));
        interact(player, horse);

        float expected = Math.min(max, start + 10.0F);
        if (Math.abs(horse.getHealth() - expected) > 0.001F) {
            helper.fail("feeding super_rotten_flesh to a damaged zombie horse should heal +10.0 (to "
                    + expected + "), was " + horse.getHealth());
            return;
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("a successful zombie-horse heal should consume one super_rotten_flesh (2 -> 1)");
            return;
        }
        helper.succeed();
    }

    /**
     * MNT-015: feeding a FULL-health ZombieHorse must NOT consume the food (B7: acknowledge the click, don't waste
     * the food), and the interact event is cancelled so vanilla does not also act. We assert via the cancellation
     * result of the posted event plus the unchanged food count.
     */
    static void fullHealthZombieHorseFeedKeepsFoodAndCancels(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ZombieHorse horse = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 1));
        horse.setHealth(horse.getMaxHealth());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH, 3));
        PlayerInteractEvent.EntityInteract event = interact(player, horse);

        if (!event.isCanceled()) {
            helper.fail("feeding a full-health zombie horse should cancel the interact (B7), not silently fall through");
            return;
        }
        if (player.getMainHandItem().getCount() != 3) {
            helper.fail("feeding a full-health zombie horse must NOT consume the food (count stayed at 3)");
            return;
        }
        if (Math.abs(horse.getHealth() - horse.getMaxHealth()) > 0.001F) {
            helper.fail("a full-health zombie horse should remain at max health after a refused feed");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-016: a zombie player interacting with a NORMAL (living) horse is refused + the event cancelled.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-016: a zombie player interacting with a vanilla (living) Horse is refused &mdash; the interact event is
     * cancelled with a SERVER result (and the refusal message sent). A normal horse is never a zombie-player mount.
     * Empty hand so we exercise the pure refusal block (no food path).
     */
    static void normalHorseInteractIsRefusedAndCancelled(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Horse horse = helper.spawn(EntityTypes.HORSE, new BlockPos(1, 2, 1));
        boolean wasTamed = horse.isTamed();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        PlayerInteractEvent.EntityInteract event = interact(player, horse);

        if (!event.isCanceled()) {
            helper.fail("a zombie player interacting with a normal horse should cancel the interact (refused)");
            return;
        }
        if (horse.isTamed() != wasTamed) {
            helper.fail("a refused normal horse must NOT be tamed/owned by the zombie player");
            return;
        }
        if (player.isPassenger()) {
            helper.fail("a zombie player must NOT end up riding a refused normal horse");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-003: riding a tamed (owned) spider is allowed; an untamed spider is refused (no ride).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-003 (positive): an empty-handed NORMAL/ADULT connected player rides its owned spider through the real
     * three-argument interaction path. The mount event and both sides of the passenger relation must be established.
     */
    static void ownedSpiderRideAllowed(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        Spider spider = null;
        MountObserver observer = null;
        boolean observerRegistered = false;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));
            spider.setData(IAmZombieAttachments.SPIDER_MOUNT, SpiderMountData.ownedBy(playerId));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            observer = new MountObserver(playerId, spider.getUUID());
            NeoForge.EVENT_BUS.register(observer);
            observerRegistered = true;

            InteractionResult result = player.interactOn(spider, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(result == InteractionResult.SUCCESS_SERVER,
                    "owned-spider interaction should return SUCCESS_SERVER");
            assertMounted(helper, player, spider, observer, "owned spider");
        } finally {
            try {
                if (observerRegistered) {
                    NeoForge.EVENT_BUS.unregister(observer);
                }
            } finally {
                try {
                    if (spider != null) {
                        spider.discard();
                    }
                } finally {
                    if (playerId != null) {
                        GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * MNT-003 (negative): an empty-handed interact with an UNTAMED spider does not start a ride (the spider is not
     * owned, so {@code canMount(SPIDER, false)} is false). The player must not become a passenger.
     */
    static void untamedSpiderRideRefused(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 2, 1));
        // Untamed by default.

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        interact(player, spider);

        if (player.isPassenger()) {
            helper.fail("a zombie player must not be able to ride an UNTAMED spider");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-011: a chicken is a baby-only mount; an ADULT rider is refused via the real interact handler.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-011 (positive): an empty-handed NORMAL/BABY connected player rides a chicken through the production
     * interaction and mount-event chain.
     */
    static void babyCanRideChicken(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        Chicken chicken = null;
        MountObserver observer = null;
        boolean observerRegistered = false;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.BABY);
            playerId = player.getUUID();
            chicken = helper.spawn(EntityTypes.CHICKEN, new BlockPos(1, 2, 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            observer = new MountObserver(playerId, chicken.getUUID());
            NeoForge.EVENT_BUS.register(observer);
            observerRegistered = true;

            InteractionResult result = player.interactOn(chicken, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(result == InteractionResult.SUCCESS_SERVER,
                    "baby-chicken interaction should return SUCCESS_SERVER");
            assertMounted(helper, player, chicken, observer, "baby chicken");
        } finally {
            try {
                if (observerRegistered) {
                    NeoForge.EVENT_BUS.unregister(observer);
                }
            } finally {
                try {
                    if (chicken != null) {
                        chicken.discard();
                    }
                } finally {
                    if (playerId != null) {
                        GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * MNT-011 (negative): an ADULT zombie player interacting empty-handed with a Chicken must NOT mount it (the
     * chicken is a baby-only mount). The interact is still acknowledged (cancelled) but no ride happens.
     */
    static void adultCannotRideChicken(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Chicken chicken = helper.spawn(EntityTypes.CHICKEN, new BlockPos(1, 2, 1));

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        interact(player, chicken);

        if (player.isPassenger()) {
            helper.fail("an ADULT zombie player must NOT be able to ride a chicken (baby-only mount)");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // MNT-017: a horse killed by a zombie player converts to a zombie horse via the shared infection pipeline.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * MNT-017 (A2 pipeline migration): killing a half-health Horse through the real damage pipeline
     * ({@code hurtServer}, the same driving technique as the FixRegression nautilus conversion test) must convert
     * it into a ZombieHorse via the shared infection pipeline in {@code ZombieInfectionEvents} &mdash; tamed,
     * owned by the killing player, and with its health restored to the pre-death RATIO (0.5 of the zombie horse's
     * max), proving the {@code recordPendingHorseHealthRatio} bridge + the migrated conversion work end-to-end.
     * The HARD environment makes the infection roll deterministic (configured chance 1.0).
     */
    static void zombieHorseInfectionOnDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        BlockPos rel = new BlockPos(1, 2, 1);
        Horse horse = helper.spawn(EntityTypes.HORSE, rel);
        // Half health BEFORE the lethal hit: the LivingIncomingDamage snapshot must capture exactly ratio 0.5.
        horse.setHealth(horse.getMaxHealth() * 0.5F);

        GameTestSeams.killByPlayerAttack(level, player, horse);

        helper.startSequence().thenExecuteAfter(2, () -> {
            List<ZombieHorse> converted =
                    level.getEntitiesOfClass(ZombieHorse.class, new AABB(helper.absolutePos(rel)).inflate(4.0));
            if (converted.isEmpty()) {
                helper.fail("horse was not converted to a zombie horse (HARD infection should be deterministic)");
                return;
            }
            ZombieHorse zombieHorse = converted.get(0);
            if (!zombieHorse.isTamed()) {
                helper.fail("the converted zombie horse should be tamed for its new owner");
                return;
            }
            UUID ownerUuid = zombieHorse.getOwnerReference() == null ? null : zombieHorse.getOwnerReference().getUUID();
            if (!player.getUUID().equals(ownerUuid)) {
                helper.fail("the converted zombie horse should be owned by the killing zombie player");
                return;
            }
            float expected = Math.max(1.0F, zombieHorse.getMaxHealth() * 0.5F);
            if (Math.abs(zombieHorse.getHealth() - expected) > 0.001F) {
                helper.fail("the converted zombie horse should restore the pre-death health ratio (expected "
                        + expected + ", was " + zombieHorse.getHealth() + ")");
            }
        }).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    private static void assertMounted(
            GameTestHelper helper,
            ServerPlayer player,
            Entity mount,
            MountObserver observer,
            String label) {
        helper.assertTrue(observer.matchingMountEvents == 1,
                label + " should publish exactly one matching EntityMountEvent, observed "
                        + observer.matchingMountEvents);
        helper.assertFalse(observer.lastMountCanceled,
                label + " EntityMountEvent should be finally uncanceled at LOWEST priority");
        helper.assertTrue(player.isPassenger(), label + " rider should report itself as a passenger");
        helper.assertTrue(player.getVehicle() == mount, label + " rider should reference the expected vehicle");
        helper.assertTrue(mount.getFirstPassenger() == player,
                label + " mount should reference the player as its first passenger");
    }

    /**
     * Posts the real {@link PlayerInteractEvent.EntityInteract} (MAIN_HAND) to {@link NeoForge#EVENT_BUS} &mdash; the
     * exact event vanilla fires from {@code Player.interactOn} &mdash; so the mod's {@code @SubscribeEvent}
     * {@code ZombieMountEvents.onEntityInteract} handler runs against the FakePlayer + target. Returns the posted
     * event so callers can inspect its cancellation state.
     */
    private static PlayerInteractEvent.EntityInteract interact(FakePlayer player, net.minecraft.world.entity.Entity target) {
        PlayerInteractEvent.EntityInteract event =
                new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    private static final class MountObserver {
        private final UUID riderId;
        private final UUID mountId;
        private int matchingMountEvents;
        private boolean lastMountCanceled;

        private MountObserver(UUID riderId, UUID mountId) {
            this.riderId = riderId;
            this.mountId = mountId;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onMount(EntityMountEvent event) {
            Entity mounted = event.getEntityBeingMounted();
            if (event.isMounting()
                    && riderId.equals(event.getEntityMounting().getUUID())
                    && mounted != null
                    && mountId.equals(mounted.getUUID())) {
                matchingMountEvents++;
                lastMountCanceled = event.isCanceled();
            }
        }
    }
}
