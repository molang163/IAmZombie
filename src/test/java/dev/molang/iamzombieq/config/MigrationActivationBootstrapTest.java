package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationActivationBootstrapTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preferencesCandidatesNeverConstructServerWorldOrAppearancePaths() {
        Path global = absolute(temporaryDirectory.resolve("config"));
        Path legacy = global.resolve(ActualTargetResolver.LEGACY_BASENAME);
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.preferences(
                        global, legacy);

        assertEquals(
                Set.of(MigrationTarget.PREFERENCES),
                candidates.applicableTargets());
        assertEquals(
                global.resolve(ActualTargetResolver.PREFERENCES_BASENAME),
                candidates.targetFor(legacy));
        assertTrue(candidates.fixedCandidates().contains(legacy));
        String allPaths = candidates.fixedCandidates().toString();
        assertFalse(allPaths.contains("serverconfig"));
        assertFalse(allPaths.contains(ActualTargetResolver.SERVER_BASENAME));
        assertFalse(allPaths.contains("iamzombieq-client.toml"));
    }

    @Test
    void selectedCandidateProjectionContainsOnlyActualTargetArtifactsAndLegacy() {
        Path global = absolute(temporaryDirectory.resolve("config"));
        Path world = absolute(
                temporaryDirectory.resolve("world/serverconfig"));
        Path legacy = global.resolve(ActualTargetResolver.LEGACY_BASENAME);
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.dedicated(
                        global, world, legacy);
        Path globalTarget =
                global.resolve(ActualTargetResolver.SERVER_BASENAME);

        List<Path> selected = candidates.selectedCandidates(globalTarget);

        assertTrue(selected.contains(globalTarget));
        assertTrue(selected.contains(legacy));
        assertTrue(selected.containsAll(
                MigrationFileSystem.ArtifactPaths.forTarget(globalTarget)
                        .fixedCandidates()));
        assertTrue(selected.stream().noneMatch(
                path -> path.startsWith(world)));
    }

    @Test
    void presentWorldTargetIgnoresUnrelatedGlobalAndLegacyMetadata()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("isolated-world-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("isolated-world-root"))
                .toAbsolutePath()
                .normalize();
        Path world = Files.createDirectory(
                worldRoot.resolve("serverconfig"));
        Path worldTarget = world.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        byte[] canonical =
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER);
        Files.write(worldTarget, canonical);

        Path unrelated = Files.writeString(
                temporaryDirectory.resolve("unrelated-global-source"),
                "must never be inspected through either symlink");
        Files.createSymbolicLink(
                global.resolve(ActualTargetResolver.SERVER_BASENAME),
                unrelated);
        Files.createSymbolicLink(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                unrelated);

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migrateServer(global, world);

        assertEquals(
                MigrationTargetState.Outcome.EXISTING_VALID,
                state.orElseThrow().outcome());
        assertEquals(worldTarget, state.orElseThrow().actualTarget());
        assertArrayEquals(
                canonical,
                Files.readAllBytes(worldTarget),
                "valid world target bytes must remain unchanged");
        assertNoArtifacts(worldTarget);
    }

    @Test
    void presentPreferencesTargetDoesNotInspectUnsafeLegacy()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve(
                                "isolated-preferences-config"))
                .toAbsolutePath()
                .normalize();
        Path preferences = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        byte[] canonical = ConfigMigrationEngineTest.canonical(
                MigrationTarget.PREFERENCES);
        Files.write(preferences, canonical);
        Path unrelated = Files.writeString(
                temporaryDirectory.resolve("unrelated-legacy-source"),
                "legacy metadata must not be queried");
        Files.createSymbolicLink(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                unrelated);

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migratePreferences(global);

        assertEquals(
                MigrationTargetState.Outcome.EXISTING_VALID,
                state.orElseThrow().outcome());
        assertEquals(preferences, state.orElseThrow().actualTarget());
        assertArrayEquals(canonical, Files.readAllBytes(preferences));
        assertNoArtifacts(preferences);
    }

    @Test
    void freshServerRunCreatesNoMigrationArtifactAndNoTarget()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-world"))
                .toAbsolutePath()
                .normalize();
        Path world = worldRoot.resolve("serverconfig");

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migrateServer(global, world);

        assertTrue(state.isEmpty());
        assertFalse(Files.exists(
                global.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertFalse(Files.exists(world));
        assertNoArtifacts(
                global.resolve(ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(
                world.resolve(ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void freshGlobalFallbackRechecksWorldAbsenceImmediatelyBeforeReturn()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-race-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-race-world"))
                .toAbsolutePath()
                .normalize();
        Path world = Files.createDirectory(
                worldRoot.resolve("serverconfig"));
        Path worldTarget = world.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        byte[] injected = "external = true\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(
                        global,
                        world,
                        () -> writeUnchecked(worldTarget, injected)));

        assertEquals(
                MigrationTargetState.Phase.NO_EVIDENCE,
                failure.phase());
        assertEquals(
                "world-absence-guard",
                failure.operation());
        assertEquals(
                ActualTargetResolver.SERVER_BASENAME,
                failure.artifact());
        assertEquals(
                global.resolve(ActualTargetResolver.SERVER_BASENAME),
                failure.target());
        assertTrue(failure.reason().contains(
                "before successful global return"));
        assertArrayEquals(injected, Files.readAllBytes(worldTarget));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.LEGACY_BASENAME)));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(global.resolve(
                ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(worldTarget);
    }

    @Test
    void freshGlobalFallbackRejectsWorldParentIdentitySwapBeforeReturn()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-swap-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-swap-world"))
                .toAbsolutePath()
                .normalize();
        Path world = Files.createDirectory(
                worldRoot.resolve("serverconfig"));
        Path displaced = worldRoot.resolve("serverconfig-old");

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(
                        global,
                        world,
                        () -> {
                            moveUnchecked(world, displaced);
                            createDirectoryUnchecked(world);
                        }));

        assertEquals(
                MigrationTargetState.Phase.NO_EVIDENCE,
                failure.phase());
        assertEquals(
                "fresh-namespace-revalidation",
                failure.operation());
        assertTrue(failure.reason().contains(
                "binding changed"));
        assertTrue(Files.isDirectory(world));
        assertTrue(Files.isDirectory(displaced));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.LEGACY_BASENAME)));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(global.resolve(
                ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(world.resolve(
                ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(displaced.resolve(
                ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void freshGlobalFallbackRejectsAbsentWorldParentAppearingEmpty()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve(
                                "fresh-parent-appears-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve(
                                "fresh-parent-appears-world"))
                .toAbsolutePath()
                .normalize();
        Path world = worldRoot.resolve("serverconfig");

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(
                        global,
                        world,
                        () -> createDirectoryUnchecked(world)));

        assertEquals(
                MigrationTargetState.Phase.NO_EVIDENCE,
                failure.phase());
        assertEquals(
                "world-absence-guard",
                failure.operation());
        assertTrue(failure.reason().contains(
                "before successful global return"));
        assertTrue(Files.isDirectory(world));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.LEGACY_BASENAME)));
        assertFalse(Files.exists(global.resolve(
                ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(global.resolve(
                ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(world.resolve(
                ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void freshWorldSymlinkParentIsNeverMisclassifiedAsSafeAbsence()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-link-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-link-world"))
                .toAbsolutePath()
                .normalize();
        Path outside = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-link-outside"))
                .toAbsolutePath()
                .normalize();
        Path world = Files.createSymbolicLink(
                worldRoot.resolve("serverconfig"), outside);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(global, world));

        assertTrue(failure.reason().contains(
                "World server-config parent"));
        assertFalse(Files.exists(
                global.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(
                global.resolve(ActualTargetResolver.SERVER_BASENAME));
        assertNoArtifacts(
                outside.resolve(ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void freshGlobalSymlinkParentIsNeverAcceptedAsAnActualTargetBinding()
            throws Exception {
        Path outside = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-global-outside"))
                .toAbsolutePath()
                .normalize();
        Path global = Files.createSymbolicLink(
                        temporaryDirectory.resolve("fresh-global-link"),
                        outside)
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("fresh-global-world"))
                .toAbsolutePath()
                .normalize();
        Path world = worldRoot.resolve("serverconfig");

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(global, world));

        assertTrue(failure.reason().contains(
                "actual target parent namespace could not be trusted"));
        assertFalse(Files.exists(
                outside.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(
                outside.resolve(ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void preferencesOnlyMigrationPublishesOnlyItsOwnTarget()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("preferences-config"))
                .toAbsolutePath()
                .normalize();
        Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migratePreferences(global);

        assertTrue(state.isPresent());
        assertEquals(MigrationTarget.PREFERENCES, state.orElseThrow().targetKind());
        assertTrue(Files.isRegularFile(
                global.resolve(ActualTargetResolver.PREFERENCES_BASENAME)));
        assertFalse(Files.exists(
                global.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertFalse(Files.exists(global.resolve("iamzombieq-client.toml")));
        assertNoArtifacts(
                global.resolve(ActualTargetResolver.SERVER_BASENAME));

        Optional<MigrationTargetState> restarted =
                ProductionConfigMigration.migratePreferences(global);
        assertEquals(
                MigrationTargetState.Outcome.COMPLETE,
                restarted.orElseThrow().outcome(),
                "the first call must close and release its OS lock");
    }

    @Test
    void serverLegacyChoosesGlobalWithoutCreatingWorldServerconfig()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("server-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("server-world"))
                .toAbsolutePath()
                .normalize();
        Path world = worldRoot.resolve("serverconfig");
        Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migrateServer(global, world);

        assertEquals(
                global.resolve(ActualTargetResolver.SERVER_BASENAME),
                state.orElseThrow().actualTarget());
        assertFalse(Files.exists(world));
        assertFalse(Files.exists(
                global.resolve(ActualTargetResolver.PREFERENCES_BASENAME)));
    }

    @Test
    void emptyExistingWorldServerconfigIsRelativelyGuardedWhileGlobalMigrates()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("existing-guard-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("existing-guard-world"))
                .toAbsolutePath()
                .normalize();
        Path world = Files.createDirectory(worldRoot.resolve("serverconfig"));
        Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());

        Optional<MigrationTargetState> state =
                ProductionConfigMigration.migrateServer(global, world);

        assertEquals(
                global.resolve(ActualTargetResolver.SERVER_BASENAME),
                state.orElseThrow().actualTarget());
        assertFalse(Files.exists(
                world.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(
                world.resolve(ActualTargetResolver.SERVER_BASENAME));
    }

    @Test
    void unsafeWorldServerconfigParentBlocksBeforeGlobalPublication()
            throws Exception {
        Path global = Files.createDirectory(
                        temporaryDirectory.resolve("unsafe-guard-config"))
                .toAbsolutePath()
                .normalize();
        Path worldRoot = Files.createDirectory(
                        temporaryDirectory.resolve("unsafe-guard-world"))
                .toAbsolutePath()
                .normalize();
        Path outside = Files.createDirectory(
                temporaryDirectory.resolve("unsafe-guard-outside"));
        Path world = Files.createSymbolicLink(
                worldRoot.resolve("serverconfig"), outside);
        Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migrateServer(global, world));

        assertTrue(failure.reason().contains(
                "World server-config parent"));
        assertFalse(Files.exists(
                global.resolve(ActualTargetResolver.SERVER_BASENAME)));
        assertNoArtifacts(
                global.resolve(ActualTargetResolver.SERVER_BASENAME));
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void writeUnchecked(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void moveUnchecked(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void createDirectoryUnchecked(Path path) {
        try {
            Files.createDirectory(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void assertNoArtifacts(Path target) {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertFalse(Files.exists(artifacts.lock()));
        assertFalse(Files.exists(artifacts.journal()));
        assertFalse(Files.exists(artifacts.backup()));
        assertFalse(Files.exists(artifacts.initial()));
        assertFalse(Files.exists(artifacts.marker()));
        artifacts.fixedStages()
                .forEach(stage -> assertFalse(Files.exists(stage)));
    }
}
