package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationEvidenceCodecTest {
    @Test
    void strictCodecIsDeterministicAndRoundTripsCompleteEvidence() {
        MigrationEvidence evidence = MigrationEvidenceTest.sample(
                MigrationTarget.SERVER,
                Path.of("/config/iamzombieq-server.toml"));
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();

        byte[] first = codec.encode(evidence);
        byte[] second = codec.encode(evidence);
        assertArrayEquals(first, second);
        assertTrue(new String(first, StandardCharsets.UTF_8)
                .startsWith("IAMZOMBIEQ-MIGRATION-EVIDENCE\nversion=1\n"));
        assertEquals(evidence, codec.decode(first));
    }

    @Test
    void markerRequiresCompleteParentBinding() {
        MigrationEvidenceCodec codec = new MigrationEvidenceCodec();
        String encoded = new String(
                codec.encode(MigrationEvidenceTest.sample(
                        MigrationTarget.SERVER,
                        Path.of("/config/iamzombieq-server.toml"))),
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
                Path.of("/config/iamzombieq-server.toml"));
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
