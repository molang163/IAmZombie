package dev.molang.iamzombieq.rules.food;
import dev.molang.iamzombieq.rules.EffectSpec;

import java.util.List;

/**
 * A resolved zombie-food rule (tier + buffs/debuffs + flags). Stable public API (1.x): exposed via {@code api/*}
 * (e.g. {@code IFoodRuleProvider}, the eat event DTOs); backward-compatible additions only within 1.x.
 *
 * @since 1.0
 */
public record FoodRule(
        FoodTier tier,
        List<EffectSpec> buffs,
        List<EffectSpec> debuffs,
        boolean restoresBabyState,
        boolean suppressesVanillaPositiveEffects
) {
    public FoodRule {
        buffs = List.copyOf(buffs);
        debuffs = List.copyOf(debuffs);
    }

    /** T3 HUMAN_COOKED is the only tier that applies the human-food punishment (Hunger + Nausea). */
    public boolean appliesHumanFoodPunishment() {
        return tier == FoodTier.HUMAN_COOKED;
    }
}
