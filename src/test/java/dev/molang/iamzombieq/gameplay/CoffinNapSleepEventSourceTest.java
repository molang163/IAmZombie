package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the migration of daytime auto-wake suppression for coffin nappers from the fragile
 * {@code @Redirect} coffin-sleep mixin on {@code Player#tick} to NeoForge's
 * {@code CanContinueSleepingEvent} handler in {@link CoffinNapManager}. Both vanilla wake gates (the
 * Player.tick daytime wake and the LivingEntity bed-exists check) run through that event, so the event
 * wiring must stay in place AND the old mixin must stay deleted (source file + mixins.json registration).
 *
 * <p>The deleted mixin's class name is spelled split ({@code "...Sleep" + "Mixin"}) so the
 * source guard can verify that the deleted class name no longer occurs elsewhere, which
 * is exactly what this test guards — no live reference may reappear anywhere, including here.
 */
class CoffinNapSleepEventSourceTest {
    private static final String OLD_MIXIN_NAME = "PlayerCoffinSleep" + "Mixin";
    private static final Path MANAGER = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/CoffinNapManager.java");
    private static final Path OLD_MIXIN = Path.of("src/main/java/dev/molang/iamzombieq/mixin/" + OLD_MIXIN_NAME + ".java");
    private static final Path MIXIN_JSON = Path.of("src/main/resources/iamzombieq.mixins.json");

    @Test
    void napManagerKeepsDaytimeNappersAsleepThroughTheSleepEvent() throws IOException {
        String source = Files.readString(MANAGER);

        assertTrue(source.contains("CanContinueSleepingEvent"),
                "CoffinNapManager must handle NeoForge's CanContinueSleepingEvent after the mixin replacement");
        assertTrue(source.contains("setContinueSleeping(true)"),
                "the handler must force continue-sleeping to suppress the vanilla daytime auto-wake");
        assertTrue(source.contains("isNapping(player.getUUID())"),
                "the handler must only keep sleeping for an active coffin napper (isNapping guard)");
    }

    @Test
    void oldCoffinSleepMixinStaysDeleted() throws IOException {
        assertFalse(Files.exists(OLD_MIXIN),
                OLD_MIXIN_NAME + " was replaced by the CanContinueSleepingEvent handler and must not come back");
        assertFalse(Files.readString(MIXIN_JSON).contains(OLD_MIXIN_NAME),
                "iamzombieq.mixins.json must not register the deleted " + OLD_MIXIN_NAME);
    }
}
