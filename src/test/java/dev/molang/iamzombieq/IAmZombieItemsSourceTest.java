package dev.molang.iamzombieq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-scan pinning (no Minecraft bootstrap) the coffin BlockItem registration. The coffin item must be
 * registered with an explicit Item.Properties that calls stacksTo(1) so a coffin stacks to exactly one — guarding
 * RC1 (the coffin previously used registerSimpleBlockItem and stacked to the default 64).
 */
class IAmZombieItemsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieItems.java");

    @Test
    void coffinItemStacksToOne() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("new BlockItem(IAmZombieBlocks.COFFIN.get(), properties)"),
                "the coffin should be registered via an explicit BlockItem factory (registerItem shape, mirroring HEROBRINE_HEAD)");
        assertTrue(source.contains("new Item.Properties().stacksTo(1)"),
                "the coffin item properties should call stacksTo(1) so a coffin stacks to exactly one");
        assertFalse(source.contains("registerSimpleBlockItem(IAmZombieBlocks.COFFIN)"),
                "the bare registerSimpleBlockItem(COFFIN) (default max stack 64) should be replaced");
    }
}
