package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieBalanceConfigSourceTest {
    private static final Path CONFIG = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path PLAYER_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");
    // Form-attribute application (innate armor, etc.) was split out of ZombiePlayerEvents into this dedicated class.
    private static final Path FORM_ATTRIBUTES = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFormAttributes.java");
    private static final Path INFECTION_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");
    private static final Path MOUNT_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMountEvents.java");

    @Test
    void innateArmorValuesAreCommonConfigEntriesAndUsedByPlayerEvents() throws IOException {
        String config = Files.readString(CONFIG);
        // The form-attribute apply (which reads the configured armor) now lives in ZombieFormAttributes.
        String formAttributes = Files.readString(FORM_ATTRIBUTES);

        assertTrue(config.contains("NORMAL_ZOMBIE_INNATE_ARMOR"), "normal zombie innate armor should be configurable");
        assertTrue(config.contains("DROWNED_INNATE_ARMOR"), "drowned innate armor should be configurable");
        assertTrue(config.contains("HUSK_INNATE_ARMOR"), "husk innate armor should be configurable");
        assertTrue(config.contains("ZOMBIFIED_PIGLIN_INNATE_ARMOR"), "zombified piglin innate armor should be configurable");
        assertTrue(formAttributes.contains("configuredInnateArmor(form)"),
                "player armor refresh should read the configured armor for the current form");
        assertTrue(formAttributes.contains("form, data.state().size(), configuredArmor, difficultyFraction"),
                "the configured armor value should feed the pure form-attribute table");
    }

    @Test
    void infectionProbabilitiesAreCommonConfigEntriesAndUsedAtRuntime() throws IOException {
        String config = Files.readString(CONFIG);
        String infectionEvents = Files.readString(INFECTION_EVENTS);
        String mountEvents = Files.readString(MOUNT_EVENTS);

        assertTrue(config.contains("EASY_INFECTION_CHANCE"), "easy infection chance should be configurable");
        assertTrue(config.contains("NORMAL_INFECTION_CHANCE"), "normal infection chance should be configurable");
        assertTrue(config.contains("HARD_INFECTION_CHANCE"), "hard infection chance should be configurable");
        assertTrue(infectionEvents.contains("configuredInfectionChance(gameDifficulty(level.getDifficulty()))"), "villager infection should use configured chances");
        assertTrue(mountEvents.contains("configuredInfectionChance(gameDifficulty(level.getDifficulty()))"), "animal infection should use configured chances");
    }

    @Test
    void sunlightHeadgearDurabilityLossUsesCanonicalServerAuthority() throws IOException {
        String config = Files.readString(CONFIG);
        String playerEvents = Files.readString(PLAYER_EVENTS);

        assertTrue(config.contains("SUN_PROTECTION_HEADGEAR_DAMAGE"), "sun-protection headgear damage should be configurable");
        assertTrue(playerEvents.contains("IAmZombieServerConfig.SUN_PROTECTION_HEADGEAR_DAMAGE.get()"),
                "sun headgear damage should read canonical SERVER authority");
    }
}
