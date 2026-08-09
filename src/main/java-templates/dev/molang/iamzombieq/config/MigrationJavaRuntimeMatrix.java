package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Set;

/** Exact Java runtime and migration-profile matrix generated for one node. */
final class MigrationJavaRuntimeMatrix {
    private static final String NODE_ID = "${node_id}";
    private static final Set<Integer> RUNTIME_FEATURES =
            Set.of(${runtime_java_features});

    private MigrationJavaRuntimeMatrix() {
    }

    static Set<Integer> runtimeFeatures() {
        return RUNTIME_FEATURES;
    }

    static boolean supportsSecureProfile(int javaFeature) {
        return RUNTIME_FEATURES.contains(javaFeature);
    }

    static boolean supportsBasicProfile(int javaFeature) {
        return RUNTIME_FEATURES.contains(javaFeature);
    }

    static void requireSupported(Path legacy, Path target) {
        requireSupported(Runtime.version().feature(), legacy, target);
    }

    static void requireSupported(
            int javaFeature, Path legacy, Path target) {
        if (RUNTIME_FEATURES.contains(javaFeature)) {
            return;
        }
        throw MigrationFailure.operational(
                legacy,
                target,
                MigrationTargetState.Phase.NO_EVIDENCE,
                "java-runtime",
                "runtime-java-policy",
                "Java feature "
                        + javaFeature
                        + " is not approved for Stonecutter node "
                        + NODE_ID
                        + "; approved exact features are "
                        + RUNTIME_FEATURES
                        + ". Migration stopped before metadata read, lock, "
                        + "stage, or target access",
                null);
    }
}
