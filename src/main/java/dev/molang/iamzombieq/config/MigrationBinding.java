package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

record MigrationBinding(
        Path target,
        Path logicalParent,
        Path physicalParent,
        List<Ancestor> ancestors,
        String directoryIdentity,
        String providerIdentity,
        String fileStoreIdentity,
        int javaFeature,
        String operatingSystem) {

    MigrationBinding {
        target = normalizedAbsolute(target, "target");
        logicalParent = normalizedAbsolute(logicalParent, "logicalParent");
        physicalParent = normalizedAbsolute(physicalParent, "physicalParent");
        if (!logicalParent.equals(target.getParent())) {
            throw new IllegalArgumentException(
                    "Logical parent does not contain target: " + target);
        }
        ancestors = List.copyOf(Objects.requireNonNull(ancestors, "ancestors"));
        if (ancestors.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one ancestor identity is required");
        }
        directoryIdentity = nonBlank(directoryIdentity, "directoryIdentity");
        providerIdentity = nonBlank(providerIdentity, "providerIdentity");
        fileStoreIdentity = nonBlank(fileStoreIdentity, "fileStoreIdentity");
        if (javaFeature <= 0) {
            throw new IllegalArgumentException("Invalid Java feature: " + javaFeature);
        }
        operatingSystem = nonBlank(operatingSystem, "operatingSystem");
    }

    static MigrationBinding capture(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        return new MigrationBinding(
                observation.target(),
                observation.logicalParent(),
                observation.physicalParent(),
                observation.ancestors(),
                observation.directoryIdentity(),
                observation.providerIdentity(),
                observation.fileStoreIdentity(),
                observation.javaFeature(),
                observation.operatingSystem());
    }

    void verifyUnchanged(Observation observation) {
        MigrationBinding current;
        try {
            current = capture(observation);
        } catch (IllegalArgumentException invalidObservation) {
            throw new IllegalStateException(
                    "Migration target parent binding became invalid",
                    invalidObservation);
        }
        if (!equals(current)) {
            throw new IllegalStateException(
                    "Migration target parent binding changed from "
                            + this
                            + " to "
                            + current);
        }
    }

    Observation toObservation() {
        return new Observation(
                target,
                logicalParent,
                physicalParent,
                ancestors,
                directoryIdentity,
                providerIdentity,
                fileStoreIdentity,
                javaFeature,
                operatingSystem);
    }

    record Ancestor(Path path, String identity) {
        Ancestor {
            path = normalizedAbsolute(path, "ancestor path");
            identity = nonBlank(identity, "ancestor identity");
        }
    }

    record Observation(
            Path target,
            Path logicalParent,
            Path physicalParent,
            List<Ancestor> ancestors,
            String directoryIdentity,
            String providerIdentity,
            String fileStoreIdentity,
            int javaFeature,
            String operatingSystem) {

        Observation {
            target = Objects.requireNonNull(target, "target");
            logicalParent = Objects.requireNonNull(logicalParent, "logicalParent");
            physicalParent = Objects.requireNonNull(physicalParent, "physicalParent");
            ancestors = List.copyOf(Objects.requireNonNull(ancestors, "ancestors"));
            directoryIdentity =
                    Objects.requireNonNull(directoryIdentity, "directoryIdentity");
            providerIdentity =
                    Objects.requireNonNull(providerIdentity, "providerIdentity");
            fileStoreIdentity =
                    Objects.requireNonNull(fileStoreIdentity, "fileStoreIdentity");
            operatingSystem =
                    Objects.requireNonNull(operatingSystem, "operatingSystem");
        }

        Observation withTarget(Path value) {
            return new Observation(
                    value,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withLogicalParent(Path value) {
            return new Observation(
                    target,
                    value,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withPhysicalParent(Path value) {
            return new Observation(
                    target,
                    logicalParent,
                    value,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withAncestors(List<Ancestor> value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    value,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withDirectoryIdentity(String value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    value,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withProviderIdentity(String value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    value,
                    fileStoreIdentity,
                    javaFeature,
                    operatingSystem);
        }

        Observation withFileStoreIdentity(String value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    value,
                    javaFeature,
                    operatingSystem);
        }

        Observation withJavaFeature(int value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    value,
                    operatingSystem);
        }

        Observation withOperatingSystem(String value) {
            return new Observation(
                    target,
                    logicalParent,
                    physicalParent,
                    ancestors,
                    directoryIdentity,
                    providerIdentity,
                    fileStoreIdentity,
                    javaFeature,
                    value);
        }
    }

    private static Path normalizedAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (!path.isAbsolute() || !path.equals(path.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    field + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
