package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

record MigrationEvidence(
        MigrationTarget targetKind,
        Path target,
        MigrationBinding binding,
        String schemaVersion,
        MigrationAccessProfile profile,
        Durability commitProfile,
        String lockIdentity,
        String phase,
        String projectionSha256,
        String rawLegacySha256,
        Map<String, String> artifactHashes,
        Map<String, Durability> artifactDurability) {

    MigrationEvidence {
        Objects.requireNonNull(targetKind, "targetKind");
        target = normalizedAbsolute(target, "target");
        binding = Objects.requireNonNull(binding, "binding");
        if (!target.equals(binding.target())) {
            throw new IllegalArgumentException(
                    "Evidence target does not match its parent binding");
        }
        schemaVersion = nonBlank(schemaVersion, "schemaVersion");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(commitProfile, "commitProfile");
        lockIdentity = nonBlank(lockIdentity, "lockIdentity");
        phase = nonBlank(phase, "phase");
        projectionSha256 = sha256(projectionSha256, "projectionSha256");
        rawLegacySha256 = sha256(rawLegacySha256, "rawLegacySha256");
        artifactHashes = immutableHashes(artifactHashes);
        artifactDurability = immutableDurability(artifactDurability);
        if (!artifactHashes.keySet().equals(artifactDurability.keySet())) {
            throw new IllegalArgumentException(
                    "Artifact hash and durability keys must match");
        }
    }

    static Builder builder(MigrationTarget targetKind) {
        return new Builder(targetKind);
    }

    void verifyBoundTo(
            Path expectedTarget,
            MigrationBinding expectedBinding,
            MigrationAccessProfile expectedProfile) {
        verifyBoundTo(
                expectedTarget,
                expectedBinding,
                expectedProfile,
                commitProfile);
    }

    void verifyBoundTo(
            Path expectedTarget,
            MigrationBinding expectedBinding,
            MigrationAccessProfile expectedProfile,
            Durability expectedCommitProfile) {
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        Objects.requireNonNull(expectedBinding, "expectedBinding");
        Objects.requireNonNull(expectedProfile, "expectedProfile");
        Objects.requireNonNull(
                expectedCommitProfile, "expectedCommitProfile");
        if (!expectedTarget.isAbsolute()
                || !expectedTarget.equals(expectedTarget.normalize())
                || !target.equals(expectedTarget)
                || !binding.equals(expectedBinding)
                || profile != expectedProfile
                || commitProfile != expectedCommitProfile) {
            throw new IllegalStateException(
                    "Migration evidence is not bound to the requested target, "
                            + "parent, access profile, and commit profile");
        }
    }

    enum Durability {
        BASIC,
        STRONG
    }

    static final class Builder {
        private final MigrationTarget targetKind;
        private Path target;
        private MigrationBinding binding;
        private String schemaVersion;
        private MigrationAccessProfile profile;
        private Durability commitProfile = Durability.BASIC;
        private String lockIdentity;
        private String phase;
        private String projectionSha256;
        private String rawLegacySha256;
        private Map<String, String> artifactHashes;
        private Map<String, Durability> artifactDurability;

        private Builder(MigrationTarget targetKind) {
            this.targetKind = Objects.requireNonNull(targetKind, "targetKind");
        }

        Builder target(Path value) {
            target = value;
            return this;
        }

        Builder binding(MigrationBinding value) {
            binding = value;
            return this;
        }

        Builder schemaVersion(String value) {
            schemaVersion = value;
            return this;
        }

        Builder profile(MigrationAccessProfile value) {
            profile = value;
            return this;
        }

        Builder commitProfile(Durability value) {
            commitProfile = value;
            return this;
        }

        Builder lockIdentity(String value) {
            lockIdentity = value;
            return this;
        }

        Builder phase(String value) {
            phase = value;
            return this;
        }

        Builder projectionSha256(String value) {
            projectionSha256 = value;
            return this;
        }

        Builder rawLegacySha256(String value) {
            rawLegacySha256 = value;
            return this;
        }

        Builder artifactHashes(Map<String, String> value) {
            artifactHashes = value;
            return this;
        }

        Builder artifactDurability(Map<String, Durability> value) {
            artifactDurability = value;
            return this;
        }

        MigrationEvidence build() {
            return new MigrationEvidence(
                    targetKind,
                    target,
                    binding,
                    schemaVersion,
                    profile,
                    commitProfile,
                    lockIdentity,
                    phase,
                    projectionSha256,
                    rawLegacySha256,
                    artifactHashes,
                    artifactDurability);
        }
    }

    private static Map<String, String> immutableHashes(
            Map<String, String> hashes) {
        Objects.requireNonNull(hashes, "artifactHashes");
        if (hashes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Artifact hashes must not be empty");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        hashes.forEach((key, value) -> {
            String checkedKey = nonBlank(key, "artifact hash key");
            String checkedValue = sha256(value, "artifact hash " + checkedKey);
            if (copy.putIfAbsent(checkedKey, checkedValue) != null) {
                throw new IllegalArgumentException(
                        "Duplicate artifact hash key: " + checkedKey);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Durability> immutableDurability(
            Map<String, Durability> durability) {
        Objects.requireNonNull(durability, "artifactDurability");
        if (durability.isEmpty()) {
            throw new IllegalArgumentException(
                    "Artifact durability must not be empty");
        }
        LinkedHashMap<String, Durability> copy = new LinkedHashMap<>();
        durability.forEach((key, value) -> {
            String checkedKey = nonBlank(key, "artifact durability key");
            Durability checkedValue =
                    Objects.requireNonNull(value, "artifact durability value");
            if (copy.putIfAbsent(checkedKey, checkedValue) != null) {
                throw new IllegalArgumentException(
                        "Duplicate artifact durability key: " + checkedKey);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Path normalizedAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (!path.isAbsolute() || !path.equals(path.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    field + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static String sha256(String value, String field) {
        value = nonBlank(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase SHA-256 value");
        }
        return value;
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
