package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Child-process entry point for the real Windows migration crash matrix.
 *
 * <p>The parent test launches this class with the current test worker's output and the
 * production classes on the class path. The child deliberately uses the real
 * {@link ConfigMigrationEngine} and {@link JdkMigrationFileSystem}; the only
 * test seam is the production fault checkpoint that terminates the OS process.
 */
final class WindowsMigrationCrashProcessMain {
    static final int CRASH_EXIT_CODE = 197;

    private WindowsMigrationCrashProcessMain() {}

    public static void main(String[] arguments) {
        try {
            run(arguments);
            System.err.println(
                    "Migration completed without reaching the armed crash checkpoint");
            System.exit(3);
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void run(String[] arguments) throws Exception {
        if (arguments.length != 5) {
            throw new IllegalArgumentException(
                    "Expected legacy, target, artifact, timing, and occurrence arguments");
        }
        if (!System.getProperty("os.name", "unknown").startsWith("Windows")) {
            throw new IllegalStateException("Crash child requires Windows");
        }
        int javaFeature = Runtime.version().feature();
        if (!MigrationJavaRuntimeMatrix.supportsBasicProfile(javaFeature)) {
            throw new IllegalStateException(
                    "Crash child requires a BASIC runtime approved by this "
                            + "Stonecutter node; actual feature="
                            + javaFeature
                            + ", approved="
                            + MigrationJavaRuntimeMatrix.runtimeFeatures());
        }
        if (!"1".equals(System.getenv("IAMZOMBIEQ_WINDOWS_GATE_ARMED"))) {
            throw new IllegalStateException(
                    "Crash child requires the armed disposable Windows gate");
        }
        if (!"1".equals(System.getenv(
                "IAMZOMBIEQ_WINDOWS_PROCESS_DEATH_ARMED"))) {
            throw new IllegalStateException(
                    "Crash child requires the explicitly armed process-death gate");
        }

        Path legacy = normalizedAbsolute(arguments[0], "legacy");
        Path target = normalizedAbsolute(arguments[1], "target");
        AtomicConfigPublisher.Artifact crashArtifact =
                AtomicConfigPublisher.Artifact.valueOf(arguments[2]);
        MigrationFaultInjector.Timing crashTiming =
                MigrationFaultInjector.Timing.valueOf(arguments[3]);
        int requestedOccurrence = Integer.parseInt(arguments[4]);
        if (requestedOccurrence < 1) {
            throw new IllegalArgumentException("occurrence must be positive");
        }

        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        if (profile != MigrationAccessProfile.BASIC) {
            throw new IllegalStateException(
                    "Crash child requires the production Windows BASIC path, found "
                            + profile);
        }

        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.PREFERENCES,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        AtomicInteger matchingCheckpoints = new AtomicInteger();
        MigrationFaultInjector crash = point -> {
            if (point.artifact() == crashArtifact
                    && point.operation()
                            == MigrationFaultInjector.Operation.ATOMIC_MOVE
                    && point.timing() == crashTiming
                    && matchingCheckpoints.incrementAndGet()
                            == requestedOccurrence) {
                System.err.println(
                        "HALT "
                                + crashArtifact
                                + " "
                                + crashTiming
                                + " occurrence="
                                + requestedOccurrence);
                System.err.flush();
                Runtime.getRuntime().halt(CRASH_EXIT_CODE);
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            new ConfigMigrationEngine(ConfigSchemaCatalog.load(), crash)
                    .migrate(request, store);
        }
    }

    private static Path normalizedAbsolute(String value, String field) {
        Path path = Path.of(value);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(
                    field + " must be normalized and absolute: " + value);
        }
        return path;
    }
}
