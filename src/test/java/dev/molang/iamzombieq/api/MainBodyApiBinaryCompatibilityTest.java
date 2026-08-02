package dev.molang.iamzombieq.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.AccessFlag;
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
    private static final Path CLASS_ROOT = Path.of("build/classes/java/main");
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
            Path classFile = CLASS_ROOT.resolve(expectedOwner + ".class");
            assertTrue(Files.isRegularFile(classFile), "compiled API class must exist at " + classFile);

            // ClassFile.parse reads class-file bytes only. It neither defines the class nor runs <clinit>.
            ClassModel model = ClassFile.of().parse(classFile);
            String owner = model.thisClass().asInternalName();
            assertEquals(expectedOwner, owner, "class-file owner must match its stable JVM name");
            entries.add(new AbiEntry("CLASS", owner, "-", access(model.flags().flagsMask()), "-", signature(model)));

            model.methods().stream()
                    .filter(method -> method.flags().has(AccessFlag.PUBLIC))
                    .map(method -> new AbiEntry(
                            "METHOD",
                            owner,
                            method.methodName().stringValue(),
                            access(method.flags().flagsMask()),
                            method.methodType().stringValue(),
                            signature(method)))
                    .forEach(entries::add);
        }
        return entries;
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

    private static String signature(AttributedElement element) {
        return element.findAttribute(Attributes.signature())
                .map(attribute -> attribute.signature().stringValue())
                .orElse("-");
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
