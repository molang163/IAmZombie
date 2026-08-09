package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.block.CoffinBlock;
import dev.molang.iamzombieq.internal.logging.ZombieLog;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules.NapWakeReason;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
//? if >=26.1
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
//? if >=26.1
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
//? if >=26.1
import net.minecraft.world.clock.ClockTimeMarkers;
//? if >=26.1
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=26.1
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Coffin "true sleep" driver. Unlike the previous instant time-skip, this lets a zombie player actually sleep for a
 * few seconds (sleeping pose + fade-to-black), runs a per-dimension vote against {@code players_sleeping_percentage},
 * and only then advances the dimension clock to NIGHT and wakes everyone resting in that dimension together.
 *
 * <p>Server-thread only: {@link PlayerTickEvent.Post} fires on the server thread for {@link ServerPlayer}s, so the
 * per-UUID nap map is a plain {@link HashMap} with no synchronization. The map is cleaned up on logout and on server
 * stop so naps never leak across reconnects or world reloads.</p>
 */
public final class CoffinNapManager {
    private static final Map<UUID, Nap> NAPS = new HashMap<>();

    // 100 ticks (~5s) is vanilla's deep-sleep threshold (Player.isSleepingLongEnough). After a player is deep-asleep
    // we wait at most this many extra ticks for the rest of the dimension's zombies to catch the vote before waking
    // them with no time skip. Hardcoded anti-deadlock guard (no config knob): a single hold-out can never strand a
    // resting zombie forever.
    private static final long MAX_WAIT_TICKS = 200L;
    private static final long DEEP_SLEEP_TICKS = 100L;
    //? if <26.1
    //private static final long LEGACY_NIGHT_TICK = 13000L;

    private CoffinNapManager() {
    }

    private static final class Nap {
        final BlockPos headPos;
        final long startTick;
        float lastHealth;

        Nap(BlockPos headPos, long startTick, float lastHealth) {
            this.headPos = headPos;
            this.startTick = startTick;
            this.lastHealth = lastHealth;
        }
    }

    /**
     * Begin a real sleep on the head half of the coffin. Returns false (so the caller can fall back to only setting a
     * respawn point) when the player is mounted, already sleeping, or the target is no longer a bed.
     */
    public static boolean beginNap(ServerLevel level, ServerPlayer player, BlockPos headPos) {
        if (player.isSleeping() || player.isPassenger()) {
            return false;
        }
        BlockState headState = level.getBlockState(headPos);
        if (!headState.isBed(level, headPos, player)) {
            return false;
        }
        // Real sleeping pose + fade-to-black + setBedOccupied(true). startSleeping (NOT startSleepInBed) bypasses the
        // dimension WHEN_DARK bed rule so zombies can sleep during the day.
        player.startSleeping(headPos);
        CoffinBlock.setCoffinRespawn(level, player, headPos);
        NAPS.put(player.getUUID(), new Nap(headPos, level.getGameTime(), player.getHealth()));
        return true;
    }

    /**
     * True while {@code id} is in an active coffin nap. Used by {@link #onCanContinueSleeping} to keep a daytime coffin
     * napper asleep against vanilla's per-tick bed-rule auto-wake.
     */
    public static boolean isNapping(UUID id) {
        return NAPS.containsKey(id);
    }

