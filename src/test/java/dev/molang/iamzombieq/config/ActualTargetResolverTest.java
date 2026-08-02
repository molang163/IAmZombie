package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ActualTargetResolverTest {
    private static final Path ROOT = Path.of("/e2").toAbsolutePath().normalize();
    private static final Path GLOBAL = ROOT.resolve("config");
    private static final Path WORLD_A = ROOT.resolve("world-a/serverconfig");
    private static final Path WORLD_B = ROOT.resolve("world-b/serverconfig");
    private static final String SERVER = "iamzombieq-server.toml";
    private static final String PREFERENCES = "iamzombieq-preferences-client.toml";

    @Test
    void worldPresentSelectsWorldAndWorldAbsentSelectsGlobal() {
        StateTable states = new StateTable();
        states.set(WORLD_A.resolve(SERVER), MigrationPathState.PRESENT);
        ActualTargetResolver.Resolution world =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);
        assertEquals(WORLD_A.resolve(SERVER), world.actualTarget());
        assertEquals(ActualTargetResolver.Location.WORLD, world.location());
        assertTrue(world.worldGuard().isEmpty());

        states.set(WORLD_A.resolve(SERVER), MigrationPathState.ABSENT);
        ActualTargetResolver.Resolution global =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);
        assertEquals(GLOBAL.resolve(SERVER), global.actualTarget());
        assertEquals(ActualTargetResolver.Location.GLOBAL, global.location());
        assertTrue(global.worldGuard().isPresent());
    }

    @Test
    void worldUnknownNeverFallsBackToGlobal() {
        for (MigrationPathState state :
                new MigrationPathState[] {MigrationPathState.UNKNOWN, MigrationPathState.UNSAFE}) {
            StateTable states = new StateTable();
            states.set(WORLD_A.resolve(SERVER), state);
            assertThrows(
                    IllegalStateException.class,
                    () -> new ActualTargetResolver(reader(states))
                            .resolveServer(GLOBAL, WORLD_A));
            assertFalse(states.queries().contains(GLOBAL.resolve(SERVER)));
        }
    }

    @Test
    void worldMetadataFailureRetainsConcreteCauseInTheF1Contract() {
        Path worldTarget = WORLD_A.resolve(SERVER);
        AccessDeniedException denied = new AccessDeniedException(
                worldTarget.toString(), null, "permission denied");
        ActualTargetResolver.StateReader reader =
                new ActualTargetResolver.StateReader() {
                    @Override
                    public MigrationPathState.Observation observe(Path path) {
                        return MigrationPathState.observe(() -> {
                            throw denied;
                        });
                    }
                };

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> new ActualTargetResolver(reader)
                        .resolveServer(GLOBAL, WORLD_A));

        assertEquals(worldTarget, failure.target());
        assertEquals(
                GLOBAL.resolve(ActualTargetResolver.LEGACY_BASENAME),
                failure.legacy());
        assertTrue(failure.reason().contains("AccessDeniedException"));
        assertTrue(failure.reason().contains("permission denied"));
        assertEquals(denied, failure.getCause());
    }

    @Test
    void globalEvidenceCannotCompleteAnyWorld() {
        StateTable states = new StateTable();
        states.set(WORLD_A.resolve(SERVER), MigrationPathState.ABSENT);
        ActualTargetResolver.Resolution global =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);

        assertTrue(global.evidenceAppliesTo(GLOBAL.resolve(SERVER)));
        assertFalse(global.evidenceAppliesTo(WORLD_A.resolve(SERVER)));

        states.set(WORLD_A.resolve(SERVER), MigrationPathState.PRESENT);
        ActualTargetResolver.Resolution world =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);
        assertFalse(world.evidenceAppliesTo(GLOBAL.resolve(SERVER)));
        assertTrue(world.evidenceAppliesTo(WORLD_A.resolve(SERVER)));
    }

    @Test
    void worldAbsenceGuardWrapsEveryGlobalOperationAndReturn() {
        StateTable states = new StateTable();
        Path worldTarget = WORLD_A.resolve(SERVER);
        states.sequence(
                worldTarget,
                MigrationPathState.ABSENT,
                MigrationPathState.ABSENT,
                MigrationPathState.PRESENT);
        ActualTargetResolver.Resolution operationResolution =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);

        assertThrows(
                IllegalStateException.class,
                () -> operationResolution.worldGuard().orElseThrow()
                        .around("global-content-read", () -> "not accepted"));
        assertEquals(3, states.queryCount(worldTarget));

        states.sequence(
                worldTarget,
                MigrationPathState.ABSENT,
                MigrationPathState.ABSENT);
        ActualTargetResolver.Resolution returnResolution =
                new ActualTargetResolver(reader(states)).resolveServer(GLOBAL, WORLD_A);
        assertThrows(
                IllegalStateException.class,
                () -> {
                    states.set(worldTarget, MigrationPathState.UNKNOWN);
                    returnResolution.worldGuard().orElseThrow().beforeSuccessfulReturn();
                });
    }

    @Test
    void worldAbsenceGuardRetainsConcreteMetadataCause() {
        Path worldTarget = WORLD_A.resolve(SERVER);
        Path globalTarget = GLOBAL.resolve(SERVER);
        AccessDeniedException denied = new AccessDeniedException(
                worldTarget.toString(), null, "permission denied");
        AtomicInteger worldTargetQueries = new AtomicInteger();
        ActualTargetResolver.StateReader reader = path -> {
            if (path.equals(worldTarget)
                    && worldTargetQueries.incrementAndGet() > 1) {
                return MigrationPathState.observe(() -> {
                    throw denied;
                });
            }
            return MigrationPathState.Observation.fromState(
                    MigrationPathState.ABSENT);
        };
        ActualTargetResolver.Resolution resolution =
                new ActualTargetResolver(reader).resolveServer(GLOBAL, WORLD_A);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> resolution.worldGuard()
                        .orElseThrow()
                        .beforeSuccessfulReturn());

        assertEquals(globalTarget, failure.target());
        assertEquals(
                GLOBAL.resolve(ActualTargetResolver.LEGACY_BASENAME),
                failure.legacy());
        assertTrue(failure.reason().contains("AccessDeniedException"));
        assertTrue(failure.reason().contains("permission denied"));
        assertEquals(denied, failure.getCause());
    }

    @Test
    void worldAAndWorldBIndependentlyGuardSharedGlobalComplete() {
        StateTable states = new StateTable();
        states.set(WORLD_A.resolve(SERVER), MigrationPathState.ABSENT);
        states.set(WORLD_B.resolve(SERVER), MigrationPathState.ABSENT);
        ActualTargetResolver resolver = new ActualTargetResolver(reader(states));

        ActualTargetResolver.Resolution a = resolver.resolveServer(GLOBAL, WORLD_A);
        ActualTargetResolver.Resolution b = resolver.resolveServer(GLOBAL, WORLD_B);
        assertEquals(a.actualTarget(), b.actualTarget());
        assertNotSame(a.worldGuard().orElseThrow(), b.worldGuard().orElseThrow());
        assertTrue(a.worldGuard().orElseThrow().guardedCandidates()
                .contains(WORLD_A.resolve(SERVER)));
        assertTrue(b.worldGuard().orElseThrow().guardedCandidates()
                .contains(WORLD_B.resolve(SERVER)));

        states.set(WORLD_B.resolve(SERVER), MigrationPathState.PRESENT);
        assertThrows(
                IllegalStateException.class,
                () -> b.worldGuard().orElseThrow().beforeSuccessfulReturn());
    }

    @Test
    void preferencesAndEveryWorldRemainPathAndEvidenceIsolated() {
        StateTable states = new StateTable();
        states.set(WORLD_A.resolve(SERVER), MigrationPathState.PRESENT);
        states.set(WORLD_B.resolve(SERVER), MigrationPathState.PRESENT);
        ActualTargetResolver resolver = new ActualTargetResolver(reader(states));
        Path otherGlobal = ROOT.resolve("other-instance/config");

        Path a = resolver.resolveServer(GLOBAL, WORLD_A).actualTarget();
        Path b = resolver.resolveServer(GLOBAL, WORLD_B).actualTarget();
        Path preferences = resolver.resolvePreferences(GLOBAL).actualTarget();
        Path otherPreferences = resolver.resolvePreferences(otherGlobal).actualTarget();
        assertEquals(WORLD_A.resolve(SERVER), a);
        assertEquals(WORLD_B.resolve(SERVER), b);
        assertEquals(GLOBAL.resolve(PREFERENCES), preferences);
        assertEquals(otherGlobal.resolve(PREFERENCES), otherPreferences);
        assertEquals(4, java.util.Set.of(a, b, preferences, otherPreferences).size());
    }

    @Test
    void normalizedAbsoluteParentsAndSingleBasenamesAreMandatory() {
        StateTable states = new StateTable();
        ActualTargetResolver resolver = new ActualTargetResolver(reader(states));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveServer(Path.of("relative"), WORLD_A));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveServer(GLOBAL.resolve("child/.."), WORLD_A));
        assertThrows(
                IllegalArgumentException.class,
                () -> ActualTargetResolver.fixedChild(GLOBAL, "../escape.toml"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ActualTargetResolver.fixedChild(GLOBAL, "nested/file.toml"));
    }

    private static ActualTargetResolver.StateReader reader(StateTable states) {
        return path -> MigrationPathState.Observation.fromState(
                states.state(path));
    }

    private static final class StateTable {
        private final Map<Path, ArrayDeque<MigrationPathState>> states = new HashMap<>();
        private final Map<Path, Integer> queries = new HashMap<>();

        void set(Path path, MigrationPathState state) {
            sequence(path, state);
        }

        void sequence(Path path, MigrationPathState... values) {
            states.put(path, new ArrayDeque<>(java.util.List.of(values)));
            queries.put(path, 0);
        }

        MigrationPathState state(Path path) {
            queries.merge(path, 1, Integer::sum);
            ArrayDeque<MigrationPathState> values = states.get(path);
            if (values == null || values.isEmpty()) {
                return MigrationPathState.ABSENT;
            }
            return values.size() == 1 ? values.getFirst() : values.removeFirst();
        }

        java.util.Set<Path> queries() {
            return queries.keySet();
        }

        int queryCount(Path path) {
            return queries.getOrDefault(path, 0);
        }
    }
}
