package dev.molang.iamzombieq.rules;

public final class ZombiePotionRules {
    private ZombiePotionRules() {
    }

    public static boolean shouldInvertHealAndHarm(boolean isPlayer, boolean isCreative, boolean isSpectator) {
        // N6: undead heal/harm potion inversion applies in creative too (a zombie is a zombie in every game mode);
        // the isCreative parameter is retained for call-site/API stability but no longer gates the inversion. Only
        // spectators are excluded.
        return isPlayer && !isSpectator;
    }
}
