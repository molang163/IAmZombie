package dev.molang.iamzombieq.rules;

public enum HeadProtection {
    NONE,
    PUMPKIN,
    STEVE_HEAD,
    OTHER_HELMET;

    public boolean protectsFromSun() {
        return this != NONE;
    }
}
