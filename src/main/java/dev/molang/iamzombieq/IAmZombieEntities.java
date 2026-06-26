package dev.molang.iamzombieq;

import dev.molang.iamzombieq.entity.HerobrineEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IAmZombieEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(IAmZombieMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HerobrineEntity>> HEROBRINE = ENTITIES.registerEntityType(
            "herobrine",
            HerobrineEntity::new,
            MobCategory.MONSTER,
            builder -> builder.sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .noLootTable()
    );

    private IAmZombieEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(IAmZombieEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HEROBRINE.get(), HerobrineEntity.createAttributes().build());
    }
}
