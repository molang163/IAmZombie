package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.giant.BlockCrushQuery;
import dev.molang.iamzombieq.rules.giant.GiantRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.tags.IAmZombieBlockTags;
import dev.molang.iamzombieq.util.MountCapability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Giant-form event wiring: the passive walk-destruction sweep + stomp aura ({@link #handleGiantTick}, driven by the
 * core per-tick coordinator) and the active left-click AoE ({@link #onGiantSwing}). Owns the two per-giant transient
 * maps ({@code GIANT_LAST_POS} / {@code GIANT_SWING_COOLDOWN}), self-cleaning them on logout / server stop and via
 * {@link #cleanupOnFormLeave} when the player leaves the giant form.
 */
public final class GiantPlayerEvents {
    // Per-giant last-tick position, so the passive walk-destruction sweep volume spans from last to current position
    // (catches blocks a fast/sprinting giant would otherwise phase past). Server-side only; cleared on logout + stop.
    private static final Map<UUID, Vec3> GIANT_LAST_POS = new HashMap<>();
    // Per-giant game-time until which the active swing AoE is on cooldown, so left-clicking isn't an infinite
    // instant-miner. Server-side only; cleared on logout + server stop.
    private static final Map<UUID, Long> GIANT_SWING_COOLDOWN = new HashMap<>();

    private GiantPlayerEvents() {
    }

    static void handleGiantTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            GIANT_LAST_POS.remove(player.getUUID());
            return;
        }
        // Passive walk-destruction runs EVERY tick (碰哪哪坏): soft blocks in the body's sweep are cleared before the
        // movement collision resolves, so the giant never jams on them; hard/immune blocks survive and stop it.
        smashBlocksWhileWalking(level, player);
        // The stomp aura (area damage) stays on the cheaper 1-second cadence.
        if (player.tickCount % 20 == 0) {
            damageNearbyAsGiant(level, player);
        }
    }

    private static void damageNearbyAsGiant(ServerLevel level, ServerPlayer player) {
        double radius = GiantRules.giantAutoDamageRadius();
        AABB area = player.getBoundingBox().inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target ->
                target != player
                        && target.isAlive()
                        && !target.isSpectator()
                        && target != player.getVehicle()
                        && !player.hasPassenger(target)
                        && !isOwnedMount(target, player))) {
            target.hurtServer(level, player.damageSources().playerAttack(player), (float) GiantRules.giantAutoDamageAmount());
        }
    }

    // A giant must not stomp its OWN mounts. Beyond the custom Spider mount (SPIDER_MOUNT attachment), a player can
    // also own vanilla-tamed mounts via setOwner — a tamed ZombieHorse/SkeletonHorse (AbstractHorse) or a tamed
    // ZombieNautilus — that it is not currently riding; those fall inside the stomp AABB and would be killed (#10).
    // getOwnerReference() is @Nullable (null for wild/untamed mounts) and UUIDs are compared with equals().
    private static boolean isOwnedMount(LivingEntity target, ServerPlayer player) {
        java.util.UUID owner = player.getUUID();
        if (target instanceof net.minecraft.world.entity.monster.spider.Spider spider) {
            return MountCapability.isOwnedSpider(spider, owner);
        }
        if (target instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
            var ref = horse.getOwnerReference();
            return ref != null && owner.equals(ref.getUUID());
        }
        // CROSS_VERSION-NAUTILUS-CAPABILITY:giant-owned-exemption
        //? if >=1.21.11 {
        if (target instanceof net.minecraft.world.entity.animal.nautilus.ZombieNautilus nautilus) {
            var ref = nautilus.getOwnerReference();
            return ref != null && owner.equals(ref.getUUID());
        }
        //?}
        return false;
    }

    // The giant's passive walk-destruction: crush the SOFT blocks (GIANT_SOFT tag / very-soft fallback) the scaled
    // body sweeps through, never the foot layer. The sweep volume spans last->current position so a sprinting giant
    // (>0.5 block/tick) doesn't phase past blocks. No drops (跑一路掉物 = 崩服 + 刷物). Hard/immune blocks survive and
    // naturally stop the giant — the "碾村但被天然大山挡住" balance valve.
    private static void smashBlocksWhileWalking(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        Vec3 now = player.position();
        Vec3 last = GIANT_LAST_POS.getOrDefault(id, now);
        GIANT_LAST_POS.put(id, now);
        if (now.distanceToSqr(last) < 0.05 * 0.05) {
            return;
        }
        AABB rawBody = player.getBoundingBox();
        double footY = rawBody.minY;
        // Clamp the sweep delta to the normal per-tick reach so a STALE GIANT_LAST_POS (a long teleport or a
        // dimension change never seeds/clears this map) cannot expand the sweep AABB across thousands of blocks
        // and iterate an unbounded (mostly-air) volume every tick. A giant walks << reach/tick, so normal
        // walk-destruction is unchanged; only the pathological stale-delta case is bounded.
        double reachH = GiantRules.giantPassiveReachHorizontal();
        double reachV = GiantRules.giantPassiveReachVertical();
        double deltaX = Math.max(-reachH, Math.min(reachH, last.x - now.x));
        double deltaY = Math.max(-reachV, Math.min(reachV, last.y - now.y));
        double deltaZ = Math.max(-reachH, Math.min(reachH, last.z - now.z));
        AABB sweep = rawBody.inflate(reachH, reachV, reachH)
                .expandTowards(deltaX, deltaY, deltaZ);
        crushGiantBlocks(level, player, BlockPos.betweenClosed(sweep), footY, true,
                GiantRules.giantPassiveDestroyCapPerTick(), GiantRules.GIANT_PASSIVE_MAX_HARDNESS, false);
    }

    /**
     * The unified giant destruction kernel: crush a batch of positions, filtering air and fluids,
     * block entities / the GIANT_IMMUNE blacklist, keeping the GIANT_SOFT whitelist plus a hardness fallback, and
     * (for passive contact) preserving the foot layer. Removes blocks with {@code setBlock(AIR, flag 34)} =
     * UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS, so there are NO item drops and NO neighbour (redstone/physics)
     * cascades — the only safe way to delete blocks at giant scale. Returns the number destroyed.
     */
    private static int crushGiantBlocks(ServerLevel level, ServerPlayer player, Iterable<BlockPos> positions,
                                        double footY, boolean preserveFootLayer, int cap, float maxHardness,
                                        boolean dropToInventory) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int destroyed = 0;
        for (BlockPos pos : positions) {
            if (destroyed >= cap) {
                break;
            }
            if (preserveFootLayer && !GiantRules.giantDestroysBlockLayer(pos.getY(), footY)) {
                continue;
            }
            cursor.set(pos);
            BlockState state = level.getBlockState(cursor);
            float destroySpeed = state.isAir() ? 0.0F : state.getDestroySpeed(level, cursor);
            if (!GiantRules.giantCanCrush(new BlockCrushQuery(
                    state.isAir(),
                    state.hasBlockEntity(),
                    !state.getFluidState().isEmpty(),
                    state.is(IAmZombieBlockTags.GIANT_SOFT),
                    state.is(IAmZombieBlockTags.GIANT_IMMUNE),
                    destroySpeed,
                    maxHardness))) {
                continue;
            }
            if (dropToInventory) {
                // The active swing rakes loot into the giant's pack; overflow is discarded (NOT scattered — a litter
                // of drops at giant scale risks lag/dupe). The setBlock(flag 34) below never drops anything itself.
                for (ItemStack drop : Block.getDrops(state, level, cursor.immutable(), level.getBlockEntity(cursor))) {
                    player.getInventory().add(drop);
                }
            }
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            destroyed++;
        }
        return destroyed;
    }

    @SubscribeEvent
    public static void onGiantSwing(PlayerInteractEvent.LeftClickBlock event) {
        // The giant's active 一拳一大片: a left-click on a block within its long reach blasts a cube centred on the
        // aimed block. Server-authoritative (ServerPlayer only), gated to the START of the click and a cooldown.
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !ZombiePlayerEvents.shouldApplyZombieRules(player)
                || player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form() != ZombieForm.GIANT) {
            return;
        }
        long now = level.getGameTime();
        Long cooldownUntil = GIANT_SWING_COOLDOWN.get(player.getUUID());
        if (cooldownUntil != null && now < cooldownUntil) {
            return;
        }
        BlockPos center = event.getPos();
        // Server-side reach validation: reject a block beyond the giant's (already-extended) block reach.
        double reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        if (player.getEyePosition(1.0F).distanceToSqr(Vec3.atCenterOf(center)) > (reach + 1.0) * (reach + 1.0)) {
            return;
        }
        int half = GiantRules.giantSwingCubeEdge() / 2;
        List<BlockPos> cube = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, -half, -half), center.offset(half, half, half))) {
            cube.add(pos.immutable());
        }
        // Crush the blocks nearest the impact first, up to the per-swing cap; the punch breaks stone/ores (high
        // hardness cap) but never obsidian/bedrock/containers, and the loot rakes into the giant's pack.
        cube.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        // A committed swing always starts the cooldown — even one that only struck immune/obsidian blocks — so the
        // giant cannot become an infinite instant miner by clicking unbreakable targets.
        GIANT_SWING_COOLDOWN.put(player.getUUID(), now + GiantRules.giantSwingCooldownTicks());
        crushGiantBlocks(level, player, cube, 0.0, false,
                GiantRules.giantSwingMaxBlocks(), GiantRules.GIANT_SWING_MAX_HARDNESS, true);
    }

    /**
     * Drop the giant walk-destruction sweep anchor + swing cooldown for a player leaving the giant form. Called from
     * {@link ZombieFormAttributes#applyFormAttributes} in its {@code !giant} branch (this runs once per form change
     * via the signature-cached refresh) rather than leaking them in the maps until the player logs out.
     */
    public static void cleanupOnFormLeave(UUID uuid) {
        GIANT_LAST_POS.remove(uuid);
        GIANT_SWING_COOLDOWN.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop the giant walk-destruction sweep anchor + swing cooldown so they can't accumulate for the server's
        // lifetime (and a reconnecting player starts with a fresh sweep origin / no stale cooldown).
        GIANT_LAST_POS.remove(event.getEntity().getUUID());
        GIANT_SWING_COOLDOWN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GIANT_LAST_POS.clear();
        GIANT_SWING_COOLDOWN.clear();
    }
}
