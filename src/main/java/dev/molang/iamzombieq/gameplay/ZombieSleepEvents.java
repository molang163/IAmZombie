package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.rules.sleep.SleepAction;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ZombieSleepEvents {
    private ZombieSleepEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        SleepAction action = ZombieSleepRules.useBed(isZombiePlayer(player));
        if (action != SleepAction.BED_EXPLODES) {
            return;
        }

        Level level = player.level();
        BlockPos clickedPos = event.getPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!(clickedState.getBlock() instanceof BedBlock)) {
            return;
        }

        // Only the "use the bed" gesture explodes; a sneaking player placing a block against the bed should still place it.
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);

        if (!level.isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.BED);
            }
            explodeBed(level, clickedPos, clickedState);
        }
    }

    private static boolean isZombiePlayer(Player player) {
        return !player.isSpectator();
    }

    private static void explodeBed(Level level, BlockPos clickedPos, BlockState clickedState) {
        BlockPos headPos = clickedState.getValue(BedBlock.PART) == BedPart.HEAD
                ? clickedPos
                : clickedPos.relative(clickedState.getValue(BedBlock.FACING));
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return;
        }

        level.removeBlock(headPos, false);
        BlockPos footPos = headPos.relative(headState.getValue(BedBlock.FACING).getOpposite());
        if (level.getBlockState(footPos).getBlock() instanceof BedBlock) {
            level.removeBlock(footPos, false);
        }

        Vec3 boomPos = Vec3.atCenterOf(headPos);
        ZombieSleepRules.BedExplosionSettings settings = ZombieSleepRules.bedExplosionSettings(
                IAmZombieConfig.BED_EXPLOSION_POWER.get().floatValue(),
                IAmZombieConfig.BED_EXPLOSION_CAUSES_FIRE.get()
        );
        level.explode(null, level.damageSources().badRespawnPointExplosion(boomPos), null, boomPos, settings.power(), settings.causesFire(), Level.ExplosionInteraction.BLOCK);
    }
}
