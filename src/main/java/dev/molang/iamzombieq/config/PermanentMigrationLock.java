package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

final class PermanentMigrationLock {
    private static final byte[] MAGIC =
            "IAMZOMBIEQ-LOCK\nversion=1\n".getBytes(StandardCharsets.UTF_8);

    private final Port port;
    private final MigrationFaultInjector faults;
    private final MigrationTargetState.Phase phase;

    PermanentMigrationLock(Port port) {
        this(
                port,
                MigrationFaultInjector.none(),
                MigrationTargetState.Phase.LOCKED);
    }

    PermanentMigrationLock(
            Port port,
            MigrationFaultInjector faults,
            MigrationTargetState.Phase phase) {
        this.port = Objects.requireNonNull(port, "port");
        this.faults = Objects.requireNonNull(faults, "faults");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    Acquisition acquire(Request request, UnderLockTargetGate targetGate) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(targetGate, "targetGate");
        try {
            if (request.hasExpectedIdentity()) {
                return validateExistingLock(request, targetGate);
            }
            checkpoint(
                    MigrationFaultInjector.Operation.LOCK_CREATE,
                    MigrationFaultInjector.Timing.BEFORE);
            boolean createdNow = port.createNew(request.basename());
            checkpoint(
                    MigrationFaultInjector.Operation.LOCK_CREATE,
                    MigrationFaultInjector.Timing.AFTER);
            if (createdNow) {
                return initializeCurrentCallLock(request, targetGate);
            }
            throw new IllegalStateException(
                    "Permanent migration lock appeared after it was observed absent");
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Permanent migration lock acquisition failed: "
                            + request.basename(),
                    failure);
        }
    }

    private Acquisition initializeCurrentCallLock(
            Request request, UnderLockTargetGate targetGate) throws IOException {
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Timing.BEFORE);
        requireAcquired(port.tryLock(request.basename(), true));
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Timing.BEFORE);
        String identity = requireIdentity(port.identity(request.basename()));
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Timing.AFTER);
        if (request.hasExpectedIdentity()) {
            requireExpectedIdentity(request, identity);
        }
        requireTargetAbsent(targetGate);

        byte[] initializedPayload =
                payloadWithIdentity(request.payload(), identity);
        checkpoint(
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.BEFORE);
        port.writePayload(request.basename(), initializedPayload);
        checkpoint(
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Timing.BEFORE);
        port.forceFile(request.basename());
        checkpoint(
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Timing.AFTER);
        MigrationEvidence.Durability durability =
                forceDirectoryIfRequired(request);
        verifyBound(request, identity, initializedPayload);
        return new Acquisition(
                identity,
                payloadSha256(initializedPayload),
                request.profile(),
                durability,
                true,
                false);
    }

    private Acquisition validateExistingLock(
            Request request, UnderLockTargetGate targetGate) throws IOException {
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_OPEN,
                MigrationFaultInjector.Timing.BEFORE);
        port.openExisting(request.basename());
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_OPEN,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Timing.BEFORE);
        requireAcquired(port.tryLock(request.basename(), false));
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Timing.BEFORE);
        String identity = requireIdentity(port.identity(request.basename()));
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Timing.AFTER);
        if (!request.hasExpectedIdentity()) {
            throw new IllegalStateException(
                    "Pre-existing permanent lock has no trusted inode identity");
        }
        requireExpectedIdentity(request, identity);

        checkpoint(
                MigrationFaultInjector.Operation.LOCK_READ,
                MigrationFaultInjector.Timing.BEFORE);
        byte[] actual = Objects.requireNonNull(
                port.readPayload(request.basename()), "existing lock payload");
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_READ,
                MigrationFaultInjector.Timing.AFTER);
        byte[] expectedPayload =
                payloadWithIdentity(request.payload(), identity);
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION,
                MigrationFaultInjector.Timing.BEFORE);
        if (actual.length == 0
                && request.allowEmptyFirstCreationRecovery()) {
            port.validatePayload(actual, actual);
            checkpoint(
                    MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION,
                    MigrationFaultInjector.Timing.AFTER);
            verifyBound(request, identity, actual);
            requireTargetAbsent(targetGate);
            verifyBound(request, identity, actual);
            return initializeExistingEmptyLock(request, identity);
        }
        if (!Arrays.equals(actual, expectedPayload)) {
            throw new IllegalStateException(
                    "Pre-existing permanent lock payload is not trusted");
        }
        port.validatePayload(actual, expectedPayload);
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION,
                MigrationFaultInjector.Timing.AFTER);
        requireTargetAbsent(targetGate);
        verifyBound(request, identity, actual);
        return new Acquisition(
                identity,
                payloadSha256(actual),
                request.profile(),
                MigrationEvidence.Durability.BASIC,
                false,
                false);
    }

    private Acquisition initializeExistingEmptyLock(
            Request request, String identity) throws IOException {
        byte[] initializedPayload =
                payloadWithIdentity(request.payload(), identity);
        checkpoint(
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.BEFORE);
        port.writePayload(request.basename(), initializedPayload);
        checkpoint(
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Timing.BEFORE);
        port.forceFile(request.basename());
        checkpoint(
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Timing.AFTER);
        MigrationEvidence.Durability durability =
                forceDirectoryIfRequired(request);
        verifyBound(request, identity, initializedPayload);
        return new Acquisition(
                identity,
                payloadSha256(initializedPayload),
                request.profile(),
                durability,
                false,
                true);
    }

    private void verifyBound(
            Request request, String identity, byte[] payload)
            throws IOException {
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_VALIDATE,
                MigrationFaultInjector.Timing.BEFORE);
        port.verifyBound(
                request.basename(),
                identity,
                payloadSha256(payload));
        checkpoint(
                MigrationFaultInjector.Operation.LOCK_VALIDATE,
                MigrationFaultInjector.Timing.AFTER);
    }

    private MigrationEvidence.Durability forceDirectoryIfRequired(
            Request request) throws IOException {
        if (!request.strongRequired()) {
            return MigrationEvidence.Durability.BASIC;
        }
        checkpoint(
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY,
                MigrationFaultInjector.Timing.BEFORE);
        port.forceDirectory();
        checkpoint(
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY,
                MigrationFaultInjector.Timing.AFTER);
        return MigrationEvidence.Durability.STRONG;
    }

    private void checkpoint(
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing) {
        faults.inject(new MigrationFaultInjector.Point(
                phase, null, operation, timing));
    }

    private static void requireAcquired(TryLockResult result) {
        if (result != TryLockResult.ACQUIRED) {
            throw new IllegalStateException(
                    "Permanent migration lock is contended or untrusted");
        }
    }

    private static String requireIdentity(String identity) {
        Objects.requireNonNull(identity, "lock identity");
        if (identity.isBlank()) {
            throw new IllegalStateException(
                    "Permanent migration lock identity is untrusted");
        }
        return identity;
    }

    private static void requireExpectedIdentity(
            Request request, String identity) {
        if (!request.expectedIdentity().equals(identity)) {
            throw new IllegalStateException(
                    "Permanent migration lock identity changed");
        }
    }

    private static void requireTargetAbsent(UnderLockTargetGate targetGate) {
        if (!targetGate.targetRemainsAbsent()) {
            throw new IllegalStateException(
                    "Target appeared while the permanent migration lock was held");
        }
    }

    enum TryLockResult {
        ACQUIRED,
        CONTENDED
    }

    record Request(
            String basename,
            byte[] payload,
            String expectedIdentity,
            MigrationAccessProfile profile,
            boolean strongRequired,
            boolean allowEmptyFirstCreationRecovery) {

        Request {
            basename = MigrationDirectorySession.requireBasename(basename);
            payload = Objects.requireNonNull(payload, "payload").clone();
            if (!startsWith(payload, MAGIC)) {
                throw new IllegalArgumentException(
                        "Permanent lock payload has invalid magic or version");
            }
            if (new String(payload, StandardCharsets.UTF_8)
                    .contains("\nlockIdentity=")) {
                throw new IllegalArgumentException(
                        "Base lock payload must not contain an inode identity");
            }
            expectedIdentity =
                    Objects.requireNonNull(expectedIdentity, "expectedIdentity");
            if (!expectedIdentity.isEmpty() && expectedIdentity.isBlank()) {
                throw new IllegalArgumentException(
                        "Expected lock identity must be empty or non-blank");
            }
            Objects.requireNonNull(profile, "profile");
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        Request withStrongRequired(boolean value) {
            return new Request(
                    basename,
                    payload,
                    expectedIdentity,
                    profile,
                    value,
                    allowEmptyFirstCreationRecovery);
        }

        Request withEmptyFirstCreationRecovery(boolean value) {
            return new Request(
                    basename,
                    payload,
                    expectedIdentity,
                    profile,
                    strongRequired,
                    value);
        }

        boolean hasExpectedIdentity() {
            return !expectedIdentity.isEmpty();
        }
    }

    record Acquisition(
            String identity,
            String payloadSha256,
            MigrationAccessProfile profile,
            MigrationEvidence.Durability durability,
            boolean createdNow,
            boolean recoveredEmptyFirstCreation) {
        Acquisition {
            identity = requireIdentity(identity);
            payloadSha256 = requireSha(payloadSha256);
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(durability, "durability");
        }
    }

    @FunctionalInterface
    interface UnderLockTargetGate {
        boolean targetRemainsAbsent();
    }

    interface Port {
        boolean createNew(String basename) throws IOException;

        void openExisting(String basename) throws IOException;

        TryLockResult tryLock(String basename, boolean currentCall)
                throws IOException;

        byte[] readPayload(String basename) throws IOException;

        String identity(String basename) throws IOException;

        void validatePayload(byte[] actual, byte[] expected) throws IOException;

        void verifyBound(
                String basename,
                String expectedIdentity,
                String expectedPayloadSha256)
                throws IOException;

        void writePayload(String basename, byte[] bytes) throws IOException;

        void forceFile(String basename) throws IOException;

        void forceDirectory() throws IOException;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    static byte[] payloadWithIdentity(byte[] basePayload, String identity) {
        Objects.requireNonNull(basePayload, "basePayload");
        String checkedIdentity = requireIdentity(identity);
        if (checkedIdentity.indexOf('\n') >= 0
                || checkedIdentity.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Permanent lock identity contains a line break");
        }
        if (basePayload.length == 0
                || basePayload[basePayload.length - 1] != '\n') {
            throw new IllegalArgumentException(
                    "Base lock payload must end with LF");
        }
        byte[] suffix = ("lockIdentity=" + checkedIdentity + "\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(
                basePayload, basePayload.length + suffix.length);
        System.arraycopy(
                suffix, 0, result, basePayload.length, suffix.length);
        return result;
    }

    static String payloadSha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static String requireSha(String value) {
        Objects.requireNonNull(value, "payloadSha256");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Lock payload SHA must be lowercase SHA-256");
        }
        return value;
    }
}
