package dev.molang.iamzombieq.gametest;

import java.util.List;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
// CROSS_VERSION-NAUTILUS-CAPABILITY:gametest-body-imports
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
//?}
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runtime regression GameTests for the 2026-07-09 bug-fix batch — the fixes whose behavior the FakePlayer harness can
 * drive (mob conversions via the real death pipeline; cake right-click). Each asserts the FIXED behavior, so it passes
 * only when the fix is present. Runs on the shared HARD environment ({@code env_hard}) so the infection chance is 1.0
 * (deterministic conversion). Driven by the shared {@link IAmZombieGameTestRegistry}.
 */
final class IAmZombieFixRegressionGameTestBodies {

    private IAmZombieFixRegressionGameTestBodies() {
    }

    /** #2: an unsaddled nautilus converts to a zombie-nautilus with NO saddle (the fix copies the source's empty slot). */
    // CROSS_VERSION-NAUTILUS-CAPABILITY:gametest-saddle-body
    //? if >=1.21.11 {
    static void nautilusSaddleNotFabricated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        BlockPos rel = new BlockPos(1, 2, 1);
        Nautilus nautilus = helper.spawn(EntityTypes.NAUTILUS, rel); // wild -> unsaddled
        GameTestSeams.killByPlayerAttack(level, player, nautilus);

