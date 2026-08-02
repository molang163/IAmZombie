package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.molang.iamzombieq.util.SourceScan;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigAuthorityReloadBridgeSourceTest {
    private static final Path CLIENT = Path.of(
            "src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java");
    private static final Path RELOAD_QUEUE = Path.of(
            "src/main/java/dev/molang/iamzombieq/client/"
                    + "ConfigAuthorityReloadQueue.java");

    @Test
    void clientModBusInstallsServerReloadAuthorityBridge() throws Exception {
        String source = Files.readString(CLIENT);
        String handler = SourceScan.methodBody(
                source, "private static void onServerConfigReloading(");
        String compact = SourceScan.compact(
                SourceScan.stripComments(handler));

        assertTrue(source.contains(
                "modEventBus.addListener("
                        + "IAmZombieClient::onServerConfigReloading)"),
                "client mod-bus registration is missing the SERVER reload bridge");
        assertTrue(source.contains("ModConfigEvent.Reloading"));
        assertTrue(source.contains(
                "\"refreshClientFromSyncedServerConfig\""));
        assertTrue(compact.contains(
                "!IAmZombieMod.MOD_ID.equals(config.getModId())"));
        assertTrue(compact.contains(
                "config.getType()!=ModConfig.Type.SERVER"));
        assertTrue(compact.contains(
                "config.getSpec()!=IAmZombieServerConfig.SPEC"));
        assertTrue(compact.contains("config.getLoadedConfig()"));
        assertTrue(compact.contains(
                "loaded.config().configFormat().isInMemory()"));
        assertTrue(compact.contains(
                "captured.isMemoryConnection()==inMemory"));
        assertFalse(handler.contains("IAmZombiePreferencesConfig.SPEC"));
        assertFalse(handler.contains("IAmZombieClientConfig.SPEC"));
        assertFalse(handler.replace("IAmZombieServerConfig.SPEC", "")
                .contains("IAmZombieServerConfig."));
    }

    @Test
    void delayedReloadRechecksConnectionIdentityBeforeRefresh()
            throws Exception {
        String source = Files.readString(CLIENT);
        String handler = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(
                        source,
                        "private static void onServerConfigReloading(")));
        String queue = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(
                        Files.readString(RELOAD_QUEUE),
                        "static void enqueue(")));

        int capture = handler.indexOf("Connectioncaptured=");
        int enqueue = handler.indexOf(
                "ConfigAuthorityReloadQueue.enqueue(");
        assertTrue(capture >= 0 && enqueue > capture);
        int current = queue.indexOf(
                "currentConnection.get()!=captured");
        int ready = queue.indexOf("!ready.test(captured)");
        int refresh = queue.indexOf("refresh.accept(captured)");
        assertTrue(current >= 0 && ready > current && refresh > ready,
                "the queued task must identity-check the current raw "
                        + "Connection before invoking the refresh boundary");
        assertFalse(source.contains(
                "import dev.molang.iamzombieq.config.ConfigAuthorityRuntime"));
    }

    @Test
    void refreshRuntimeKeepsHandshakeProtocolUnchanged() throws Exception {
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/config/"
                        + "ConfigAuthorityRuntime.java"));
        String refresh = SourceScan.methodBody(
                runtime,
                "static void refreshClientFromSyncedServerConfig(");

        assertTrue(refresh.contains(
                "ConfigAuthorityConnections.refreshClientIfReady("));
        assertTrue(refresh.contains(
                "ConfigAuthorityRemoteValues.captureServerConfig()"));
        assertTrue(refresh.contains("ConfigAuthorityProtocol.snapshot("));
        assertFalse(refresh.contains("context.reply"));
        assertFalse(refresh.contains("ConfigAuthorityAck"));
        assertFalse(refresh.contains("beginClient"));
        assertFalse(refresh.contains("registerPayload"));
    }
}
