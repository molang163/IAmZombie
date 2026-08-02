package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConfigMigrationEngineTest {
    private static final Path ROOT =
            Path.of("/migration-engine").toAbsolutePath().normalize();
    private static final Path LEGACY =
            ROOT.resolve("config/iamzombieq-common.toml");
    private static final Path SERVER =
            ROOT.resolve("world/serverconfig/iamzombieq-server.toml");
    private static final Path PREFERENCES =
            ROOT.resolve("config/iamzombieq-preferences.toml");

    @Test
    void freshPerformsMetadataOnlyAndCreatesNoArtifact() {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        MigrationTargetState result = engine().migrate(request(SERVER), store);

        assertEquals(MigrationTargetState.Outcome.FRESH, result.outcome());
        assertEquals(MigrationTargetState.Phase.NO_EVIDENCE, result.phase());
        assertTrue(store.events.stream()
                .allMatch(event -> event.startsWith("state:")));
        assertTrue(store.files.isEmpty());
    }

    @Test
    void validExistingTargetIgnoresCorruptLegacyAndDoesNotMergeOrMark()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(SERVER, canonical(MigrationTarget.SERVER));
        store.put(LEGACY, "not = [valid".getBytes(StandardCharsets.UTF_8));

        MigrationTargetState result = engine().migrate(request(SERVER), store);

        assertEquals(
                MigrationTargetState.Outcome.EXISTING_VALID, result.outcome());
        assertTrue(store.events.contains("read:EXISTING_TARGET:" + SERVER));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.equals("read:LEGACY:" + LEGACY)));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("lock:")));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
        assertArrayEquals(
                canonical(MigrationTarget.SERVER), store.files.get(SERVER));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void validExistingTargetNeverQueriesUntrustedLegacyMetadata()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(SERVER, canonical(MigrationTarget.SERVER));
        AccessDeniedException denied =
                new AccessDeniedException(LEGACY.toString(), null, "denied");
        store.forcedObservations.put(
                LEGACY,
                MigrationPathState.observe(() -> {
                    throw denied;
                }));

        MigrationTargetState result = engine().migrate(request(SERVER), store);

        assertEquals(
                MigrationTargetState.Outcome.EXISTING_VALID, result.outcome());
        assertFalse(store.events.contains("state:" + LEGACY));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("lock:")));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void validAdministratorEditOnExistingTargetRemainsAccepted()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        String canonical = new String(
                canonical(MigrationTarget.SERVER), StandardCharsets.UTF_8);
        String edited = canonical.replace(
                "startingRottenFlesh = 9\n",
                "startingRottenFlesh = 10\n");
        assertNotEquals(canonical, edited);
        store.put(SERVER, edited.getBytes(StandardCharsets.UTF_8));
        store.put(LEGACY, "not = [valid".getBytes(StandardCharsets.UTF_8));

        MigrationTargetState result = engine().migrate(request(SERVER), store);

        assertEquals(
                MigrationTargetState.Outcome.EXISTING_VALID, result.outcome());
        assertArrayEquals(
                edited.getBytes(StandardCharsets.UTF_8),
                store.files.get(SERVER));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("lock:")));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void missingExistingKeyFailsWithoutMerge() throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        String encoded = new String(
                canonical(MigrationTarget.SERVER), StandardCharsets.UTF_8);
        int firstBlank = encoded.indexOf("\n\n");
        store.put(
                SERVER,
                encoded.substring(firstBlank + 2)
                        .getBytes(StandardCharsets.UTF_8));
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals(
                MigrationTargetState.Phase.NO_EVIDENCE, failure.phase());
        assertEquals("existing-target-validation", failure.operation());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void invalidExistingTargetsFailWithoutLegacyReadOrMigrationWrites()
            throws IOException {
        String valid = new String(
                canonical(MigrationTarget.SERVER), StandardCharsets.UTF_8);
        Map<String, String> invalidTargets = Map.of(
                "malformed",
                "not = [valid",
                "wrong-type",
                valid.replace(
                        "startingRottenFlesh = 9",
                        "startingRottenFlesh = \"nine\""),
                "out-of-range",
                valid.replace(
                        "startingRottenFlesh = 9",
                        "startingRottenFlesh = 65"),
                "unknown-key",
                valid + "\nunknownTargetKey = true\n",
                "correction-unstable",
                valid.replaceFirst("(?m)^#.*$", "#changed"),
                "old-writer-extra-comment-space",
                valid.replaceFirst("(?m)^#", "# "));

        for (Map.Entry<String, String> variant : invalidTargets.entrySet()) {
            MigrationEngineTestStore store = new MigrationEngineTestStore();
            byte[] original =
                    variant.getValue().getBytes(StandardCharsets.UTF_8);
            store.put(SERVER, original);
            store.put(
                    LEGACY,
                    "corrupt legacy must not be read"
                            .getBytes(StandardCharsets.UTF_8));

            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> engine().migrate(request(SERVER), store),
                    variant.getKey());

            assertEquals(
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    failure.phase(),
                    variant.getKey());
            assertEquals(
                    "existing-target-validation",
                    failure.operation(),
                    variant.getKey());
            assertArrayEquals(
                    original, store.files.get(SERVER), variant.getKey());
            assertFalse(
                    store.events.contains("read:LEGACY:" + LEGACY),
                    variant.getKey());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("lock:")),
                    variant.getKey());
            assertFalse(
                    store.events.stream()
                            .anyMatch(event -> event.startsWith("publish:")),
                    variant.getKey());
            assertFalse(
                    store.files.containsKey(paths(SERVER).marker()),
                    variant.getKey());
        }
    }

    @Test
    void absentTargetAndPresentLegacyPublishesCompleteBoundTarget()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());

        MigrationTargetState result = engine().migrate(request(SERVER), store);

        assertEquals(MigrationTargetState.Outcome.MIGRATED, result.outcome());
        assertEquals(MigrationTargetState.Phase.COMPLETE, result.phase());
        assertTrue(store.files.containsKey(SERVER));
        assertTrue(store.files.containsKey(paths(SERVER).lock()));
        assertTrue(store.files.containsKey(paths(SERVER).journal()));
        assertTrue(store.files.containsKey(paths(SERVER).backup()));
        assertTrue(store.files.containsKey(paths(SERVER).initial()));
        assertTrue(store.files.containsKey(paths(SERVER).marker()));
        assertFalse(paths(SERVER).fixedStages().stream()
                .anyMatch(store.files::containsKey));

        int targetRead = store.events.lastIndexOf("read:TARGET:" + SERVER);
        int markerPublish = store.events.indexOf(
                "publish:MARKER:" + paths(SERVER).marker());
        assertTrue(targetRead >= 0);
        assertTrue(markerPublish > targetRead);
        TargetConfigValidator.Result validation =
                new TargetConfigValidator(ConfigSchemaCatalog.load())
                        .validateEncoded(
                                MigrationTarget.SERVER,
                                new String(
                                        store.files.get(SERVER),
                                        StandardCharsets.UTF_8));
        assertTrue(validation.valid(), validation.issues().toString());
    }

    @Test
    void targetAppearanceUnderNewPermanentLockIsF1BeforeLegacyRead()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.appearTargetDuringNewLock = canonical(MigrationTarget.SERVER);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
        assertEquals("under-lock-target-check", failure.operation());
        assertTrue(store.files.containsKey(paths(SERVER).lock()));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.files.containsKey(paths(SERVER).journal()));
        assertFalse(store.files.containsKey(paths(SERVER).backup()));
        assertFalse(store.files.containsKey(paths(SERVER).initial()));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void initializedLockOnlyStateRequiresManualRecoveryWithoutLegacyRead()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.appearTargetAfterLockInitialization =
                canonical(MigrationTarget.SERVER);

        MigrationFailure first = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals("under-lock-target-check", first.operation());
        assertTrue(store.files.containsKey(paths(SERVER).lock()));
        assertTrue(store.files.get(paths(SERVER).lock()).length > 0);
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.files.containsKey(paths(SERVER).journal()));

        store.remove(SERVER);
        store.events.clear();
        MigrationFailure restart = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals("locked-recovery", restart.operation());
        assertTrue(restart.reason().contains("manual recovery"));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void permanentLockPathnameAbaCannotReturnMigrated()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        Path lock = paths(SERVER).lock();
        store.lockPathToSwapDuringBinding = lock;

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
        assertEquals("permanent-lock-revalidation", failure.operation());
        assertTrue(failure.reason().contains("identity"));
        assertTrue(store.files.containsKey(lock));
        assertFalse(store.files.containsKey(SERVER));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void permanentLockPayloadBindsEveryAncestorIdentity() throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request request = request(SERVER);

        engine().migrate(request, store);

        String payload = new String(
                store.files.get(paths(SERVER).lock()),
                StandardCharsets.UTF_8);
        for (int index = 0;
                index < request.binding().ancestors().size();
                index++) {
            MigrationBinding.Ancestor ancestor =
                    request.binding().ancestors().get(index);
            assertTrue(payload.contains(
                    "ancestor."
                            + index
                            + ".path="
                            + ancestor.path()
                            + "\n"));
            assertTrue(payload.contains(
                    "ancestor."
                            + index
                            + ".identity="
                            + ancestor.identity()
                            + "\n"));
        }
    }

    @Test
    void metadataFailureRetainsConcreteCauseInTheF1Contract()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        AccessDeniedException denied =
                new AccessDeniedException(SERVER.toString(), null, "denied");
        store.forcedObservations.put(
                SERVER,
                MigrationPathState.observe(() -> {
                    throw denied;
                }));

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals("nofollow-metadata", failure.operation());
        assertTrue(failure.reason().contains("AccessDeniedException"));
        assertTrue(failure.reason().contains("denied"));
        assertEquals(denied, failure.getCause());
    }

    @Test
    void serverAndPreferencesUseIndependentProjectionAndEvidence()
            throws IOException {
        Path preferencesTarget =
                ROOT.resolve("config/iamzombieq-preferences-client.toml");
        MigrationEngineTestStore serverStore = new MigrationEngineTestStore();
        MigrationEngineTestStore preferencesStore =
                new MigrationEngineTestStore();
        byte[] legacy = LegacyConfigParserTest.fixtureBytes();
        serverStore.put(LEGACY, legacy);
        preferencesStore.put(LEGACY, legacy);

        MigrationTargetState server =
                engine().migrate(request(SERVER), serverStore);
        MigrationTargetState preferences = engine().migrate(
                request(MigrationTarget.PREFERENCES, preferencesTarget),
                preferencesStore);

        assertNotEquals(
                server.projectionSha256(), preferences.projectionSha256());
        assertNotEquals(
                paths(SERVER).marker(), paths(preferences.actualTarget()).marker());
        assertEquals(52, parsed(serverStore.files.get(SERVER)).size());
        assertEquals(
                4,
                parsed(preferencesStore.files.get(preferences.actualTarget()))
                        .size());
    }

    @Test
    void blockingFailureRendersPathsPhaseReasonAndRecovery()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(SERVER, "broken".getBytes(StandardCharsets.UTF_8));

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));
        String rendered = failure.getMessage();

        assertTrue(rendered.contains(LEGACY.toString()));
        assertTrue(rendered.contains(SERVER.toString()));
        assertTrue(rendered.contains("NO_EVIDENCE"));
        assertTrue(rendered.contains("malformed"));
        assertTrue(rendered.contains("Recovery:"));
        assertFalse(failure.synthetic());
    }

    @Test
    void engineRoutesPerOperationPublicationFaultsAndLabelsThemSynthetic()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector injector = point -> {
            if (point.artifact()
                            == AtomicConfigPublisher.Artifact.BACKUP
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic backup stage-create fault");
            }
        };

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine(injector).migrate(request(SERVER), store));

        assertTrue(failure.synthetic());
        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("backup", failure.artifact());
        assertEquals("STAGE_CREATE", failure.operation());
        assertTrue(store.files.containsKey(paths(SERVER).lock()));
        assertTrue(store.files.containsKey(paths(SERVER).journal()));
        assertFalse(store.files.containsKey(paths(SERVER).backup()));
        assertFalse(store.files.containsKey(paths(SERVER).initial()));
        assertFalse(store.files.containsKey(SERVER));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    @Test
    void failedGlobalOperationStillRevalidatesWorldAbsenceGuard()
            throws IOException {
        Path globalParent = ROOT.resolve("config");
        Path worldParent = ROOT.resolve("world/serverconfig");
        Path worldTarget =
                worldParent.resolve(ActualTargetResolver.SERVER_BASENAME);
        AtomicBoolean worldAppeared = new AtomicBoolean();
        ActualTargetResolver resolver = new ActualTargetResolver(path -> {
            if (path.equals(worldTarget) && worldAppeared.get()) {
                return MigrationPathState.Observation.fromState(
                        MigrationPathState.PRESENT);
            }
            return MigrationPathState.Observation.fromState(
                    MigrationPathState.ABSENT);
        });
        ActualTargetResolver.Resolution resolution =
                resolver.resolveServer(globalParent, worldParent);
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        MigrationFaultInjector injector = point -> {
            if (point.artifact()
                            == AtomicConfigPublisher.Artifact.BACKUP
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                worldAppeared.set(true);
                throw new IllegalStateException(
                        "synthetic publication failure");
            }
        };

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine(injector)
                        .migrate(
                                request(
                                        MigrationTarget.SERVER,
                                        resolution.actualTarget(),
                                        resolution.worldGuard()),
                                store));

        assertFalse(failure.synthetic());
        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertTrue(failure.reason().contains(
                "World candidate is not safely absent"));
        assertTrue(failure.reason().contains(worldTarget.toString()));
        assertTrue(failure.getMessage().contains(worldTarget.toString()));
        assertFalse(store.files.containsKey(resolution.actualTarget()));
        assertFalse(store.files.containsKey(
                paths(resolution.actualTarget()).backup()));
    }

    @Test
    void laterJournalIdentityIsGuardedAsItsOwnGlobalOperation()
            throws IOException {
        Path globalParent = ROOT.resolve("config");
        Path worldParent = ROOT.resolve("world/serverconfig");
        Path worldTarget =
                worldParent.resolve(ActualTargetResolver.SERVER_BASENAME);
        AtomicBoolean worldAppeared = new AtomicBoolean();
        ActualTargetResolver resolver = new ActualTargetResolver(path -> {
            if (path.equals(worldTarget) && worldAppeared.get()) {
                return MigrationPathState.Observation.fromState(
                        MigrationPathState.PRESENT);
            }
            return MigrationPathState.Observation.fromState(
                    MigrationPathState.ABSENT);
        });
        ActualTargetResolver.Resolution resolution =
                resolver.resolveServer(globalParent, worldParent);
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        store.beforeIdentityReturn = () -> worldAppeared.set(true);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(
                        request(
                                MigrationTarget.SERVER,
                                resolution.actualTarget(),
                                resolution.worldGuard()),
                        store));

        Path journal = paths(resolution.actualTarget()).journal();
        assertTrue(store.events.contains("identity:" + journal));
        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("world-absence-guard", failure.operation());
        assertTrue(failure.reason().contains(worldTarget.toString()));
        assertTrue(failure.reason().contains(
                "NOFOLLOW identity " + journal.getFileName()));
        assertEquals(
                MigrationTargetState.Phase.PREPARED,
                MigrationJournal.decode(store.files.get(journal)).phase());
    }

    @Test
    void markerPublishesOnlyAfterCanonicalTargetValidation()
            throws IOException {
        for (MigrationFaultInjector.Operation operation : List.of(
                MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                MigrationFaultInjector.Operation.SCHEMA_SHA_VALIDATION)) {
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                MigrationEngineTestStore store =
                        new MigrationEngineTestStore();
                store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
                AtomicBoolean fired = new AtomicBoolean();
                MigrationFaultInjector injector = point -> {
                    if (point.artifact()
                                    == AtomicConfigPublisher.Artifact.TARGET
                            && point.operation() == operation
                            && point.timing() == timing
                            && fired.compareAndSet(false, true)) {
                        throw new IllegalStateException(
                                "synthetic canonical target validation");
                    }
                };

                MigrationFailure failure = assertThrows(
                        MigrationFailure.class,
                        () -> engine(injector).migrate(request(SERVER), store));

                assertTrue(fired.get());
                assertTrue(failure.synthetic());
                assertTrue(store.files.containsKey(SERVER));
                assertFalse(store.files.containsKey(paths(SERVER).marker()));
                assertFalse(store.events.stream()
                        .anyMatch(event -> event.startsWith(
                                "publish:MARKER:")));
            }
        }
    }

    @Test
    void completeAlwaysRevalidatesCanonicalTarget() throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        assertEquals(
                MigrationTargetState.Outcome.MIGRATED,
                engine().migrate(request(SERVER), store).outcome());
        store.put(
                LEGACY,
                "corrupt legacy after completion"
                        .getBytes(StandardCharsets.UTF_8));
        store.put(SERVER, "corrupt target".getBytes(StandardCharsets.UTF_8));
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request(SERVER), store));

        assertEquals(MigrationTargetState.Phase.COMPLETE, failure.phase());
        assertEquals("complete-target-validation", failure.operation());
        assertTrue(store.events.contains("read:TARGET:" + SERVER));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.equals(
                        "publish:TARGET:" + SERVER)));
    }

    @Test
    void preferencesCompleteAcceptsValidEditAndRejectsCorrectionUnstableEdit()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request request =
                request(MigrationTarget.PREFERENCES, PREFERENCES);
        assertEquals(
                MigrationTargetState.Outcome.MIGRATED,
                engine().migrate(request, store).outcome());
        byte[] validEdit = new String(
                        store.files.get(PREFERENCES),
                        StandardCharsets.UTF_8)
                .replace(
                        "herobrineHeartbeatNearDistance = 11",
                        "herobrineHeartbeatNearDistance = 12")
                .getBytes(StandardCharsets.UTF_8);
        store.put(PREFERENCES, validEdit);
        store.events.clear();
        int movesBefore = store.atomicMoves.size();

        assertEquals(
                MigrationTargetState.Outcome.COMPLETE,
                engine().migrate(request, store).outcome());
        assertArrayEquals(validEdit, store.files.get(PREFERENCES));
        assertEquals(movesBefore, store.atomicMoves.size());
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));

        byte[] unstable = new String(
                        validEdit, StandardCharsets.UTF_8)
                .replace(
                        "herobrineHeartbeatEnabled = false",
                        "herobrineHeartbeatEnabled = \"false\"")
                .getBytes(StandardCharsets.UTF_8);
        store.put(PREFERENCES, unstable);
        store.events.clear();

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(request, store));

        assertEquals(MigrationTargetState.Phase.COMPLETE, failure.phase());
        assertEquals("complete-target-validation", failure.operation());
        assertArrayEquals(unstable, store.files.get(PREFERENCES));
        assertFalse(store.events.contains("read:LEGACY:" + LEGACY));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:")));
    }

    @Test
    void markerAndPermanentLockBindCommitProfileWithoutMarkerSelfCertification()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request base = request(SERVER);
        ConfigMigrationEngine.Request strongRequest =
                new ConfigMigrationEngine.Request(
                        base.targetKind(),
                        base.legacy(),
                        base.actualTarget(),
                        base.binding(),
                        base.profile(),
                        base.worldGuard(),
                        true);

        MigrationTargetState result =
                engine().migrate(strongRequest, store);
        MigrationMarker marker = MigrationMarker.decode(
                store.files.get(paths(SERVER).marker()));
        String lock = new String(
                store.files.get(paths(SERVER).lock()),
                StandardCharsets.UTF_8);

        assertEquals(
                MigrationEvidence.Durability.STRONG,
                result.commitProfile());
        assertEquals(
                MigrationEvidence.Durability.STRONG,
                marker.evidence().commitProfile());
        assertTrue(lock.contains("durabilityProfile=STRONG\n"));
        assertFalse(marker.evidence()
                .artifactDurability()
                .containsKey("marker"));
    }

    @Test
    void completeStrongMarkerRetriesDirectoryDurabilityWithoutRepublish()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request base = request(SERVER);
        ConfigMigrationEngine.Request strongRequest =
                new ConfigMigrationEngine.Request(
                        base.targetKind(),
                        base.legacy(),
                        base.actualTarget(),
                        base.binding(),
                        base.profile(),
                        base.worldGuard(),
                        true);
        engine().migrate(strongRequest, store);
        byte[] targetBefore = store.files.get(SERVER).clone();
        byte[] markerBefore =
                store.files.get(paths(SERVER).marker()).clone();
        store.events.clear();

        MigrationTargetState result =
                engine().migrate(strongRequest, store);

        assertEquals(MigrationTargetState.Outcome.COMPLETE, result.outcome());
        assertTrue(store.events.contains("resume:MARKER:"
                + paths(SERVER).marker()));
        assertTrue(store.events.contains("force-directory:MARKER"));
        assertFalse(store.events.stream()
                .anyMatch(event -> event.startsWith("publish:MARKER:")));
        assertArrayEquals(targetBefore, store.files.get(SERVER));
        assertArrayEquals(
                markerBefore, store.files.get(paths(SERVER).marker()));
    }

    @Test
    void basicProfileCannotClaimStrongCommitDurability() {
        ConfigMigrationEngine.Request base = request(SERVER);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ConfigMigrationEngine.Request(
                        base.targetKind(),
                        base.legacy(),
                        base.actualTarget(),
                        base.binding(),
                        MigrationAccessProfile.BASIC,
                        base.worldGuard(),
                        true));
    }

    @Test
    void strongJournalDirectoryFaultResumesDurabilityWithoutRepublish()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request base = request(SERVER);
        ConfigMigrationEngine.Request strongRequest =
                new ConfigMigrationEngine.Request(
                        base.targetKind(),
                        base.legacy(),
                        base.actualTarget(),
                        base.binding(),
                        base.profile(),
                        base.worldGuard(),
                        true);
        AtomicBoolean faulted = new AtomicBoolean();
        MigrationFaultInjector fault = point -> {
            if (point.artifact() == AtomicConfigPublisher.Artifact.JOURNAL
                    && point.operation()
                            == MigrationFaultInjector.Operation
                                    .DIRECTORY_DURABILITY
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE
                    && faulted.compareAndSet(false, true)) {
                throw new IllegalStateException(
                        "synthetic journal directory durability");
            }
        };

        MigrationFailure first = assertThrows(
                MigrationFailure.class,
                () -> engine(fault).migrate(strongRequest, store));
        assertTrue(first.synthetic());
        assertTrue(store.files.containsKey(paths(SERVER).journal()));
        assertFalse(store.files.containsKey(paths(SERVER).backup()));
        store.events.clear();

        MigrationTargetState recovered =
                engine().migrate(strongRequest, store);

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertTrue(store.events.contains("resume:JOURNAL:"
                + paths(SERVER).journal()));
        assertTrue(store.events.contains("force-directory:JOURNAL"));
        int resumedGeneration = store.events.indexOf(
                "resume:JOURNAL:" + paths(SERVER).journal());
        int nextGeneration = indexStartingWith(
                store.events, "publish:JOURNAL:");
        assertTrue(nextGeneration < 0 || nextGeneration > resumedGeneration);
    }

    @Test
    void strongEvidenceCannotDowngradeAnyCommittedArtifactToBasic()
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request base = request(SERVER);
        ConfigMigrationEngine.Request strongRequest =
                new ConfigMigrationEngine.Request(
                        base.targetKind(),
                        base.legacy(),
                        base.actualTarget(),
                        base.binding(),
                        base.profile(),
                        base.worldGuard(),
                        true);
        engine().migrate(strongRequest, store);
        Path journalPath = paths(SERVER).journal();
        MigrationJournal journal =
                MigrationJournal.decode(store.files.get(journalPath));
        MigrationEvidence evidence = journal.evidence();
        Map<String, MigrationEvidence.Durability> downgraded =
                new java.util.LinkedHashMap<>(
                        evidence.artifactDurability());
        downgraded.put("lock", MigrationEvidence.Durability.BASIC);
        MigrationEvidence tampered = new MigrationEvidence(
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
                evidence.artifactHashes(),
                downgraded);
        store.files.put(
                journalPath,
                new MigrationJournal(journal.generation(), tampered).encode());
        store.remove(paths(SERVER).marker());

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine().migrate(strongRequest, store));

        assertTrue(failure.reason().contains("artifact durability"));
        assertFalse(store.files.containsKey(paths(SERVER).marker()));
    }

    private static ConfigMigrationEngine engine() {
        return engine(MigrationFaultInjector.none());
    }

    private static ConfigMigrationEngine engine(
            MigrationFaultInjector faults) {
        return new ConfigMigrationEngine(ConfigSchemaCatalog.load(), faults);
    }

    static ConfigMigrationEngine.Request request(Path target) {
        return request(MigrationTarget.SERVER, target);
    }

    static ConfigMigrationEngine.Request request(
            MigrationTarget kind, Path target) {
        return request(kind, target, Optional.empty());
    }

    static ConfigMigrationEngine.Request request(
            MigrationTarget kind,
            Path target,
            Optional<ActualTargetResolver.WorldAbsenceGuard> worldGuard) {
        Path parent = target.getParent();
        MigrationBinding binding = new MigrationBinding(
                target,
                parent,
                parent,
                List.of(new MigrationBinding.Ancestor(
                        parent, "parent:" + parent)),
                "directory:" + parent,
                "file:test-provider",
                "test-store",
                25,
                "Linux");
        return new ConfigMigrationEngine.Request(
                kind,
                LEGACY,
                target,
                binding,
                MigrationAccessProfile.SECURE,
                worldGuard,
                false);
    }

    static MigrationFileSystem.ArtifactPaths paths(Path target) {
        return MigrationFileSystem.ArtifactPaths.forTarget(target);
    }

    static byte[] canonical(MigrationTarget target) throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        Map<String, Object> projection = ConfigProjection.project(
                target,
                LegacyConfigParser.parse(LegacyConfigParserTest.fixtureBytes()),
                schema);
        return ConfigProjectionCodec.encode(target, projection, schema)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parsed(byte[] bytes) {
        return LegacyConfigParser.parse(bytes).rawValues();
    }

    private static int indexStartingWith(
            List<String> values, String prefix) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }
}

