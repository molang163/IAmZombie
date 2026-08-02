package dev.molang.iamzombieq.rules.giant;

/**
 * The inputs to {@link GiantRules#giantCanCrush(BlockCrushQuery)}, grouped into a record so the several same-typed
 * boolean flags (notably {@code isSoftTag} / {@code isImmuneTag}) cannot be silently swapped at a call site (R5).
 * Fields are declared in the exact order of the former positional parameters; the crush predicate reads them
 * verbatim, so behaviour is byte-for-byte unchanged. Internal to the rules layer (NOT public {@code api/*}).
 */
public record BlockCrushQuery(
        boolean isAir,
        boolean hasBlockEntity,
        boolean isFluid,
        boolean isSoftTag,
        boolean isImmuneTag,
        float destroySpeed,
        float maxHardness
) {
}
