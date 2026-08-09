package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent JVM entry point for the hosted Windows lock-contention gate. */
final class WindowsMigrationLockProcessMain {
    private static final String MAGIC =
            "IAMZOMBIEQ-WINDOWS-EXTERNAL-PROCESS-V1";
    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private static final Duration RELEASE_TIMEOUT = Duration.ofSeconds(90);

    private WindowsMigrationLockProcessMain() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: <holder|contender> <global> <result> ...");
        }
        String role = arguments[0];
        Path global = normalizedAbsolute(arguments[1], "global");
        Path result = normalizedAbsolute(arguments[2], "result");
        requireWindowsNodeJavaNtfs(global);

        switch (role) {
            case "holder" -> {
                if (arguments.length != 5) {
                    throw new IllegalArgumentException(
                            "holder requires <global> <result> <ready> <release>");
                }
                holdProductionLock(
                        global,
                        result,
                        normalizedAbsolute(arguments[3], "ready"),
                        normalizedAbsolute(arguments[4], "release"));
            }
            case "contender" -> {
                if (arguments.length != 3) {
                    throw new IllegalArgumentException(
                            "contender requires <global> <result>");
                }
                contendThroughProductionMigration(global, result);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown process role: " + role);
        }
    }

    private static void holdProductionLock(
            Path global, Path result, Path ready, Path release)
            throws Exception {
        Path legacy = global.resolve(ActualTargetResolver.LEGACY_BASENAME);
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        require(
                profile == MigrationAccessProfile.BASIC,
                "holder did not select the Windows BASIC profile");

        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.PREFERENCES,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        byte[] lockBasePayload = lockBasePayload(request, schema);
        Path lock = MigrationFileSystem.ArtifactPaths
                .forTarget(target)
                .lock();
        AtomicBoolean announced = new AtomicBoolean();
        MigrationFaultInjector holdAfterAcquisition = point -> {
            if (point.operation()
                            == MigrationFaultInjector.Operation.LOCK_ACQUIRE
                    && point.timing()
                            == MigrationFaultInjector.Timing.AFTER
                    && announced.compareAndSet(false, true)) {
                try {
                    MigrationPathState.Metadata lockMetadata =
                            fileSystem.readNofollowMetadata(lock);
                    byte[] expectedLockPayload =
                            PermanentMigrationLock.payloadWithIdentity(
                                    lockBasePayload,
                                    lockMetadata.identity());
                    require(
                            lockMetadata.size() == expectedLockPayload.length,
                            "held lock size differs from its verified payload");
                    writeNew(
                            ready,
                            MAGIC
                                    + "\nrole=holder\nstatus=LOCK_HELD\n"
                                    + "pid="
                                    + ProcessHandle.current().pid()
                                    + "\nlock="
                                    + lock
                                    + "\nlockSize="
                                    + expectedLockPayload.length
                                    + "\nlockSha256="
                                    + PermanentMigrationLock.payloadSha256(
                                            expectedLockPayload)
                                    + "\n");
                    awaitRelease(release);
                } catch (IOException failure) {
                    throw new IllegalStateException(
                            "Could not publish READY or await RELEASE", failure);
                }
            }
        };

        MigrationTargetState state;
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            state = new ConfigMigrationEngine(
                            schema, holdAfterAcquisition)
                    .migrate(request, store);
        }
        require(announced.get(), "holder never reached LOCK_ACQUIRE/AFTER");
        require(
                state.outcome() == MigrationTargetState.Outcome.MIGRATED,
                "holder outcome was " + state.outcome());
        require(
                state.phase() == MigrationTargetState.Phase.COMPLETE,
                "holder phase was " + state.phase());
        writeNew(
                result,
                MAGIC
                        + "\nrole=holder\nstatus=COMPLETED_AND_RELEASED\n"
                        + "outcome="
                        + state.outcome()
                        + "\nphase="
                        + state.phase()
                        + "\n");
    }

    private static void contendThroughProductionMigration(
            Path global, Path result) throws IOException {
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        MigrationFailure contention;
        try {
            ProductionConfigMigration.migratePreferences(global);
            throw new IllegalStateException(
                    "contender unexpectedly passed the held permanent lock");
        } catch (MigrationFailure failure) {
            contention = failure;
        }

        require(
                contention.phase() == MigrationTargetState.Phase.LOCKED,
                "contender phase was " + contention.phase());
        require(
                contention.artifact().equals("migration-core"),
                "contender artifact was " + contention.artifact());
        require(
                contention.operation().equals("engine-execution"),
                "contender operation was " + contention.operation());
        require(!contention.synthetic(), "contender failure was synthetic");
        require(
                contention.reason()
                        .toLowerCase(Locale.ROOT)
                        .contains("contended or untrusted"),
                "contender did not report lock contention: "
                        + contention.reason());
        require(
                contention.recovery().contains("C1-F1-STOP-PRESERVE-v1"),
                "contender did not return the permanent F1 recovery contract");

        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        require(
                Files.isRegularFile(artifacts.lock(), LinkOption.NOFOLLOW_LINKS),
                "holder permanent lock is absent");
        require(
                Files.size(artifacts.lock()) > 0,
                "holder permanent lock is not initialized");
        requireAbsent(artifacts.target());
        requireAbsent(artifacts.journal());
        requireAbsent(artifacts.backup());
        requireAbsent(artifacts.initial());
        requireAbsent(artifacts.marker());
        for (Path stage : artifacts.fixedStages()) {
            requireAbsent(stage);
        }

        writeNew(
                result,
                MAGIC
                        + "\nrole=contender\nstatus=EXPECTED_CONTENTION_F1\n"
                        + "pid="
                        + ProcessHandle.current().pid()
                        + "\n"
                        + "phase="
                        + contention.phase()
                        + "\nartifact="
                        + contention.artifact()
                        + "\noperation="
                        + contention.operation()
                        + "\n");
    }

    private static void awaitRelease(Path release) throws IOException {
        long deadline = System.nanoTime() + RELEASE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(release, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = Files.readAttributes(
                        release,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()
                        || attributes.isSymbolicLink()
                        || attributes.isOther()) {
                    throw new IOException(
                            "RELEASE signal is not a safe regular file: "
                                    + release);
                }
                return;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "Interrupted while awaiting RELEASE", interrupted);
            }
        }
        throw new IOException("Timed out awaiting RELEASE: " + release);
    }

    private static byte[] lockBasePayload(
            ConfigMigrationEngine.Request request,
            ConfigSchemaCatalog schema) {
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

    private static void requireWindowsNodeJavaNtfs(Path global)
            throws IOException {
        require(
                System.getProperty("os.name", "unknown")
                        .startsWith("Windows"),
                "subprocess is not running on Windows");
        int javaFeature = Runtime.version().feature();
        require(
                MigrationJavaRuntimeMatrix.supportsBasicProfile(javaFeature),
                "subprocess Java feature "
                        + javaFeature
                        + " is not approved for Windows BASIC on this node; "
                        + "approved="
                        + MigrationJavaRuntimeMatrix.runtimeFeatures());
        require(!isUnc(global), "subprocess global path is UNC");
        var provider = global.getFileSystem().provider();
        require(
                provider == FileSystems.getDefault().provider(),
                "subprocess is not using the default provider instance");
        require(provider.getScheme().equals("file"), "provider scheme is not file");
        require(
                provider.getClass().getName().equals(WINDOWS_PROVIDER),
                "provider class is " + provider.getClass().getName());
        BasicFileAttributes attributes = Files.readAttributes(
                global,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        require(attributes.isDirectory(), "global is not a directory");
        require(!attributes.isSymbolicLink(), "global is a symlink");
        require(!attributes.isOther(), "global is a reparse point");
        require(attributes.fileKey() == null, "Windows global fileKey is non-null");
        FileStore store = Files.getFileStore(global);
        require(store.type().equals("NTFS"), "FileStore type is " + store.type());
        Object firstVsn = store.getAttribute("volume:vsn");
        Object secondVsn = Files.getFileStore(global)
                .getAttribute("volume:vsn");
        require(firstVsn != null, "volume:vsn is unavailable");
        require(firstVsn.equals(secondVsn), "volume:vsn is unstable");
    }

    private static Path normalizedAbsolute(String value, String description) {
        Path path = Path.of(value);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(
                    description + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static void writeNew(Path path, String value) throws IOException {
        Path stage = path.resolveSibling(
                path.getFileName()
                        + ".stage-"
                        + ProcessHandle.current().pid());
        Files.writeString(
                stage,
                value,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        Files.move(stage, path, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void requireAbsent(Path path) {
        require(
                !Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                "unauthorized migration artifact exists: " + path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean isUnc(Path path) {
        return path.toString().startsWith("\\\\");
    }
}
