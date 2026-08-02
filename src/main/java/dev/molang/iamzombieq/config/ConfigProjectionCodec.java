package dev.molang.iamzombieq.config;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ConfigProjectionCodec {
    private static final String MAGIC = "IAMZOMBIEQ_CONFIG_PROJECTION";
    private static final int CODEC_VERSION = 1;

    private ConfigProjectionCodec() {
    }

    static String encode(
            MigrationTarget target,
            Map<String, Object> values,
            ConfigSchemaCatalog schema) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(schema, "schema");

        StringBuilder output = new StringBuilder();
        for (ConfigSchemaCatalog.Entry entry : schema.entries(target)) {
            if (!values.containsKey(entry.key())) {
                throw new IllegalArgumentException(
                        join("missing canonical value ", target, ":", entry.key()));
            }
            Object value = canonicalValue(entry, values.get(entry.key()));
            appendComment(output, entry.comment());
            output.append(entry.key())
                    .append(" = ")
                    .append(render(value))
                    .append('\n');
        }
        return output.toString();
    }

    static String typedSha256(
            MigrationTarget target,
            Map<String, Object> values,
            ConfigSchemaCatalog schema) {
        byte[] projection = typedBytes(target, values, schema);
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(projection));
            return join(
                    schema.version(),
                    ":",
                    target.name().toLowerCase(Locale.ROOT),
                    ":",
                    digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] typedBytes(
            MigrationTarget target,
            Map<String, Object> values,
            ConfigSchemaCatalog schema) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(schema, "schema");

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeText(output, MAGIC);
                output.writeInt(CODEC_VERSION);
                writeText(output, target.name().toLowerCase(Locale.ROOT));
                writeText(output, schema.version());
                List<ConfigSchemaCatalog.Entry> entries =
                        schema.entries(target);
                output.writeInt(entries.size());
                for (ConfigSchemaCatalog.Entry entry : entries) {
                    writeText(output, entry.sourceKey());
                    writeText(output, entry.key());
                    output.writeByte(entry.type().ordinal());
                    boolean present = values.containsKey(entry.key());
                    output.writeBoolean(present);
                    if (present) {
                        Object value =
                                canonicalValue(entry, values.get(entry.key()));
                        writeValue(output, entry.type(), value);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "cannot encode in-memory projection", exception);
        }
    }

    private static void writeValue(
            DataOutputStream output,
            ConfigSchemaCatalog.ValueType type,
            Object value) throws IOException {
        switch (type) {
            case BOOLEAN -> output.writeBoolean((Boolean) value);
            case INTEGER -> output.writeLong(((Number) value).longValue());
            case DOUBLE -> output.writeLong(
                    Double.doubleToLongBits(((Number) value).doubleValue()));
            case STRING -> writeText(output, (String) value);
            case LIST -> {
                List<?> list = (List<?>) value;
                output.writeInt(list.size());
                for (Object element : list) {
                    writeText(output, (String) element);
                }
            }
        }
    }

    private static void writeText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static Object canonicalValue(
            ConfigSchemaCatalog.Entry entry, Object value) {
        if (!entry.accepts(value)) {
            throw new IllegalArgumentException(
                    join(
                            "wrong value type for ",
                            entry.target(),
                            ":",
                            entry.key()));
        }
        if (!entry.inRange(value)) {
            throw new IllegalArgumentException(
                    join(
                            "value outside range for ",
                            entry.target(),
                            ":",
                            entry.key()));
        }
        return entry.canonicalValue(value);
    }

    private static void appendComment(StringBuilder output, String comment) {
        for (String line : comment.split("\n", -1)) {
            output.append('#').append(line).append('\n');
        }
    }

    private static String render(Object value) {
        if (value instanceof String text) {
            return join("\"", escape(text), "\"");
        }
        if (value instanceof List<?> list) {
            StringBuilder rendered = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    rendered.append(", ");
                }
                rendered.append(render(list.get(index)));
            }
            return rendered.append(']').toString();
        }
        if (value instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return Long.toString(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            return Double.toString(number.doubleValue());
        }
        throw new IllegalArgumentException(
                join("unsupported canonical value ", value));
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\f", "\\f")
                .replace("\r", "\\r");
    }

    private static String join(Object... parts) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            result.append(part);
        }
        return result.toString();
    }
}
