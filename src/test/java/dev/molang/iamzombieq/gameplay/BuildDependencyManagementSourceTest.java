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
    void foojayAndGameVersionsStayInTheirCurrentFiles() throws IOException {
        String settings = SourceScan.compact(
                SourceScan.stripComments(Files.readString(Path.of("settings.gradle"))));
        String properties = Files.readString(Path.of("gradle.properties"));

        assertTrue(settings.contains(
                "id'org.gradle.toolchains.foojay-resolver-convention'version'1.0.0'"),
                "the settings-only foojay plugin should remain pinned in settings.gradle");
        assertFalse(settings.contains("libs.plugins"), "settings.gradle should not consume the project catalog");
        assertTrue(properties.lines().anyMatch("minecraft_version=26.2"::equals));
        assertTrue(properties.lines().anyMatch("minecraft_version_range=[26.2]"::equals));
        // The primary tested baseline is 26.2.0.25-beta. The declared range keeps .12-beta as the retained minimum
        // and bounds the upper end at 26.2.1-alpha (exclusive), allowing later 26.2.0.x builds while the entire next
        // MC line — including its 26.2.1-* prereleases, which a bound at the 26.2.1 release would have leaked — stays
        // excluded. Range behavior is exercised against the real Maven engine in NeoForgeVersionRangeSourceTest.
        assertTrue(properties.lines().anyMatch("neo_version=26.2.0.25-beta"::equals));
        assertTrue(properties.lines().anyMatch("neo_version_range=[26.2.0.12-beta,26.2.1-alpha)"::equals));
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
        String buildStep = "- name: Build with Gradle";
        String gameTestStep = "- name: Run GameTests";
        String uploadStep = "- name: Upload artifacts";

        assertEquals(1, SourceScan.countOccurrences(workflow, buildStep),
                "the workflow should contain exactly one Gradle build step");
        assertEquals(1, SourceScan.countOccurrences(workflow, gameTestStep),
                "the workflow should contain exactly one GameTest gate");
        assertEquals(1, SourceScan.countOccurrences(workflow, uploadStep),
                "the workflow should contain exactly one artifact upload step");
        int buildIndex = workflow.indexOf(buildStep);
        int gameTestIndex = workflow.indexOf(gameTestStep);
        int uploadIndex = workflow.indexOf(uploadStep);
        assertTrue(buildIndex < gameTestIndex && gameTestIndex < uploadIndex,
                "the mandatory GameTest gate must stay between build and artifact upload");
        String gameTestBlock = workflow.substring(gameTestIndex, uploadIndex);
        assertEquals(1L, gameTestBlock.lines().map(String::strip).filter(line -> line.startsWith("run:")).count(),
                "the GameTest step should contain exactly one command");
        assertTrue(gameTestBlock.lines().map(String::strip)
                        .anyMatch("run: ./gradlew runGameTestServer"::equals),
                "the GameTest gate must run the exact real headless server command");
        assertFalse(workflow.contains("continue-on-error"),
                "CI validation steps must not opt out of workflow failure propagation");
        assertTrue(workflow.contains("uses: actions/upload-artifact@v4"));
        assertTrue(workflow.contains("path: build/libs/*.jar"));
    }
}