        ZombieNautilus[] convertedBox = new ZombieNautilus[1];
        helper.startSequence()
                // Poll every tick for the converted zombie-nautilus to register (HARD chance-1.0 conversion; ~1-2
                // ticks) instead of a fixed 2t wait -> no registration-timing race. Search radius tightened to 1.0
                // (in-place conversion at victim's original rel) to exclude stray entities in the shared level.
                .thenWaitUntil(() -> {
                    List<ZombieNautilus> converted =
                            level.getEntitiesOfClass(ZombieNautilus.class, new AABB(helper.absolutePos(rel)).inflate(1.0));
                    if (converted.isEmpty()) {
                        throw helper.assertionException("waiting for the converted ZombieNautilus to register");
                    }
                    convertedBox[0] = converted.get(0);
                })
                .thenExecute(() -> {
                    ZombieNautilus converted = convertedBox[0];
                    if (converted == null) {
                        GameTestAssertions.fail(helper, "nautilus was not converted (HARD infection should be deterministic)");
                        return;
                    }
                    ItemStack saddle = converted.getItemBySlot(EquipmentSlot.SADDLE);
                    if (!saddle.isEmpty()) {
                        GameTestAssertions.fail(helper, "#2 regression: an unsaddled nautilus's conversion fabricated a saddle (" + saddle + ")");
                    }
                })
                .thenSucceed();
    }
    //?}

    /** #3/#4: a pig infected by a ZOMBIFIED_PIGLIN-form player converts to an ADULT piglin with native equipment. */
    static void piglinConversionNotBabyAndArmed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        BlockPos rel = new BlockPos(1, 2, 1);
        Pig pig = helper.spawn(EntityTypes.PIG, rel);
        GameTestSeams.killByPlayerAttack(level, player, pig);

        ZombifiedPiglin[] convertedBox = new ZombifiedPiglin[1];
        helper.startSequence()
                // Poll every tick for the converted zombified-piglin to register (HARD chance-1.0 conversion; ~1-2
                // ticks) instead of a fixed 2t wait -> no registration-timing race. Search radius tightened to 1.0
                // (in-place conversion at victim's original rel) to exclude stray entities in the shared level.
                .thenWaitUntil(() -> {
                    List<ZombifiedPiglin> converted =
                            level.getEntitiesOfClass(ZombifiedPiglin.class, new AABB(helper.absolutePos(rel)).inflate(1.0));
                    if (converted.isEmpty()) {
                        throw helper.assertionException("waiting for the converted ZombifiedPiglin to register");
                    }
                    convertedBox[0] = converted.get(0);
                })
                .thenExecute(() -> {
                    ZombifiedPiglin piglin = convertedBox[0];
                    if (piglin == null) {
                        GameTestAssertions.fail(helper, "pig was not converted to a zombified piglin (HARD infection should be deterministic)");
                        return;
                    }
                    if (piglin.isBaby()) {
                        GameTestAssertions.fail(helper, "#3 regression: an adult pig converted into a BABY zombified piglin (spurious finalizeSpawn baby roll)");
                        return;
                    }
                    // Vanilla always equips GOLDEN_SWORD here; >=1.21.11 can instead roll GOLDEN_SPEAR 5% of the time.
                    ItemStack mainhand = piglin.getItemBySlot(EquipmentSlot.MAINHAND);
                    boolean hasNodeNativeGoldenWeapon = mainhand.is(Items.GOLDEN_SWORD);
                    // CROSS_VERSION-GOLDEN-SPEAR-EQUIPMENT-POOL:high-node-vanilla-roll
                    //? if >=1.21.11
                    hasNodeNativeGoldenWeapon = hasNodeNativeGoldenWeapon || mainhand.is(Items.GOLDEN_SPEAR);
                    if (!hasNodeNativeGoldenWeapon) {
                        GameTestAssertions.fail(helper, "converted zombified piglin should hold its node-native default golden weapon (equipment must survive the fix), had "
                                + mainhand);
                    }
                })
                .thenSucceed();
    }

    /** #11: right-clicking a fresh cake while HOLDING A CANDLE (candle placement, no slice eaten) must NOT punish. */
    static void cakeCandlePlaceNotPunished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.getFoodData().setFoodLevel(6); // hunger room so the handler's canEat gate is satisfied
        BlockPos cakeAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cakeAbs, Blocks.CAKE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CANDLE));
        postRightClick(player, cakeAbs);

        if (player.hasEffect(MobEffects.HUNGER)) {
            GameTestAssertions.fail(helper, "#11 regression: placing a candle on a cake (no slice eaten) still applied the human-food punishment");
            return;
        }
        helper.succeed();
    }

    /** #11 (control): a normal empty-hand cake bite (with hunger room) MUST still punish — the fix must not over-skip. */
    static void cakeNormalBiteStillPunished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.getFoodData().setFoodLevel(6);
        BlockPos cakeAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cakeAbs, Blocks.CAKE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        postRightClick(player, cakeAbs);

        if (!player.hasEffect(MobEffects.HUNGER)) {
            GameTestAssertions.fail(helper, "a normal empty-hand cake bite should still apply the human-food punishment (fix must not over-skip)");
            return;
        }
        helper.succeed();
    }

    /** #11: holding a candle over a BITTEN cake cannot place a candle (vanilla eats), so it must still punish. */
    static void cakeCandleOnBittenCakeStillPunished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.getFoodData().setFoodLevel(6);
        BlockPos cakeAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cakeAbs, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 2), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CANDLE));
        postRightClickAt(player, cakeAbs, cakeAbs.getY() + 0.5);
        if (!player.hasEffect(MobEffects.HUNGER)) {
            GameTestAssertions.fail(helper, "holding a candle over a BITTEN cake still eats a slice (no placement) and must still be punished");
            return;
        }
        helper.succeed();
    }

    /** #11: an empty-hand click on the CAKE part (lower half) of a LIT candle-cake eats -> must still punish. */
    static void litCandleCakeBodyEatStillPunished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.getFoodData().setFoodLevel(6);
        BlockPos cakeAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cakeAbs, Blocks.CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        postRightClickAt(player, cakeAbs, cakeAbs.getY() + 0.2); // lower half = cake part = eats
        if (!player.hasEffect(MobEffects.HUNGER)) {
            GameTestAssertions.fail(helper, "an empty-hand hit on the CAKE part of a LIT candle-cake eats a slice and must still be punished");
            return;
        }
        helper.succeed();
    }

    /** #11: an empty-hand click on the CANDLE part (upper half) of a LIT candle-cake extinguishes (no eat) -> no punish. */
    static void litCandleCakeExtinguishNotPunished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.getFoodData().setFoodLevel(6);
        BlockPos cakeAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cakeAbs, Blocks.CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        postRightClickAt(player, cakeAbs, cakeAbs.getY() + 0.9); // upper half = candle part = extinguish, no eat
        if (player.hasEffect(MobEffects.HUNGER)) {
            GameTestAssertions.fail(helper, "extinguishing a LIT candle-cake (empty hand, candle-part hit) does not eat and must NOT be punished");
            return;
        }
        helper.succeed();
    }

    private static void postRightClick(FakePlayer player, BlockPos absPos) {
        postRightClickAt(player, absPos, absPos.getY() + 0.5);
    }

    private static void postRightClickAt(FakePlayer player, BlockPos absPos, double hitY) {
        Vec3 loc = new Vec3(absPos.getX() + 0.5, hitY, absPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(loc, Direction.UP, absPos, false);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, absPos, hit));
    }

    /** #10: an OWNED zombie-horse near a giant survives the stomp aura while an in-radius WILD one is stomped. */
    static void giantAuraSparesOwnedHorseStompsWild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer giant = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);
        ZombieHorse owned = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(2, 2, 1)); // within giantAutoDamageRadius (5)
        owned.setOwner(giant);
        ZombieHorse wild = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 2)); // wild, in radius
        owned.setHealth(owned.getMaxHealth());
        wild.setHealth(wild.getMaxHealth());
        float ownedBefore = owned.getHealth();
        float wildBefore = wild.getHealth();

        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(giant)); // fresh giant tickCount==0 -> the %20 aura gate fires

        if (wild.getHealth() >= wildBefore) {
            GameTestAssertions.fail(helper, "control: the giant stomp aura should damage a WILD mount in radius (aura did not fire)");
            return;
        }
        if (owned.getHealth() < ownedBefore) {
            GameTestAssertions.fail(helper, "#10 regression: the giant stomp aura damaged the player's OWN owned zombie-horse");
            return;
        }
        helper.succeed();
    }

    /** #10 (nautilus arm): an OWNED zombie-nautilus survives the aura while a WILD zombie-horse control is stomped. */
    // CROSS_VERSION-NAUTILUS-CAPABILITY:gametest-stomp-body
    //? if >=1.21.11 {
    static void giantAuraSparesOwnedNautilusStompsWild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer giant = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);
        ZombieNautilus owned = helper.spawn(EntityTypes.ZOMBIE_NAUTILUS, new BlockPos(2, 2, 1));
        owned.setOwner(giant);
        ZombieHorse wild = helper.spawn(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 2));
        owned.setHealth(owned.getMaxHealth());
        wild.setHealth(wild.getMaxHealth());
        float ownedBefore = owned.getHealth();
        float wildBefore = wild.getHealth();

        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(giant));

        if (wild.getHealth() >= wildBefore) {
            GameTestAssertions.fail(helper, "control: the giant stomp aura should damage a WILD mount in radius (aura did not fire)");
            return;
        }
        if (owned.getHealth() < ownedBefore) {
            GameTestAssertions.fail(helper, "#10 regression: the giant stomp aura damaged the player's OWN owned zombie-nautilus");
            return;
        }
        helper.succeed();
    }
    //?}

    /**
     * #1: the passive walk-destruction sweep clamps its last->now delta to the per-tick reach, so a STALE
     * GIANT_LAST_POS (a long teleport) cannot raze a far-away block. Seed the map by posting a tick at A, then move
     * the giant far to B, then post again: a control block within reach of B is crushed (sweep live), but a block
     * ~15 blocks back at A survives (the clamp bounded the sweep). Without the clamp the unbounded B->A sweep would
     * raze the far block. Deterministic (no RNG in the sweep/crush path).
     */
    static void giantSweepClampBoundsTeleport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer giant = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);

        // Seed GIANT_LAST_POS = A (the put happens before the early-return, and last==now so nothing is destroyed).
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(giant));
        BlockPos aBlock = giant.blockPosition();
        BlockPos farBlock = aBlock.above(); // ~15 blocks back from B, above the foot layer
        level.setBlock(farBlock, Blocks.DIRT.defaultBlockState(), 3);

        // Move the giant a large delta to B (a stale-position sweep source).
        giant.snapTo(giant.getX() + 15.0, giant.getY(), giant.getZ(), 0.0F, 0.0F);
        BlockPos bBlock = giant.blockPosition();
        BlockPos nearBlock = bBlock.offset(1, 1, 0); // within reach of B, above the foot layer
        level.setBlock(nearBlock, Blocks.DIRT.defaultBlockState(), 3);

        // Sweep tick: now=B, last=A (stale) -> the clamp bounds the sweep to ~reach around B.
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(giant));

        if (!level.getBlockState(nearBlock).isAir()) {
            GameTestAssertions.fail(helper, "control: the giant passive sweep should crush a soft block within reach of its new position (sweep did not fire)");
            return;
        }
        if (level.getBlockState(farBlock).isAir()) {
            GameTestAssertions.fail(helper, "#1 regression: the sweep razed a block ~15 blocks back at the STALE position -> delta clamp not applied (unbounded sweep)");
            return;
        }
        helper.succeed();
    }

}
