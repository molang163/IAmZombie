package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.core.ZombieForm;

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

    public static boolean zombifiedPiglinsDefendPlayer(ZombieForm form) {
        return form == ZombieForm.ZOMBIFIED_PIGLIN;
    }

    public static double maxHealth(ZombieForm form) {
        return form == ZombieForm.GIANT ? 100.0 : 20.0;
    }

    // ---- Giant identity (设计指南 §2.4/§6): the SCALE attribute does NOT auto-scale reach/step/attack, so each of
    // these is an explicit target applied as its own attribute modifier. ----

    /** Block-interaction (mining/placing) reach for the giant: 4.5 × 6 = 27 (vanilla base 4.5). */
    public static double giantBlockInteractionRange() {
        return 27.0;
    }

    /** Entity-interaction (melee/interact) reach for the giant: 3.0 × 6 = 18 (vanilla base 3.0). */
    public static double giantEntityInteractionRange() {
        return 18.0;
    }

    /** Step height for the giant so it strides over short walls instead of jamming: ≈ 0.6 × 6 = 3.6 (base 0.6). */
    public static double giantStepHeight() {
        return 3.6;
    }

    /** Bonus safe-fall distance for the giant so its tall body does not fall-die from ordinary drops (base 3.0). */
    public static double giantSafeFallBonus() {
        return 3.0;
    }

    /** The giant's flat melee attack damage: a small bump above the vanilla Giant's 50 (still NOT difficulty-scaled),
     * keeping the giant slightly stronger than the Warden (vanilla melee 30) per the strengthening pass. */
    public static double giantAttackDamage() {
        return 55.0;
    }

    /** Radius (blocks) of the giant's body-contact stomp aura (设计指南 §4.x; widened for the strengthening pass). */
    public static double giantAutoDamageRadius() {
        return 5.0;
    }

    /** Per-pulse damage of the giant's stomp aura, applied on the 20-tick cadence (raised for the strengthening pass). */
    public static double giantAutoDamageAmount() {
        return 10.0;
    }

    public static int giantBlockDestructionRadius() {
        return 3;
    }

    /** Horizontal (X/Z) reach the giant's passive walk-destruction inflates its body box by, so it razes a WIDER
     * swath as it strides (raised for the village-razing pass). */
    public static double giantPassiveReachHorizontal() {
        return 2.0;
    }

    /** Vertical (Y) reach the giant's passive walk-destruction inflates its body box by, so it razes TALLER
     * structures above its head; the foot layer and below stay protected by {@link #giantDestroysBlockLayer}. */
    public static double giantPassiveReachVertical() {
        return 2.0;
    }

    /**
     * Whether a block at world Y {@code blockMinY} should be destroyed by the giant's body contact, given the
     * giant's foot (bounding-box min) Y {@code giantFootY}. The giant destroys blocks its scaled bounding box
     * touches EXCEPT the foot layer (and anything below it) so it never digs out its own footing.
     *
     * <p>A block occupies {@code [blockMinY, blockMinY + 1)}; the foot layer is the block whose cell contains
     * {@code giantFootY}. Any block whose cell starts strictly below the cell above the foot is preserved.
     */
    public static boolean giantDestroysBlockLayer(int blockMinY, double giantFootY) {
        int footLayer = (int) Math.floor(giantFootY);
        return blockMinY > footLayer;
    }

    /**
     * The crush predicate for the giant's destruction kernel (设计指南 §4.1/§9.5). A block is crushable only when it
     * is not air, has no block entity (containers), is not a fluid, and is not on the absolute {@code GIANT_IMMUNE}
     * blacklist. Anything on the {@code GIANT_SOFT} whitelist is always crushable; otherwise it falls back to a
     * hardness gate ({@code destroySpeed} in {@code [0, maxHardness]}). Passive walking passes a STONE-TIER
     * {@code maxHardness} so the walking giant razes terrain/village blocks (stone 1.5, cobble 2.0) but deepslate
     * 3.0+/obsidian still stop it; the active swing passes a HIGHER one so the punch also breaks ores.
     */
    public static boolean giantCanCrush(boolean isAir, boolean hasBlockEntity, boolean isFluid,
                                        boolean isSoftTag, boolean isImmuneTag, float destroySpeed, float maxHardness) {
        if (isAir || hasBlockEntity || isFluid || isImmuneTag) {
            return false;
        }
        if (isSoftTag) {
            return true;
        }
        return destroySpeed >= 0.0F && destroySpeed <= maxHardness;
    }

    /** Passive walk-destruction hardness fallback: stone-tier, so the walking giant razes terrain/village blocks
     * (stone 1.5, cobblestone/planks/logs/stone-bricks 2.0) but harder blocks (deepslate 3.0+, obsidian) still stop it. */
    public static final float GIANT_PASSIVE_MAX_HARDNESS = 2.0F;
    /** Active swing-destruction hardness fallback: high, so the punch breaks stone/ores (but never obsidian/bedrock). */
    public static final float GIANT_SWING_MAX_HARDNESS = 5.0F;

    /** Max blocks the giant's passive walk-destruction removes per tick (bounds worst-case work; raised to match the
     * wider/taller footprint while still capping per-tick cost). */
    public static int giantPassiveDestroyCapPerTick() {
        return 256;
    }

    /** Edge length of the giant's active swing destruction cube (raised for the bigger-smash pass: a 17³ region). */
    public static int giantSwingCubeEdge() {
        return 17;
    }

    /** Max blocks a single giant swing destroys (the nearest-to-impact within the cube; raised for the bigger swing). */
    public static int giantSwingMaxBlocks() {
        return 200;
    }

    /** Cooldown (ticks) between giant swing AoE destructions, so it is not an infinite instant-miner. */
    public static long giantSwingCooldownTicks() {
        return 25L;
    }

    /** Break-speed multiplier an empty-handed vanilla-style zombie gets when breaking a wooden door. */
    public static final float WOODEN_DOOR_BREAK_MULTIPLIER = 3.0F;

    /**
     * Whether a zombie player should get the vanilla-zombie wooden-door break-speed boost: only when the main hand
     * is empty AND the targeted block is a wooden door. Keeps trapdoors/fence-gates out for vanilla alignment.
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

    public static double infectionChance(GameDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0;
            case EASY -> 0.25;
            case NORMAL -> 0.50;
            case HARD -> 1.0;
        };
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
