package dev.molang.iamzombieq.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IAmZombieAttachmentsTest {
    private static final Path PLAYER_EVENTS =
            Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");
    private static final Path FOOD_EVENTS =
            Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFoodEvents.java");
    private static final Path SERVER_ZOMBIE_PLAYER =
            Path.of("src/main/java/dev/molang/iamzombieq/internal/core/ServerZombiePlayer.java");

    @Test
    void playerZombieAttachmentIsSyncedToClients() throws IOException {
        String source = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java"));

        assertTrue(source.contains(".sync(PlayerZombieDataSync.INSTANCE)"),
                "PLAYER_ZOMBIE attachment must opt into NeoForge client sync");
        assertTrue(source.contains("PlayerZombieDataSync"), "PLAYER_ZOMBIE attachment should have an explicit sync codec/handler");
    }

    @Test
    void playerZombieWritesRelyOnSetDataAutomaticSync() throws IOException {
        String playerEvents = Files.readString(PLAYER_EVENTS);
        String foodEvents = Files.readString(FOOD_EVENTS);
        String facade = Files.readString(SERVER_ZOMBIE_PLAYER);
        String playerEventsCode = SourceScan.compact(SourceScan.stripComments(playerEvents));
        String foodEventsCode = SourceScan.compact(SourceScan.stripComments(foodEvents));
        String facadeCode = SourceScan.compact(SourceScan.stripComments(facade));

        assertEquals(2, SourceScan.countOccurrences(playerEventsCode,
                "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE,nextData)"),
                "clone and death-evolution writes must remain");
        assertEquals(1, SourceScan.countOccurrences(playerEventsCode,
                "killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE,nextData)"),
                "giant-kill transform write must remain");
        assertEquals(1, SourceScan.countOccurrences(foodEventsCode,
                "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE,data.withState(data.state().asAdult()))"),
                "super-rotten-flesh baby-to-adult write must remain");
        assertEquals(1, SourceScan.countOccurrences(facadeCode,
                "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE.get(),next)"),
                "public facade write must remain");

        assertFalse(playerEventsCode.contains(".syncData(IAmZombieAttachments.PLAYER_ZOMBIE"),
                "ZombiePlayerEvents must rely on setData automatic sync");
        assertFalse(foodEventsCode.contains(".syncData(IAmZombieAttachments.PLAYER_ZOMBIE"),
                "ZombieFoodEvents must rely on setData automatic sync");
        assertFalse(facadeCode.contains(".syncData(IAmZombieAttachments.PLAYER_ZOMBIE"),
                "ServerZombiePlayer must rely on setData automatic sync");
    }
}
