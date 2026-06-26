package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.gameplay.PeacefulGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The single server-side "forbid Peaceful" chokepoint. Every runtime difficulty change routes through
 * {@code MinecraftServer.setDifficulty(Difficulty, boolean)} — the {@code /difficulty} command, the client
 * change-difficulty packet / open-to-LAN settings, and a dedicated server applying {@code server.properties}.
 * Coercing the argument to Easy here makes Peaceful unreachable at runtime regardless of its source.
 *
 * <p>The {@code /difficulty} command also gets a friendly rejection message ({@link DifficultyCommandMixin}) before it
 * reaches this point; this mixin is the catch-all backstop for every other path. Server-side common mixin
 * (dedicated-server safe; {@code MinecraftServer} is a server class).
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @ModifyVariable(
            method = "setDifficulty(Lnet/minecraft/world/Difficulty;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Difficulty iamzombieq$forbidPeaceful(Difficulty difficulty) {
        return PeacefulGuard.sanitize(difficulty);
    }
}
