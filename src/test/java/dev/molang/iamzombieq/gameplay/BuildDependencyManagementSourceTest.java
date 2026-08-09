package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BuildDependencyManagementSourceTest {
    private static final Path VERSION_CATALOG = Path.of("gradle", "libs.versions.toml");
    private static final Path DEPENDABOT = Path.of(".github", "dependabot.yml");
    private static final Path BUILD_WORKFLOW = Path.of(".github", "workflows", "build.yml");

    @Test
    void versionCatalogPinsModDevAndJunit() throws IOException {
        assertTrue(Files.isRegularFile(VERSION_CATALOG), "the standard Gradle version catalog should exist");

        String catalog = SourceScan.compact(Files.readString(VERSION_CATALOG));
        assertTrue(catalog.contains("[versions]moddev=\"2.0.141\"junit=\"5.13.4\""),
                "the catalog should pin the current ModDev and JUnit versions");
        assertTrue(catalog.contains(
                "[libraries]junit-jupiter={module=\"org.junit.jupiter:junit-jupiter\",version.ref=\"junit\"}"),
                "the JUnit library alias should use the catalog version");
        assertTrue(catalog.contains(
                "[plugins]moddev={id=\"net.neoforged.moddev\",version.ref=\"moddev\"}"),
                "the ModDev plugin alias should use the catalog version");
    }

    @Test
    void buildConsumesCatalogAliasesAndLeavesLauncherUnversioned() throws IOException {
        String build = SourceScan.compact(SourceScan.stripComments(Files.readString(Path.of("build.gradle"))));

        assertTrue(build.contains("alias(libs.plugins.moddev)"), "build.gradle should consume the ModDev plugin alias");
        assertTrue(build.contains("testImplementationlibs.junit.jupiter"),
                "build.gradle should consume the JUnit library alias");
        assertTrue(build.contains("testRuntimeOnly\"org.junit.platform:junit-platform-launcher\""),
                "the launcher should remain unversioned so the JUnit BOM aligns it");
        assertFalse(build.contains("id'net.neoforged.moddev'version"),
                "the ModDev version should not remain inline");
        assertFalse(build.contains("org.junit.jupiter:junit-jupiter:"),
                "the Jupiter version should not remain inline");
        assertFalse(build.contains("org.junit.platform:junit-platform-launcher:"),
                "the platform launcher must not be assigned the Jupiter version");
        assertFalse(build.contains("2.0.141"), "the ModDev version should live only in the catalog");
        assertFalse(build.contains("5.13.4"), "the JUnit version should live only in the catalog");
    }

    @Test
    void foojayAndGameVersionsStayInTheirAuthorityFiles() throws IOException {
        String settings = SourceScan.compact(
                SourceScan.stripComments(Files.readString(Path.of("settings.gradle"))));
        String properties = Files.readString(Path.of("gradle.properties"));
        var central = StonecutterScaffoldSourceTest.parseToml(Path.of("stonecutter.properties.toml"));

        assertTrue(settings.contains(
                "id'org.gradle.toolchains.foojay-resolver-convention'version'1.0.0'"),
                "the settings-only foojay plugin should remain pinned in settings.gradle");
        assertFalse(settings.contains("libs.plugins"), "settings.gradle should not consume the project catalog");
        for (String stalePrefix : new String[] {
                "minecraft_version=", "minecraft_version_range=", "neo_version=", "neo_version_range=", "mod_"
        }) {
            assertFalse(properties.lines().map(String::strip).anyMatch(line -> line.startsWith(stalePrefix)),
                    "gradle.properties must not remain a second coordinate authority: " + stalePrefix);
        }

        // .25 remains a historical runtime baseline admitted by this range. The project tracks the .47 compile
        // coordinate without narrowing the declared same-line compatibility range.
        var node26_2 = central.get("26.2.x");
        assertEquals("26.2", node26_2.get("deps.minecraft"));
        assertEquals("[26.2,26.3)", node26_2.get("mod.mc_compat"));
        assertEquals("26.2.0.47-beta", node26_2.get("deps.neo_loader"));
        assertEquals("[26.2.0.12-beta,26.2.1-alpha)", node26_2.get("deps.neo_range"));
    }

    @Test
    void dependabotTracksExactlyTwoWeeklyEcosystemsWithoutAutomerge() throws IOException {
        assertTrue(Files.isRegularFile(DEPENDABOT), "Dependabot configuration should live under .github");

        String expected = """
                version: 2
                updates:
                  - package-ecosystem: "gradle"
                    directory: "/"
                    schedule:
                      interval: "weekly"
                  - package-ecosystem: "github-actions"
                    directory: "/"
                    schedule:
                      interval: "weekly"
                """;
        String actual = Files.readString(DEPENDABOT).replace("\r\n", "\n");
        assertEquals(expected.strip(), actual.strip(),
                "Dependabot should contain exactly the two requested weekly ecosystems");

        try (var paths = Files.walk(Path.of(".github"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path).toLowerCase(Locale.ROOT);
                assertFalse(source.contains("automerge"), "automatic merge configuration should not be added");
                assertFalse(source.contains("auto-merge"), "automatic merge configuration should not be added");
            }
        }
    }

    @Test
    void gameTestsRemainAMandatoryGateBeforeArtifactUpload() throws IOException {
        assertTrue(Files.isRegularFile(BUILD_WORKFLOW), "the Build workflow should remain present");

        String workflow = Files.readString(BUILD_WORKFLOW).replace("\r\n", "\n");
        String selectStep = "- name: Select canonical node";
        String buildStep = "- name: Build with Gradle";
        String gameTestStep = "- name: Run GameTests";
        String uploadStep = "- name: Upload artifacts";

        assertEquals(1, SourceScan.countOccurrences(workflow, selectStep),
                "the workflow should explicitly select the canonical node once");
        assertEquals(1, SourceScan.countOccurrences(workflow, buildStep),
                "the workflow should contain exactly one Gradle build step");
        assertEquals(1, SourceScan.countOccurrences(workflow, gameTestStep),
                "the workflow should contain exactly one GameTest gate");
        assertEquals(1, SourceScan.countOccurrences(workflow, uploadStep),
                "the workflow should contain exactly one artifact upload step");
        int selectIndex = workflow.indexOf(selectStep);
        int buildIndex = workflow.indexOf(buildStep);
        int gameTestIndex = workflow.indexOf(gameTestStep);
        int uploadIndex = workflow.indexOf(uploadStep);
        assertTrue(selectIndex < buildIndex && buildIndex < gameTestIndex && gameTestIndex < uploadIndex,
                "node selection and build must precede the mandatory GameTest gate and artifact upload");
        String selectBlock = workflow.substring(selectIndex, buildIndex);
        assertTrue(selectBlock.lines().map(String::strip)
                        .anyMatch("run: ./gradlew 'Set active project to 26.2.x'"::equals),
                "CI must switch the canonical project before executing source-reading validation");
        String buildBlock = workflow.substring(buildIndex, gameTestIndex);
        assertTrue(buildBlock.lines().map(String::strip)
                        .anyMatch("run: ./gradlew :26.2.x:build"::equals),
                "CI must build only the explicitly selected canonical node");
        String gameTestBlock = workflow.substring(gameTestIndex, uploadIndex);
        assertEquals(1L, gameTestBlock.lines().map(String::strip).filter(line -> line.startsWith("run:")).count(),
                "the GameTest step should contain exactly one command");
        assertTrue(gameTestBlock.lines().map(String::strip)
                        .anyMatch("run: ./gradlew :26.2.x:runGameTestServer"::equals),
                "the GameTest gate must run the exact qualified real headless server command");
        assertFalse(workflow.contains("run: ./gradlew runGameTestServer"),
                "CI must not invoke a top-level runtime task that can fan out across nodes");
        assertFalse(workflow.contains("continue-on-error"),
                "CI validation steps must not opt out of workflow failure propagation");
        assertTrue(workflow.contains("uses: actions/upload-artifact@v4"));
        assertTrue(workflow.contains("path: versions/26.2.x/build/libs/*.jar"));
        assertTrue(workflow.contains("if-no-files-found: error"),
                "a missing formal node artifact must fail CI instead of producing a false green");
    }
}
