package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieEvolutionRulesTest {
    @Test
    void drowningAsAdultNormalZombieEvolvesIntoDrownedInPlace() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.DROWNED, ZombieSize.ADULT), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.EVOLVE_TO_DROWNED, result.outcome());
    }

    @Test
    void sunlightDeathInDesertAsAdultNormalZombieEvolvesIntoHuskInPlace() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.SUNLIGHT, BiomeContext.DESERT);

        assertEquals(new ZombieState(ZombieForm.HUSK, ZombieSize.ADULT), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.EVOLVE_TO_HUSK, result.outcome());
    }

    @Test
    void sunlightDeathInHotDryNonDesertBiomeDoesNotEvolveIntoHusk() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.SUNLIGHT, BiomeContext.HOT_DRY_NON_DESERT);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertFalse(result.inPlaceRespawn());
        assertFalse(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void lavaDeathInNetherAsAdultNormalZombieEvolvesIntoZombifiedPiglinInPlace() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(
                before,
                DeathTrigger.LAVA,
                BiomeContext.OTHER,
                DimensionContext.NETHER
        );

        assertEquals(new ZombieState(ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.EVOLVE_TO_ZOMBIFIED_PIGLIN, result.outcome());
    }

    @Test
    void lavaDeathOutsideNetherDoesNotEvolveIntoZombifiedPiglin() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(
                before,
                DeathTrigger.LAVA,
                BiomeContext.OTHER,
                DimensionContext.OVERWORLD
        );

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertFalse(result.inPlaceRespawn());
        assertFalse(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void babyNormalZombieDrowningEvolvesIntoBabyDrownedPreservingSize() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.DROWNED, ZombieSize.BABY), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertEquals(DeathOutcome.EVOLVE_TO_DROWNED, result.outcome());
    }

    @Test
    void adultHuskDrowningRevertsToNormalZombieInPlacePreservingAdultSize() {
        ZombieState before = new ZombieState(ZombieForm.HUSK, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void babyHuskDrowningRevertsToNormalZombieInPlacePreservingBabySize() {
        ZombieState before = new ZombieState(ZombieForm.HUSK, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void babyNormalZombieSunDeathInDesertEvolvesIntoBabyHuskPreservingSize() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.SUNLIGHT, BiomeContext.DESERT);

        assertEquals(new ZombieState(ZombieForm.HUSK, ZombieSize.BABY), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertEquals(DeathOutcome.EVOLVE_TO_HUSK, result.outcome());
    }

    @Test
    void babyNormalZombieLavaDeathInNetherEvolvesIntoBabyZombifiedPiglinPreservingSize() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(
                before,
                DeathTrigger.LAVA,
                BiomeContext.OTHER,
                DimensionContext.NETHER
        );

        assertEquals(new ZombieState(ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.BABY), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertEquals(DeathOutcome.EVOLVE_TO_ZOMBIFIED_PIGLIN, result.outcome());
    }

    @Test
    void adultNormalZombieStillCrossTransformsPreservingAdultSize() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        assertEquals(
                new ZombieState(ZombieForm.DROWNED, ZombieSize.ADULT),
                ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER).nextState());
    }

    @Test
    void nonNormalFormDoesNotCrossTransformAgain() {
        // A drowned zombie that drowns again does not "re-evolve"; form cross-transforms are gated on NORMAL only.
        ZombieState before = new ZombieState(ZombieForm.DROWNED, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.DROWNING, BiomeContext.OTHER);

        assertFalse(result.inPlaceRespawn());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void starvationAsAdultKeepsMainFormButTurnsBabyInPlace() {
        ZombieState before = new ZombieState(ZombieForm.HUSK, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.STARVATION, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.HUSK, ZombieSize.BABY), result.nextState());
        assertTrue(result.inPlaceRespawn());
        assertTrue(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.EVOLVE_TO_BABY, result.outcome());
    }

    @Test
    void starvationAsGiantIsOrdinaryDeathAndDoesNotProduceBabyGiant() {
        ZombieState before = new ZombieState(ZombieForm.GIANT, ZombieSize.ADULT);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.STARVATION, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertFalse(result.inPlaceRespawn());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void starvationAsBabyIsOrdinaryDeathAndResetsToNormalZombie() {
        ZombieState before = new ZombieState(ZombieForm.DROWNED, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.STARVATION, BiomeContext.OTHER);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertFalse(result.inPlaceRespawn());
        assertFalse(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void unrelatedDeathAlwaysUsesVanillaRespawnAndResetsForm() {
        ZombieState before = new ZombieState(ZombieForm.HUSK, ZombieSize.BABY);

        EvolutionResult result = ZombieEvolutionRules.resolveDeath(before, DeathTrigger.OTHER, BiomeContext.DESERT);

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), result.nextState());
        assertFalse(result.inPlaceRespawn());
        assertFalse(result.keepInventoryAndExperience());
        assertEquals(DeathOutcome.ORDINARY_DEATH_RESET, result.outcome());
    }

    @Test
    void creativePlayerKillingVanillaGiantCanTransformIntoGiantForm() {
        ZombieState before = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

        assertTrue(ZombieEvolutionRules.canTransformFromGiantKill(true, "minecraft:giant"));
        assertEquals(new ZombieState(ZombieForm.GIANT, ZombieSize.ADULT), ZombieEvolutionRules.giantStateAfterKill(before));
    }

    @Test
    void survivalPlayerKillingGiantDoesNotUnlockGiantForm() {
        assertFalse(ZombieEvolutionRules.canTransformFromGiantKill(false, "minecraft:giant"));
        assertFalse(ZombieEvolutionRules.canTransformFromGiantKill(true, "minecraft:zombie"));
    }
}
