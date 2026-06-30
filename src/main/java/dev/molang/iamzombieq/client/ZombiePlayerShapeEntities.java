package dev.molang.iamzombieq.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class ZombiePlayerShapeEntities {
    private final Map<UUID, CachedShape> shapes = new HashMap<>();

    public LivingEntity shapeFor(AbstractClientPlayer player) {
        return cachedShapeFor(player).entity;
    }

    /**
     * Returns a per-player, per-frame-refreshed {@link ZombiePlayerRenderReplacement} that reuses a cached shape
     * {@link EntityRenderState} instead of allocating a fresh state + wrapper every frame. The cached state is
     * fully repopulated in place by re-running the renderer's {@code extractRenderState} (the same extraction
     * that the {@code createRenderState(entity, partialTick)} pipeline runs), so every per-frame field
     * (position/light/pose/scale/shadowRadius/equipment/...) is refreshed exactly as before. The only field the
     * fresh-allocation path additionally produced via {@code finalizeRenderState} is {@code shadowPieces}, which
     * the shape renderer's {@code submit} never reads (the real player's own shadow is rendered from the avatar
     * state, untouched here) — so behavior is preserved.
     *
     * <p>The cache is invalidated together with the shape entity: on form change, level change (handled by
     * {@link #cachedShapeFor}), and on player leave/clear. It is also rebuilt if the resolved shape renderer
     * identity changes (e.g. resource reload).
     */
    public ZombiePlayerRenderReplacement replacementFor(
            AbstractClientPlayer player,
            EntityRenderer<LivingEntity, EntityRenderState> renderer
    ) {
        // Reuse the entry just resolved/synced by shapeFor() in the same frame (no extra syncShape).
        CachedShape cached = shapes.get(player.getUUID());
        if (cached == null) {
            cached = cachedShapeFor(player);
        }
        // No shape entity (createShape returned null) -> no replacement; the caller renders the vanilla avatar.
        if (cached.entity == null) {
            return null;
        }
        EntityRenderState shapeState = cached.shapeState;
        if (shapeState == null || cached.renderer != renderer || cached.replacement == null) {
            shapeState = renderer.createRenderState();
            cached.renderer = renderer;
            cached.shapeState = shapeState;
            cached.replacement = new ZombiePlayerRenderReplacement(renderer, shapeState);
        }
        // Repopulate ALL per-frame fields on the reused state (matches the vanilla extract step exactly).
        renderer.extractRenderState(cached.entity, shapeState, 1.0F);
        return cached.replacement;
    }

    private CachedShape cachedShapeFor(AbstractClientPlayer player) {
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        ZombieForm form = data.state().form();
        CachedShape cached = shapes.get(player.getUUID());
        // `cached.entity == null` MUST precede `.level()` so || short-circuits: createShape (EntityType.create) is
        // @Nullable, so the cached entity can be null and we must not dereference it.
        if (cached == null || cached.form != form || cached.entity == null || cached.entity.level() != player.level()) {
            LivingEntity entity = createShape(player, form);
            cached = new CachedShape(form, entity);
            shapes.put(player.getUUID(), cached);
        }
        // When no shape entity could be created, skip the per-frame sync; the render path falls back to the vanilla
        // avatar (see IAmZombieClient + replacementFor). Never NPE on a null shape (the reported crash).
        if (cached.entity != null) {
            syncShape(player, cached.entity, data.state().size() == ZombieSize.BABY);
        }
        return cached;
    }

    public void remove(AbstractClientPlayer player) {
        shapes.remove(player.getUUID());
    }

    public void clear() {
        shapes.clear();
    }

    private static LivingEntity createShape(AbstractClientPlayer player, ZombieForm form) {
        return switch (form) {
            case DROWNED -> EntityTypes.DROWNED.create(player.level(), EntitySpawnReason.LOAD);
            case HUSK -> EntityTypes.HUSK.create(player.level(), EntitySpawnReason.LOAD);
            case ZOMBIFIED_PIGLIN -> EntityTypes.ZOMBIFIED_PIGLIN.create(player.level(), EntitySpawnReason.LOAD);
            default -> EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.LOAD);
        };
    }

    private static void syncShape(AbstractClientPlayer player, LivingEntity shape, boolean baby) {
        shape.setId(player.getId());
        shape.setPos(player.getX(), player.getY(), player.getZ());
        shape.xOld = player.xOld;
        shape.yOld = player.yOld;
        shape.zOld = player.zOld;
        shape.setYRot(player.getYRot());
        shape.yRotO = player.yRotO;
        shape.setXRot(player.getXRot());
        shape.xRotO = player.xRotO;
        shape.yBodyRot = player.yBodyRot;
        shape.yBodyRotO = player.yBodyRotO;
        shape.yHeadRot = player.yHeadRot;
        shape.yHeadRotO = player.yHeadRotO;
        shape.tickCount = player.tickCount;
        shape.setPose(player.getPose());
        // RC2: mirror the player's sleeping block position onto the cached shape so vanilla
        // LivingEntityRenderer.extractRenderState derives a non-null bedOrientation (the coffin FACING) + the
        // STANDING eyeHeight, which on the LIVE shape-submit render path (the real player is never submitted) drive
        // BOTH the bed-centering translate and the flat sleeping yaw. The shape is cached and reused across frames,
        // so CLEAR the pos when the player is awake -- otherwise the body would stay flat-on-its-back after standing.
        if (player.getSleepingPos().isPresent()) {
            shape.setSleepingPos(player.getSleepingPos().get());
        } else {
            shape.clearSleepingPos();
        }
        shape.setOnGround(player.onGround());
        shape.setDeltaMovement(player.getDeltaMovement());
        shape.setInvisible(player.isInvisible());
        shape.noPhysics = true;

        if (shape instanceof Zombie zombie) {
            zombie.setBaby(baby);
        }
        if (shape instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setAggressive(player.swinging || player.isUsingItem());
        }

        shape.setItemSlot(EquipmentSlot.MAINHAND, player.getItemBySlot(EquipmentSlot.MAINHAND));
        shape.setItemSlot(EquipmentSlot.OFFHAND, player.getItemBySlot(EquipmentSlot.OFFHAND));
        shape.setItemSlot(EquipmentSlot.HEAD, player.getItemBySlot(EquipmentSlot.HEAD));
        shape.setItemSlot(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST));
        shape.setItemSlot(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS));
        shape.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static final class CachedShape {
        private final ZombieForm form;
        private final LivingEntity entity;
        private EntityRenderer<LivingEntity, EntityRenderState> renderer;
        private EntityRenderState shapeState;
        private ZombiePlayerRenderReplacement replacement;

        private CachedShape(ZombieForm form, LivingEntity entity) {
            this.form = form;
            this.entity = entity;
        }
    }
}
