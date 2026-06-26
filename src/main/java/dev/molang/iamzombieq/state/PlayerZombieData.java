package dev.molang.iamzombieq.state;

import dev.molang.iamzombieq.rules.core.ZombieState;

public record PlayerZombieData(
        ZombieState state,
        boolean receivedFirstDrownedReward,
        boolean receivedFirstHuskReward,
        boolean receivedFirstZombifiedPiglinReward
) {
    public static final PlayerZombieData DEFAULT = new PlayerZombieData(ZombieState.DEFAULT, false, false, false);

    public PlayerZombieData withState(ZombieState nextState) {
        return new PlayerZombieData(
                nextState,
                receivedFirstDrownedReward,
                receivedFirstHuskReward,
                receivedFirstZombifiedPiglinReward
        );
    }

    public PlayerZombieData resetStateForOrdinaryDeath() {
        return withState(ZombieState.DEFAULT);
    }

    public PlayerZombieData withFirstDrownedRewardClaimed() {
        return new PlayerZombieData(state, true, receivedFirstHuskReward, receivedFirstZombifiedPiglinReward);
    }

    public PlayerZombieData withFirstHuskRewardClaimed() {
        return new PlayerZombieData(state, receivedFirstDrownedReward, true, receivedFirstZombifiedPiglinReward);
    }

    public PlayerZombieData withFirstZombifiedPiglinRewardClaimed() {
        return new PlayerZombieData(state, receivedFirstDrownedReward, receivedFirstHuskReward, true);
    }
}
