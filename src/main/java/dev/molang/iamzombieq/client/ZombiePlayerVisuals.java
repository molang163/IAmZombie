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
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
//? if >=26.1
import net.minecraft.client.model.monster.piglin.AdultZombifiedPiglinModel;
//? if >=26.1
import net.minecraft.client.model.monster.piglin.BabyZombifiedPiglinModel;
//? if >=26.1
import net.minecraft.client.model.monster.zombie.BabyDrownedModel;
//? if >=26.1
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.piglin.ZombifiedPiglinModel;
//? if <1.21.11
//import net.minecraft.client.model.ZombifiedPiglinModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.zombie.DrownedModel;
//? if <1.21.11
//import net.minecraft.client.model.DrownedModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.zombie.ZombieModel;
//? if <1.21.11
//import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
//? if >=1.21.10
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if <1.21.10
//import net.minecraft.client.renderer.MultiBufferSource;
// CROSS_VERSION-ARMOR-MODEL-SET-API:import
//? if >=1.21.10
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
//? if >=1.21.10
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//? if <1.21.10
//import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
// CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-import
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.21.10
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.10 {
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ZombiePlayerVisuals {
    private static final float POSITIVE_Y_NORMAL_THRESHOLD = 0.999F;
    private static MonsterModels monsterModels;
    private static final Map<UUID, CachedSkin> SKIN_CACHE = new HashMap<>();

    private ZombiePlayerVisuals() {
    }

    public static void initializeMonsterBodyLayers(EntityRendererProvider.Context context) {
        monsterModels = createMonsterModels(context.getModelSet(), context.getEquipmentRenderer());
    }

    //? if >=1.21.10
    public static void applyPlayerSkin(AvatarRenderState state) {
    //? if <1.21.10
    //public static void applyPlayerSkin(PlayerRenderState state) {
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

    //? if >=1.21.10
    public static void renderMonsterBody(RenderPlayerEvent.Pre<?> event) {
    //? if <1.21.10
    //public static void renderMonsterBody(RenderPlayerEvent.Pre event) {
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        //? if >=1.21.10
        AvatarRenderState avatarState = event.getRenderState();
        //? if <1.21.10
        //PlayerRenderState avatarState = event.getRenderState();
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
        int overlay = LivingEntityRenderer.getOverlayCoords(zombieState, 0.0F);
        poseStack.pushPose();
        applyLivingBodyTransform(zombieState, poseStack);
        //? if >=1.21.10 {
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        collector.submitModel(
                model,
                zombieState,
                poseStack,
                // CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-cutout
                //? if >=1.21.11
                RenderTypes.entityCutout(texture),
                //? if <1.21.11
                //RenderType.entityCutout(texture),
                zombieState.lightCoords,
                overlay,
                -1,
                null,
                zombieState.outlineColor,
                null
        );
        submitMonsterBodyLayers(models(), zombieState, avatarState, poseStack, collector);
        //?} else {
        /*int packedLight = event.getPackedLight();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        model.setupAnim(zombieState);
        model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityCutout(texture)),
                packedLight,
                overlay,
                -1);
        renderMonsterBodyLayers(
                models(), zombieState, avatarState, poseStack, bufferSource, packedLight);
        *///?}
        poseStack.popPose();
        event.setCanceled(true);
    }

    //? if >=26.2
    public static void renderFirstPersonArm(RenderArmEvent<?> event) {
    //? if <26.2
    //public static void renderFirstPersonArm(RenderArmEvent event) {
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.FIRST_PERSON_ARM_SKIN_MODE.get())) {
            return;
        }
        //? if >=26.2 {
        if (!(event.getAvatar() instanceof AbstractClientPlayer player)
                || !shouldUseZombieVisuals(player)) {
            return;
        }
        //?} else {
        /*AbstractClientPlayer player = event.getPlayer();
        if (!shouldUseZombieVisuals(player)) {
            return;
        }
        *///?}

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        ZombieForm form = data.state().form();
        boolean baby = data.state().size() == ZombieSize.BABY;
        HumanoidArm armSide = event.getArm();
        HumanoidModel<?> monsterModel = models().firstPersonModelFor(form, baby);
        ModelPart arm = humanoidArm(monsterModel, armSide);
        Identifier texture = firstPersonTextureFor(form, baby);

        arm.resetPose();
        arm.visible = true;
        arm.skipDraw = false;
        arm.zRot = armSide == HumanoidArm.RIGHT ? 0.1F : -0.1F;
        //? if >=26.2 {
        ModelPart playerArm = event.getArmPart();
        //?}
        //? if >=1.21.10 && <26.2 {
        /*ModelPart playerArm = humanoidArm(Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getPlayerRenderer(player)
                .getModel(),
                armSide);
        *///?}
        //? if <1.21.10 {
        /*net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer =
                (net.minecraft.client.renderer.entity.player.PlayerRenderer) Minecraft.getInstance()
                        .getEntityRenderDispatcher()
                        .getRenderer(player);
        ModelPart playerArm = humanoidArm(playerRenderer.getModel(), armSide);
        *///?}
        Vector3f offset = firstPersonArmOffset(playerArm, monsterModel.root(), arm, armSide);
        PoseStack poseStack = event.getPoseStack();
        //? if >=26.2
        int packedLight = event.getLightCoords();
        //? if <26.2
        //int packedLight = event.getPackedLight();
        poseStack.pushPose();
        try {
            poseStack.translate(offset.x(), offset.y(), offset.z());
            applyPartPose(poseStack, monsterModel.root().getInitialPose());
            //? if >=1.21.10 {
            event.getSubmitNodeCollector().submitModelPart(
                    arm,
                    poseStack,
                    // CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-translucent
                    //? if >=1.21.11
                    RenderTypes.entityTranslucent(texture),
                    //? if <1.21.11
                    //RenderType.entityTranslucent(texture),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    null
            );
            //?} else {
            /*arm.render(
                    poseStack,
                    event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture)),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    -1);
            *///?}
        } finally {
            poseStack.popPose();
        }
        event.setCanceled(true);
    }

    static Vector3f firstPersonArmOffset(
            ModelPart playerArm,
            ModelPart monsterRoot,
            ModelPart monsterArm,
            HumanoidArm armSide
    ) {
        float roll = armSide == HumanoidArm.RIGHT ? 0.1F : -0.1F;
        Vector3f playerTip = firstPersonArmTip(playerArm, PartPose.ZERO, roll);
        Vector3f monsterTip = firstPersonArmTip(monsterArm, monsterRoot.getInitialPose(), roll);
        return playerTip.sub(monsterTip);
    }

    static Vector3f firstPersonArmTip(ModelPart arm, PartPose rootPose, float roll) {
        PoseStack poseStack = new PoseStack();
        applyPartPose(poseStack, rootPose);
        PartPose initial = arm.getInitialPose();
        applyPartPose(poseStack, new PartPose(
                initial.x(), initial.y(), initial.z(),
                initial.xRot(), initial.yRot(), roll,
                initial.xScale(), initial.yScale(), initial.zScale()
        ));
        return poseStack.last().pose().transformPosition(positiveYTipCenter(arm), new Vector3f());
    }

    static void applyPartPose(PoseStack poseStack, PartPose pose) {
        poseStack.translate(pose.x() / 16.0F, pose.y() / 16.0F, pose.z() / 16.0F);
        if (pose.xRot() != 0.0F || pose.yRot() != 0.0F || pose.zRot() != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZYX(pose.zRot(), pose.yRot(), pose.xRot()));
        }
        if (pose.xScale() != 1.0F || pose.yScale() != 1.0F || pose.zScale() != 1.0F) {
            poseStack.scale(pose.xScale(), pose.yScale(), pose.zScale());
        }
    }

    private static Vector3f positiveYTipCenter(ModelPart arm) {
        Vector3f[] farthest = {null};
        arm.visit(new PoseStack(), (ignoredPose, path, ignoredCubeIndex, cube) -> {
            if (!path.isEmpty()) {
                return;
            }
            for (ModelPart.Polygon polygon : cube.polygons) {
                if (polygon.normal().y() <= POSITIVE_Y_NORMAL_THRESHOLD) {
                    continue;
                }
                Vector3f center = new Vector3f();
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    //? if >=1.21.10 {
                    center.add(vertex.worldX(), vertex.worldY(), vertex.worldZ());
                    //?} else {
                    /*center.add(vertex.pos().x() / 16.0F, vertex.pos().y() / 16.0F, vertex.pos().z() / 16.0F);
                    *///?}
                }
                center.div(polygon.vertices().length);
                if (farthest[0] == null || center.y() > farthest[0].y()) {
                    farthest[0] = center;
                }
            }
        });
        if (farthest[0] == null) {
            throw new IllegalStateException("First-person arm has no own-cube +Y face");
        }
        return farthest[0];
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
        //? if >=1.21.10 {
        ClientAsset.Texture cape = original.cape();
        ClientAsset.Texture elytra = original.elytra();
        //?} else {
        /*Identifier cape = original.capeTexture();
        Identifier elytra = original.elytraTexture();
        *///?}
        CachedSkin cached = SKIN_CACHE.get(id);
        if (cached != null && cached.form == form && cached.baby == baby
                && java.util.Objects.equals(cached.cape, cape) && java.util.Objects.equals(cached.elytra, elytra)) {
            return cached.skin;
        }
        Identifier texture = textureFor(form, baby);
        //? if >=1.21.10 {
        PlayerSkin skin = new PlayerSkin(new FixedTexture(texture), cape, elytra, PlayerModelType.WIDE, false);
        //?} else {
        /*PlayerSkin skin = new PlayerSkin(texture, null, cape, elytra, PlayerSkin.Model.WIDE, false);
        *///?}
        SKIN_CACHE.put(id, new CachedSkin(form, baby, cape, elytra, skin));
        return skin;
    }

    static void invalidateSkin(UUID id) {
        SKIN_CACHE.remove(id);
    }

    static void clearSkins() {
        SKIN_CACHE.clear();
    }

    private static Identifier textureFor(ZombieForm form, boolean baby) {
        // CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-form
        //? if >=26.1 {
        if (baby) {
            return switch (form) {
                case DROWNED -> Identifier.withDefaultNamespace("textures/entity/zombie/drowned_baby.png");
                case HUSK -> Identifier.withDefaultNamespace("textures/entity/zombie/husk_baby.png");
                case ZOMBIFIED_PIGLIN -> Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");
                default -> Identifier.withDefaultNamespace("textures/entity/zombie/zombie_baby.png");
            };
        }
        //?}
        return Identifier.parse(ZombieRenderRules.monsterTexturePath(form));
    }

    private static Identifier firstPersonTextureFor(ZombieForm form, boolean baby) {
        // CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-first-person-piglin
        //? if >=26.1 {
        if (baby && form == ZombieForm.ZOMBIFIED_PIGLIN) {
            return Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin_baby.png");
        }
        //?}
        return textureFor(form, baby);
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
                //? if >=26.1
                new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY)),
                //? if <26.1
                //new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY)),
                new DrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED)),
                //? if >=26.1
                new BabyDrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED_BABY)),
                //? if <26.1
                //new DrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED_BABY)),
                new ZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK)),
                //? if >=26.1
                new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK_BABY)),
                //? if <26.1
                //new ZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK_BABY)),
                //? if >=26.1
                new AdultZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN)),
                //? if <26.1
                //new ZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN)),
                //? if >=26.1
                new BabyZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN_BABY)),
                //? if <26.1
                //new ZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN_BABY)),
                // CROSS_VERSION-ARMOR-MODEL-SET-API:factory
                //? if >=1.21.10 {
                layerSet(ModelLayers.ZOMBIE_ARMOR, ModelLayers.ZOMBIE_BABY_ARMOR, entityModels, equipmentRenderer),
                layerSet(ModelLayers.DROWNED_ARMOR, ModelLayers.DROWNED_BABY_ARMOR, entityModels, equipmentRenderer),
                layerSet(ModelLayers.HUSK_ARMOR, ModelLayers.HUSK_BABY_ARMOR, entityModels, equipmentRenderer)
                //?} else {
                /*layerSet(
                        ModelLayers.ZOMBIE_INNER_ARMOR,
                        ModelLayers.ZOMBIE_OUTER_ARMOR,
                        ModelLayers.ZOMBIE_BABY_INNER_ARMOR,
                        ModelLayers.ZOMBIE_BABY_OUTER_ARMOR,
                        entityModels,
                        equipmentRenderer),
                layerSet(
                        ModelLayers.DROWNED_INNER_ARMOR,
                        ModelLayers.DROWNED_OUTER_ARMOR,
                        ModelLayers.DROWNED_BABY_INNER_ARMOR,
                        ModelLayers.DROWNED_BABY_OUTER_ARMOR,
                        entityModels,
                        equipmentRenderer),
                layerSet(
                        ModelLayers.HUSK_INNER_ARMOR,
                        ModelLayers.HUSK_OUTER_ARMOR,
                        ModelLayers.HUSK_BABY_INNER_ARMOR,
                        ModelLayers.HUSK_BABY_OUTER_ARMOR,
                        entityModels,
                        equipmentRenderer)
                *///?}
        );
    }

    // CROSS_VERSION-ARMOR-MODEL-SET-API:helper
    private static MonsterLayerSet layerSet(
            //? if >=1.21.10 {
            ArmorModelSet<ModelLayerLocation> adultArmor,
            ArmorModelSet<ModelLayerLocation> babyArmor,
            //?} else {
            /*ModelLayerLocation adultInnerArmor,
            ModelLayerLocation adultOuterArmor,
            ModelLayerLocation babyInnerArmor,
            ModelLayerLocation babyOuterArmor,
            *///?}
            EntityModelSet entityModels,
            EquipmentLayerRenderer equipmentRenderer
    ) {
        ZombieModel<ZombieRenderState> adultParentModel = new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE));
        //? if >=26.1
        ZombieModel<ZombieRenderState> babyParentModel = new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY));
        //? if <26.1
        //ZombieModel<ZombieRenderState> babyParentModel = new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY));
        RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> adultParent = () -> adultParentModel;
        RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> babyParent = () -> babyParentModel;
        //? if >=1.21.10 {
        HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>> armor =
                equipmentRenderer == null ? null : new HumanoidArmorLayer<>(
                        adultParent,
                        ArmorModelSet.bake(adultArmor, entityModels, HumanoidModel::new),
                        ArmorModelSet.bake(babyArmor, entityModels, HumanoidModel::new),
                        equipmentRenderer
                );
        return new MonsterLayerSet(
                adultParentModel,
                babyParentModel,
                armor,
                armor,
                new ZombiePlayerItemInHandLayer(adultParent),
                new ZombiePlayerItemInHandLayer(babyParent)
        );
        //?} else {
        /*HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>>
                adultArmorLayer = equipmentRenderer == null ? null : new HumanoidArmorLayer<>(
                        adultParent,
                        new HumanoidModel<>(entityModels.bakeLayer(adultInnerArmor)),
                        new HumanoidModel<>(entityModels.bakeLayer(adultOuterArmor)),
                        equipmentRenderer
                );
        HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>>
                babyArmorLayer = equipmentRenderer == null ? null : new HumanoidArmorLayer<>(
                        babyParent,
                        new HumanoidModel<>(entityModels.bakeLayer(babyInnerArmor)),
                        new HumanoidModel<>(entityModels.bakeLayer(babyOuterArmor)),
                        equipmentRenderer
                );
        return new MonsterLayerSet(
                adultParentModel,
                babyParentModel,
                adultArmorLayer,
                babyArmorLayer,
                new ZombiePlayerItemInHandLayer(adultParent),
                new ZombiePlayerItemInHandLayer(babyParent)
        );
        *///?}
    }

    // CROSS_VERSION-ARMOR-MODEL-SET-API:age-selection
    //? if >=1.21.10 {
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
        HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>>
                armor = layers.armor(zombieState.isBaby);
        if (armor != null) {
            armor.submit(poseStack, collector, zombieState.lightCoords, zombieState, zombieState.yRot, zombieState.xRot);
        }
        layers.handItems(zombieState.isBaby).submit(poseStack, collector, zombieState.lightCoords, zombieState, avatarState);
    }
    //?} else {
    /*private static void renderMonsterBodyLayers(
            MonsterModels models,
            ZombieRenderState zombieState,
            PlayerRenderState avatarState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        MonsterLayerSet layers = models.layersFor(zombieState.entityType, zombieState.isBaby);
        ZombieModel<ZombieRenderState> parentModel = layers.parentModel(zombieState.isBaby);
        parentModel.setupAnim(zombieState);
        HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>, HumanoidModel<ZombieRenderState>>
                armor = layers.armor(zombieState.isBaby);
        if (armor != null) {
            armor.render(
                    poseStack, bufferSource, packedLight, zombieState, zombieState.yRot, zombieState.xRot);
        }
        layers.handItems(zombieState.isBaby).render(
                poseStack, bufferSource, packedLight, zombieState, avatarState);
    }
    *///?}

    private static void applyLivingBodyTransform(ZombieRenderState state, PoseStack poseStack) {
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.bodyRot));
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

    //? if >=1.21.10
    private static ZombieRenderState copyToZombieState(AvatarRenderState source, ZombieForm form, boolean baby) {
    //? if <1.21.10
    //private static ZombieRenderState copyToZombieState(PlayerRenderState source, ZombieForm form, boolean baby) {
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
        //? if >=1.21.10 {
        target.lightCoords = source.lightCoords;
        target.outlineColor = source.outlineColor;
        //?}
        target.passengerOffset = source.passengerOffset;
        target.nameTag = source.nameTag;
        //? if >=26.1
        target.scoreText = source.scoreText;
        target.nameTagAttachment = source.nameTagAttachment;
        target.leashStates = source.leashStates;
        //? if >=1.21.10
        target.shadowRadius = source.shadowRadius;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.deathTime = source.deathTime;
        target.walkAnimationPos = source.walkAnimationPos;
        target.walkAnimationSpeed = source.walkAnimationSpeed;
        target.scale = source.scale;
        target.ageScale = source.ageScale;
        // CROSS_VERSION-LIVING-RENDER-KINETIC-FEEDBACK-API
        //? if >=1.21.11
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
        // CROSS_VERSION-HELD-ITEM-RENDER-STATE-API
        //? if >=1.21.11
        target.rightHandItemStack = source.rightHandItemStack;
        target.leftArmPose = source.leftArmPose;
        //? if >=1.21.11 {
        target.leftHandItemStack = source.leftHandItemStack;
        target.swingAnimationType = source.swingAnimationType;
        //?}
        target.attackTime = source.attackTime;
        target.isAggressive = true;
        return target;
    }

    private record MonsterModels(
            ZombieModel<ZombieRenderState> normal,
            ZombieModel<ZombieRenderState> babyNormal,
            DrownedModel drowned,
            DrownedModel babyDrowned,
            ZombieModel<ZombieRenderState> husk,
            ZombieModel<ZombieRenderState> babyHusk,
            ZombifiedPiglinModel zombifiedPiglin,
            ZombifiedPiglinModel babyZombifiedPiglin,
            MonsterLayerSet normalLayers,
            MonsterLayerSet drownedLayers,
            MonsterLayerSet huskLayers
    ) {
        HumanoidModel<?> firstPersonModelFor(ZombieForm form, boolean baby) {
            return switch (form) {
                case NORMAL, GIANT -> baby ? babyNormal : normal;
                case DROWNED -> baby ? babyDrowned : drowned;
                case HUSK -> baby ? babyHusk : husk;
                case ZOMBIFIED_PIGLIN -> baby ? babyZombifiedPiglin : zombifiedPiglin;
            };
        }

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
            HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>,
                    HumanoidModel<ZombieRenderState>> adultArmor,
            HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>,
                    HumanoidModel<ZombieRenderState>> babyArmor,
            ZombiePlayerItemInHandLayer adultHandItems,
            ZombiePlayerItemInHandLayer babyHandItems
    ) {
        ZombieModel<ZombieRenderState> parentModel(boolean baby) {
            return baby ? babyParentModel : adultParentModel;
        }

        HumanoidArmorLayer<ZombieRenderState, ZombieModel<ZombieRenderState>,
                HumanoidModel<ZombieRenderState>> armor(boolean baby) {
            return baby ? babyArmor : adultArmor;
        }

        ZombiePlayerItemInHandLayer handItems(boolean baby) {
            return baby ? babyHandItems : adultHandItems;
        }
    }

    private static final class ZombiePlayerItemInHandLayer extends ItemInHandLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {
        private ZombiePlayerItemInHandLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> renderer) {
            super(renderer);
        }

        // CROSS_VERSION-HELD-ITEM-SUBMIT-API
        //? if >=1.21.11 {
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
        //?}
        //? if >=1.21.10 && <1.21.11 {
        /*private void submit(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                int lightCoords,
                ZombieRenderState zombieState,
                AvatarRenderState avatarState
        ) {
            submitArmWithItem(zombieState, avatarState.rightHandItem, HumanoidArm.RIGHT, poseStack, collector, lightCoords);
            submitArmWithItem(zombieState, avatarState.leftHandItem, HumanoidArm.LEFT, poseStack, collector, lightCoords);
        }

        @Override
        protected void submitArmWithItem(
                ZombieRenderState state,
                ItemStackRenderState item,
                HumanoidArm arm,
                PoseStack poseStack,
                SubmitNodeCollector collector,
                int lightCoords
        ) {
            super.submitArmWithItem(state, item, arm, poseStack, collector, lightCoords);
        }
        *///?}
        //? if <1.21.10 {
        /*private void render(
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight,
                ZombieRenderState zombieState,
                PlayerRenderState avatarState
        ) {
            renderArmWithItem(
                    zombieState,
                    avatarState.rightHandItem,
                    HumanoidArm.RIGHT,
                    poseStack,
                    bufferSource,
                    packedLight);
            renderArmWithItem(
                    zombieState,
                    avatarState.leftHandItem,
                    HumanoidArm.LEFT,
                    poseStack,
                    bufferSource,
                    packedLight);
        }

        @Override
        protected void renderArmWithItem(
                ZombieRenderState state,
                ItemStackRenderState item,
                HumanoidArm arm,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight
        ) {
            super.renderArmWithItem(state, item, arm, poseStack, bufferSource, packedLight);
        }
        *///?}
    }

    static ModelPart humanoidArm(HumanoidModel<?> model, HumanoidArm arm) {
        // CROSS_VERSION-HUMANOID-ARM-ACCESS-API
        //? if >=1.21.11 {
        return model.getArm(arm);
        //?} else {
        /*return arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        *///?}
    }

    //? if >=1.21.10 {
    private record FixedTexture(Identifier texturePath) implements ClientAsset.Texture {
        @Override
        public Identifier id() {
            return texturePath;
        }
    }
    //?}

    private record CachedSkin(
            ZombieForm form,
            boolean baby,
            //? if >=1.21.10 {
            ClientAsset.Texture cape,
            ClientAsset.Texture elytra,
            //?} else {
            /*Identifier cape,
            Identifier elytra,
            *///?}
            PlayerSkin skin
    ) {
    }
}
