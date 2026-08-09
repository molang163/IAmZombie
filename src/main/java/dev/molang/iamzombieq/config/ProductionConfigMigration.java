package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Package-private production adapter for the otherwise Minecraft-free migration
 * core.
 */
final class ProductionConfigMigration {
    private ProductionConfigMigration() {
    }

    static Optional<MigrationTargetState> migrateServer(
            Path globalConfigParent, Path worldServerConfigParent) {
        return migrateServer(
                globalConfigParent,
                worldServerConfigParent,
                FreshReturnCheckpoint.none());
    }

    static Optional<MigrationTargetState> migrateServer(
            Path globalConfigParent,
            Path worldServerConfigParent,
            FreshReturnCheckpoint freshReturnCheckpoint) {
        Path global = normalizedDirectory(
                globalConfigParent, "global config parent");
        Path world = normalizedDirectory(
                worldServerConfigParent, "world server-config parent");
        FreshReturnCheckpoint checkpoint = Objects.requireNonNull(
                freshReturnCheckpoint, "freshReturnCheckpoint");
        Path legacy = ActualTargetResolver.fixedChild(
                global, ActualTargetResolver.LEGACY_BASENAME);
        MigrationJavaRuntimeMatrix.requireSupported(
                legacy,
                ActualTargetResolver.fixedChild(
                        world, ActualTargetResolver.SERVER_BASENAME));
        try (ProductionPort port =
                ProductionPort.server(global, world, legacy)) {
            return execute(
                    MigrationTarget.SERVER, legacy, port, checkpoint);
        }
    }

    static Optional<MigrationTargetState> migratePreferences(
            Path globalConfigParent) {
        Path global = normalizedDirectory(
                globalConfigParent, "global config parent");
        Path legacy = ActualTargetResolver.fixedChild(
                global, ActualTargetResolver.LEGACY_BASENAME);
        MigrationJavaRuntimeMatrix.requireSupported(
                legacy,
                ActualTargetResolver.fixedChild(
                        global,
                        ActualTargetResolver.PREFERENCES_BASENAME));
        try (ProductionPort port =
                ProductionPort.preferences(global, legacy)) {
            return execute(
                    MigrationTarget.PREFERENCES,
                    legacy,
                    port,
                    FreshReturnCheckpoint.none());
        }
    }

