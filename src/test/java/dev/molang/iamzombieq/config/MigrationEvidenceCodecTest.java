package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationEvidenceCodecTest {
    @Test
    void strictCodecIsDeterministicAndRoundTripsCompleteEvidence() {
        MigrationEvidence evidence = MigrationEvidenceTest.sample(
                MigrationTarget.SERVER,
                MigrationBindingTest.absolutePath(
                        "config", "iamzombieq-server.toml"));
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();

        byte[] first = codec.encode(evidence);
        byte[] second = codec.encode(evidence);
        assertArrayEquals(first, second);
        assertTrue(new String(first, StandardCharsets.UTF_8)
                .startsWith("IAMZOMBIEQ-MIGRATION-EVIDENCE\nversion=1\n"));
        assertEquals(evidence, codec.decode(first));
    }

    @Test
    void taggedWindowsFingerprintsRoundTripWithoutChangingEvidenceSchema() {
        Path target = MigrationBindingTest.absolutePath(
                "config", "iamzombieq-server.toml");
        Path parent = target.getParent();
        String tag = "WINDOWS_BASIC_FINGERPRINT_V1:AAECAwQ";
        MigrationBinding binding = new MigrationBinding(
                target,
                parent,
                parent,
                List.of(
                        new MigrationBinding.Ancestor(
                                target.getRoot(), tag + ":drive-root"),
                        new MigrationBinding.Ancestor(
                                parent, tag + ":logical-parent")),
                tag + ":logical-parent",
                "file:sun.nio.fs.WindowsFileSystemProvider",
                "Windows volume E|NTFS|sun.nio.fs.WindowsFileStore",
                25,
                "Windows 11");
        MigrationEvidence sample = MigrationEvidenceTest.sample(
                MigrationTarget.SERVER, target);
        MigrationEvidence evidence = MigrationEvidence.builder(MigrationTarget.SERVER)
                .target(target)
                .binding(binding)
                .schemaVersion(sample.schemaVersion())
                .profile(MigrationAccessProfile.BASIC)
                .commitProfile(MigrationEvidence.Durability.BASIC)
                .lockIdentity(tag + ":permanent-lock")
                .phase(sample.phase())
                .projectionSha256(sample.projectionSha256())
                .rawLegacySha256(sample.rawLegacySha256())
                .artifactHashes(sample.artifactHashes())
                .artifactDurability(Map.of(
                        "journal", MigrationEvidence.Durability.BASIC,
                        "backup", MigrationEvidence.Durability.BASIC,
                        "initial", MigrationEvidence.Durability.BASIC,
                        "target", MigrationEvidence.Durability.BASIC,
                        "marker", MigrationEvidence.Durability.BASIC))
                .build();
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();

        byte[] encoded = codec.encode(evidence);
        MigrationEvidence decoded = codec.decode(encoded);

        assertEquals(evidence, decoded);
        assertEquals(
                tag + ":drive-root",
                decoded.binding().ancestors().getFirst().identity());
        assertEquals(
                tag + ":logical-parent",
                decoded.binding().directoryIdentity());
        assertEquals(tag + ":permanent-lock", decoded.lockIdentity());
        String text = new String(encoded, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(
                "IAMZOMBIEQ-MIGRATION-EVIDENCE\nversion=1\n"));
        assertEquals(
                21,
                text.lines().count(),
                "tagged identities must use the unchanged version-1 field set");
    }

    @Test
    void markerRequiresCompleteParentBinding() {
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();
        String encoded = new String(
                codec.encode(MigrationEvidenceTest.sample(
                        MigrationTarget.SERVER,
                        MigrationBindingTest.absolutePath(
                                "config", "iamzombieq-server.toml"))),
                StandardCharsets.UTF_8);
        for (String field : List.of(
                "target",
                "logicalParent",
                "physicalParent",
                "ancestors",
                "directoryIdentity",
                "providerIdentity",
                "fileStoreIdentity",
                "schemaVersion",
                "profile",
                "lockIdentity",
                "phase",
                "projectionSha256",
                "rawLegacySha256",
                "artifactHashes",
                "artifactDurability")) {
            String omitted = encoded.replaceFirst(
                    "(?m)^" + java.util.regex.Pattern.quote(field) + "=.*\\n", "");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(omitted.getBytes(StandardCharsets.UTF_8)),
                    () -> "accepted omitted evidence field " + field);
        }
    }

    @Test
    void unknownDuplicateAndMismatchedEvidenceFailsClosed() {
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();
        MigrationEvidence expected = MigrationEvidenceTest.sample(
                MigrationTarget.SERVER,
                MigrationBindingTest.absolutePath(
                        "config", "iamzombieq-server.toml"));
        String encoded = new String(codec.encode(expected), StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode((encoded + "unknown=value\n")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode((encoded + "phase=COMPLETE\n")
                        .getBytes(StandardCharsets.UTF_8)));

        MigrationEvidence decoded = codec.decode(encoded
                .replace("profile=SECURE", "profile=BASIC")
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(
                IllegalStateException.class,
                () -> decoded.verifyBoundTo(
                        expected.target(), expected.binding(), expected.profile()));
    }
}
