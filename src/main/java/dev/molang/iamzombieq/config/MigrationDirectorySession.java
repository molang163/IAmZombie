package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

final class MigrationDirectorySession implements AutoCloseable {
    private final MigrationAccessProfile profile;
    private final MigrationBinding binding;
    private final Backend backend;
    private boolean closed;

    private MigrationDirectorySession(
            MigrationAccessProfile profile,
            MigrationBinding binding,
            Backend backend) {
        this.profile = profile;
        this.binding = binding;
        this.backend = backend;
    }

    static MigrationDirectorySession open(
            MigrationAccessProfile profile,
            MigrationBinding binding,
            Factory factory) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(factory, "factory");
        try {
            Backend backend = switch (profile) {
                case SECURE -> factory.openSecure(binding);
                case BASIC -> factory.openBasic(binding);
            };
            if (backend == null) {
                throw new IOException("Directory-session factory returned null");
            }
            return adopt(profile, binding, backend);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Could not open frozen " + profile + " directory session",
                    failure);
        }
    }

    static MigrationDirectorySession adopt(
            MigrationAccessProfile profile,
            MigrationBinding binding,
            Backend backend) {
        return new MigrationDirectorySession(
                Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(binding, "binding"),
                Objects.requireNonNull(backend, "backend"));
    }

    MigrationAccessProfile profile() {
        return profile;
    }

    MigrationBinding binding() {
        return binding;
    }

    MigrationPathState.Metadata readNofollowMetadata(String basename)
            throws IOException {
        ensureOpen();
        String operand = requireBasename(basename);
        return Objects.requireNonNull(
                backend.readNofollowMetadata(operand),
                "NOFOLLOW metadata");
    }

    byte[] readBoundContent(ContentKind kind, String basename) throws IOException {
        Objects.requireNonNull(kind, "kind");
        ensureOpen();
        String operand = requireBasename(basename);

        MigrationPathState.Metadata before =
                requireSafeRegular(backend.readNofollowMetadata(operand), operand);
        OpenedContent opened = Objects.requireNonNull(
                backend.openNofollow(operand), "opened content");
        MigrationPathState.Metadata after =
                requireSafeRegular(backend.readNofollowMetadata(operand), operand);
        if (!opened.regularFile()
                || !before.identity().equals(opened.identity())
                || before.size() != opened.bytes().length
                || !before.identity().equals(after.identity())
                || before.size() != after.size()
                || !opened.identity().equals(after.identity())) {
            throw new IllegalStateException(
                    "Observed leaf identity changed while reading "
                            + kind
                            + ": "
                            + operand);
        }
        return opened.bytes();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            backend.close();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Migration directory session is closed");
        }
    }

    private static MigrationPathState.Metadata requireSafeRegular(
            MigrationPathState.Metadata metadata, String basename) {
        if (metadata == null
                || !metadata.regularFile()
                || metadata.symbolicLink()
                || metadata.identity() == null
                || metadata.identity().isBlank()
                || metadata.size() < 0) {
            throw new IllegalStateException(
                    "Content leaf is absent, unsafe, or untrusted: " + basename);
        }
        return metadata;
    }

    static String requireBasename(String basename) {
        Objects.requireNonNull(basename, "basename");
        if (basename.isBlank()
                || basename.equals(".")
                || basename.equals("..")
                || basename.indexOf('/') >= 0
                || basename.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "Expected one non-empty relative basename: " + basename);
        }
        java.nio.file.Path operand = java.nio.file.Path.of(basename);
        if (operand.isAbsolute()
                || operand.getNameCount() != 1
                || !operand.getFileName().toString().equals(basename)) {
            throw new IllegalArgumentException(
                    "Expected one relative basename: " + basename);
        }
        return basename;
    }

    enum ContentKind {
        LEGACY,
        EXISTING_TARGET,
        LOCK,
        JOURNAL,
        BACKUP,
        INITIAL,
        TARGET,
        MARKER,
        STAGE
    }

    @FunctionalInterface
    interface Factory {
        Backend openSecure(MigrationBinding binding) throws IOException;

        default Backend openBasic(MigrationBinding binding) throws IOException {
            throw new IOException("BASIC migration access is unavailable");
        }
    }

    interface Backend extends AutoCloseable {
        MigrationPathState.Metadata readNofollowMetadata(String basename)
                throws IOException;

        OpenedContent openNofollow(String basename) throws IOException;

        @Override
        void close() throws IOException;
    }

    record OpenedContent(String identity, boolean regularFile, byte[] bytes) {
        OpenedContent {
            identity = Objects.requireNonNull(identity, "identity");
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OpenedContent that
                    && identity.equals(that.identity)
                    && regularFile == that.regularFile
                    && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(identity, regularFile);
            return 31 * result + Arrays.hashCode(bytes);
        }
    }
}
