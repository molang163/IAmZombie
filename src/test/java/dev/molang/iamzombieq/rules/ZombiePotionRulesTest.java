package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombiePotionRulesTest {
    @Test
    void survivalAndAdventurePlayersUseUndeadInstantPotionLogic() {
        assertTrue(ZombiePotionRules.shouldInvertHealAndHarm(true, false, false));
    }

    @Test
    void spectatorsAndNonPlayersKeepVanillaInstantPotionLogic() {
        // N6: creative zombie players now also use undead inversion (creative rules align with survival);
        // only spectators and non-players keep vanilla instant-potion logic.
        assertTrue(ZombiePotionRules.shouldInvertHealAndHarm(true, true, false), "creative zombie players invert (N6)");
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(true, false, true));
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, false, false));
    }
}
