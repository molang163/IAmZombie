/**
 * Rule and outcome model types. The types exposed through {@code api/*} from this package —
 * {@link dev.molang.iamzombieq.rules.DeathOutcome} and {@link dev.molang.iamzombieq.rules.EffectSpec} (and, via
 * the {@code rules.core} / {@code rules.food} sub-packages, {@code ZombieForm}/{@code ZombieSize}/{@code ZombieState}
 * and {@code FoodRule}) — are part of the <b>stable public API surface</b> (semver 1.x): backward-compatible
 * additions only within 1.x. They live here (outside {@code api/*}) for packaging reasons but are surfaced through
 * {@code api/core/IZombiePlayer}, the {@code api/event/*} DTOs, and {@code api/extension/*}, so they are frozen as
 * public API.
 *
 * @since 1.0
 * @apiNote 2.0 may revisit zombie forms via the {@code api/registry} form registry; within 1.x these types are
 *          stable.
 */
package dev.molang.iamzombieq.rules;
