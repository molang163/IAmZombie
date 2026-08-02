package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackagingHygieneTest {
    @Test
    void sourceTreeContainsNoBlockbenchProjectsOrExamplemodAssets() throws IOException {
        try (var paths = Files.walk(Path.of("src"))) {
            List<Path> entries = paths.toList();
            assertFalse(entries.stream().anyMatch(path -> path.getFileName().toString().endsWith(".bbmodel")),
                    "Blockbench project files should not live in the source tree");
            assertFalse(entries.stream().anyMatch(path -> path.endsWith(Path.of("assets", "examplemod"))),
                    "examplemod template assets should not live in the source tree");
        }
    }

    @Test
    void finalizedResourcesExcludeOnlyThePresentDatagenCache() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("exclude(\".cache/**\")"), "datagen cache directory should be excluded from resources");
        assertFalse(build.contains("exclude(\"**/*.bbmodel\")"), "dead Blockbench exclude should not remain");
        assertFalse(build.contains("exclude(\"assets/examplemod/**\")"), "dead examplemod exclude should not remain");
    }

    @Test
    void unusedMdkPublishingScaffoldIsAbsent() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertFalse(build.contains("id 'maven-publish'"), "unused maven-publish plugin should not be applied");
        assertFalse(build.contains("publishing {"), "unused example publishing block should not remain");
        assertFalse(build.contains("MavenPublication"), "unused example Maven publication should not remain");
    }

    @Test
    void wrapperAndLocalRuntimeWiringStayIntact() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("distributionType = Wrapper.DistributionType.BIN"),
                "wrapper distribution should remain binary-only");
        assertTrue(build.contains("runtimeClasspath.extendsFrom localRuntime"),
                "the real localRuntime wiring should remain intact");
    }

    @Test
    void dependencyBlockContainsNoMdkExamples() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertFalse(build.contains("mezz.jei"), "JEI template dependency comments should not remain");
        assertFalse(build.contains("coolmod"), "coolmod template dependency comments should not remain");
        assertFalse(build.contains("implementation files("), "file dependency template comments should not remain");
        assertFalse(build.contains("implementation project("), "project dependency template comments should not remain");
    }
}
