package dev.molang.iamzombieq.api.extension;

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
 * Guards the reviewed extension API class-file shape without loading or initializing any production class.
 */
class ExtensionApiBinaryCompatibilityTest {
    private static final Path CLASS_ROOT = Path.of("build/classes/java/main");
    private static final String FIXTURE =
            "/dev/molang/iamzombieq/api/extension/api-extension-public-binary-1.x.tsv";
    private static final String EXPERIMENTAL =
            "Lorg/jetbrains/annotations/ApiStatus$Experimental;";
    private static final Map<String, Integer> EXPECTED_PUBLIC_METHOD_COUNTS = Map.of(
            "dev/molang/iamzombieq/api/extension/IZombieExtensions", 4,
            "dev/molang/iamzombieq/api/extension/IFoodRuleProvider", 1,
            "dev/molang/iamzombieq/api/extension/IAttackerHook", 1,
            "dev/molang/iamzombieq/api/extension/AttackerDecision", 2);
    private static final Map<String, Integer> EXPECTED_PUBLIC_FIELD_COUNTS = Map.of(
            "dev/molang/iamzombieq/api/extension/IZombieExtensions", 0,
            "dev/molang/iamzombieq/api/extension/IFoodRuleProvider", 0,
            "dev/molang/iamzombieq/api/extension/IAttackerHook", 0,
            "dev/molang/iamzombieq/api/extension/AttackerDecision", 4);
    private static final Comparator<AbiEntry> ABI_ORDER = Comparator
            .comparing(AbiEntry::kind)
            .thenComparing(AbiEntry::owner)
            .thenComparing(AbiEntry::name)
            .thenComparing(AbiEntry::descriptor)
            .thenComparing(AbiEntry::access)
            .thenComparing(AbiEntry::signature)
            .thenComparing(AbiEntry::annotations);

    @Test
    void extensionApiBinaryShapeMatchesReviewedFixture() throws IOException {
        List<AbiEntry> expected = readFixture();
        List<AbiEntry> actual = readCompiledClasses();

        assertNoDuplicates("reviewed fixture", expected);
        assertNoDuplicates("compiled classes", actual);
        assertFixtureCoverage(expected);
        assertExperimentalMarkers(expected);

        assertEquals(sorted(expected), sorted(actual),
                "extension API owner/modifiers/name/descriptor/Signature/annotations must match the reviewed fixture");
    }

    private static List<AbiEntry> readCompiledClasses() throws IOException {
        List<AbiEntry> entries = new ArrayList<>();
        for (String expectedOwner : EXPECTED_PUBLIC_METHOD_COUNTS.keySet()) {
            Path classFile = CLASS_ROOT.resolve(expectedOwner + ".class");
            assertTrue(Files.isRegularFile(classFile), "compiled extension API class must exist at " + classFile);

            // ClassFile.parse reads class-file bytes only. It neither defines the class nor runs <clinit>.
            ClassModel model = ClassFile.of().parse(classFile);
            String owner = model.thisClass().asInternalName();
            assertEquals(expectedOwner, owner, "class-file owner must match its reviewed JVM name");
            entries.add(new AbiEntry(
                    "CLASS", owner, "-", access(model.flags().flagsMask()), "-", signature(model), annotations(model)));

            model.fields().stream()
                    .filter(field -> field.flags().has(AccessFlag.PUBLIC))
                    .map(field -> new AbiEntry(
                            "FIELD",
                            owner,
                            field.fieldName().stringValue(),
                            access(field.flags().flagsMask()),
                            field.fieldType().stringValue(),
                            signature(field),
                            annotations(field)))
                    .forEach(entries::add);

            model.methods().stream()
                    .filter(method -> method.flags().has(AccessFlag.PUBLIC))
                    .map(method -> new AbiEntry(
                            "METHOD",
                            owner,
                            method.methodName().stringValue(),
                            access(method.flags().flagsMask()),
                            method.methodType().stringValue(),
                            signature(method),
                            annotations(method)))
                    .forEach(entries::add);
        }
        return entries;
    }

    private static List<AbiEntry> readFixture() throws IOException {
        InputStream stream = ExtensionApiBinaryCompatibilityTest.class.getResourceAsStream(FIXTURE);
        assertNotNull(stream, "missing reviewed ABI fixture " + FIXTURE);

        List<AbiEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(7, columns.length, "fixture row must have seven TSV columns: " + line);
                entries.add(new AbiEntry(
                        columns[0], columns[1], columns[2], columns[3],
                        columns[4], columns[5], columns[6]));
            }
        }
        return entries;
    }

    private static void assertFixtureCoverage(List<AbiEntry> entries) {
        Map<String, List<AbiEntry>> byOwner = entries.stream()
                .collect(Collectors.groupingBy(AbiEntry::owner));
        assertEquals(EXPECTED_PUBLIC_METHOD_COUNTS.keySet(), byOwner.keySet(),
                "fixture must cover exactly the four reviewed api/extension owners");

        for (String owner : EXPECTED_PUBLIC_METHOD_COUNTS.keySet()) {
            List<AbiEntry> ownerEntries = byOwner.get(owner);
            assertEquals(1, ownerEntries.stream().filter(entry -> entry.kind().equals("CLASS")).count(),
                    owner + " must have exactly one CLASS row");
            assertEquals(EXPECTED_PUBLIC_METHOD_COUNTS.get(owner).longValue(),
                    ownerEntries.stream().filter(entry -> entry.kind().equals("METHOD")).count(),
                    owner + " must pin every current public method");
            assertEquals(EXPECTED_PUBLIC_FIELD_COUNTS.get(owner).longValue(),
                    ownerEntries.stream().filter(entry -> entry.kind().equals("FIELD")).count(),
                    owner + " must pin every current public field");
        }

        assertTrue(entries.stream().allMatch(entry ->
                        entry.kind().equals("CLASS")
                                || entry.kind().equals("FIELD")
                                || entry.kind().equals("METHOD")),
                "extension fixture may contain only CLASS, public FIELD, and public METHOD rows");
    }

    private static void assertExperimentalMarkers(List<AbiEntry> entries) {
        for (String owner : List.of(
                "dev/molang/iamzombieq/api/extension/IAttackerHook",
                "dev/molang/iamzombieq/api/extension/AttackerDecision")) {
            AbiEntry classEntry = entries.stream()
                    .filter(entry -> entry.kind().equals("CLASS") && entry.owner().equals(owner))
                    .findFirst()
                    .orElseThrow();
            assertTrue(List.of(classEntry.annotations().split(",")).contains(EXPERIMENTAL),
                    owner + " must retain its class-file @ApiStatus.Experimental marker");
        }
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

    private static String annotations(AttributedElement element) {
        List<String> annotations = new ArrayList<>();
        element.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(attribute -> attribute.annotations().forEach(
                        annotation -> annotations.add(annotation.className().stringValue())));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(attribute -> attribute.annotations().forEach(
                        annotation -> annotations.add(annotation.className().stringValue())));
        return annotations.isEmpty()
                ? "-"
                : annotations.stream().sorted().collect(Collectors.joining(","));
    }

    private record AbiEntry(
            String kind,
            String owner,
            String name,
            String access,
            String descriptor,
            String signature,
            String annotations) {
    }
}
