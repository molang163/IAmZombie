package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoffinRecipeDataTest {
    private static final Path RECIPE_ROOT = Path.of("src/main/resources/data/iamzombieq/recipe");
    private static final Map<String, String> COFFIN_PLANKS = Map.ofEntries(
            Map.entry("oak", "minecraft:oak_planks"),
            Map.entry("spruce", "minecraft:spruce_planks"),
            Map.entry("birch", "minecraft:birch_planks"),
            Map.entry("jungle", "minecraft:jungle_planks"),
            Map.entry("acacia", "minecraft:acacia_planks"),
            Map.entry("cherry", "minecraft:cherry_planks"),
            Map.entry("dark_oak", "minecraft:dark_oak_planks"),
            Map.entry("pale_oak", "minecraft:pale_oak_planks"),
            Map.entry("mangrove", "minecraft:mangrove_planks"),
            Map.entry("bamboo", "minecraft:bamboo_planks"),
            Map.entry("crimson", "minecraft:crimson_planks"),
            Map.entry("warped", "minecraft:warped_planks")
    );

    @Test
    void everyCoffinRecipeUsesCapturedShapeAndMaterials() throws IOException {
        for (Map.Entry<String, String> entry : COFFIN_PLANKS.entrySet()) {
            String variant = entry.getKey();
            String json = compact(Files.readString(RECIPE_ROOT.resolve(variant + "_coffin.json")));

            assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), variant);
            assertTrue(json.contains("\"pattern\":[\"PPP\",\"WRW\",\"PPP\"]"), variant);
            assertTrue(json.contains("\"P\":\"" + entry.getValue() + "\""), variant);
            assertTrue(json.contains("\"W\":\"minecraft:white_wool\""), variant);
            assertTrue(json.contains("\"R\":\"minecraft:rotten_flesh\""), variant);
            assertTrue(json.contains("\"id\":\"iamzombieq:coffin\""), variant);
        }
    }

    private static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }
}
