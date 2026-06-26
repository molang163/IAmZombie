package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.rules.IAmZombieAdvancementIds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IAmZombieAdvancementsTest {
    private static final Path ADVANCEMENT_ROOT = Path.of("src/main/resources/data/iamzombieq/advancement");

    @Test
    void allManualAdvancementsHaveImpossibleManualCriterion() throws IOException {
        for (String path : IAmZombieAdvancementIds.all()) {
            Path jsonPath = ADVANCEMENT_ROOT.resolve(path + ".json");
            assertTrue(Files.isRegularFile(jsonPath), () -> "missing advancement json " + jsonPath);

            String json = Files.readString(jsonPath).replaceAll("\\s+", "");
            assertTrue(json.contains("\"manual\":{\"trigger\":\"minecraft:impossible\"}"), path);
            assertTrue(json.contains("\"requirements\":[[\"manual\"]]"), path);
        }
    }

    @Test
    void advancementListIncludesZombifiedPiglinEvolution() {
        assertTrue(IAmZombieAdvancementIds.all().contains("zombified_piglin"));
    }

    @Test
    void advancementListIncludesVillagerInfection() {
        assertTrue(IAmZombieAdvancementIds.all().contains("infection"));
    }

    @Test
    void advancementDisplayTextUsesLocalizationKeys() throws IOException {
        for (String path : IAmZombieAdvancementIds.all()) {
            Path jsonPath = ADVANCEMENT_ROOT.resolve(path + ".json");
            String json = Files.readString(jsonPath);
            assertTrue(json.contains("\"translate\""), () -> "advancement display should use translate keys: " + path);
            assertTrue(json.contains("advancement.iamzombieq." + path + ".title"), () -> "missing title key in " + path);
            assertTrue(json.contains("advancement.iamzombieq." + path + ".description"), () -> "missing description key in " + path);
        }
    }
}
