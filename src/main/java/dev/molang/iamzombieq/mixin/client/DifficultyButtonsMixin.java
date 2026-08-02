package dev.molang.iamzombieq.mixin.client;

import dev.molang.iamzombieq.gameplay.PeacefulGuard;
import net.minecraft.client.gui.screens.options.DifficultyButtons;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Peaceful unselectable in the IN-GAME difficulty cycle button. As of 26.1 the in-game difficulty control lives
 * in the World-Options screen, which builds its button via {@code DifficultyButtons.create(...)} using
 * {@code withValues(Difficulty.values())}; we redirect that single {@code Difficulty.values()} call to the
 * non-Peaceful set so the button never offers Peaceful. {@code create} is static, so the handler is static too.
 * Client-only (dedicated-server safe).
 *
 * <p>Woven code may call ordinary mod classes such as {@link PeacefulGuard}; it must not call a helper inside the
 * mod's mixin package, which would trigger Mixin's class-loading protection.
 */
@Mixin(DifficultyButtons.class)
abstract class DifficultyButtonsMixin {
    @Redirect(
            method = "create",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"))
    private static Difficulty[] iamzombieq$onlyNonPeacefulDifficulties() {
        return PeacefulGuard.selectableDifficulties();
    }
}
