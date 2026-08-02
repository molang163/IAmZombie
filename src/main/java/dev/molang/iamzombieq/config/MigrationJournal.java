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

record MigrationJournal(int generation, MigrationEvidence evidence) {
    private static final String MAGIC = "IAMZOMBIEQ-MIGRATION-JOURNAL";
    private static final String VERSION = "1";
    private static final Set<String> FIELDS =
            Set.of("generation", "evidence");

    MigrationJournal {
        if (generation < 1) {
            throw new IllegalArgumentException(
                    "Journal generation must be positive");
        }
        Objects.requireNonNull(evidence, "evidence");
        phaseOf(evidence);
    }

    MigrationTargetState.Phase phase() {
        return phaseOf(evidence);
    }

    byte[] encode() {
        byte[] evidenceBytes = new MigrationEvidenceCodec().encode(evidence);
        String encoded = MAGIC
                + "\nversion="
                + VERSION
                + "\ngeneration="
                + generation
                + "\nevidence="
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(evidenceBytes)
                + "\n";
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    static MigrationJournal decode(byte[] bytes) {
        String encoded = strictUtf8(bytes);
        if (encoded.indexOf('\r') >= 0 || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException(
                    "Journal must use canonical LF with terminal newline");
        }
        String[] lines = encoded.split("\n", -1);
        if (lines.length < 5
                || !lines[0].equals(MAGIC)
                || !lines[1].equals("version=" + VERSION)
                || !lines[lines.length - 1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid migration journal header or version");
        }
        Map<String, String> fields = fields(lines, 2, "journal");
        try {
            int generation = Integer.parseInt(fields.get("generation"));
            byte[] evidenceBytes = Base64.getUrlDecoder()
                    .decode(fields.get("evidence"));
            MigrationEvidence evidence =
                    new MigrationEvidenceCodec().decode(evidenceBytes);
            return new MigrationJournal(generation, evidence);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Invalid migration journal value", failure);
        }
    }

    private static Map<String, String> fields(
            String[] lines, int first, String description) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = first; index < lines.length - 1; index++) {
            String line = lines[index];
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "Malformed " + description + " line");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!FIELDS.contains(key)) {
                throw new IllegalArgumentException(
                        "Unknown " + description + " field: " + key);
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate " + description + " field: " + key);
            }
        }
        if (!values.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException(
                    "Migration " + description + " fields are incomplete");
        }
        return values;
    }

    private static MigrationTargetState.Phase phaseOf(
            MigrationEvidence evidence) {
        try {
            MigrationTargetState.Phase phase =
                    MigrationTargetState.Phase.valueOf(evidence.phase());
            if (phase == MigrationTargetState.Phase.NO_EVIDENCE
                    || phase == MigrationTargetState.Phase.LOCKED) {
                throw new IllegalArgumentException(
                        "Journal cannot persist transient phase " + phase);
            }
            return phase;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Journal evidence has invalid phase", failure);
        }
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
                    "Migration journal is not strict UTF-8", failure);
        }
    }
}
