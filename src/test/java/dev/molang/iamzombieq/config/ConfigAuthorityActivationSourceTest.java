package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigAuthorityActivationSourceTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path MOD =
            MAIN.resolve("dev/molang/iamzombieq/IAmZombieMod.java");
    private static final Path CLIENT =
            MAIN.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java");
    private static final Path CLIENT_CONFIGURATION_MIXIN = MAIN.resolve(
            "dev/molang/iamzombieq/mixin/client/"
                    + "ClientConfigurationPacketListenerMixin.java");
    private static final Path SERVER_CONFIGURATION_MIXIN = MAIN.resolve(
            "dev/molang/iamzombieq/mixin/"
                    + "ServerConfigurationPacketListenerMixin.java");
    private static final Path CLIENT_MOUNT_MIXIN = MAIN.resolve(
            "dev/molang/iamzombieq/mixin/client/"
                    + "LivingEntityAuthorityMixin.java");
    private static final Path COMMON_MOUNT_MIXIN = MAIN.resolve(
            "dev/molang/iamzombieq/mixin/LivingEntityMixin.java");

    @Test
    void productionRegistersPayloadsWithoutInitialOnlyTaskEvent()
            throws Exception {
        String source = Files.readString(MOD);
        String compact = source.replaceAll("\\s+", "");

        assertTrue(source.contains("MethodHandles.privateLookupIn("));
        assertTrue(source.contains("\"registerPayloads\""));
        assertTrue(compact.contains(
                "addListener(IAmZombieMod::iamzombieq$registerAuthorityPayloads)"));
        assertFalse(source.contains("RegisterConfigurationTasksEvent"));
        assertFalse(source.contains("\"registerConfigurationTask\""));
        assertFalse(source.contains("import dev.molang.iamzombieq.config."
                + "ConfigAuthorityRuntime;"));
        assertFalse(source.contains("ConfigAuthorityRuntime::"));
    }

    @Test
    void everyInitialAndReconfigurationReturnQueuesAuthorityBeforeWorldEntry()
            throws Exception {
        String source = Files.readString(SERVER_CONFIGURATION_MIXIN);
        String compact = source.replaceAll("\\s+", "");

        assertTrue(source.contains(
                "@Mixin(ServerConfigurationPacketListenerImpl.class)"));
        assertTrue(source.contains(
                "@Shadow @Final private Queue<ConfigurationTask> "
                        + "configurationTasks"));
        assertTrue(source.contains(
                "method = \"returnToWorld()V\""));
        assertTrue(source.contains("at = @At(\"HEAD\")"));
        assertTrue(source.contains("require = 1"));
        assertTrue(source.contains("\"beginServerConfiguration\""));
        assertTrue(compact.contains("configurationTasks.add("));
        assertTrue(source.contains("MethodHandles.privateLookupIn("));
        assertFalse(source.contains("RegisterConfigurationTasksEvent"));
    }

    @Test
    void everyClientConfigurationListenerStartsPendingWithoutBypass()
            throws Exception {
        String source = Files.readString(CLIENT_CONFIGURATION_MIXIN);
        String compact = source.replaceAll("\\s+", "");

        assertTrue(source.contains(
                "@Mixin(ClientConfigurationPacketListenerImpl.class)"));
        assertTrue(source.contains(
                "method = \"<init>(Lnet/minecraft/client/Minecraft;\""));
        assertTrue(source.contains(
                "Lnet/minecraft/network/Connection;"));
        assertTrue(source.contains(
                "Lnet/minecraft/client/multiplayer/CommonListenerCookie;)V"));
        assertTrue(source.contains("at = @At(\"TAIL\")"));
        assertTrue(source.contains("require = 1"));
        assertTrue(source.contains("MethodHandles.privateLookupIn("));
        assertTrue(source.contains("\"beginClientConfiguration\""));
        assertTrue(source.contains("invokeExact(connection)"));
        assertTrue(compact.contains(
                "method=\"handleConfigurationFinished(\""
                        + "+\"Lnet/minecraft/network/protocol/configuration/\""
                        + "+\"ClientboundFinishConfigurationPacket;)V\""));
        assertTrue(compact.contains(
                "target=\"Lnet/minecraft/network/protocol/PacketUtils;"
                        + "\"+\"ensureRunningOnSameThread("));
        assertTrue(compact.contains("shift=At.Shift.AFTER"));
        assertTrue(compact.contains("cancellable=true"));
        assertTrue(source.contains("\"requireReady\""));
        assertTrue(compact.contains(
                "REQUIRE_READY.invokeExact("
                        + "iamzombieq$authorityConnection)"));
        assertTrue(source.contains(
                "iamzombieq$authorityConnection.disconnect("));
        assertTrue(source.contains("callback.cancel()"));
        assertFalse(source.contains("import dev.molang.iamzombieq.config."
                + "ConfigAuthorityRuntime;"));
        assertFalse(source.contains("isMemoryConnection"));
        assertTrue(source.contains("throw iamzombieq$failClosed(failure)"));
    }

    @Test
    void logoutClearsTheExactConnectionAndFoodTooltipUsesOnlyRemote19()
            throws Exception {
        String source = Files.readString(CLIENT);

        assertTrue(source.contains("MethodHandles.privateLookupIn("));
        assertTrue(source.contains("\"clear\""));
        assertTrue(source.contains("\"configuredZombieFoods\""));
        assertTrue(source.contains("\"resolveFoodConfig\""));
        assertTrue(source.contains("invokeExact(event.getConnection())"));
        String configuredFoods = source.substring(
                source.indexOf("private static List<String> configuredZombieFoods("),
                source.indexOf("private static int resolveFoodConfig("));
        String resolvedFood = source.substring(
                source.indexOf("private static int resolveFoodConfig("),
                source.indexOf("private static Class<?> authorityRuntimeClass("));
        assertTrue(configuredFoods.contains("throw failClosed(failure)"));
        assertTrue(resolvedFood.contains("throw failClosed(failure)"));
        assertFalse(source.contains("import dev.molang.iamzombieq.config."
                + "ConfigAuthorityRuntime;"));
        assertFalse(source.contains("IAmZombieConfig"));
        assertTrue(source.contains(
                "config.getSpec() != IAmZombieServerConfig.SPEC"));
        assertFalse(source.replace(
                        "IAmZombieServerConfig.SPEC", "")
                .contains("IAmZombieServerConfig."),
                "the client may identity-check SERVER SPEC but must not read "
                        + "a local SERVER ConfigValue");
    }

    @Test
    void spiderClientSimulationCannotUseALocalServerDefault()
            throws Exception {
        String client = Files.readString(CLIENT_MOUNT_MIXIN);
        String common = Files.readString(COMMON_MOUNT_MIXIN);
        String compactClient = client.replaceAll("\\s+", "");

        assertTrue(client.contains("MethodHandles.privateLookupIn("));
        assertTrue(client.contains("\"spiderMountSpeed\""));
        assertTrue(compactClient.contains(
                "invokeExact(listener.getConnection())"));
        assertTrue(compactClient.contains(
                "catch(Throwablefailure){"
                        + "throwiamzombieq$failClosed(failure);}"));
        assertFalse(client.contains("import dev.molang.iamzombieq.config."
                + "ConfigAuthorityRuntime;"));
        assertFalse(client.contains("IAmZombieConfig"));
        assertFalse(client.contains("IAmZombieServerConfig"));
        int clientSideGate = common.indexOf(
                ".level().isClientSide()");
        int serverRead = common.indexOf(
                "IAmZombieServerConfig.SPIDER_MOUNT_SPEED.get()");
        assertTrue(clientSideGate >= 0 && serverRead > clientSideGate);
        assertTrue(common.substring(clientSideGate, serverRead)
                .contains("return;"));
    }
}
