package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import java.util.function.DoubleSupplier;

public final class ZombieSunlightRules {
    private ZombieSunlightRules() {
    }

    public static boolean shouldBurn(ZombieForm form, boolean exposedToSun, HeadProtection protection) {
        return exposedToSun && burnsInSunlight(form) && !protection.protectsFromSun();
    }

    public static boolean shouldDamageHeadProtection(ZombieForm form, boolean exposedToSun, HeadProtection protection) {
        return exposedToSun && burnsInSunlight(form) && protection.protectsFromSun();
    }

    private static boolean burnsInSunlight(ZombieForm form) {
        return form == ZombieForm.NORMAL || form == ZombieForm.DROWNED;
    }

    public static boolean isVanillaSunBurnTick(
            boolean monstersBurn,
            float brightness,
            float randomFloat,
            boolean canSeeSky,
            boolean inWaterRainOrPowderSnow
    ) {
        return isVanillaSunBurnTick(
                new SunBurnContext(
                        monstersBurn,
                        brightness,
                        randomFloat,
                        canSeeSky,
                        inWaterRainOrPowderSnow
                )
        );
    }

    public static boolean isVanillaSunBurnTick(
            boolean monstersBurn,
            float brightness,
            DoubleSupplier randomFloat,
            boolean canSeeSky,
            boolean inWaterRainOrPowderSnow
    ) {
        if (!monstersBurn || !(brightness > 0.5F)) {
            return false;
        }
        double randomValue = randomFloat.getAsDouble();
        return isVanillaSunBurnTick(
                new SunBurnContext(
                        monstersBurn,
                        brightness,
                        randomValue,
                        canSeeSky,
                        inWaterRainOrPowderSnow
                )
        );
    }

    public static boolean isVanillaSunBurnTick(SunBurnContext context) {
        return context.monstersBurn()
                && context.brightness() > 0.5F
                && context.randomValue() * 30.0F < (context.brightness() - 0.4F) * 2.0F
                && !context.inWaterRainOrPowderSnow()
                && context.canSeeSky();
    }
}
