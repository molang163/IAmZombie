package dev.molang.iamzombieq.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned authority wire identity derived from the checked-in authority
 * schema, never from the mod version.
 */
final class ConfigAuthorityProtocol {
    static final String PROTOCOL = "1";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String SCHEMA_FINGERPRINT = deriveSchemaFingerprint();
    private static final String NEGOTIATION_VERSION =
            "authority/" + PROTOCOL + "/" + SCHEMA_FINGERPRINT;

    private ConfigAuthorityProtocol() {
    }

    static String schemaFingerprint() {
        return SCHEMA_FINGERPRINT;
    }

    static String negotiationVersion() {
        return NEGOTIATION_VERSION;
    }

    static ConfigAuthoritySnapshot snapshot(
            long epoch, ConfigAuthorityRemoteValues values) {
        if (epoch <= 0L) {
            throw new IllegalArgumentException(
                    "Authority epoch must be positive");
        }
        Objects.requireNonNull(values, "values");
        return new ConfigAuthoritySnapshot(
                epoch,
                PROTOCOL,
                SCHEMA_FINGERPRINT,
                values.payloadSha256(),
                values);
    }

    static void validateSnapshot(ConfigAuthoritySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.epoch() <= 0L) {
            throw new ConfigAuthorityProtocolException(
                    "Authority snapshot epoch is not positive");
        }
        if (!PROTOCOL.equals(snapshot.protocol())) {
            throw new ConfigAuthorityProtocolException(
                    "Authority protocol mismatch");
        }
        if (!SCHEMA_FINGERPRINT.equals(snapshot.schemaFingerprint())) {
            throw new ConfigAuthorityProtocolException(
                    "Authority schema fingerprint mismatch");
        }
        if (!validSha256(snapshot.payloadSha256())
                || !snapshot.values().payloadSha256()
                        .equals(snapshot.payloadSha256())) {
            throw new ConfigAuthorityProtocolException(
                    "Authority payload hash mismatch");
        }
    }

    static void validateAck(
            ConfigAuthoritySnapshot expected, ConfigAuthorityAck ack) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(ack, "ack");
        if (ack.epoch() != expected.epoch()
                || !ack.protocol().equals(expected.protocol())
                || !ack.schemaFingerprint().equals(
                        expected.schemaFingerprint())
                || !ack.payloadSha256().equals(
                        expected.payloadSha256())) {
            throw new ConfigAuthorityProtocolException(
                    "Authority acknowledgement does not match the current epoch and payload");
        }
    }

    static String payloadSha256(ConfigAuthorityRemoteValues values) {
        Objects.requireNonNull(values, "values");
        MessageDigest digest = sha256();
        updateText(digest, "iamzombieq-remote19-v1");
        updateText(digest, "ZOMBIE_FOODS");
        updateInt(digest, values.zombieFoods().size());
        for (String food : values.zombieFoods()) {
            updateText(digest, food);
        }
        for (String field : ConfigAuthorityRemoteValues.integerFields()) {
            updateText(digest, field);
            updateInt(digest, values.integer(field));
        }
        updateText(digest, "SPIDER_MOUNT_SPEED");
        updateLong(
                digest,
                Double.doubleToLongBits(values.spiderMountSpeed()));
        return hex(digest.digest());
    }

    private static String deriveSchemaFingerprint() {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        MessageDigest digest = sha256();
        updateText(digest, "iamzombieq-authority-schema-v1");
        updateText(digest, schema.version());
        List<ConfigSchemaCatalog.Entry> entries = schema.entries();
        updateInt(digest, entries.size());
        for (ConfigSchemaCatalog.Entry entry : entries) {
            updateText(digest, entry.target().name());
            updateText(digest, entry.sourceKey());
            updateText(digest, entry.field());
            updateText(digest, entry.key());
            updateText(digest, entry.type().name());
            updateValue(digest, entry.defaultValue());
            updateNumber(digest, entry.minimum());
            updateNumber(digest, entry.maximum());
            updateText(digest, entry.comment());
        }
        return hex(digest.digest());
    }

    private static void updateValue(MessageDigest digest, Object value) {
        if (value instanceof List<?> list) {
            updateText(digest, "LIST");
            updateInt(digest, list.size());
            for (Object element : list) {
                updateText(digest, String.valueOf(element));
            }
        } else if (value instanceof Boolean booleanValue) {
            updateText(digest, "BOOLEAN");
            digest.update((byte) (booleanValue ? 1 : 0));
        } else if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            updateText(digest, "INTEGER");
            updateLong(digest, ((Number) value).longValue());
        } else if (value instanceof Number number) {
            updateText(digest, "DOUBLE");
            updateLong(
                    digest,
                    Double.doubleToLongBits(number.doubleValue()));
        } else {
            updateText(digest, "STRING");
            updateText(digest, String.valueOf(value));
        }
    }

    private static void updateNumber(
            MessageDigest digest, Number number) {
        if (number == null) {
            updateText(digest, "NONE");
        } else {
            updateText(digest, "NUMBER");
            updateLong(
                    digest,
                    Double.doubleToLongBits(number.doubleValue()));
        }
    }

    private static void updateText(
            MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static boolean validSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "The JDK does not provide SHA-256", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
