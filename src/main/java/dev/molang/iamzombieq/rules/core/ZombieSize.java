package dev.molang.iamzombieq.rules.core;

/**
 * The zombie player's body size. Stable public API (1.x): exposed via {@code api/*} (e.g. {@code IZombiePlayer});
 * backward-compatible additions only within 1.x.
 *
 * @since 1.0
 */
public enum ZombieSize {
    ADULT("adult"),
    BABY("baby");

    private final String id;

    ZombieSize(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ZombieSize byId(String id) {
        for (ZombieSize size : values()) {
            if (size.id.equals(id)) {
                return size;
            }
        }
        return ADULT;
    }
}
