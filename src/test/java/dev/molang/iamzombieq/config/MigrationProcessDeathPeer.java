package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Optional;

/** Test-only subprocess that dies at one exact real-filesystem checkpoint. */
final class MigrationProcessDeathPeer {
    static final int LOCK_EXIT = 71;
    static final int TARGET_EXIT = 72;
    static final int PREPARED_EXIT = 73;

    private MigrationProcessDeathPeer() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "usage: MODE CASE_DIRECTORY EXPECTED_PARENT_PID");
        }
        Mode mode = Mode.valueOf(arguments[0]);
        Path directory = Path.of(arguments[1]).toAbsolutePath().normalize();
        long expectedParent = Long.parseLong(arguments[2]);
        long actualParent = ProcessHandle.current().parent()
                .orElseThrow()
                .pid();
        if (actualParent != expectedParent) {
            throw new IllegalStateException(
                    "unexpected parent PID " + actualParent);
        }

        Path legacy = directory.resolve("iamzombieq-common.toml");
        Path target = directory.resolve("iamzombieq-server.toml");
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        true);
        MigrationFaultInjector death = point -> {
            if (mode.matches(point)) {
                Runtime.getRuntime().halt(mode.exitCode);
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            new ConfigMigrationEngine(ConfigSchemaCatalog.load(), death)
                    .migrate(request, store);
        }
        throw new IllegalStateException(
                "process-death checkpoint was not reached: " + mode);
    }

    enum Mode {
        LOCK_AFTER_OS_ACQUIRE(LOCK_EXIT) {
            @Override
            boolean matches(MigrationFaultInjector.Point point) {
                return point.artifact() == null
                        && point.operation()
                                == MigrationFaultInjector.Operation.LOCK_TRY_LOCK
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER;
            }
        },
        TARGET_AFTER_ATOMIC_MOVE(TARGET_EXIT) {
            @Override
            boolean matches(MigrationFaultInjector.Point point) {
                return point.artifact() == AtomicConfigPublisher.Artifact.TARGET
                        && point.operation()
                                == MigrationFaultInjector.Operation.ATOMIC_MOVE
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER;
            }
        },
        PREPARED_AFTER_JOURNAL_MOVE(PREPARED_EXIT) {
            @Override
            boolean matches(MigrationFaultInjector.Point point) {
                return point.artifact()
                                == AtomicConfigPublisher.Artifact.JOURNAL
                        && point.operation()
                                == MigrationFaultInjector.Operation.ATOMIC_MOVE
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER
                        && point.phase()
                                == MigrationTargetState.Phase.PREPARED;
            }
        };

        private final int exitCode;

        Mode(int exitCode) {
            this.exitCode = exitCode;
        }

        int exitCode() {
            return exitCode;
        }

        abstract boolean matches(MigrationFaultInjector.Point point);
    }
}
