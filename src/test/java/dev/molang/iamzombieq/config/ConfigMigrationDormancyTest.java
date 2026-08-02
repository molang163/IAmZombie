package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigMigrationDormancyTest {
    private static final Path MAIN_ROOT =
            Path.of("src/main/java").toAbsolutePath().normalize();
    private static final Path CONFIG_ROOT =
            MAIN_ROOT.resolve("dev/molang/iamzombieq/config");
    private static final Set<String> DORMANT_CORE_TYPES = Set.of(
            "ActualTargetResolver",
            "AtomicConfigPublisher",
            "ConfigMigrationEngine",
            "ConfigProjection",
            "ConfigProjectionCodec",
            "ConfigSchemaCatalog",
            "JdkMigrationFileSystem",
            "LegacyConfigParser",
            "MigrationAccessProfile",
            "MigrationBinding",
            "MigrationDirectorySession",
            "MigrationEvidence",
            "MigrationEvidenceCodec",
            "MigrationFailure",
            "MigrationFaultInjector",
            "MigrationFileSystem",
            "MigrationJournal",
            "MigrationMarker",
            "MigrationMetadataBootstrap",
            "MigrationPathState",
            "MigrationTarget",
            "MigrationTargetState",
            "PermanentMigrationLock",
            "TargetConfigValidator");

    @Test
    void activatedCoreRemainsEncapsulatedBehindTheReviewedBridge()
            throws IOException {
        for (Path path : productionSources()) {
            if (path.startsWith(CONFIG_ROOT)) {
                continue;
            }
            String executable = stripCommentsAndLiterals(Files.readString(path));
            for (String type : DORMANT_CORE_TYPES) {
                assertFalse(
                        Pattern.compile("\\b" + Pattern.quote(type) + "\\b")
                                .matcher(executable)
                                .find(),
                        () -> "package-private migration type "
                                + type
                                + " escaped its reviewed bridge through "
                                + MAIN_ROOT.relativize(path));
            }
        }
    }

    @Test
    void commonConfigPackageContainsNoClientClassOrAudioLinkage()
            throws IOException {
        String core = executableConfigSources();
        for (String forbidden : List.of(
                "registerConfig(",
                "registerConfig (",
                "@SubscribeEvent",
                "EventBusSubscriber",
                "net.minecraft.client",
                "dev.molang.iamzombieq.client",
                "Dist.CLIENT",
                "runData",
                "runServer",
                "GameTest",
                "javax.sound",
                "OpenAL")) {
            assertFalse(
                    core.contains(forbidden),
                    () -> "common config code acquired forbidden hook/reference "
                            + forbidden);
        }
    }

    @Test
    void migrationArtifactNamesRemainConfinedToConfigCore()
            throws IOException {
        for (Path path : productionSources()) {
            if (path.startsWith(CONFIG_ROOT)) {
                continue;
            }
            String source = Files.readString(path);
            assertFalse(
                    source.contains(".iamzombieq-migration-v1"),
                    () -> "migration artifact path escaped dormant core through "
                            + MAIN_ROOT.relativize(path));
        }
    }

    @Test
    void frozenMigrationCoreTypesRemainPackagePrivateInTheCommonConfigPackage()
            throws IOException {
        for (String type : DORMANT_CORE_TYPES) {
            Path source = CONFIG_ROOT.resolve(type + ".java");
            assertTrue(
                    Files.isRegularFile(source),
                    () -> "missing migration core source " + source);
            String declaration = stripCommentsAndLiterals(Files.readString(source));
            assertTrue(
                    declaration.contains("package dev.molang.iamzombieq.config;"),
                    () -> type + " left the common config package");
            assertFalse(
                    Pattern.compile("\\bpublic\\s+(?:final\\s+)?(?:class|interface|record|enum)\\s+"
                                    + Pattern.quote(type)
                                    + "\\b")
                            .matcher(declaration)
                            .find(),
                    () -> type + " became a public production API");
        }
    }

    @Test
    void migrationCoreNeverResolvesAnyConfigHolder()
            throws IOException {
        for (String type : DORMANT_CORE_TYPES) {
            Path path = CONFIG_ROOT.resolve(type + ".java");
                String raw = Files.readString(path);
                String executable = stripCommentsAndLiterals(raw);
                for (String holder : List.of(
                        "IAmZombieConfig",
                        "IAmZombieServerConfig",
                        "IAmZombiePreferencesConfig")) {
                    assertFalse(
                            Pattern.compile("\\b" + holder + "\\b")
                                    .matcher(executable)
                                    .find(),
                            () -> "migration core resolved holder "
                                    + holder
                                    + " from "
                                    + path);
                    assertFalse(
                            raw.contains("Class.forName")
                                    && raw.contains(holder),
                            () -> "migration core reflectively resolved holder "
                                    + holder
                                    + " from "
                                    + path);
                }
        }
    }

    private static List<Path> productionSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN_ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String executableConfigSources() throws IOException {
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> paths = Files.walk(CONFIG_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                combined.append(stripCommentsAndLiterals(Files.readString(path)))
                        .append('\n');
            }
        }
        return combined.toString();
    }

    private static String stripCommentsAndLiterals(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean string = false;
        boolean character = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next =
                    index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    code.append('\n');
                } else {
                    code.append(' ');
                }
            } else if (blockComment) {
                if (current == '*' && next == '/') {
                    code.append("  ");
                    index++;
                    blockComment = false;
                } else {
                    code.append(current == '\n' ? '\n' : ' ');
                }
            } else if (string) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && index + 1 < source.length()) {
                    code.append(' ');
                    index++;
                } else if (current == '"') {
                    string = false;
                }
            } else if (character) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && index + 1 < source.length()) {
                    code.append(' ');
                    index++;
                } else if (current == '\'') {
                    character = false;
                }
            } else if (current == '/' && next == '/') {
                code.append("  ");
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                code.append("  ");
                index++;
                blockComment = true;
            } else if (current == '"') {
                code.append(' ');
                string = true;
            } else if (current == '\'') {
                code.append(' ');
                character = true;
            } else {
                code.append(current);
            }
        }
        return code.toString();
    }
}
