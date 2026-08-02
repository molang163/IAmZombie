package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ActualTargetResolver {
    static final String LEGACY_BASENAME = "iamzombieq-common.toml";
    static final String SERVER_BASENAME = "iamzombieq-server.toml";
    static final String PREFERENCES_BASENAME =
            "iamzombieq-preferences-client.toml";

    private final StateReader stateReader;

    ActualTargetResolver(StateReader stateReader) {
        this.stateReader = Objects.requireNonNull(stateReader, "stateReader");
    }

    Resolution resolveServer(Path globalConfigParent, Path worldServerConfigParent) {
        Path globalParent = requireNormalizedAbsolute(
                globalConfigParent, "global config parent");
        Path worldParent = requireNormalizedAbsolute(
                worldServerConfigParent, "world server-config parent");
        Path legacy = fixedChild(globalParent, LEGACY_BASENAME);
        Path worldTarget = fixedChild(worldParent, SERVER_BASENAME);
        MigrationPathState.Observation worldObservation =
                requireObservation(worldTarget, legacy, worldTarget);

        return switch (worldObservation.state()) {
            case PRESENT -> new Resolution(
                    worldTarget, Location.WORLD, Optional.empty());
            case ABSENT -> {
                Path globalTarget = fixedChild(globalParent, SERVER_BASENAME);
                WorldAbsenceGuard guard = new WorldAbsenceGuard(
                        stateReader,
                        worldCandidates(worldTarget),
                        legacy,
                        globalTarget);
                guard.verifyArtifactAbsenceAtResolution();
                yield new Resolution(
                        globalTarget,
                        Location.GLOBAL,
                        Optional.of(guard));
            }
            case UNKNOWN, UNSAFE -> throw MigrationFailure.operational(
                    legacy,
                    worldTarget,
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    SERVER_BASENAME,
                    "world-target-resolution",
                    "World server target is "
                            + worldObservation.state()
                            + " and is not safely absent; "
                            + worldObservation.detail(),
                    worldObservation.cause());
        };
    }

    Resolution resolvePreferences(Path globalConfigParent) {
        Path globalParent = requireNormalizedAbsolute(
                globalConfigParent, "global config parent");
        return new Resolution(
                fixedChild(globalParent, PREFERENCES_BASENAME),
                Location.GLOBAL,
                Optional.empty());
    }

    static Path fixedChild(Path parent, String basename) {
        Path checkedParent = requireNormalizedAbsolute(parent, "parent");
        String checkedBasename = requireBasename(basename);
        Path child = checkedParent.resolve(checkedBasename).normalize();
        if (!checkedParent.equals(child.getParent())) {
            throw new IllegalArgumentException(
                    "Fixed child escaped its parent: " + basename);
        }
        return child;
    }

    private MigrationPathState.Observation requireObservation(
            Path path, Path legacy, Path target) {
        MigrationPathState.Observation observation;
        try {
            observation = stateReader.observe(path);
        } catch (RuntimeException failure) {
            throw MigrationFailure.operational(
                    legacy,
                    target,
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    path.getFileName().toString(),
                    "world-target-metadata",
                    "Could not classify the world server target",
                    failure);
        }
        if (observation == null || observation.state() == null) {
            throw MigrationFailure.operational(
                    legacy,
                    target,
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    path.getFileName().toString(),
                    "world-target-metadata",
                    "World server target metadata observation was null",
                    null);
        }
        return observation;
    }

    private static List<Path> worldCandidates(Path worldTarget) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(worldTarget);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(worldTarget);
        candidates.addAll(artifacts.fixedCandidates());
        return List.copyOf(candidates);
    }

    private static Path requireNormalizedAbsolute(Path path, String description) {
        Objects.requireNonNull(path, description);
        if (!path.isAbsolute() || !path.equals(path.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    description + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static String requireBasename(String basename) {
        Objects.requireNonNull(basename, "basename");
        if (basename.isBlank()
                || basename.equals(".")
                || basename.equals("..")
                || basename.indexOf('/') >= 0
                || basename.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "Expected one non-empty relative basename: " + basename);
        }
        Path operand = Path.of(basename);
        if (operand.isAbsolute()
                || operand.getNameCount() != 1
                || !operand.getFileName().toString().equals(basename)) {
            throw new IllegalArgumentException(
                    "Expected one relative basename: " + basename);
        }
        return basename;
    }

    enum Location {
        WORLD,
        GLOBAL
    }

    record Resolution(
            Path actualTarget,
            Location location,
            Optional<WorldAbsenceGuard> worldGuard) {
        Resolution {
            actualTarget = requireNormalizedAbsolute(actualTarget, "actual target");
            Objects.requireNonNull(location, "location");
            worldGuard = Objects.requireNonNull(worldGuard, "worldGuard");
        }

        boolean evidenceAppliesTo(Path candidate) {
            if (candidate == null
                    || !candidate.isAbsolute()
                    || !candidate.equals(candidate.toAbsolutePath().normalize())) {
                return false;
            }
            return actualTarget.equals(candidate);
        }
    }

    static final class WorldAbsenceGuard {
        private final StateReader stateReader;
        private final List<Path> guardedCandidates;
        private final Path legacy;
        private final Path actualTarget;

        private WorldAbsenceGuard(
                StateReader stateReader,
                List<Path> guardedCandidates,
                Path legacy,
                Path actualTarget) {
            this.stateReader = Objects.requireNonNull(stateReader, "stateReader");
            this.guardedCandidates = List.copyOf(guardedCandidates);
            this.legacy = requireNormalizedAbsolute(legacy, "legacy");
            this.actualTarget =
                    requireNormalizedAbsolute(actualTarget, "actual target");
            if (this.guardedCandidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "A world absence guard needs fixed candidates");
            }
        }

        List<Path> guardedCandidates() {
            return guardedCandidates;
        }

        WorldAbsenceGuard rebind(StateReader boundStateReader) {
            return new WorldAbsenceGuard(
                    Objects.requireNonNull(
                            boundStateReader, "boundStateReader"),
                    guardedCandidates,
                    legacy,
                    actualTarget);
        }

        <T> T around(String operation, GuardedOperation<T> guardedOperation)
                throws Exception {
            return around(
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    operation,
                    guardedOperation);
        }

        <T> T around(
                MigrationTargetState.Phase phase,
                String operation,
                GuardedOperation<T> guardedOperation)
                throws Exception {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(guardedOperation, "guardedOperation");
            String checkedOperation = requireOperation(operation);
            verifyAbsent(phase, "before " + checkedOperation);

            T result;
            try {
                result = guardedOperation.run();
            } catch (Exception failure) {
                verifyAfterFailure(
                        phase, "after failed " + checkedOperation, failure);
                throw failure;
            } catch (Error failure) {
                verifyAfterFailure(
                        phase, "after failed " + checkedOperation, failure);
                throw failure;
            }

            verifyAbsent(phase, "after " + checkedOperation);
            return result;
        }

        void beforeSuccessfulReturn() {
            beforeSuccessfulReturn(MigrationTargetState.Phase.NO_EVIDENCE);
        }

        void beforeSuccessfulReturn(MigrationTargetState.Phase phase) {
            Objects.requireNonNull(phase, "phase");
            verifyAbsent(phase, "before successful global return");
        }

        private void verifyAfterFailure(
                MigrationTargetState.Phase phase,
                String operation,
                Throwable failure) {
            try {
                verifyAbsent(phase, operation);
            } catch (RuntimeException guardFailure) {
                guardFailure.addSuppressed(failure);
                throw guardFailure;
            }
        }

        private void verifyAbsent(
                MigrationTargetState.Phase phase, String operation) {
            for (Path candidate : guardedCandidates) {
                MigrationPathState.Observation observation =
                        observe(candidate, phase, operation);
                if (observation.state() != MigrationPathState.ABSENT) {
                    throw failure(
                            candidate,
                            phase,
                            "world-absence-guard",
                            "World candidate is not safely absent: "
                                    + candidate
                                    + " "
                                    + operation
                                    + ": "
                                    + observation.state()
                                    + "; "
                                    + observation.detail(),
                            observation.cause());
                }
            }
        }

        private void verifyArtifactAbsenceAtResolution() {
            for (int index = 1; index < guardedCandidates.size(); index++) {
                Path candidate = guardedCandidates.get(index);
                MigrationPathState.Observation observation =
                        observe(
                                candidate,
                                MigrationTargetState.Phase.NO_EVIDENCE,
                                "artifact absence at resolution");
                if (observation.state() != MigrationPathState.ABSENT) {
                    throw failure(
                            candidate,
                            MigrationTargetState.Phase.NO_EVIDENCE,
                            "world-artifact-absence",
                            "World migration artifact "
                                    + candidate
                                    + " is not safely absent: "
                                    + observation.state()
                                    + "; "
                                    + observation.detail(),
                            observation.cause());
                }
            }
        }

        private MigrationPathState.Observation observe(
                Path candidate,
                MigrationTargetState.Phase phase,
                String operation) {
            try {
                MigrationPathState.Observation observation =
                        stateReader.observe(candidate);
                if (observation == null || observation.state() == null) {
                    throw failure(
                            candidate,
                            phase,
                            "world-absence-metadata",
                            "World absence metadata observation for "
                                    + candidate
                                    + " was null "
                                    + operation,
                            null);
                }
                return observation;
            } catch (MigrationFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw failure(
                        candidate,
                        phase,
                        "world-absence-metadata",
                        "Could not classify world candidate "
                                + candidate
                                + " "
                                + operation,
                        failure);
            }
        }

        private MigrationFailure failure(
                Path candidate,
                MigrationTargetState.Phase phase,
                String operation,
                String reason,
                Throwable cause) {
            return MigrationFailure.operational(
                    legacy,
                    actualTarget,
                    phase,
                    candidate.getFileName().toString(),
                    operation,
                    reason,
                    cause);
        }

        private static String requireOperation(String operation) {
            Objects.requireNonNull(operation, "operation");
            if (operation.isBlank()) {
                throw new IllegalArgumentException("Operation must be named");
            }
            return operation;
        }
    }

    @FunctionalInterface
    interface StateReader {
        MigrationPathState.Observation observe(Path path);
    }

    @FunctionalInterface
    interface GuardedOperation<T> {
        T run() throws Exception;
    }
}
