package dev.molang.iamzombieq.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.molang.iamzombieq.IAmZombieClientConfig;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieRenderRules;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.BabyDrownedModel;
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
import net.minecraft.client.model.monster.zombie.DrownedModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;

public final class ZombiePlayerVisuals {
    private static MonsterModels monsterModels;
    private static boolean renderingFirstPersonArm;
    private static final Map<UUID, CachedSkin> SKIN_CACHE = new HashMap<>();

    private ZombiePlayerVisuals() {
    }

    public static void initializeMonsterBodyLayers(EntityRendererProvider.Context context) {
        monsterModels = createMonsterModels(context.getModelSet(), context.getEquipmentRenderer());
    }

    public static void applyPlayerSkin(AvatarRenderState state) {
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(state.id);
        if (!(entity instanceof Player player) || !shouldUseZombieVisuals(player)) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        ZombieForm form = data.state().form();
        boolean baby = data.state().size() == ZombieSize.BABY;
        PlayerSkin original = state.skin;
        state.skin = cachedZombieSkin(player.getUUID(), form, baby, original);
        state.isBaby = baby;
        state.showHat = false;
        state.showJacket = false;
        state.showLeftSleeve = false;
        state.showRightSleeve = false;
        state.showLeftPants = false;
        state.showRightPants = false;
    }

    public static void renderMonsterBody(RenderPlayerEvent.Pre<?> event) {
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        AvatarRenderState avatarState = event.getRenderState();
        Entity entity = minecraft.level.getEntity(avatarState.id);
        if (!(entity instanceof Player player) || !shouldUseZombieVisuals(player)) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        boolean baby = data.state().size() == ZombieSize.BABY;
        ZombieRenderState zombieState = copyToZombieState(avatarState, data.state().form(), baby);
        EntityModel<? super ZombieRenderState> model = models().modelFor(data.state().form(), baby);
        Identifier texture = textureFor(data.state().form(), baby);

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        int overlay = LivingEntityRenderer.getOverlayCoords(zombieState, 0.0F);
        poseStack.pushPose();
        applyLivingBodyTransform(zombieState, poseStack);
        collector.submitModel(
                model,
                zombieState,
                poseStack,
                RenderTypes.entityCutout(texture),
                zombieState.lightCoords,
                overlay,
                -1,
                null,
                zombieState.outlineColor,
                null
        );
        submitMonsterBodyLayers(models(), zombieState, avatarState, poseStack, collector);
        poseStack.popPose();
        event.setCanceled(true);
    }

