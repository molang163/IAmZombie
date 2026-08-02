package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.giant.GiantRules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class ZombieBalanceRules {
    public static final double NORMAL_STEVE_HEAD_BASE = 0.025;
    public static final double NORMAL_STEVE_HEAD_LOOTING_BONUS = 0.01;
    public static final double MATCHBOX_STEVE_HEAD_BASE = 0.01;
    public static final double MATCHBOX_STEVE_HEAD_LOOTING_BONUS = 0.01;
    public static final double STRONG_STEVE_HEAD_BASE = 0.30;
    public static final double STRONG_STEVE_HEAD_LOOTING_BONUS = 0.05;
    public static final int EFFECT_REFRESH_MARGIN_TICKS = 220;
    public static final int HUSK_MELEE_HUNGER_DURATION_TICKS = 20 * 15;
    public static final float SUNLIGHT_BURN_DURATION_SECONDS = 8.0F;
    public static final float EVOLUTION_RESPAWN_HEALTH_FRACTION = 0.5F;
    public static final double VANILLA_PLAYER_MAX_HEALTH_BASE = 20.0;
    public static final double VANILLA_PLAYER_SCALE_BASE = 1.0;
    public static final double VANILLA_PLAYER_BLOCK_INTERACTION_RANGE_BASE = 4.5;
    public static final double VANILLA_PLAYER_ENTITY_INTERACTION_RANGE_BASE = 3.0;
    public static final double VANILLA_PLAYER_STEP_HEIGHT_BASE = 0.6;
    public static final double VANILLA_PLAYER_ATTACK_DAMAGE_BASE = 1.0;

    private static final double GIANT_SCALE_TARGET = 6.0;
    private static final double NON_GIANT_ATTACK_DAMAGE_TARGET = 3.0;

    private ZombieBalanceRules() {
    }

    public static int innateArmor(ZombieForm form) {
        return switch (form) {
            case NORMAL, DROWNED, ZOMBIFIED_PIGLIN -> 2;
            case HUSK -> 4;
            case GIANT -> 0;
        };
    }

    public static boolean hasFireResistance(ZombieForm form) {
        return form == ZombieForm.ZOMBIFIED_PIGLIN;
    }

    public static double goldDurabilityConsumptionMultiplier(ZombieForm form) {
        return form == ZombieForm.ZOMBIFIED_PIGLIN ? 0.25 : 1.0;
    }

    /**
     * M4: the gold-durability reduction roll, moved verbatim out of {@code ItemStackMixin} so it is unit-testable.
     * Scales {@code amount} by {@link #goldDurabilityConsumptionMultiplier(ZombieForm)}, truncates, adds 1 when
     * {@code randomDouble} (expected in {@code [0, 1)}) rolls under the fractional remainder, and clamps the result
     * to {@code [0, amount]}.
     */
    public static int scaledDurabilityDamage(int amount, ZombieForm form, double randomDouble) {
        double scaledAmount = amount * goldDurabilityConsumptionMultiplier(form);
        int reducedAmount = (int) scaledAmount;
        if (randomDouble < scaledAmount - reducedAmount) {
            reducedAmount++;
        }
        return Math.max(0, Math.min(amount, reducedAmount));
    }

    public static boolean zombifiedPiglinsDefendPlayer(ZombieForm form) {
        return form == ZombieForm.ZOMBIFIED_PIGLIN;
    }

    public static double maxHealth(ZombieForm form) {
        return form == ZombieForm.GIANT ? 100.0 : 20.0;
    }

    /** Stable semantic keys for the complete form-attribute modifier table. */
    public enum FormAttributeKey {
        INNATE_ARMOR,
        BABY_SCALE,
        BABY_SPEED,
        DROWNED_SUBMERGED_MINING,
        GIANT_MAX_HEALTH,
        GIANT_SCALE,
        GIANT_BLOCK_INTERACTION_RANGE,
        GIANT_ENTITY_INTERACTION_RANGE,
        GIANT_STEP_HEIGHT,
        GIANT_SAFE_FALL_DISTANCE,
        NON_GIANT_ATTACK_DAMAGE,
        GIANT_ATTACK_DAMAGE,
        DIFFICULTY_ATTACK_DAMAGE
    }

    /** Minecraft-free equivalents of the two attribute operations used by form modifiers. */
    public enum AttributeDeltaOperation {
        ADD_VALUE,
        ADD_MULTIPLIED_BASE
    }

    /** One complete-table row; zero amounts are intentional removal instructions and must not be filtered. */
    public record AttributeDelta(FormAttributeKey key, AttributeDeltaOperation operation, double amount) {
    }

    /**
     * The complete thirteen-row form-attribute delta table. Every semantic key is returned for every state so the
     * Minecraft adapter can remove stable-ID modifiers whose current amount is zero.
     */
    public static List<AttributeDelta> formAttributeDeltas(
            ZombieForm form,
            ZombieSize size,
            double configuredArmor,
            double difficultyFraction) {
        boolean baby = size == ZombieSize.BABY;
        boolean drowned = form == ZombieForm.DROWNED;
        boolean giant = form == ZombieForm.GIANT;
        return List.of(
                new AttributeDelta(FormAttributeKey.INNATE_ARMOR,
                        AttributeDeltaOperation.ADD_VALUE, configuredArmor),
                new AttributeDelta(FormAttributeKey.BABY_SCALE,
                        AttributeDeltaOperation.ADD_VALUE, baby ? -0.5 : 0.0),
                new AttributeDelta(FormAttributeKey.BABY_SPEED,
                        AttributeDeltaOperation.ADD_MULTIPLIED_BASE, baby ? 0.5 : 0.0),
                new AttributeDelta(FormAttributeKey.DROWNED_SUBMERGED_MINING,
                        AttributeDeltaOperation.ADD_VALUE, drowned ? 0.8 : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_MAX_HEALTH,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? maxHealth(ZombieForm.GIANT) - VANILLA_PLAYER_MAX_HEALTH_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_SCALE,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? GIANT_SCALE_TARGET - VANILLA_PLAYER_SCALE_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_BLOCK_INTERACTION_RANGE,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? GiantRules.giantBlockInteractionRange()
                                - VANILLA_PLAYER_BLOCK_INTERACTION_RANGE_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_ENTITY_INTERACTION_RANGE,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? GiantRules.giantEntityInteractionRange()
                                - VANILLA_PLAYER_ENTITY_INTERACTION_RANGE_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_STEP_HEIGHT,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? GiantRules.giantStepHeight() - VANILLA_PLAYER_STEP_HEIGHT_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.GIANT_SAFE_FALL_DISTANCE,
                        AttributeDeltaOperation.ADD_VALUE, giant ? GiantRules.giantSafeFallBonus() : 0.0),
                new AttributeDelta(FormAttributeKey.NON_GIANT_ATTACK_DAMAGE,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? 0.0 : NON_GIANT_ATTACK_DAMAGE_TARGET - VANILLA_PLAYER_ATTACK_DAMAGE_BASE),
                new AttributeDelta(FormAttributeKey.GIANT_ATTACK_DAMAGE,
                        AttributeDeltaOperation.ADD_VALUE,
                        giant ? GiantRules.giantAttackDamage() - VANILLA_PLAYER_ATTACK_DAMAGE_BASE : 0.0),
                new AttributeDelta(FormAttributeKey.DIFFICULTY_ATTACK_DAMAGE,
                        AttributeDeltaOperation.ADD_MULTIPLIED_BASE, giant ? 0.0 : difficultyFraction));
    }

    // ---- Giant identity: moved verbatim to rules/giant/GiantRules (R3). The @Deprecated members below are
    // one-line forwarders kept only for external addon compatibility (semver 1.x); in-repo callers use GiantRules. ----

    /** @deprecated moved to {@link GiantRules#giantBlockInteractionRange()}. */
    @Deprecated
    public static double giantBlockInteractionRange() {
        return GiantRules.giantBlockInteractionRange();
    }

    /** @deprecated moved to {@link GiantRules#giantEntityInteractionRange()}. */
    @Deprecated
    public static double giantEntityInteractionRange() {
        return GiantRules.giantEntityInteractionRange();
    }

    /** @deprecated moved to {@link GiantRules#giantStepHeight()}. */
    @Deprecated
    public static double giantStepHeight() {
        return GiantRules.giantStepHeight();
    }

    /** @deprecated moved to {@link GiantRules#giantSafeFallBonus()}. */
    @Deprecated
    public static double giantSafeFallBonus() {
        return GiantRules.giantSafeFallBonus();
    }

    /** @deprecated moved to {@link GiantRules#giantAttackDamage()}. */
    @Deprecated
    public static double giantAttackDamage() {
        return GiantRules.giantAttackDamage();
    }

    /** @deprecated moved to {@link GiantRules#giantAutoDamageRadius()}. */
    @Deprecated
    public static double giantAutoDamageRadius() {
        return GiantRules.giantAutoDamageRadius();
    }

    /** @deprecated moved to {@link GiantRules#giantAutoDamageAmount()}. */
    @Deprecated
    public static double giantAutoDamageAmount() {
        return GiantRules.giantAutoDamageAmount();
    }

    /** @deprecated moved to {@link GiantRules#giantBlockDestructionRadius()}. */
    @Deprecated
    public static int giantBlockDestructionRadius() {
        return GiantRules.giantBlockDestructionRadius();
    }

    /** @deprecated moved to {@link GiantRules#giantPassiveReachHorizontal()}. */
    @Deprecated
    public static double giantPassiveReachHorizontal() {
        return GiantRules.giantPassiveReachHorizontal();
    }

    /** @deprecated moved to {@link GiantRules#giantPassiveReachVertical()}. */
    @Deprecated
    public static double giantPassiveReachVertical() {
        return GiantRules.giantPassiveReachVertical();
    }

    /** @deprecated moved to {@link GiantRules#giantDestroysBlockLayer(int, double)}. */
    @Deprecated
    public static boolean giantDestroysBlockLayer(int blockMinY, double giantFootY) {
        return GiantRules.giantDestroysBlockLayer(blockMinY, giantFootY);
    }

    /** @deprecated moved to {@link GiantRules#giantCanCrush(dev.molang.iamzombieq.rules.giant.BlockCrushQuery)}. */
    @Deprecated
    public static boolean giantCanCrush(boolean isAir, boolean hasBlockEntity, boolean isFluid,
                                        boolean isSoftTag, boolean isImmuneTag, float destroySpeed, float maxHardness) {
        return GiantRules.giantCanCrush(new dev.molang.iamzombieq.rules.giant.BlockCrushQuery(
                isAir, hasBlockEntity, isFluid, isSoftTag, isImmuneTag, destroySpeed, maxHardness));
    }

    /** @deprecated moved to {@link GiantRules#GIANT_PASSIVE_MAX_HARDNESS}. */
    @Deprecated
    public static final float GIANT_PASSIVE_MAX_HARDNESS = GiantRules.GIANT_PASSIVE_MAX_HARDNESS;
    /** @deprecated moved to {@link GiantRules#GIANT_SWING_MAX_HARDNESS}. */
    @Deprecated
    public static final float GIANT_SWING_MAX_HARDNESS = GiantRules.GIANT_SWING_MAX_HARDNESS;

    /** @deprecated moved to {@link GiantRules#giantPassiveDestroyCapPerTick()}. */
    @Deprecated
    public static int giantPassiveDestroyCapPerTick() {
        return GiantRules.giantPassiveDestroyCapPerTick();
    }

    /** @deprecated moved to {@link GiantRules#giantSwingCubeEdge()}. */
    @Deprecated
    public static int giantSwingCubeEdge() {
        return GiantRules.giantSwingCubeEdge();
    }

    /** @deprecated moved to {@link GiantRules#giantSwingMaxBlocks()}. */
    @Deprecated
    public static int giantSwingMaxBlocks() {
        return GiantRules.giantSwingMaxBlocks();
    }

    /** @deprecated moved to {@link GiantRules#giantSwingCooldownTicks()}. */
    @Deprecated
    public static long giantSwingCooldownTicks() {
        return GiantRules.giantSwingCooldownTicks();
    }

    /**
     * Deliberate MOD buff (not vanilla): empty-handed zombie players break wooden doors 3x faster. Vanilla zombies
     * have no break-speed multiplier at all -- they smash doors via the timed BreakDoorGoal (a fixed 240-tick break,
     * HARD-gated), which has no destroy-speed component. 3.0F coincides with the oak-door hardness
     * (Blocks.OAK_DOOR.strength(3.0F)).
     */
    public static final float WOODEN_DOOR_BREAK_MULTIPLIER = 3.0F;

    /**
     * Whether a zombie player should get the mod's wooden-door break-speed buff (see WOODEN_DOOR_BREAK_MULTIPLIER --
     * a mod feature, not vanilla): only when the main hand is empty AND the targeted block is a wooden door. Keeps
     * trapdoors/fence-gates out so the buff applies only to the wooden doors vanilla zombies actually break.
     */
    public static boolean shouldBoostWoodenDoorBreak(boolean mainHandEmpty, boolean blockIsWoodenDoor) {
        return mainHandEmpty && blockIsWoodenDoor;
    }

    /** A single stack in a randomized reward bundle, identified by its registry id and a count. */
    public record RewardEntry(String itemId, int count) {
    }

    /** Desert-themed loot pool for the first husk-evolution reward, paired with a randomized count range. */
    private record DesertReward(String itemId, int minCount, int maxCount) {
    }

    private static final List<DesertReward> HUSK_DESERT_POOL = List.of(
            new DesertReward("minecraft:sand", 8, 24),
            new DesertReward("minecraft:sandstone", 4, 12),
            new DesertReward("minecraft:cactus", 2, 6),
            new DesertReward("minecraft:dead_bush", 1, 3),
            new DesertReward("minecraft:bone", 1, 4),
            new DesertReward("minecraft:terracotta", 2, 8)
    );

    public static final int HUSK_REWARD_MIN_STACKS = 2;
    public static final int HUSK_REWARD_MAX_STACKS = 4;

    /**
     * A randomized desert-themed reward bundle for the first husk evolution. Picks {@value #HUSK_REWARD_MIN_STACKS}
     * to {@value #HUSK_REWARD_MAX_STACKS} distinct entries from {@link #HUSK_DESERT_POOL}, each with a random count
     * inside its pool range. Deterministic for a seeded {@link RandomGenerator}. Always non-empty and bounded.
     * Takes a pure-Java {@link RandomGenerator} (not a Minecraft RandomSource) so the rules layer stays unit-testable.
     */
    public static List<RewardEntry> huskFirstRewardBundle(RandomGenerator random) {
        List<DesertReward> pool = new ArrayList<>(HUSK_DESERT_POOL);
        int stacks = HUSK_REWARD_MIN_STACKS
                + random.nextInt(HUSK_REWARD_MAX_STACKS - HUSK_REWARD_MIN_STACKS + 1);
        stacks = Math.min(stacks, pool.size());
        List<RewardEntry> bundle = new ArrayList<>(stacks);
        for (int i = 0; i < stacks; i++) {
            DesertReward pick = pool.remove(random.nextInt(pool.size()));
            int count = pick.minCount() + random.nextInt(pick.maxCount() - pick.minCount() + 1);
            bundle.add(new RewardEntry(pick.itemId(), count));
        }
        return bundle;
    }

    /** @deprecated moved to {@link ZombieInfectionRules#infectionChance(GameDifficulty)}. */
    @Deprecated
    public static double infectionChance(GameDifficulty difficulty) {
        return ZombieInfectionRules.infectionChance(difficulty);
    }

    public static double normalSteveHeadDropChance(int lootingLevel) {
        return headDropChance(NORMAL_STEVE_HEAD_BASE, NORMAL_STEVE_HEAD_LOOTING_BONUS, lootingLevel);
    }

    public static double matchboxSteveHeadDropChance(int lootingLevel) {
        return headDropChance(MATCHBOX_STEVE_HEAD_BASE, MATCHBOX_STEVE_HEAD_LOOTING_BONUS, lootingLevel);
    }

    public static double strongSteveHeadDropChance(int lootingLevel) {
        return headDropChance(STRONG_STEVE_HEAD_BASE, STRONG_STEVE_HEAD_LOOTING_BONUS, lootingLevel);
    }

    public static double headDropChance(double baseChance, double lootingBonus, int lootingLevel) {
        return clampChance(baseChance + lootingBonus * Math.max(0, lootingLevel));
    }

    private static double clampChance(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
