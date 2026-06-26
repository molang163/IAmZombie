package dev.molang.iamzombieq.entity;

import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HerobrineEntity extends Monster {
    private static final int MAX_LIFETIME_TICKS = 20 * 45;

    // Synced encounter phase ordinal (HerobrineEncounter.Phase). Set server-side from the
    // observing player's per-player encounter state so the client can drive the phase-gated
    // heartbeat (HB-AUDIO-HEARTBEAT) without any new packets. Defaults to OBSERVATION's ordinal.
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(HerobrineEntity.class, EntityDataSerializers.INT);

    public HerobrineEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setGlowingTag(true);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, HerobrineEncounter.Phase.OBSERVATION.ordinal());
    }

    /** Server-side: publish the observing player's current encounter phase for client heartbeat gating. */
    public void setEncounterPhase(HerobrineEncounter.Phase phase) {
        this.getEntityData().set(DATA_PHASE, phase.ordinal());
    }

    /** Client- or server-side: the encounter phase last published for this Herobrine. */
    public HerobrineEncounter.Phase getEncounterPhase() {
        int ordinal = this.getEntityData().get(DATA_PHASE);
        HerobrineEncounter.Phase[] values = HerobrineEncounter.Phase.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return HerobrineEncounter.Phase.OBSERVATION;
        }
        return values[ordinal];
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, HerobrineRules.GAZE_DISTANCE);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        this.setNoGravity(true);
        this.setGlowingTag(true);
        if (!this.level().isClientSide() && this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        return InteractionResult.PASS;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }
}
