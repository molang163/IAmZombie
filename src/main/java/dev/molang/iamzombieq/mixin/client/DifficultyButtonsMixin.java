package dev.molang.iamzombieq.mixin.client;

import dev.molang.iamzombieq.gameplay.PeacefulGuard;
//? if >=26.1
import net.minecraft.client.gui.screens.options.DifficultyButtons;
//? if <26.1
//import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Peaceful unselectable in the IN-GAME difficulty cycle button. The World-Options screen builds that control
 * through {@code DifficultyButtons.create(...)} on 26.1+ and {@code OptionsScreen.createDifficultyButton(...)} on
 * earlier nodes. Both use {@code Difficulty.values()}, so this redirects that single call to the non-Peaceful set.
 * Both targets are static, so the handler is static too. Client-only (dedicated-server safe).
 *
 * <p>Woven code may call ordinary mod classes such as {@link PeacefulGuard}; it must not call a helper inside the
 * mod's mixin package, which would trigger Mixin's class-loading protection.
 */
//? if >=26.1
@Mixin(DifficultyButtons.class)
//? if <26.1
//@Mixin(OptionsScreen.class)
abstract class DifficultyButtonsMixin {
    @Redirect(
            //? if >=26.1
            method = "create",
            //? if <26.1
            //method = "createDifficultyButton",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"))
    private static Difficulty[] iamzombieq$onlyNonPeacefulDifficulties() {
        return PeacefulGuard.selectableDifficulties();
    }
}
