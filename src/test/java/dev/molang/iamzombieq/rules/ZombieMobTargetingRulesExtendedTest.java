package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.rules.ZombieMobTargetingRules.MobKind;
import org.junit.jupiter.api.Test;

/**
 * Committed-contract matrix coverage for {@link ZombieMobTargetingRules}, a NEW sibling to
 * {@link ZombieMobTargetingRulesTest} (which it does not modify). The attacker table is owned/evolved by another area,
 * so this asserts only the stable, registry-free decision contract:
 *
 * <ul>
 *   <li>{@code attacksZombiePlayer}: IRON_GOLEM/SNOW_GOLEM/ZOGLIN/GOAT/CREEPER/BOSS attack every form; TRADER_LLAMA every
 *       form except ZOMBIFIED_PIGLIN; AXOLOTL only DROWNED; IGNORED never.</li>
 *   <li>{@code shouldIgnore} is the inverse of {@code attacksZombiePlayer} UNLESS retaliating or neutral-anger overrides
 *       force the fight.</li>
 * </ul>
 *
 * All calls are pure MobKind/ZombieForm enum logic, runnable on the JUnit-only test classpath (no live mobs).
 */
class ZombieMobTargetingRulesExtendedTest {

    /** The committed expected result of attacksZombiePlayer, recomputed independently of the production switch. */
    private static boolean expectedAttacks(MobKind kind, ZombieForm form) {
        return switch (kind) {
            case IRON_GOLEM, SNOW_GOLEM, ZOGLIN, GOAT, CREEPER, BOSS, PROVOKED_SELF_TARGETING -> true;
            case TRADER_LLAMA -> form != ZombieForm.ZOMBIFIED_PIGLIN;
            case AXOLOTL -> form == ZombieForm.DROWNED;
            case IGNORED -> false;
        };
    }

    @Test
    void ironGolemAttacksTheZombiePlayerInEveryForm() {
        for (ZombieForm form : ZombieForm.values()) {
            assertTrue(ZombieMobTargetingRules.attacksZombiePlayer(MobKind.IRON_GOLEM, form),
                    "the iron golem attacks the zombie player in " + form);
        }
    }

    @Test
    void axolotlAttacksOnlyTheDrownedForm() {
        for (ZombieForm form : ZombieForm.values()) {
            boolean expected = form == ZombieForm.DROWNED;
            assertEquals(expected, ZombieMobTargetingRules.attacksZombiePlayer(MobKind.AXOLOTL, form),
                    "the axolotl attacks only the drowned form (" + form + ")");
        }
    }

    @Test
    void traderLlamaAttacksEveryFormExceptZombifiedPiglin() {
        for (ZombieForm form : ZombieForm.values()) {
            boolean expected = form != ZombieForm.ZOMBIFIED_PIGLIN;
            assertEquals(expected, ZombieMobTargetingRules.attacksZombiePlayer(MobKind.TRADER_LLAMA, form),
                    "the trader llama spits at every form except the zombified piglin (" + form + ")");
        }
    }

    @Test
    void attacksZombiePlayerMatchesTheCommittedTableForEveryKindFormPair() {
        for (MobKind kind : MobKind.values()) {
            for (ZombieForm form : ZombieForm.values()) {
                assertEquals(expectedAttacks(kind, form),
                        ZombieMobTargetingRules.attacksZombiePlayer(kind, form),
                        "attacksZombiePlayer(" + kind + ", " + form + ")");
            }
        }
    }

    @Test
    void anIgnoredKindNeverAttacksUnlessProvoked() {
        for (ZombieForm form : ZombieForm.values()) {
            // Unprovoked: an IGNORED mob attacks nothing, so it is ignored (target denied).
            assertFalse(ZombieMobTargetingRules.attacksZombiePlayer(MobKind.IGNORED, form),
                    "an IGNORED mob never attacks the zombie player in " + form);
            assertTrue(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, form, false, false),
                    "an unprovoked IGNORED mob is denied the target in " + form);
            // Retaliation overrides the ignore so a genuine struck-back fight resolves.
            assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, form, true, false),
                    "retaliation lets an IGNORED mob fight back in " + form);
            // Neutral anger likewise overrides.
            assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, form, false, true),
                    "neutral anger lets an IGNORED mob fight in " + form);
        }
    }

    @Test
    void shouldIgnoreIsTheInverseOfAttacksWhenNeitherOverrideApplies() {
        for (MobKind kind : MobKind.values()) {
            for (ZombieForm form : ZombieForm.values()) {
                boolean ignored = ZombieMobTargetingRules.shouldIgnore(kind, form, false, false);
                assertEquals(!ZombieMobTargetingRules.attacksZombiePlayer(kind, form), ignored,
                        "without overrides, shouldIgnore is !attacksZombiePlayer (" + kind + ", " + form + ")");
            }
        }
    }

    @Test
    void retaliationOrAngerAlwaysClearsTheIgnoreAcrossTheWholeMatrix() {
        for (MobKind kind : MobKind.values()) {
            for (ZombieForm form : ZombieForm.values()) {
                assertFalse(ZombieMobTargetingRules.shouldIgnore(kind, form, true, false),
                        "retaliation always allows the fight (" + kind + ", " + form + ")");
                assertFalse(ZombieMobTargetingRules.shouldIgnore(kind, form, false, true),
                        "neutral anger always allows the fight (" + kind + ", " + form + ")");
                assertFalse(ZombieMobTargetingRules.shouldIgnore(kind, form, true, true),
                        "both overrides at once still allows the fight (" + kind + ", " + form + ")");
            }
        }
    }
}
