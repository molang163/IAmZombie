package dev.molang.iamzombieq.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TargetConfigValidator {
    private static final String DOCUMENT = "<document>";

    private final ConfigSchemaCatalog schema;

    TargetConfigValidator(ConfigSchemaCatalog schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    Result validate(MigrationTarget target, Map<String, Object> values) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(values, "values");
        List<Issue> issues = new ArrayList<>();

        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            if (!values.containsKey(entry.key())) {
                issues.add(new Issue(Kind.MISSING, entry.key()));
                continue;
            }
            Object value = values.get(entry.key());
            if (!entry.accepts(value)) {
                issues.add(new Issue(Kind.TYPE, entry.key()));
            } else if (!entry.inRange(value)) {
                issues.add(new Issue(Kind.RANGE, entry.key()));
            }
        }

        for (String key : values.keySet()) {
            try {
                schema.require(target, key);
            } catch (IllegalArgumentException ignored) {
                issues.add(new Issue(Kind.UNKNOWN, key));
            }
        }
        return new Result(issues);
    }

    Result validateEncoded(MigrationTarget target, String encoded) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(encoded, "encoded");

        LegacyConfigParser.Parsed parsed;
        try {
            parsed = LegacyConfigParser.parse(
                    encoded.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return new Result(List.of(new Issue(Kind.MALFORMED, DOCUMENT)));
        }

        List<Issue> issues =
                new ArrayList<>(validate(target, parsed.rawValues()).issues());
        if (issues.isEmpty()) {
            Map<String, Object> corrected =
                    correct(target, parsed.rawValues());
            for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
                if (!Objects.equals(
                        parsed.rawValues().get(entry.key()),
                        corrected.get(entry.key()))) {
                    issues.add(new Issue(Kind.CORRECTION, entry.key()));
                }
            }
            if (!corrected.equals(correct(target, corrected))) {
                issues.add(new Issue(Kind.CORRECTION, DOCUMENT));
            }
        }
        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            Object actual = parsed.specComments().get(entry.key());
            if (!(actual instanceof String comment)
                    || !normalizeComment(comment)
                            .equals(normalizeComment(entry.comment()))) {
                issues.add(new Issue(Kind.COMMENT, entry.key()));
            }
        }
        return new Result(issues);
    }

    Map<String, Object> correct(
            MigrationTarget target, Map<String, Object> values) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(values, "values");

        LinkedHashMap<String, Object> corrected = new LinkedHashMap<>();
        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            Object value = values.get(entry.key());
            if (values.containsKey(entry.key())
                    && entry.accepts(value)
                    && entry.inRange(value)) {
                corrected.put(
                        entry.key(),
                        immutableValue(entry.canonicalValue(value)));
            } else {
                corrected.put(
                        entry.key(), immutableValue(entry.defaultValue()));
            }
        }
        return Collections.unmodifiableMap(corrected);
    }

    private static String normalizeComment(String comment) {
        return comment.replace("\r\n", "\n");
    }

    private static Object immutableValue(Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : value;
    }

    enum Kind {
        MISSING,
        UNKNOWN,
        TYPE,
        RANGE,
        COMMENT,
        CORRECTION,
        MALFORMED
    }

    record Issue(Kind kind, String key) {
        Issue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(key, "key");
        }
    }

    record Result(List<Issue> issues) {
        Result {
            issues = List.copyOf(issues);
        }

        boolean valid() {
            return issues.isEmpty();
        }
    }
}
