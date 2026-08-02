package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DedicatedMigrationIsolationTest {
    private static final Path ROOT =
            Path.of("/dedicated-migration").toAbsolutePath().normalize();
    private static final Path GLOBAL = ROOT.resolve("config");
    private static final Path WORLD = ROOT.resolve("world/serverconfig");
    private static final Path LEGACY =
            GLOBAL.resolve("iamzombieq-common.toml");
    private static final Path WORLD_SERVER =
            WORLD.resolve(ActualTargetResolver.SERVER_BASENAME);
    private static final Path GLOBAL_SERVER =
            GLOBAL.resolve(ActualTargetResolver.SERVER_BASENAME);

    @Test
    void dedicatedNeverConstructsOrTouchesPreferences() {
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY);
        assertNoPreferences(candidates.fixedCandidates());

        DedicatedTrapPort port = new DedicatedTrapPort(WORLD_SERVER);
        MigrationMetadataBootstrap.Result result =
                new MigrationMetadataBootstrap(port).inspect(candidates);

        assertEquals(
                MigrationMetadataBootstrap.Kind.REQUIRES_SESSION,
                result.kind());
        assertNoPreferences(port.paths);
        assertEquals(1, count(port.events, "select-profile"));
        assertEquals(1, count(port.events, "open-session"));
        assertFalse(port.events.contains("content"));
        assertFalse(port.events.contains("hash"));
        assertFalse(port.events.contains("create-directory"));
        assertFalse(port.events.contains("create-artifact"));
    }

    @Test
    void dedicatedFreshPathUsesOnlyFixedNofollowMetadata() {
        DedicatedTrapPort port = new DedicatedTrapPort(null);
        MigrationMetadataBootstrap.Result result =
                new MigrationMetadataBootstrap(port).inspect(
                        MigrationMetadataBootstrap.Candidates.dedicated(
                                GLOBAL, WORLD, LEGACY));

        assertEquals(MigrationMetadataBootstrap.Kind.FRESH, result.kind());
        assertTrue(port.events.stream()
                .allMatch(event -> event.startsWith("metadata:")));
        assertNoPreferences(port.paths);
    }

    @Test
    void dedicatedCandidateSetIsServerOnlyAndPerNamespace() {
        List<Path> candidates =
                MigrationMetadataBootstrap.Candidates.dedicated(
                                GLOBAL, WORLD, LEGACY)
                        .fixedCandidates();

        assertTrue(candidates.contains(WORLD_SERVER));
        assertTrue(candidates.contains(GLOBAL_SERVER));
        assertTrue(candidates.contains(LEGACY));
        assertTrue(candidates.stream().allMatch(
                path -> path.getParent().equals(WORLD)
                        || path.getParent().equals(GLOBAL)));
        assertNoPreferences(candidates);
    }

    @Test
    void dedicatedApplicabilityDrivesTheEngineWithoutPreferencesPathAccess()
            throws IOException {
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.dedicated(
                        GLOBAL, WORLD, LEGACY);
        assertEquals(Set.of(MigrationTarget.SERVER), applicableTargets(candidates));

        DedicatedTrapPort bootstrapPort = new DedicatedTrapPort(LEGACY);
        MigrationMetadataBootstrap.Result bootstrap =
                new MigrationMetadataBootstrap(bootstrapPort)
                        .inspect(candidates);
        assertEquals(
                MigrationMetadataBootstrap.Kind.REQUIRES_SESSION,
                bootstrap.kind());
        assertEquals(GLOBAL_SERVER, bootstrap.operationalTarget());

        DedicatedTrapStore store = new DedicatedTrapStore();
        store.delegate.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request request = request(GLOBAL_SERVER);
        MigrationTargetState state = migrateApplicable(
                new ConfigMigrationEngine(
                        ConfigSchemaCatalog.load(),
                        MigrationFaultInjector.none()),
                bootstrap,
                request,
                store);

        assertEquals(MigrationTargetState.Outcome.MIGRATED, state.outcome());
        assertEquals(MigrationTarget.SERVER, state.targetKind());
        assertEquals(GLOBAL_SERVER, state.actualTarget());
        assertNoPreferences(bootstrapPort.paths);
        assertNoPreferences(store.paths);
    }

    @SuppressWarnings("unchecked")
    private static Set<MigrationTarget> applicableTargets(
            MigrationMetadataBootstrap.Candidates candidates) {
        try {
            Method method = candidates.getClass().getDeclaredMethod(
                    "applicableTargets");
            return (Set<MigrationTarget>) method.invoke(candidates);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "dedicated bootstrap must expose its exact applicable targets",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static MigrationTargetState migrateApplicable(
            ConfigMigrationEngine engine,
            MigrationMetadataBootstrap.Result bootstrap,
            ConfigMigrationEngine.Request request,
            ConfigMigrationEngine.Store store) {
        try {
            Method method = ConfigMigrationEngine.class.getDeclaredMethod(
                    "migrateApplicable",
                    MigrationMetadataBootstrap.Result.class,
                    ConfigMigrationEngine.Request.class,
                    ConfigMigrationEngine.Store.class);
            return (MigrationTargetState) method.invoke(
                    engine, bootstrap, request, store);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "dedicated applicability must bridge bootstrap to the engine",
                    exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static ConfigMigrationEngine.Request request(Path target) {
        Path parent = target.getParent();
        MigrationBinding binding = new MigrationBinding(
                target,
                parent,
                parent,
                List.of(new MigrationBinding.Ancestor(
                        parent, "dedicated-parent:" + parent)),
                "dedicated-directory:" + parent,
                "file:dedicated-test-provider",
                "dedicated-test-store",
                25,
                "Linux");
        return new ConfigMigrationEngine.Request(
                MigrationTarget.SERVER,
                LEGACY,
                target,
                binding,
                MigrationAccessProfile.SECURE,
                Optional.empty(),
                false);
    }

    private static AssertionError propagate(Throwable failure) {
        if (failure instanceof AssertionError assertion) {
            return assertion;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new AssertionError(failure);
    }

    private static void assertNoPreferences(List<Path> paths) {
        assertFalse(
                paths.stream().map(Path::toString).anyMatch(
                        path -> path.contains("preferences")
                                || path.contains("client.toml")),
                () -> "dedicated path set touched preferences/client data: "
                        + paths);
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static final class DedicatedTrapPort
            implements MigrationMetadataBootstrap.Port {
        private final Path present;
        private final List<Path> paths = new ArrayList<>();
        private final List<String> events = new ArrayList<>();

        private DedicatedTrapPort(Path present) {
            this.present = present;
        }

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(Path path)
                throws IOException {
            trapPreferences(path);
            paths.add(path);
            events.add("metadata:" + path);
            if (!path.equals(present)) {
                throw new NoSuchFileException(path.toString());
            }
            return new MigrationPathState.Metadata(
                    true, false, "dedicated-leaf:" + path, 1);
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
            trapPreferences(path);
            events.add("content");
            throw new AssertionError(
                    "bootstrap must not read content during dedicated inspection");
        }

        @Override
        public void hashContent() {
            events.add("hash");
            throw new AssertionError(
                    "bootstrap must not hash content during dedicated inspection");
        }

        @Override
        public void createDirectory() {
            events.add("create-directory");
            throw new AssertionError(
                    "dedicated bootstrap must not create a directory");
        }

        @Override
        public void createArtifact() {
            events.add("create-artifact");
            throw new AssertionError(
                    "dedicated bootstrap must not create a migration artifact");
        }

        private static void trapPreferences(Path path) {
            String value = path.toString();
            if (value.contains("preferences") || value.contains("client.toml")) {
                throw new AssertionError(
                        "dedicated execution touched preferences path " + path);
            }
        }
    }

    private static final class DedicatedTrapStore
            implements ConfigMigrationEngine.Store {
        private final MigrationEngineTestStore delegate =
                new MigrationEngineTestStore();
        private final List<Path> paths = new ArrayList<>();

        @Override
        public MigrationPathState state(Path path) {
            trap(path);
            return delegate.state(path);
        }

        @Override
        public MigrationPathState.Observation observe(Path path) {
            trap(path);
            return delegate.observe(path);
        }

        @Override
        public byte[] read(
                Path path, MigrationDirectorySession.ContentKind kind) {
            trap(path);
            return delegate.read(path, kind);
        }

        @Override
        public ConfigMigrationEngine.LockLease acquirePermanentLock(
                ConfigMigrationEngine.LockRequest request) {
            trap(request.lock());
            trap(request.target());
            return delegate.acquirePermanentLock(request);
        }

        @Override
        public AtomicConfigPublisher.Port publicationPort(
                ConfigMigrationEngine.PublishRequest request) {
            trap(request.stage());
            trap(request.destination());
            return delegate.publicationPort(request);
        }

        @Override
        public String identity(Path path) {
            trap(path);
            return delegate.identity(path);
        }

        @Override
        public void verifyPermanentLock(
                Path path, ConfigMigrationEngine.LockLease lease) {
            trap(path);
            delegate.verifyPermanentLock(path, lease);
        }

        @Override
        public void verifyBinding(MigrationBinding binding) {
            trap(binding.target());
            trap(binding.logicalParent());
            trap(binding.physicalParent());
            for (MigrationBinding.Ancestor ancestor : binding.ancestors()) {
                trap(ancestor.path());
            }
            delegate.verifyBinding(binding);
        }

        private void trap(Path path) {
            DedicatedTrapPort.trapPreferences(path);
            paths.add(path);
        }
    }
}
