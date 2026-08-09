package dev.molang.iamzombieq.util;

import java.util.Set;

/**
 * Test-side view of the centralized Stonecutter platform capability matrix.
 *
 * <p>The authority lives in {@code stonecutter.properties.toml}; Gradle injects the active row. Tests use this
 * class so node predicates and the two explicit N/A GameTest IDs are not copied across source guards.
 */
public final class StonecutterCapabilityMatrix {
    public static final String NAUTILUS_PRESENT = "PRESENT";
    public static final String NAUTILUS_PLATFORM_ABSENT = "N/A_PLATFORM_ABSENT";
    public static final String SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT = "PRESENT";
    public static final String SUBMIT_NODE_COLLECTOR_PIPELINE_ABSENT =
            "N/A_PLATFORM_ABSENT";
    public static final Set<String> NAUTILUS_NA_GAMETEST_IDS = Set.of(
            "reg_nautilus_saddle_not_fabricated",
            "reg_giant_aura_spares_owned_nautilus_stomps_wild");

    private static final Set<String> KNOWN_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
    private static final Set<String> GAME_TEST_BOOLEAN_STRING_ASSERTION_OVERLOAD_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> CONNECTION_OWNED_CLIENT_LOADED_STATE_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> ENTITY_REFERENCE_PERSISTENT_ANGER_TARGET_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> PERSISTENT_ANGER_END_TIME_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> PUBLIC_ZOMBIFIED_PIGLIN_DEFAULT_EQUIPMENT_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> PUBLIC_HUMANOID_MODEL_ARM_ACCESS_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> KINETIC_HIT_FEEDBACK_RENDER_STATE_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> HELD_ITEM_STACK_PAYLOAD_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11");
    private static final Set<String> DISTINCT_BABY_MONSTER_TEXTURE_NODES =
            Set.of("26.2.x", "26.1.x");
    private static final Set<String> SKULL_MODEL_TEXTURE_REGISTRATION_OVERLOAD_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10");
    private static final Set<String> GENERIC_RENDER_PLAYER_EVENT_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10");
    private static final Set<String> ARMOR_MODEL_SET_NODES =
            Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10");

    private StonecutterCapabilityMatrix() {
    }

    public static String nodeId() {
        String node = System.getProperty("iamzombieq.test.nodeId");
        if (!KNOWN_NODES.contains(node)) {
            throw new IllegalStateException("Unknown Stonecutter test node: " + node);
        }
        return node;
    }

    public static String nautilusStatus() {
        String status = System.getProperty("iamzombieq.test.platform.nautilus");
        if (!Set.of(NAUTILUS_PRESENT, NAUTILUS_PLATFORM_ABSENT).contains(status)) {
            throw new IllegalStateException("Unknown Nautilus platform capability: " + status);
        }
        return status;
    }

    public static boolean hasNautilusEntityApi() {
        return nautilusStatus().equals(NAUTILUS_PRESENT);
    }

    public static String activeEntityTypeHolder() {
        return nodeId().equals("26.2.x") ? "Entity" + "Types" : "Entity" + "Type";
    }

    /**
     * Whether {@code GameTestHelper.assertTrue/assertFalse(boolean, String)} are native node overloads.
     */
    public static boolean hasNativeGameTestBooleanStringAssertionOverloads() {
        return GAME_TEST_BOOLEAN_STRING_ASSERTION_OVERLOAD_NODES.contains(nodeId());
    }

    /**
     * Whether the PlayerLoaded handshake state is owned by {@code ServerGamePacketListenerImpl}.
     */
    public static boolean hasConnectionOwnedClientLoadedState() {
        return CONNECTION_OWNED_CLIENT_LOADED_STATE_NODES.contains(nodeId());
    }

    /**
     * Whether {@code NeutralMob#setPersistentAngerTarget} accepts an {@code EntityReference}.
     */
    public static boolean hasEntityReferencePersistentAngerTargetApi() {
        return ENTITY_REFERENCE_PERSISTENT_ANGER_TARGET_NODES.contains(nodeId());
    }

