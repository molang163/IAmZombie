package dev.molang.iamzombieq.block;

import com.mojang.serialization.MapCodec;
import dev.molang.iamzombieq.gameplay.CoffinNapManager;
import dev.molang.iamzombieq.gameplay.IAmZombieAdvancements;
import dev.molang.iamzombieq.rules.sleep.SleepAction;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieMobTargetingRules;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CoffinBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CoffinBlock> CODEC = simpleCodec(CoffinBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    // Foot of the coffin is the low chest section; the head end is the raised "lid" section.
    private static final VoxelShape FOOT_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 7.0, 15.0);
    private static final VoxelShape HEAD_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0);
    private static final int DAY_END_TICK = 12000;

    public CoffinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, BedPart.FOOT)
                .setValue(OCCUPIED, false));
    }

    @Override
    public MapCodec<CoffinBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // Respawn/sleep logic operates on the HEAD half (mirrors vanilla bed respawn semantics).
        BlockPos headPos = pos;
        if (state.getValue(PART) != BedPart.HEAD) {
            headPos = pos.relative(state.getValue(FACING));
            BlockState headState = serverLevel.getBlockState(headPos);
            if (!headState.is(this)) {
                return InteractionResult.CONSUME;
            }
        }

        SleepAction action = ZombieSleepRules.useCoffin(isZombiePlayer(serverPlayer), hasHostileNearby(serverLevel, serverPlayer, headPos), canRestToNight(serverLevel));
        return switch (action) {
            case DENY_NOT_ZOMBIE -> {
                serverPlayer.sendOverlayMessage(Component.translatable("iamzombieq.message.coffin.zombie_only"));
                yield InteractionResult.SUCCESS_SERVER;
            }
            case DENY_HOSTILE_NEARBY -> {
                serverPlayer.sendOverlayMessage(Component.translatable("iamzombieq.message.coffin.not_safe"));
                yield InteractionResult.SUCCESS_SERVER;
            }
            case REST_UNTIL_NIGHT -> {
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.COFFIN);
                // Enter a real, multi-tick sleep: register the nap and let CoffinNapManager drive it (count the
                // deep-sleep timer, run the per-dimension vote, and advance the clock to night once enough zombies
                // have slept long enough). The time skip no longer happens instantly on right-click.
                if (CoffinNapManager.beginNap(serverLevel, serverPlayer, headPos)) {
                    serverPlayer.sendOverlayMessage(Component.translatable("iamzombieq.message.coffin.lying_down"));
                } else {
                    // Mounted / already sleeping / cannot lie down: fall back to just setting the respawn point.
                    setCoffinRespawn(serverLevel, serverPlayer, headPos);
                    serverPlayer.sendOverlayMessage(Component.translatable("iamzombieq.message.coffin.respawn_set_only"));
                }
                yield InteractionResult.SUCCESS_SERVER;
            }
            case SET_RESPAWN -> {
                // Night / no day-night clock: behave like a vanilla bed and only set the respawn point.
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.COFFIN);
                setCoffinRespawn(serverLevel, serverPlayer, headPos);
                serverLevel.playSound(null, headPos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                // Night (or a clockless dimension): night has already arrived / there is no night to wait for, so use
                // the neutral "respawn saved" line -- NOT respawn_set_only, whose "but night never came" is false here.
                serverPlayer.sendOverlayMessage(Component.translatable("iamzombieq.message.coffin.respawn_set"));
                yield InteractionResult.SUCCESS_SERVER;
            }
            case PASS_THROUGH, BED_EXPLODES -> InteractionResult.PASS;
        };
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    @Override
    public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type, LevelReader levelReader, BlockPos pos, float orientation) {
        Direction facing = state.getValue(FACING);
        return findCoffinStandUpPosition(type, levelReader, pos, facing, orientation)
                .map(standUpPos -> ServerPlayer.RespawnPosAngle.of(standUpPos, pos, 0.0F));
    }

    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
        level.setBlock(pos, state.setValue(OCCUPIED, occupied), 3);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        BlockPos headPos = pos.relative(facing);
        Level level = context.getLevel();
        if (level.getBlockState(headPos).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(headPos)) {
            return this.defaultBlockState().setValue(FACING, facing).setValue(PART, BedPart.FOOT);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        BlockPos headPos = pos.relative(state.getValue(FACING));
        level.setBlockAndUpdate(headPos, state.setValue(PART, BedPart.HEAD));
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    ) {
        if (directionToNeighbour == getNeighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            return neighbourState.is(this) && neighbourState.getValue(PART) != state.getValue(PART)
                    ? state.setValue(OCCUPIED, neighbourState.getValue(OCCUPIED))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BedPart part = state.getValue(PART);
            if (part == BedPart.FOOT) {
                BlockPos headPos = pos.relative(getNeighbourDirection(part, state.getValue(FACING)));
                BlockState headState = level.getBlockState(headPos);
                if (headState.is(this) && headState.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, headPos, Block.getId(headState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static Direction getNeighbourDirection(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == BedPart.HEAD ? HEAD_SHAPE : FOOT_SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    private static boolean isZombiePlayer(ServerPlayer player) {
        return !player.isSpectator();
    }

    // A zombie isn't scared of fellow undead — only of the creatures that PROACTIVELY attack the zombie player
    // (category ① "会主动攻击" of 我的世界_亡灵四生物关系_完整版: iron/snow golem, zoglin, goat, creeper, trader
    // llama [every form except zombified piglin], and the axolotl [drowned form only]). Form-awareness is taken from
    // the mod's authoritative ZombieMobTargetingRules, but the SET that blocks sleep is a coffin-owned PROACTIVE-only
    // whitelist (blocksCoffinSleep). Mere presence within range is enough ("周围有会主动攻击的生物就不能睡觉").
    //
    // We deliberately do NOT block on every kind that ZombieMobTargetingRules.attacksZombiePlayer returns true for,
    // because that predicate is also true for CONDITIONAL/provoked attackers — BOSS (Warden after vibration anger;
    // the Wither doesn't even attack undead) and PROVOKED_SELF_TARGETING (Enderman eye-contact, polar bear cub-defense).
    // Those only attack when provoked, so they must not keep a zombie from sleeping merely by being nearby; if one
    // actually strikes a napper, the CoffinNapManager damage path wakes it. The whitelist defaults to NOT blocking, so
    // any future MobKind the targeting matrix adds can never silently leak back into sleep-blocking. (Other players are
    // fellow zombies and are never scanned — Player is not a Mob.)
    public static boolean hasHostileNearby(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
        Vec3 center = Vec3.atBottomCenterOf(pos);
        AABB area = new AABB(center.x() - 8.0, center.y() - 5.0, center.z() - 8.0, center.x() + 8.0, center.y() + 5.0, center.z() + 8.0);
        // Predicate overload: only proactive attackers are collected — the list is empty in the common "nothing
        // nearby" case, so we test isEmpty() instead of building a list of every mob in range.
        return !level.getEntitiesOfClass(Mob.class, area, mob -> {
            if (isFriendlyGolem(mob)) {
                return false;
            }
            ZombieMobTargetingRules.MobKind kind = ZombieMobTargetingRules.classify(mob);
            return blocksCoffinSleep(kind) && ZombieMobTargetingRules.attacksZombiePlayer(kind, form);
        }).isEmpty();
    }

    // A player's OWN golems guard them and must never block their coffin sleep, so we exempt them at the Mob-instance
    // level (before the kind-based whitelist). A player-created iron golem is exempt even if it is momentarily angry
    // (e.g. retaliating after being hit) — by design, it's the player's own and isn't treated as a threat. Snow golems
    // have no wild variant (always player- or dispenser-built), so they are always friendly. WILD (non-player-created)
    // iron golems and genuinely hostile mobs still fall through to blocksCoffinSleep and keep blocking sleep.
    private static boolean isFriendlyGolem(Mob mob) {
        if (mob instanceof IronGolem ironGolem) {
            return ironGolem.isPlayerCreated();
        }
        return mob instanceof SnowGolem;
    }

    // Proactive / unconditional natural enemies whose mere presence makes a coffin unsafe to sleep in. Conditional /
    // provoked kinds (BOSS, PROVOKED_SELF_TARGETING) and IGNORED are NOT here, and `default -> false` keeps the coffin
    // decoupled from the shared targeting matrix: a new MobKind there won't block sleep until it is opted in here.
    private static boolean blocksCoffinSleep(ZombieMobTargetingRules.MobKind kind) {
        return switch (kind) {
            case IRON_GOLEM, SNOW_GOLEM, ZOGLIN, GOAT, CREEPER, TRADER_LLAMA, AXOLOTL -> true;
            default -> false;
        };
    }

    private static boolean canRestToNight(ServerLevel level) {
        if (level.dimensionType().defaultClock().isEmpty()) {
            return false;
        }
        long clockTime = Math.floorMod(level.getDefaultClockTime(), 24000L);
        return clockTime < DAY_END_TICK;
    }

    // Set the coffin as the player's respawn point, mirroring vanilla bed semantics (respawn is set the moment you
    // lie down). Exposed so CoffinNapManager can set it when a nap begins.
    public static void setCoffinRespawn(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ServerPlayer.RespawnConfig respawnConfig = new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(level.dimension(), pos, player.getYRot(), player.getXRot()),
                false
        );
        player.setRespawnPosition(respawnConfig, true);
    }

    private static Optional<Vec3> findCoffinStandUpPosition(EntityType<?> type, LevelReader level, BlockPos pos, Direction forward, float yaw) {
        Direction right = forward.getClockWise();
        Direction side = right.isFacingAngle(yaw) ? right.getOpposite() : right;
        int[][] offsets = {
                {side.getStepX(), side.getStepZ()},
                {-side.getStepX(), -side.getStepZ()},
                {forward.getStepX(), forward.getStepZ()},
                {-forward.getStepX(), -forward.getStepZ()},
                {side.getStepX() + forward.getStepX(), side.getStepZ() + forward.getStepZ()},
                {-side.getStepX() + forward.getStepX(), -side.getStepZ() + forward.getStepZ()}
        };

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] offset : offsets) {
            cursor.set(pos.getX() + offset[0], pos.getY(), pos.getZ() + offset[1]);
            Vec3 standUp = DismountHelper.findSafeDismountLocation(type, level, cursor, true);
            if (standUp != null) {
                return Optional.of(standUp);
            }
        }

        return BedBlock.findStandUpPosition(type, level, pos, forward, yaw);
    }
}
