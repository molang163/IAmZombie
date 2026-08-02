package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetConfigValidatorTest {
    private static final List<Class<?>> MIGRATION_TYPES = List.of(
            MigrationTarget.class,
            ConfigSchemaCatalog.class,
            LegacyConfigParser.class,
            ConfigProjection.class,
            ConfigProjectionCodec.class,
            TargetConfigValidator.class);
    private static final Set<String> FORBIDDEN_LINKS = Set.of(
            "IAmZombieConfig",
            "IAmZombieServerConfig",
            "IAmZombiePreferencesConfig",
            "IAmZombieClientConfig",
            "ConfigValue",
            "FileConfig",
            "Class.forName",
            "MethodHandles",
            "ServiceLoader",
            "loadClass(",
            "iamzombieq-client.toml",
            "playerSkinMode",
            "firstPersonArmSkinMode");

    @Test
    void rawValidationRejectsMissingUnknownTypeRangeAndInvalidList() {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        TargetConfigValidator validator = new TargetConfigValidator(schema);
        Map<String, Object> valid =
                ConfigProjection.projectCatalogDefaults(schema).serverValues();
        assertTrue(validator.validate(MigrationTarget.SERVER, valid).valid());

        LinkedHashMap<String, Object> missing = new LinkedHashMap<>(valid);
        missing.remove("startingRottenFlesh");
        assertIssue(validator.validate(MigrationTarget.SERVER, missing),
                TargetConfigValidator.Kind.MISSING, "startingRottenFlesh");

        LinkedHashMap<String, Object> unknown = new LinkedHashMap<>(valid);
        unknown.put("not.in.authority", true);
        assertIssue(validator.validate(MigrationTarget.SERVER, unknown),
                TargetConfigValidator.Kind.UNKNOWN, "not.in.authority");

        LinkedHashMap<String, Object> wrongType = new LinkedHashMap<>(valid);
        wrongType.put("startingRottenFlesh", "nine");
        assertIssue(validator.validate(MigrationTarget.SERVER, wrongType),
                TargetConfigValidator.Kind.TYPE, "startingRottenFlesh");

        LinkedHashMap<String, Object> outOfRange = new LinkedHashMap<>(valid);
        outOfRange.put("startingRottenFlesh", 65L);
        assertIssue(validator.validate(MigrationTarget.SERVER, outOfRange),
                TargetConfigValidator.Kind.RANGE, "startingRottenFlesh");

        LinkedHashMap<String, Object> invalidList = new LinkedHashMap<>(valid);
        invalidList.put("zombieFoods", List.of("missing_namespace_separator"));
        assertIssue(validator.validate(MigrationTarget.SERVER, invalidList),
                TargetConfigValidator.Kind.TYPE, "zombieFoods");

        LinkedHashMap<String, Object> holderEquivalent = new LinkedHashMap<>(valid);
        holderEquivalent.put("herobrineJoltEnabled", "FALSE");
        holderEquivalent.put("zombieFoods", List.of(":path", "modid:", ":"));
        assertTrue(
                validator.validate(MigrationTarget.SERVER, holderEquivalent).valid(),
                "the dormant shadow validator must accept every value accepted by "
                        + "the canonical holder validators");
    }

    @Test
    void encodedValidationIsCorrectionStableWithoutCorrectingTheInput() {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        TargetConfigValidator validator = new TargetConfigValidator(schema);
        Map<String, Object> valid =
                ConfigProjection.projectCatalogDefaults(schema).serverValues();
        String canonical =
                ConfigProjectionCodec.encode(MigrationTarget.SERVER, valid, schema);

        assertTrue(validator.validateEncoded(MigrationTarget.SERVER, canonical).valid());
        assertTrue(validator.validateEncoded(
                        MigrationTarget.SERVER, canonical.replace("\n", "\r\n"))
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical + "\nunknownTargetKey = true\n")
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replace("startingRottenFlesh = 8", "startingRottenFlesh = \"eight\""))
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replace(
                                "debugLogging = false",
                                "debugLogging = \"false\""))
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replace("startingRottenFlesh = 8", "startingRottenFlesh = 65"))
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replace(
                                "minecraft:rotten_flesh",
                                "missing_namespace_separator"))
                .valid());

        ConfigSchemaCatalog.Entry first =
                schema.entries(MigrationTarget.SERVER).getFirst();
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replaceFirst(
                                java.util.regex.Pattern.quote("#" + first.comment()),
                                "# changed"))
                .valid());
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replaceFirst(
                                java.util.regex.Pattern.quote("#" + first.comment()),
                                "# " + first.comment()))
                .valid(),
                "a comment marker that NightConfig parses with an extra leading "
                        + "space is not NeoForge correction-stable");
        assertTrue(canonical.contains("# Default: 8"));
        assertFalse(validator.validateEncoded(
                        MigrationTarget.SERVER,
                        canonical.replaceFirst(
                                "# Default: 8",
                                "#Default: 8"))
                .valid(),
                "range metadata must retain the one leading space supplied by "
                        + "the canonical NeoForge ValueSpec comment");

        Map<String, Object> correctionOne =
                validator.correct(MigrationTarget.SERVER, valid);
        Map<String, Object> correctionTwo =
                validator.correct(MigrationTarget.SERVER, correctionOne);
        assertEquals(valid, correctionOne);
        assertEquals(correctionOne, correctionTwo);
        assertEquals(
                ConfigProjectionCodec.typedSha256(
                        MigrationTarget.SERVER, correctionOne, schema),
                ConfigProjectionCodec.typedSha256(
                        MigrationTarget.SERVER, correctionTwo, schema));
    }

    @Test
    void migrationSourcesAndBytecodeDoNotLinkHoldersFileConfigOrAppearance()
            throws IOException {
        Path root = Path.of("src/main/java/dev/molang/iamzombieq/config");
        for (Class<?> type : MIGRATION_TYPES) {
            Path source = root.resolve(type.getSimpleName() + ".java");
            String sourceText = Files.readString(source);
            for (String token : FORBIDDEN_LINKS) {
                assertFalse(
                        sourceText.contains(token),
                        () -> source + " links forbidden token " + token);
            }

            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new AssertionError("missing class bytes for " + type.getName());
                }
                String constantPool =
                        new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                for (String token : FORBIDDEN_LINKS) {
                    assertFalse(
                            constantPool.contains(token),
                            () -> type.getSimpleName() + " bytecode links " + token);
                }
            }
        }

        String parser = Files.readString(root.resolve("LegacyConfigParser.java"));
        assertFalse(parser.contains("java.nio.file"));
        assertFalse(parser.contains("FileInputStream"));
        assertFalse(parser.contains("FileOutputStream"));
    }

    private static void assertIssue(
            TargetConfigValidator.Result result,
            TargetConfigValidator.Kind kind,
            String key) {
        assertTrue(
                result.issues().contains(new TargetConfigValidator.Issue(kind, key)),
                () -> "missing " + kind + " issue for " + key + ": " + result.issues());
    }
}
