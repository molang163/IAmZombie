package dev.molang.iamzombieq.gametest;

import com.mojang.serialization.MapCodec;
//? if <26.1
//import java.util.ArrayDeque;
//? if <26.1
//import java.util.IdentityHashMap;
//? if <26.1
//import java.util.Map;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
//? if <26.1
//import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;

/**
 * Backports 26.2's restoring HARD GameTest environment to nodes that do not provide
 * {@code TestEnvironmentDefinition.SetDifficulty}.
 */
final class RestoringHardDifficultyEnvironment
        //? if >=26.1
        implements TestEnvironmentDefinition<Difficulty> {
        //? if <26.1
        //implements TestEnvironmentDefinition {
    static final MapCodec<RestoringHardDifficultyEnvironment> CODEC =
            MapCodec.unit(RestoringHardDifficultyEnvironment::new);

    //? if <26.1
    //private final Map<MinecraftServer, ArrayDeque<Difficulty>> savedDifficulties = new IdentityHashMap<>();

    //? if >=26.1 {
    @Override
    public Difficulty setup(ServerLevel level) {
        Difficulty oldDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.HARD, true);
        return oldDifficulty;
    }

    @Override
    public void teardown(ServerLevel level, Difficulty savedDifficulty) {
        level.getServer().setDifficulty(savedDifficulty, true);
    }
    //?} else {
    /*@Override
    public void setup(ServerLevel level) {
        MinecraftServer server = level.getServer();
        savedDifficulties.computeIfAbsent(server, ignored -> new ArrayDeque<>()).push(level.getDifficulty());
        server.setDifficulty(Difficulty.HARD, true);
    }

    @Override
    public void teardown(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ArrayDeque<Difficulty> stack = savedDifficulties.get(server);
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException("restoring HARD GameTest environment was not active");
        }
        Difficulty savedDifficulty = stack.peek();
        server.setDifficulty(savedDifficulty, true);
        stack.pop();
        if (stack.isEmpty()) {
            savedDifficulties.remove(server);
        }
    }
    *///?}

    @Override
    public MapCodec<RestoringHardDifficultyEnvironment> codec() {
        return CODEC;
    }
}
