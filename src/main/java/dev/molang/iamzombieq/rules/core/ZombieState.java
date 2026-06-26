package dev.molang.iamzombieq.rules.core;

/**
 * The zombie player's full {@code (form, size)} state. Stable public API (1.x): exposed via {@code api/*}
 * (e.g. {@code IZombiePlayer}, the evolve event DTOs); backward-compatible additions only within 1.x.
 *
 * @since 1.0
 */
public record ZombieState(ZombieForm form, ZombieSize size) {
    public static final ZombieState DEFAULT = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);

    public ZombieState asAdult() {
        return new ZombieState(form, ZombieSize.ADULT);
    }
}
