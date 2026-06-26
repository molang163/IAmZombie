package dev.molang.iamzombieq;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.molang.iamzombieq.gameplay.CoffinNapManager;
import dev.molang.iamzombieq.gameplay.DifficultyGuardEvents;
import dev.molang.iamzombieq.gameplay.HerobrineEvents;
import dev.molang.iamzombieq.gameplay.ZombieFoodEvents;
import dev.molang.iamzombieq.gameplay.ZombieInfectionEvents;
import dev.molang.iamzombieq.gameplay.ZombieMobTargetingEvents;
import dev.molang.iamzombieq.gameplay.ZombieMountEvents;
import dev.molang.iamzombieq.gameplay.ZombiePlayerEvents;
import dev.molang.iamzombieq.gameplay.ZombieSleepEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(IAmZombieMod.MOD_ID)
public final class IAmZombieMod {
    public static final String MOD_ID = "iamzombieq";
    public static final String ENGLISH_NAME = "I Am Zombie?";
    public static final String CHINESE_NAME = "我是僵尸？";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IAmZombieMod(IEventBus modEventBus, ModContainer modContainer) {
        IAmZombieRegistries.register(modEventBus);
        NeoForge.EVENT_BUS.register(HerobrineEvents.class);
        NeoForge.EVENT_BUS.register(ZombieFoodEvents.class);
        NeoForge.EVENT_BUS.register(ZombieInfectionEvents.class);
        NeoForge.EVENT_BUS.register(ZombieMobTargetingEvents.class);
        NeoForge.EVENT_BUS.register(ZombieMountEvents.class);
        NeoForge.EVENT_BUS.register(ZombiePlayerEvents.class);
        NeoForge.EVENT_BUS.register(ZombieSleepEvents.class);
        NeoForge.EVENT_BUS.register(CoffinNapManager.class);
        NeoForge.EVENT_BUS.register(DifficultyGuardEvents.class);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            dev.molang.iamzombieq.client.IAmZombieClient.register(modEventBus);
        }
        modContainer.registerConfig(ModConfig.Type.COMMON, IAmZombieConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, IAmZombieClientConfig.SPEC);
    }
}
