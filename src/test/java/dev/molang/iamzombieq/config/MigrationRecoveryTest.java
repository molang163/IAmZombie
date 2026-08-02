package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationRecoveryTest {
    private static final Path ROOT =
            Path.of("/migration-engine").toAbsolutePath().normalize();
    private static final Path LEGACY =
            ROOT.resolve("config/iamzombieq-common.toml");
    private static final Path TARGET =
            ROOT.resolve("world/serverconfig/iamzombieq-server.toml");

    @Test
    void committedTargetBeforeJournalAdvanceResumesWithoutRepublishOrReseed()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.failCommittedArtifact = AtomicConfigPublisher.Artifact.TARGET;

        assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));
        long targetPublishes = count(
                store, "publish:TARGET:" + paths().target());
        assertEquals(1, targetPublishes);
        byte[] committed = store.files.get(TARGET).clone();
        store.put(
                LEGACY,
                "changed = true".getBytes(StandardCharsets.UTF_8));
        store.events.clear();

        MigrationTargetState recovered = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertEquals(0, count(
                store, "publish:TARGET:" + paths().target()));
        assertTrue(java.util.Arrays.equals(committed, store.files.get(TARGET)));
        assertTrue(store.files.containsKey(paths().marker()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
    }

    @Test
    void preparedJournalRejectsExternallySeededInitialBeforeAnyPublication()
            throws IOException {
        MigrationEngineTestStore store = preparedStore();
        byte[] externalInitial =
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER);
        store.put(paths().initial(), externalInitial);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("artifact-phase-consistency", failure.operation());
        assertTrue(failure.reason().contains("initial"));
        assertTrue(failure.reason().contains("BACKUP_PUBLISHED"));
        assertArrayEquals(externalInitial, store.files.get(paths().initial()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")
                        || event.startsWith("resume:")));
        assertFalse(store.files.containsKey(paths().backup()));
        assertFalse(store.files.containsKey(TARGET));
        assertFalse(store.files.containsKey(paths().marker()));
    }

    @Test
    void preparedJournalRejectsExternallySeededTargetBeforeAnyPublication()
            throws IOException {
        MigrationEngineTestStore store = preparedStore();
        byte[] externalTarget =
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER);
        store.put(TARGET, externalTarget);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("artifact-phase-consistency", failure.operation());
        assertTrue(failure.reason().contains("target"));
        assertTrue(failure.reason().contains("INITIAL_PUBLISHED"));
        assertArrayEquals(externalTarget, store.files.get(TARGET));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")
                        || event.startsWith("resume:")));
        assertFalse(store.files.containsKey(paths().backup()));
        assertFalse(store.files.containsKey(paths().initial()));
        assertFalse(store.files.containsKey(paths().marker()));
    }

    @Test
    void backupPublishedJournalRejectsExternallySeededTargetBeforeInitialWrite()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector beforeInitialStage = point -> {
            if (point.artifact() == AtomicConfigPublisher.Artifact.INITIAL
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic stop before initial publication");
            }
        };
        assertThrows(
                MigrationFailure.class,
                () -> engine(beforeInitialStage)
                        .migrate(
                                ConfigMigrationEngineTest.request(TARGET),
                                store));
        assertEquals(
                MigrationTargetState.Phase.BACKUP_PUBLISHED,
                MigrationJournal.decode(store.files.get(paths().journal()))
                        .phase());
        byte[] externalTarget =
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER);
        store.put(TARGET, externalTarget);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(
                MigrationTargetState.Phase.BACKUP_PUBLISHED, failure.phase());
        assertEquals("artifact-phase-consistency", failure.operation());
        assertTrue(failure.reason().contains("target"));
        assertTrue(failure.reason().contains("INITIAL_PUBLISHED"));
        assertArrayEquals(externalTarget, store.files.get(TARGET));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")
                        || event.startsWith("resume:")));
        assertFalse(store.files.containsKey(paths().initial()));
        assertFalse(store.files.containsKey(paths().marker()));
    }

    @Test
    void recoveryDoesNotAdoptExactTargetAppearingAfterLockedEntrySnapshot()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector beforeTargetStage = point -> {
            if (point.artifact() == AtomicConfigPublisher.Artifact.TARGET
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic stop before target publication");
            }
        };
        assertThrows(
                MigrationFailure.class,
                () -> engine(beforeTargetStage)
                        .migrate(
                                ConfigMigrationEngineTest.request(TARGET),
                                store));
        assertEquals(
                MigrationTargetState.Phase.INITIAL_PUBLISHED,
                MigrationJournal.decode(store.files.get(paths().journal()))
                        .phase());
        byte[] externalTarget =
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER);
        store.appearAfterObservedAbsentPath = TARGET;
        store.appearAfterObservedAbsentOccurrence = 2;
        store.appearAfterObservedAbsentBytes = externalTarget;
        store.observedAbsentOccurrences = 0;
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(
                MigrationTargetState.Phase.INITIAL_PUBLISHED, failure.phase());
        assertEquals("recovery-publication-evidence", failure.operation());
        assertTrue(failure.reason().contains("target"));
        assertTrue(failure.reason().contains("locked recovery snapshot"));
        assertArrayEquals(externalTarget, store.files.get(TARGET));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.equals(
                                "publish:TARGET:" + TARGET)
                        || event.equals("resume:TARGET:" + TARGET)));
        assertFalse(store.files.containsKey(paths().marker()));
        assertEquals(
                MigrationTargetState.Phase.INITIAL_PUBLISHED,
                MigrationJournal.decode(store.files.get(paths().journal()))
                        .phase());
    }

    @Test
    void legacyChangeAfterPreparedIsF1BeforeBackup() throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.failCommittedArtifact = AtomicConfigPublisher.Artifact.JOURNAL;

        assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));
        assertTrue(store.files.containsKey(paths().journal()));
        store.put(
                LEGACY,
                new String(
                                LegacyConfigParserTest.fixtureBytes(),
                                StandardCharsets.UTF_8)
                        .replace(
                                "startingRottenFlesh = 9",
                                "startingRottenFlesh = 10")
                        .getBytes(StandardCharsets.UTF_8));
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertTrue(failure.reason().contains("legacy"));
        assertFalse(store.files.containsKey(paths().backup()));
    }

    @Test
    void permanentLockWithoutJournalOrMarkerRequiresManualRecoveryOnSameInode()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector beforeJournalCreate = point -> {
            if (point.artifact() == AtomicConfigPublisher.Artifact.JOURNAL
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic crash before journal stage creation");
            }
        };

        assertThrows(
                MigrationFailure.class,
                () -> engine(beforeJournalCreate)
                        .migrate(
                                ConfigMigrationEngineTest.request(TARGET),
                                store));
        String lockIdentity = store.identities.get(paths().lock());
        byte[] lockPayload = store.files.get(paths().lock()).clone();
        assertFalse(store.files.containsKey(paths().journal()));
        assertFalse(store.files.containsKey(paths().marker()));
        store.events.clear();

        MigrationFailure recovered = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("locked-recovery", recovered.operation());
        assertTrue(recovered.reason().contains("manual recovery"));
        assertEquals(lockIdentity, store.identities.get(paths().lock()));
        assertArrayEquals(lockPayload, store.files.get(paths().lock()));
        assertTrue(store.events.contains("lock:open:" + paths().lock()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
        assertFalse(store.files.containsKey(paths().marker()));
    }

    @Test
    void emptyFirstCreationLockIsInitializedInPlaceAndMigrationContinues()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.put(paths().lock(), new byte[0]);
        String originalIdentity = store.identities.get(paths().lock());
        store.events.clear();

        MigrationTargetState recovered = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(
                MigrationTargetState.Outcome.MIGRATED,
                recovered.outcome());
        assertEquals(originalIdentity, store.identities.get(paths().lock()));
        assertTrue(store.files.get(paths().lock()).length > 0);
        assertTrue(store.events.contains("lock:open:" + paths().lock()));
        assertFalse(store.events.contains("lock:create:" + paths().lock()));
        assertTrue(store.events.contains("read:LEGACY:" + LEGACY));
        assertTrue(store.files.containsKey(TARGET));
        assertTrue(store.files.containsKey(paths().marker()));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.contains("unlink")
                        || event.contains("replace")));
    }

    @Test
    void emptyLockWithAnyExistingArtifactOrStageStaysF1AndUnmodified()
            throws IOException {
        java.util.List<Path> conflicts = new java.util.ArrayList<>(
                java.util.List.of(
                        TARGET,
                        paths().journal(),
                        paths().backup(),
                        paths().initial(),
                        paths().marker()));
        conflicts.addAll(paths().fixedStages());

        for (Path conflict : conflicts) {
            MigrationEngineTestStore store = new MigrationEngineTestStore();
            store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
            store.put(paths().lock(), new byte[0]);
            String lockIdentity = store.identities.get(paths().lock());
            store.put(
                    conflict,
                    ("conflict:" + conflict.getFileName())
                            .getBytes(StandardCharsets.UTF_8));
            store.events.clear();

            assertThrows(
                    MigrationFailure.class,
                    () -> engine().migrate(
                            ConfigMigrationEngineTest.request(TARGET), store),
                    conflict.toString());

            assertEquals(
                    lockIdentity,
                    store.identities.get(paths().lock()),
                    conflict.toString());
            assertEquals(
                    0,
                    store.files.get(paths().lock()).length,
                    conflict.toString());
            assertFalse(
                    store.events.contains("read:LEGACY:" + LEGACY),
                    conflict.toString());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("publish:")),
                    conflict.toString());
        }
    }

    @Test
    void emptyLockUnderLockArtifactRaceFailsBeforeInitializationOrLegacyRead()
            throws IOException {
        java.util.List<Path> conflicts = new java.util.ArrayList<>(
                java.util.List.of(
                        TARGET,
                        paths().journal(),
                        paths().backup(),
                        paths().initial(),
                        paths().marker()));
        conflicts.addAll(paths().fixedStages());

        for (Path conflict : conflicts) {
            MigrationEngineTestStore store = pristineEmptyLockStore();
            String lockIdentity = store.identities.get(paths().lock());
            store.beforeEmptyLockRecoveryGate = () -> store.put(
                    conflict,
                    ("under-lock-conflict:" + conflict.getFileName())
                            .getBytes(StandardCharsets.UTF_8));
            store.events.clear();

            assertThrows(
                    MigrationFailure.class,
                    () -> engine().migrate(
                            ConfigMigrationEngineTest.request(TARGET), store),
                    conflict.toString());

            assertEquals(
                    lockIdentity,
                    store.identities.get(paths().lock()),
                    conflict.toString());
            assertEquals(
                    0,
                    store.files.get(paths().lock()).length,
                    conflict.toString());
            assertFalse(
                    store.events.contains("read:LEGACY:" + LEGACY),
                    conflict.toString());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("publish:")),
                    conflict.toString());
        }
    }

    @Test
    void emptyLockPostInitializationArtifactRaceFailsBeforeLegacyOrPublish()
            throws IOException {
        java.util.List<Path> conflicts = new java.util.ArrayList<>(
                java.util.List.of(
                        TARGET,
                        paths().journal(),
                        paths().backup(),
                        paths().initial(),
                        paths().marker()));
        conflicts.addAll(paths().fixedStages());

        for (Path conflict : conflicts) {
            MigrationEngineTestStore store = pristineEmptyLockStore();
            String lockIdentity = store.identities.get(paths().lock());
            byte[] conflictBytes =
                    ("post-initialization-conflict:"
                                    + conflict.getFileName())
                            .getBytes(StandardCharsets.UTF_8);
            java.util.concurrent.atomic.AtomicBoolean injected =
                    new java.util.concurrent.atomic.AtomicBoolean();
            MigrationFaultInjector appearAfterInitialization = point -> {
                if (point.operation()
                                == MigrationFaultInjector.Operation.LOCK_ACQUIRE
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER
                        && injected.compareAndSet(false, true)) {
                    store.put(conflict, conflictBytes);
                }
            };
            store.events.clear();

            assertThrows(
                    MigrationFailure.class,
                    () -> engine(appearAfterInitialization)
                            .migrate(
                                    ConfigMigrationEngineTest.request(TARGET),
                                    store),
                    conflict.toString());

            assertTrue(injected.get(), conflict.toString());
            assertEquals(
                    lockIdentity,
                    store.identities.get(paths().lock()),
                    conflict.toString());
            assertTrue(
                    store.files.get(paths().lock()).length > 0,
                    conflict.toString());
            assertArrayEquals(
                    conflictBytes,
                    store.files.get(conflict),
                    conflict.toString());
            assertFalse(
                    store.events.contains("read:LEGACY:" + LEGACY),
                    conflict.toString());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("publish:")),
                    conflict.toString());
        }
    }

    @Test
    void emptyLockRequiresApplicableSafeLegacyBeforeAndInsideOsLock()
            throws IOException {
        MigrationEngineTestStore missing = new MigrationEngineTestStore();
        missing.put(paths().lock(), new byte[0]);
        MigrationFailure missingFailure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), missing));
        assertEquals(LEGACY, missingFailure.legacy());
        assertEquals(TARGET, missingFailure.target());
        assertTrue(missingFailure.phase()
                == MigrationTargetState.Phase.LOCKED);
        assertTrue(missingFailure.reason().contains("lock"));
        assertTrue(missingFailure.recovery().contains("preserve"));
        assertTrue(missingFailure.recovery().contains("Restart"));
        assertEquals(0, missing.files.get(paths().lock()).length);
        assertFalse(missing.events.contains("read:LEGACY:" + LEGACY));

        MigrationEngineTestStore unsafe = pristineEmptyLockStore();
        unsafe.forcedObservations.put(
                LEGACY,
                new MigrationPathState.Observation(
                        MigrationPathState.UNSAFE,
                        "synthetic unsafe legacy",
                        null));
        assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), unsafe));
        assertEquals(0, unsafe.files.get(paths().lock()).length);
        assertFalse(unsafe.events.contains("read:LEGACY:" + LEGACY));

        MigrationEngineTestStore disappears = pristineEmptyLockStore();
        disappears.beforeEmptyLockRecoveryGate =
                () -> disappears.remove(LEGACY);
        assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), disappears));
        assertEquals(0, disappears.files.get(paths().lock()).length);
        assertFalse(disappears.events.contains("read:LEGACY:" + LEGACY));
    }

    @Test
    void completeAlwaysRevalidatesTargetAndNeverReadsLegacy()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        store.events.clear();

        MigrationTargetState complete = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(MigrationTargetState.Outcome.COMPLETE, complete.outcome());
        assertTrue(store.events.contains("read:TARGET:" + TARGET));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));

        store.remove(TARGET);
        store.events.clear();
        MigrationFailure missing = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));
        assertEquals(MigrationTargetState.Phase.COMPLETE, missing.phase());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
    }

    @Test
    void corruptCompleteTargetIsF1InsteadOfCorrectionOrReseed()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        store.put(TARGET, "invalid".getBytes(StandardCharsets.UTF_8));
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.COMPLETE, failure.phase());
        assertEquals("complete-target-validation", failure.operation());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.equals(
                        "publish:TARGET:" + TARGET)));
    }

    @Test
    void completeMissingInitialIsF1WithoutLegacyRead() throws IOException {
        MigrationEngineTestStore store = migratedStore();
        store.remove(paths().initial());
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.COMPLETE, failure.phase());
        assertEquals("initial-recovery", failure.operation());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void completeAcceptsEquivalentCrLfFormattingWithoutAnyPublication()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        byte[] changed = new String(
                        store.files.get(TARGET), StandardCharsets.UTF_8)
                .replace("\n", "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(new TargetConfigValidator(ConfigSchemaCatalog.load())
                .validateEncoded(
                        MigrationTarget.SERVER,
                        new String(changed, StandardCharsets.UTF_8))
                .valid());
        store.put(TARGET, changed);
        store.events.clear();
        int movesBefore = store.atomicMoves.size();

        MigrationTargetState completed = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(
                MigrationTargetState.Outcome.COMPLETE,
                completed.outcome());
        assertArrayEquals(changed, store.files.get(TARGET));
        assertEquals(movesBefore, store.atomicMoves.size());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void completeRejectsEveryInvalidCurrentConfigurationWithoutReseed()
            throws IOException {
        String valid = new String(
                ConfigMigrationEngineTest.canonical(MigrationTarget.SERVER),
                StandardCharsets.UTF_8);
        java.util.LinkedHashMap<String, byte[]> invalid =
                new java.util.LinkedHashMap<>();
        invalid.put(
                "missing",
                valid.replaceFirst(
                                "(?m)^startingRottenFlesh = 9\\R", "")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "wrong-comment",
                valid.replaceFirst("(?m)^#.*$", "#changed authority comment")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "old-writer-extra-comment-space",
                valid.replaceFirst("(?m)^#", "# ")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "unknown",
                (valid + "\nunknownTargetKey = true\n")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "wrong-type",
                valid.replace(
                                "startingRottenFlesh = 9",
                                "startingRottenFlesh = \"nine\"")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "correction-unstable",
                valid.replace(
                                "debugLogging = true",
                                "debugLogging = \"true\"")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "out-of-range",
                valid.replace(
                                "startingRottenFlesh = 9",
                                "startingRottenFlesh = 65")
                        .getBytes(StandardCharsets.UTF_8));
        invalid.put(
                "malformed-utf8",
                new byte[] {(byte) 0xc3, (byte) 0x28});

        for (java.util.Map.Entry<String, byte[]> variant
                : invalid.entrySet()) {
            MigrationEngineTestStore store = migratedStore();
            byte[] changed = variant.getValue();
            store.put(TARGET, changed);
            store.events.clear();
            int movesBefore = store.atomicMoves.size();

            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> engine().migrate(
                            ConfigMigrationEngineTest.request(TARGET), store),
                    variant.getKey());

            assertEquals(
                    MigrationTargetState.Phase.COMPLETE,
                    failure.phase(),
                    variant.getKey());
            assertEquals(
                    "complete-target-validation",
                    failure.operation(),
                    variant.getKey());
            assertArrayEquals(changed, store.files.get(TARGET));
            assertEquals(movesBefore, store.atomicMoves.size());
            assertFalse(
                    store.events.contains("read:LEGACY:" + LEGACY),
                    variant.getKey());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("publish:")),
                    variant.getKey());
        }
    }

    @Test
    void completeUnsafeOrUnknownTargetMetadataRemainsF1WithoutLegacyRead()
            throws IOException {
        for (MigrationPathState.Observation observation : java.util.List.of(
                new MigrationPathState.Observation(
                        MigrationPathState.UNSAFE,
                        "synthetic unsafe target",
                        null),
                new MigrationPathState.Observation(
                        MigrationPathState.UNKNOWN,
                        "synthetic target metadata I/O failure",
                        new IOException("synthetic metadata I/O")))) {
            MigrationEngineTestStore store = migratedStore();
            byte[] journal = store.files.get(paths().journal()).clone();
            byte[] marker = store.files.get(paths().marker()).clone();
            store.forcedObservations.put(TARGET, observation);
            store.events.clear();

            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> engine().migrate(
                            ConfigMigrationEngineTest.request(TARGET), store),
                    observation.state().name());

            assertEquals(
                    "nofollow-metadata",
                    failure.operation(),
                    observation.state().name());
            assertTrue(
                    failure.reason().contains(observation.detail()),
                    observation.state().name());
            assertArrayEquals(journal, store.files.get(paths().journal()));
            assertArrayEquals(marker, store.files.get(paths().marker()));
            assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
            assertFalse(store.events.stream()
                    .anyMatch(event -> event.startsWith("publish:")));
        }
    }

    @Test
    void completeAcceptsIndependentCommentChangeWithoutReseedOrRepublish()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        byte[] changed = (new String(
                                store.files.get(TARGET),
                                StandardCharsets.UTF_8)
                        + "# semantically neutral byte change\n")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(new TargetConfigValidator(ConfigSchemaCatalog.load())
                .validateEncoded(
                        MigrationTarget.SERVER,
                        new String(changed, StandardCharsets.UTF_8))
                .valid());
        store.put(TARGET, changed);
        store.events.clear();
        int movesBefore = store.atomicMoves.size();

        MigrationTargetState completed = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(
                MigrationTargetState.Outcome.COMPLETE,
                completed.outcome());
        assertArrayEquals(changed, store.files.get(TARGET));
        assertEquals(movesBefore, store.atomicMoves.size());
        assertTrue(store.events.contains("read:TARGET:" + TARGET));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void completeJournalWithoutMarkerStillRequiresHistoricalTargetEvidence()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        store.remove(paths().marker());
        byte[] changed = (new String(
                                store.files.get(TARGET),
                                StandardCharsets.UTF_8)
                        + "# same typed projection, different bytes\n")
                .getBytes(StandardCharsets.UTF_8);
        store.put(TARGET, changed);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(MigrationTargetState.Phase.COMPLETE, failure.phase());
        assertEquals("target-hash-validation", failure.operation());
        assertArrayEquals(changed, store.files.get(TARGET));
        assertFalse(store.files.containsKey(paths().marker()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void targetPublishedRecoveryStillRequiresHistoricalTargetEvidence()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector stopBeforeCompleteJournal = point -> {
            if (point.phase()
                            == MigrationTargetState.Phase.TARGET_PUBLISHED
                    && point.artifact()
                            == AtomicConfigPublisher.Artifact.JOURNAL
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic stop before COMPLETE journal");
            }
        };
        assertThrows(
                MigrationFailure.class,
                () -> engine(stopBeforeCompleteJournal)
                        .migrate(
                                ConfigMigrationEngineTest.request(TARGET),
                                store));
        assertEquals(
                MigrationTargetState.Phase.TARGET_PUBLISHED,
                MigrationJournal.decode(store.files.get(paths().journal()))
                        .phase());
        byte[] changed = new String(
                        store.files.get(TARGET), StandardCharsets.UTF_8)
                .replace(
                        "startingRottenFlesh = 9",
                        "startingRottenFlesh = 10")
                .getBytes(StandardCharsets.UTF_8);
        store.put(TARGET, changed);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals(
                MigrationTargetState.Phase.TARGET_PUBLISHED,
                failure.phase());
        assertEquals("target-hash-validation", failure.operation());
        assertArrayEquals(changed, store.files.get(TARGET));
        assertFalse(store.files.containsKey(paths().marker()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void completeEvidenceRequiresExactPhaseArtifactKeySet()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        Path journalPath = paths().journal();
        MigrationJournal journal =
                MigrationJournal.decode(store.files.get(journalPath));
        MigrationEvidence evidence = journal.evidence();
        java.util.LinkedHashMap<String, String> hashes =
                new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, MigrationEvidence.Durability>
                durability = new java.util.LinkedHashMap<>();
        for (String key : java.util.List.of("lock", "backup")) {
            hashes.put(key, evidence.artifactHashes().get(key));
            durability.put(key, evidence.artifactDurability().get(key));
        }
        MigrationEvidence incomplete = new MigrationEvidence(
                evidence.targetKind(),
                evidence.target(),
                evidence.binding(),
                evidence.schemaVersion(),
                evidence.profile(),
                evidence.commitProfile(),
                evidence.lockIdentity(),
                evidence.phase(),
                evidence.projectionSha256(),
                evidence.rawLegacySha256(),
                hashes,
                durability);
        store.put(
                journalPath,
                new MigrationJournal(journal.generation(), incomplete).encode());
        store.remove(paths().marker());
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("evidence-artifact-set-validation", failure.operation());
        assertTrue(failure.reason().contains("artifact set"));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void completeAcceptsValidAdministratorValueChangeWithoutTargetWrite()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        String canonical = new String(
                store.files.get(TARGET), StandardCharsets.UTF_8);
        String changed = canonical.replace(
                "startingRottenFlesh = 9",
                "startingRottenFlesh = 10");
        assertTrue(new TargetConfigValidator(ConfigSchemaCatalog.load())
                .validateEncoded(MigrationTarget.SERVER, changed)
                .valid());
        byte[] changedBytes = changed.getBytes(StandardCharsets.UTF_8);
        store.put(TARGET, changedBytes);
        store.events.clear();
        int movesBefore = store.atomicMoves.size();

        MigrationTargetState completed = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertEquals(
                MigrationTargetState.Outcome.COMPLETE,
                completed.outcome());
        assertArrayEquals(changedBytes, store.files.get(TARGET));
        assertEquals(movesBefore, store.atomicMoves.size());
        assertTrue(store.events.contains("read:TARGET:" + TARGET));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void schemaMismatchedJournalEvidenceIsF1()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        Path journalPath = paths().journal();
        MigrationJournal journal =
                MigrationJournal.decode(store.files.get(journalPath));
        MigrationEvidence evidence = journal.evidence();
        MigrationEvidence mismatched = new MigrationEvidence(
                evidence.targetKind(),
                evidence.target(),
                evidence.binding(),
                "future-schema",
                evidence.profile(),
                evidence.commitProfile(),
                evidence.lockIdentity(),
                evidence.phase(),
                evidence.projectionSha256(),
                evidence.rawLegacySha256(),
                evidence.artifactHashes(),
                evidence.artifactDurability());
        store.put(
                journalPath,
                new MigrationJournal(journal.generation(), mismatched).encode());
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("evidence-binding-validation", failure.operation());
        assertTrue(failure.reason().contains("schema"));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void projectionMismatchedJournalEvidenceIsF1()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        Path journalPath = paths().journal();
        MigrationJournal journal =
                MigrationJournal.decode(store.files.get(journalPath));
        MigrationEvidence evidence = journal.evidence();
        String mismatchedProjection =
                evidence.projectionSha256().equals("0".repeat(64))
                        ? "1".repeat(64)
                        : "0".repeat(64);
        MigrationEvidence mismatched = new MigrationEvidence(
                evidence.targetKind(),
                evidence.target(),
                evidence.binding(),
                evidence.schemaVersion(),
                evidence.profile(),
                evidence.commitProfile(),
                evidence.lockIdentity(),
                evidence.phase(),
                mismatchedProjection,
                evidence.rawLegacySha256(),
                evidence.artifactHashes(),
                evidence.artifactDurability());
        store.put(
                journalPath,
                new MigrationJournal(journal.generation(), mismatched).encode());
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("prepared-recovery", failure.operation());
        assertTrue(failure.reason().contains("projection"));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void markerWithoutPermanentLockIsDeterministicF1()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        store.remove(paths().lock());
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("evidence-consistency", failure.operation());
        assertTrue(failure.reason().contains("marker"));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
    }

    @Test
    void fixedOrphanStageIsNeverDeletedPromotedOrAdopted()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        Path stage = paths().fixedStages().getFirst();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.put(stage, "partial".getBytes(StandardCharsets.UTF_8));

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));

        assertEquals("orphan-stage-check", failure.operation());
        assertTrue(failure.recovery().contains(stage.toString()));
        assertTrue(failure.recovery().contains("copy"));
        assertTrue(failure.recovery().contains("verify"));
        assertTrue(failure.recovery().contains(
                "remove only the original orphan fixed stage"));
        assertTrue(failure.recovery().contains("restart"));
        assertTrue(store.files.containsKey(stage));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));

        byte[] operatorEvidenceCopy = store.files.get(stage).clone();
        store.remove(stage);
        store.events.clear();
        MigrationTargetState recovered = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);

        assertArrayEquals(
                "partial".getBytes(StandardCharsets.UTF_8),
                operatorEvidenceCopy);
        assertEquals(MigrationTargetState.Outcome.MIGRATED, recovered.outcome());
        assertFalse(store.files.containsKey(stage));
        assertTrue(store.files.containsKey(TARGET));
    }

    @Test
    void markerAndJournalCodecsRejectUnknownOrMissingEvidence()
            throws IOException {
        MigrationEngineTestStore store = migratedStore();
        byte[] journalBytes = store.files.get(paths().journal());
        byte[] markerBytes = store.files.get(paths().marker());

        MigrationJournal journal = MigrationJournal.decode(journalBytes);
        MigrationMarker marker = MigrationMarker.decode(markerBytes);
        assertEquals(MigrationTargetState.Phase.COMPLETE, journal.phase());
        assertEquals(MigrationTargetState.Phase.COMPLETE, marker.phase());
        assertEquals(
                journal.evidence().binding(), marker.evidence().binding());

        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationJournal.decode(
                        new String(journalBytes, StandardCharsets.UTF_8)
                                .replace(
                                        "version=1\n",
                                        "version=1\nunknown=value\n")
                                .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationMarker.decode(
                        new String(markerBytes, StandardCharsets.UTF_8)
                                .replace("evidence=", "missingEvidence=")
                                .getBytes(StandardCharsets.UTF_8)));
    }

    private static MigrationEngineTestStore migratedStore()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationTargetState migrated = engine().migrate(
                ConfigMigrationEngineTest.request(TARGET), store);
        assertEquals(MigrationTargetState.Outcome.MIGRATED, migrated.outcome());
        return store;
    }

    private static MigrationEngineTestStore pristineEmptyLockStore()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.put(paths().lock(), new byte[0]);
        return store;
    }

    private static MigrationEngineTestStore preparedStore()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.failCommittedArtifact = AtomicConfigPublisher.Artifact.JOURNAL;
        assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        ConfigMigrationEngineTest.request(TARGET), store));
        assertEquals(
                MigrationTargetState.Phase.PREPARED,
                MigrationJournal.decode(store.files.get(paths().journal()))
                        .phase());
        assertFalse(store.files.containsKey(paths().backup()));
        assertFalse(store.files.containsKey(paths().initial()));
        assertFalse(store.files.containsKey(TARGET));
        assertFalse(store.files.containsKey(paths().marker()));
        return store;
    }

    private static ConfigMigrationEngine engine() {
        return engine(MigrationFaultInjector.none());
    }

    private static ConfigMigrationEngine engine(
            MigrationFaultInjector faults) {
        return new ConfigMigrationEngine(
                ConfigSchemaCatalog.load(), faults);
    }

    private static MigrationFileSystem.ArtifactPaths paths() {
        return ConfigMigrationEngineTest.paths(TARGET);
    }

    private static long count(
            MigrationEngineTestStore store, String event) {
        return store.events.stream().filter(event::equals).count();
    }
}
