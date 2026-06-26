package dev.molang.iamzombieq.rules.core;

/**
 * The zombie player's form. Stable public API (1.x): exposed via {@code api/*} (e.g. {@code IZombiePlayer},
 * the transform/evolve event DTOs); backward-compatible additions only within 1.x. (2.0 may revisit forms via the
 * {@code api/registry} form registry.)
 *
 * @since 1.0
 */
public enum ZombieForm {
    NORMAL("normal"),
    DROWNED("drowned"),
    HUSK("husk"),
    ZOMBIFIED_PIGLIN("zombified_piglin"),
    GIANT("giant");

    private final String id;

    ZombieForm(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ZombieForm byId(String id) {
        for (ZombieForm form : values()) {
            if (form.id.equals(id)) {
                return form;
            }
        }
        return NORMAL;
    }
}
