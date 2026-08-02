package dev.molang.iamzombieq.config;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class ConfigMigrationEngine {
    private static final String RECOVERY = MigrationFailure.OPERATOR_RECOVERY;

    private final ConfigSchemaCatalog schema;
    private final TargetConfigValidator validator;
    private final MigrationFaultInjector faults;

    ConfigMigrationEngine(
            ConfigSchemaCatalog schema, MigrationFaultInjector faults) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.validator = new TargetConfigValidator(schema);
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    MigrationTargetState migrate(Request request, Store store) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(store, "store");
        Execution execution = new Execution(request, store);
        try {
            return execution.run();
        } catch (MigrationFailure failure) {
            throw failure;
        } catch (MigrationFaultInjector.SyntheticFault failure) {
            throw execution.syntheticFailure(failure);
        } catch (RuntimeException failure) {
            throw execution.failure(
                    "migration-core",
                    "engine-execution",
                    reason(failure),
                    false,
                    failure);
        }
    }

    MigrationTargetState migrateApplicable(
            MigrationMetadataBootstrap.Result bootstrap,
            Request request,
            Store store) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(store, "store");
        if (bootstrap.kind()
                != MigrationMetadataBootstrap.Kind.REQUIRES_SESSION) {
            throw new IllegalArgumentException(
                    "Fresh bootstrap results must not open a migration session");
        }
        MigrationMetadataBootstrap.Candidates candidates =
                bootstrap.candidates();
        if (!candidates.applicableTargets().contains(request.targetKind())) {
            throw new IllegalArgumentException(
                    "Requested migration target is not applicable: "
                            + request.targetKind());
        }
        if (!candidates.legacy().equals(request.legacy())) {
            throw new IllegalArgumentException(
                    "Migration request legacy path differs from bootstrap");
        }
        if (!bootstrap.operationalTarget().equals(request.actualTarget())) {
            throw new IllegalArgumentException(
                    "Migration request actual target differs from bootstrap");
        }
        Path actualTarget = request.actualTarget();
        if (!actualTarget.equals(
                        candidates.owningTargets().get(actualTarget))
                || !expectedBasename(request.targetKind())
                        .equals(actualTarget.getFileName().toString())) {
            throw new IllegalArgumentException(
                    "Migration request target is outside its fixed applicable "
                            + "candidate set");
        }
        return migrate(request, store);
    }

    private static String expectedBasename(MigrationTarget target) {
        return switch (target) {
            case SERVER -> ActualTargetResolver.SERVER_BASENAME;
            case PREFERENCES -> ActualTargetResolver.PREFERENCES_BASENAME;
        };
    }

    private final class Execution {
        private final Request request;
        private final Store store;
        private final MigrationFileSystem.ArtifactPaths paths;
        private MigrationTargetState.Phase phase =
                MigrationTargetState.Phase.NO_EVIDENCE;
        private LockLease activeLease;

        private Execution(Request request, Store store) {
            this.request = request;
            this.store = store;
            this.paths = MigrationFileSystem.ArtifactPaths.forTarget(
                    request.actualTarget());
        }

        private MigrationTargetState run() {
            rejectOrphanStages();

            Snapshot snapshot = classifySnapshot();
            if (snapshot.marker() == MigrationPathState.PRESENT
                    && snapshot.lock() != MigrationPathState.PRESENT) {
                throw failure(
                        "marker",
                        "evidence-consistency",
                        "marker exists without its permanent lock",
                        false,
                        null);
            }
            if (snapshot.hasEvidence()
                    && snapshot.lock() != MigrationPathState.PRESENT) {
                throw failure(
                        "evidence",
                        "evidence-consistency",
                        "migration evidence exists without its permanent lock",
                        false,
                        null);
            }

            if (!snapshot.hasEvidence()) {
                if (snapshot.target() == MigrationPathState.PRESENT) {
                    validateExistingTarget();
                    beforeSuccessfulReturn();
                    return state(
                            MigrationTargetState.Outcome.EXISTING_VALID,
                            MigrationTargetState.Phase.NO_EVIDENCE,
                            "",
                            Map.of());
                }
                if (stateOf(request.legacy()) == MigrationPathState.ABSENT) {
                    guardWorld();
                    return state(
                            MigrationTargetState.Outcome.FRESH,
                            MigrationTargetState.Phase.NO_EVIDENCE,
                            "",
                            Map.of());
                }
                return startMigration();
            }
            boolean allowEmptyFirstCreationRecovery =
                    snapshot.onlyPermanentLock()
                            && stateOf(request.legacy())
                                    == MigrationPathState.PRESENT;
            return recover(snapshot, allowEmptyFirstCreationRecovery);
        }

        private MigrationTargetState startMigration() {
            phase = MigrationTargetState.Phase.LOCKED;
            byte[] lockPayload = lockPayload(request);
            LockLease lease = acquireLock(lockPayload, true, false);
            if (!lease.targetAbsentUnderLock()) {
                throw failure(
                        "permanent-lock",
                        "under-lock-target-check",
                        "target appeared after permanent lock creation",
                        false,
                        null);
            }
            return startFromAcquiredFreshLock(lease);
        }

        private MigrationTargetState startFromAcquiredFreshLock(
                LockLease lease) {
            Prepared prepared = prepareFromLegacy();
            MigrationEvidence evidence = evidence(
                    lease,
                    MigrationTargetState.Phase.PREPARED,
                    prepared,
                    Map.of("lock", lease.payloadSha256()),
                    Map.of("lock", lease.durability()));
            MigrationJournal journal = new MigrationJournal(1, evidence);
            publishJournal(null, journal);
            phase = MigrationTargetState.Phase.PREPARED;
            return continueFromJournal(
                    journal,
                    lease,
                    prepared,
                    false,
                    ResumePermissions.none());
        }

        private MigrationTargetState recover(
                Snapshot snapshot,
                boolean allowEmptyFirstCreationRecovery) {
            phase = MigrationTargetState.Phase.LOCKED;
            byte[] lockPayload = lockPayload(request);
            LockLease lease = acquireLock(
                    lockPayload,
                    false,
                    allowEmptyFirstCreationRecovery);
            if (lease.recoveredEmptyFirstCreation()) {
                rejectOrphanStages();
                Snapshot recoveredSnapshot = classifySnapshot();
                if (!lease.targetAbsentUnderLock()
                        || !recoveredSnapshot.onlyPermanentLock()
                        || stateOf(request.legacy())
                                != MigrationPathState.PRESENT) {
                    throw failure(
                            "permanent-lock",
                            "empty-lock-recovery-gate",
                            "empty first-creation lock recovery no longer "
                                    + "has its exact artifact-free portrait",
                            false,
                            null);
                }
                return startFromAcquiredFreshLock(lease);
            }
            Snapshot lockedSnapshot = classifySnapshot();

            if (lockedSnapshot.journal() == MigrationPathState.ABSENT) {
                if (lockedSnapshot.target() == MigrationPathState.PRESENT
                        || lockedSnapshot.backup()
                                == MigrationPathState.PRESENT
                        || lockedSnapshot.initial()
                                == MigrationPathState.PRESENT
                        || lockedSnapshot.marker()
                                == MigrationPathState.PRESENT) {
                    throw failure(
                            "permanent-lock",
                            "evidence-consistency",
                            "lock without journal conflicts with another artifact",
                            false,
                            null);
                }
                if (request.strongDurability()
                        && !lease.createdNow()
                        && lease.durability()
                                != MigrationEvidence.Durability.STRONG) {
                    throw failure(
                            "permanent-lock",
                            "lock-durability-recovery",
                            "STRONG permanent lock durability is unproven "
                                    + "without a bound journal",
                            false,
                            null);
                }
                throw failure(
                        "permanent-lock",
                        "locked-recovery",
                        "an initialized permanent lock has no journal; "
                                + "manual recovery is required because this "
                                + "state may record an observed target conflict",
                        false,
                        null);
            }

            MigrationJournal journal = readJournal();
            phase = journal.phase();
            verifyEvidence(journal.evidence(), lease);
            if (lockedSnapshot.marker() == MigrationPathState.PRESENT
                    && journal.phase() != MigrationTargetState.Phase.COMPLETE) {
                throw failure(
                        "marker",
                        "evidence-consistency",
                        "marker exists before the journal reached COMPLETE",
                        false,
                        null);
            }
            verifyPhaseArtifactPresence(
                    journal.phase(), lockedSnapshot);
            ensureJournalDurability(journal);
            ResumePermissions resumePermissions =
                    ResumePermissions.from(
                            journal.phase(), lockedSnapshot);

            Prepared prepared = switch (journal.phase()) {
                case PREPARED -> {
                    if (lockedSnapshot.backup()
                            == MigrationPathState.PRESENT) {
                        yield prepareFromBackup(journal.evidence());
                    }
                    Prepared preparedFromLegacy = prepareFromLegacy();
                    requirePreparedMatches(
                            journal.evidence(), preparedFromLegacy);
                    yield preparedFromLegacy;
                }
                case BACKUP_PUBLISHED,
                        INITIAL_PUBLISHED,
                        TARGET_PUBLISHED,
                        COMPLETE -> prepareFromBackup(journal.evidence());
                case NO_EVIDENCE, LOCKED -> throw failure(
                        "journal",
                        "journal-phase",
                        "journal contains a transient phase",
                        false,
                        null);
            };
            return continueFromJournal(
                    journal,
                    lease,
                    prepared,
                    true,
                    resumePermissions);
        }

        private void verifyPhaseArtifactPresence(
                MigrationTargetState.Phase journalPhase,
                Snapshot lockedSnapshot) {
            switch (journalPhase) {
                case PREPARED -> {
                    rejectPresentBeforePhase(
                            paths.initial(),
                            lockedSnapshot.initial(),
                            MigrationTargetState.Phase.BACKUP_PUBLISHED);
                    rejectPresentBeforePhase(
                            paths.target(),
                            lockedSnapshot.target(),
                            MigrationTargetState.Phase.INITIAL_PUBLISHED);
                }
                case BACKUP_PUBLISHED -> rejectPresentBeforePhase(
                        paths.target(),
                        lockedSnapshot.target(),
                        MigrationTargetState.Phase.INITIAL_PUBLISHED);
                case INITIAL_PUBLISHED,
                        TARGET_PUBLISHED,
                        COMPLETE -> {
                    // Presence is validated against journal-bound evidence below.
                }
                case NO_EVIDENCE, LOCKED -> throw failure(
                        "journal",
                        "journal-phase",
                        "journal contains a transient phase",
                        false,
                        null);
            }
        }

        private void rejectPresentBeforePhase(
                Path artifact,
                MigrationPathState state,
                MigrationTargetState.Phase firstPermittedPhase) {
            if (state == MigrationPathState.PRESENT) {
                String artifactName = artifact.equals(paths.target())
                        ? "target"
                        : artifact.equals(paths.initial())
                                ? "initial"
                                : artifact.getFileName().toString();
                throw failure(
                        artifactName,
                        "artifact-phase-consistency",
                        artifactName
                                + " artifact "
                                + artifact.getFileName()
                                + " is present before journal phase "
                                + firstPermittedPhase
                                + " permits its publish-before-advance "
                                + "recovery window",
                        false,
                        null);
            }
        }

        private MigrationTargetState continueFromJournal(
                MigrationJournal startingJournal,
                LockLease lease,
                Prepared prepared,
                boolean recoveryCall,
                ResumePermissions resumePermissions) {
            MigrationJournal journal = startingJournal;

            if (journal.phase() == MigrationTargetState.Phase.PREPARED) {
                AtomicConfigPublisher.Publication backup = ensureArtifact(
                        AtomicConfigPublisher.Artifact.BACKUP,
                        paths.fixedStages().get(1),
                        paths.backup(),
                        prepared.rawLegacy(),
                        stateOf(paths.backup()),
                        MigrationDirectorySession.ContentKind.BACKUP,
                        resumePermissions.backup());
                validateBackup(paths.backup(), prepared);
                journal = advance(
                        journal,
                        lease,
                        prepared,
                        MigrationTargetState.Phase.BACKUP_PUBLISHED,
                        "backup",
                        sha256(prepared.rawLegacy()),
                        backup.durability());
            }

            if (journal.phase()
                    == MigrationTargetState.Phase.BACKUP_PUBLISHED) {
                prepared = prepareFromBackup(journal.evidence());
                AtomicConfigPublisher.Publication initial = ensureArtifact(
                        AtomicConfigPublisher.Artifact.INITIAL,
                        paths.fixedStages().get(2),
                        paths.initial(),
                        prepared.canonical(),
                        stateOf(paths.initial()),
                        MigrationDirectorySession.ContentKind.INITIAL,
                        resumePermissions.initial());
                validateCanonical(
                        paths.initial(),
                        MigrationDirectorySession.ContentKind.INITIAL,
                        prepared.projectionSha256(),
                        "initial-validation");
                journal = advance(
                        journal,
                        lease,
                        prepared,
                        MigrationTargetState.Phase.INITIAL_PUBLISHED,
                        "initial",
                        sha256(prepared.canonical()),
                        initial.durability());
            }

            if (journal.phase()
                    == MigrationTargetState.Phase.INITIAL_PUBLISHED) {
                prepared = prepareFromInitial(journal.evidence(), prepared);
                MigrationPathState targetState = stateOf(paths.target());
                AtomicConfigPublisher.Publication target = ensureArtifact(
                        AtomicConfigPublisher.Artifact.TARGET,
                        paths.fixedStages().get(3),
                        paths.target(),
                        prepared.canonical(),
                        targetState,
                        MigrationDirectorySession.ContentKind.TARGET,
                        resumePermissions.target());
                validateCanonical(
                        paths.target(),
                        MigrationDirectorySession.ContentKind.TARGET,
                        prepared.projectionSha256(),
                        "canonical-target-validation");
                journal = advance(
                        journal,
                        lease,
                        prepared,
                        MigrationTargetState.Phase.TARGET_PUBLISHED,
                        "target",
                        sha256(prepared.canonical()),
                        target.durability());
            }

            if (journal.phase()
                    == MigrationTargetState.Phase.TARGET_PUBLISHED
                    || journal.phase()
                            == MigrationTargetState.Phase.COMPLETE) {
                prepared = prepareFromInitial(
                        journal.evidence(), prepared);
            }

            if (journal.phase()
                    == MigrationTargetState.Phase.TARGET_PUBLISHED) {
                validateCanonicalEvidence(
                        paths.target(),
                        MigrationDirectorySession.ContentKind.TARGET,
                        journal.evidence(),
                        "target",
                        "canonical-target-validation");
                journal = advance(
                        journal,
                        lease,
                        prepared,
                        MigrationTargetState.Phase.COMPLETE,
                        null,
                        null,
                        null);
            }

            if (journal.phase() != MigrationTargetState.Phase.COMPLETE) {
                throw failure(
                        "journal",
                        "journal-phase",
                        "state machine stopped at an unsupported phase",
                        false,
                        null);
            }

            phase = MigrationTargetState.Phase.COMPLETE;
            verifyEvidence(journal.evidence(), lease);
            if (resumePermissions.marker()) {
                validateCompleteTarget(
                        paths.target(),
                        MigrationDirectorySession.ContentKind.TARGET,
                        "complete-target-validation");
            } else {
                validateCanonicalEvidence(
                        paths.target(),
                        MigrationDirectorySession.ContentKind.TARGET,
                        journal.evidence(),
                        "target",
                        "canonical-target-validation");
            }
            ensureMarker(journal, resumePermissions.marker());
            beforeSuccessfulReturn();
            return state(
                    recoveryCall
                            ? MigrationTargetState.Outcome.COMPLETE
                            : MigrationTargetState.Outcome.MIGRATED,
                    MigrationTargetState.Phase.COMPLETE,
                    journal.evidence().projectionSha256(),
                    journal.evidence().artifactDurability());
        }

        private Snapshot classifySnapshot() {
            MigrationPathState target = stateOf(paths.target());
            MigrationPathState lock = stateOf(paths.lock());
            MigrationPathState journal = stateOf(paths.journal());
            MigrationPathState backup = stateOf(paths.backup());
            MigrationPathState initial = stateOf(paths.initial());
            MigrationPathState marker = stateOf(paths.marker());
            return new Snapshot(
                    target, lock, journal, backup, initial, marker);
        }

        private void rejectOrphanStages() {
            for (Path stage : paths.fixedStages()) {
                MigrationPathState state = stateOf(stage);
                if (state == MigrationPathState.PRESENT) {
                    throw failureWithRecovery(
                            stage.getFileName().toString(),
                            "orphan-stage-check",
                            "fixed stage exists and cannot be adopted or deleted",
                            MigrationFailure.orphanStageRecovery(
                                    stage, paths.target()),
                            false,
                            null);
                }
            }
        }

        private MigrationPathState stateOf(Path path) {
            MigrationPathState.Observation observation = withWorldGuard(
                    "NOFOLLOW metadata " + path.getFileName(),
                    () -> {
                        verifyActiveLock();
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation.METADATA,
                                MigrationFaultInjector.Timing.BEFORE);
                        MigrationPathState.Observation observed =
                                store.observe(path);
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation.METADATA,
                                MigrationFaultInjector.Timing.AFTER);
                        verifyActiveLock();
                        return observed;
                    });
            MigrationPathState state =
                    observation == null ? null : observation.state();
            if (state == null
                    || state == MigrationPathState.UNKNOWN
                    || state == MigrationPathState.UNSAFE) {
                throw failure(
                        path.getFileName().toString(),
                        "nofollow-metadata",
                        "path metadata is UNKNOWN or UNSAFE: "
                                + (observation == null
                                        ? "null observation"
                                        : observation.detail()),
                        false,
                        observation == null ? null : observation.cause());
            }
            return state;
        }

        private void validateExistingTarget() {
            byte[] bytes = read(
                    paths.target(),
                    MigrationDirectorySession.ContentKind.EXISTING_TARGET);
            String encoded;
            try {
                encoded = strictUtf8(bytes);
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "target",
                        "existing-target-validation",
                        "malformed existing target encoding",
                        false,
                        failure);
            }
            TargetConfigValidator.Result result =
                    validator.validateEncoded(request.targetKind(), encoded);
            if (!result.valid()) {
                throw failure(
                        "target",
                        "existing-target-validation",
                        "malformed, incomplete, out-of-range, or "
                                + "correction-unstable existing target: "
                                + result.issues(),
                        false,
                        null);
            }
        }

        private Prepared prepareFromLegacy() {
            phase = MigrationTargetState.Phase.PREPARED;
            byte[] raw = read(
                    request.legacy(),
                    MigrationDirectorySession.ContentKind.LEGACY);
            return prepare(raw, "legacy-parse-and-projection");
        }

        private Prepared prepareFromBackup(MigrationEvidence evidence) {
            phase = MigrationTargetState.Phase.valueOf(evidence.phase());
            if (stateOf(paths.backup()) != MigrationPathState.PRESENT) {
                throw failure(
                        "backup",
                        "backup-recovery",
                        "journal phase requires an immutable backup",
                        false,
                        null);
            }
            byte[] raw = read(
                    paths.backup(),
                    MigrationDirectorySession.ContentKind.BACKUP);
            Prepared prepared = prepare(raw, "backup-parse-and-projection");
            requirePreparedMatches(evidence, prepared);
            if (MigrationTargetState.Phase.valueOf(evidence.phase())
                            != MigrationTargetState.Phase.PREPARED
                    || evidence.artifactHashes().containsKey("backup")) {
                requireArtifactHash(evidence, "backup", raw);
            }
            return prepared;
        }

        private Prepared prepareFromInitial(
                MigrationEvidence evidence, Prepared prepared) {
            if (stateOf(paths.initial()) != MigrationPathState.PRESENT) {
                throw failure(
                        "initial",
                        "initial-recovery",
                        "journal phase requires an immutable initial image",
                        false,
                        null);
            }
            byte[] initial = read(
                    paths.initial(),
                    MigrationDirectorySession.ContentKind.INITIAL);
            requireArtifactHash(evidence, "initial", initial);
            if (!Arrays.equals(initial, prepared.canonical())) {
                throw failure(
                        "initial",
                        "initial-recovery",
                        "initial image differs from the bound backup projection",
                        false,
                        null);
            }
            validateTargetBytes(
                    initial,
                    prepared.projectionSha256(),
                    "initial-validation");
            return prepared;
        }

        private Prepared prepare(byte[] raw, String operation) {
            try {
                LegacyConfigParser.Parsed parsed = LegacyConfigParser.parse(raw);
                Map<String, Object> projection = ConfigProjection.project(
                        request.targetKind(), parsed, schema);
                String typedSha = ConfigProjectionCodec.typedSha256(
                        request.targetKind(), projection, schema);
                int separator = typedSha.lastIndexOf(':');
                String projectionSha = typedSha.substring(separator + 1);
                byte[] canonical = ConfigProjectionCodec.encode(
                                request.targetKind(), projection, schema)
                        .getBytes(StandardCharsets.UTF_8);
                validateTargetBytes(
                        canonical, projectionSha, "generated-canonical-validation");
                return new Prepared(
                        raw, sha256(raw), projectionSha, canonical);
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "legacy",
                        operation,
                        "legacy data is malformed or invalid for "
                                + request.targetKind(),
                        false,
                        failure);
            }
        }

        private void requirePreparedMatches(
                MigrationEvidence evidence, Prepared prepared) {
            if (!evidence.rawLegacySha256().equals(prepared.rawSha256())
                    || !evidence.projectionSha256()
                            .equals(prepared.projectionSha256())) {
                throw failure(
                        "legacy",
                        "prepared-recovery",
                        "legacy bytes or applicable projection changed after PREPARED",
                        false,
                        null);
            }
        }

        private void validateBackup(Path backup, Prepared prepared) {
            byte[] bytes =
                    read(backup, MigrationDirectorySession.ContentKind.BACKUP);
            if (!Arrays.equals(bytes, prepared.rawLegacy())
                    || !sha256(bytes).equals(prepared.rawSha256())) {
                throw failure(
                        "backup",
                        "backup-validation",
                        "published backup differs from exact legacy bytes",
                        false,
                        null);
            }
            Prepared reopened = prepare(bytes, "backup-reparse");
            if (!reopened.projectionSha256()
                    .equals(prepared.projectionSha256())) {
                throw failure(
                        "backup",
                        "backup-validation",
                        "backup projection SHA differs after reopen",
                        false,
                        null);
            }
        }

        private void validateCanonical(
                Path path,
                MigrationDirectorySession.ContentKind kind,
                String expectedProjection,
                String operation) {
            if (stateOf(path) != MigrationPathState.PRESENT) {
                throw failure(
                        path.getFileName().toString(),
                        operation,
                        "required canonical target artifact is missing",
                        false,
                        null);
            }
            byte[] bytes = read(path, kind);
            validateTargetBytes(bytes, expectedProjection, operation);
        }

        private void validateCanonicalEvidence(
                Path path,
                MigrationDirectorySession.ContentKind kind,
                MigrationEvidence evidence,
                String artifact,
                String operation) {
            if (stateOf(path) != MigrationPathState.PRESENT) {
                throw failure(
                        path.getFileName().toString(),
                        operation,
                        "required canonical target artifact is missing",
                        false,
                        null);
            }
            byte[] bytes = read(path, kind);
            requireArtifactHash(evidence, artifact, bytes);
            validateTargetBytes(
                    bytes, evidence.projectionSha256(), operation);
        }

        private void validateCompleteTarget(
                Path path,
                MigrationDirectorySession.ContentKind kind,
                String operation) {
            if (stateOf(path) != MigrationPathState.PRESENT) {
                throw failure(
                        path.getFileName().toString(),
                        operation,
                        "required canonical target artifact is missing",
                        false,
                        null);
            }
            validateCurrentTargetBytes(read(path, kind), operation);
        }

        private void validateCurrentTargetBytes(
                byte[] bytes, String operation) {
            try {
                String encoded = strictUtf8(bytes);
                TargetConfigValidator.Result result =
                        validator.validateEncoded(request.targetKind(), encoded);
                if (!result.valid()) {
                    throw new IllegalArgumentException(
                            "schema/correction issues " + result.issues());
                }
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "target",
                        operation,
                        "malformed, incomplete, schema-invalid, or "
                                + "correction-unstable canonical target",
                        false,
                        failure);
            }
        }

        private void validateTargetBytes(
                byte[] bytes, String expectedProjection, String operation) {
            try {
                String encoded = strictUtf8(bytes);
                TargetConfigValidator.Result result =
                        validator.validateEncoded(request.targetKind(), encoded);
                if (!result.valid()) {
                    throw new IllegalArgumentException(
                            "schema/correction issues " + result.issues());
                }
                Map<String, Object> values =
                        LegacyConfigParser.parse(bytes).rawValues();
                String typed = ConfigProjectionCodec.typedSha256(
                        request.targetKind(), values, schema);
                String actual = typed.substring(typed.lastIndexOf(':') + 1);
                if (!actual.equals(expectedProjection)) {
                    throw new IllegalArgumentException(
                            "projection SHA mismatch");
                }
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "target",
                        operation,
                        "malformed, incomplete, schema-invalid, SHA-mismatched, "
                                + "or correction-unstable canonical target",
                        false,
                        failure);
            }
        }

        private MigrationJournal advance(
                MigrationJournal prior,
                LockLease lease,
                Prepared prepared,
                MigrationTargetState.Phase nextPhase,
                String artifact,
                String artifactHash,
                MigrationEvidence.Durability durability) {
            LinkedHashMap<String, String> hashes =
                    new LinkedHashMap<>(prior.evidence().artifactHashes());
            LinkedHashMap<String, MigrationEvidence.Durability> profiles =
                    new LinkedHashMap<>(
                            prior.evidence().artifactDurability());
            if (artifact != null) {
                hashes.put(artifact, artifactHash);
                profiles.put(artifact, durability);
            }
            MigrationEvidence nextEvidence = evidence(
                    lease, nextPhase, prepared, hashes, profiles);
            MigrationJournal next =
                    new MigrationJournal(prior.generation() + 1, nextEvidence);
            publishJournal(prior, next);
            phase = nextPhase;
            return next;
        }

        private void publishJournal(
                MigrationJournal prior, MigrationJournal next) {
            AtomicConfigPublisher.DestinationExpectation expectation =
                    prior == null
                            ? AtomicConfigPublisher.DestinationExpectation.absent()
                            : AtomicConfigPublisher.DestinationExpectation.exactPrior(
                                    prior.encode(),
                                    withWorldGuard(
                                            "NOFOLLOW identity "
                                                    + paths.journal()
                                                            .getFileName(),
                                            () -> {
                                                verifyBinding();
                                                String identity =
                                                        store.identity(paths.journal());
                                                verifyBinding();
                                                return identity;
                                            }));
            publish(new PublishRequest(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    paths.fixedStages().getFirst(),
                    paths.journal(),
                    next.encode(),
                    expectation,
                    request.strongDurability()));
            MigrationJournal reopened = readJournal();
            if (!reopened.equals(next)) {
                throw failure(
                        "journal",
                        "journal-reopen-validation",
                        "reopened journal generation differs from publication",
                        false,
                        null);
            }
        }

        private MigrationJournal readJournal() {
            try {
                return MigrationJournal.decode(read(
                        paths.journal(),
                        MigrationDirectorySession.ContentKind.JOURNAL));
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "journal",
                        "journal-reparse",
                        "journal is malformed, incomplete, or unknown-version",
                        false,
                        failure);
            }
        }

        private void ensureJournalDurability(MigrationJournal journal) {
            if (!request.strongDurability()) {
                return;
            }
            resume(new PublishRequest(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    paths.fixedStages().getFirst(),
                    paths.journal(),
                    journal.encode(),
                    AtomicConfigPublisher.DestinationExpectation.absent(),
                    true));
        }

        private AtomicConfigPublisher.Publication ensureArtifact(
                AtomicConfigPublisher.Artifact artifact,
                Path stage,
                Path destination,
                byte[] bytes,
                MigrationPathState destinationState,
                MigrationDirectorySession.ContentKind contentKind,
                boolean resumeExpected) {
            verifyRecoveryPublicationState(
                    artifact, destinationState, resumeExpected);
            PublishRequest publication = new PublishRequest(
                    artifact,
                    stage,
                    destination,
                    bytes,
                    AtomicConfigPublisher.DestinationExpectation.absent(),
                    request.strongDurability());
            AtomicConfigPublisher.Publication result;
            if (destinationState == MigrationPathState.ABSENT) {
                if (artifact == AtomicConfigPublisher.Artifact.TARGET
                        && stateOf(paths.target())
                                != MigrationPathState.ABSENT) {
                    throw failure(
                            "target",
                            "final-target-presence-check",
                            "target appeared before its sole atomic publication",
                            false,
                            null);
                }
                result = publish(publication);
            } else {
                result = resume(publication);
            }
            byte[] reopened = read(destination, contentKind);
            if (!Arrays.equals(reopened, bytes)) {
                throw failure(
                        artifact.name().toLowerCase(),
                        "canonical-reopen-validation",
                        "committed artifact bytes are not exact",
                        false,
                        null);
            }
            return result;
        }

        private void verifyRecoveryPublicationState(
                AtomicConfigPublisher.Artifact artifact,
                MigrationPathState destinationState,
                boolean resumeExpected) {
            String artifactName =
                    artifact.name().toLowerCase(java.util.Locale.ROOT);
            if (destinationState == MigrationPathState.PRESENT
                    && !resumeExpected) {
                throw failure(
                        artifactName,
                        "recovery-publication-evidence",
                        artifactName
                                + " is present but was absent from the locked "
                                + "recovery snapshot; exact bytes do not prove "
                                + "migration publication provenance",
                        false,
                        null);
            }
            if (destinationState == MigrationPathState.ABSENT
                    && resumeExpected) {
                throw failure(
                        artifactName,
                        "recovery-publication-evidence",
                        artifactName
                                + " was present in the locked recovery "
                                + "snapshot but is now absent",
                        false,
                        null);
            }
        }

        private AtomicConfigPublisher.Publication publish(
                PublishRequest publication) {
            return withWorldGuard(
                    "atomic publication " + publication.artifact(),
                    () -> {
                        verifyBinding();
                        AtomicConfigPublisher.Publication result =
                                new AtomicConfigPublisher(
                                                store.publicationPort(publication),
                                                faults,
                                                phase)
                                        .publish(publication.atomicRequest());
                        verifyBinding();
                        return result;
                    });
        }

        private AtomicConfigPublisher.Publication resume(
                PublishRequest publication) {
            return withWorldGuard(
                    "publication recovery " + publication.artifact(),
                    () -> {
                        verifyBinding();
                        AtomicConfigPublisher.Publication result =
                                new AtomicConfigPublisher(
                                                store.publicationPort(publication),
                                                faults,
                                                phase)
                                        .resume(publication.atomicRequest());
                        verifyBinding();
                        return result;
                    });
        }

        private void ensureMarker(
                MigrationJournal journal, boolean resumeExpected) {
            MigrationMarker expected =
                    new MigrationMarker(journal.evidence());
            byte[] bytes = expected.encode();
            MigrationPathState markerState = stateOf(paths.marker());
            verifyRecoveryPublicationState(
                    AtomicConfigPublisher.Artifact.MARKER,
                    markerState,
                    resumeExpected);
            if (markerState == MigrationPathState.PRESENT) {
                MigrationMarker actual;
                try {
                    actual = MigrationMarker.decode(read(
                            paths.marker(),
                            MigrationDirectorySession.ContentKind.MARKER));
                } catch (IllegalArgumentException failure) {
                    throw failure(
                            "marker",
                            "marker-validation",
                            "marker is malformed or unknown-version",
                            false,
                            failure);
                }
                if (!actual.equals(expected)) {
                    throw failure(
                            "marker",
                            "marker-validation",
                            "marker evidence differs from COMPLETE journal",
                            false,
                            null);
                }
            }
            checkpoint(
                    phase,
                    AtomicConfigPublisher.Artifact.MARKER,
                    MigrationFaultInjector.Operation.MARKER_PUBLISH,
                    MigrationFaultInjector.Timing.BEFORE);
            ensureArtifact(
                    AtomicConfigPublisher.Artifact.MARKER,
                    paths.fixedStages().get(4),
                    paths.marker(),
                    bytes,
                    markerState,
                    MigrationDirectorySession.ContentKind.MARKER,
                    resumeExpected);
            checkpoint(
                    phase,
                    AtomicConfigPublisher.Artifact.MARKER,
                    MigrationFaultInjector.Operation.MARKER_PUBLISH,
                    MigrationFaultInjector.Timing.AFTER);
            MigrationMarker reopened = MigrationMarker.decode(read(
                    paths.marker(),
                    MigrationDirectorySession.ContentKind.MARKER));
            if (!reopened.equals(expected)) {
                throw failure(
                        "marker",
                        "marker-reopen-validation",
                        "reopened marker differs from COMPLETE journal",
                        false,
                        null);
            }
        }

        private LockLease acquireLock(
                byte[] payload,
                boolean requireTargetAbsent,
                boolean allowEmptyFirstCreationRecovery) {
            LockLease lease = withWorldGuard(
                    "permanent lock acquisition",
                    () -> {
                        verifyBinding();
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation.LOCK_ACQUIRE,
                                MigrationFaultInjector.Timing.BEFORE);
                        LockLease acquired =
                                store.acquirePermanentLock(new LockRequest(
                                        paths.lock(),
                                        payload,
                                        paths.target(),
                                        request.legacy(),
                                        requireTargetAbsent,
                                        allowEmptyFirstCreationRecovery,
                                        request.profile(),
                                        request.strongDurability(),
                                        faults,
                                        phase));
                        activeLease = acquired;
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation.LOCK_ACQUIRE,
                                MigrationFaultInjector.Timing.AFTER);
                        verifyBinding();
                        return acquired;
                    });
            if (lease.profile() != request.profile()) {
                throw failure(
                        "permanent-lock",
                        "lock-profile-validation",
                        "permanent lock changed the frozen access profile",
                        false,
                        null);
            }
            return lease;
        }

        private byte[] read(
                Path path, MigrationDirectorySession.ContentKind kind) {
            return withWorldGuard(
                            "NOFOLLOW content read " + kind,
                            () -> {
                                verifyBinding();
                                byte[] bytes = Objects.requireNonNull(
                                        store.read(path, kind),
                                        "store content");
                                verifyBinding();
                                return bytes.clone();
                            })
                    .clone();
        }

        private void verifyBinding() {
            withWorldGuard(
                    "target-parent binding revalidation",
                    () -> {
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation
                                        .BINDING_REVALIDATION,
                                MigrationFaultInjector.Timing.BEFORE);
                        store.verifyBinding(request.binding());
                        checkpoint(
                                phase,
                                null,
                                MigrationFaultInjector.Operation
                                        .BINDING_REVALIDATION,
                                MigrationFaultInjector.Timing.AFTER);
                        verifyActiveLock();
                        return Boolean.TRUE;
                    });
        }

        private void verifyActiveLock() {
            LockLease lease = activeLease;
            if (lease == null) {
                return;
            }
            try {
                checkpoint(
                        phase,
                        null,
                        MigrationFaultInjector.Operation.LOCK_VALIDATE,
                        MigrationFaultInjector.Timing.BEFORE);
                store.verifyPermanentLock(paths.lock(), lease);
                checkpoint(
                        phase,
                        null,
                        MigrationFaultInjector.Operation.LOCK_VALIDATE,
                        MigrationFaultInjector.Timing.AFTER);
            } catch (MigrationFailure
                    | MigrationFaultInjector.SyntheticFault failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw failure(
                        "permanent-lock",
                        "permanent-lock-revalidation",
                        "permanent lock identity or payload no longer "
                                + "matches the acquired lease",
                        false,
                        failure);
            }
        }

        private void guardWorld() {
            if (request.worldGuard().isPresent()) {
                request.worldGuard()
                        .orElseThrow()
                        .beforeSuccessfulReturn(phase);
            }
        }

        private <T> T withWorldGuard(
                String operation, Supplier<T> guardedOperation) {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(guardedOperation, "guardedOperation");
            if (request.worldGuard().isEmpty()) {
                return guardedOperation.get();
            }
            try {
                return request.worldGuard()
                        .orElseThrow()
                        .around(phase, operation, guardedOperation::get);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "Unexpected checked world-guard failure during "
                                + operation,
                        failure);
            }
        }

        private void beforeSuccessfulReturn() {
            verifyBinding();
            guardWorld();
        }

        private void checkpoint(
                MigrationTargetState.Phase checkpointPhase,
                AtomicConfigPublisher.Artifact artifact,
                MigrationFaultInjector.Operation operation,
                MigrationFaultInjector.Timing timing) {
            faults.inject(new MigrationFaultInjector.Point(
                    checkpointPhase, artifact, operation, timing));
        }

        private MigrationEvidence evidence(
                LockLease lease,
                MigrationTargetState.Phase evidencePhase,
                Prepared prepared,
                Map<String, String> hashes,
                Map<String, MigrationEvidence.Durability> durability) {
            return MigrationEvidence.builder(request.targetKind())
                    .target(request.actualTarget())
                    .binding(request.binding())
                    .schemaVersion(schema.version())
                    .profile(request.profile())
                    .commitProfile(commitProfile())
                    .lockIdentity(lease.identity())
                    .phase(evidencePhase.name())
                    .projectionSha256(prepared.projectionSha256())
                    .rawLegacySha256(prepared.rawSha256())
                    .artifactHashes(hashes)
                    .artifactDurability(durability)
                    .build();
        }

        private void verifyEvidence(
                MigrationEvidence evidence, LockLease lease) {
            try {
                evidence.verifyBoundTo(
                        request.actualTarget(),
                        request.binding(),
                        request.profile(),
                        commitProfile());
                if (evidence.targetKind() != request.targetKind()
                        || !evidence.schemaVersion().equals(schema.version())
                        || !evidence.lockIdentity().equals(lease.identity())
                        || !lease.payloadSha256().equals(
                                evidence.artifactHashes().get("lock"))
                        || evidence.artifactDurability()
                                .values()
                                .stream()
                                .anyMatch(value ->
                                        value != commitProfile())) {
                    throw new IllegalStateException(
                            "evidence kind, schema, lock identity, or lock "
                                    + "payload SHA differs, or artifact durability "
                                    + "does not match the commit profile");
                }
                Set<String> expectedArtifacts =
                        expectedArtifactKeys(evidence.phase());
                if (!evidence.artifactHashes()
                                .keySet()
                                .equals(expectedArtifacts)
                        || !evidence.artifactDurability()
                                .keySet()
                                .equals(expectedArtifacts)) {
                    throw failure(
                            "evidence",
                            "evidence-artifact-set-validation",
                            "journal evidence artifact set for "
                                    + evidence.phase()
                                    + " must be exactly "
                                    + expectedArtifacts,
                            false,
                            null);
                }
            } catch (MigrationFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw failure(
                        "evidence",
                        "evidence-binding-validation",
                        "journal/marker evidence does not bind the requested "
                                + "target, parent, schema, profile, lock, and "
                                + "artifact durability",
                        false,
                        failure);
            }
        }

        private Set<String> expectedArtifactKeys(String evidencePhase) {
            MigrationTargetState.Phase parsed;
            try {
                parsed = MigrationTargetState.Phase.valueOf(evidencePhase);
            } catch (IllegalArgumentException failure) {
                throw new IllegalStateException(
                        "Evidence phase is unknown: " + evidencePhase,
                        failure);
            }
            return switch (parsed) {
                case PREPARED -> Set.of("lock");
                case BACKUP_PUBLISHED -> Set.of("lock", "backup");
                case INITIAL_PUBLISHED ->
                        Set.of("lock", "backup", "initial");
                case TARGET_PUBLISHED, COMPLETE ->
                        Set.of("lock", "backup", "initial", "target");
                case NO_EVIDENCE, LOCKED -> throw new IllegalStateException(
                        "Transient phase cannot carry journal evidence: "
                                + parsed);
            };
        }

        private void requireArtifactHash(
                MigrationEvidence evidence, String artifact, byte[] bytes) {
            String expected = evidence.artifactHashes().get(artifact);
            if (expected == null || !expected.equals(sha256(bytes))) {
                throw failure(
                        artifact,
                        artifact + "-hash-validation",
                        "artifact hash is missing or conflicts with journal",
                        false,
                        null);
            }
        }

        private MigrationTargetState state(
                MigrationTargetState.Outcome outcome,
                MigrationTargetState.Phase statePhase,
                String projectionSha,
                Map<String, MigrationEvidence.Durability> durability) {
            return new MigrationTargetState(
                    request.targetKind(),
                    request.actualTarget(),
                    outcome,
                    statePhase,
                    projectionSha,
                    commitProfile(),
                    durability);
        }

        private MigrationEvidence.Durability commitProfile() {
            return request.strongDurability()
                    ? MigrationEvidence.Durability.STRONG
                    : MigrationEvidence.Durability.BASIC;
        }

        private MigrationFailure failure(
                String artifact,
                String operation,
                String failureReason,
                boolean synthetic,
                Throwable cause) {
            return new MigrationFailure(
                    request.legacy(),
                    request.actualTarget(),
                    phase,
                    artifact,
                    operation,
                    failureReason,
                    RECOVERY,
                    synthetic,
                    cause);
        }

        private MigrationFailure failureWithRecovery(
                String artifact,
                String operation,
                String failureReason,
                String recovery,
                boolean synthetic,
                Throwable cause) {
            return new MigrationFailure(
                    request.legacy(),
                    request.actualTarget(),
                    phase,
                    artifact,
                    operation,
                    failureReason,
                    recovery,
                    synthetic,
                    cause);
        }

        private MigrationFailure syntheticFailure(
                MigrationFaultInjector.SyntheticFault fault) {
            MigrationFaultInjector.Point point = fault.point();
            String artifact = point.artifact() == null
                    ? "migration-core"
                    : point.artifact().name().toLowerCase(
                            java.util.Locale.ROOT);
            return new MigrationFailure(
                    request.legacy(),
                    request.actualTarget(),
                    point.phase(),
                    artifact,
                    point.operation().name(),
                    reason(fault),
                    RECOVERY,
                    true,
                    fault);
        }
    }

    record Request(
            MigrationTarget targetKind,
            Path legacy,
            Path actualTarget,
            MigrationBinding binding,
            MigrationAccessProfile profile,
            Optional<ActualTargetResolver.WorldAbsenceGuard> worldGuard,
            boolean strongDurability) {
        Request {
            Objects.requireNonNull(targetKind, "targetKind");
            legacy = normalizedAbsolute(legacy, "legacy");
            actualTarget = normalizedAbsolute(actualTarget, "actualTarget");
            binding = Objects.requireNonNull(binding, "binding");
            if (!binding.target().equals(actualTarget)) {
                throw new IllegalArgumentException(
                        "Request binding does not match actual target");
            }
            Objects.requireNonNull(profile, "profile");
            worldGuard = Objects.requireNonNull(worldGuard, "worldGuard");
            if (profile == MigrationAccessProfile.BASIC
                    && strongDurability) {
                throw new IllegalArgumentException(
                        "BASIC migration profile cannot claim STRONG durability");
            }
        }
    }

    record LockRequest(
            Path lock,
            byte[] payload,
            Path target,
            Path legacy,
            boolean requireTargetAbsent,
            boolean allowEmptyFirstCreationRecovery,
            MigrationAccessProfile profile,
            boolean strongRequired,
            MigrationFaultInjector faults,
            MigrationTargetState.Phase phase) {
        LockRequest {
            lock = normalizedAbsolute(lock, "lock");
            payload = Objects.requireNonNull(payload, "payload").clone();
            target = normalizedAbsolute(target, "target");
            legacy = normalizedAbsolute(legacy, "legacy");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(faults, "faults");
            Objects.requireNonNull(phase, "phase");
            if (!lock.getParent().equals(target.getParent())) {
                throw new IllegalArgumentException(
                        "Permanent lock must share the target parent");
            }
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record LockLease(
            String identity,
            String payloadSha256,
            MigrationEvidence.Durability durability,
            boolean createdNow,
            boolean targetAbsentUnderLock,
            MigrationAccessProfile profile,
            boolean recoveredEmptyFirstCreation) {
        LockLease {
            Objects.requireNonNull(identity, "identity");
            if (identity.isBlank()) {
                throw new IllegalArgumentException(
                        "Lock identity must not be blank");
            }
            payloadSha256 = requireSha(payloadSha256, "payloadSha256");
            Objects.requireNonNull(durability, "durability");
            Objects.requireNonNull(profile, "profile");
            if (createdNow && recoveredEmptyFirstCreation) {
                throw new IllegalArgumentException(
                        "A newly created lock cannot also be an existing "
                                + "empty-lock recovery");
            }
        }
    }

    record PublishRequest(
            AtomicConfigPublisher.Artifact artifact,
            Path stage,
            Path destination,
            byte[] bytes,
            AtomicConfigPublisher.DestinationExpectation expectation,
            boolean strongRequired) {
        PublishRequest {
            Objects.requireNonNull(artifact, "artifact");
            stage = normalizedAbsolute(stage, "stage");
            destination = normalizedAbsolute(destination, "destination");
            if (!stage.getParent().equals(destination.getParent())
                    || stage.equals(destination)) {
                throw new IllegalArgumentException(
                        "Fixed stage and destination must be distinct siblings");
            }
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            Objects.requireNonNull(expectation, "expectation");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        AtomicConfigPublisher.Request atomicRequest() {
            return new AtomicConfigPublisher.Request(
                    artifact,
                    stage.getFileName().toString(),
                    destination.getFileName().toString(),
                    bytes,
                    expectation,
                    strongRequired);
        }
    }

    interface Store {
        MigrationPathState state(Path path);

        default MigrationPathState.Observation observe(Path path) {
            return MigrationPathState.Observation.fromState(state(path));
        }

        byte[] read(Path path, MigrationDirectorySession.ContentKind kind);

        LockLease acquirePermanentLock(LockRequest request);

        AtomicConfigPublisher.Port publicationPort(PublishRequest request);

        String identity(Path path);

        void verifyPermanentLock(Path path, LockLease lease);

        void verifyBinding(MigrationBinding binding);
    }

    private record Snapshot(
            MigrationPathState target,
            MigrationPathState lock,
            MigrationPathState journal,
            MigrationPathState backup,
            MigrationPathState initial,
            MigrationPathState marker) {
        private boolean hasEvidence() {
            return lock == MigrationPathState.PRESENT
                    || journal == MigrationPathState.PRESENT
                    || backup == MigrationPathState.PRESENT
                    || initial == MigrationPathState.PRESENT
                    || marker == MigrationPathState.PRESENT;
        }

        private boolean onlyPermanentLock() {
            return target == MigrationPathState.ABSENT
                    && lock == MigrationPathState.PRESENT
                    && journal == MigrationPathState.ABSENT
                    && backup == MigrationPathState.ABSENT
                    && initial == MigrationPathState.ABSENT
                    && marker == MigrationPathState.ABSENT;
        }
    }

    private record ResumePermissions(
            boolean backup,
            boolean initial,
            boolean target,
            boolean marker) {
        private static ResumePermissions none() {
            return new ResumePermissions(false, false, false, false);
        }

        private static ResumePermissions from(
                MigrationTargetState.Phase phase,
                Snapshot lockedSnapshot) {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(lockedSnapshot, "lockedSnapshot");
            return new ResumePermissions(
                    phase == MigrationTargetState.Phase.PREPARED
                            && lockedSnapshot.backup()
                                    == MigrationPathState.PRESENT,
                    phase == MigrationTargetState.Phase.BACKUP_PUBLISHED
                            && lockedSnapshot.initial()
                                    == MigrationPathState.PRESENT,
                    phase == MigrationTargetState.Phase.INITIAL_PUBLISHED
                            && lockedSnapshot.target()
                                    == MigrationPathState.PRESENT,
                    phase == MigrationTargetState.Phase.COMPLETE
                            && lockedSnapshot.marker()
                                    == MigrationPathState.PRESENT);
        }
    }

    private record Prepared(
            byte[] rawLegacy,
            String rawSha256,
            String projectionSha256,
            byte[] canonical) {
        private Prepared {
            rawLegacy = Objects.requireNonNull(rawLegacy, "rawLegacy").clone();
            rawSha256 = requireSha(rawSha256, "rawSha256");
            projectionSha256 =
                    requireSha(projectionSha256, "projectionSha256");
            canonical = Objects.requireNonNull(canonical, "canonical").clone();
        }

        @Override
        public byte[] rawLegacy() {
            return rawLegacy.clone();
        }

        @Override
        public byte[] canonical() {
            return canonical.clone();
        }
    }

    private byte[] lockPayload(Request request) {
        StringBuilder output = new StringBuilder();
        output.append("IAMZOMBIEQ-LOCK\n");
        output.append("version=1\n");
        output.append("target=")
                .append(request.actualTarget())
                .append('\n');
        output.append("logicalParent=")
                .append(request.binding().logicalParent())
                .append('\n');
        output.append("physicalParent=")
                .append(request.binding().physicalParent())
                .append('\n');
        output.append("ancestorCount=")
                .append(request.binding().ancestors().size())
                .append('\n');
        for (int index = 0;
                index < request.binding().ancestors().size();
                index++) {
            MigrationBinding.Ancestor ancestor =
                    request.binding().ancestors().get(index);
            output.append("ancestor.")
                    .append(index)
                    .append(".path=")
                    .append(ancestor.path())
                    .append('\n');
            output.append("ancestor.")
                    .append(index)
                    .append(".identity=")
                    .append(ancestor.identity())
                    .append('\n');
        }
        output.append("directoryIdentity=")
                .append(request.binding().directoryIdentity())
                .append('\n');
        output.append("providerIdentity=")
                .append(request.binding().providerIdentity())
                .append('\n');
        output.append("fileStoreIdentity=")
                .append(request.binding().fileStoreIdentity())
                .append('\n');
        output.append("profile=").append(request.profile()).append('\n');
        output.append("durabilityProfile=")
                .append(request.strongDurability()
                        ? MigrationEvidence.Durability.STRONG
                        : MigrationEvidence.Durability.BASIC)
                .append('\n');
        output.append("schemaVersion=")
                .append(schema.version())
                .append('\n');
        output.append("force=file-force+atomic-move\n");
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String strictUtf8(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("content is not strict UTF-8", failure);
        }
    }

    private static Path normalizedAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(
                    field + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static String requireSha(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    field + " must be lowercase SHA-256");
        }
        return value;
    }

    private static String reason(RuntimeException failure) {
        return MigrationFailure.describe(failure);
    }
}
