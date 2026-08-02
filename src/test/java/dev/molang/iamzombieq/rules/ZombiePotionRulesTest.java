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
        // Creative zombie players also use undead inversion, matching survival behavior;
        // only spectators and non-players keep vanilla instant-potion logic.
        assertTrue(ZombiePotionRules.shouldInvertHealAndHarm(true, true, false), "creative zombie players invert heal and harm");
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(true, false, true));
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, false, false));
    }
}
