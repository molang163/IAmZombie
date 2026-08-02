package dev.molang.iamzombieq;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Canonical, dedicated-server-safe physical-client preference schema.
 *
 * <p>The holder deliberately has no client-class dependency. Physical clients
 * migrate its target before registration; dedicated servers neither migrate
 * nor load its CLIENT file.</p>
 */
public final class IAmZombiePreferencesConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HEROBRINE_HEARTBEAT_ENABLED = BUILDER
            .comment("Whether a vanilla heartbeat is layered under the Herobrine silence once the encounter escalates (client-side).")
            .define("herobrineHeartbeatEnabled", true);

    public static final ModConfigSpec.IntValue HEROBRINE_HEARTBEAT_NEAR_DISTANCE = BUILDER
            .comment("Inner distance (blocks) of the Herobrine heartbeat band; at/under this the heartbeat is fastest and loudest.")
            .defineInRange("herobrineHeartbeatNearDistance", 12, 1, 28);

    public static final ModConfigSpec.IntValue HEROBRINE_HEARTBEAT_FAR_DISTANCE = BUILDER
            .comment("Outer distance (blocks) of the Herobrine heartbeat band; beyond this no heartbeat plays.")
            .defineInRange("herobrineHeartbeatFarDistance", 28, 1, 64);

    public static final ModConfigSpec.BooleanValue HEROBRINE_JOLT_VIGNETTE_ENABLED = BUILDER
            .comment("Whether the brief client red vignette is shown for the Herobrine jolt.")
            .define("herobrineJoltVignetteEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IAmZombiePreferencesConfig() {
    }
}
