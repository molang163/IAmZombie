package dev.molang.iamzombieq.internal.mount;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;

/** Internal typed bridge implemented on the transformed {@code ServerPlayer}. */
@ApiStatus.Internal
public interface SpiderPassengerRestorationAccess {
    void iamzombieq$beginSpiderPassengerRestoration(Entity entityToRide);

    void iamzombieq$endSpiderPassengerRestoration(Entity entityToRide);
}
