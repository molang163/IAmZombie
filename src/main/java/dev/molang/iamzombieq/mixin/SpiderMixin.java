package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.util.MountCapability;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B2/B6 -- spider climbing while ridden.
 *
 * <p>Vanilla {@code Spider#onClimbable()} returns the synced {@code isClimbing()} flag, and
 * {@code Spider#tick()} only refreshes that flag SERVER-side ({@code setClimbing(horizontalCollision)} inside
 * an {@code if (!isClientSide)} guard). When a player rides an owner-tamed spider, the vanilla riding flow
 * runs {@code LivingEntity#travel} on the CONTROLLING CLIENT (via the MobMixin controlling-passenger hook),
 * and that client never refreshes the climbing flag -- so the spider would not climb while being driven, and
 * the synced flag could be stale (B6 "never resets").
 *
 * <p>Fix: for a ridden, owner-controlled spider, derive climbability directly from the LOCAL
 * {@code horizontalCollision} (the value the running {@code travel} uses), so vanilla's
 * {@code handleRelativeFrictionAndCalculateMovement} applies the upward climb motion when pressed against a
 * wall, and naturally returns false (resets) the moment it is no longer colliding. When the spider is NOT
 * ridden by its owner, the inject does nothing and vanilla {@code isClimbing()} behavior (server-set each
 * tick) is unchanged.
 *
 * <p>Target verified against the decompiled 26.2 jar: {@code Spider#onClimbable()} (Spider.java line ~114,
 * {@code public boolean onClimbable()}), and the public {@code Entity#horizontalCollision} field
 * (Entity.java line ~229).
 */
@Mixin(Spider.class)
abstract class SpiderMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$ownerRiddenSpiderClimbsWalls(CallbackInfoReturnable<Boolean> callback) {
        Spider self = (Spider) (Object) this;
        // activeFor matches only when this spider is being validly ridden by its owner (the SPIDER capability);
        // shared with the controlling-passenger + despawn-backstop hooks so the gate cannot drift.
        if (MountCapability.activeFor(self).isPresent()) {
            // True only while actually pressed against a wall; false otherwise (resets immediately on the
            // controlling client, fixing both "won't climb while ridden" (B2) and "stuck climbing" (B6)).
            callback.setReturnValue(self.horizontalCollision);
        }
    }
}
