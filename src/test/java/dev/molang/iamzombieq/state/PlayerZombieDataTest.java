package dev.molang.iamzombieq.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;

class PlayerZombieDataTest {
    @Test
    void defaultDataStartsAsAdultNormalZombieWithNoFirstEvolutionRewardsClaimed() {
        PlayerZombieData data = PlayerZombieData.DEFAULT;

        assertEquals(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT), data.state());
        assertFalse(data.receivedFirstDrownedReward());
        assertFalse(data.receivedFirstHuskReward());
        assertFalse(data.receivedFirstZombifiedPiglinReward());
    }

    @Test
    void changingStateKeepsOneTimeRewardFlags() {
        PlayerZombieData data = PlayerZombieData.DEFAULT
                .withFirstDrownedRewardClaimed()
                .withState(new ZombieState(ZombieForm.DROWNED, ZombieSize.BABY));

        assertEquals(new ZombieState(ZombieForm.DROWNED, ZombieSize.BABY), data.state());
        assertTrue(data.receivedFirstDrownedReward());
        assertFalse(data.receivedFirstHuskReward());
        assertFalse(data.receivedFirstZombifiedPiglinReward());
    }

    @Test
    void ordinaryDeathResetKeepsOneTimeRewardFlags() {
        PlayerZombieData data = PlayerZombieData.DEFAULT
                .withFirstDrownedRewardClaimed()
                .withFirstHuskRewardClaimed()
                .withFirstZombifiedPiglinRewardClaimed()
                .withState(new ZombieState(ZombieForm.HUSK, ZombieSize.BABY));

        PlayerZombieData reset = data.resetStateForOrdinaryDeath();

        assertEquals(ZombieState.DEFAULT, reset.state());
        assertTrue(reset.receivedFirstDrownedReward());
        assertTrue(reset.receivedFirstHuskReward());
        assertTrue(reset.receivedFirstZombifiedPiglinReward());
    }
}
