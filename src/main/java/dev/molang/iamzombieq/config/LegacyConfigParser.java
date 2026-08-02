package dev.molang.iamzombieq.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LegacyConfigParser {
    private LegacyConfigParser() {
    }

    static Parsed parse(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            rejectDuplicateAssignments(text);

            CommentedConfig config =
                    new TomlParser().parse(new StringReader(text));
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            flatten(config, "", values);
            LinkedHashMap<String, Object> comments = sourceComments(text);
            LinkedHashMap<String, Object> specComments =
                    new LinkedHashMap<>();
            flattenSpecComments(config, "", specComments);
            return new Parsed(values, comments, specComments);
        } catch (CharacterCodingException | RuntimeException exception) {
            throw new IllegalArgumentException("invalid legacy TOML", exception);
        }
    }

    private static void rejectDuplicateAssignments(String text) {
        Set<String> assigned = new HashSet<>();
        String section = "";
        for (String rawLine : text.split("\\R", -1)) {
            String line = stripInlineComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = normalizeKey(line.substring(1, line.length() - 1));
                continue;
            }
            int equals = unquotedEquals(line);
            if (equals < 1) {
                continue;
            }
            String key = qualify(section, normalizeKey(line.substring(0, equals)));
            if (!assigned.add(key)) {
                throw new IllegalArgumentException(
                        join("duplicate legacy TOML key ", key));
            }
        }
    }

    private static LinkedHashMap<String, Object> sourceComments(String text) {
        LinkedHashMap<String, Object> comments = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>();
        String section = "";
        for (String rawLine : text.split("\\R", -1)) {
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("#")) {
                pending.add(trimmed.substring(1).trim());
                continue;
            }
            String line = stripInlineComment(rawLine).trim();
            if (line.isEmpty()) {
                if (trimmed.isEmpty()) {
                    pending.clear();
                }
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = normalizeKey(line.substring(1, line.length() - 1));
                pending.clear();
                continue;
            }
            int equals = unquotedEquals(line);
            if (equals > 0) {
                String key =
                        qualify(section, normalizeKey(line.substring(0, equals)));
                if (!pending.isEmpty()) {
                    comments.put(key, String.join("\n", pending));
                }
            }
            pending.clear();
        }
        return comments;
    }

    private static void flatten(
            CommentedConfig config,
            String prefix,
            Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String key = join(prefix, entry.getKey());
            Object value = entry.getValue();
            if (value instanceof CommentedConfig child) {
                flatten(child, join(key, "."), values);
            } else {
                values.put(key, normalizeValue(value));
            }
        }
    }

    private static void flattenSpecComments(
            CommentedConfig config,
            String prefix,
            Map<String, Object> comments) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String key = join(prefix, entry.getKey());
            Object value = entry.getValue();
            if (value instanceof CommentedConfig child) {
                flattenSpecComments(child, join(key, "."), comments);
            } else {
                String comment = config.getComment(entry.getKey());
                if (comment != null) {
                    comments.put(key, comment);
                }
            }
        }
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return List.copyOf(normalized);
        }
        return value;
    }

    private static int unquotedEquals(String line) {
        boolean quoted = false;
        char quote = '\0';
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (quote == '"' && escaped) {
                    escaped = false;
                } else if (quote == '"' && current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quoted = false;
                }
            } else if (current == '"' || current == '\'') {
                quoted = true;
                quote = current;
            } else if (current == '=') {
                return index;
            }
        }
        return -1;
    }

    private static String stripInlineComment(String line) {
        boolean quoted = false;
        char quote = '\0';
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (quote == '"' && escaped) {
                    escaped = false;
                } else if (quote == '"' && current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quoted = false;
                }
            } else if (current == '"' || current == '\'') {
                quoted = true;
                quote = current;
            } else if (current == '#') {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static String normalizeKey(String key) {
        StringBuilder normalized = new StringBuilder();
        for (String part : key.trim().split("\\s*\\.\\s*")) {
            if (!normalized.isEmpty()) {
                normalized.append('.');
            }
            normalized.append(unquote(part.trim()));
        }
        return normalized.toString();
    }

    private static String unquote(String key) {
        if (key.length() >= 2
                && ((key.startsWith("\"") && key.endsWith("\""))
                || (key.startsWith("'") && key.endsWith("'")))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static String qualify(String section, String key) {
        return section.isEmpty() ? key : join(section, ".", key);
    }

    private static String join(Object... parts) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            result.append(part);
        }
        return result.toString();
    }

    record Parsed(
            Map<String, Object> rawValues,
            Map<String, Object> comments,
            Map<String, Object> specComments) {
        Parsed {
            rawValues = Collections.unmodifiableMap(
                    new LinkedHashMap<>(rawValues));
            comments = Collections.unmodifiableMap(
                    new LinkedHashMap<>(comments));
            specComments = Collections.unmodifiableMap(
                    new LinkedHashMap<>(specComments));
        }
    }
}
