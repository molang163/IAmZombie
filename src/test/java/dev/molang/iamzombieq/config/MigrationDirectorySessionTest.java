package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MigrationDirectorySessionTest {
    @Test
    void rawNofollowMetadataUsesTheAlreadyOpenedRelativeBackend()
            throws IOException {
        RecordingFactory factory = new RecordingFactory();
        try (MigrationDirectorySession session = MigrationDirectorySession.open(
                MigrationAccessProfile.SECURE,
                MigrationBinding.capture(
                        MigrationBindingTest.observation("dir-1", "store-1")),
                factory)) {
            assertEquals(
                    new MigrationPathState.Metadata(
                            true, false, "leaf-1", 7),
                    session.readNofollowMetadata(
                            "iamzombieq-server.toml"));
            assertEquals(0, factory.backend.nofollowOpens);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> session.readNofollowMetadata("../escape"));
        }
        assertEquals(1, factory.secureOpens.get());
        assertEquals(0, factory.basicOpens.get());
        assertEquals(1, factory.backend.closeCount);
    }

    @Test
    void secureUsesOneOpenSessionAndValidatedSingleBasenames() throws IOException {
        RecordingFactory factory = new RecordingFactory();
        try (MigrationDirectorySession session = MigrationDirectorySession.open(
                MigrationAccessProfile.SECURE,
                MigrationBinding.capture(MigrationBindingTest.observation("dir-1", "store-1")),
                factory)) {
            assertArrayEquals(
                    "payload".getBytes(StandardCharsets.UTF_8),
                    session.readBoundContent(
                            MigrationDirectorySession.ContentKind.LEGACY,
                            "iamzombieq-server.toml"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> session.readBoundContent(
                            MigrationDirectorySession.ContentKind.LEGACY, "../escape"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> session.readBoundContent(
                            MigrationDirectorySession.ContentKind.LEGACY, "nested/file"));
        }
        assertEquals(1, factory.secureOpens.get());
        assertEquals(0, factory.basicOpens.get());
        assertEquals(1, factory.backend.closeCount);
    }

    @Test
    void secureFailureNeverFallsBackToBasic() {
        RecordingFactory factory = new RecordingFactory();
        factory.secureFailure = new IOException("secure open failed");
        assertThrows(
                IllegalStateException.class,
                () -> MigrationDirectorySession.open(
                        MigrationAccessProfile.SECURE,
                        MigrationBinding.capture(
                                MigrationBindingTest.observation("dir-1", "store-1")),
                        factory));
        assertEquals(1, factory.secureOpens.get());
        assertEquals(0, factory.basicOpens.get());
    }

    @Test
    void contentOpenRejectsLeafSymlinkSwap() throws IOException {
        for (MigrationAccessProfile profile : MigrationAccessProfile.values()) {
            for (MigrationDirectorySession.ContentKind kind
                    : MigrationDirectorySession.ContentKind.values()) {
                RecordingFactory factory = new RecordingFactory();
                factory.backend.metadata.clear();
                factory.backend.metadata.add(
                        new MigrationPathState.Metadata(true, false, "leaf-1", 7));
                factory.backend.metadata.add(
                        new MigrationPathState.Metadata(false, true, "link-2", 7));
                try (MigrationDirectorySession session = MigrationDirectorySession.open(
                        profile,
                        MigrationBinding.capture(
                                MigrationBindingTest.observation("dir-1", "store-1")),
                        factory)) {
                    assertThrows(
                            IllegalStateException.class,
                            () -> session.readBoundContent(
                                    kind, "iamzombieq-server.toml"));
                }
                assertEquals(1, factory.backend.nofollowOpens);
            }
        }
    }

    @Test
    void contentOpenBindsClassifiedLeafIdentity() throws IOException {
        for (MigrationAccessProfile profile : MigrationAccessProfile.values()) {
            for (MigrationDirectorySession.ContentKind kind
                    : MigrationDirectorySession.ContentKind.values()) {
                for (String openedIdentity : new String[] {"leaf-2", "leaf-1"}) {
                    RecordingFactory factory = new RecordingFactory();
                    factory.backend.openedIdentity = openedIdentity;
                    if (openedIdentity.equals("leaf-1")) {
                        factory.backend.metadata.removeLast();
                        factory.backend.metadata.add(
                                new MigrationPathState.Metadata(
                                        true, false, "leaf-2", 7));
                    }
                    try (MigrationDirectorySession session =
                            MigrationDirectorySession.open(
                                    profile,
                                    MigrationBinding.capture(
                                            MigrationBindingTest.observation(
                                                    "dir-1", "store-1")),
                                    factory)) {
                        assertThrows(
                                IllegalStateException.class,
                                () -> session.readBoundContent(
                                        kind, "iamzombieq-server.toml"));
                    }
                }
            }
        }
    }

    private static final class RecordingFactory
            implements MigrationDirectorySession.Factory {
        private final AtomicInteger secureOpens = new AtomicInteger();
        private final AtomicInteger basicOpens = new AtomicInteger();
        private final RecordingBackend backend = new RecordingBackend();
        private IOException secureFailure;

        @Override
        public MigrationDirectorySession.Backend openSecure(MigrationBinding binding)
                throws IOException {
            secureOpens.incrementAndGet();
            if (secureFailure != null) {
                throw secureFailure;
            }
            return backend;
        }

        @Override
        public MigrationDirectorySession.Backend openBasic(MigrationBinding binding) {
            basicOpens.incrementAndGet();
            return backend;
        }
    }

    private static final class RecordingBackend
            implements MigrationDirectorySession.Backend {
        private final ArrayDeque<MigrationPathState.Metadata> metadata =
                new ArrayDeque<>();
        private String openedIdentity = "leaf-1";
        private int nofollowOpens;
        private int closeCount;

        private RecordingBackend() {
            metadata.add(new MigrationPathState.Metadata(true, false, "leaf-1", 7));
            metadata.add(new MigrationPathState.Metadata(true, false, "leaf-1", 7));
        }

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(String basename) {
            return metadata.size() == 1 ? metadata.getFirst() : metadata.removeFirst();
        }

        @Override
        public MigrationDirectorySession.OpenedContent openNofollow(String basename) {
            nofollowOpens++;
            return new MigrationDirectorySession.OpenedContent(
                    openedIdentity, true, "payload".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
