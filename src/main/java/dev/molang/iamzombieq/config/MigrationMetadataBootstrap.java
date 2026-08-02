package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class MigrationMetadataBootstrap {
    private final Port port;

    MigrationMetadataBootstrap(Port port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    Result inspect(Candidates candidates) {
        Objects.requireNonNull(candidates, "candidates");
        LinkedHashMap<Path, Observation> observations = new LinkedHashMap<>();
        boolean allAbsent = true;

        for (Path candidate : candidates.fixedCandidates()) {
            Observation observation;
            try {
                observation = observePort(candidate);
            } catch (RuntimeException failure) {
                throw operationalFailure(
                        candidates,
                        candidates.targetFor(candidate),
                        candidate,
                        "bootstrap-metadata",
                        "Initial NOFOLLOW metadata inspection failed",
                        failure);
            }
            observations.put(candidate, observation);
            if (observation.state() != MigrationPathState.ABSENT) {
                allAbsent = false;
            }
        }

        Path operationalTarget =
                candidates.operationalTarget(observations);
        if (allAbsent) {
            return new Result(
                    Kind.FRESH,
                    observations,
                    candidates,
                    operationalTarget);
        }

        return requireSession(
                observations, candidates, operationalTarget);
    }

    Result inspectLegacyAfterTargetFresh(Result targetFresh) {
        Objects.requireNonNull(targetFresh, "targetFresh");
        if (targetFresh.kind() != Kind.FRESH
                || targetFresh.candidates().fixedCandidates().contains(
                        targetFresh.candidates().legacy())) {
            throw new IllegalArgumentException(
                    "Legacy may only be inspected after a legacy-free actual "
                            + "target bootstrap proved every target artifact "
                            + "absent");
        }

        Candidates expanded =
                targetFresh.candidates().withLegacyForActualTarget();
        Path legacy = expanded.legacy();
        Observation legacyObservation;
        try {
            legacyObservation = observePort(legacy);
        } catch (RuntimeException failure) {
            throw operationalFailure(
                    expanded,
                    targetFresh.operationalTarget(),
                    legacy,
                    "bootstrap-legacy-metadata",
                    "Legacy NOFOLLOW metadata inspection failed after the "
                            + "actual target was safely absent",
                    failure);
        }

        LinkedHashMap<Path, Observation> observations =
                new LinkedHashMap<>(targetFresh.observations());
        observations.put(legacy, legacyObservation);
        if (legacyObservation.state() == MigrationPathState.ABSENT) {
            return new Result(
                    Kind.FRESH,
                    observations,
                    expanded,
                    targetFresh.operationalTarget());
        }
        port.prepareLegacySession();
        return requireSession(
                observations,
                expanded,
                targetFresh.operationalTarget());
    }

    private Result requireSession(
            Map<Path, Observation> observations,
            Candidates candidates,
            Path operationalTarget) {
        try {
            port.selectProfile();
        } catch (RuntimeException failure) {
            throw operationalFailure(
                    candidates,
                    operationalTarget,
                    operationalTarget,
                    "bootstrap-profile-selection",
                    "Migration access profile selection failed",
                    failure);
        }
        try {
            port.openSession();
        } catch (RuntimeException failure) {
            throw operationalFailure(
                    candidates,
                    operationalTarget,
                    operationalTarget,
                    "bootstrap-session-open",
                    "Migration directory session open failed",
                    failure);
        }
        return new Result(
                Kind.REQUIRES_SESSION,
                observations,
                candidates,
                operationalTarget);
    }

    void revalidateThroughSession(Result result, SessionMetadataReader reader) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(reader, "reader");
        if (result.kind() != Kind.REQUIRES_SESSION) {
            throw new IllegalStateException(
                    "Fresh bootstrap result cannot open a migration session");
        }

        for (Path candidate : result.candidates()
                .selectedCandidates(result.operationalTarget())) {
            Map.Entry<Path, Observation> entry = Map.entry(
                    candidate, result.observations().get(candidate));
            Observation current;
            try {
                current = observe(reader, entry.getKey());
            } catch (RuntimeException failure) {
                throw operationalFailure(
                        result.candidates(),
                        result.candidates().targetFor(entry.getKey()),
                        entry.getKey(),
                        "bootstrap-session-metadata",
                        "Bound-session metadata revalidation failed",
                        failure);
            }
            if (!entry.getValue().equals(current)) {
                throw operationalFailure(
                        result.candidates(),
                        result.candidates().targetFor(entry.getKey()),
                        entry.getKey(),
                        "bootstrap-session-revalidation",
                        "Migration bootstrap metadata changed before session access",
                        null);
            }
        }
    }

    void revalidateFreshAfterNamespaceBinding(
            Result result, SessionMetadataReader reader) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(reader, "reader");
        if (result.kind() != Kind.FRESH) {
            throw new IllegalStateException(
                    "Only a fresh bootstrap result can use fresh metadata "
                            + "revalidation");
        }

        for (Path candidate : result.candidates().fixedCandidates()) {
            Observation expected = result.observations().get(candidate);
            Observation current;
            try {
                current = observe(reader, candidate);
            } catch (RuntimeException failure) {
                throw operationalFailure(
                        result.candidates(),
                        result.candidates().targetFor(candidate),
                        candidate,
                        "fresh-namespace-revalidation",
                        "Fresh NOFOLLOW metadata revalidation failed after "
                                + "binding the target namespace",
                        failure);
            }
            if (!expected.equals(current)) {
                throw operationalFailure(
                        result.candidates(),
                        result.candidates().targetFor(candidate),
                        candidate,
                        "fresh-namespace-revalidation",
                        "A migration candidate changed while binding the "
                                + "fresh target namespace",
                        null);
            }
        }
    }

    private static MigrationFailure operationalFailure(
            Candidates candidates,
            Path target,
            Path artifact,
            String operation,
            String reason,
            Throwable cause) {
        return MigrationFailure.operational(
                candidates.legacy(),
                target,
                MigrationTargetState.Phase.NO_EVIDENCE,
                artifact.getFileName().toString(),
                operation,
                reason,
                cause);
    }

    private Observation observePort(Path candidate) {
        try {
            return present(candidate, port.readNofollowMetadata(candidate));
        } catch (NoSuchFileException absent) {
            return new Observation(MigrationPathState.ABSENT, null);
        } catch (IOException failure) {
            throw untrusted(candidate, "metadata query failed", failure);
        }
    }

    private static Observation observe(
            SessionMetadataReader reader, Path candidate) {
        try {
            return present(candidate, reader.read(candidate));
        } catch (NoSuchFileException absent) {
            return new Observation(MigrationPathState.ABSENT, null);
        } catch (IOException failure) {
            throw untrusted(candidate, "metadata query failed", failure);
        }
    }

    private static Observation present(
            Path candidate, MigrationPathState.Metadata metadata) {
        if (metadata == null) {
            throw untrusted(candidate, "metadata result was null", null);
        }
        if (metadata.symbolicLink() || !metadata.regularFile()) {
            throw untrusted(
                    candidate,
                    "candidate is a symbolic link or non-regular file",
                    null);
        }
        if (metadata.identity() == null
                || metadata.identity().isBlank()
                || metadata.size() < 0) {
            throw untrusted(candidate, "candidate metadata is incomplete", null);
        }
        return new Observation(MigrationPathState.PRESENT, metadata);
    }

    private static IllegalStateException untrusted(
            Path candidate, String reason, Throwable cause) {
        String message =
                "Migration bootstrap cannot trust " + candidate + ": " + reason;
        return cause == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }

    enum Kind {
        FRESH,
        REQUIRES_SESSION
    }

    record Result(
            Kind kind,
            Map<Path, Observation> observations,
            Candidates candidates,
            Path operationalTarget) {
        Result {
            Objects.requireNonNull(kind, "kind");
            observations = Map.copyOf(
                    Objects.requireNonNull(observations, "observations"));
            candidates = Objects.requireNonNull(candidates, "candidates");
            operationalTarget = Candidates.requireNormalizedAbsolute(
                    operationalTarget);
            if (observations.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bootstrap observations cannot be empty");
            }
        }
    }

    record Observation(
            MigrationPathState state, MigrationPathState.Metadata metadata) {
        Observation {
            Objects.requireNonNull(state, "state");
            if (state == MigrationPathState.PRESENT && metadata == null) {
                throw new IllegalArgumentException(
                        "Present bootstrap observation needs metadata");
            }
            if (state == MigrationPathState.ABSENT && metadata != null) {
                throw new IllegalArgumentException(
                        "Absent bootstrap observation cannot have metadata");
            }
            if (state == MigrationPathState.UNKNOWN
                    || state == MigrationPathState.UNSAFE) {
                throw new IllegalArgumentException(
                        "Untrusted bootstrap states cannot be retained");
            }
        }
    }

    record Candidates(
            List<Path> fixedCandidates,
            Path legacy,
            Map<Path, Path> owningTargets,
            Set<MigrationTarget> applicableTargets) {
        Candidates {
            fixedCandidates = List.copyOf(
                    Objects.requireNonNull(fixedCandidates, "fixedCandidates"));
            legacy = requireNormalizedAbsolute(legacy);
            owningTargets = Map.copyOf(
                    Objects.requireNonNull(owningTargets, "owningTargets"));
            applicableTargets = Set.copyOf(
                    Objects.requireNonNull(
                            applicableTargets, "applicableTargets"));
            if (fixedCandidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bootstrap candidate set cannot be empty");
            }
            if (applicableTargets.isEmpty()
                    || !Set.of(
                                    MigrationTarget.SERVER,
                                    MigrationTarget.PREFERENCES)
                            .containsAll(applicableTargets)) {
                throw new IllegalArgumentException(
                        "Bootstrap applicable targets must be a non-empty "
                                + "subset of server/preferences");
            }
            for (Path candidate : fixedCandidates) {
                requireNormalizedAbsolute(candidate);
            }
            if (fixedCandidates.stream().distinct().count()
                    != fixedCandidates.size()) {
                throw new IllegalArgumentException(
                        "Bootstrap candidates must be distinct");
            }
            if (!owningTargets.keySet().equals(
                    new LinkedHashSet<>(fixedCandidates))) {
                throw new IllegalArgumentException(
                        "Every bootstrap candidate needs one owning target");
            }
            for (Path target : owningTargets.values()) {
                requireNormalizedAbsolute(target);
            }
        }

        static Candidates dedicated(
                Path globalConfigParent,
                Path worldServerConfigParent,
                Path legacyCommon) {
            return create(
                    globalConfigParent,
                    worldServerConfigParent,
                    legacyCommon,
                    false);
        }

        static Candidates integrated(
                Path globalConfigParent,
                Path worldServerConfigParent,
                Path legacyCommon) {
            return create(
                    globalConfigParent,
                    worldServerConfigParent,
                    legacyCommon,
                    true);
        }

        static Candidates preferences(
                Path globalConfigParent, Path legacyCommon) {
            Path globalParent =
                    requireNormalizedAbsolute(globalConfigParent);
            Path legacy = requireNormalizedAbsolute(legacyCommon);
            if (!globalParent.equals(legacy.getParent())) {
                throw new IllegalArgumentException(
                        "Legacy COMMON must be in the global config parent: "
                                + legacy);
            }
            Path target = ActualTargetResolver.fixedChild(
                    globalParent,
                    ActualTargetResolver.PREFERENCES_BASENAME);
            LinkedHashSet<Path> candidates = new LinkedHashSet<>();
            LinkedHashMap<Path, Path> owningTargets =
                    new LinkedHashMap<>();
            addTarget(candidates, owningTargets, target);
            candidates.add(legacy);
            owningTargets.put(legacy, target);
            return new Candidates(
                    List.copyOf(candidates),
                    legacy,
                    owningTargets,
                    Set.of(MigrationTarget.PREFERENCES));
        }

        static Candidates actualTargetOnly(
                MigrationTarget targetKind,
                Path actualTarget,
                Path legacyCommon) {
            Objects.requireNonNull(targetKind, "targetKind");
            Path target = requireNormalizedAbsolute(actualTarget);
            Path legacy = requireNormalizedAbsolute(legacyCommon);
            LinkedHashSet<Path> candidates = new LinkedHashSet<>();
            LinkedHashMap<Path, Path> owningTargets =
                    new LinkedHashMap<>();
            addTarget(candidates, owningTargets, target);
            return new Candidates(
                    List.copyOf(candidates),
                    legacy,
                    owningTargets,
                    Set.of(targetKind));
        }

        private static Candidates create(
                Path globalConfigParent,
                Path worldServerConfigParent,
                Path legacyCommon,
                boolean includePreferences) {
            Path globalParent = requireNormalizedAbsolute(globalConfigParent);
            Path worldParent =
                    requireNormalizedAbsolute(worldServerConfigParent);
            Path legacy = requireNormalizedAbsolute(legacyCommon);
            if (!globalParent.equals(legacy.getParent())) {
                throw new IllegalArgumentException(
                        "Legacy COMMON must be in the global config parent: "
                                + legacy);
            }

            LinkedHashSet<Path> candidates = new LinkedHashSet<>();
            LinkedHashMap<Path, Path> owningTargets =
                    new LinkedHashMap<>();
            Path worldTarget = ActualTargetResolver.fixedChild(
                    worldParent, ActualTargetResolver.SERVER_BASENAME);
            Path globalTarget = ActualTargetResolver.fixedChild(
                    globalParent, ActualTargetResolver.SERVER_BASENAME);
            addTarget(
                    candidates,
                    owningTargets,
                    worldTarget);
            addTarget(
                    candidates,
                    owningTargets,
                    globalTarget);
            candidates.add(legacy);
            owningTargets.put(legacy, globalTarget);
            if (includePreferences) {
                addTarget(
                        candidates,
                        owningTargets,
                        ActualTargetResolver.fixedChild(
                                globalParent,
                                ActualTargetResolver.PREFERENCES_BASENAME));
            }
            return new Candidates(
                    List.copyOf(candidates),
                    legacy,
                    owningTargets,
                    includePreferences
                            ? Set.of(
                                    MigrationTarget.SERVER,
                                    MigrationTarget.PREFERENCES)
                            : Set.of(MigrationTarget.SERVER));
        }

        private static void addTarget(
                LinkedHashSet<Path> candidates,
                LinkedHashMap<Path, Path> owningTargets,
                Path target) {
            candidates.add(target);
            owningTargets.put(target, target);
            for (Path artifact :
                    MigrationFileSystem.ArtifactPaths.forTarget(target)
                            .fixedCandidates()) {
                candidates.add(artifact);
                owningTargets.put(artifact, target);
            }
        }

        Path targetFor(Path candidate) {
            Path target = owningTargets.get(candidate);
            if (target == null) {
                throw new IllegalArgumentException(
                        "Path is outside the fixed bootstrap candidates: "
                                + candidate);
            }
            return target;
        }

        Path operationalTarget(Map<Path, Observation> observations) {
            for (Path candidate : fixedCandidates) {
                Observation observation = observations.get(candidate);
                if (observation != null
                        && observation.state() != MigrationPathState.ABSENT
                        && !candidate.equals(legacy)) {
                    return targetFor(candidate);
                }
            }
            if (fixedCandidates.contains(legacy)) {
                return targetFor(legacy);
            }
            Set<Path> targets = Set.copyOf(owningTargets.values());
            if (targets.size() != 1) {
                throw new IllegalStateException(
                        "A legacy-free bootstrap must bind exactly one actual "
                                + "target");
            }
            return targets.iterator().next();
        }

        Candidates withLegacyForActualTarget() {
            if (fixedCandidates.contains(legacy)) {
                return this;
            }
            Set<Path> targets = Set.copyOf(owningTargets.values());
            if (targets.size() != 1) {
                throw new IllegalStateException(
                        "Legacy can only be added after one actual target "
                                + "has been selected");
            }
            Path actualTarget = targets.iterator().next();
            LinkedHashSet<Path> candidates =
                    new LinkedHashSet<>(fixedCandidates);
            LinkedHashMap<Path, Path> expandedOwners =
                    new LinkedHashMap<>(owningTargets);
            candidates.add(legacy);
            expandedOwners.put(legacy, actualTarget);
            return new Candidates(
                    List.copyOf(candidates),
                    legacy,
                    expandedOwners,
                    applicableTargets);
        }

        List<Path> selectedCandidates(Path actualTarget) {
            Path selected = requireNormalizedAbsolute(actualTarget);
            boolean knownTarget = owningTargets.values().stream()
                    .anyMatch(selected::equals);
            if (!knownTarget) {
                throw new IllegalArgumentException(
                        "Selected target is outside the bootstrap candidates: "
                                + selected);
            }
            return fixedCandidates.stream()
                    .filter(candidate -> candidate.equals(legacy)
                            || selected.equals(targetFor(candidate)))
                    .toList();
        }

        static Path requireNormalizedAbsolute(Path path) {
            Objects.requireNonNull(path, "path");
            if (!path.isAbsolute()
                    || !path.equals(path.toAbsolutePath().normalize())
                    || path.getFileName() == null) {
                throw new IllegalArgumentException(
                        "Bootstrap path must be normalized and absolute: " + path);
            }
            return path;
        }
    }

    @FunctionalInterface
    interface SessionMetadataReader {
        MigrationPathState.Metadata read(Path path) throws IOException;
    }

    interface Port {
        MigrationPathState.Metadata readNofollowMetadata(Path path)
                throws IOException;

        void selectProfile();

        void openSession();

        byte[] readContent(Path path);

        void hashContent();

        void createDirectory();

        void createArtifact();

        default void prepareLegacySession() {
        }
    }
}