    @SubscribeEvent
    public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        // Replaces the deleted PlayerCoffinSleep mixin's @Redirect. Both vanilla wake gates run through this event
        // (Player.tick daytime wake @ Player.java:258; bed-exists check @ LivingEntity.java:3937), while manual
        // wake (ServerGamePacketListenerImpl:1752) and this manager's own wake() (stopSleeping direct) do NOT,
        // so forcing continue-sleeping while napping cannot trap the player; a destroyed coffin is handled by
        // this manager's per-tick isBed self-check -> wake().
        if (event.getEntity() instanceof ServerPlayer player && isNapping(player.getUUID())) {
            event.setContinueSleeping(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Nap nap = NAPS.get(player.getUUID());
        if (nap == null) {
            return;
        }
        ServerLevel level = player.level();

        // 1) Woken externally / sleeping pos lost -> just drop the nap, no time skip.
        if (!player.isSleeping() || player.getSleepingPos().isEmpty()) {
            NAPS.remove(player.getUUID());
            return;
        }
        // Coffin broken / no longer a bed -> wake with no time skip.
        BlockState headState = level.getBlockState(nap.headPos);
        if (!headState.isBed(level, nap.headPos, player)) {
            wake(player, NapWakeReason.DISTURBED);
            return;
        }

        // 2) Took damage -> disturbed, wake with no time skip. Cheap health-delta comparison.
        if (player.getHealth() < nap.lastHealth) {
            wake(player, NapWakeReason.DISTURBED);
            return;
        }
        nap.lastHealth = player.getHealth();

        // 2b) A proactive attacker (① of the relationship table) that WANDERED UP mid-nap wakes the zombie too, using
        // the exact same predicate as the right-click entry check (CoffinBlock.hasHostileNearby), throttled to once
        // per second so the per-napper AABB scan stays cheap. Entry and mid-nap therefore agree on "what's nearby".
        if ((level.getGameTime() - nap.startTick) % 20L == 0L && CoffinBlock.hasHostileNearby(level, player, nap.headPos)) {
            wake(player, NapWakeReason.DISTURBED);
            return;
        }

        // 3) Vote: count this dimension's eligible (non-spectator) zombies vs its deep-sleeping nappers.
        int eligible = countEligibleZombies(level);
        int deep = countDeepCoffinSleepers(level);
        int percentage = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (!ZombieSleepRules.enoughCoffinSleepers(deep, eligible, percentage)) {
            // Already deep-asleep but the vote is short and we have waited too long -> wake (anti-deadlock).
            if (player.isSleepingLongEnough() && level.getGameTime() - nap.startTick > DEEP_SLEEP_TICKS + MAX_WAIT_TICKS) {
                wake(player, NapWakeReason.NOT_ENOUGH_TIMEOUT);
                return;
            }
            if (ZombieSleepRules.shouldSendCoffinVoteProgress(level.getGameTime(), nap.startTick)) {
                int needed = ZombieSleepRules.coffinSleepersNeeded(eligible, percentage);
                player.sendSystemMessage(
                        Component.translatable(
                                ZombieSleepRules.coffinVoteProgressMessageKey(), deep, needed),
                        true);
            }
            return;
        }

        // 4) Enough zombies are deep-asleep -> advance to night and wake everyone in this dimension together. Clearing
        // the whole dimension's naps at once ensures the clock is only advanced a single time per vote.
        boolean skipped = advanceToNight(level);
        wakeAllInLevel(level, skipped);
        ZombieLog.debug(() -> "state.coffin_vote dimension="
                //? if >=1.21.11 {
                + level.dimension().identifier()
                //?} else {
                /*+ level.dimension().location()
                *///?}
                + " deep=" + deep + " eligible=" + eligible + " skipped=" + skipped);
    }

    private static int countEligibleZombies(ServerLevel level) {
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (ZombiePlayerGates.isZombiePlayer(p)) {
                n++;
            }
        }
        return n;
    }

    private static int countDeepCoffinSleepers(ServerLevel level) {
        int deep = 0;
        for (UUID id : NAPS.keySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p.level() == level && p.isSleepingLongEnough()) {
                deep++;
            }
        }
        return deep;
    }

    private static void wake(ServerPlayer player, NapWakeReason reason) {
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        NAPS.remove(player.getUUID());
        player.sendSystemMessage(
                Component.translatable(ZombieSleepRules.napWakeMessageKey(reason)), true);
    }

    private static void wakeAllInLevel(ServerLevel level, boolean skipped) {
        NapWakeReason reason = skipped ? NapWakeReason.VOTE_PASSED : NapWakeReason.VOTE_PASSED_NO_SKIP;
        for (UUID id : new ArrayList<>(NAPS.keySet())) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                NAPS.remove(id);
                continue;
            }
            if (p.level() != level) {
                continue;
            }
            if (p.isSleeping()) {
                p.stopSleeping();
            }
            NAPS.remove(id);
            p.sendSystemMessage(
                    Component.translatable(ZombieSleepRules.napWakeMessageKey(reason)), true);
            level.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
    }

    /**
     * Advance this dimension's clock to NIGHT. Returns false (and changes nothing) when there is no day-night clock,
     * {@code advance_time} is off, or (on 26.x) another mod cancels the
     * {@link EventHooks#onSleepFinished} adjustment. Legacy hooks may adjust the target tick.
     */
    private static boolean advanceToNight(ServerLevel level) {
        //? if >=26.1 {
        Optional<Holder<WorldClock>> defaultClock = level.dimensionType().defaultClock();
        if (defaultClock.isEmpty() || !level.getGameRules().get(GameRules.ADVANCE_TIME)) {
            return false;
        }
        ClockAdjustment adjustment = EventHooks.onSleepFinished(level, new ClockAdjustment.Marker(ClockTimeMarkers.NIGHT));
        if (adjustment == null) {
            return false;
        }
        adjustment.apply(level.clockManager(), defaultClock.get());
        if (level.getGameRules().get(GameRules.ADVANCE_WEATHER) && level.isRaining()) {
            level.resetWeatherCycle();
        }
        return true;
        //?} else {
        /*if (level.dimensionType().hasFixedTime()
                || !level.getGameRules().get(GameRules.ADVANCE_TIME)) {
            return false;
        }
        long current = level.getDayTime();
        long target = nextLegacyNight(current);
        long adjusted = EventHooks.onSleepFinished(level, target, current);
        level.setDayTime(adjusted);
        if (level.getGameRules().get(GameRules.ADVANCE_WEATHER) && level.isRaining()) {
            level.resetWeatherCycle();
        }
        return true;
        *///?}
    }

    //? if <26.1 {
    /*private static long nextLegacyNight(long current) {
        long target = current - Math.floorMod(current, 24000L) + LEGACY_NIGHT_TICK;
        return target <= current ? target + 24000L : target;
    }
    *///?}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop the disconnecting player's nap so it cannot leak into the map for the server's lifetime nor strand the
        // per-dimension vote (the offline player would otherwise still count as a non-deep nap entry).
        NAPS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NAPS.clear();
    }
}
