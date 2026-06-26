package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Full truth-table coverage for {@link ZombiePotionRules#shouldInvertHealAndHarm}, complementing
 * {@link ZombiePotionRulesTest}.
 *
 * <p>Signature is {@code shouldInvertHealAndHarm(boolean isPlayer, boolean isCreative, boolean isSpectator)} and the
 * committed contract (N6) is {@code isPlayer && !isSpectator}: a zombie player inverts heal/harm in EVERY game mode
 * (survival, adventure, creative) and only a spectator or a non-player keeps vanilla instant-potion behaviour. The
 * {@code isCreative} parameter is retained for call-site stability but no longer gates the result. Pure booleans, fully
 * runnable on the JUnit-only classpath.
 */
class ZombiePotionRulesExtendedTest {

    @Test
    void survivalOrAdventureZombiePlayerInvertsHealAndHarm() {
        // isPlayer=true, isCreative=false, isSpectator=false.
        assertTrue(ZombiePotionRules.shouldInvertHealAndHarm(true, false, false),
                "a non-creative, non-spectator zombie player inverts heal/harm");
    }

    @Test
    void creativeZombiePlayerStillInvertsHealAndHarm() {
        // N6: creative aligns with survival; isCreative no longer suppresses the inversion.
        assertTrue(ZombiePotionRules.shouldInvertHealAndHarm(true, true, false),
                "a creative zombie player still inverts heal/harm (N6)");
    }

    @Test
    void spectatorZombiePlayerKeepsVanillaInstantPotionLogic() {
        // A spectator is excluded in both creative and non-creative variants.
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(true, false, true),
                "a spectator player does not invert");
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(true, true, true),
                "a creative spectator player does not invert either");
    }

    @Test
    void nonPlayerEntityNeverInverts() {
        // A non-player undead mob is handled by vanilla undead logic, not this player-gated rule.
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, false, false));
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, true, false));
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, false, true));
        assertFalse(ZombiePotionRules.shouldInvertHealAndHarm(false, true, true));
    }

    @Test
    void creativeFlagDoesNotChangeTheResultForAnyPlayerSpectatorCombination() {
        // The result depends only on isPlayer and isSpectator; isCreative is contractually inert.
        for (boolean isPlayer : new boolean[] {true, false}) {
            for (boolean isSpectator : new boolean[] {true, false}) {
                boolean withCreative = ZombiePotionRules.shouldInvertHealAndHarm(isPlayer, true, isSpectator);
                boolean withoutCreative = ZombiePotionRules.shouldInvertHealAndHarm(isPlayer, false, isSpectator);
                assertEquals(withoutCreative, withCreative,
                        "isCreative must not change the result (isPlayer=" + isPlayer + ", isSpectator=" + isSpectator + ")");
                assertEquals(isPlayer && !isSpectator, withCreative,
                        "result must equal isPlayer && !isSpectator");
            }
        }
    }
}
