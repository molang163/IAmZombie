package dev.molang.iamzombieq.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IAmZombieAttachmentsTest {
    @Test
    void playerZombieAttachmentIsSyncedToClients() throws IOException {
        String source = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java"));

        assertTrue(source.contains(".sync("), "PLAYER_ZOMBIE attachment must opt into NeoForge client sync");
        assertTrue(source.contains("PlayerZombieDataSync"), "PLAYER_ZOMBIE attachment should have an explicit sync codec/handler");
    }
}
