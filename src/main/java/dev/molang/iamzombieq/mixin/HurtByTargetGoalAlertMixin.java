package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.gameplay.ZombieMobTargetingEvents;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix A (most vanilla) for the zombified-piglin (and any NeutralMob pack) group-anger swarm.
 *
 * <p>When the zombie player hits one piglin, vanilla's group anger should make the nearby pack swarm. Vanilla
 * propagates that via {@link HurtByTargetGoal#alertOthers()} -> {@code alertOther(kin, attacker)}, which does ONLY
 * {@code kin.setTarget(attacker)} -- it does NOT set persistent anger. So at the instant the alerted kin's target is
 * set to the zombie player, the mod's deny-list ({@link ZombieMobTargetingEvents#onChangeTarget}) sees
 * {@code isAngryAt(player)==false}, classifies the piglin as IGNORED, and nulls the target -- before vanilla's
 * {@code updatePersistentAnger} can promote it to anger. That nulling prevents the promotion (the target is gone),
 * so anger is never set and every later re-alert is re-nulled: the pack never swarms (only the directly-hit piglin,
 * which qualifies via {@code lastHurtByMob==player}, fights).
 *
 * <p>This mixin establishes the alerted kin's persistent anger toward the attacker BEFORE vanilla's
 * {@code setTarget} fires the {@code LivingChangeTargetEvent}, so the deny-list's {@code angeredNeutral} branch
 * allows the target and vanilla's own anger-gated {@code NearestAttackableTargetGoal} sustains the swarm for the
 * 20-39s anger window. It is the exact mirror of the mod's own player-as-victim workaround in
 * {@code ZombiePlayerEvents#alertFormMatchedUndead} ("establish anger BEFORE setTarget"). Gated by
 * {@link ZombieMobTargetingEvents#wouldDenyZombiePlayerTarget} so it fires ONLY for a {@link NeutralMob} the
 * deny-list would otherwise strand (a piglin, a wolf pack, ...), never for a table attacker (iron golem) and never
 * for an unprovoked piglin -- {@code alertOther} only runs after a mob's {@code HurtByTargetGoal} started, i.e. after
 * genuine provocation, so the "undead ignore the unprovoked zombie player" premise is preserved.
 */
@Mixin(HurtByTargetGoal.class)
abstract class HurtByTargetGoalAlertMixin {

    @Inject(method = "alertOther", at = @At("HEAD"))
    private void iamzombieq$preAngerAlertedNeutral(Mob other, LivingEntity target, CallbackInfo ci) {
        if (other instanceof NeutralMob neutral
                && ZombieMobTargetingEvents.wouldDenyZombiePlayerTarget(other, target)) {
            // Mirror the mod's alertFormMatchedUndead pattern: anger BEFORE the (vanilla) setTarget that follows.
            neutral.setPersistentAngerTarget(EntityReference.of(target));
            neutral.startPersistentAngerTimer();
        }
    }
}
