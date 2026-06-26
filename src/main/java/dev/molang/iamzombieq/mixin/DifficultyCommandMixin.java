package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.gameplay.PeacefulGuard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects {@code /difficulty peaceful}. The UI buttons are already filtered (see the client mixins), but the command
 * bypasses them, so this server-side mixin cancels the command before it applies and tells the sender why. Verified
 * against the decompiled 26.2 jar: {@code DifficultyCommand.setDifficulty} calls {@code MinecraftServer.setDifficulty},
 * which does NOT fire NeoForge's {@code DifficultyChangeEvent} — so an event handler wouldn't work; intercepting the
 * command directly is the reliable point. Server-side command logic (dedicated-server safe).
 */
@Mixin(DifficultyCommand.class)
abstract class DifficultyCommandMixin {
    @Inject(
            method = "setDifficulty(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/Difficulty;)I",
            at = @At("HEAD"),
            cancellable = true)
    private static void iamzombieq$rejectPeaceful(CommandSourceStack source, Difficulty difficulty,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (PeacefulGuard.isForbidden(difficulty)) {
            source.sendFailure(Component.translatable("iamzombieq.message.peaceful_rejected"));
            cir.setReturnValue(0);
        }
    }
}
