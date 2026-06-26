/**
 * Food-rule model types. {@link dev.molang.iamzombieq.rules.food.FoodRule} is part of the <b>stable public API
 * surface</b> (semver 1.x): it is exposed through {@code api/extension/IFoodRuleProvider} and the
 * {@code api/event/ZombieEatPreEvent}/{@code ZombieAteEvent} DTOs, so it is frozen as public API (backward-compatible
 * additions only within 1.x). It lives here (outside {@code api/*}) for packaging reasons but is surfaced through
 * {@code api/*}.
 *
 * @since 1.0
 */
package dev.molang.iamzombieq.rules.food;
