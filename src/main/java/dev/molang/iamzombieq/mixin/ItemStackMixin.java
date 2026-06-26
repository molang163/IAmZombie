package dev.molang.iamzombieq.mixin;

import java.util.function.Consumer;

import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @ModifyVariable(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int iamzombieq$reduceZombifiedPiglinGoldDurability(
            int amount,
            int originalAmount,
            ServerLevel level,
            @Nullable LivingEntity owner,
            Consumer<Item> onBreak
    ) {
        if (amount <= 0 || !(owner instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return amount;
        }

        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.isDamageableItem() || !stack.is(ItemTags.PIGLIN_LOVED)) {
            return amount;
        }
        if (player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form() != ZombieForm.ZOMBIFIED_PIGLIN) {
            return amount;
        }

        double scaledAmount = amount * ZombieBalanceRules.goldDurabilityConsumptionMultiplier(ZombieForm.ZOMBIFIED_PIGLIN);
        int reducedAmount = (int) scaledAmount;
        if (level.getRandom().nextDouble() < scaledAmount - reducedAmount) {
            reducedAmount++;
        }
        return Math.max(0, Math.min(amount, reducedAmount));
    }
}
