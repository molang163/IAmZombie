package dev.molang.iamzombieq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B4-NIX2: guards {@code flake.nix} as the sole Nix native-runtime library manifest. The launcher remains a
 * parameter-transparent {@code nix develop} delegator, while Gradle retains only the NeoForge runtime task
 * that disables early window control.
 */
class NixClientRuntimeSourceTest {
    private static final Path SCRIPT = Path.of("scripts/run-client-nixos.sh");
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path FLAKE = Path.of("flake.nix");
    private static final Path FLAKE_LOCK = Path.of("flake.lock");

    @Test
    void scriptIsAThinNixDevelopDelegator() throws IOException {
        String expected = """
                #!/usr/bin/env bash
                set -euo pipefail

                cd "$(dirname "$0")/.."

                exec nix develop --no-update-lock-file --command ./gradlew runClient "$@"
                """;

        String actual = Files.readString(SCRIPT).replace("\r\n", "\n");
        assertEquals(expected, actual,
                "the script should only cd to the repo root and transparently delegate to nix develop");
    }

    @Test
    void scriptRemainsExecutable() {
        assertTrue(Files.isExecutable(SCRIPT), "the script must keep its executable bit (mode 755)");
    }

    @Test
    void flakeIsTheSoleNixRuntimeLibraryManifest() throws IOException {
        assertTrue(Files.isRegularFile(FLAKE), "flake.nix must declare the Nix client runtime");
        assertTrue(Files.isRegularFile(FLAKE_LOCK), "flake.lock must pin the dev-shell inputs");

        String flake = Files.readString(FLAKE);

        assertTrue(flake.contains("jdk25"), "the dev shell must provide Java 25");
        assertTrue(flake.contains("JAVA_HOME"), "the dev shell must export JAVA_HOME");
        assertEquals(1, SourceScan.countOccurrences(flake, "runtimeLibraries ="),
                "flake.nix must have one explicit runtime library manifest");
        assertTrue(flake.contains("lib.makeLibraryPath"), "LD_LIBRARY_PATH must be derived from Nix packages");
        assertTrue(flake.contains("/run/opengl-driver/lib"), "the NVIDIA driver link must remain available");
        for (String library : List.of(
                "glibc", "stdenv.cc.cc.lib",
                "libglvnd", "openal", "flite", "vulkan-loader",
                "alsa-lib", "libpulseaudio", "pipewire", "udev",
                "libdrm", "wayland", "libxkbcommon",
                "libx11", "libxext", "libxcursor", "libxrandr", "libxi",
                "libxxf86vm", "libxfixes", "libxrender", "libxcb", "libxau", "libxdmcp",
                "libxinerama")) {
            assertEquals(1L, flake.lines().filter(line -> line.strip().equals(library)).count(),
                    "flake.nix must declare exactly one " + library + " runtime entry");
        }
        assertFalse(flake.contains("earlyWindowControl"),
                "earlyWindowControl is NeoForge run preparation and must stay in Gradle");
        assertFalse(flake.contains("fml.toml"),
                "fml.toml preparation is a Gradle task, not a dev-shell hook");
    }

    @Test
    void gradleAndScriptDoNotOwnNativeLibraryDiscovery() throws IOException {
        String build = Files.readString(BUILD_GRADLE);
        String script = Files.readString(SCRIPT);

        for (String forbidden : List.of(
                "/nix/store",
                "findFirstNixLibraryDir",
                "devClientLdLibraryPath",
                "mergedDevClientLdLibraryPath",
                "LD_LIBRARY_PATH",
                "FilenameFilter",
                ".listFiles(",
                "requiredFile",
                "sort { it.name }")) {
            assertFalse(build.contains(forbidden), "build.gradle must not contain " + forbidden);
        }
        for (String forbidden : List.of(
                "/nix/store",
                "findFirstNixLibraryDir",
                "devClientLdLibraryPath",
                "mergedDevClientLdLibraryPath",
                "LD_LIBRARY_PATH",
                "find_first_lib_dir",
                "prepend_ld_path",
                "find /nix")) {
            assertFalse(script.contains(forbidden), "the launcher must not contain " + forbidden);
        }
    }

    @Test
    void gradleKeepsOnlyEarlyWindowRuntimePreparation() throws IOException {
        String build = Files.readString(BUILD_GRADLE);
        assertTrue(build.contains("layout.projectDirectory.file('run/config/fml.toml')"),
                "Gradle must continue to own the NeoForge fml.toml preparation");
        assertTrue(build.contains("tasks.register('configureDevClientRuntime')"),
                "the configureDevClientRuntime task should still exist");
        assertTrue(build.contains("'earlyWindowControl = false'"),
                "configureDevClientRuntime should still force earlyWindowControl = false");
        assertTrue(build.contains("tasks.named('runClient').configure {"),
                "runClient's configuration block should still exist");
        assertTrue(build.contains("dependsOn(configureDevClientRuntime)"),
                "runClient should still depend on configureDevClientRuntime");
    }
}
