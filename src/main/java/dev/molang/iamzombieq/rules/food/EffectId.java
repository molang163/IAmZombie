package dev.molang.iamzombieq.rules.food;

/**
 * A Minecraft-free description of a single mob effect to grant: the effect's registry id string plus its duration
 * and amplifier. This is the data form the {@link ZombieFoodRules} explicit food table stores so the rules class can
 * stay loadable (and JUnit-testable) without any Minecraft/NeoForge class on the classpath. The events layer converts
 * each {@code EffectId} into a live {@code EffectSpec} (a {@code Holder<MobEffect>} + duration + amplifier) via an
 * effect-resolver function passed into {@link ZombieFoodRules#ruleFor} / {@link ZombieFoodRules#ruleForStack}.
 *
 * @param effectId      the effect's registry id as a good-form {@code namespace:path} string (e.g. {@code "minecraft:strength"})
 * @param durationTicks the effect duration in ticks (clamped to {@code >= 0})
 * @param amplifier     the effect amplifier / level index (clamped to {@code >= 0})
 */
public record EffectId(String effectId, int durationTicks, int amplifier) {

    /** Clamp construction: negative duration/amplifier become zero (mirrors {@code EffectSpec.of}). */
    public static EffectId of(String effectId, int durationTicks, int amplifier) {
        return new EffectId(effectId, Math.max(0, durationTicks), Math.max(0, amplifier));
    }
}
