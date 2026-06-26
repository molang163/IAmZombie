package dev.molang.iamzombieq.rules;

import java.util.List;

public final class IAmZombieAdvancementIds {
    public static final String ROOT = "root";
    public static final String ROTTEN_FLESH = "rotten_flesh";
    public static final String HUMAN_FOOD = "human_food";
    public static final String SUN = "sun";
    public static final String BED = "bed";
    public static final String COFFIN = "coffin";
    public static final String DROWNED = "drowned";
    public static final String BABY = "baby";
    public static final String HUSK = "husk";
    public static final String ZOMBIFIED_PIGLIN = "zombified_piglin";
    public static final String INFECTION = "infection";
    public static final String HORSE_INFECTION = "horse_infection";
    public static final String FIRST_EVOLUTION = "first_evolution";

    private static final List<String> ALL = List.of(
            ROOT,
            ROTTEN_FLESH,
            HUMAN_FOOD,
            SUN,
            BED,
            COFFIN,
            DROWNED,
            BABY,
            HUSK,
            ZOMBIFIED_PIGLIN,
            INFECTION,
            HORSE_INFECTION,
            FIRST_EVOLUTION
    );

    private IAmZombieAdvancementIds() {
    }

    public static List<String> all() {
        return ALL;
    }
}
