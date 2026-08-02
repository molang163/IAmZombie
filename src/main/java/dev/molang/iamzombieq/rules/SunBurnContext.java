package dev.molang.iamzombieq.rules;

public record SunBurnContext(
        boolean monstersBurn,
        float brightness,
        double randomValue,
        boolean canSeeSky,
        boolean inWaterRainOrPowderSnow
) {
}
