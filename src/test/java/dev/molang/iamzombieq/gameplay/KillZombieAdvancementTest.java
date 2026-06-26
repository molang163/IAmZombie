package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Covers the two advancement changes that are data-only (no Java award path):
 *
 * <ul>
 *   <li>The new {@code iamzombieq:kill_zombie} advancement ("自杀残杀的新学期") fires on the vanilla
 *       {@code player_killed_entity} trigger restricted to the {@code #iamzombieq:zombies} tag (all zombies + giant),
 *       so it is intentionally NOT in {@link dev.molang.iamzombieq.rules.IAmZombieAdvancementIds#all()} (that list is
 *       for the manual/impossible code-awarded advancements only).</li>
 *   <li>The vanilla "Monster Hunter" advancement is renamed to "现在谁是怪物？" purely by overriding its
 *       {@code minecraft}-namespace lang keys — the advancement JSON, criteria and tree are left untouched.</li>
 * </ul>
 *
 * These are file-content assertions runnable on the Minecraft-free test classpath; the actual in-game rename
 * (lang override precedence) and the kill trigger firing are runtime behaviors verified in game.
 */
class KillZombieAdvancementTest {
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void killZombieAdvancementTriggersOnKillingTheZombieTag() throws IOException {
        Path json = RES.resolve("data/iamzombieq/advancement/kill_zombie.json");
        assertTrue(Files.isRegularFile(json), () -> "missing advancement json " + json);

        String compact = Files.readString(json).replaceAll("\\s+", "");
        assertTrue(compact.contains("\"trigger\":\"minecraft:player_killed_entity\""),
                "kill_zombie uses the vanilla player_killed_entity trigger");
        assertTrue(compact.contains("\"minecraft:entity_type\":\"#iamzombieq:zombies\""),
                "kill_zombie matches the #iamzombieq:zombies tag");
        assertTrue(compact.contains("advancement.iamzombieq.kill_zombie.title"), "title translate key present");
        assertTrue(compact.contains("advancement.iamzombieq.kill_zombie.description"),
                "description translate key present");
    }

    @Test
    void zombiesTagExtendsVanillaAndAddsGiant() throws IOException {
        Path tag = RES.resolve("data/iamzombieq/tags/entity_type/zombies.json");
        assertTrue(Files.isRegularFile(tag), () -> "missing entity_type tag " + tag);

        String compact = Files.readString(tag).replaceAll("\\s+", "");
        assertTrue(compact.contains("\"#minecraft:zombies\""), "extends the vanilla zombies tag");
        assertTrue(compact.contains("\"minecraft:giant\""), "adds the giant (not in the vanilla tag)");
    }

    @Test
    void killZombieKeysPresentInBothModLangs() throws IOException {
        for (String lang : new String[] {"zh_cn", "en_us"}) {
            String json = Files.readString(RES.resolve("assets/iamzombieq/lang/" + lang + ".json"));
            assertTrue(json.contains("advancement.iamzombieq.kill_zombie.title"), () -> lang + " missing title");
            assertTrue(json.contains("advancement.iamzombieq.kill_zombie.description"),
                    () -> lang + " missing description");
        }
    }

    @Test
    void monsterHunterRenameOverridesVanillaLangKeys() throws IOException {
        for (String lang : new String[] {"zh_cn", "en_us"}) {
            Path p = RES.resolve("assets/minecraft/lang/" + lang + ".json");
            assertTrue(Files.isRegularFile(p), () -> "missing minecraft lang override " + p);

            String json = Files.readString(p);
            assertTrue(json.contains("advancements.adventure.kill_a_mob.title"),
                    () -> lang + " missing kill_a_mob title override");
            assertTrue(json.contains("advancements.adventure.kill_a_mob.description"),
                    () -> lang + " missing kill_a_mob description override");
        }
    }
}
