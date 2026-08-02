package dev.molang.iamzombieq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.molang.iamzombieq.util.SourceScan;
import org.junit.jupiter.api.Test;

/**
 * Source-scan pinning (no Minecraft bootstrap) two IAmZombieItems registration invariants:
 * <ul>
 *   <li>the coffin BlockItem must be registered with an explicit Item.Properties that calls stacksTo(1) so a coffin
 *       stacks to exactly one (RC1 — it previously used registerSimpleBlockItem and stacked to the default 64);</li>
 *   <li>the disguise mask's registration PATH and its equipment-asset id must both flow from the single
 *       {@code DisguiseRules.DISGUISE_MASK_PATH} constant, so the item id, the equip material id and
 *       {@code DisguiseRules.DISGUISE_MASK_ID} can never drift apart (T2 sub-item (3)).</li>
 * </ul>
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

    @Test
    void disguiseMaskRegistrationAndEquipAssetShareTheSinglePathConstant() throws IOException {
        String source = SourceScan.stripComments(Files.readString(SOURCE));

        // Item registration path must be the shared constant, not a bare "disguise_mask" literal.
        assertTrue(source.contains("registerSimpleItem(") && source.contains("DisguiseRules.DISGUISE_MASK_PATH"),
                "the disguise_mask item must be registered via DisguiseRules.DISGUISE_MASK_PATH");
        // Equipment-asset id must be built from the same constant (ModIds.id(DisguiseRules.DISGUISE_MASK_PATH)).
        assertTrue(source.contains("ModIds.id(DisguiseRules.DISGUISE_MASK_PATH)"),
                "the disguise_mask equip asset must be ModIds.id(DisguiseRules.DISGUISE_MASK_PATH) — same source of truth");
        // Both the registration and the equip asset must reference the constant (≥2 uses).
        assertTrue(SourceScan.countOccurrences(source, "DisguiseRules.DISGUISE_MASK_PATH") >= 2,
                "both the disguise_mask registration and its equip asset must flow from DISGUISE_MASK_PATH");
        // No bare "disguise_mask" string literal should remain in IAmZombieItems (both were replaced by the constant).
        assertFalse(source.contains("\"disguise_mask\""),
                "no bare \"disguise_mask\" literal should remain — it must come from DisguiseRules.DISGUISE_MASK_PATH");
    }
}
