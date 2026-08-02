package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.molang.iamzombieq.rules.ZombieBalanceRules.AttributeDelta;
import dev.molang.iamzombieq.rules.ZombieBalanceRules.AttributeDeltaOperation;
import dev.molang.iamzombieq.rules.ZombieBalanceRules.FormAttributeKey;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ZombieFormAttributeDeltasTest {
    private static final double EPSILON = 1.0e-9;
    private static final List<GameDifficulty> SUPPORTED_DIFFICULTIES = List.of(
            GameDifficulty.EASY,
            GameDifficulty.NORMAL,
            GameDifficulty.HARD);

    @Test
    void vanillaPlayerAttributeBasesAreNamedAndExact() {
        assertEquals(20.0, ZombieBalanceRules.VANILLA_PLAYER_MAX_HEALTH_BASE, EPSILON);
        assertEquals(1.0, ZombieBalanceRules.VANILLA_PLAYER_SCALE_BASE, EPSILON);
        assertEquals(4.5, ZombieBalanceRules.VANILLA_PLAYER_BLOCK_INTERACTION_RANGE_BASE, EPSILON);
        assertEquals(3.0, ZombieBalanceRules.VANILLA_PLAYER_ENTITY_INTERACTION_RANGE_BASE, EPSILON);
        assertEquals(0.6, ZombieBalanceRules.VANILLA_PLAYER_STEP_HEIGHT_BASE, EPSILON);
        assertEquals(1.0, ZombieBalanceRules.VANILLA_PLAYER_ATTACK_DAMAGE_BASE, EPSILON);
    }

    @Test
    void everyFormSizeAndDifficultyReturnsTheCompleteExactThirteenRowTable() {
        for (ZombieForm form : ZombieForm.values()) {
            for (ZombieSize size : ZombieSize.values()) {
                for (GameDifficulty difficulty : SUPPORTED_DIFFICULTIES) {
                    double configuredArmor = configuredArmorFor(form);
                    double difficultyFraction = ZombieDamageRules.attackDamageBonusFraction(difficulty);
                    List<AttributeDelta> deltas = ZombieBalanceRules.formAttributeDeltas(
                            form, size, configuredArmor, difficultyFraction);
                    String label = form + "/" + size + "/" + difficulty;

                    assertEquals(13, deltas.size(), label + " must retain all zero-valued cleanup rows");
                    assertEquals(
                            13,
                            deltas.stream().map(AttributeDelta::key).distinct().count(),
                            label + " must contain thirteen unique semantic keys");

                    Map<FormAttributeKey, AttributeDelta> byKey = deltas.stream().collect(Collectors.toMap(
                            AttributeDelta::key,
                            Function.identity(),
                            (left, right) -> {
                                throw new AssertionError("duplicate key " + left.key());
                            },
                            () -> new EnumMap<>(FormAttributeKey.class)));
                    assertEquals(Set.copyOf(Arrays.asList(FormAttributeKey.values())), byKey.keySet(),
                            label + " must contain every semantic key, including non-applicable zero rows");

                    boolean baby = size == ZombieSize.BABY;
                    boolean drowned = form == ZombieForm.DROWNED;
                    boolean giant = form == ZombieForm.GIANT;
                    assertDelta(byKey, FormAttributeKey.INNATE_ARMOR,
                            AttributeDeltaOperation.ADD_VALUE, configuredArmor, label);
                    assertDelta(byKey, FormAttributeKey.BABY_SCALE,
                            AttributeDeltaOperation.ADD_VALUE, baby ? -0.5 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.BABY_SPEED,
                            AttributeDeltaOperation.ADD_MULTIPLIED_BASE, baby ? 0.5 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.DROWNED_SUBMERGED_MINING,
                            AttributeDeltaOperation.ADD_VALUE, drowned ? 0.8 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_MAX_HEALTH,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 80.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_SCALE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 5.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_BLOCK_INTERACTION_RANGE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 22.5 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_ENTITY_INTERACTION_RANGE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 15.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_STEP_HEIGHT,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 3.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_SAFE_FALL_DISTANCE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 3.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.NON_GIANT_ATTACK_DAMAGE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 0.0 : 2.0, label);
                    assertDelta(byKey, FormAttributeKey.GIANT_ATTACK_DAMAGE,
                            AttributeDeltaOperation.ADD_VALUE, giant ? 54.0 : 0.0, label);
                    assertDelta(byKey, FormAttributeKey.DIFFICULTY_ATTACK_DAMAGE,
                            AttributeDeltaOperation.ADD_MULTIPLIED_BASE,
                            giant ? 0.0 : difficultyFraction,
                            label);

                    assertEquals(11, deltas.stream()
                            .filter(delta -> delta.operation() == AttributeDeltaOperation.ADD_VALUE)
                            .count(), label + " must preserve all eleven ADD_VALUE operations");
                    assertEquals(2, deltas.stream()
                            .filter(delta -> delta.operation() == AttributeDeltaOperation.ADD_MULTIPLIED_BASE)
                            .count(), label + " must preserve both ADD_MULTIPLIED_BASE operations");
                }
            }
        }
    }

    @Test
    void configuredArmorIsPassedThroughWithoutFormSpecificReinterpretation() {
        double sentinelArmor = 17.25;
        for (ZombieForm form : ZombieForm.values()) {
            AttributeDelta armor = delta(
                    ZombieBalanceRules.formAttributeDeltas(form, ZombieSize.ADULT, sentinelArmor, 0.25),
                    FormAttributeKey.INNATE_ARMOR);
            assertEquals(sentinelArmor, armor.amount(), EPSILON, form + " configured armor must pass through");
        }
    }

    @Test
    void giantBabyKeepsTheExistingSizeAndFormStackingSemantics() {
        Map<FormAttributeKey, AttributeDelta> byKey = byKey(ZombieBalanceRules.formAttributeDeltas(
                ZombieForm.GIANT, ZombieSize.BABY, 0.0, 0.5));

        assertEquals(5.5,
                ZombieBalanceRules.VANILLA_PLAYER_SCALE_BASE
                        + byKey.get(FormAttributeKey.BABY_SCALE).amount()
                        + byKey.get(FormAttributeKey.GIANT_SCALE).amount(),
                EPSILON);
        assertEquals(0.5, byKey.get(FormAttributeKey.BABY_SPEED).amount(), EPSILON);
        assertEquals(0.0, byKey.get(FormAttributeKey.NON_GIANT_ATTACK_DAMAGE).amount(), EPSILON);
        assertEquals(54.0, byKey.get(FormAttributeKey.GIANT_ATTACK_DAMAGE).amount(), EPSILON);
        assertEquals(0.0, byKey.get(FormAttributeKey.DIFFICULTY_ATTACK_DAMAGE).amount(), EPSILON);
    }

    @Test
    void rulesTableSourceHasNoMinecraftOrNeoForgeDependency() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/rules/ZombieBalanceRules.java"));
        assertFalse(source.contains("net.minecraft."), "rules table must stay Minecraft-free");
        assertFalse(source.contains("net.neoforged."), "rules table must stay NeoForge-free");
    }

    private static double configuredArmorFor(ZombieForm form) {
        return switch (form) {
            case NORMAL, DROWNED, ZOMBIFIED_PIGLIN -> 2.0;
            case HUSK -> 4.0;
            case GIANT -> 0.0;
        };
    }

    private static Map<FormAttributeKey, AttributeDelta> byKey(List<AttributeDelta> deltas) {
        Map<FormAttributeKey, AttributeDelta> result = new EnumMap<>(FormAttributeKey.class);
        for (AttributeDelta delta : deltas) {
            result.put(delta.key(), delta);
        }
        return result;
    }

    private static AttributeDelta delta(List<AttributeDelta> deltas, FormAttributeKey key) {
        return deltas.stream()
                .filter(delta -> delta.key() == key)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + key));
    }

    private static void assertDelta(
            Map<FormAttributeKey, AttributeDelta> byKey,
            FormAttributeKey key,
            AttributeDeltaOperation operation,
            double amount,
            String label) {
        AttributeDelta delta = byKey.get(key);
        assertEquals(operation, delta.operation(), label + "/" + key + " operation");
        assertEquals(amount, delta.amount(), EPSILON, label + "/" + key + " amount");
    }
}
