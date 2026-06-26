package dev.molang.iamzombieq.mixin.client;

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
 */
@Mixin(DifficultyButtons.class)
abstract class DifficultyButtonsMixin {
    @Redirect(
            method = "create",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"))
    private static Difficulty[] iamzombieq$onlyNonPeacefulDifficulties() {
        // Inlined (not a shared helper): the redirect is woven into vanilla DifficultyButtons, which cannot reference a
        // class in the mod's mixin package, so we build the non-Peaceful array here from the vanilla enum only.
        return new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
    }
}
