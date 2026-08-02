package dev.molang.iamzombieq.config;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class MigrationEvidenceCodec {
    private static final String MAGIC = "IAMZOMBIEQ-MIGRATION-EVIDENCE";
    private static final String VERSION = "1";
    private static final Set<String> FIELDS = Set.of(
            "targetKind",
            "target",
            "logicalParent",
            "physicalParent",
            "ancestors",
            "directoryIdentity",
            "providerIdentity",
            "fileStoreIdentity",
            "javaFeature",
            "operatingSystem",
            "schemaVersion",
            "profile",
            "commitProfile",
            "lockIdentity",
            "phase",
            "projectionSha256",
            "rawLegacySha256",
            "artifactHashes",
            "artifactDurability");

    byte[] encode(MigrationEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        StringBuilder encoded = new StringBuilder(1024);
        encoded.append(MAGIC).append('\n');
        append(encoded, "version", VERSION);
        append(encoded, "targetKind", evidence.targetKind().name());
        append(encoded, "target", encodeString(evidence.target().toString()));
        append(
                encoded,
                "logicalParent",
                encodeString(evidence.binding().logicalParent().toString()));
        append(
                encoded,
                "physicalParent",
                encodeString(evidence.binding().physicalParent().toString()));
        append(encoded, "ancestors", encodeAncestors(evidence.binding().ancestors()));
        append(
                encoded,
                "directoryIdentity",
                encodeString(evidence.binding().directoryIdentity()));
        append(
                encoded,
                "providerIdentity",
                encodeString(evidence.binding().providerIdentity()));
        append(
                encoded,
                "fileStoreIdentity",
                encodeString(evidence.binding().fileStoreIdentity()));
        append(
                encoded,
                "javaFeature",
                Integer.toString(evidence.binding().javaFeature()));
        append(
                encoded,
                "operatingSystem",
                encodeString(evidence.binding().operatingSystem()));
        append(encoded, "schemaVersion", encodeString(evidence.schemaVersion()));
        append(encoded, "profile", evidence.profile().name());
        append(encoded, "commitProfile", evidence.commitProfile().name());
        append(encoded, "lockIdentity", encodeString(evidence.lockIdentity()));
        append(encoded, "phase", evidence.phase());
        append(encoded, "projectionSha256", evidence.projectionSha256());
        append(encoded, "rawLegacySha256", evidence.rawLegacySha256());
        append(
                encoded,
                "artifactHashes",
                encodeStringMap(evidence.artifactHashes()));
        append(
                encoded,
                "artifactDurability",
                encodeDurabilityMap(evidence.artifactDurability()));
        return encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    MigrationEvidence decode(byte[] bytes) {
        String encoded = decodeStrictUtf8(bytes);
        if (encoded.indexOf('\r') >= 0 || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException(
                    "Evidence must use canonical LF lines with a terminal newline");
        }

        String[] lines = encoded.split("\n", -1);
        if (lines.length < 4
                || !lines[0].equals(MAGIC)
                || !lines[1].equals("version=" + VERSION)
                || !lines[lines.length - 1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid migration evidence header or version");
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (int index = 2; index < lines.length - 1; index++) {
            String line = lines[index];
            int separator = line.indexOf('=');
            if (separator <= 0 || separator != line.lastIndexOf('=')) {
                throw new IllegalArgumentException(
                        "Malformed migration evidence line: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!FIELDS.contains(key)) {
                throw new IllegalArgumentException(
                        "Unknown migration evidence field: " + key);
            }
            if (fields.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate migration evidence field: " + key);
            }
        }
        if (!fields.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException(
                    "Migration evidence fields are incomplete");
        }

        try {
            Path target = Path.of(decodeString(fields.get("target")));
            Path logicalParent =
                    Path.of(decodeString(fields.get("logicalParent")));
            Path physicalParent =
                    Path.of(decodeString(fields.get("physicalParent")));
            MigrationBinding binding = MigrationBinding.capture(
                    new MigrationBinding.Observation(
                            target,
                            logicalParent,
                            physicalParent,
                            decodeAncestors(fields.get("ancestors")),
                            decodeString(fields.get("directoryIdentity")),
                            decodeString(fields.get("providerIdentity")),
                            decodeString(fields.get("fileStoreIdentity")),
                            Integer.parseInt(fields.get("javaFeature")),
                            decodeString(fields.get("operatingSystem"))));

            return MigrationEvidence.builder(
                            MigrationTarget.valueOf(fields.get("targetKind")))
                    .target(target)
                    .binding(binding)
                    .schemaVersion(decodeString(fields.get("schemaVersion")))
                    .profile(MigrationAccessProfile.valueOf(fields.get("profile")))
                    .commitProfile(MigrationEvidence.Durability.valueOf(
                            fields.get("commitProfile")))
                    .lockIdentity(decodeString(fields.get("lockIdentity")))
                    .phase(fields.get("phase"))
                    .projectionSha256(fields.get("projectionSha256"))
                    .rawLegacySha256(fields.get("rawLegacySha256"))
                    .artifactHashes(
                            decodeStringMap(fields.get("artifactHashes")))
                    .artifactDurability(
                            decodeDurabilityMap(fields.get("artifactDurability")))
                    .build();
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Invalid migration evidence value", failure);
        }
    }

    private static void append(
            StringBuilder destination, String key, String value) {
        Objects.requireNonNull(value, key);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Evidence value contains a line break: " + key);
        }
        destination.append(key).append('=').append(value).append('\n');
    }

    private static String encodeAncestors(
            List<MigrationBinding.Ancestor> ancestors) {
        ArrayList<String> values = new ArrayList<>(ancestors.size());
        for (MigrationBinding.Ancestor ancestor : ancestors) {
            values.add(encodeString(ancestor.path().toString())
                    + ":"
                    + encodeString(ancestor.identity()));
        }
        return String.join(",", values);
    }

    private static List<MigrationBinding.Ancestor> decodeAncestors(
            String encoded) {
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Ancestors must not be empty");
        }
        ArrayList<MigrationBinding.Ancestor> ancestors = new ArrayList<>();
        for (String item : encoded.split(",", -1)) {
            String[] pair = splitPair(item, "ancestor");
            ancestors.add(new MigrationBinding.Ancestor(
                    Path.of(decodeString(pair[0])), decodeString(pair[1])));
        }
        return List.copyOf(ancestors);
    }

    private static String encodeStringMap(Map<String, String> values) {
        TreeMap<String, String> sorted = new TreeMap<>(values);
        ArrayList<String> encoded = new ArrayList<>(sorted.size());
        sorted.forEach((key, value) -> encoded.add(
                encodeString(key) + ":" + encodeString(value)));
        return String.join(",", encoded);
    }

    private static Map<String, String> decodeStringMap(String encoded) {
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException(
                    "Artifact hash map must not be empty");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String item : encoded.split(",", -1)) {
            String[] pair = splitPair(item, "artifact hash");
            String key = decodeString(pair[0]);
            String value = decodeString(pair[1]);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate artifact hash key: " + key);
            }
        }
        return values;
    }

    private static String encodeDurabilityMap(
            Map<String, MigrationEvidence.Durability> values) {
        TreeMap<String, MigrationEvidence.Durability> sorted =
                new TreeMap<>(values);
        ArrayList<String> encoded = new ArrayList<>(sorted.size());
        sorted.forEach((key, value) -> encoded.add(
                encodeString(key) + ":" + value.name()));
        return String.join(",", encoded);
    }

    private static Map<String, MigrationEvidence.Durability>
            decodeDurabilityMap(String encoded) {
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException(
                    "Artifact durability map must not be empty");
        }
        LinkedHashMap<String, MigrationEvidence.Durability> values =
                new LinkedHashMap<>();
        for (String item : encoded.split(",", -1)) {
            String[] pair = splitPair(item, "artifact durability");
            String key = decodeString(pair[0]);
            MigrationEvidence.Durability value =
                    MigrationEvidence.Durability.valueOf(pair[1]);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate artifact durability key: " + key);
            }
        }
        return values;
    }

    private static String[] splitPair(String encoded, String description) {
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator != encoded.lastIndexOf(':')) {
            throw new IllegalArgumentException(
                    "Malformed " + description + " entry");
        }
        return new String[] {
            encoded.substring(0, separator), encoded.substring(separator + 1)
        };
    }

    private static String encodeString(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeString(String value) {
        try {
            return decodeStrictUtf8(Base64.getUrlDecoder().decode(value));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Malformed evidence string encoding", failure);
        }
    }

    private static String decodeStrictUtf8(byte[] bytes) {
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
                    "Migration evidence is not strict UTF-8", failure);
        }
    }
}
