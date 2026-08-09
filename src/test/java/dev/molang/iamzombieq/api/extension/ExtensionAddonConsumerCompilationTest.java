package dev.molang.iamzombieq.api.extension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles an external-package addon fixture against production class files. The compiler reads classpath bytes only;
 * this test does not reflect on, define, or initialize any production class.
 */
class ExtensionAddonConsumerCompilationTest {
    private static final String FIXTURE =
            "/dev/molang/iamzombieq/api/extension/Low14FoodAddon.java";

    @TempDir
    Path tempDirectory;

    @Test
    void externalAddonCompilesAgainstTheSupportedFoodRegisterOverload() throws IOException {
        String fixtureSource;
        try (InputStream stream = ExtensionAddonConsumerCompilationTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(stream, "missing external addon source fixture " + FIXTURE);
            fixtureSource = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(fixtureSource.contains("package external.addon.low14;"),
                "the consumer fixture must remain outside the mod's package");
        assertTrue(fixtureSource.contains("IZombieExtensions.register(provider);"),
                "the consumer fixture must call the supported food-provider register overload");
        assertFalse(fixtureSource.contains("foodRuleProviders()")
                        || fixtureSource.contains("attackerHooks()"),
                "the external consumer must not call Internal registry accessors");

        Path sourceRoot = tempDirectory.resolve("source");
        Path sourceFile = sourceRoot.resolve("external/addon/low14/Low14FoodAddon.java");
        Path outputDirectory = tempDirectory.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(outputDirectory);
        Files.writeString(sourceFile, fixtureSource, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the active node's JDK compiler must be available to compile the addon fixture");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile);
            List<String> options = List.of(
                    "-proc:none",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outputDirectory.toString());
            boolean compiled = Boolean.TRUE.equals(
                    compiler.getTask(null, fileManager, diagnostics, options, null, units).call());

            assertTrue(compiled, () -> "external addon fixture must compile through IZombieExtensions.register:"
                    + System.lineSeparator() + formatDiagnostics(diagnostics));
        }

        assertTrue(Files.isRegularFile(
                        outputDirectory.resolve("external/addon/low14/Low14FoodAddon.class")),
                "addon compilation output must stay inside the JUnit temporary directory");
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind()
                        + " line " + diagnostic.getLineNumber()
                        + ": " + diagnostic.getMessage(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }
}
