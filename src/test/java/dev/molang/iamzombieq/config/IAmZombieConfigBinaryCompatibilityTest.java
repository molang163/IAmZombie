package dev.molang.iamzombieq.config;

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

import org.junit.jupiter.api.Test;

/**
 * Guards the 1.0.3 public binary shape of {@code IAmZombieConfig} without loading or initializing it.
 */
class IAmZombieConfigBinaryCompatibilityTest {
    private static final String OWNER = "dev/molang/iamzombieq/IAmZombieConfig";
    private static final Path CLASS_FILE = Path.of(
            "build/classes/java/main/dev/molang/iamzombieq/IAmZombieConfig.class");
    private static final String FIXTURE =
            "/dev/molang/iamzombieq/config/iamzombieconfig-public-binary-1.0.3.tsv";
    private static final Comparator<AbiEntry> ABI_ORDER = Comparator
            .comparing(AbiEntry::kind)
            .thenComparing(AbiEntry::owner)
            .thenComparing(AbiEntry::name)
            .thenComparing(AbiEntry::descriptor)
            .thenComparing(AbiEntry::access)
            .thenComparing(AbiEntry::signature);

    @Test
    void publicBinaryShapeMatchesTheReviewed103Fixture() throws IOException {
        List<AbiEntry> expected = readFixture();
        List<AbiEntry> actual = readCompiledClass();

        assertNoDuplicates("reviewed fixture", expected);
        assertNoDuplicates("compiled class", actual);
        assertFixtureCoverage(expected);

        assertEquals(sorted(expected), sorted(actual),
                "IAmZombieConfig public owner/name/access/descriptor/Signature must remain binary-compatible");
    }

    private static List<AbiEntry> readCompiledClass() throws IOException {
        assertTrue(Files.isRegularFile(CLASS_FILE), "compiled IAmZombieConfig.class must exist at " + CLASS_FILE);

        // ClassFile.parse reads the class-file bytes only. It neither defines the class nor runs <clinit>.
        ClassModel model = ClassFile.of().parse(CLASS_FILE);
        String owner = model.thisClass().asInternalName();
        List<AbiEntry> entries = new ArrayList<>();
        entries.add(new AbiEntry("CLASS", owner, "-", access(model.flags().flagsMask()), "-", signature(model)));

        model.fields().stream()
                .filter(field -> field.flags().has(AccessFlag.PUBLIC))
                .map(field -> new AbiEntry(
                        "FIELD",
                        owner,
                        field.fieldName().stringValue(),
                        access(field.flags().flagsMask()),
                        field.fieldType().stringValue(),
                        signature(field)))
                .forEach(entries::add);

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
        return entries;
    }

    private static List<AbiEntry> readFixture() throws IOException {
        InputStream stream = IAmZombieConfigBinaryCompatibilityTest.class.getResourceAsStream(FIXTURE);
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
        List<AbiEntry> classes = entries.stream().filter(entry -> entry.kind().equals("CLASS")).toList();
        List<AbiEntry> fields = entries.stream().filter(entry -> entry.kind().equals("FIELD")).toList();
        List<AbiEntry> methods = entries.stream().filter(entry -> entry.kind().equals("METHOD")).toList();

        assertEquals(List.of(new AbiEntry("CLASS", OWNER, "-", "0x0031", "-", "-")), classes,
                "fixture must pin the exact public/final/super class modifiers");
        assertEquals(56, fields.size(), "fixture must contain 55 legacy ConfigValue fields plus SPEC");
        assertEquals(55, fields.stream().filter(field -> !field.name().equals("SPEC")).count(),
                "fixture must explicitly cover all 55 legacy ConfigValue fields");
        assertEquals(1, fields.stream().filter(field -> field.name().equals("SPEC")).count(),
                "fixture must contain SPEC exactly once");
        assertEquals("Lnet/neoforged/neoforge/common/ModConfigSpec;",
                fields.stream().filter(field -> field.name().equals("SPEC")).findFirst().orElseThrow().descriptor(),
                "SPEC descriptor is part of K1");

        Map<String, String> helperDescriptors = methods.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(AbiEntry::name, AbiEntry::descriptor));
        assertEquals(Map.of(
                "configuredZombieFoods", "()Ljava/util/Set;",
                "configuredInnateArmor", "(Ldev/molang/iamzombieq/rules/core/ZombieForm;)I",
                "configuredInfectionChance", "(Ldev/molang/iamzombieq/rules/difficulty/GameDifficulty;)D"),
                helperDescriptors,
                "fixture must explicitly cover the three public helper methods and their JVM descriptors");
        assertTrue(entries.stream().allMatch(entry -> entry.owner().equals(OWNER)),
                "every fixture entry must pin the original JVM owner");
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