final class MigrationEngineTestStore implements ConfigMigrationEngine.Store {
    final Map<Path, byte[]> files = new HashMap<>();
    final Map<Path, String> identities = new HashMap<>();
    final Map<Path, MigrationPathState> forcedStates = new HashMap<>();
    final Map<Path, MigrationPathState.Observation> forcedObservations =
            new HashMap<>();
    final List<String> events = new ArrayList<>();
    final List<String> atomicMoves = new ArrayList<>();
    byte[] appearTargetDuringNewLock;
    byte[] appearTargetAfterLockInitialization;
    Path appearAfterObservedAbsentPath;
    int appearAfterObservedAbsentOccurrence = -1;
    byte[] appearAfterObservedAbsentBytes;
    int observedAbsentOccurrences;
    Path lockPathToSwapDuringBinding;
    AtomicConfigPublisher.Artifact failCommittedArtifact;
    Runnable beforeIdentityReturn = () -> {};
    Runnable beforeEmptyLockRecoveryGate = () -> {};
    private int identitySequence;

    void put(Path path, byte[] bytes) {
        files.put(path, bytes.clone());
        identities.computeIfAbsent(path, ignored -> nextIdentity(path));
    }

    void remove(Path path) {
        files.remove(path);
        identities.remove(path);
    }

