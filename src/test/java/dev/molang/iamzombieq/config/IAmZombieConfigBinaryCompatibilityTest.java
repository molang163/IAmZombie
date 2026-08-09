package dev.molang.iamzombieq.config;

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

import org.junit.jupiter.api.Test;

/**
 * Guards the 1.0.3 public binary shape of {@code IAmZombieConfig} without loading or initializing it.
 */
class IAmZombieConfigBinaryCompatibilityTest {
    private static final String OWNER = "dev/molang/iamzombieq/IAmZombieConfig";
    private static final String MAIN_CLASSES_DIR_PROPERTY = "iamzombieq.test.mainClassesDir";
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

        assertNoDuplicates("public compatibility fixture", expected);
        assertNoDuplicates("compiled class", actual);
        assertFixtureCoverage(expected);

        assertEquals(sorted(expected), sorted(actual),
                "IAmZombieConfig public owner/name/access/descriptor/Signature must remain binary-compatible");
    }

    private static List<AbiEntry> readCompiledClass() throws IOException {
        Path classFile = compiledClassRoot().resolve("dev/molang/iamzombieq/IAmZombieConfig.class");
        assertTrue(Files.isRegularFile(classFile), "compiled IAmZombieConfig.class must exist at " + classFile);

        // The reader consumes class-file bytes only. It neither defines the class nor runs <clinit>.
        ClassInfo model = ClassFileAbiReader.read(classFile);
        //? if >=1.21.11 {
        assertEquals(
                dev.molang.iamzombieq.util.JdkClassFileAbiOracle.read(classFile),
                model,
                "Java 21-safe reader must match the independent JDK class-file oracle for " + OWNER);
        //?}
        String owner = model.internalName();
        assertEquals(OWNER, owner, "class-file owner must retain the stable JVM name");
        List<AbiEntry> entries = new ArrayList<>();
        entries.add(new AbiEntry(
                "CLASS", owner, "-", access(model.accessFlags()), "-", model.signature().orElse("-")));

        model.fields().stream()
                .filter(MemberInfo::isPublic)
                .map(field -> new AbiEntry(
                        "FIELD",
                        owner,
                        field.name(),
                        access(field.accessFlags()),
                        field.descriptor(),
                        field.signature().orElse("-")))
                .forEach(entries::add);

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
        return entries;
    }

    private static Path compiledClassRoot() {
        String configured = System.getProperty(MAIN_CLASSES_DIR_PROPERTY);
        assertNotNull(configured, "Gradle must inject the active node's compiled main class directory");
        return Path.of(configured);
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
                "the SPEC descriptor is part of the compatibility surface");

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

    private record AbiEntry(
            String kind,
            String owner,
            String name,
            String access,
            String descriptor,
            String signature) {
    }
}
