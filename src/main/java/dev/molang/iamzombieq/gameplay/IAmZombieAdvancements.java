package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.ModIds;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class IAmZombieAdvancements {
    public static final String MANUAL_CRITERION = "manual";

    public static final Identifier ROOT = id("root");
    public static final Identifier ROTTEN_FLESH = id("rotten_flesh");
    public static final Identifier HUMAN_FOOD = id("human_food");
    public static final Identifier SUN = id("sun");
    public static final Identifier BED = id("bed");
    public static final Identifier COFFIN = id("coffin");
    public static final Identifier DROWNED = id("drowned");
    public static final Identifier BABY = id("baby");
    public static final Identifier HUSK = id("husk");
    public static final Identifier ZOMBIFIED_PIGLIN = id("zombified_piglin");
    public static final Identifier INFECTION = id("infection");
    public static final Identifier HORSE_INFECTION = id("horse_infection");
    public static final Identifier FIRST_EVOLUTION = id("first_evolution");

    private IAmZombieAdvancements() {
    }

    public static void award(ServerPlayer player, Identifier id) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, MANUAL_CRITERION);
        }
    }

    private static Identifier id(String path) {
        return ModIds.id(path);
    }
}
