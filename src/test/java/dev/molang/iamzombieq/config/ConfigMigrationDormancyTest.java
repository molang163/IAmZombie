package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigMigrationDormancyTest {
    private static final Path MAIN_ROOT =
            Path.of("src/main/java").toAbsolutePath().normalize();
    private static final Path CONFIG_ROOT =
            MAIN_ROOT.resolve("dev/molang/iamzombieq/config");
    private static final Path MAIN_TEMPLATE_ROOT =
            Path.of("src/main/java-templates").toAbsolutePath().normalize();
    private static final Path CONFIG_TEMPLATE_ROOT =
            MAIN_TEMPLATE_ROOT.resolve("dev/molang/iamzombieq/config");
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
            "MigrationIdentityPolicy",
            "MigrationJournal",
            "MigrationJavaRuntimeMatrix",
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
            if (path.startsWith(CONFIG_ROOT)
                    || path.startsWith(CONFIG_TEMPLATE_ROOT)) {
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
    void filesystemCapabilityObservationUsesTheCentralRuntimeMatrix()
            throws IOException {
        Path fileSystemSource =
                CONFIG_ROOT.resolve("JdkMigrationFileSystem.java");
        String fileSystem =
                stripCommentsAndLiterals(Files.readString(fileSystemSource));
        assertTrue(
                Pattern.compile(
                                "JdkMigrationFileSystem\\s*\\(\\s*"
                                        + "ContentOpenHook\\s+contentOpenHook\\s*\\)"
                                        + "\\s*\\{\\s*this\\s*\\(\\s*"
                                        + "contentOpenHook\\s*,\\s*"
                                        + "Runtime\\.version\\(\\)\\.feature\\(\\)"
                                        + "\\s*\\)\\s*;\\s*\\}",
                                Pattern.DOTALL)
                        .matcher(fileSystem)
                        .find(),
                "the production hook constructor must observe the real runtime");

        String profile = stripCommentsAndLiterals(Files.readString(
                CONFIG_ROOT.resolve("MigrationAccessProfile.java")));
        assertEquals(
                0,
                Pattern.compile("\\bjavaFeature\\s*==\\s*\\d+\\b")
                        .matcher(profile)
                        .results()
                        .count(),
                "profile selection must not duplicate Java feature numbers");
        assertEquals(
                1,
                Pattern.compile("supportsSecureProfile\\(javaFeature\\)")
                        .matcher(profile)
                        .results()
                        .count());
        assertEquals(
                1,
                Pattern.compile("supportsBasicProfile\\(javaFeature\\)")
                        .matcher(profile)
                        .results()
                        .count());

        String identityPolicy = stripCommentsAndLiterals(Files.readString(
                CONFIG_ROOT.resolve("MigrationIdentityPolicy.java")));
        assertEquals(
                0,
                Pattern.compile("\\bjavaFeature\\s*==\\s*\\d+\\b")
                        .matcher(identityPolicy)
                        .results()
                        .count(),
                "Windows identity admission must not duplicate Java features");
        assertEquals(
                1,
                Pattern.compile("supportsBasicProfile\\(javaFeature\\)")
                        .matcher(identityPolicy)
                        .results()
                        .count(),
                "Windows identity admission must use the generated matrix");
    }

    @Test
    void generatedRuntimeMatrixStaysPackagePrivateWithReviewedConsumersOnly()
            throws IOException {
        assertFalse(
                Modifier.isPublic(MigrationJavaRuntimeMatrix.class.getModifiers()),
                "the generated runtime matrix must not become public API");
        assertFalse(
                Modifier.isProtected(MigrationJavaRuntimeMatrix.class.getModifiers()),
                "the generated runtime matrix must remain package-private");

        Path template = coreSource("MigrationJavaRuntimeMatrix");
        Set<Path> consumers = productionSources().stream()
                .filter(path -> !path.equals(template))
                .filter(path -> {
                    try {
                        return Pattern.compile("\\bMigrationJavaRuntimeMatrix\\b")
                                .matcher(stripCommentsAndLiterals(
                                        Files.readString(path)))
                                .find();
                    } catch (IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(
                        CONFIG_ROOT.resolve("MigrationAccessProfile.java"),
                        CONFIG_ROOT.resolve("MigrationIdentityPolicy.java"),
                        CONFIG_ROOT.resolve("ProductionConfigMigration.java")),
                consumers,
                "the generated policy must remain behind the three reviewed bridges");
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
    void migrationCoreHasNoNativeReflectionOrCopyDeleteFallback()
            throws IOException {
        String core = executableConfigSources();
        for (String forbidden : List.of(
                "Class.forName",
                "java.lang.reflect",
                "java.lang.invoke",
                "MethodHandles",
                "setAccessible(",
                "trySetAccessible(",
                "System.load(",
                "System.loadLibrary(",
                "java.lang.foreign",
                "Linker",
                "SymbolLookup",
                "sun.misc.Unsafe",
                "jdk.internal.misc.Unsafe",
                "com.sun.jna",
                "jnr.",
                "ProcessBuilder",
                "Runtime.getRuntime().exec(",
                "Files.copy(",
                "Files.delete(",
                "Files.deleteIfExists(",
                "REPLACE_EXISTING")) {
            assertFalse(
                    core.contains(forbidden),
                    () -> "migration core acquired forbidden fallback/linkage "
                            + forbidden);
        }
        assertFalse(
                Pattern.compile("\\bnative\\b").matcher(core).find(),
                "migration core must stay pure Java");
        assertEquals(
                1,
                Pattern.compile("Files\\.move\\s*\\(")
                        .matcher(core)
                        .results()
                        .count(),
                "BASIC must retain one publication call");
        assertEquals(
                1,
                Pattern.compile("StandardCopyOption\\.ATOMIC_MOVE")
                        .matcher(core)
                        .results()
                        .count(),
                "BASIC publication must remain atomic-only");
        assertEquals(
                1,
                Pattern.compile("directory\\.move\\s*\\(")
                        .matcher(core)
                        .results()
                        .count(),
                "SECURE must retain one relative publication call");
    }

    @Test
    void migrationArtifactNamesRemainConfinedToConfigCore()
            throws IOException {
        for (Path path : productionSources()) {
            if (path.startsWith(CONFIG_ROOT)
                    || path.startsWith(CONFIG_TEMPLATE_ROOT)) {
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
            Path source = coreSource(type);
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
            Path path = coreSource(type);
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
        List<Path> sources = new ArrayList<>();
        sources.addAll(javaSources(MAIN_ROOT));
        sources.addAll(javaSources(MAIN_TEMPLATE_ROOT));
        return sources.stream().sorted().toList();
    }

    private static String executableConfigSources() throws IOException {
        StringBuilder combined = new StringBuilder();
        for (Path root : List.of(CONFIG_ROOT, CONFIG_TEMPLATE_ROOT)) {
            for (Path path : javaSources(root)) {
                combined.append(stripCommentsAndLiterals(Files.readString(path)))
                        .append('\n');
            }
        }
        return combined.toString();
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path coreSource(String type) {
        Path root = type.equals("MigrationJavaRuntimeMatrix")
                ? CONFIG_TEMPLATE_ROOT
                : CONFIG_ROOT;
        return root.resolve(type + ".java");
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
