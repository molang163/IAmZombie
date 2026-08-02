package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigMigrationActivationLifecycleSourceTest {
    private static final Path BRIDGE = Path.of(
            "src/main/java/dev/molang/iamzombieq/config/"
                    + "ConfigMigrationBootstrap.java");
    private static final Path MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/mixin/"
                    + "ServerLifecycleHooksMixin.java");
    private static final Path MOD = Path.of(
            "src/main/java/dev/molang/iamzombieq/IAmZombieMod.java");
    private static final Path MIXINS =
            Path.of("src/main/resources/iamzombieq.mixins.json");

    @Test
    void serverHookIsRequiredUnmappedHeadAndDoesNotSwallowF1()
            throws Exception {
        String source = Files.readString(MIXIN);
        assertTrue(source.contains("ServerLifecycleHooks.class"));
        assertTrue(source.contains(
                "method = \"handleServerAboutToStart"
                        + "(Lnet/minecraft/server/MinecraftServer;)V\""));
        assertTrue(source.contains("@At(\"HEAD\")"));
        assertTrue(source.contains("remap = false"));
        assertTrue(source.contains("require = 1"));
        assertTrue(source.contains(
                "ConfigMigrationBootstrap.migrateServer(server)"));
        assertFalse(source.contains("catch ("));
        String mixins = Files.readString(MIXINS);
        assertTrue(mixins.contains("\"required\": true"));
        assertTrue(mixins.contains("\"ServerLifecycleHooksMixin\""));
    }

    @Test
    void physicalPreferencesEntryGatesDatagenAndDedicatedBeforePaths()
            throws Exception {
        String source = Files.readString(BRIDGE);
        int method = source.indexOf(
                "migratePhysicalClientPreferences()");
        int datagen = source.indexOf(
                "DatagenModLoader.isRunningDataGen()", method);
        int dedicated = source.indexOf(
                "Dist.DEDICATED_SERVER", datagen);
        int configPath = source.indexOf("FMLPaths.CONFIGDIR", dedicated);
        assertTrue(method >= 0);
        assertTrue(datagen > method);
        assertTrue(dedicated > datagen);
        assertTrue(configPath > dedicated);
        assertTrue(source.substring(method, configPath).contains(
                "FMLEnvironment.getDist() == Dist.DEDICATED_SERVER"),
                "the physical-client entry must return on dedicated, not invert the gate");
        assertFalse(source.substring(method, configPath)
                .contains(ActualTargetResolver.PREFERENCES_BASENAME));
    }

    @Test
    void physicalPreferencesMigrationRunsBeforeAnyConfigRegistration()
            throws Exception {
        String source = Files.readString(MOD);
        int constructor =
                source.indexOf("public IAmZombieMod(");
        int migration = source.indexOf(
                "ConfigMigrationBootstrap"
                        + ".migratePhysicalClientPreferences()",
                constructor);
        int firstRegistration =
                source.indexOf("registerConfig(", constructor);
        assertTrue(constructor >= 0);
        assertTrue(migration > constructor);
        assertTrue(firstRegistration > migration);
    }

    @Test
    void serverEntryUsesRawWorldPathWithoutCreatingDirectoryHelper()
            throws Exception {
        String source = Files.readString(BRIDGE);
        assertTrue(source.contains("new LevelResource(\"serverconfig\")"));
        assertTrue(source.contains("FMLPaths.CONFIGDIR"));
        assertFalse(source.contains("createDirectories"));
        assertFalse(source.contains("getServerConfigPath"));
    }

    @Test
    void bridgeIsExplicitlyInternalAndContainsNoFailureSwallowing()
            throws Exception {
        String source = Files.readString(BRIDGE);
        assertTrue(source.contains("@ApiStatus.Internal"));
        assertTrue(source.contains(
                "public final class ConfigMigrationBootstrap"));
        assertFalse(source.contains("catch ("));
    }
}
