package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieForm;

public final class ZombieEvolutionRules {
    private ZombieEvolutionRules() {
    }

    public static EvolutionResult resolveDeath(ZombieState current, DeathTrigger trigger, BiomeContext biomeContext) {
        return resolveDeath(current, trigger, biomeContext, DimensionContext.OVERWORLD);
    }

    public static EvolutionResult resolveDeath(
            ZombieState current,
            DeathTrigger trigger,
            BiomeContext biomeContext,
            DimensionContext dimensionContext
    ) {
        if (current.size() == ZombieSize.ADULT
                && trigger == DeathTrigger.STARVATION
                && current.form() != ZombieForm.GIANT) {
            return EvolutionResult.evolved(
                    new ZombieState(current.form(), ZombieSize.BABY),
                    DeathOutcome.EVOLVE_TO_BABY
            );
        }

        // A husk that drowns reverts to an ordinary NORMAL zombie in place (preserving baby/adult size). This must be
        // checked BEFORE the NORMAL+DROWNING -> DROWNED case below so the husk de-evolves rather than being skipped.
        // The form change is an in-place respawn that keeps inventory/XP; it grants no first-evolution reward and no
        // evolution advancement, so ORDINARY_DEATH_RESET is the fitting outcome (its reward/advancement branches are
        // no-ops) while still flagging an in-place respawn so the death is canceled and the form is rewritten.
        if (current.form() == ZombieForm.HUSK && trigger == DeathTrigger.DROWNING) {
            return new EvolutionResult(
                    new ZombieState(ZombieForm.NORMAL, current.size()),
                    true,
                    true,
                    DeathOutcome.ORDINARY_DEATH_RESET
            );
        }

        // Form cross-transforms are gated on the NORMAL form only (any size) and preserve the current size, so a
        // baby zombie that drowns/sun-dies/lava-dies becomes a baby drowned/husk/zombified piglin rather than
        // falling through to an ordinary reset.
        if (current.form() == ZombieForm.NORMAL && trigger == DeathTrigger.DROWNING) {
            return EvolutionResult.evolved(
                    new ZombieState(ZombieForm.DROWNED, current.size()),
                    DeathOutcome.EVOLVE_TO_DROWNED
            );
        }

        if (current.form() == ZombieForm.NORMAL
                && trigger == DeathTrigger.SUNLIGHT
                && biomeContext == BiomeContext.DESERT) {
            return EvolutionResult.evolved(
                    new ZombieState(ZombieForm.HUSK, current.size()),
                    DeathOutcome.EVOLVE_TO_HUSK
            );
        }

        if (current.form() == ZombieForm.NORMAL
                && trigger == DeathTrigger.LAVA
                && dimensionContext == DimensionContext.NETHER) {
            return EvolutionResult.evolved(
                    new ZombieState(ZombieForm.ZOMBIFIED_PIGLIN, current.size()),
                    DeathOutcome.EVOLVE_TO_ZOMBIFIED_PIGLIN
            );
        }

        return EvolutionResult.ordinaryReset();
    }

    public static boolean canTransformFromGiantKill(boolean creativeMode, String killedEntityTypeId) {
        return creativeMode && "minecraft:giant".equals(killedEntityTypeId);
    }

    public static ZombieState giantStateAfterKill(ZombieState current) {
        return new ZombieState(ZombieForm.GIANT, ZombieSize.ADULT);
    }
}
