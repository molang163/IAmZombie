package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PackagingHygieneTest {
    @Test
    void finalizedResourcesExcludeTemplateAndDatagenCacheDirectories() throws IOException {
        assertFalse(Files.exists(Path.of("src/main/resources/assets/examplemod")), "examplemod template assets should not ship");

        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("exclude(\".cache/**\")"), "datagen cache directory should be excluded from resources");
        assertTrue(build.contains("exclude(\"assets/examplemod/**\")"), "template examplemod assets should be excluded from resources");
    }
}
