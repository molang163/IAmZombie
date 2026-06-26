/**
 * Core zombie model types. The types exposed through {@code api/*} from this package —
 * {@link dev.molang.iamzombieq.rules.core.ZombieForm}, {@link dev.molang.iamzombieq.rules.core.ZombieSize}, and
 * {@link dev.molang.iamzombieq.rules.core.ZombieState} — are part of the <b>stable public API surface</b>
 * (semver 1.x): backward-compatible additions only within 1.x. They live here (outside {@code api/*}) for
 * packaging reasons but are surfaced through {@code api/core/IZombiePlayer} and the {@code api/event/*} DTOs, so
 * they are frozen as public API.
 *
 * @since 1.0
 * @apiNote 2.0 may revisit zombie forms via the {@code api/registry} form registry; within 1.x these types are
 *          stable.
 */
package dev.molang.iamzombieq.rules.core;
