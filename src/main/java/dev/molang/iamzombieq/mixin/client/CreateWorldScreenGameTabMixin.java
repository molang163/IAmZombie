package dev.molang.iamzombieq.mixin.client;

import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Peaceful unselectable in the world-creation difficulty cycle button. The GameTab builds it with
 * {@code CycleButton.builder(...).withValues(Difficulty.values())}; we redirect that single {@code Difficulty.values()}
 * call to the non-Peaceful set so the button never offers Peaceful.
 *
 * <p>We redirect {@code Difficulty.values()} (called exactly once in the GameTab constructor) rather than the
 * {@code withValues(...)} arg, because the constructor builds MULTIPLE CycleButtons via {@code withValues}; redirecting
 * {@code Difficulty.values()} unambiguously targets only the difficulty button. {@code GameTab} is a private inner
 * class, so it is targeted by binary name via {@code targets}. Client-only (dedicated-server safe).
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
abstract class CreateWorldScreenGameTabMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"))
    private Difficulty[] iamzombieq$onlyNonPeacefulDifficulties() {
        // Inlined (not a shared helper): the redirect is woven into the vanilla GameTab, which cannot reference a
        // class in the mod's mixin package, so we build the non-Peaceful array here from the vanilla enum only.
        return new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
    }
}
