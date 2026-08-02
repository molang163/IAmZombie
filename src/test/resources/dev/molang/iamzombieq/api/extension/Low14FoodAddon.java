package external.addon.low14;

import dev.molang.iamzombieq.api.extension.IFoodRuleProvider;
import dev.molang.iamzombieq.api.extension.IZombieExtensions;

public final class Low14FoodAddon {
    private Low14FoodAddon() {
    }

    public static void registerFoodProvider() {
        IFoodRuleProvider provider = (eater, stack, itemId) -> null;
        IZombieExtensions.register(provider);
    }
}
