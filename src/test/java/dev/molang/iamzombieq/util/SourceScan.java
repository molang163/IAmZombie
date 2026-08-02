package dev.molang.iamzombieq.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Test-only source reading and slicing helpers for the {@code *SourceTest} guards.
 *
 * <p>Those tests read a production {@code .java} file as a string and assert that a particular method still
 * contains (or no longer contains) a load-bearing marker. Historically they carved the method out with
 * {@code source.substring(source.indexOf("methodA"), source.indexOf("methodB"))}: a hand-picked A..B window that
 * (a) silently returns garbage when either anchor is missing ({@code indexOf} returns {@code -1}, which
 * {@code substring} then treats as an out-of-range index or an empty/oversized slice), and (b) breaks the moment a
 * sibling method is reordered or renamed. This helper replaces that with a single brace-balanced scan from the
 * method signature to its own closing brace, so the window is defined by the method itself rather than by whatever
 * happens to follow it.</p>
 *
 * <p>No Minecraft runtime — directly JUnit-testable (see {@code SourceScanTest}).</p>
 */
public final class SourceScan {
    private static final Path MAIN_JAVA_ROOT = Path.of("src/main/java").toAbsolutePath().normalize();
    private static final Path RESOURCE_ROOT = Path.of("src/main/resources").toAbsolutePath().normalize();
    private static final Map<Path, String> CACHE = new HashMap<>();

    private SourceScan() {
    }

    /**
     * Reads a production Java source relative to {@code src/main/java}.
     */
    public static String mainJava(String relativePath) throws IOException {
        return read(MAIN_JAVA_ROOT, relativePath);
    }

    /**
     * Reads a production resource relative to {@code src/main/resources}.
     */
    public static String resource(String relativePath) throws IOException {
        return read(RESOURCE_ROOT, relativePath);
    }

    private static String read(Path root, String relativePath) throws IOException {
        Path resolved = resolve(root, relativePath);
        synchronized (CACHE) {
            String cached = CACHE.get(resolved);
            if (cached != null) {
                return cached;
            }
            String source = Files.readString(resolved);
            CACHE.put(resolved, source);
            return source;
        }
    }

