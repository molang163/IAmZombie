package dev.molang.iamzombieq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IAmZombieModTest {
    @Test
    void exposesApprovedModIdentity() {
        assertEquals("iamzombieq", IAmZombieMod.MOD_ID);
        assertEquals("I Am Zombie?", IAmZombieMod.ENGLISH_NAME);
        assertEquals("我是僵尸？", IAmZombieMod.CHINESE_NAME);
    }

    @Test
    void declaresMixinConfigForUndeadPlayerHooks() throws Exception {
        String template = Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml"));

        assertTrue(template.contains("[[mixins]]"));
        assertTrue(template.contains("config=\"${mod_id}.mixins.json\""));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/iamzombieq.mixins.json")));
    }
}