    /**
     * Whether {@code NeutralMob} stores persistent anger as an absolute end time.
     */
    public static boolean hasPersistentAngerEndTimeApi() {
        return PERSISTENT_ANGER_END_TIME_NODES.contains(nodeId());
    }

    /**
     * Whether {@code ZombifiedPiglin#populateDefaultEquipmentSlots} is a public API.
     */
    public static boolean hasPublicZombifiedPiglinDefaultEquipmentApi() {
        return PUBLIC_ZOMBIFIED_PIGLIN_DEFAULT_EQUIPMENT_NODES.contains(nodeId());
    }

    /**
     * Whether {@code HumanoidModel#getArm} is a public model API.
     */
    public static boolean hasPublicHumanoidModelArmAccess() {
        return PUBLIC_HUMANOID_MODEL_ARM_ACCESS_NODES.contains(nodeId());
    }

    /**
     * Whether living render states carry the kinetic-hit feedback animation timer.
     */
    public static boolean hasKineticHitFeedbackRenderStateApi() {
        return KINETIC_HIT_FEEDBACK_RENDER_STATE_NODES.contains(nodeId());
    }

    /**
     * Whether held-item render states retain the raw stack and swing payload.
     */
    public static boolean hasHeldItemStackPayloadApi() {
        return HELD_ITEM_STACK_PAYLOAD_NODES.contains(nodeId());
    }

    /**
     * Whether held items use the submit-node collector pipeline instead of the legacy render pipeline.
     */
    public static boolean hasHeldItemCollectorSubmitApi() {
        return submitNodeCollectorRenderPipelineStatus()
                .equals(SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT);
    }

    /**
     * Whether player rendering uses AvatarRenderer's submit-node collector pipeline.
     */
    public static boolean hasPlayerRenderSubmitPipeline() {
        return submitNodeCollectorRenderPipelineStatus()
                .equals(SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT);
    }

    public static String submitNodeCollectorRenderPipelineStatus() {
        String status = System.getProperty(
                "iamzombieq.test.platform.submit_node_collector_render_pipeline");
        if (!Set.of(
                        SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT,
                        SUBMIT_NODE_COLLECTOR_PIPELINE_ABSENT)
                .contains(status)) {
            throw new IllegalStateException(
                    "Unknown submit-node collector render pipeline capability: " + status);
        }
        return status;
    }

    /**
     * Whether vanilla provides distinct zombie, drowned, husk, and zombified-piglin baby textures.
     */
    public static boolean hasDistinctBabyMonsterTextures() {
        return DISTINCT_BABY_MONSTER_TEXTURE_NODES.contains(nodeId());
    }

    /**
     * Whether {@code CreateSkullModels#registerSkullModel} accepts a texture identifier.
     */
    public static boolean hasSkullModelTextureRegistrationOverload() {
        return SKULL_MODEL_TEXTURE_REGISTRATION_OVERLOAD_NODES.contains(nodeId());
    }

    /**
     * Whether {@code RenderPlayerEvent.Pre} carries the rendered avatar type parameter.
     */
    public static boolean hasGenericRenderPlayerEvent() {
        return GENERIC_RENDER_PLAYER_EVENT_NODES.contains(nodeId());
    }

    /**
     * Whether humanoid armor layers use adult/baby {@code ArmorModelSet} values.
     */
    public static boolean hasArmorModelSetApi() {
        return ARMOR_MODEL_SET_NODES.contains(nodeId());
    }

    public static int activeNautilusRequiredGameTests() {
        return hasNautilusEntityApi() ? NAUTILUS_NA_GAMETEST_IDS.size() : 0;
    }

    public static int expectedModGameTests() {
        return 83 + activeNautilusRequiredGameTests();
    }

    public static int expectedTotalGameTests() {
        return expectedModGameTests() + 1;
    }

    public static int expectedFixRegressionBodies() {
        return 8 + activeNautilusRequiredGameTests();
    }

    public static int expectedLegacyPadding8() {
        return 80 + activeNautilusRequiredGameTests();
    }
}