    private static Path resolve(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path must be a non-empty relative path under " + root);
        }
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("absolute path is not allowed: " + relativePath + " (root: " + root + ")");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(
                    "path escapes source root: " + relativePath + " (resolved: " + resolved + ", root: " + root + ")");
        }
        return resolved;
    }

    /**
     * Returns the body of the method whose signature substring is {@code methodSignature}, spanning from the start of
     * that signature through the matching closing brace of the method body (inclusive). The signature does not need to
     * be the whole declaration; any unique substring that ends at or before the opening {@code {} works (e.g.
     * {@code "public static void onGiantSwing"} or {@code "void handleGiantTick"}).
     *
     * <p>The scan finds the first {@code &#123;} at or after the signature and then walks forward counting braces until
     * the depth returns to zero, which is the method's closing {@code &#125;}. String literals, char literals and
     * both comment styles are skipped so a brace inside {@code "}"} or {@code // }} does not throw the count off.</p>
     *
     * @throws IllegalArgumentException if {@code methodSignature} is not found (anchor missing — never a silent
     *                                  {@code -1} slice), or if the method has no opening brace, or if its braces never
     *                                  balance before end-of-file (in which case the scan does not silently walk past
     *                                  the end — it reports the unbalanced method).
     */
    public static String methodBody(String source, String methodSignature) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (methodSignature == null || methodSignature.isEmpty()) {
            throw new IllegalArgumentException("methodSignature must not be null or empty");
        }
        int start = source.indexOf(methodSignature);
        if (start < 0) {
            throw new IllegalArgumentException("method signature not found in source: " + methodSignature);
        }
        return balancedBody(source, start, methodSignature, "method signature", false);
    }

    /**
     * Returns the uniquely anchored nested code block, spanning from {@code blockAnchor} through that block's matching
     * closing brace (inclusive).
     *
     * <p>Unlike {@link #methodBody(String, String)}, this helper deliberately requires the anchor to occur exactly
     * once in the supplied source slice. Callers should first narrow to a method and then select an {@code if},
     * {@code for}, {@code switch}, or other nested block. Missing or repeated anchors therefore fail closed instead of
     * silently selecting a sibling branch. Ignoring whitespace and comments, the first structural token after the
     * anchor must be its opening brace, so a brace from a later sibling cannot be claimed accidentally. Braces in
     * strings, chars, and comments are ignored while balancing the body.</p>
     *
     * @throws IllegalArgumentException if the source/anchor is invalid, the anchor is missing or repeated, no
     *                                  structural opening brace follows it, or the block is unbalanced
     */
    public static String blockBody(String source, String blockAnchor) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (blockAnchor == null || blockAnchor.isEmpty()) {
            throw new IllegalArgumentException("blockAnchor must not be null or empty");
        }
        int start = source.indexOf(blockAnchor);
        if (start < 0) {
            throw new IllegalArgumentException("block anchor not found in source: " + blockAnchor);
        }
        int duplicate = source.indexOf(blockAnchor, start + 1);
        if (duplicate >= 0) {
            throw new IllegalArgumentException(
                    "block anchor is not unique: " + blockAnchor
                            + " (first at " + start + ", repeated at " + duplicate + ")");
        }
        return balancedBody(source, start, blockAnchor, "block anchor", true);
    }

    private static String balancedBody(
            String source,
            int start,
            String anchor,
            String anchorKind,
            boolean openingMustImmediatelyFollowAnchor) {
        int scanStart = Math.max(start, start + anchor.length() - 1);
        int open = openingMustImmediatelyFollowAnchor
                ? immediateStructuralOpeningBrace(
                        source,
                        anchor.endsWith("{") ? scanStart : start + anchor.length())
                : structuralOpeningBrace(source, scanStart);
        if (open < 0) {
            throw new IllegalArgumentException("no opening brace found after " + anchorKind + ": " + anchor);
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException(
                "unbalanced braces: body never closed for " + anchorKind + ": " + anchor);
    }

    private static int immediateStructuralOpeningBrace(String source, int start) {
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '/' && next == '/') {
                int newline = source.indexOf('\n', i + 2);
                if (newline < 0) {
                    return -1;
                }
                i = newline;
                continue;
            }
            if (c == '/' && next == '*') {
                int close = source.indexOf("*/", i + 2);
                if (close < 0) {
                    return -1;
                }
                i = close + 1;
                continue;
            }
            return c == '{' ? i : -1;
        }
        return -1;
    }

    private static int structuralOpeningBrace(String source, int start) {
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether every non-empty marker occurs in the supplied order. Missing markers fail closed instead of allowing
     * raw {@code indexOf} values such as {@code -1 < 42} to masquerade as a valid order.
     */
    public static boolean containsInOrder(String source, String... markers) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (markers == null) {
            throw new IllegalArgumentException("markers must not be null");
        }
        int from = 0;
        for (String marker : markers) {
            if (marker == null || marker.isEmpty()) {
                throw new IllegalArgumentException("markers must not contain null or empty values");
            }
            int index = source.indexOf(marker, from);
            if (index < 0) {
                return false;
            }
            from = index + marker.length();
        }
        return true;
    }

    /**
     * Counts the number of non-overlapping occurrences of {@code needle} in {@code haystack}. Consolidated (byte-for-byte)
     * from the private helper that {@code ZombiePlayerEventsSourceTest} used to count how many times a marker
     * (e.g. {@code "Attributes.MOVEMENT_SPEED"}) appears inside a scanned method body.
     */
    public static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /**
     * Strips {@code /* *} block comments and {@code //} line comments from a chunk of source, so a marker inside a
     * comment is not mistaken for live code. Consolidated (byte-for-byte) from the private helper in
     * {@code ZombieMountRulesTest}; the known limitation that a {@code /}{@code /} or {@code /*} sequence inside a
     * string/URL literal is also removed is PRESERVED unchanged (the existing scans pass under this behavior).
     */
    public static String stripComments(String code) {
        return code
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    /**
     * Collapses all runs of whitespace out of a JSON string so a data-scan can assert on compact fragments regardless
     * of the pretty-printing. Consolidated (byte-for-byte) from the duplicated private helper in
     * {@code CoffinRecipeDataTest} and {@code CoffinLootTableDataTest}.
     */
    public static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }
}