    private static Optional<MigrationTargetState> execute(
            MigrationTarget targetKind,
            Path legacy,
            ProductionPort port,
            FreshReturnCheckpoint freshReturnCheckpoint) {
        port.resolveTarget();
        Path actualTarget = port.actualTarget();
        MigrationMetadataBootstrap.Candidates candidates =
                MigrationMetadataBootstrap.Candidates.actualTargetOnly(
                        targetKind, actualTarget, legacy);
        MigrationMetadataBootstrap bootstrap =
                new MigrationMetadataBootstrap(port);
        MigrationMetadataBootstrap.Result result =
                bootstrap.inspect(candidates);
        if (result.kind() == MigrationMetadataBootstrap.Kind.FRESH) {
            result = bootstrap.inspectLegacyAfterTargetFresh(result);
        }
        if (result.kind() == MigrationMetadataBootstrap.Kind.FRESH) {
            try {
                port.validateFreshNamespace();
                freshReturnCheckpoint.beforeFinalVerification();
                port.beforeFreshSuccessfulReturn(bootstrap, result);
            } catch (MigrationFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw MigrationFailure.operational(
                        legacy,
                        result.operationalTarget(),
                        MigrationTargetState.Phase.NO_EVIDENCE,
                        result.operationalTarget()
                                .getFileName()
                                .toString(),
                        "fresh-namespace-binding",
                        "Fresh metadata was all absent, but the actual target "
                                + "parent namespace could not be trusted",
                        failure);
            }
            return Optional.empty();
        }

        if (!result.operationalTarget().equals(actualTarget)) {
            throw MigrationFailure.operational(
                    legacy,
                    actualTarget,
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    actualTarget.getFileName().toString(),
                    "bootstrap-target-reconciliation",
                    "Profile-independent bootstrap selected "
                            + result.operationalTarget()
                            + " but the actual-target resolver selected "
                            + actualTarget,
                    null);
        }

        MigrationMetadataBootstrap.Result sessionResult = result;
        port.aroundWorldGuard(
                "bound bootstrap metadata revalidation",
                () -> {
                    bootstrap.revalidateThroughSession(
                            sessionResult,
                            port.store()::readNofollowMetadata);
                    return Boolean.TRUE;
                });

        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        targetKind,
                        legacy,
                        actualTarget,
                        port.binding(),
                        port.profile(),
                        port.worldGuard(),
                        port.strongDurability());
        MigrationTargetState state = new ConfigMigrationEngine(
                        ConfigSchemaCatalog.load(),
                        MigrationFaultInjector.none())
                .migrateApplicable(result, request, port.store());
        port.recordPhase(state.phase());
        return Optional.of(state);
    }

    @FunctionalInterface
    interface FreshReturnCheckpoint {
        void beforeFinalVerification();

        static FreshReturnCheckpoint none() {
            return () -> {
            };
        }
    }

    private static Path normalizedDirectory(Path path, String description) {
        Objects.requireNonNull(path, description);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(
                    description + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static final class ProductionPort
            implements MigrationMetadataBootstrap.Port, AutoCloseable {
        private final JdkMigrationFileSystem fileSystem =
                new JdkMigrationFileSystem();
        private final MigrationTarget targetKind;
        private final Path global;
        private final Path world;
        private final Path legacy;
        private ActualTargetResolver.Resolution resolution;
        private MigrationBinding binding;
        private List<MigrationBinding> requiredBindings;
        private MigrationAccessProfile.Frozen frozenProfile;
        private GuardPlan guardPlan;
        private MigrationDirectorySession guardSession;
        private ActualTargetResolver.WorldAbsenceGuard boundWorldGuard;
        private JdkMigrationFileSystem.StoreSession store;
        private MigrationTargetState.Phase lastPhase =
                MigrationTargetState.Phase.NO_EVIDENCE;
        private boolean legacySessionRequired;
        private boolean closed;

        private ProductionPort(
                MigrationTarget targetKind,
                Path global,
                Path world,
                Path legacy) {
            this.targetKind = Objects.requireNonNull(
                    targetKind, "targetKind");
            this.global = Objects.requireNonNull(global, "global");
            this.world = world;
            this.legacy = Objects.requireNonNull(legacy, "legacy");
        }

        static ProductionPort server(
                Path global, Path world, Path legacy) {
            return new ProductionPort(
                    MigrationTarget.SERVER, global, world, legacy);
        }

        static ProductionPort preferences(Path global, Path legacy) {
            return new ProductionPort(
                    MigrationTarget.PREFERENCES,
                    global,
                    null,
                    legacy);
        }

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(Path path)
                throws IOException {
            ensureOpen();
            return fileSystem.readNofollowMetadata(path);
        }

        @Override
        public void selectProfile() {
            ensureOpen();
            if (frozenProfile != null) {
                throw new IllegalStateException(
                        "Migration profile was already selected");
            }
            bindResolvedTarget();
            try {
                MigrationAccessProfile selected = null;
                for (MigrationBinding required : requiredBindings) {
                    MigrationAccessProfile candidate =
                            MigrationAccessProfile.select(
                                    fileSystem.capabilities(required),
                                    false);
                    if (selected == null) {
                        selected = candidate;
                    } else if (selected != candidate) {
                        throw new IllegalStateException(
                                "Migration target and world guard "
                                        + "require different access profiles");
                    }
                }
                frozenProfile = MigrationAccessProfile.freeze(
                        Objects.requireNonNull(
                                selected, "selected profile"));
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not select the migration access profile",
                        failure);
            }
        }

        void validateFreshNamespace() {
            ensureOpen();
            if (frozenProfile != null || store != null) {
                throw new IllegalStateException(
                        "Fresh namespace validation must precede profile "
                                + "selection and session access");
            }
            bindResolvedTarget();
        }

        void beforeFreshSuccessfulReturn(
                MigrationMetadataBootstrap bootstrap,
                MigrationMetadataBootstrap.Result result) {
            ensureOpen();
            Objects.requireNonNull(bootstrap, "bootstrap");
            Objects.requireNonNull(result, "result");
            if (result.kind() != MigrationMetadataBootstrap.Kind.FRESH
                    || frozenProfile != null
                    || store != null
                    || guardSession != null
                    || boundWorldGuard != null) {
                throw new IllegalStateException(
                        "Fresh final verification must remain metadata-only");
            }

            verifyFreshBindings();
            bootstrap.revalidateFreshAfterNamespaceBinding(
                    result, this::readNofollowMetadata);
            if (guardPlan != null) {
                requireResolution()
                        .worldGuard()
                        .orElseThrow()
                        .rebind(guardPlan.freshStateReader(fileSystem))
                        .beforeSuccessfulReturn(
                                MigrationTargetState.Phase.NO_EVIDENCE);
            }
            verifyFreshBindings();
        }

        void resolveTarget() {
            ensureOpen();
            if (resolution != null) {
                throw new IllegalStateException(
                        "Migration actual target was already resolved");
            }
            resolution = switch (targetKind) {
                case SERVER -> new ActualTargetResolver(fileSystem::observe)
                        .resolveServer(global, Objects.requireNonNull(world));
                case PREFERENCES ->
                        new ActualTargetResolver(fileSystem::observe)
                                .resolvePreferences(global);
            };
        }

        private void bindResolvedTarget() {
            if (resolution == null) {
                throw new IllegalStateException(
                        "Migration actual target must be resolved before "
                                + "binding its namespace");
            }
            if (binding != null
                    || requiredBindings != null
                    || guardPlan != null) {
                throw new IllegalStateException(
                        "Migration target namespace was already bound");
            }

            try {
                binding = MigrationBinding.capture(
                        fileSystem.observeBinding(
                                resolution.actualTarget()));
                ArrayList<MigrationBinding> capturedBindings =
                        new ArrayList<>();
                capturedBindings.add(binding);
                if (legacySessionRequired
                        && !legacy.getParent().equals(
                                resolution.actualTarget().getParent())) {
                    capturedBindings.add(MigrationBinding.capture(
                            fileSystem.observeBinding(legacy)));
                }
                if (resolution.worldGuard().isPresent()) {
                    guardPlan = GuardPlan.capture(
                            fileSystem,
                            Objects.requireNonNull(world),
                            resolution.worldGuard().orElseThrow(),
                            legacy,
                            resolution.actualTarget());
                    capturedBindings.add(guardPlan.binding());
                }
                requiredBindings = List.copyOf(capturedBindings);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not bind migration directories",
                        failure);
            }
        }

        @Override
        public void openSession() {
            ensureOpen();
            if (store != null) {
                throw new IllegalStateException(
                        "Migration session was already opened");
            }
            MigrationAccessProfile profile = profile();
            store = fileSystem.openStore(
                    profile,
                    binding(),
                    legacy,
                    legacySessionRequired);
            if (guardPlan != null) {
                guardSession = fileSystem.openDirectorySession(
                        profile, guardPlan.binding());
                boundWorldGuard = resolution.worldGuard()
                        .orElseThrow()
                        .rebind(guardPlan.stateReader(guardSession));
            }
        }

        Path actualTarget() {
            return requireResolution().actualTarget();
        }

        MigrationBinding binding() {
            if (binding == null) {
                throw new IllegalStateException(
                        "Migration binding is not selected");
            }
            return binding;
        }

        MigrationAccessProfile profile() {
            if (frozenProfile == null) {
                throw new IllegalStateException(
                        "Migration profile is not frozen");
            }
            return frozenProfile.profile();
        }

        boolean strongDurability() {
            return profile() == MigrationAccessProfile.SECURE;
        }

        Optional<ActualTargetResolver.WorldAbsenceGuard> worldGuard() {
            return Optional.ofNullable(boundWorldGuard);
        }

        JdkMigrationFileSystem.StoreSession store() {
            if (store == null) {
                throw new IllegalStateException(
                        "Migration store is not open");
            }
            return store;
        }

        void recordPhase(MigrationTargetState.Phase phase) {
            lastPhase = Objects.requireNonNull(phase, "phase");
        }

        <T> T aroundWorldGuard(
                String operation,
                ActualTargetResolver.GuardedOperation<T> work) {
            Objects.requireNonNull(work, "work");
            if (boundWorldGuard == null) {
                try {
                    return work.run();
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(
                            "Unexpected checked bootstrap operation failure",
                            failure);
                }
            }
            try {
                return boundWorldGuard.around(
                        MigrationTargetState.Phase.NO_EVIDENCE,
                        operation,
                        work);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "Unexpected checked world-guard failure", failure);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            IOException closeFailure = null;
            if (store != null) {
                try {
                    store.close();
                } catch (IOException failure) {
                    closeFailure = failure;
                }
            }
            if (guardSession != null) {
                try {
                    guardSession.close();
                } catch (IOException failure) {
                    if (closeFailure == null) {
                        closeFailure = failure;
                    } else {
                        closeFailure.addSuppressed(failure);
                    }
                }
            }
            if (closeFailure != null) {
                Path target = resolution == null
                        ? ActualTargetResolver.fixedChild(
                                global,
                                targetKind == MigrationTarget.SERVER
                                        ? ActualTargetResolver.SERVER_BASENAME
                                        : ActualTargetResolver
                                                .PREFERENCES_BASENAME)
                        : resolution.actualTarget();
                throw MigrationFailure.operational(
                        legacy,
                        target,
                        lastPhase,
                        "migration-session",
                        "bound-session-close",
                        "Could not close the migration directory session",
                        closeFailure);
            }
        }

        @Override
        public byte[] readContent(Path path) {
            throw new IllegalStateException(
                    "Bootstrap metadata port cannot read content");
        }

        @Override
        public void hashContent() {
            throw new IllegalStateException(
                    "Bootstrap metadata port cannot hash content");
        }

        @Override
        public void createDirectory() {
            throw new IllegalStateException(
                    "Bootstrap metadata port cannot create directories");
        }

        @Override
        public void createArtifact() {
            throw new IllegalStateException(
                    "Bootstrap metadata port cannot create artifacts");
        }

        @Override
        public void prepareLegacySession() {
            ensureOpen();
            if (frozenProfile != null
                    || binding != null
                    || requiredBindings != null
                    || store != null) {
                throw new IllegalStateException(
                        "Legacy session requirement must be frozen before "
                                + "profile selection or namespace binding");
            }
            legacySessionRequired = true;
        }

        private ActualTargetResolver.Resolution requireResolution() {
            if (resolution == null) {
                throw new IllegalStateException(
                        "Actual migration target is not resolved");
            }
            return resolution;
        }

        private void verifyFreshBindings() {
            if (requiredBindings == null || requiredBindings.isEmpty()) {
                throw new IllegalStateException(
                        "Fresh namespace bindings were not captured");
            }
            for (MigrationBinding required : requiredBindings) {
                try {
                    required.verifyUnchanged(
                            fileSystem.observeBinding(required.target()));
                } catch (IOException | RuntimeException failure) {
                    throw MigrationFailure.operational(
                            legacy,
                            requireResolution().actualTarget(),
                            MigrationTargetState.Phase.NO_EVIDENCE,
                            required.logicalParent()
                                    .getFileName()
                                    .toString(),
                            "fresh-namespace-revalidation",
                            "Fresh target or world-guard binding changed "
                                    + "before successful return",
                            failure);
                }
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException(
                        "Production migration port is closed");
            }
        }
    }

    private record GuardPlan(
            Kind kind,
            Path worldServerConfigParent,
            List<Path> guardedCandidates,
            MigrationBinding binding) {
        private GuardPlan {
            Objects.requireNonNull(kind, "kind");
            worldServerConfigParent = Objects.requireNonNull(
                    worldServerConfigParent, "worldServerConfigParent");
            guardedCandidates = List.copyOf(
                    Objects.requireNonNull(
                            guardedCandidates, "guardedCandidates"));
            binding = Objects.requireNonNull(binding, "binding");
        }

        static GuardPlan capture(
                JdkMigrationFileSystem fileSystem,
                Path worldServerConfigParent,
                ActualTargetResolver.WorldAbsenceGuard original,
                Path legacy,
                Path actualTarget)
                throws IOException {
            JdkMigrationFileSystem.DirectoryObservation parent =
                    fileSystem.observeDirectory(worldServerConfigParent);
            return switch (parent.state()) {
                case PRESENT -> new GuardPlan(
                        Kind.EXISTING_PARENT,
                        worldServerConfigParent,
                        original.guardedCandidates(),
                        MigrationBinding.capture(
                                fileSystem.observeBinding(
                                        ActualTargetResolver.fixedChild(
                                                worldServerConfigParent,
                                                ActualTargetResolver
                                                        .SERVER_BASENAME))));
                case ABSENT -> {
                    Path worldRoot = worldServerConfigParent.getParent();
                    if (worldRoot == null) {
                        throw guardFailure(
                                legacy,
                                actualTarget,
                                worldServerConfigParent,
                                "World server-config parent has no world root",
                                null);
                    }
                    JdkMigrationFileSystem.DirectoryObservation root =
                            fileSystem.observeDirectory(worldRoot);
                    if (root.state() != MigrationPathState.PRESENT) {
                        throw guardFailure(
                                legacy,
                                actualTarget,
                                worldRoot,
                                "World root is not a safe stable directory: "
                                        + root.state()
                                        + "; "
                                        + root.detail(),
                                root.cause());
                    }
                    yield new GuardPlan(
                            Kind.MISSING_CHILD,
                            worldServerConfigParent,
                            original.guardedCandidates(),
                            MigrationBinding.capture(
                                    fileSystem.observeBinding(
                                            worldServerConfigParent)));
                }
                case UNKNOWN, UNSAFE -> throw guardFailure(
                        legacy,
                        actualTarget,
                        worldServerConfigParent,
                        "World server-config parent is not safely bound: "
                                + parent.state()
                                + "; "
                                + parent.detail(),
                        parent.cause());
            };
        }

        ActualTargetResolver.StateReader stateReader(
                MigrationDirectorySession session) {
            Objects.requireNonNull(session, "session");
            return candidate -> {
                if (!guardedCandidates.contains(candidate)) {
                    throw new IllegalArgumentException(
                            "Path is outside the fixed world guard: "
                                    + candidate);
                }
                String operand = kind == Kind.EXISTING_PARENT
                        ? candidate.getFileName().toString()
                        : worldServerConfigParent.getFileName().toString();
                return MigrationPathState.observe(
                        () -> session.readNofollowMetadata(operand));
            };
        }

        ActualTargetResolver.StateReader freshStateReader(
                JdkMigrationFileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "fileSystem");
            return candidate -> {
                if (!guardedCandidates.contains(candidate)) {
                    throw new IllegalArgumentException(
                            "Path is outside the fixed world guard: "
                                    + candidate);
                }
                Path observed = kind == Kind.EXISTING_PARENT
                        ? candidate
                        : worldServerConfigParent;
                return fileSystem.observe(observed);
            };
        }

        private static MigrationFailure guardFailure(
                Path legacy,
                Path actualTarget,
                Path artifact,
                String reason,
                Throwable cause) {
            return MigrationFailure.operational(
                    legacy,
                    actualTarget,
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    artifact.getFileName().toString(),
                    "world-guard-binding",
                    reason,
                    cause);
        }

        private enum Kind {
            EXISTING_PARENT,
            MISSING_CHILD
        }
    }
}
