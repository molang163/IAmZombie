package dev.molang.iamzombieq.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ConfigProjection {
    private static final String JOLT_FIELD = "HEROBRINE_JOLT_ENABLED";
    private static final String JOLT_SOURCE = "herobrineJoltEnabled";

    private ConfigProjection() {
    }

    static Map<String, Object> project(
            MigrationTarget target,
            LegacyConfigParser.Parsed legacy,
            ConfigSchemaCatalog schema) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(schema, "schema");

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            if (!legacy.rawValues().containsKey(entry.sourceKey())) {
                throw invalid(entry, "missing applicable legacy key");
            }
            Object value = legacy.rawValues().get(entry.sourceKey());
            if (!entry.accepts(value)) {
                throw invalid(entry, "wrong legacy value type");
            }
            if (!entry.inRange(value)) {
                throw invalid(entry, "legacy value is outside the inclusive range");
            }
            result.put(
                    entry.key(),
                    immutableValue(entry.canonicalValue(value)));
        }
        return Collections.unmodifiableMap(result);
    }

    static Result projectCatalogDefaults(ConfigSchemaCatalog schema) {
        Objects.requireNonNull(schema, "schema");
        LinkedHashMap<String, Object> server =
                defaultValues(schema, MigrationTarget.SERVER);
        LinkedHashMap<String, Object> preferences =
                defaultValues(schema, MigrationTarget.PREFERENCES);
        LinkedHashMap<String, Object> inert = new LinkedHashMap<>();
        for (ConfigKeyCatalog.Entry entry : ConfigKeyCatalog.entries()) {
            if (entry.inert()) {
                inert.put(
                        entry.legacyTomlKey(),
                        schema.require(
                                        MigrationTarget.SERVER,
                                        entry.targets().getFirst().tomlKey())
                                .defaultValue());
            }
        }

        List<String> split = List.of(JOLT_FIELD);
        Map<String, List<TargetKey>> links = Map.of(
                JOLT_FIELD,
                List.of(
                        new TargetKey(
                                MigrationTarget.SERVER,
                                "herobrineJoltEnabled"),
                        new TargetKey(
                                MigrationTarget.PREFERENCES,
                                "herobrineJoltVignetteEnabled")));
        return new Result(
                server,
                preferences,
                inert,
                split,
                links,
                new Report(55, 47, 3, 1, 4));
    }

    private static LinkedHashMap<String, Object> defaultValues(
            ConfigSchemaCatalog schema, MigrationTarget target) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            values.put(entry.key(), immutableValue(entry.defaultValue()));
        }
        return values;
    }

    private static Object immutableValue(Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : value;
    }

    private static IllegalArgumentException invalid(
            ConfigSchemaCatalog.Entry entry, String reason) {
        return new IllegalArgumentException(
                join(
                        reason,
                        " ",
                        entry.sourceKey(),
                        " for ",
                        entry.target(),
                        ":",
                        entry.key()));
    }

    record TargetKey(MigrationTarget target, String key) {
        TargetKey {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(key, "key");
        }
    }

    record Report(int legacyKeys, int direct, int renamed, int split, int inert) {
    }

    record Result(
            Map<String, Object> serverValues,
            Map<String, Object> preferenceValues,
            Map<String, Object> inertLegacyValues,
            List<String> splitSources,
            Map<String, List<TargetKey>> links,
            Report report) {
        Result {
            serverValues = immutableMap(serverValues);
            preferenceValues = immutableMap(preferenceValues);
            inertLegacyValues = immutableMap(inertLegacyValues);
            splitSources = List.copyOf(splitSources);
            LinkedHashMap<String, List<TargetKey>> immutableLinks =
                    new LinkedHashMap<>();
            for (Map.Entry<String, List<TargetKey>> entry : links.entrySet()) {
                immutableLinks.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            links = Collections.unmodifiableMap(immutableLinks);
            Objects.requireNonNull(report, "report");
        }

        List<TargetKey> targetsForLegacy(String source) {
            if (JOLT_SOURCE.equals(source)) {
                return links.getOrDefault(JOLT_FIELD, List.of());
            }
            return links.getOrDefault(source, List.of());
        }

        Map<String, Object> values(MigrationTarget target) {
            return target == MigrationTarget.SERVER
                    ? serverValues
                    : preferenceValues;
        }

        Map<String, Object> appearanceValues() {
            return Map.of();
        }

        private static Map<String, Object> immutableMap(
                Map<String, Object> values) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                copy.put(entry.getKey(), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    private static String join(Object... parts) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            result.append(part);
        }
        return result.toString();
    }
}
