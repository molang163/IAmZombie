package dev.molang.iamzombieq.config;

import java.util.Objects;

enum MigrationAccessProfile {
    SECURE,
    BASIC;

    static MigrationAccessProfile select(
            Capabilities capabilities, boolean artifactAlreadyExists) {
        Objects.requireNonNull(capabilities, "capabilities");
        if (artifactAlreadyExists) {
            throw new IllegalStateException(
                    "Migration access profile must be selected before any artifact");
        }

        if (capabilities.isCertifiedSecureTuple()) {
            return SECURE;
        }
        if (capabilities.isCertifiedWindowsBasicTuple()) {
            return BASIC;
        }
        throw new IllegalStateException(
                "Unsupported migration filesystem capability tuple: "
                        + capabilities);
    }

    static Frozen freeze(MigrationAccessProfile profile) {
        return new Frozen(Objects.requireNonNull(profile, "profile"));
    }

    record Capabilities(
            String operatingSystem,
            int javaFeature,
            String providerScheme,
            String providerClass,
            boolean defaultProvider,
            boolean secureDirectoryStream,
            boolean nofollowMetadata,
            boolean nofollowOpen,
            boolean atomicMove) {

        Capabilities {
            operatingSystem = nonBlank(operatingSystem, "operatingSystem");
            if (javaFeature <= 0) {
                throw new IllegalArgumentException(
                        "Invalid Java feature: " + javaFeature);
            }
            providerScheme = nonBlank(providerScheme, "providerScheme");
            providerClass = nonBlank(providerClass, "providerClass");
        }

        private boolean commonCapabilities() {
            return defaultProvider
                    && providerScheme.equals("file")
                    && nofollowMetadata
                    && nofollowOpen
                    && atomicMove;
        }

        private boolean isCertifiedSecureTuple() {
            return operatingSystem.equals("Linux")
                    && javaFeature == 25
                    && providerClass.equals(
                            "sun.nio.fs.LinuxFileSystemProvider")
                    && commonCapabilities()
                    && secureDirectoryStream;
        }

        private boolean isCertifiedWindowsBasicTuple() {
            return operatingSystem.startsWith("Windows")
                    && javaFeature == 25
                    && providerClass.equals(
                            "sun.nio.fs.WindowsFileSystemProvider")
                    && commonCapabilities()
                    && !secureDirectoryStream;
        }
    }

    static final class Frozen {
        private final MigrationAccessProfile profile;

        private Frozen(MigrationAccessProfile profile) {
            this.profile = profile;
        }

        MigrationAccessProfile profile() {
            return profile;
        }

        Frozen transitionTo(MigrationAccessProfile requested) {
            Objects.requireNonNull(requested, "requested");
            if (profile != requested) {
                throw new IllegalStateException(
                        "Migration access profile is frozen as " + profile);
            }
            return this;
        }
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