    @Override
    public MigrationPathState state(Path path) {
        events.add("state:" + path);
        MigrationPathState forced = forcedStates.get(path);
        if (forced != null) {
            return forced;
        }
        return files.containsKey(path)
                ? MigrationPathState.PRESENT
                : MigrationPathState.ABSENT;
    }

    @Override
    public MigrationPathState.Observation observe(Path path) {
        MigrationPathState.Observation forced = forcedObservations.get(path);
        if (forced != null) {
            events.add("state:" + path);
            return forced;
        }
        MigrationPathState.Observation observation =
                MigrationPathState.Observation.fromState(state(path));
        if (path.equals(appearAfterObservedAbsentPath)
                && observation.state() == MigrationPathState.ABSENT
                && ++observedAbsentOccurrences
                        == appearAfterObservedAbsentOccurrence) {
            put(path, appearAfterObservedAbsentBytes);
        }
        return observation;
    }

    @Override
    public byte[] read(
            Path path, MigrationDirectorySession.ContentKind kind) {
        events.add("read:" + kind + ":" + path);
        byte[] bytes = files.get(path);
        if (bytes == null) {
            throw new IllegalStateException("missing test file " + path);
        }
        return bytes.clone();
    }

    @Override
    public ConfigMigrationEngine.LockLease acquirePermanentLock(
            ConfigMigrationEngine.LockRequest request) {
        Path lock = request.lock();
        boolean created = !files.containsKey(lock);
        events.add("lock:" + (created ? "create" : "open") + ":" + lock);
        if (created) {
            put(lock, new byte[0]);
            if (appearTargetDuringNewLock != null) {
                put(request.target(), appearTargetDuringNewLock);
            }
        }
        boolean targetAbsent = !files.containsKey(request.target());
        if (request.requireTargetAbsent() && !targetAbsent) {
            return new ConfigMigrationEngine.LockLease(
                    identities.get(lock),
                    sha256(files.get(lock)),
                    MigrationEvidence.Durability.BASIC,
                    created,
                    false,
                    request.profile(),
                    false);
        }
        byte[] initializedPayload =
                PermanentMigrationLock.payloadWithIdentity(
                        request.payload(), identities.get(lock));
        boolean recoveredEmptyFirstCreation = false;
        if (created) {
            files.put(lock, initializedPayload);
        } else if (files.get(lock).length == 0
                && request.allowEmptyFirstCreationRecovery()) {
            beforeEmptyLockRecoveryGate.run();
            if (!hasEmptyFirstCreationPortrait(request)) {
                throw new IllegalStateException(
                        "empty first-creation lock portrait changed");
            }
            files.put(lock, initializedPayload);
            events.add("lock:initialize-empty:" + lock);
            recoveredEmptyFirstCreation = true;
        } else if (!Arrays.equals(files.get(lock), initializedPayload)) {
            throw new IllegalStateException("untrusted permanent lock payload");
        }
        if (created && appearTargetAfterLockInitialization != null) {
            put(request.target(), appearTargetAfterLockInitialization);
            targetAbsent = false;
        }
        return new ConfigMigrationEngine.LockLease(
                identities.get(lock),
                sha256(files.get(lock)),
                request.strongRequired()
                        ? MigrationEvidence.Durability.STRONG
                        : MigrationEvidence.Durability.BASIC,
                created,
                targetAbsent,
                request.profile(),
                recoveredEmptyFirstCreation);
    }

