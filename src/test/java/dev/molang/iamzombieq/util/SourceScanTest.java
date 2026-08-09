package dev.molang.iamzombieq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SourceScanTest {

    private static final String MOD_SOURCE = "dev/molang/iamzombieq/IAmZombieMod.java";
    private static final String MIXINS_RESOURCE = "iamzombieq.mixins.json";

    private static final String SAMPLE = String.join("\n",
            "class Example {",
            "    private static void alpha(int x) {",
            "        if (x > 0) {",
            "            doThing(x);",
            "        }",
            "        marker(\"alpha-marker\");",
            "    }",
            "",
            "    @SubscribeEvent",
            "    public static void beta(Event event) {",
            "        marker(\"beta-marker\");",
            "    }",
            "}");

    @Test
    void mainJavaReadsKnownProductionSource() throws IOException {
        String source = SourceScan.mainJava(MOD_SOURCE);
        assertTrue(source.contains("public final class IAmZombieMod"));
    }

    @Test
    void resourceReadsKnownMainResource() throws IOException {
        String source = SourceScan.resource(MIXINS_RESOURCE);
        assertTrue(source.contains("\"mixins\""));
    }

    @Test
    void successfulReadsAreCachedByResolvedPath() throws IOException {
        String mainSource = SourceScan.mainJava(MOD_SOURCE);
        assertSame(mainSource, SourceScan.mainJava(MOD_SOURCE),
                "repeated main-source reads should return the cached value");

        String resource = SourceScan.resource(MIXINS_RESOURCE);
        assertSame(resource, SourceScan.resource(MIXINS_RESOURCE),
                "repeated resource reads should return the cached value");
    }

    @Test
    void missingFilePreservesIOExceptionWithLocatablePath() {
        String missing = "dev/molang/iamzombieq/DefinitelyMissingSourceScanFixture.java";
        IOException ex = assertThrows(IOException.class, () -> SourceScan.mainJava(missing));
        assertTrue(ex.getMessage().contains("DefinitelyMissingSourceScanFixture.java"),
                "the error should identify the missing relative path");
        assertTrue(ex.getMessage().contains(
                        Path.of("src", "main", "java").toString()),
                "the error should identify the resolved source root");
    }

    @Test
    void absolutePathsAreRejectedForBothRoots() {
        String absoluteMain = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieMod.java")
                .toAbsolutePath().toString();
        IllegalArgumentException mainEx = assertThrows(IllegalArgumentException.class,
                () -> SourceScan.mainJava(absoluteMain));
        assertTrue(mainEx.getMessage().contains(absoluteMain));

        String absoluteResource = Path.of("src/main/resources/iamzombieq.mixins.json")
                .toAbsolutePath().toString();
        IllegalArgumentException resourceEx = assertThrows(IllegalArgumentException.class,
                () -> SourceScan.resource(absoluteResource));
        assertTrue(resourceEx.getMessage().contains(absoluteResource));
    }

    @Test
    void pathsEscapingTheirRootAreRejected() {
        String escape = "../../../build.gradle.kts";
        IllegalArgumentException mainEx = assertThrows(IllegalArgumentException.class,
                () -> SourceScan.mainJava(escape));
        assertTrue(mainEx.getMessage().contains(escape));

        IllegalArgumentException resourceEx = assertThrows(IllegalArgumentException.class,
                () -> SourceScan.resource(escape));
        assertTrue(resourceEx.getMessage().contains(escape));
    }

    @Test
    void extractsTheMethodBodyBoundedByItsOwnBraces() {
        String body = SourceScan.methodBody(SAMPLE, "private static void alpha");
        assertTrue(body.startsWith("private static void alpha(int x) {"),
                "the slice should start at the given signature");
        assertTrue(body.contains("alpha-marker"), "the slice should contain the method's own marker");
        assertTrue(body.stripTrailing().endsWith("}"), "the slice should end at the method's closing brace");
        // The slice is bounded to the method: it must NOT leak into the sibling method that follows it.
        assertFalse(body.contains("beta-marker"),
                "the slice must stop at the method's own closing brace, not bleed into the next method");
    }

    @Test
    void secondMethodIsIsolatedFromTheFirst() {
        String body = SourceScan.methodBody(SAMPLE, "public static void beta");
        assertTrue(body.contains("beta-marker"), "the second method should be extractable independently");
        assertFalse(body.contains("alpha-marker"), "the second method's slice must not include the first method");
    }

    @Test
    void signatureCanBeAPartialUniqueSubstring() {
        // Callers pass e.g. "void handleGiantTick" rather than the whole declaration.
        String body = SourceScan.methodBody(SAMPLE, "void alpha");
        assertTrue(body.contains("alpha-marker"));
        assertFalse(body.contains("beta-marker"));
    }

    @Test
    void bracesInsideStringLiteralsDoNotConfuseTheScanner() {
        String src = String.join("\n",
                "class C {",
                "    void m() {",
                "        String s = \"a { b } c\";",
                "        char open = '{';",
                "        marker();",
                "    }",
                "    void other() { leak(); }",
                "}");
        String body = SourceScan.methodBody(src, "void m");
        assertTrue(body.contains("marker();"), "the real body should be captured");
        assertFalse(body.contains("leak();"),
                "braces inside string/char literals must not end the method early or run it long");
    }

    @Test
    void bracesInsideCommentsDoNotConfuseTheScanner() {
        String src = String.join("\n",
                "class C {",
                "    void m() {",
                "        // a stray } brace in a line comment",
                "        /* and a { block comment } too */",
                "        marker();",
                "    }",
                "    void other() { leak(); }",
                "}");
        String body = SourceScan.methodBody(src, "void m");
        assertTrue(body.contains("marker();"), "the real body should be captured");
        assertFalse(body.contains("leak();"), "braces inside comments must not terminate the scan early");
    }

    @Test
    void handlesDeeplyNestedBraces() {
        String src = String.join("\n",
                "class C {",
                "    void m() {",
                "        for (;;) { while (true) { if (x) { deep(); } } }",
                "        marker();",
                "    }",
                "    void other() { leak(); }",
                "}");
        String body = SourceScan.methodBody(src, "void m");
        assertTrue(body.contains("deep();") && body.contains("marker();"),
                "nested braces should be balanced back to the method's own closing brace");
        assertFalse(body.contains("leak();"), "the scan must not run into the following method");
    }

    @Test
    void missingSignatureThrowsInsteadOfSilentlySlicing() {
        // The core guard: a missing anchor must NOT degrade into a -1 substring (garbage/empty) — it must be loud.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SourceScan.methodBody(SAMPLE, "void doesNotExist"));
        assertTrue(ex.getMessage().contains("doesNotExist"), "the error should name the missing signature");
    }

    @Test
    void unbalancedBodyThrowsInsteadOfWalkingPastEndOfFile() {
        String truncated = "class C {\n    void m() {\n        marker();\n"; // never closes m()
        assertThrows(IllegalArgumentException.class, () -> SourceScan.methodBody(truncated, "void m"),
                "an unterminated method should throw rather than silently return to end-of-file");
    }

    @Test
    void nullOrEmptyArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SourceScan.methodBody(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> SourceScan.methodBody(SAMPLE, null));
        assertThrows(IllegalArgumentException.class, () -> SourceScan.methodBody(SAMPLE, ""));
    }

    // ---------- blockBody (unique nested code blocks) ----------

    @Test
    void extractsAUniqueNestedBlockWithoutStoppingAtItsFirstInnerBrace() {
        String method = SourceScan.methodBody(SAMPLE, "private static void alpha");
        String block = SourceScan.blockBody(method, "if (x > 0)");

        assertTrue(block.startsWith("if (x > 0) {"));
        assertTrue(block.contains("doThing(x);"));
        assertTrue(block.stripTrailing().endsWith("}"));
        assertFalse(block.contains("alpha-marker"),
                "the nested slice must stop at the if block rather than leaking into its parent method");
    }

    @Test
    void nestedBlockIgnoresBracesInStringsCharsAndCommentsBeforeAndInsideTheBlock() {
        String src = String.join("\n",
                "class C {",
                "    void m() {",
                "        if (ready) /* fake { */ {",
                "            String braces = \"{ not structural }\";",
                "            char close = '}';",
                "            // } neither is this",
                "            if (nested) { work(); }",
                "            marker();",
                "        }",
                "        sibling();",
                "    }",
                "}");

        String block = SourceScan.blockBody(src, "if (ready)");
        assertTrue(block.contains("if (nested) { work(); }"));
        assertTrue(block.contains("marker();"));
        assertFalse(block.contains("sibling();"));
    }

    @Test
    void nestedBlockRequiresExactlyOneAnchor() {
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> SourceScan.blockBody(SAMPLE, "if (missing)"));
        assertTrue(missing.getMessage().contains("if (missing)"));
        assertTrue(missing.getMessage().contains("not found"));

        String duplicate = "if (same) { first(); }\nif (same) { second(); }";
        IllegalArgumentException repeated = assertThrows(
                IllegalArgumentException.class,
                () -> SourceScan.blockBody(duplicate, "if (same)"));
        assertTrue(repeated.getMessage().contains("if (same)"));
        assertTrue(repeated.getMessage().contains("not unique"));
    }

    @Test
    void nestedBlockRejectsMissingOrUnbalancedBraces() {
        IllegalArgumentException noOpening = assertThrows(
                IllegalArgumentException.class,
                () -> SourceScan.blockBody(
                        "if (ready) return;\nif (later) { wrong(); }",
                        "if (ready)"));
        assertTrue(noOpening.getMessage().contains("opening brace"));

        IllegalArgumentException unbalanced = assertThrows(
                IllegalArgumentException.class,
                () -> SourceScan.blockBody("if (ready) { if (nested) { work(); }", "if (ready)"));
        assertTrue(unbalanced.getMessage().contains("unbalanced"));
        assertTrue(unbalanced.getMessage().contains("if (ready)"));
    }

    @Test
    void nestedBlockRejectsNullOrEmptyArguments() {
        assertThrows(IllegalArgumentException.class, () -> SourceScan.blockBody(null, "if (x)"));
        assertThrows(IllegalArgumentException.class, () -> SourceScan.blockBody(SAMPLE, null));
        assertThrows(IllegalArgumentException.class, () -> SourceScan.blockBody(SAMPLE, ""));
    }

    // ---------- containsInOrder (fail closed on missing anchors) ----------

    @Test
    void containsInOrderRequiresEveryAnchorInSequence() {
        String source = "alpha(); beta(); gamma();";
        assertTrue(SourceScan.containsInOrder(source, "alpha();", "beta();", "gamma();"));
        assertFalse(SourceScan.containsInOrder(source, "missing();", "beta();", "gamma();"));
        assertFalse(SourceScan.containsInOrder(source, "alpha();", "missing();", "gamma();"));
        assertFalse(SourceScan.containsInOrder(source, "alpha();", "beta();", "missing();"));
        assertFalse(SourceScan.containsInOrder(source, "gamma();", "beta();"),
                "present anchors in the wrong order must fail");
    }

    // ---------- countOccurrences (consolidated from ZombiePlayerEventsSourceTest) ----------

    @Test
    void countOccurrencesCountsNonOverlappingMatches() {
        assertEquals(0, SourceScan.countOccurrences("abcabc", "z"), "a missing needle counts zero");
        assertEquals(2, SourceScan.countOccurrences("abcabc", "abc"), "two disjoint matches count two");
        assertEquals(3, SourceScan.countOccurrences("aaa", "a"), "single-char matches count each occurrence");
    }

    @Test
    void countOccurrencesAdvancesByNeedleLengthSoOverlapsAreNotDoubleCounted() {
        // "aa" in "aaaa" is non-overlapping: positions 0 and 2, not 0/1/2 — matches the original private helper.
        assertEquals(2, SourceScan.countOccurrences("aaaa", "aa"));
    }

    // ---------- stripComments (consolidated from ZombieMountRulesTest) ----------

    @Test
    void stripCommentsRemovesLineAndBlockComments() {
        String code = "int x = 1; // trailing\n/* block */ int y = 2;";
        String stripped = SourceScan.stripComments(code);
        assertFalse(stripped.contains("trailing"), "line comments must be removed");
        assertFalse(stripped.contains("block"), "block comments must be removed");
        assertTrue(stripped.contains("int x = 1;"), "live code before a comment must remain");
        assertTrue(stripped.contains("int y = 2;"), "live code after a comment must remain");
    }

    @Test
    void stripCommentsPreservesTheKnownStringLiteralCorrosionLimitation() {
        // DELIBERATELY pinned: the helper is a plain regex strip, so a // sequence inside a string literal (a URL,
        // say) is ALSO removed. The existing source scans pass under this behavior; it must stay unchanged.
        String withUrl = "String s = \"http://example.com\";";
        assertFalse(SourceScan.stripComments(withUrl).contains("example.com"),
                "the known string/URL // corrosion is preserved (not 'improved') so scan results stay identical");
    }

    // ---------- compact (consolidated from CoffinRecipeDataTest / CoffinLootTableDataTest) ----------

    @Test
    void compactCollapsesAllWhitespaceRuns() {
        String json = "{\n  \"a\" : 1,\n  \"b\" : 2\n}";
        assertEquals("{\"a\":1,\"b\":2}", SourceScan.compact(json),
                "spaces, tabs and newlines should all be squeezed out");
    }
}
