package dev.molang.iamzombieq.rules.giant;

/**
 * Giant-form balance rules, split out of {@link dev.molang.iamzombieq.rules.ZombieBalanceRules} (R3): all giant*
 * methods and GIANT_* constants moved here verbatim. ZombieBalanceRules keeps {@code @Deprecated} one-line
 * forwarders for external addon compatibility only; in-repo callers address this class directly.
 */
public final class GiantRules {
    private GiantRules() {
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
    public static boolean giantCanCrush(BlockCrushQuery query) {
        if (query.isAir() || query.hasBlockEntity() || query.isFluid() || query.isImmuneTag()) {
            return false;
        }
        if (query.isSoftTag()) {
            return true;
        }
        return query.destroySpeed() >= 0.0F && query.destroySpeed() <= query.maxHardness();
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
}
