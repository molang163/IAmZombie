package dev.molang.iamzombieq.rules;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A single mob-effect specification (effect + duration + amplifier) carried by a {@code FoodRule}. Stable public
 * API (1.x): exposed via {@code api/*} (through {@code FoodRule} on the food extension hook + eat event DTOs);
 * backward-compatible additions only within 1.x.
 *
 * @since 1.0
 */
public record EffectSpec(Holder<MobEffect> effect, int durationTicks, int amplifier) {

    /** Clamp construction: negative duration/amplifier become zero. */
    public static EffectSpec of(Holder<MobEffect> effect, int durationTicks, int amplifier) {
        return new EffectSpec(effect, Math.max(0, durationTicks), Math.max(0, amplifier));
    }

    public MobEffectInstance toInstance() {
        return new MobEffectInstance(effect, durationTicks, amplifier);
    }
}
