package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;

public final class ZombieDamageRules {
    public static final String SUNLIGHT_DAMAGE_TYPE_ID = "iamzombieq:sunlight";

    private ZombieDamageRules() {
    }

    public static DeathTrigger triggerFromDamageTypeId(String damageTypeId) {
        return switch (damageTypeId) {
            case "minecraft:drown" -> DeathTrigger.DROWNING;
            case "minecraft:starve" -> DeathTrigger.STARVATION;
            case "minecraft:lava" -> DeathTrigger.LAVA;
            case SUNLIGHT_DAMAGE_TYPE_ID -> DeathTrigger.SUNLIGHT;
            default -> DeathTrigger.OTHER;
        };
    }

    /**
     * Whether a vanilla on-fire damage tick should be re-attributed to the custom sunlight damage type (for the
     * sunlight death message and desert husk evolution). True while the player is still within an active
     * sun-ignited burn window and the current form actually burns in sunlight (i.e. not head-protected).
     *
     * <p>This intentionally relabels every on-fire tick during the window uniformly, rather than tracking
     * ownership of individual ticks — vanilla fire keeps the correct timing/cadence and the
     * {@code iamzombieq:sunlight} type is in {@code minecraft:no_knockback}, so the relabel is indistinguishable
     * from vanilla fire apart from the death attribution.
     */
    public static boolean shouldConvertOnFireDamageToSunlight(
            boolean sourceIsOnFire,
            boolean withinSunlightFireWindow,
            boolean formBurnsInSunlight
    ) {
        return sourceIsOnFire && withinSunlightFireWindow && formBurnsInSunlight;
    }

    /**
     * Difficulty-scaled multiplier applied to a zombie player's attack damage, vanilla-aligned in the sense that
     * the bonus grows monotonically with the world difficulty: {@code PEACEFUL <= EASY < NORMAL < HARD}. Peaceful
     * shares Easy's lowest tier (Peaceful is unsupported gameplay), so the relation is non-strict only there.
     *
     * <p>A multiplier of {@code 1.0} means no bonus. The returned value is intended to be turned into an
     * {@code ADD_MULTIPLIED_BASE} attribute modifier amount via {@link #attackDamageBonusFraction(GameDifficulty)}.
     */
    public static double attackDamageMultiplier(GameDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> 1.10;
            case NORMAL -> 1.25;
            case HARD -> 1.50;
        };
    }

    /**
     * The bonus fraction ({@code multiplier - 1}) suitable for an {@code ADD_MULTIPLIED_BASE} attack-damage
     * modifier amount. {@code 0.0} means no bonus.
     */
    public static double attackDamageBonusFraction(GameDifficulty difficulty) {
        return attackDamageMultiplier(difficulty) - 1.0;
    }
}
