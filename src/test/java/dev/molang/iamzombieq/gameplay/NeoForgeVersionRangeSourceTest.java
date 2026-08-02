package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

/**
 * Behavioral guard for the shipped {@code neo_version_range}. FML evaluates a mod's NeoForge dependency
 * range with Maven's {@link VersionRange}/{@link DefaultArtifactVersion} at load time, so this test parses the
 * exact range value out of {@code gradle.properties} and drives that SAME engine — proving what the range really
 * admits/rejects rather than trusting the literal spelling.
 *
 * <p>The trap this catches: an exclusive upper bound at the next line's <em>release</em> ({@code 26.2.1}) still
 * admits that line's prereleases ({@code 26.2.1-alpha}/{@code 26.2.1-beta}/{@code 26.2.1.0-beta}), because Maven
 * orders a prerelease BEFORE its release. Only an upper bound at {@code 26.2.1-alpha} (the earliest possible
 * {@code 26.2.1-*} qualifier) excludes the whole next Minecraft line while keeping every {@code 26.2.0.x}.</p>
 */
class NeoForgeVersionRangeSourceTest {
    private static VersionRange shippedRange() throws IOException, InvalidVersionSpecificationException {
        String properties = Files.readString(Path.of("gradle.properties"));
        String spec = properties.lines()
                .filter(l -> l.startsWith("neo_version_range="))
                .map(l -> l.substring("neo_version_range=".length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("gradle.properties has no neo_version_range"));
        return VersionRange.createFromVersionSpec(spec);
    }

    private static boolean admits(VersionRange range, String version) {
        return range.containsVersion(new DefaultArtifactVersion(version));
    }

    @Test
    void acceptsTheRetainedMinimumTheCertifiedBaselineAndEverySameLineBuild() throws Exception {
        VersionRange range = shippedRange();
        // .12 retained minimum, .25 certified baseline, and arbitrary later same-MC-26.2-line 26.2.0.x builds.
        for (String v : new String[] {
                "26.2.0.12-beta", "26.2.0.13-beta", "26.2.0.15-beta", "26.2.0.25-beta", "26.2.0.99-beta"}) {
            assertTrue(admits(range, v), "the range must admit same-line build " + v);
        }
    }

    @Test
    void rejectsBelowTheRetainedMinimum() throws Exception {
        assertFalse(admits(shippedRange(), "26.2.0.11-beta"),
                "26.2.0.11-beta is below the .12 floor and must be rejected");
    }

    @Test
    void rejectsTheEntireNextMinecraftLineIncludingItsPrereleases() throws Exception {
        VersionRange range = shippedRange();
        // The next MC line's release AND every prerelease qualifier that sorts before it must all be excluded.
        for (String v : new String[] {
                "26.2.1-alpha", "26.2.1-beta", "26.2.1.0-beta", "26.2.1.1-beta", "26.2.1"}) {
            assertFalse(admits(range, v),
                    "the range must reject next-MC-line version " + v + " (prereleases sort before the release)");
        }
    }

    @Test
    void theShippedSpecIsTheAlphaBoundedNextLineExclusion() throws IOException {
        // Pin the exact spelling so the fix cannot silently regress to the release-bounded form that leaked prereleases.
        String properties = Files.readString(Path.of("gradle.properties"));
        assertTrue(properties.lines().anyMatch("neo_version_range=[26.2.0.12-beta,26.2.1-alpha)"::equals),
                "the upper bound must be 26.2.1-alpha (exclusive), not the release 26.2.1 which leaked 26.2.1-* prereleases");
    }
}
