package dev.molang.iamzombieq.rules.food;

public enum FoodTier {
    CARRION("iamzombieq.tooltip.food.carrion"),
    FORAGE("iamzombieq.tooltip.food.forage"),
    HUMAN_COOKED("iamzombieq.tooltip.food.human_cooked"),
    SPECIAL("iamzombieq.tooltip.food.special");

    private final String tooltipKey;

    FoodTier(String tooltipKey) {
        this.tooltipKey = tooltipKey;
    }

    public String tooltipKey() {
        return tooltipKey;
    }
}