    private boolean hasEmptyFirstCreationPortrait(
            ConfigMigrationEngine.LockRequest request) {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(
                        request.target());
        if (!files.containsKey(request.legacy())) {
            throw new IllegalStateException(
                    "empty-lock recovery legacy is absent "
                            + request.legacy());
        }
        for (Path requiredAbsent : java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                artifacts.target(),
                                artifacts.journal(),
                                artifacts.backup(),
                                artifacts.initial(),
                                artifacts.marker()),
                        artifacts.fixedStages().stream())
                .toList()) {
            if (files.containsKey(requiredAbsent)) {
                throw new IllegalStateException(
                        "empty-lock recovery artifact is present "
                                + requiredAbsent);
            }
        }
        return true;
    }

    @Override
    public AtomicConfigPublisher.Port publicationPort(
            ConfigMigrationEngine.PublishRequest request) {
        return new PublicationPort(request);
    }

    @Override
    public String identity(Path path) {
        events.add("identity:" + path);
        String identity = identities.get(path);
        if (identity == null) {
            throw new IllegalStateException("missing identity " + path);
        }
        beforeIdentityReturn.run();
        return identity;
    }

    @Override
    public void verifyPermanentLock(
            Path path, ConfigMigrationEngine.LockLease lease) {
        events.add("lock:verify:" + path);
        if (!lease.identity().equals(identities.get(path))) {
            throw new IllegalStateException(
                    "permanent lock identity changed");
        }
        byte[] payload = files.get(path);
        if (payload == null
                || !lease.payloadSha256().equals(sha256(payload))) {
            throw new IllegalStateException(
                    "permanent lock payload changed");
        }
    }

    @Override
    public void verifyBinding(MigrationBinding binding) {
        events.add("binding:" + binding.target());
        if (lockPathToSwapDuringBinding != null
                && files.containsKey(lockPathToSwapDuringBinding)) {
            identities.put(
                    lockPathToSwapDuringBinding,
                    nextIdentity(lockPathToSwapDuringBinding));
            lockPathToSwapDuringBinding = null;
        }
    }

    private void verifyExpectation(ConfigMigrationEngine.PublishRequest request) {
        AtomicConfigPublisher.DestinationExpectation expectation =
                request.expectation();
        byte[] current = files.get(request.destination());
        if (expectation.state() == AtomicConfigPublisher.ExpectedState.ABSENT) {
            if (current != null) {
                throw new IllegalStateException(
                        "observable destination conflict "
                                + request.destination());
            }
            return;
        }
        if (current == null
                || !Arrays.equals(current, expectation.priorBytes())
                || !identity(request.destination())
                        .equals(expectation.priorIdentity())) {
            throw new IllegalStateException(
                    "prior journal generation changed "
                            + request.destination());
        }
    }

    private String nextIdentity(Path path) {
        identitySequence++;
        return "inode:" + identitySequence + ":" + path;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private final class PublicationPort
            implements AtomicConfigPublisher.Port {
        private final ConfigMigrationEngine.PublishRequest request;

        private PublicationPort(
                ConfigMigrationEngine.PublishRequest request) {
            this.request = request;
        }

        @Override
        public void createNew(String stage) throws IOException {
            events.add(
                    "publish:"
                            + request.artifact()
                            + ":"
                            + request.destination());
            if (files.containsKey(request.stage())) {
                throw new IOException(
                        "fixed stage already exists " + request.stage());
            }
            put(request.stage(), new byte[0]);
        }

        @Override
        public void write(String stage, byte[] bytes) {
            files.put(request.stage(), bytes.clone());
        }

        @Override
        public void forceFile(String stage) {}

        @Override
        public void closeStage(String stage) {}

        @Override
        public void verifyDestination(
                String destination,
                AtomicConfigPublisher.DestinationExpectation expectation) {
            verifyExpectation(request);
        }

        @Override
        public void atomicMove(String stage, String destination)
                throws IOException {
            byte[] bytes = files.remove(request.stage());
            identities.remove(request.stage());
            atomicMoves.add(request.artifact() + ":" + sha256(bytes));
            files.put(request.destination(), bytes);
            identities.put(
                    request.destination(),
                    nextIdentity(request.destination()));
            if (failCommittedArtifact == request.artifact()) {
                failCommittedArtifact = null;
                throw new AtomicConfigPublisher.CommittedMoveException(
                        "synthetic committed-move fault "
                                + request.artifact());
            }
        }

        @Override
        public byte[] reopenNofollow(String destination)
                throws IOException {
            MigrationDirectorySession.ContentKind kind = switch (
                    request.artifact()) {
                case JOURNAL ->
                    MigrationDirectorySession.ContentKind.JOURNAL;
                case BACKUP ->
                    MigrationDirectorySession.ContentKind.BACKUP;
                case INITIAL ->
                    MigrationDirectorySession.ContentKind.INITIAL;
                case TARGET ->
                    MigrationDirectorySession.ContentKind.TARGET;
                case MARKER ->
                    MigrationDirectorySession.ContentKind.MARKER;
            };
            return read(request.destination(), kind);
        }

        @Override
        public void validate(String destination, byte[] expected)
                throws IOException {
            if (!Arrays.equals(files.get(request.destination()), expected)) {
                throw new IOException("canonical bytes differ");
            }
        }

        @Override
        public void forceDirectory() {
            events.add("force-directory:" + request.artifact());
        }

        @Override
        public boolean stageExists(String stage) {
            events.add(
                    "resume:"
                            + request.artifact()
                            + ":"
                            + request.destination());
            return files.containsKey(request.stage());
        }

        @Override
        public boolean canonicalMatches(
                String destination, byte[] expected) {
            return Arrays.equals(
                    files.get(request.destination()), expected);
        }
    }
}
