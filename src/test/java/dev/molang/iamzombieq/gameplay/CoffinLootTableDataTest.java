package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Data-scan pinning the coffin block loot table. To stop the two-half coffin dropping two items (RC3 dupe), the
 * item entry must be gated by a full block_state_property condition on part=head, so only the head half ever drops
 * an item. The pool-level survives_explosion condition must remain.
 */
class CoffinLootTableDataTest {
    private static final Path LOOT_TABLE =
            Path.of("src/main/resources/data/iamzombieq/loot_table/blocks/coffin.json");

    @Test
    void coffinDropsOnlyFromTheHeadHalf() throws IOException {
        String json = compact(Files.readString(LOOT_TABLE));

        assertTrue(json.contains("\"condition\":\"minecraft:block_state_property\""),
                "the coffin drop should be gated by a block_state_property condition");
        assertTrue(json.contains("\"block\":\"iamzombieq:coffin\""),
                "the block_state_property condition should target the coffin block (full form, not a bare {part:head})");
        assertTrue(json.contains("\"properties\":{\"part\":\"head\"}"),
                "the coffin drop should only fire for the head half so a 2-half coffin drops exactly one");
        assertTrue(json.contains("\"condition\":\"minecraft:survives_explosion\""),
                "the pool-level survives_explosion condition must be preserved");
    }

    private static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }
}
