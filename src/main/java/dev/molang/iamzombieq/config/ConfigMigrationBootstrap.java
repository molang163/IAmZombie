package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Objects;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal lifecycle bridge for the configuration migration core.
 *
 * <p>This is public only because production lifecycle hooks live in another
 * package. It is not a supported addon API.</p>
 */
@ApiStatus.Internal
public final class ConfigMigrationBootstrap {
    private ConfigMigrationBootstrap() {
    }

    public static void migrateServer(MinecraftServer server) {
        if (DatagenModLoader.isRunningDataGen()) {
            return;
        }
        MinecraftServer checkedServer =
                Objects.requireNonNull(server, "server");
        Path global = normalized(FMLPaths.CONFIGDIR.get());
        Path world = normalized(checkedServer.getWorldPath(
                new LevelResource("serverconfig")));
        ProductionConfigMigration.migrateServer(global, world);
    }

    public static void migratePhysicalClientPreferences() {
        if (DatagenModLoader.isRunningDataGen()) {
            return;
        }
        if (FMLEnvironment.getDist() == Dist.DEDICATED_SERVER) {
            return;
        }
        ProductionConfigMigration.migratePreferences(
                normalized(FMLPaths.CONFIGDIR.get()));
    }

    private static Path normalized(Path path) {
        return Objects.requireNonNull(path, "path")
                .toAbsolutePath()
                .normalize();
    }
}
