package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

enum MigrationPathState {
    ABSENT,
    PRESENT,
    UNKNOWN,
    UNSAFE;

    static MigrationPathState classify(MetadataReader reader) {
        return observe(reader).state();
    }

    static Observation observe(MetadataReader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            Metadata metadata = reader.read();
            if (metadata == null) {
                return new Observation(
                        UNKNOWN, "metadata result was null", null);
            }
            if (metadata.symbolicLink()) {
                return new Observation(
                        UNSAFE, "path is a symbolic link", null);
            }
            if (!metadata.regularFile()) {
                return new Observation(
                        UNSAFE, "path is not a regular file", null);
            }
            if (metadata.identity() == null
                    || metadata.identity().isBlank()
                    || metadata.size() < 0) {
                return new Observation(
                        UNKNOWN, "metadata identity or size is untrusted", null);
            }
            return new Observation(PRESENT, "safe regular file", null);
        } catch (NoSuchFileException missing) {
            return new Observation(ABSENT, describe(missing), missing);
        } catch (IOException failure) {
            return new Observation(UNKNOWN, describe(failure), failure);
        } catch (RuntimeException failure) {
            return new Observation(UNKNOWN, describe(failure), failure);
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @FunctionalInterface
    interface MetadataReader {
        Metadata read() throws IOException;
    }

    record Metadata(boolean regularFile, boolean symbolicLink, String identity, long size) {}

    record Observation(MigrationPathState state, String detail, Throwable cause) {
        Observation {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) {
                throw new IllegalArgumentException(
                        "Metadata observation detail must not be blank");
            }
        }

        static Observation fromState(MigrationPathState state) {
            MigrationPathState checked =
                    Objects.requireNonNull(state, "state");
            return new Observation(
                    checked, "path state reported as " + checked, null);
        }
    }
}
