package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.IAmZombieClientConfig;
import dev.molang.iamzombieq.rules.ZombiePlayerSkinMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigAuthoritySourceTest {
    private static final Path PRODUCTION_ROOT = Path.of("src/main/java");
    private static final Path MOD_SOURCE =
            PRODUCTION_ROOT.resolve("dev/molang/iamzombieq/IAmZombieMod.java");
    private static final Path SERVER_HOLDER =
            PRODUCTION_ROOT.resolve("dev/molang/iamzombieq/IAmZombieServerConfig.java");
    private static final Path PREFERENCES_HOLDER =
            PRODUCTION_ROOT.resolve("dev/molang/iamzombieq/IAmZombiePreferencesConfig.java");
    private static final Path KEY_CATALOG =
            PRODUCTION_ROOT.resolve("dev/molang/iamzombieq/config/ConfigKeyCatalog.java");

    private static final Pattern CONFIG_REGISTRATION =
            Pattern.compile("modContainer\\s*\\.\\s*registerConfig\\s*\\([^;]+\\);");
    private static final Pattern ANY_CONFIG_REGISTRATION =
            Pattern.compile("\\bregisterConfig\\s*\\(");
    @Test
    void canonicalAuthorityHolderSourcesExist() {
        assertAll(
                () -> assertTrue(Files.isRegularFile(SERVER_HOLDER),
                        () -> "missing canonical SERVER schema holder: " + SERVER_HOLDER.toAbsolutePath()),
                () -> assertTrue(Files.isRegularFile(PREFERENCES_HOLDER),
                        () -> "missing canonical preferences schema holder: "
                                + PREFERENCES_HOLDER.toAbsolutePath()));
    }

    @Test
    void productionRegistrationIsTheAtomicC1Triple() throws IOException {
        String source = requireSource(MOD_SOURCE, "mod entry point");
        Matcher matcher = CONFIG_REGISTRATION.matcher(source);
        List<String> registrations = new ArrayList<>();
        while (matcher.find()) {
            registrations.add(compact(matcher.group()));
        }

        assertEquals(List.of(
                        "modContainer.registerConfig(ModConfig.Type.SERVER,IAmZombieServerConfig.SPEC);",
                        "modContainer.registerConfig(ModConfig.Type.CLIENT,IAmZombieClientConfig.SPEC);",
                        "modContainer.registerConfig(ModConfig.Type.CLIENT,IAmZombiePreferencesConfig.SPEC,\"iamzombieq-preferences-client.toml\");"),
                registrations,
                "activation must atomically expose SERVER plus the unchanged appearance CLIENT and explicit preferences CLIENT");
        assertFalse(source.contains("ModConfig.Type.COMMON"),
                "the transitional COMMON registration must not survive the activation candidate");

        int repositoryRegistrationCount = 0;
        try (Stream<Path> paths = Files.walk(PRODUCTION_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .toList()) {
                Matcher repositoryMatcher = ANY_CONFIG_REGISTRATION.matcher(
                        stripCommentsAndLiterals(Files.readString(path)));
                while (repositoryMatcher.find()) {
                    repositoryRegistrationCount++;
                }
            }
        }
        assertEquals(3, repositoryRegistrationCount,
                "activation must register exactly SERVER=1 and CLIENT=2");
    }

    @Test
    void authorityHoldersAreDedicatedServerSafe() throws IOException {
        for (Path holder : List.of(SERVER_HOLDER, PREFERENCES_HOLDER)) {
            String source = requireSource(holder, "authority schema holder");
            assertAll(holder.toString(),
                    () -> assertFalse(source.contains("net.minecraft.client"),
                            "config holders must not link net.minecraft.client classes"),
                    () -> assertFalse(source.contains("dev.molang.iamzombieq.client"),
                            "config holders must not link the mod's client package"),
                    () -> assertFalse(source.contains("Dist.CLIENT"),
                            "config holders must be loadable without a physical-client distribution guard"),
                    () -> assertFalse(source.contains("OnlyIn"),
                            "config holders must not carry client-only annotations"));
        }
    }

    @Test
    void keyCatalogRecordsMetadataWithoutReadingEitherHolder() throws IOException {
        String rawSource = requireSource(KEY_CATALOG, "config key catalog");
        String executableSource = stripCommentsAndLiterals(rawSource);
        Pattern holderMemberAccess = Pattern.compile(
                "\\bIAmZombie(?:Server|Preferences)Config\\s*\\.\\s*(?!class\\b)");

        assertFalse(holderMemberAccess.matcher(executableSource).find(),
                "the catalog may name a holder owner but must not read SPEC or ConfigValue members");
        assertFalse(rawSource.contains("Class.forName"),
                "the dormant catalog must not activate either holder reflectively");
        assertFalse(rawSource.contains("MethodHandles") || rawSource.contains("ServiceLoader")
                        || rawSource.contains("loadClass(") || rawSource.contains("getField(")
                        || rawSource.contains("getDeclaredField(") || rawSource.contains("getMethod(")
                        || rawSource.contains("getDeclaredMethod("),
                "the dormant catalog must not use an alternate reflective loading path");
    }

    @Test
    void appearanceSchemaRemainsTheExactTwoLegacyOptions() {
        assertEquals(Set.of("playerSkinMode", "firstPersonArmSkinMode"),
                IAmZombieClientConfig.SPEC.getValues().valueMap().keySet(),
                "iamzombieq-client.toml must remain the two-key appearance file");

        assertEquals(List.of("playerSkinMode"), IAmZombieClientConfig.PLAYER_SKIN_MODE.getPath());
        assertSame(ZombiePlayerSkinMode.MONSTER_TEXTURE,
                IAmZombieClientConfig.PLAYER_SKIN_MODE.getDefault());
        assertEquals(
                "How zombie players are skinned in third person. MONSTER_TEXTURE uses vanilla zombie/drowned/husk "
                        + "textures on the player model; PLAYER_SKIN keeps the player's own skin.\n"
                        + "Allowed Values: MONSTER_TEXTURE, PLAYER_SKIN",
                IAmZombieClientConfig.PLAYER_SKIN_MODE.getSpec().getComment());

        assertEquals(List.of("firstPersonArmSkinMode"),
                IAmZombieClientConfig.FIRST_PERSON_ARM_SKIN_MODE.getPath());
        assertSame(ZombiePlayerSkinMode.MONSTER_TEXTURE,
                IAmZombieClientConfig.FIRST_PERSON_ARM_SKIN_MODE.getDefault());
        assertEquals(
                "How first-person arms are skinned. MONSTER_TEXTURE uses the current zombie form texture; "
                        + "PLAYER_SKIN keeps vanilla player arms.\n"
                        + "Allowed Values: MONSTER_TEXTURE, PLAYER_SKIN",
                IAmZombieClientConfig.FIRST_PERSON_ARM_SKIN_MODE.getSpec().getComment());
    }

    private static void assertNoProductionReferenceOutsideOwnSource(Path ownSource, String simpleName)
            throws IOException {
        Pattern reference = Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b");
        Pattern reflectiveLoad = Pattern.compile(
                "Class\\s*\\.\\s*forName\\s*\\([^)]*" + Pattern.quote(simpleName), Pattern.DOTALL);
        try (Stream<Path> paths = Files.walk(PRODUCTION_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .filter(candidate -> !candidate.equals(ownSource))
                    .filter(candidate -> !candidate.equals(KEY_CATALOG))
                    .sorted()
                    .toList()) {
                String rawSource = Files.readString(path);
                String executableSource = stripCommentsAndLiterals(rawSource);
                assertFalse(reference.matcher(executableSource).find(),
                        () -> simpleName + " must remain dormant; production reference found in " + path);
                assertFalse(reflectiveLoad.matcher(rawSource).find(),
                        () -> simpleName + " must not be activated reflectively from " + path);
            }
        }
    }

    private static String requireSource(Path path, String label) throws IOException {
        assertTrue(Files.isRegularFile(path),
                () -> "missing " + label + " source: " + path.toAbsolutePath());
        return Files.readString(path);
    }

    private static String stripCommentsAndLiterals(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    code.append('\n');
                } else {
                    code.append(' ');
                }
            } else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    code.append("  ");
                    index++;
                    inBlockComment = false;
                } else {
                    code.append(current == '\n' ? '\n' : ' ');
                }
            } else if (inString) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && index + 1 < source.length()) {
                    code.append(' ');
                    index++;
                } else if (current == '"') {
                    inString = false;
                }
            } else if (inChar) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && index + 1 < source.length()) {
                    code.append(' ');
                    index++;
                } else if (current == '\'') {
                    inChar = false;
                }
            } else if (current == '/' && next == '/') {
                code.append("  ");
                index++;
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                code.append("  ");
                index++;
                inBlockComment = true;
            } else if (current == '"') {
                code.append(' ');
                inString = true;
            } else if (current == '\'') {
                code.append(' ');
                inChar = true;
            } else {
                code.append(current);
            }
        }
        return code.toString();
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
