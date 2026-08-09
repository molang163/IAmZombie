package dev.molang.iamzombieq;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.molang.iamzombieq.config.ConfigMigrationBootstrap;
import dev.molang.iamzombieq.gameplay.CoffinNapManager;
import dev.molang.iamzombieq.gameplay.DifficultyGuardEvents;
import dev.molang.iamzombieq.gameplay.GiantPlayerEvents;
import dev.molang.iamzombieq.gameplay.HerobrineEvents;
import dev.molang.iamzombieq.gameplay.ZombieFoodEvents;
import dev.molang.iamzombieq.gameplay.ZombieInfectionEvents;
import dev.molang.iamzombieq.gameplay.ZombieMobTargetingEvents;
import dev.molang.iamzombieq.gameplay.ZombieMountEvents;
import dev.molang.iamzombieq.gameplay.ZombiePlayerEvents;
import dev.molang.iamzombieq.gameplay.ZombieReinforcementEvents;
import dev.molang.iamzombieq.gameplay.ZombieSleepEvents;
import dev.molang.iamzombieq.gameplay.ZombieSunlightEvents;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(IAmZombieMod.MOD_ID)
public final class IAmZombieMod {
    public static final String MOD_ID = "iamzombieq";
    public static final String ENGLISH_NAME = "I Am Zombie?";
    public static final String CHINESE_NAME = "我是僵尸？";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String AUTHORITY_RUNTIME =
            "dev.molang.iamzombieq.config.ConfigAuthorityRuntime";
    private static final MethodHandle REGISTER_AUTHORITY_PAYLOADS =
            iamzombieq$authorityHandle(
                    "registerPayloads",
                    RegisterPayloadHandlersEvent.class);

    public IAmZombieMod(IEventBus modEventBus, ModContainer modContainer) {
        ConfigMigrationBootstrap.migratePhysicalClientPreferences();
        IAmZombieRegistries.register(modEventBus);
        modEventBus.addListener(
                IAmZombieMod::iamzombieq$registerAuthorityPayloads);
        NeoForge.EVENT_BUS.register(HerobrineEvents.class);
        NeoForge.EVENT_BUS.register(ZombieFoodEvents.class);
        NeoForge.EVENT_BUS.register(ZombieInfectionEvents.class);
        NeoForge.EVENT_BUS.register(ZombieMobTargetingEvents.class);
        NeoForge.EVENT_BUS.register(ZombieMountEvents.class);
        NeoForge.EVENT_BUS.register(ZombiePlayerEvents.class);
        NeoForge.EVENT_BUS.register(ZombieSunlightEvents.class);
        NeoForge.EVENT_BUS.register(ZombieReinforcementEvents.class);
        NeoForge.EVENT_BUS.register(GiantPlayerEvents.class);
        NeoForge.EVENT_BUS.register(ZombieSleepEvents.class);
        NeoForge.EVENT_BUS.register(CoffinNapManager.class);
        NeoForge.EVENT_BUS.register(DifficultyGuardEvents.class);
        //? if >=1.21.10 {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
        //?} else {
        /*if (FMLEnvironment.dist == Dist.CLIENT) {
        *///?}
            dev.molang.iamzombieq.client.IAmZombieClient.register(modEventBus);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, IAmZombieServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, IAmZombieClientConfig.SPEC);
        modContainer.registerConfig(
                ModConfig.Type.CLIENT,
                IAmZombiePreferencesConfig.SPEC,
                "iamzombieq-preferences-client.toml");
    }

    private static void iamzombieq$registerAuthorityPayloads(
            RegisterPayloadHandlersEvent event) {
        try {
            REGISTER_AUTHORITY_PAYLOADS.invokeExact(event);
        } catch (Throwable failure) {
            throw iamzombieq$failClosed(failure);
        }
    }

    private static MethodHandle iamzombieq$authorityHandle(
            String methodName, Class<?> parameterType) {
        try {
            Class<?> runtime = Class.forName(
                    AUTHORITY_RUNTIME,
                    true,
                    IAmZombieMod.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    runtime, MethodHandles.lookup());
            return lookup.findStatic(
                    runtime,
                    methodName,
                    MethodType.methodType(void.class, parameterType));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static RuntimeException iamzombieq$failClosed(
            Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(
                "Configuration authority runtime invocation failed",
                failure);
    }
}
