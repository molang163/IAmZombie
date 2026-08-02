package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermanentMigrationLockTest {
    private static final byte[] PAYLOAD =
            "IAMZOMBIEQ-LOCK\nversion=1\ntarget=/config/iamzombieq-server.toml\n"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] INITIALIZED_PAYLOAD =
            PermanentMigrationLock.payloadWithIdentity(
                    PAYLOAD, "dev:7:ino:11");

    @Test
    void firstCreateNewWritesMagicPayloadForcesAndKeepsPermanentInode() {
        RecordingPort port = new RecordingPort();
        PermanentMigrationLock.Acquisition acquisition =
                new PermanentMigrationLock(port).acquire(newRequest(), () -> true);

        assertEquals("dev:7:ino:11", acquisition.identity());
        assertArrayEquals(INITIALIZED_PAYLOAD, port.payload);
        assertEquals(
                List.of(
                        "create-new",
                        "try-lock-current",
                        "identity",
                        "write-payload",
                        "force-file",
                        "verify-bound"),
                port.events);
        assertFalse(port.events.contains("unlink"));
        assertFalse(port.events.contains("replace"));
    }

    @Test
    void initializedPayloadBindsThePermanentInodeIdentity() {
        RecordingPort port = new RecordingPort();

        new PermanentMigrationLock(port).acquire(newRequest(), () -> true);

        String payload = new String(port.payload, StandardCharsets.UTF_8);
        assertTrue(payload.startsWith(
                new String(PAYLOAD, StandardCharsets.UTF_8)));
        assertTrue(payload.endsWith("lockIdentity=dev:7:ino:11\n"));
    }

    @Test
    void faultAtCreateLeavesNoArtifact() {
        RecordingPort port = new RecordingPort();
        port.fault = Fault.CREATE;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));
        assertFalse(port.lockExists);
        assertEquals(List.of("create-new"), port.events);
    }

    @Test
    void faultAtCurrentCallLockAcquisitionLeavesUninitializedPermanentLock() {
        RecordingPort port = new RecordingPort();
        port.fault = Fault.CURRENT_TRY_LOCK;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));
        assertTrue(port.lockExists);
        assertEquals(0, port.payload.length);
        assertFalse(port.events.contains("write-payload"));
    }

    @Test
    void faultAfterCreateLeavesPermanentUninitializedLock() {
        RecordingPort port = new RecordingPort();
        port.fault = Fault.WRITE;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));
        assertTrue(port.lockExists);
        assertFalse(port.events.contains("unlink"));
        assertFalse(port.events.contains("replace"));

        port.fault = Fault.NONE;
        port.newlyCreated = false;
        port.events.clear();
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));
        assertFalse(port.events.contains("repair"));
    }

    @Test
    void eligibleExistingEmptyLockInitializesSameInodeWithoutCreateOrReplace() {
        RecordingPort port = RecordingPort.validExisting();
        port.payload = new byte[0];

        PermanentMigrationLock.Acquisition acquisition =
                new PermanentMigrationLock(port).acquire(
                        request().withEmptyFirstCreationRecovery(true),
                        () -> true);

        assertEquals("dev:7:ino:11", acquisition.identity());
        assertFalse(acquisition.createdNow());
        assertTrue(acquisition.recoveredEmptyFirstCreation());
        assertArrayEquals(INITIALIZED_PAYLOAD, port.payload);
        assertEquals(
                List.of(
                        "open-existing",
                        "try-lock-existing",
                        "identity",
                        "read-payload",
                        "validate-payload",
                        "verify-bound",
                        "verify-bound",
                        "write-payload",
                        "force-file",
                        "verify-bound"),
                port.events);
        assertFalse(port.events.contains("create-new"));
        assertFalse(port.events.contains("unlink"));
        assertFalse(port.events.contains("replace"));
    }

    @Test
    void emptyLockRecoveryGateFailureLeavesSameZeroLengthInodeUntouched() {
        RecordingPort port = RecordingPort.validExisting();
        port.payload = new byte[0];
        String identity = port.identity;

        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port).acquire(
                        request().withEmptyFirstCreationRecovery(true),
                        () -> false));

        assertEquals(identity, port.identity);
        assertEquals(0, port.payload.length);
        assertFalse(port.events.contains("write-payload"));
        assertFalse(port.events.contains("force-file"));
        assertFalse(port.events.contains("create-new"));
    }

    @Test
    void emptyLockWriteAndForceFaultsNeverFallbackOrReplaceInode() {
        for (Fault fault : new Fault[] {Fault.WRITE, Fault.FORCE}) {
            RecordingPort port = RecordingPort.validExisting();
            port.payload = new byte[0];
            String identity = port.identity;
            port.fault = fault;

            assertThrows(
                    IllegalStateException.class,
                    () -> new PermanentMigrationLock(port).acquire(
                            request().withEmptyFirstCreationRecovery(true),
                            () -> true),
                    fault.name());

            assertEquals(identity, port.identity, fault.name());
            assertFalse(port.events.contains("create-new"), fault.name());
            assertFalse(port.events.contains("unlink"), fault.name());
            assertFalse(port.events.contains("replace"), fault.name());
            assertFalse(port.events.contains("basic-fallback"), fault.name());
        }
    }

    @Test
    void strongEmptyLockRecoveryForcesFileAndDirectoryOnSameInode() {
        RecordingPort port = RecordingPort.validExisting();
        port.payload = new byte[0];

        PermanentMigrationLock.Acquisition acquisition =
                new PermanentMigrationLock(port).acquire(
                        request()
                                .withStrongRequired(true)
                                .withEmptyFirstCreationRecovery(true),
                        () -> true);

        assertEquals(
                MigrationEvidence.Durability.STRONG,
                acquisition.durability());
        assertTrue(acquisition.recoveredEmptyFirstCreation());
        assertTrue(port.events.contains("force-file"));
        assertTrue(port.events.contains("force-directory"));
        assertFalse(port.events.contains("create-new"));
    }

    @Test
    void faultAtForceLeavesPermanentUntrustedLock() {
        RecordingPort port = new RecordingPort();
        port.fault = Fault.FORCE;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));
        assertTrue(port.lockExists);
        assertArrayEquals(INITIALIZED_PAYLOAD, port.payload);
        assertFalse(port.events.contains("unlink"));
    }

    @Test
    void pathnameIdentitySwapAfterForceBlocksAcquisition() {
        RecordingPort port = new RecordingPort();
        port.swapIdentityAfterForce = true;

        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> true));

        assertTrue(port.lockExists);
        assertArrayEquals(INITIALIZED_PAYLOAD, port.payload);
        assertEquals("dev:7:ino:99", port.identity);
        assertFalse(port.events.contains("unlink"));
        assertFalse(port.events.contains("replace"));
    }

    @Test
    void faultAtStrongDirectoryDurabilityBlocks() {
        RecordingPort port = new RecordingPort();
        port.fault = Fault.DIRECTORY_FORCE;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(
                                newRequest().withStrongRequired(true),
                                () -> true));
        assertTrue(port.lockExists);
        assertFalse(port.events.contains("basic-fallback"));
    }

    @Test
    void existingLockOpenFailureNeverRepairs() {
        assertExistingFaultNeverRepairs(Fault.OPEN);
    }

    @Test
    void existingLockReadFailureNeverRepairs() {
        assertExistingFaultNeverRepairs(Fault.READ);
    }

    @Test
    void existingLockTryLockExceptionNeverRepairs() {
        assertExistingFaultNeverRepairs(Fault.EXISTING_TRY_LOCK);
    }

    @Test
    void existingLockIdentityRevalidationFailureNeverRepairs() {
        assertExistingFaultNeverRepairs(Fault.IDENTITY);
    }

    @Test
    void existingLockPayloadRevalidationFailureNeverRepairs() {
        assertExistingFaultNeverRepairs(Fault.PAYLOAD);
    }

    @Test
    void observedExistingLockNeverFallsBackToCreateNewWhenItDisappears() {
        RecordingPort port = RecordingPort.validExisting();
        port.lockExists = false;
        port.newlyCreated = true;

        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(request(), () -> true));

        assertEquals(List.of("open-existing"), port.events);
        assertFalse(port.lockExists);
        assertFalse(port.events.contains("create-new"));
        assertFalse(port.events.contains("write-payload"));
    }

    @Test
    void lockFaultsNeverRepairOrInitializeExistingInode() {
        for (Fault fault : new Fault[] {
            Fault.OPEN,
            Fault.READ,
            Fault.EXISTING_TRY_LOCK,
            Fault.IDENTITY,
            Fault.PAYLOAD
        }) {
            RecordingPort port = RecordingPort.validExisting();
            byte[] before = port.payload.clone();
            port.fault = fault;
            assertThrows(
                    IllegalStateException.class,
                    () -> new PermanentMigrationLock(port).acquire(request(), () -> true),
                    () -> "accepted existing-lock fault " + fault);
            assertArrayEquals(before, port.payload);
            assertFalse(port.events.contains("write-payload"));
            assertFalse(port.events.contains("repair"));
            assertFalse(port.events.contains("unlink"));
            assertFalse(port.events.contains("replace"));
        }
    }

    @Test
    void preExistingEmptyPartialCorruptAndIdentityMismatchFailClosed() {
        RecordingPort missing = new RecordingPort();
        missing.newlyCreated = false;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(missing)
                        .acquire(request(), () -> true));
        assertFalse(missing.events.contains("write-payload"));

        for (byte[] invalid : new byte[][] {
            new byte[0],
            "IAM".getBytes(StandardCharsets.UTF_8),
            "wrong payload".getBytes(StandardCharsets.UTF_8)
        }) {
            RecordingPort port = RecordingPort.validExisting();
            port.payload = invalid.clone();
            assertThrows(
                    IllegalStateException.class,
                    () -> new PermanentMigrationLock(port).acquire(request(), () -> true));
            assertArrayEquals(invalid, port.payload);
            assertFalse(port.events.contains("write-payload"));
        }

        RecordingPort mismatched = RecordingPort.validExisting();
        mismatched.identity = "dev:7:ino:99";
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(mismatched)
                        .acquire(request(), () -> true));
    }

    @Test
    void recoveryPermissionNeverRepairsNonzeroPartialOrCorruptPayload() {
        for (byte[] invalid : new byte[][] {
            "I".getBytes(StandardCharsets.UTF_8),
            "IAMZOMBIEQ-LOCK\nversion=1\npartial"
                    .getBytes(StandardCharsets.UTF_8),
            "wrong payload".getBytes(StandardCharsets.UTF_8)
        }) {
            RecordingPort port = RecordingPort.validExisting();
            port.payload = invalid.clone();
            String identity = port.identity;

            assertThrows(
                    IllegalStateException.class,
                    () -> new PermanentMigrationLock(port).acquire(
                            request().withEmptyFirstCreationRecovery(true),
                            () -> true));

            assertEquals(identity, port.identity);
            assertArrayEquals(invalid, port.payload);
            assertFalse(port.events.contains("write-payload"));
            assertFalse(port.events.contains("force-file"));
            assertFalse(port.events.contains("create-new"));
            assertFalse(port.events.contains("unlink"));
            assertFalse(port.events.contains("replace"));
        }
    }

    @Test
    void sameInodeContentionUsesNonBlockingTryLock() {
        for (PermanentMigrationLock.TryLockResult contention
                : new PermanentMigrationLock.TryLockResult[] {
                    PermanentMigrationLock.TryLockResult.CONTENDED, null
                }) {
            RecordingPort port = RecordingPort.validExisting();
            port.tryLockResult = contention;
            assertThrows(
                    IllegalStateException.class,
                    () -> new PermanentMigrationLock(port)
                            .acquire(request(), () -> true));
            assertEquals(1, count(port.events, "try-lock-existing"));
            assertEquals(0, count(port.events, "blocking-lock"));
            assertArrayEquals(INITIALIZED_PAYLOAD, port.payload);
            assertFalse(port.events.contains("repair"));
        }
    }

    @Test
    void targetAppearingBeforeUnderLockCheckLeavesOnlyPermanentLockAndFailsClosed() {
        RecordingPort port = new RecordingPort();
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port)
                        .acquire(newRequest(), () -> false));
        assertTrue(port.lockExists);
        assertEquals(0, port.payload.length);
        assertFalse(port.events.contains("write-payload"));
        assertFalse(port.events.contains("force-file"));
    }

    @Test
    void injectorExposesEveryPermanentLockBoundaryAsSynthetic() {
        List<MigrationFaultInjector.Operation> currentCall = List.of(
                MigrationFaultInjector.Operation.LOCK_CREATE,
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Operation.LOCK_VALIDATE,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY);
        List<MigrationFaultInjector.Operation> existing = List.of(
                MigrationFaultInjector.Operation.LOCK_OPEN,
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Operation.LOCK_READ,
                MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION,
                MigrationFaultInjector.Operation.LOCK_VALIDATE);

        for (MigrationFaultInjector.Operation operation : currentCall) {
            assertSyntheticLockPoint(operation, false);
        }
        for (MigrationFaultInjector.Operation operation : existing) {
            assertSyntheticLockPoint(operation, true);
        }
    }

    @Test
    void injectorExposesEveryEmptyLockRecoveryBoundaryAsSynthetic() {
        List<MigrationFaultInjector.Operation> operations = List.of(
                MigrationFaultInjector.Operation.LOCK_OPEN,
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Operation.LOCK_READ,
                MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION,
                MigrationFaultInjector.Operation.LOCK_VALIDATE,
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY);

        for (MigrationFaultInjector.Operation operation : operations) {
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                RecordingPort port = RecordingPort.validExisting();
                port.payload = new byte[0];
                MigrationFaultInjector injector = point -> {
                    if (point.operation() == operation
                            && point.timing() == timing) {
                        throw new IllegalStateException(
                                "synthetic empty recovery " + operation);
                    }
                };
                PermanentMigrationLock.Request recoveryRequest =
                        request().withEmptyFirstCreationRecovery(true);
                if (operation
                        == MigrationFaultInjector.Operation
                                .DIRECTORY_DURABILITY) {
                    recoveryRequest =
                            recoveryRequest.withStrongRequired(true);
                }
                PermanentMigrationLock.Request checkedRequest =
                        recoveryRequest;

                MigrationFaultInjector.SyntheticFault failure =
                        assertThrows(
                                MigrationFaultInjector.SyntheticFault.class,
                                () -> new PermanentMigrationLock(
                                                port,
                                                injector,
                                                MigrationTargetState.Phase
                                                        .LOCKED)
                                        .acquire(
                                                checkedRequest,
                                                () -> true),
                                operation + "/" + timing);

                assertEquals(
                        operation,
                        failure.point().operation(),
                        operation + "/" + timing);
                assertEquals(
                        timing,
                        failure.point().timing(),
                        operation + "/" + timing);
                assertFalse(
                        port.events.contains("create-new"),
                        operation + "/" + timing);
                assertFalse(
                        port.events.contains("unlink"),
                        operation + "/" + timing);
                assertFalse(
                        port.events.contains("replace"),
                        operation + "/" + timing);
                assertFalse(
                        port.events.contains("basic-fallback"),
                        operation + "/" + timing);
            }
        }
    }

    private static void assertSyntheticLockPoint(
            MigrationFaultInjector.Operation operation, boolean existing) {
        RecordingPort port =
                existing ? RecordingPort.validExisting() : new RecordingPort();
        MigrationFaultInjector injector = point -> {
            if (point.operation() == operation
                    && point.timing() == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException("synthetic " + operation);
            }
        };
        PermanentMigrationLock.Request baseRequest =
                existing ? request() : newRequest();
        PermanentMigrationLock.Request request = operation
                        == MigrationFaultInjector.Operation.DIRECTORY_DURABILITY
                ? baseRequest.withStrongRequired(true)
                : baseRequest;

        MigrationFaultInjector.SyntheticFault failure = assertThrows(
                MigrationFaultInjector.SyntheticFault.class,
                () -> new PermanentMigrationLock(
                                port,
                                injector,
                                MigrationTargetState.Phase.LOCKED)
                        .acquire(request, () -> true));

        assertEquals(operation, failure.point().operation());
        assertEquals(
                MigrationTargetState.Phase.LOCKED,
                failure.point().phase());
        assertTrue(failure.getMessage().contains("synthetic"));
    }

    private static PermanentMigrationLock.Request request() {
        return new PermanentMigrationLock.Request(
                "iamzombieq-server.toml.iamzombieq-migration-v1.lock",
                PAYLOAD,
                "dev:7:ino:11",
                MigrationAccessProfile.SECURE,
                false,
                false);
    }

    private static PermanentMigrationLock.Request newRequest() {
        return new PermanentMigrationLock.Request(
                "iamzombieq-server.toml.iamzombieq-migration-v1.lock",
                PAYLOAD,
                "",
                MigrationAccessProfile.SECURE,
                false,
                false);
    }

    private static void assertExistingFaultNeverRepairs(Fault fault) {
        RecordingPort port = RecordingPort.validExisting();
        byte[] before = port.payload.clone();
        port.fault = fault;
        assertThrows(
                IllegalStateException.class,
                () -> new PermanentMigrationLock(port).acquire(request(), () -> true));
        assertArrayEquals(before, port.payload);
        assertFalse(port.events.contains("write-payload"));
        assertFalse(port.events.contains("repair"));
        assertFalse(port.events.contains("unlink"));
        assertFalse(port.events.contains("replace"));
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private enum Fault {
        NONE,
        CREATE,
        CURRENT_TRY_LOCK,
        WRITE,
        FORCE,
        DIRECTORY_FORCE,
        OPEN,
        READ,
        EXISTING_TRY_LOCK,
        IDENTITY,
        PAYLOAD
    }

    private static final class RecordingPort implements PermanentMigrationLock.Port {
        private final List<String> events = new ArrayList<>();
        private boolean newlyCreated = true;
        private boolean lockExists;
        private byte[] payload = new byte[0];
        private String identity = "dev:7:ino:11";
        private Fault fault = Fault.NONE;
        private boolean swapIdentityAfterForce;
        private PermanentMigrationLock.TryLockResult tryLockResult =
                PermanentMigrationLock.TryLockResult.ACQUIRED;

        static RecordingPort validExisting() {
            RecordingPort port = new RecordingPort();
            port.newlyCreated = false;
            port.lockExists = true;
            port.payload = INITIALIZED_PAYLOAD.clone();
            return port;
        }

        @Override
        public boolean createNew(String basename) throws IOException {
            events.add("create-new");
            fail(Fault.CREATE);
            if (newlyCreated) {
                lockExists = true;
            }
            return newlyCreated;
        }

        @Override
        public void openExisting(String basename) throws IOException {
            events.add("open-existing");
            fail(Fault.OPEN);
            if (!lockExists) {
                throw new IOException("missing lock");
            }
        }

        @Override
        public PermanentMigrationLock.TryLockResult tryLock(
                String basename, boolean currentCall) throws IOException {
            events.add(currentCall ? "try-lock-current" : "try-lock-existing");
            fail(currentCall ? Fault.CURRENT_TRY_LOCK : Fault.EXISTING_TRY_LOCK);
            return tryLockResult;
        }

        @Override
        public byte[] readPayload(String basename) throws IOException {
            events.add("read-payload");
            fail(Fault.READ);
            return payload.clone();
        }

        @Override
        public String identity(String basename) throws IOException {
            events.add("identity");
            fail(Fault.IDENTITY);
            return identity;
        }

        @Override
        public void validatePayload(byte[] actual, byte[] expected) throws IOException {
            events.add("validate-payload");
            fail(Fault.PAYLOAD);
            if (!java.util.Arrays.equals(actual, expected)) {
                throw new IOException("payload mismatch");
            }
        }

        @Override
        public void verifyBound(
                String basename,
                String expectedIdentity,
                String expectedPayloadSha256)
                throws IOException {
            events.add("verify-bound");
            if (!expectedIdentity.equals(identity)) {
                throw new IOException("lock identity mismatch");
            }
            if (!expectedPayloadSha256.equals(
                    PermanentMigrationLock.payloadSha256(payload))) {
                throw new IOException("lock payload mismatch");
            }
        }

        @Override
        public void writePayload(String basename, byte[] bytes) throws IOException {
            events.add("write-payload");
            fail(Fault.WRITE);
            payload = bytes.clone();
        }

        @Override
        public void forceFile(String basename) throws IOException {
            events.add("force-file");
            fail(Fault.FORCE);
            if (swapIdentityAfterForce) {
                identity = "dev:7:ino:99";
            }
        }

        @Override
        public void forceDirectory() throws IOException {
            events.add("force-directory");
            fail(Fault.DIRECTORY_FORCE);
        }

        private void fail(Fault at) throws IOException {
            if (fault == at) {
                throw new IOException("fault at " + at);
            }
        }
    }
}
