package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;

class MigrationPathStateTest {
    @Test
    void nofollowClassifierDistinguishesAbsentPresentUnknownAndUnsafe() {
        assertEquals(
                MigrationPathState.ABSENT,
                classifyFailure(new NoSuchFileException("missing")));
        assertEquals(
                MigrationPathState.UNKNOWN,
                classifyFailure(new AccessDeniedException("denied")));
        assertEquals(
                MigrationPathState.UNKNOWN,
                classifyFailure(new IOException("generic I/O")));

        assertEquals(
                MigrationPathState.PRESENT,
                MigrationPathState.classify(
                        () -> new MigrationPathState.Metadata(true, false, "file-1", 7)));
        assertEquals(
                MigrationPathState.UNSAFE,
                MigrationPathState.classify(
                        () -> new MigrationPathState.Metadata(false, true, "link-1", 0)));
        assertEquals(
                MigrationPathState.UNSAFE,
                MigrationPathState.classify(
                        () -> new MigrationPathState.Metadata(false, false, "directory-1", 0)));
        assertEquals(
                MigrationPathState.UNKNOWN,
                MigrationPathState.classify(
                        () -> new MigrationPathState.Metadata(true, false, "", 7)));
        assertEquals(
                MigrationPathState.UNKNOWN,
                MigrationPathState.classify(
                        () -> new MigrationPathState.Metadata(true, false, null, 7)));
    }

    @Test
    void detailedObservationPreservesConcreteMetadataFailure() {
        MigrationPathState.Observation denied =
                MigrationPathState.observe(() -> {
                    throw new AccessDeniedException("denied");
                });
        MigrationPathState.Observation generic =
                MigrationPathState.observe(() -> {
                    throw new IOException("generic I/O");
                });

        assertEquals(MigrationPathState.UNKNOWN, denied.state());
        assertTrue(denied.detail().contains("AccessDeniedException"));
        assertTrue(denied.detail().contains("denied"));
        assertInstanceOf(AccessDeniedException.class, denied.cause());
        assertEquals(MigrationPathState.UNKNOWN, generic.state());
        assertTrue(generic.detail().contains("generic I/O"));
        assertInstanceOf(IOException.class, generic.cause());
    }

    private static MigrationPathState classifyFailure(IOException failure) {
        return MigrationPathState.classify(() -> {
            throw failure;
        });
    }
}
