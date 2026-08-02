package dev.molang.iamzombieq.config;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

record MigrationMarker(MigrationEvidence evidence) {
    private static final String MAGIC = "IAMZOMBIEQ-MIGRATION-MARKER";
    private static final String VERSION = "1";
    private static final Set<String> FIELDS = Set.of("evidence");

    MigrationMarker {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.phase().equals(MigrationTargetState.Phase.COMPLETE.name())) {
            throw new IllegalArgumentException(
                    "Marker evidence must be COMPLETE");
        }
    }

    MigrationTargetState.Phase phase() {
        return MigrationTargetState.Phase.COMPLETE;
    }

    byte[] encode() {
        byte[] evidenceBytes = new MigrationEvidenceCodec().encode(evidence);
        String encoded = MAGIC
                + "\nversion="
                + VERSION
                + "\nevidence="
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(evidenceBytes)
                + "\n";
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    static MigrationMarker decode(byte[] bytes) {
        String encoded = strictUtf8(bytes);
        if (encoded.indexOf('\r') >= 0 || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException(
                    "Marker must use canonical LF with terminal newline");
        }
        String[] lines = encoded.split("\n", -1);
        if (lines.length < 4
                || !lines[0].equals(MAGIC)
                || !lines[1].equals("version=" + VERSION)
                || !lines[lines.length - 1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid migration marker header or version");
        }
        Map<String, String> fields = fields(lines);
        try {
            byte[] evidenceBytes = Base64.getUrlDecoder()
                    .decode(fields.get("evidence"));
            return new MigrationMarker(
                    new MigrationEvidenceCodec().decode(evidenceBytes));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Invalid migration marker value", failure);
        }
    }

    private static Map<String, String> fields(String[] lines) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 2; index < lines.length - 1; index++) {
            String line = lines[index];
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "Malformed marker line");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!FIELDS.contains(key)) {
                throw new IllegalArgumentException(
                        "Unknown marker field: " + key);
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate marker field: " + key);
            }
        }
        if (!values.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException(
                    "Migration marker fields are incomplete");
        }
        return values;
    }

    private static String strictUtf8(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException(
                    "Migration marker is not strict UTF-8", failure);
        }
    }
}
