package dev.molang.iamzombieq;

import dev.molang.iamzombieq.state.IAmZombieAttachments;
import net.neoforged.bus.api.IEventBus;

public final class IAmZombieRegistries {
    private IAmZombieRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        IAmZombieAttachments.register(modEventBus);
        IAmZombieBlocks.register(modEventBus);
        IAmZombieEntities.register(modEventBus);
        IAmZombieItems.register(modEventBus);
    }
}
