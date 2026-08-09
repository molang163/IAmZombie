package dev.molang.iamzombieq.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.ClassFileAbiReader;
import dev.molang.iamzombieq.util.ClassFileAbiReader.ClassInfo;
import dev.molang.iamzombieq.util.ClassFileAbiReader.MemberInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Guards the reviewed 1.x binary shape of the stable player facade and Transform/Evolve event DTOs without loading
 * or initializing any production class.
 */
class MainBodyApiBinaryCompatibilityTest {
    private static final String MAIN_CLASSES_DIR_PROPERTY = "iamzombieq.test.mainClassesDir";
    private static final String FIXTURE =
            "/dev/molang/iamzombieq/api/main-body-public-binary-1.x.tsv";
    private static final Map<String, Integer> EXPECTED_PUBLIC_METHOD_COUNTS = Map.of(
            "dev/molang/iamzombieq/api/core/IZombiePlayerAPI", 1,
            "dev/molang/iamzombieq/api/core/IZombiePlayer", 10,
            "dev/molang/iamzombieq/api/event/ZombieTransformPreEvent", 4,
            "dev/molang/iamzombieq/api/event/ZombieTransformedEvent", 4,
            "dev/molang/iamzombieq/api/event/ZombieEvolvePreEvent", 5,
            "dev/molang/iamzombieq/api/event/ZombieEvolvedEvent", 5);
    private static final Comparator<AbiEntry> ABI_ORDER = Comparator
            .comparing(AbiEntry::kind)
            .thenComparing(AbiEntry::owner)
            .thenComparing(AbiEntry::name)
            .thenComparing(AbiEntry::descriptor)
            .thenComparing(AbiEntry::access)
            .thenComparing(AbiEntry::signature);

    @Test
    void stableMainBodyBinaryShapeMatchesReviewedFixture() throws IOException {
        List<AbiEntry> expected = readFixture();
        List<AbiEntry> actual = readCompiledClasses();

        assertNoDuplicates("public compatibility fixture", expected);
        assertNoDuplicates("compiled classes", actual);
        assertFixtureCoverage(expected);

        assertEquals(sorted(expected), sorted(actual),
                "stable main-body API owner/modifiers/name/descriptor/Signature must remain binary-compatible");
    }

    private static List<AbiEntry> readCompiledClasses() throws IOException {
        List<AbiEntry> entries = new ArrayList<>();
        for (String expectedOwner : EXPECTED_PUBLIC_METHOD_COUNTS.keySet()) {
            Path classFile = compiledClassRoot().resolve(expectedOwner + ".class");
            assertTrue(Files.isRegularFile(classFile), "compiled API class must exist at " + classFile);

            // The reader consumes class-file bytes only. It neither defines the class nor runs <clinit>.
            ClassInfo model = ClassFileAbiReader.read(classFile);
            //? if >=1.21.11 {
            assertEquals(
                    dev.molang.iamzombieq.util.JdkClassFileAbiOracle.read(classFile),
                    model,
                    "Java 21-safe reader must match the independent JDK class-file oracle for " + expectedOwner);
            //?}
            String owner = model.internalName();
            assertEquals(expectedOwner, owner, "class-file owner must match its stable JVM name");
            entries.add(new AbiEntry(
                    "CLASS", owner, "-", access(model.accessFlags()), "-", model.signature().orElse("-")));

            model.methods().stream()
                    .filter(MemberInfo::isPublic)
                    .map(method -> new AbiEntry(
                            "METHOD",
                            owner,
                            method.name(),
                            access(method.accessFlags()),
                            method.descriptor(),
                            method.signature().orElse("-")))
                    .forEach(entries::add);
        }
        return entries;
    }

    private static Path compiledClassRoot() {
        String configured = System.getProperty(MAIN_CLASSES_DIR_PROPERTY);
        assertNotNull(configured, "Gradle must inject the active node's compiled main class directory");
        return Path.of(configured);
    }

    private static List<AbiEntry> readFixture() throws IOException {
        InputStream stream = MainBodyApiBinaryCompatibilityTest.class.getResourceAsStream(FIXTURE);
        assertNotNull(stream, "missing reviewed ABI fixture " + FIXTURE);

        List<AbiEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(6, columns.length, "fixture row must have six TSV columns: " + line);
                entries.add(new AbiEntry(
                        columns[0], columns[1], columns[2], columns[3], columns[4], columns[5]));
            }
        }
        return entries;
    }

    private static void assertFixtureCoverage(List<AbiEntry> entries) {
        Map<String, List<AbiEntry>> byOwner = entries.stream()
                .collect(Collectors.groupingBy(AbiEntry::owner));
        assertEquals(EXPECTED_PUBLIC_METHOD_COUNTS.keySet(), byOwner.keySet(),
                "fixture must cover exactly the six reviewed stable main-body API owners");

        for (Map.Entry<String, Integer> expected : EXPECTED_PUBLIC_METHOD_COUNTS.entrySet()) {
            List<AbiEntry> ownerEntries = byOwner.get(expected.getKey());
            assertEquals(1, ownerEntries.stream().filter(entry -> entry.kind().equals("CLASS")).count(),
                    expected.getKey() + " must have exactly one CLASS row");
            assertEquals(expected.getValue().longValue(),
                    ownerEntries.stream().filter(entry -> entry.kind().equals("METHOD")).count(),
                    expected.getKey() + " must pin every current public method");
        }

        assertTrue(entries.stream().allMatch(entry ->
                        entry.kind().equals("CLASS") || entry.kind().equals("METHOD")),
                "main-body fixture may contain only CLASS and public METHOD rows");
        assertTrue(entries.stream().allMatch(entry -> entry.signature().equals("-")),
                "the reviewed stable main-body types currently have no generic Signature attributes");
    }

    private static void assertNoDuplicates(String source, List<AbiEntry> entries) {
        Set<AbiEntry> unique = new HashSet<>(entries);
        assertEquals(entries.size(), unique.size(), source + " must not contain duplicate ABI rows");
    }

    private static List<AbiEntry> sorted(List<AbiEntry> entries) {
        return entries.stream().sorted(ABI_ORDER).toList();
    }

    private static String access(int flags) {
        return String.format(Locale.ROOT, "0x%04x", flags);
    }

    private record AbiEntry(
            String kind,
            String owner,
            String name,
            String access,
            String descriptor,
            String signature) {
    }
}