    @SuppressWarnings("deprecation")
    public static void renderFirstPersonArm(RenderArmEvent event) {
        if (renderingFirstPersonArm) {
            return;
        }
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.FIRST_PERSON_ARM_SKIN_MODE.get())
                || !shouldUseZombieVisuals(event.getPlayer())) {
            return;
        }

        Identifier texture = textureFor(event.getPlayer().getData(IAmZombieAttachments.PLAYER_ZOMBIE));
        AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getPlayerRenderer(event.getPlayer());
        renderingFirstPersonArm = true;
        try {
            if (event.getArm() == HumanoidArm.RIGHT) {
                renderer.renderRightHand(event.getPoseStack(), event.getSubmitNodeCollector(), event.getPackedLight(), texture, false);
            } else {
                renderer.renderLeftHand(event.getPoseStack(), event.getSubmitNodeCollector(), event.getPackedLight(), texture, false);
            }
        } finally {
            renderingFirstPersonArm = false;
        }
        event.setCanceled(true);
    }

    static boolean shouldUseZombieVisuals(Player player) {
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        return ZombieRenderRules.shouldUseZombieVisuals(player.isSpectator(), player.isCreative(), data.state().form());
    }

    /**
     * Memoizes the zombie-skin {@link PlayerSkin} per player keyed by (UUID, form, baby, original cape, original
     * elytra) instead of allocating {@code new PlayerSkin(new FixedTexture(...))} every render frame. The result is
     * value-identical to the per-frame allocation: any change to form/baby (texture) or to the player's underlying
     * cape/elytra rebuilds the cached skin. Entries are dropped on player leave/clear (see {@link #invalidateSkin}
     * and {@link #clearSkins}).
     */
    private static PlayerSkin cachedZombieSkin(UUID id, ZombieForm form, boolean baby, PlayerSkin original) {
        ClientAsset.Texture cape = original.cape();
        ClientAsset.Texture elytra = original.elytra();
        CachedSkin cached = SKIN_CACHE.get(id);
        if (cached != null && cached.form == form && cached.baby == baby
                && java.util.Objects.equals(cached.cape, cape) && java.util.Objects.equals(cached.elytra, elytra)) {
            return cached.skin;
        }
        Identifier texture = textureFor(form, baby);
        PlayerSkin skin = new PlayerSkin(new FixedTexture(texture), cape, elytra, PlayerModelType.WIDE, false);
        SKIN_CACHE.put(id, new CachedSkin(form, baby, cape, elytra, skin));
        return skin;
    }

    static void invalidateSkin(UUID id) {
        SKIN_CACHE.remove(id);
    }

    static void clearSkins() {
        SKIN_CACHE.clear();
    }

    private static Identifier textureFor(PlayerZombieData data) {
        return textureFor(data.state().form(), data.state().size() == ZombieSize.BABY);
    }

    private static Identifier textureFor(ZombieForm form, boolean baby) {
        if (baby) {
            return switch (form) {
                case DROWNED -> Identifier.withDefaultNamespace("textures/entity/zombie/drowned_baby.png");
                case HUSK -> Identifier.withDefaultNamespace("textures/entity/zombie/husk_baby.png");
                case ZOMBIFIED_PIGLIN -> Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");
                default -> Identifier.withDefaultNamespace("textures/entity/zombie/zombie_baby.png");
            };
        }
        return Identifier.parse(ZombieRenderRules.monsterTexturePath(form));
    }

    private static MonsterModels models() {
        if (monsterModels == null) {
            Minecraft minecraft = Minecraft.getInstance();
            monsterModels = createMonsterModels(minecraft.getEntityModels(), null);
        }
        return monsterModels;
    }

    private static MonsterModels createMonsterModels(EntityModelSet entityModels, EquipmentLayerRenderer equipmentRenderer) {
        return new MonsterModels(
                new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE)),
                new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY)),
                new DrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED)),
                new BabyDrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED_BABY)),
                new ZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK)),
                new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK_BABY)),
                layerSet(ModelLayers.ZOMBIE_ARMOR, ModelLayers.ZOMBIE_BABY_ARMOR, entityModels, equipmentRenderer),
                layerSet(ModelLayers.DROWNED_ARMOR, ModelLayers.DROWNED_BABY_ARMOR, entityModels, equipmentRenderer),
                layerSet(ModelLayers.HUSK_ARMOR, ModelLayers.HUSK_BABY_ARMOR, entityModels, equipmentRenderer)
        );
    }

    private static MonsterLayerSet layerSet(
            ArmorModelSet<ModelLayerLocation> adultArmor,
            ArmorModelSet<ModelLayerLocation> babyArmor,
            EntityModelSet entityModels,
            EquipmentLayerRenderer equipmentRenderer
    ) {
        ZombieModel<ZombieRenderState> adultParentModel = new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE));
        ZombieModel<ZombieRenderState> babyParentModel = new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY));
        RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> adultParent = () -> adultParentModel;
        RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> babyParent = () -> babyParentModel;
        return new MonsterLayerSet(
                adultParentModel,
                babyParentModel,
                equipmentRenderer == null ? null : new HumanoidArmorLayer<>(
                                adultParent,
                                ArmorModelSet.bake(adultArmor, entityModels, HumanoidModel::new),
                                ArmorModelSet.bake(babyArmor, entityModels, HumanoidModel::new),
                                equipmentRenderer
                        ),
                new ZombiePlayerItemInHandLayer(adultParent),
                new ZombiePlayerItemInHandLayer(babyParent)
        );
    }

    private static void submitMonsterBodyLayers(
            MonsterModels models,
            ZombieRenderState zombieState,
            AvatarRenderState avatarState,
            PoseStack poseStack,
            SubmitNodeCollector collector
    ) {
        MonsterLayerSet layers = models.layersFor(zombieState.entityType, zombieState.isBaby);
        ZombieModel<ZombieRenderState> parentModel = layers.parentModel(zombieState.isBaby);
        parentModel.setupAnim(zombieState);
        if (layers.armor != null) {
            layers.armor.submit(poseStack, collector, zombieState.lightCoords, zombieState, zombieState.yRot, zombieState.xRot);
        }
        layers.handItems(zombieState.isBaby).submit(poseStack, collector, zombieState.lightCoords, zombieState, avatarState);
    }

    private static void applyLivingBodyTransform(ZombieRenderState state, PoseStack poseStack) {
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        if (!state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.bodyRot));
        }
        if (state.deathTime > 0.0F) {
            float fall = Mth.sqrt((state.deathTime - 1.0F) / 20.0F * 1.6F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Math.min(fall, 1.0F) * 90.0F));
        } else if (state.isAutoSpinAttack) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F - state.xRot));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(state.ageInTicks * -75.0F));
        } else if (state.isUpsideDown) {
            poseStack.translate(0.0F, (state.boundingBoxHeight + 0.1F) / scale, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        }

        if (state.isInWater && state.swimAmount > 0.0F) {
            float rotationX = Mth.lerp(state.swimAmount, 0.0F, -10.0F - state.xRot);
            poseStack.rotateAround(com.mojang.math.Axis.XP.rotationDegrees(rotationX), 0.0F, state.boundingBoxHeight / 2.0F / scale, 0.0F);
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
    }

    /**
     * The disguise mask now ships a worn equipment texture ({@code textures/entity/equipment/humanoid/disguise_mask.png})
     * derived from the mask face, so its equipment-asset layer renders correctly on the zombie shape's head; no
     * suppression is needed. Kept as a pass-through hook in case a future head item needs broken-render suppression.
     */
    private static ItemStack suppressBrokenWornHead(ItemStack headEquipment) {
        return headEquipment;
    }

    private static ZombieRenderState copyToZombieState(AvatarRenderState source, ZombieForm form, boolean baby) {
        ZombieRenderState target = new ZombieRenderState();
        target.entityType = switch (form) {
            case DROWNED -> EntityTypes.DROWNED;
            case HUSK -> EntityTypes.HUSK;
            default -> EntityTypes.ZOMBIE;
        };
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.ageInTicks = source.ageInTicks;
        target.boundingBoxWidth = source.boundingBoxWidth;
        target.boundingBoxHeight = source.boundingBoxHeight;
        target.eyeHeight = source.eyeHeight;
        target.distanceToCameraSq = source.distanceToCameraSq;
        target.isInvisible = source.isInvisible;
        target.isDiscrete = source.isDiscrete;
        target.displayFireAnimation = source.displayFireAnimation;
        target.lightCoords = source.lightCoords;
        target.outlineColor = source.outlineColor;
        target.passengerOffset = source.passengerOffset;
        target.nameTag = source.nameTag;
        target.scoreText = source.scoreText;
        target.nameTagAttachment = source.nameTagAttachment;
        target.leashStates = source.leashStates;
        target.shadowRadius = source.shadowRadius;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.deathTime = source.deathTime;
        target.walkAnimationPos = source.walkAnimationPos;
        target.walkAnimationSpeed = source.walkAnimationSpeed;
        target.scale = source.scale;
        target.ageScale = source.ageScale;
        target.ticksSinceKineticHitFeedback = source.ticksSinceKineticHitFeedback;
        target.isUpsideDown = source.isUpsideDown;
        target.isFullyFrozen = source.isFullyFrozen;
        target.isBaby = baby;
        target.isInWater = source.isInWater;
        target.isAutoSpinAttack = source.isAutoSpinAttack;
        target.hasRedOverlay = source.hasRedOverlay;
        target.isInvisibleToPlayer = source.isInvisibleToPlayer;
        target.bedOrientation = source.bedOrientation;
        target.pose = source.pose;
        target.swimAmount = source.swimAmount;
        target.speedValue = source.speedValue;
        target.maxCrossbowChargeDuration = source.maxCrossbowChargeDuration;
        target.ticksUsingItem = source.ticksUsingItem;
        target.useItemHand = source.useItemHand;
        target.isCrouching = source.isCrouching;
        target.isFallFlying = source.isFallFlying;
        target.isVisuallySwimming = source.isVisuallySwimming;
        target.isPassenger = source.isPassenger;
        target.isUsingItem = source.isUsingItem;
        target.elytraRotX = source.elytraRotX;
        target.elytraRotY = source.elytraRotY;
        target.elytraRotZ = source.elytraRotZ;
        target.headEquipment = suppressBrokenWornHead(source.headEquipment);
        target.chestEquipment = source.chestEquipment;
        target.legsEquipment = source.legsEquipment;
        target.feetEquipment = source.feetEquipment;
        target.mainArm = source.mainArm;
        target.attackArm = source.attackArm;
        target.rightArmPose = source.rightArmPose;
        target.rightHandItemStack = source.rightHandItemStack;
        target.leftArmPose = source.leftArmPose;
        target.leftHandItemStack = source.leftHandItemStack;
        target.swingAnimationType = source.swingAnimationType;
        target.attackTime = source.attackTime;
        target.isAggressive = true;
        return target;
    }

    private record MonsterModels(
            ZombieModel<ZombieRenderState> normal,
            BabyZombieModel<ZombieRenderState> babyNormal,
            DrownedModel drowned,
            BabyDrownedModel babyDrowned,
            ZombieModel<ZombieRenderState> husk,
            BabyZombieModel<ZombieRenderState> babyHusk,
            MonsterLayerSet normalLayers,
            MonsterLayerSet drownedLayers,
            MonsterLayerSet huskLayers
    ) {
        EntityModel<? super ZombieRenderState> modelFor(ZombieForm form, boolean baby) {
            if (baby) {
                return switch (form) {
                    case DROWNED -> babyDrowned;
                    case HUSK -> babyHusk;
                    default -> babyNormal;
                };
            }
            return switch (form) {
                case DROWNED -> drowned;
                case HUSK -> husk;
                default -> normal;
            };
        }

        MonsterLayerSet layersFor(net.minecraft.world.entity.EntityType<?> entityType, boolean baby) {
            if (entityType == EntityTypes.DROWNED) {
                return drownedLayers;
            }
            if (entityType == EntityTypes.HUSK) {
                return huskLayers;
            }
            return normalLayers;
        }
    }

    private record MonsterLayerSet(
            ZombieModel<ZombieRenderState> adultParentModel,
            ZombieModel<ZombieRenderState> babyParentModel,
            HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>> armor,
            ZombiePlayerItemInHandLayer adultHandItems,
            ZombiePlayerItemInHandLayer babyHandItems
    ) {
        ZombieModel<ZombieRenderState> parentModel(boolean baby) {
            return baby ? babyParentModel : adultParentModel;
        }

        ZombiePlayerItemInHandLayer handItems(boolean baby) {
            return baby ? babyHandItems : adultHandItems;
        }
    }

    private static final class ZombiePlayerItemInHandLayer extends ItemInHandLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {
        private ZombiePlayerItemInHandLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> renderer) {
            super(renderer);
        }

        private void submit(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                int lightCoords,
                ZombieRenderState zombieState,
                AvatarRenderState avatarState
        ) {
            submitArmWithItem(zombieState, avatarState.rightHandItemState, avatarState.rightHandItemStack, HumanoidArm.RIGHT, poseStack, collector, lightCoords);
            submitArmWithItem(zombieState, avatarState.leftHandItemState, avatarState.leftHandItemStack, HumanoidArm.LEFT, poseStack, collector, lightCoords);
        }

        @Override
        protected void submitArmWithItem(
                ZombieRenderState state,
                ItemStackRenderState item,
                ItemStack itemStack,
                HumanoidArm arm,
                PoseStack poseStack,
                SubmitNodeCollector submitNodeCollector,
                int lightCoords
        ) {
            super.submitArmWithItem(state, item, itemStack, arm, poseStack, submitNodeCollector, lightCoords);
        }
    }

    private record FixedTexture(Identifier texturePath) implements ClientAsset.Texture {
        @Override
        public Identifier id() {
            return texturePath;
        }
    }

    private record CachedSkin(
            ZombieForm form,
            boolean baby,
            ClientAsset.Texture cape,
            ClientAsset.Texture elytra,
            PlayerSkin skin
    ) {
    }
}
