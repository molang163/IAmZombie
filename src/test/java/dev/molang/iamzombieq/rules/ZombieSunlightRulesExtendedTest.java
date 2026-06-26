package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive matrix coverage for {@link ZombieSunlightRules}, complementing {@link ZombieSunlightRulesTest}.
 *
 * <p>{@code shouldBurn} == {@code exposedToSun && (form is NORMAL or DROWNED) && protection == NONE}. Only NORMAL and
 * DROWNED burn; any head protection (PUMPKIN/STEVE_HEAD/OTHER_HELMET) blocks it; no sky exposure never burns. The
 * {@code isVanillaSunBurnTick} formula is {@code monstersBurn && brightness > 0.5 && random*30 < (brightness-0.4)*2 &&
 * !inWaterRainOrPowderSnow && canSeeSky}. All pure enum/primitive calls, runnable on the JUnit-only classpath.
 */
class ZombieSunlightRulesExtendedTest {
    private static final Set<ZombieForm> BURNING_FORMS = EnumSet.of(ZombieForm.NORMAL, ZombieForm.DROWNED);

    @Test
    void shouldBurnAcrossEveryFormExposureAndProtectionCombination() {
        for (ZombieForm form : ZombieForm.values()) {
            for (boolean exposed : new boolean[] {true, false}) {
                for (HeadProtection protection : HeadProtection.values()) {
                    boolean expected = exposed
                            && BURNING_FORMS.contains(form)
                            && protection == HeadProtection.NONE;
                    assertEquals(expected, ZombieSunlightRules.shouldBurn(form, exposed, protection),
                            "shouldBurn(" + form + ", exposed=" + exposed + ", " + protection + ")");
                }
            }
        }
    }

    @Test
    void onlyNormalAndDrownedAreSunVulnerableForms() {
        // Pinned explicitly so a future form added to the burning set is caught here, not just silently in the matrix.
        assertTrue(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, HeadProtection.NONE));
        assertTrue(ZombieSunlightRules.shouldBurn(ZombieForm.DROWNED, true, HeadProtection.NONE));
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.HUSK, true, HeadProtection.NONE));
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.ZOMBIFIED_PIGLIN, true, HeadProtection.NONE));
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.GIANT, true, HeadProtection.NONE),
                "the giant does not burn in sunlight");
    }

    @Test
    void everyNonNoneHeadProtectionBlocksBurningOnASunVulnerableForm() {
        for (HeadProtection protection : HeadProtection.values()) {
            boolean burns = ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, protection);
            if (protection == HeadProtection.NONE) {
                assertTrue(burns, "an unprotected NORMAL zombie burns");
            } else {
                assertFalse(burns, protection + " should block sunlight burning");
            }
            assertEquals(protection != HeadProtection.NONE, protection.protectsFromSun(),
                    protection + ".protectsFromSun()");
        }
    }

    @Test
    void noSkyExposureNeverBurnsRegardlessOfFormOrProtection() {
        for (ZombieForm form : ZombieForm.values()) {
            for (HeadProtection protection : HeadProtection.values()) {
                assertFalse(ZombieSunlightRules.shouldBurn(form, false, protection),
                        "no sky exposure must never burn (" + form + ", " + protection + ")");
            }
        }
    }

    @Test
    void shouldDamageHeadProtectionOnlyWhenAProtectedSunVulnerableFormIsExposed() {
        for (ZombieForm form : ZombieForm.values()) {
            for (boolean exposed : new boolean[] {true, false}) {
                for (HeadProtection protection : HeadProtection.values()) {
                    boolean expected = exposed
                            && BURNING_FORMS.contains(form)
                            && protection != HeadProtection.NONE;
                    assertEquals(expected, ZombieSunlightRules.shouldDamageHeadProtection(form, exposed, protection),
                            "shouldDamageHeadProtection(" + form + ", exposed=" + exposed + ", " + protection + ")");
                }
            }
        }
    }

    // ---- isVanillaSunBurnTick boundary precision ----

    @Test
    void vanillaSunBurnTickNeedsMonstersBurningEnabled() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(false, 1.0F, 0.0F, true, false),
                "monsters-don't-burn disables the burn tick even in full sun");
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, true, false));
    }

    @Test
    void vanillaSunBurnTickBrightnessBoundaryIsStrictlyAboveHalf() {
        // brightness > 0.5F (strict): 0.5 fails, the next representable float above 0.5 passes.
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 0.5F, 0.0F, true, false), "brightness == 0.5 fails");
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, Math.nextUp(0.5F), 0.0F, true, false),
                "the smallest float above 0.5 passes the brightness gate");
    }

    @Test
    void vanillaSunBurnTickRandomBoundaryMatchesVanillaFormula() {
        // Burn requires random*30 < (brightness-0.4F)*2.0F. At brightness 1.0F the right-hand side is exactly
        // (1.0F-0.4F)*2.0F == 1.2000000476837158 (float imprecision), so the bound on the roll is ~0.0400000016.
        float brightness = 1.0F;
        // Compute the exact bound the production code uses, so the test tracks the real (imprecise) float arithmetic.
        double bound = ((brightness - 0.4F) * 2.0F) / 30.0F;
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, brightness, 0.039F, true, false),
                "a roll clearly below the bound burns");
        // A roll just under the bound burns; the same roll nudged just over it does not.
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, brightness, (float) (bound - 1e-4), true, false),
                "a roll just below the exact bound burns");
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, brightness, (float) bound, true, false),
                "a roll equal to the exact bound does NOT burn (strict <)");
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, brightness, 0.041F, true, false),
                "a roll clearly above the bound does not burn");
    }

    @Test
    void vanillaSunBurnTickRequiresSkyAndDryWeather() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, false, false), "no sky never burns");
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, true, true),
                "in water/rain/powder snow never burns");
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, true, false),
                "dry, sky-exposed, bright, low roll burns");
    }
}
