package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoffinRecipeDataTest {
    private static final Path RECIPE_ROOT = Path.of("src/main/resources/data/iamzombieq/recipe");

    @Test
    void coffinRecipeUsesCapturedShapeAndMaterials() throws IOException {
        String json = SourceScan.compact(Files.readString(RECIPE_ROOT.resolve("coffin.json")));

        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), "coffin recipe should be crafting_shaped");
        assertTrue(json.contains("\"pattern\":[\"PPP\",\"WRW\",\"PPP\"]"), "coffin recipe should keep the captured 3x3 pattern");
        assertTrue(json.contains("\"P\":\"#minecraft:planks\""), "coffin recipe should accept any planks via the vanilla tag");
        assertTrue(json.contains("\"W\":\"minecraft:white_wool\""), "coffin recipe should use white wool");
        assertTrue(json.contains("\"R\":\"minecraft:rotten_flesh\""), "coffin recipe should use rotten flesh");
        assertTrue(json.contains("\"id\":\"iamzombieq:coffin\""), "coffin recipe should produce the coffin block");
    }
}
