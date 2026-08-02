package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombiePreferencesConfig;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigActivationContractTest {
    private static final Path MAIN =
            Path.of("src/main/java").toAbsolutePath().normalize();
    private static final Path MOD =
            MAIN.resolve("dev/molang/iamzombieq/IAmZombieMod.java");
    private static final Path FACADE =
            MAIN.resolve("dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path APPEARANCE =
            MAIN.resolve("dev/molang/iamzombieq/IAmZombieClientConfig.java");
    private static final Pattern REGISTRATION = Pattern.compile(
            "modContainer\\s*\\.\\s*registerConfig\\s*\\([^;]+\\);");

    @Test
    void everyLegacyFieldIsTheCanonicalConfigValueObject()
            throws ReflectiveOperationException {
        for (ConfigKeyCatalog.Entry entry : ConfigKeyCatalog.entries()) {
            Field facade = IAmZombieConfig.class.getField(entry.legacyField());
            ConfigKeyCatalog.Target target = entry.targets().getFirst();
            Class<?> owner = Class.forName(target.owner());
            Object canonical = owner.getField(target.field()).get(null);
            assertSame(
                    canonical,
                    facade.get(null),
                    entry.legacyField()
                            + " must alias its one canonical ConfigValue");
        }
        assertSame(
                IAmZombieServerConfig.SPEC,
                IAmZombieConfig.SPEC,
                "the compatibility SPEC field must forward to the SERVER spec");
        assertEquals(
                55,
                ConfigKeyCatalog.entries().size(),
                "the identity check must cover all legacy fields");
    }

    @Test
    void finalRegistrationIsCommonZeroServerOneClientTwo()
            throws IOException {
        String source = Files.readString(MOD);
        Matcher matcher = REGISTRATION.matcher(source);
        java.util.ArrayList<String> registrations = new java.util.ArrayList<>();
        while (matcher.find()) {
            registrations.add(matcher.group().replaceAll("\\s+", ""));
        }
        assertEquals(
                List.of(
                        "modContainer.registerConfig(ModConfig.Type.SERVER,IAmZombieServerConfig.SPEC);",
                        "modContainer.registerConfig(ModConfig.Type.CLIENT,IAmZombieClientConfig.SPEC);",
                        "modContainer.registerConfig(ModConfig.Type.CLIENT,IAmZombiePreferencesConfig.SPEC,\"iamzombieq-preferences-client.toml\");"),
                registrations);
        assertFalse(source.contains("ModConfig.Type.COMMON"));
        assertEquals(
                1,
                registrations.stream()
                        .filter(value -> value.contains("Type.SERVER"))
                        .count());
        assertEquals(
                2,
                registrations.stream()
                        .filter(value -> value.contains("Type.CLIENT"))
                        .count());
    }

    @Test
    void productionCallersDoNotUseTheLegacyFacade() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .filter(candidate -> !candidate.equals(FACADE))
                    .filter(candidate -> !candidate.toString()
                            .contains("/gametest/"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(
                        Pattern.compile("\\bIAmZombieConfig\\b")
                                .matcher(source)
                                .find(),
                        () -> "production legacy facade caller remains: "
                                + MAIN.relativize(path));
            }
        }
    }

    @Test
    void preferencesAndJoltCallersAreSplitAtSource() throws IOException {
        String client = Files.readString(
                MAIN.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String herobrine = Files.readString(
                MAIN.resolve("dev/molang/iamzombieq/gameplay/HerobrineEvents.java"));
        assertTrue(client.contains(
                "IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_ENABLED"));
        assertTrue(client.contains(
                "IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_NEAR_DISTANCE"));
        assertTrue(client.contains(
                "IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_FAR_DISTANCE"));
        assertTrue(client.contains(
                "IAmZombiePreferencesConfig.HEROBRINE_JOLT_VIGNETTE_ENABLED"));
        assertTrue(herobrine.contains(
                "IAmZombieServerConfig.HEROBRINE_JOLT_ENABLED"));
    }

    @Test
    void appearanceHolderRemainsByteIdenticalToFrozenBaseline()
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String actual = HexFormat.of().formatHex(
                digest.digest(Files.readAllBytes(APPEARANCE)));
        assertEquals(
                "bcb1664a7dacb0d0d5a850a8dca5d127e6f612276cdba0404784951ed2fe0c21",
                actual);
    }
}
