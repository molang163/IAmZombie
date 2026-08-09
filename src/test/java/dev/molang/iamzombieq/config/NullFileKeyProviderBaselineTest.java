package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NullFileKeyProviderBaselineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void unsupportedNullKeyProviderRetainsTheFailClosedBaseline()
            throws IOException {
        URI archive = URI.create(
                "jar:" + temporaryDirectory.resolve("null-keys.zip").toUri());
        try (FileSystem fileSystem =
                FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path root = fileSystem.getPath("/");
            Path directory = Files.createDirectory(root.resolve("config"));
            Path regular = Files.writeString(directory.resolve("legacy.toml"), "x");
            Path target = directory.resolve("target.toml");

            assertNull(attributes(root).fileKey(), "root precondition");
            assertNull(attributes(directory).fileKey(), "directory precondition");
            assertNull(attributes(regular).fileKey(), "regular-file precondition");

            JdkMigrationFileSystem migrationFileSystem =
                    new JdkMigrationFileSystem();
            assertThrows(
                    IOException.class,
                    () -> migrationFileSystem.observeBinding(target),
                    "a non-Windows null-key provider must not bind ancestors");
            assertEquals(
                    MigrationPathState.UNKNOWN,
                    migrationFileSystem.classify(regular),
                    "a non-Windows null-key regular file must remain UNKNOWN");
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
    }
}
