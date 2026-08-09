package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MigrationFileSystemTest {
    @Test
    void fixedArtifactsAreDerivedWithinEachActualTargetParent() {
        Path world = MigrationBindingTest.absolutePath(
                "world-a", "serverconfig", "iamzombieq-server.toml");
        Path global = MigrationBindingTest.absolutePath(
                "config", "iamzombieq-server.toml");
        Path preferences = MigrationBindingTest.absolutePath(
                "config", "iamzombieq-preferences-client.toml");
        MigrationFileSystem.ArtifactPaths worldPaths =
                MigrationFileSystem.ArtifactPaths.forTarget(world);
        MigrationFileSystem.ArtifactPaths globalPaths =
                MigrationFileSystem.ArtifactPaths.forTarget(global);
        MigrationFileSystem.ArtifactPaths preferencePaths =
                MigrationFileSystem.ArtifactPaths.forTarget(preferences);

        assertEquals(world.getParent(), worldPaths.lock().getParent());
        assertEquals(global.getParent(), globalPaths.journal().getParent());
        assertEquals(preferences.getParent(), preferencePaths.marker().getParent());
        assertNotEquals(worldPaths.lock(), globalPaths.lock());
        assertNotEquals(globalPaths.lock(), preferencePaths.lock());
        assertTrue(worldPaths.lock().getFileName().toString()
                .startsWith("iamzombieq-server.toml.iamzombieq-migration-v1"));
        assertEquals(5, worldPaths.fixedStages().size());
    }

    @Test
    void cooperativeFilesystemApiCannotRetargetParentNamespace() {
        Set<String> methods = Arrays.stream(MigrationFileSystem.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toUnmodifiableSet());
        assertFalse(methods.contains("moveDirectory"));
        assertFalse(methods.contains("replaceDirectory"));
        assertFalse(methods.contains("createSymbolicLink"));
        assertFalse(methods.contains("mount"));
        assertTrue(methods.containsAll(Set.of(
                "readNofollowMetadata",
                "observeBinding",
                "capabilities",
                "openDirectorySession")));
    }
}
