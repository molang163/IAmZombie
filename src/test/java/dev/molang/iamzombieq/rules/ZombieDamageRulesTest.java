package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieDamageRulesTest {
    @Test
    void customSunlightDamageUsesModNamespaceAndCountsAsSunlightDeath() {
        assertEquals("iamzombieq:sunlight", ZombieDamageRules.SUNLIGHT_DAMAGE_TYPE_ID);

        assertEquals(DeathTrigger.SUNLIGHT, ZombieDamageRules.triggerFromDamageTypeId("iamzombieq:sunlight"));
        assertEquals(DeathTrigger.OTHER, ZombieDamageRules.triggerFromDamageTypeId("minecraft:on_fire"));
        assertEquals(DeathTrigger.OTHER, ZombieDamageRules.triggerFromDamageTypeId("minecraft:in_fire"));
    }

    @Test
    void vanillaDrowningAndStarvingStillMapToTheirEvolutionTriggers() {
        assertEquals(DeathTrigger.DROWNING, ZombieDamageRules.triggerFromDamageTypeId("minecraft:drown"));
        assertEquals(DeathTrigger.STARVATION, ZombieDamageRules.triggerFromDamageTypeId("minecraft:starve"));
        assertEquals(DeathTrigger.OTHER, ZombieDamageRules.triggerFromDamageTypeId("minecraft:cactus"));
    }

    @Test
    void onFireBecomesSunlightOnlyWithinTheSunFireWindowAndWhenTheFormBurns() {
        // (sourceIsOnFire, withinSunlightFireWindow, formBurnsInSunlight)
        assertTrue(ZombieDamageRules.shouldConvertOnFireDamageToSunlight(true, true, true));
        assertFalse(ZombieDamageRules.shouldConvertOnFireDamageToSunlight(false, true, true), "non-fire damage is never relabeled");
        assertFalse(ZombieDamageRules.shouldConvertOnFireDamageToSunlight(true, false, true), "fire outside the sun-fire window stays vanilla on_fire");
        assertFalse(ZombieDamageRules.shouldConvertOnFireDamageToSunlight(true, true, false), "a sun-immune/protected form's fire is not sunlight");
    }

    @Test
    void attackDamageMultiplierIsVanillaAlignedMonotonicWithDifficulty() {
        double peaceful = ZombieDamageRules.attackDamageMultiplier(GameDifficulty.PEACEFUL);
        double easy = ZombieDamageRules.attackDamageMultiplier(GameDifficulty.EASY);
        double normal = ZombieDamageRules.attackDamageMultiplier(GameDifficulty.NORMAL);
        double hard = ZombieDamageRules.attackDamageMultiplier(GameDifficulty.HARD);

        // PEACEFUL <= EASY < NORMAL < HARD
        assertTrue(peaceful <= easy, "peaceful must not exceed easy");
        assertTrue(easy < normal, "easy must be below normal");
        assertTrue(normal < hard, "normal must be below hard");

        // A multiplier never weakens the player, and the bonus fraction is exactly multiplier - 1.
        assertTrue(peaceful >= 1.0, "multiplier must never reduce attack damage");
        for (GameDifficulty difficulty : GameDifficulty.values()) {
            assertEquals(
                    ZombieDamageRules.attackDamageMultiplier(difficulty) - 1.0,
                    ZombieDamageRules.attackDamageBonusFraction(difficulty),
                    1.0e-9);
        }
    }

    @Test
    void sunlightResourcesExistAndBehaveLikeVanillaFire() throws IOException {
        Path damageType = Path.of("src/main/resources/data/iamzombieq/damage_type/sunlight.json");
        assertTrue(Files.isRegularFile(damageType), "missing sunlight damage type");
        String damageTypeJson = Files.readString(damageType).replaceAll("\\s+", "");
        assertTrue(damageTypeJson.contains("\"message_id\":\"iamzombieq.sunlight\""));
        assertTrue(damageTypeJson.contains("\"effects\":\"burning\""));

        Path fireTag = Path.of("src/main/resources/data/minecraft/tags/damage_type/is_fire.json");
        assertTrue(Files.isRegularFile(fireTag), "missing fire damage tag extension");
        assertTrue(Files.readString(fireTag).contains("\"iamzombieq:sunlight\""));

        // Sunlight must be no-knockback like vanilla fire, so the relabeled tick has no knockback / hurt-direction.
        Path noKnockback = Path.of("src/main/resources/data/minecraft/tags/damage_type/no_knockback.json");
        assertTrue(Files.isRegularFile(noKnockback), "sunlight should be added to minecraft:no_knockback");
        assertTrue(Files.readString(noKnockback).contains("\"iamzombieq:sunlight\""));

        String zh = Files.readString(Path.of("src/main/resources/assets/iamzombieq/lang/zh_cn.json"));
        assertTrue(zh.contains("\"death.attack.iamzombieq.sunlight\": \"%1$s忘了涂防晒霜\""));
    }
}
