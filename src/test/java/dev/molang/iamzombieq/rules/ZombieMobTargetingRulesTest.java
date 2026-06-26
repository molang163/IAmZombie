package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.rules.ZombieMobTargetingRules.MobKind;
import org.junit.jupiter.api.Test;

class ZombieMobTargetingRulesTest {
    private static boolean attacks(MobKind kind, ZombieForm form) {
        return ZombieMobTargetingRules.attacksZombiePlayer(kind, form);
    }

    @Test
    void ironGolemAttacksEveryFormEvenThroughTheCrudeDisguise() {
        // The crude disguise mask no longer fools the iron golem (per user); it attacks in every form.
        for (ZombieForm form : ZombieForm.values()) {
            assertTrue(attacks(MobKind.IRON_GOLEM, form), "iron golem hunts the zombie player in " + form);
        }
    }

    @Test
    void snowGolemZoglinGoatCreeperAndBossesAttackEveryForm() {
        // SnowGolem/Zoglin/Goat/Creeper attack on sight; BOSS (Warden + Wither) is allowed in every form so the
        // deny-list never interferes with their own vanilla targeting (they are not force-seeded).
        for (ZombieForm form : ZombieForm.values()) {
            for (MobKind kind : new MobKind[] {MobKind.SNOW_GOLEM, MobKind.ZOGLIN, MobKind.GOAT, MobKind.CREEPER, MobKind.BOSS}) {
                assertTrue(attacks(kind, form), kind + " should attack the zombie player in " + form);
            }
        }
    }

    @Test
    void endermanAndPolarBearSelfTargetIsNeverCancelled() {
        // Provoked-only mobs (Enderman eye-contact, polar bear cub-defense) set their target directly WITHOUT
        // persistent anger, so angeredNeutral misses them; modelled as PROVOKED_SELF_TARGETING = never cancelled,
        // never force-seeded (they acquire the player on their own only when provoked).
        for (ZombieForm form : ZombieForm.values()) {
            assertTrue(attacks(MobKind.PROVOKED_SELF_TARGETING, form),
                    "Enderman/polar bear self-acquired target must stand in " + form);
            assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.PROVOKED_SELF_TARGETING, form, false, false),
                    "their self-acquired (provoked) target is never cancelled in " + form);
        }
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.PROVOKED_SELF_TARGETING),
                "Enderman/polar bear acquire the player on their own; never force-seeded");
    }

    @Test
    void traderLlamaAttacksEveryFormExceptZombifiedPiglin() {
        assertFalse(attacks(MobKind.TRADER_LLAMA, ZombieForm.ZOMBIFIED_PIGLIN), "trader llama does not spit at a zombified piglin");
        assertTrue(attacks(MobKind.TRADER_LLAMA, ZombieForm.NORMAL));
        assertTrue(attacks(MobKind.TRADER_LLAMA, ZombieForm.DROWNED));
        assertTrue(attacks(MobKind.TRADER_LLAMA, ZombieForm.HUSK));
        assertTrue(attacks(MobKind.TRADER_LLAMA, ZombieForm.GIANT), "the giant (a zombie) is still spat at");
    }

    @Test
    void axolotlAttacksOnlyTheDrownedForm() {
        assertTrue(attacks(MobKind.AXOLOTL, ZombieForm.DROWNED));
        for (ZombieForm form : ZombieForm.values()) {
            if (form != ZombieForm.DROWNED) {
                assertFalse(attacks(MobKind.AXOLOTL, form), "axolotl ignores the non-drowned " + form);
            }
        }
    }

    @Test
    void everyOtherMobIgnoresTheZombiePlayer() {
        // Fellow monsters (zombie/skeleton/spider/…) and passive animals all classify IGNORED. (Enderman + polar
        // bear are NOT here — they are PROVOKED_SELF_TARGETING so their provoked target stands.)
        for (ZombieForm form : ZombieForm.values()) {
            assertFalse(attacks(MobKind.IGNORED, form), "an IGNORED mob never attacks the zombie player in " + form);
        }
    }

    @Test
    void onlyGolemsLlamaAndAxolotlNeedActiveSeeding() {
        // These do not naturally target a Player, so the handler must point them at the player.
        assertTrue(ZombieMobTargetingRules.needsActiveSeeding(MobKind.IRON_GOLEM));
        assertTrue(ZombieMobTargetingRules.needsActiveSeeding(MobKind.SNOW_GOLEM));
        assertTrue(ZombieMobTargetingRules.needsActiveSeeding(MobKind.TRADER_LLAMA));
        assertTrue(ZombieMobTargetingRules.needsActiveSeeding(MobKind.AXOLOTL));
        // Creeper/zoglin acquire the player via their own AI; the goat rams via its brain; the bosses self-target.
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.CREEPER));
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.ZOGLIN));
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.GOAT));
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.BOSS));
        assertFalse(ZombieMobTargetingRules.needsActiveSeeding(MobKind.IGNORED));
    }

    @Test
    void retaliationAndAngerOverrideEveryIgnore() {
        // A mob the player just struck, or an angered neutral mob, fights back even when it would otherwise ignore.
        assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, ZombieForm.NORMAL, true, false));
        assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, ZombieForm.ZOMBIFIED_PIGLIN, false, true));
    }

    @Test
    void shouldIgnoreIsTheInverseOfAttacksWhenNotProvoked() {
        assertTrue(ZombieMobTargetingRules.shouldIgnore(MobKind.IGNORED, ZombieForm.NORMAL, false, false));
        assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.CREEPER, ZombieForm.NORMAL, false, false));
        assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.IRON_GOLEM, ZombieForm.NORMAL, false, false),
                "iron golem hunts the zombie player (disguise no longer matters)");
        assertTrue(ZombieMobTargetingRules.shouldIgnore(MobKind.AXOLOTL, ZombieForm.HUSK, false, false),
                "the axolotl ignores a husk-form player");
        assertFalse(ZombieMobTargetingRules.shouldIgnore(MobKind.AXOLOTL, ZombieForm.DROWNED, false, false),
                "the axolotl hunts a drowned-form player");
    }

    // N9 (drowned trident friendly-fire) takes Minecraft LivingEntity args and is covered by the live handler;
    // it is not unit-testable in this pure (Minecraft-free) rules test sourceset.
}
