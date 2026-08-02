package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationMetadataBootstrapTest {
    private static final Path ROOT = Path.of("/e2").toAbsolutePath().normalize();
    private static final Path GLOBAL = ROOT.resolve("config");
    private static final Path WORLD = ROOT.resolve("world/serverconfig");
    private static final Path LEGACY = GLOBAL.resolve("iamzombieq-common.toml");

    @Test
    void freshUsesOnlyFixedNofollowMetadata() {
        RecordingPort port = new RecordingPort();
        MigrationMetadataBootstrap.Result result =
                new MigrationMetadataBootstrap(port).inspect(
                        MigrationMetadataBootstrap.Candidates.integrated(
                                GLOBAL, WORLD, LEGACY));

        assertEquals(MigrationMetadataBootstrap.Kind.FRESH, result.kind());
        assertFalse(port.events.isEmpty());
        assertTrue(port.events.stream().allMatch(event -> event.startsWith("metadata:")));
        assertFalse(port.events.stream().anyMatch(event -> event.startsWith("content:")));
        assertFalse(port.events.contains("hash"));
        assertFalse(port.events.contains("create-directory"));
        assertFalse(port.events.contains("create-artifact"));
        assertFalse(port.events.contains("select-profile"));
        assertFalse(port.events.contains("open-session"));
    }

    @Test
    void dedicatedBootstrapNeverEnumeratesPreferences() {
        RecordingPort port = new RecordingPort();
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY);
        assertFalse(candidates.fixedCandidates().stream()
                .map(Path::toString)
                .anyMatch(path -> path.contains("preferences")
                        || path.contains("client.toml")));
        new MigrationMetadataBootstrap(port).inspect(candidates);

        String trace = String.join("\n", port.events);
        assertTrue(trace.contains("iamzombieq-server.toml"));
        assertFalse(trace.contains("preferences"));
        assertFalse(trace.contains("client.toml"));
    }

    @Test
    void selectedPresentTargetDoesNotInspectLegacyOrAnotherTarget() {
        RecordingPort port = new RecordingPort();
        Path worldTarget = WORLD.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        Path unrelatedGlobal = GLOBAL.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        port.states.put(worldTarget, MigrationPathState.PRESENT);
        port.states.put(LEGACY, MigrationPathState.UNSAFE);
        port.states.put(unrelatedGlobal, MigrationPathState.UNSAFE);

        MigrationMetadataBootstrap.Result result =
                new MigrationMetadataBootstrap(port).inspect(
                        MigrationMetadataBootstrap.Candidates.actualTargetOnly(
                                MigrationTarget.SERVER,
                                worldTarget,
                                LEGACY));

        assertEquals(
                MigrationMetadataBootstrap.Kind.REQUIRES_SESSION,
                result.kind());
        assertEquals(worldTarget, result.operationalTarget());
        assertFalse(port.events.contains("metadata:" + LEGACY));
        assertFalse(port.events.contains("metadata:" + unrelatedGlobal));
    }

    @Test
    void legacyIsInspectedOnlyAfterEveryActualTargetArtifactWasAbsent() {
        RecordingPort port = new RecordingPort();
        Path target = GLOBAL.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        port.states.put(LEGACY, MigrationPathState.PRESENT);
        MigrationMetadataBootstrap bootstrap =
                new MigrationMetadataBootstrap(port);
        MigrationMetadataBootstrap.Result targetFresh =
                bootstrap.inspect(
                        MigrationMetadataBootstrap.Candidates.actualTargetOnly(
                                MigrationTarget.SERVER, target, LEGACY));

        assertEquals(
                MigrationMetadataBootstrap.Kind.FRESH,
                targetFresh.kind());
        int eventsBeforeLegacy = port.events.size();

        MigrationMetadataBootstrap.Result applicable =
                bootstrap.inspectLegacyAfterTargetFresh(targetFresh);

        assertEquals(
                MigrationMetadataBootstrap.Kind.REQUIRES_SESSION,
                applicable.kind());
        assertEquals(
                "metadata:" + LEGACY,
                port.events.get(eventsBeforeLegacy));
        assertEquals(1, port.events.stream()
                .filter(event -> event.equals("metadata:" + LEGACY))
                .count());
    }

    @Test
    void sessionRevalidationRejectsBootstrapDrift() {
        RecordingPort port = new RecordingPort();
        Path worldTarget = WORLD.resolve("iamzombieq-server.toml");
        port.states.put(worldTarget, MigrationPathState.PRESENT);
        MigrationMetadataBootstrap bootstrap = new MigrationMetadataBootstrap(port);
        MigrationMetadataBootstrap.Result result = bootstrap.inspect(
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY));
        assertEquals(MigrationMetadataBootstrap.Kind.REQUIRES_SESSION, result.kind());

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.revalidateThroughSession(
                        result,
                        path -> path.equals(worldTarget)
                                ? new MigrationPathState.Metadata(
                                        true, false, "changed-leaf", 1)
                                : port.metadata(path)));
        assertFalse(port.events.stream().anyMatch(event -> event.startsWith("content:")));
        assertFalse(port.events.contains("create-artifact"));
    }

    @Test
    void freshRevalidationRejectsALeafAppearingDuringNamespaceBinding() {
        RecordingPort port = new RecordingPort();
        MigrationMetadataBootstrap bootstrap =
                new MigrationMetadataBootstrap(port);
        MigrationMetadataBootstrap.Result result = bootstrap.inspect(
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY));
        Path globalTarget = GLOBAL.resolve(
                ActualTargetResolver.SERVER_BASENAME);

        assertThrows(
                MigrationFailure.class,
                () -> bootstrap.revalidateFreshAfterNamespaceBinding(
                        result,
                        path -> path.equals(globalTarget)
                                ? new MigrationPathState.Metadata(
                                        true, false, "appeared", 1)
                                : port.metadata(path)));
        assertFalse(port.events.stream()
                .anyMatch(event -> event.startsWith("content:")));
        assertFalse(port.events.contains("create-artifact"));
    }

    @Test
    void boundSessionRevalidationTouchesOnlySelectedTargetAndLegacy()
            throws Exception {
        RecordingPort port = new RecordingPort();
        Path globalTarget = GLOBAL.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        port.states.put(globalTarget, MigrationPathState.PRESENT);
        MigrationMetadataBootstrap bootstrap =
                new MigrationMetadataBootstrap(port);
        MigrationMetadataBootstrap.Result result = bootstrap.inspect(
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY));
        java.util.ArrayList<Path> rebound = new java.util.ArrayList<>();

        bootstrap.revalidateThroughSession(
                result,
                path -> {
                    rebound.add(path);
                    return port.metadata(path);
                });

        assertTrue(rebound.contains(globalTarget));
        assertTrue(rebound.contains(LEGACY));
        assertTrue(rebound.stream()
                .noneMatch(path -> path.startsWith(WORLD)));
    }

    @Test
    void unknownOrUnsafeBootstrapStateNeverBecomesFreshOrFallback() {
        for (MigrationPathState state :
                new MigrationPathState[] {MigrationPathState.UNKNOWN, MigrationPathState.UNSAFE}) {
            RecordingPort port = new RecordingPort();
            port.states.put(WORLD.resolve("iamzombieq-server.toml"), state);
            assertThrows(
                    IllegalStateException.class,
                    () -> new MigrationMetadataBootstrap(port).inspect(
                            MigrationMetadataBootstrap.Candidates.dedicated(
                                    GLOBAL, WORLD, LEGACY)));
            assertFalse(port.events.contains("select-profile"));
            assertFalse(port.events.contains("open-session"));
        }
    }

    private static final class RecordingPort
            implements MigrationMetadataBootstrap.Port {
        private final List<String> events = new ArrayList<>();
        private final Map<Path, MigrationPathState> states = new HashMap<>();

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(Path path)
                throws java.io.IOException {
            events.add("metadata:" + path);
            return metadata(path);
        }

        MigrationPathState.Metadata metadata(Path path)
                throws java.io.IOException {
            return switch (states.getOrDefault(path, MigrationPathState.ABSENT)) {
                case ABSENT -> throw new java.nio.file.NoSuchFileException(path.toString());
                case PRESENT -> new MigrationPathState.Metadata(
                        true, false, "leaf:" + path, 1);
                case UNSAFE -> new MigrationPathState.Metadata(
                        false, true, "link:" + path, 0);
                case UNKNOWN -> throw new java.io.IOException("unknown metadata");
            };
        }

        @Override
        public void selectProfile() {
            events.add("select-profile");
        }

        @Override
        public void openSession() {
            events.add("open-session");
        }

        @Override
        public byte[] readContent(Path path) {
            events.add("content:" + path);
            return new byte[0];
        }

        @Override
        public void hashContent() {
            events.add("hash");
        }

        @Override
        public void createDirectory() {
            events.add("create-directory");
        }

        @Override
        public void createArtifact() {
            events.add("create-artifact");
        }
    }
}
