package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MigrationNamespaceGuardTest {
    private static final Path CONFIG_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/config")
                    .toAbsolutePath()
                    .normalize();

    @Test
    void cooperativeMigratorCannotRetargetParent() throws IOException {
        String combined = productionCore();
        for (String forbidden : List.of(
                "Files.createSymbolicLink(",
                "Files.createLink(",
                "Files.delete(",
                "Files.deleteIfExists(",
                "Files.setAttribute(",
                "Files.createDirectory(",
                "Files.createDirectories(",
                ".renameTo(",
                "moveDirectory(",
                "replaceDirectory(",
                "ProcessBuilder(",
                "Runtime.getRuntime().exec(")) {
            assertFalse(
                    combined.contains(forbidden),
                    () -> "cooperative migration core can mutate its parent "
                            + "namespace through "
                            + forbidden);
        }

        String jdk = Files.readString(
                CONFIG_SOURCE.resolve("JdkMigrationFileSystem.java"));
        assertEquals(1, occurrences(jdk, "Files.move("));
        assertEquals(1, occurrences(jdk, "directory.move("));

        String lexicalMove = between(
                jdk,
                "Files.move(",
                "verifyBinding();",
                jdk.indexOf("Files.move("));
        assertTrue(lexicalMove.contains(
                "binding.physicalParent().resolve(checkedSource)"));
        assertTrue(lexicalMove.contains(
                "binding.physicalParent().resolve(checkedDestination)"));
        assertTrue(lexicalMove.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertFalse(lexicalMove.contains("StandardCopyOption.REPLACE_EXISTING"));

        String secureMove = between(
                jdk,
                "directory.move(",
                "verifyBinding();",
                jdk.indexOf("directory.move("));
        assertTrue(secureMove.contains("Path.of(checkedSource)"));
        assertTrue(secureMove.contains("Path.of(checkedDestination)"));
        assertFalse(secureMove.contains("binding.logicalParent()"));
        assertFalse(secureMove.contains("binding.physicalParent()"));

        assertTrue(jdk.contains(".emptyLockRecoveryPolicy("));
        assertTrue(jdk.contains("request.profile(), binding"));
        assertFalse(jdk.contains(
                "EmptyLockRecoveryPolicy.EXACT_FILE_KEY"));
        String lockVerify = between(
                jdk,
                "public void verifyBound(",
                "private void verifyPathnameReopensAsHeldFile(",
                jdk.indexOf("public void verifyBound("));
        assertTrue(lockVerify.contains(
                "if (profile == MigrationAccessProfile.BASIC)"));
        assertTrue(lockVerify.contains("verifyPathnameReopensAsHeldFile("));
        assertFalse(lockVerify.contains("backend.openNofollow("));
        assertTrue(jdk.contains("backend.openFile("));
        assertTrue(jdk.contains("requireSamePathnameMetadata("));
        assertTrue(jdk.contains("rebound.tryLock()"));
        assertTrue(jdk.contains("OverlappingFileLockException"));
    }

    private static String productionCore() throws IOException {
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> sources = Files.walk(CONFIG_SOURCE)) {
            for (Path source : sources.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                combined.append(Files.readString(source)).append('\n');
            }
        }
        return combined.toString();
    }

    private static String between(
            String source, String start, String end, int startAt) {
        int first = source.indexOf(start, startAt);
        int last = source.indexOf(end, first + start.length());
        if (first < 0 || last < 0 || last <= first) {
            throw new IllegalArgumentException(
                    "source markers are missing or out of order");
        }
        return source.substring(first, last);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle);
                index >= 0;
                index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
