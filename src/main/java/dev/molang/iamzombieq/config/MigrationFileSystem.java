package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

interface MigrationFileSystem {
    MigrationPathState.Metadata readNofollowMetadata(Path path) throws IOException;

    MigrationBinding.Observation observeBinding(Path target) throws IOException;

    MigrationAccessProfile.Capabilities capabilities(MigrationBinding binding)
            throws IOException;

    MigrationDirectorySession openDirectorySession(
            MigrationAccessProfile profile, MigrationBinding binding);

    record ArtifactPaths(
            Path target,
            Path lock,
            Path journal,
            Path backup,
            Path initial,
            Path marker,
            List<Path> fixedStages) {

        public ArtifactPaths {
            target = normalizedTarget(target);
            lock = sameParent(target, lock, "lock");
            journal = sameParent(target, journal, "journal");
            backup = sameParent(target, backup, "backup");
            initial = sameParent(target, initial, "initial");
            marker = sameParent(target, marker, "marker");
            fixedStages = List.copyOf(
                    Objects.requireNonNull(fixedStages, "fixedStages"));
            if (fixedStages.size() != 5) {
                throw new IllegalArgumentException(
                        "Exactly five fixed atomic stages are required");
            }
            for (Path stage : fixedStages) {
                sameParent(target, stage, "stage");
            }
            if (fixedStages.stream().distinct().count() != fixedStages.size()) {
                throw new IllegalArgumentException(
                        "Fixed stage paths must be distinct");
            }
        }

        static ArtifactPaths forTarget(Path target) {
            Path checkedTarget = normalizedTarget(target);
            Path parent = checkedTarget.getParent();
            String targetBasename = checkedTarget.getFileName().toString();
            String prefix = targetBasename + ".iamzombieq-migration-v1";

            Path lock = parent.resolve(prefix + ".lock");
            Path journal = parent.resolve(prefix + ".journal");
            Path backup = parent.resolve(prefix + ".legacy.backup");
            Path initial = parent.resolve(prefix + ".initial");
            Path marker = parent.resolve(prefix + ".marker");
            return new ArtifactPaths(
                    checkedTarget,
                    lock,
                    journal,
                    backup,
                    initial,
                    marker,
                    List.of(
                            parent.resolve(journal.getFileName() + ".stage"),
                            parent.resolve(backup.getFileName() + ".stage"),
                            parent.resolve(initial.getFileName() + ".stage"),
                            parent.resolve(targetBasename + ".stage"),
                            parent.resolve(marker.getFileName() + ".stage")));
        }

        List<Path> fixedCandidates() {
            ArrayList<Path> candidates = new ArrayList<>(10);
            candidates.add(lock);
            candidates.add(journal);
            candidates.add(backup);
            candidates.add(initial);
            candidates.add(marker);
            candidates.addAll(fixedStages);
            return List.copyOf(candidates);
        }

        private static Path normalizedTarget(Path target) {
            Objects.requireNonNull(target, "target");
            if (!target.isAbsolute()
                    || !target.equals(target.toAbsolutePath().normalize())
                    || target.getParent() == null
                    || target.getFileName() == null) {
                throw new IllegalArgumentException(
                        "Target must be a normalized absolute file path: " + target);
            }
            return target;
        }

        private static Path sameParent(
                Path target, Path candidate, String description) {
            Objects.requireNonNull(candidate, description);
            if (!candidate.isAbsolute()
                    || !candidate.equals(candidate.toAbsolutePath().normalize())
                    || !target.getParent().equals(candidate.getParent())
                    || candidate.getFileName() == null) {
                throw new IllegalArgumentException(
                        description + " is outside the target parent: " + candidate);
            }
            return candidate;
        }
    }
}
