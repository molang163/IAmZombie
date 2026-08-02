package dev.molang.iamzombieq.client;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IAmZombieClientRegistrationSourceTest {
    @Test
    void registersHerobrineRenderer() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("IAmZombieEntities.HEROBRINE.get()"), "missing Herobrine renderer registration");
        assertTrue(client.contains("HerobrineRenderer::new"), "Herobrine should use HerobrineRenderer");
    }

    @Test
    void registersHoverOnlyFoodClassificationTooltips() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("ItemTooltipEvent"), "missing item tooltip event hook");
        assertTrue(client.contains("ZombieFoodRules.tooltipKey"), "tooltip should use food classification rules");
    }

    @Test
    void herobrineRendererUsesEmissiveEyeLayer() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/HerobrineRenderer.java"));

        assertTrue(renderer.contains("EyesLayer"), "Herobrine should have a separate eye layer");
        assertTrue(renderer.contains("RenderTypes.eyes"), "Herobrine eyes should use the emissive eyes render type");
        assertTrue(renderer.contains("HEROBRINE_EYES"), "Herobrine eye texture should be a named renderer resource");
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/iamzombieq/textures/entity/herobrine_eyes.png")),
                "missing Herobrine eye-mask texture");
    }

    @Test
    void herobrineHeadUsesVanillaSkullItemModel() throws IOException {
        String model = Files.readString(Path.of("src/main/resources/assets/iamzombieq/items/herobrine_head.json"));

        // The head now renders through the vanilla skull pipeline: a special head model keyed to the custom
        // "herobrine" skull type (in-hand/GUI + worn via CustomHeadLayer), not the old janky flat-textured cube.
        assertFalse(model.contains("\"elements\""), "the head should be a vanilla skull model, not a hand-built cube");
        assertTrue(model.contains("minecraft:special"), "Herobrine head item should use a minecraft:special model");
        assertTrue(model.contains("minecraft:head"), "Herobrine head should render via the minecraft:head special model");
        assertTrue(model.contains("\"herobrine\""), "the special head model must reference the custom 'herobrine' skull kind");
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/iamzombieq/textures/entity/herobrine_head.png")),
                "the Herobrine head entity texture (64x64 skin) must exist for the skull model");
    }

    @Test
    void herobrineHeadIsAPlaceableSkullBlock() throws IOException {
        String type = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/block/HerobrineHeadType.java"));
        assertTrue(type.contains("SkullBlock.Type.TYPES.put(\"herobrine\""),
                "the custom skull type must self-register into SkullBlock.Type.TYPES");

        String blocks = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieBlocks.java"));
        assertTrue(blocks.contains("HEROBRINE_HEAD") && blocks.contains("HEROBRINE_WALL_HEAD"),
                "floor + wall Herobrine head blocks must be registered");
        assertTrue(blocks.contains("BlockEntityTypeAddBlocksEvent") && blocks.contains("BlockEntityTypes.SKULL"),
                "the head blocks must join the vanilla SKULL block-entity type so they save/load and render");

        String items = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieItems.java"));
        assertTrue(items.contains("StandingAndWallBlockItem") && items.contains("equippable(EquipmentSlot.HEAD)"),
                "the head item must place the skull blocks yet stay equippable on the head (sun-block)");

        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        assertTrue(client.contains("registerSkullModel") && client.contains("HerobrineHeadType.HEROBRINE"),
                "the custom skull model+texture must be registered for the HEROBRINE type");
    }

    @Test
    void herobrineAudioMuteHasExplicitRestorePath() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("muteForHerobrine"), "nearby Herobrine should enter a named mute path");
        assertTrue(client.contains("restoreHerobrineMutedAudio"), "leaving or resolving the encounter should have an explicit restore path");
        assertTrue(client.contains("minecraft.getSoundManager().pauseAllExcept()"), "mute path should pause active sounds so resume can restore them");
        assertFalse(client.contains("minecraft.getSoundManager().stop()"), "Herobrine mute must not stop and discard active sounds");
        assertTrue(client.contains("minecraft.getSoundManager().resume()"), "restore path should resume client sounds");
        assertTrue(client.contains("mutedByHerobrine = false"), "restore path should clear the local mute state");
        assertTrue(client.contains("event.setSound(null)"), "PlaySoundEvent should still block newly starting sounds while muted");
    }

    @Test
    void zombiePlayerVisualsRenderMonsterBodyInsteadOfOnlySwappingTextures() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));

        assertFalse(client.contains("ZombiePlayerVisuals.renderMonsterBody(event)"), "player pre-render should not stack the old manual monster body path on top of the avatar submit mixin");
        assertTrue(visuals.contains("ZombieModel<ZombieRenderState>"), "normal zombie players should use zombie model geometry");
        assertTrue(visuals.contains("DrownedModel"), "drowned players should use drowned model geometry");
        assertTrue(visuals.contains("BabyZombieModel"), "baby zombie players should use baby zombie geometry");
        assertTrue(visuals.contains("HUSK"), "husk players should select husk geometry/texture");
        assertTrue(visuals.contains("event.setCanceled(true)"), "monster body rendering should replace the original player render instead of stacking on top of it");
        assertFalse(visuals.contains("state.isSpectator = true"), "original player model must not be hidden by pretending the player is a spectator because that can leave spectator head rendering behind");
        assertTrue(visuals.contains("submitModel"), "monster body should submit a model, not only mutate PlayerSkin");
    }

    @Test
    void zombiePlayerMonsterBodyAlsoSubmitsEquipmentAndHandLayers() throws IOException {
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));

        assertTrue(visuals.contains("submitMonsterBodyLayers"), "monster body rendering should submit layers before suppressing player layers");
        assertTrue(visuals.contains("HumanoidArmorLayer<ZombieRenderState"), "zombie player armor should render on zombie geometry");
        assertTrue(visuals.contains("ItemInHandLayer<ZombieRenderState"), "zombie player held items should render from zombie arms");
        assertTrue(visuals.contains("ModelLayers.ZOMBIE_ARMOR"), "normal zombie armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.ZOMBIE_BABY_ARMOR"), "baby zombie armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.DROWNED_ARMOR"), "drowned armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.HUSK_ARMOR"), "husk armor model set should be used");
        assertTrue(visuals.contains("setupAnim(zombieState)"), "layers need animated zombie model pose before submitting");
        assertTrue(visuals.contains("ZombiePlayerItemInHandLayer"), "held item layer should bridge avatar item states onto zombie arms");
        assertTrue(visuals.contains("avatarState.rightHandItemState"), "right-hand item render state must be reused from the player render state");
        assertTrue(visuals.contains("avatarState.leftHandItemState"), "left-hand item render state must be reused from the player render state");
        assertTrue(visuals.contains("babyHandItems"), "baby zombie players need baby-arm hand item placement");
    }

    @Test
    void zombiePlayerVisualsUseCachedVanillaShapeEntities() throws IOException {
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("EntityTypes.ZOMBIE.create"), "normal zombie player visuals should be backed by a real vanilla zombie entity");
        assertTrue(shapeEntities.contains("EntityTypes.DROWNED.create"), "drowned player visuals should be backed by a real vanilla drowned entity");
        assertTrue(shapeEntities.contains("EntityTypes.HUSK.create"), "husk player visuals should be backed by a real vanilla husk entity");
        assertTrue(shapeEntities.contains("EntityTypes.ZOMBIFIED_PIGLIN.create"), "zombified piglin player visuals should be backed by a real vanilla zombified piglin entity");
        assertTrue(shapeEntities.contains("shape.setId(player.getId())"), "shape entities need the player entity id before vanilla render-state extraction touches held item rendering");
        assertTrue(shapeEntities.contains("setBaby"), "baby zombie player visuals should update the vanilla shape entity baby flag");
        assertTrue(shapeEntities.contains("setItemSlot"), "shape entities should receive player equipment before vanilla render state extraction");
    }

    @Test
    void zombiePlayerShapeCacheClearsOnClientLogout() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("public void clear()"), "shape cache needs a cache-wide clear method");
        assertTrue(shapeEntities.contains("shapes.clear()"), "clear method should drop cached shape entities");
        assertTrue(client.contains("ClientPlayerNetworkEvent.LoggingOut"), "client logout should clear cached shape entities");
        assertTrue(client.contains("ZOMBIE_PLAYER_SHAPES.clear()"), "logout handler should clear the shape cache");
    }

    @Test
    void zombiePlayerShapeCacheRemovesPlayersLeavingClientLevel() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("public void remove(AbstractClientPlayer player)"), "shape cache should support removing one player");
        assertTrue(client.contains("EntityLeaveLevelEvent"), "client should observe players leaving the level");
        assertTrue(client.contains("event.getEntity() instanceof AbstractClientPlayer player"), "leave handler should remove real client player entities");
        assertTrue(client.contains("ZOMBIE_PLAYER_SHAPES.remove(player)"), "leave handler should remove only the departed player's cached shape");
    }

    @Test
    void zombiePlayerReplacementRenderStateIsPreparedThroughNeoForgeModifier() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String replacement = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerRenderReplacement.java"));

        assertTrue(client.contains("RegisterRenderStateModifiersEvent"), "client setup should register a render-state modifier for avatar render states");
        assertTrue(client.contains("registerAvatarEntityModifier"), "avatar render states should receive zombie replacement data after vanilla extraction");
        assertTrue(replacement.contains("ContextKey<ZombiePlayerRenderReplacement>"), "replacement data should use NeoForge render-state context keys");
        assertTrue(replacement.contains("setRenderData"), "replacement data should be written onto the avatar render state");
        assertTrue(replacement.contains("EntityRenderer"), "replacement data should carry the vanilla shape entity renderer");
        assertTrue(replacement.contains("EntityRenderState"), "replacement data should carry the vanilla shape render state");
    }

    @Test
    void zombiePlayerReplacementCopiesAvatarAnimationIntoShapeRenderState() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String replacement = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerRenderReplacement.java"));

        // T1: this was a 15-line field-by-field mirror of copyAvatarAnimation's assignments (every `x = avatar.x`).
        // That mirror had to be edited in lockstep with the production method and asserted nothing the method's own
        // existence didn't — a maintenance tax, not a real guard. Reduced to: the wiring (client calls it after
        // vanilla extraction), the method exists, and a few representative fields prove the copy is a real animation
        // sync (walk phase + body rotation + attack progress) rather than an empty stub.
        assertTrue(client.contains("ZombiePlayerRenderReplacement.copyAvatarAnimation(renderState, shapeState)"),
                "replacement render state should receive avatar animation data after vanilla extraction");
        assertTrue(replacement.contains("copyAvatarAnimation"), "replacement should expose a named avatar animation sync path");
        assertTrue(replacement.contains("walkAnimationPos = avatar.walkAnimationPos")
                        && replacement.contains("bodyRot = avatar.bodyRot")
                        && replacement.contains("attackTime = avatar.attackTime"),
                "copyAvatarAnimation must copy the avatar's live animation (walk phase, body rotation, attack progress) onto the shape");
    }

    @Test
    void avatarRendererSubmitDelegatesZombiePlayersToVanillaShapeRenderer() throws IOException {
        String mixins = Files.readString(Path.of("src/main/resources/iamzombieq.mixins.json"));
        String mixin = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/mixin/client/AvatarRendererMixin.java"));

        assertTrue(mixins.contains("\"client\""), "client-only renderer mixins should be listed separately");
        assertTrue(mixins.contains("client.AvatarRendererMixin"), "avatar renderer submit mixin should be registered");
        assertTrue(mixin.contains("@Mixin(AvatarRenderer.class)"), "mixin should target the vanilla avatar renderer");
        assertTrue(mixin.contains("method = \"submit\""), "mixin should inject into AvatarRenderer submit where CameraRenderState is available");
        assertTrue(mixin.contains("ZombiePlayerRenderReplacement.get(state)"), "submit path should read replacement data from the avatar render state");
        assertTrue(mixin.contains("replacement.renderer().submit(replacement.renderState(), poseStack, collector, camera)"), "submit path should delegate to the vanilla shape renderer");
        assertTrue(mixin.contains("callback.cancel()"), "submit path should cancel the original player renderer after replacement");
        // At submit (after InventoryScreen overrides the avatar rotation to frontal), the shape must inherit the
        // avatar's FINAL rotation so the inventory zombie faces front while the in-world render is unchanged.
        assertTrue(mixin.contains("livingShape.bodyRot = state.bodyRot"), "submit must sync the avatar's final body rotation onto the shape (frontal in the inventory)");
        assertTrue(mixin.contains("livingShape.yRot = state.yRot"), "submit must sync the avatar's final head yaw onto the shape");
        assertTrue(mixin.contains("livingShape.xRot = state.xRot"), "submit must sync the avatar's final head pitch onto the shape");
    }

    @Test
    void firstPersonArmRenderingDirectlySubmitsTheFormCorrectMonsterArm() throws IOException {
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String method = SourceScan.stripComments(SourceScan.methodBody(visuals, "public static void renderFirstPersonArm"));
        String compact = method.replaceAll("\\s+", "");

        assertFalse(method.contains("event.getPlayer()"), "the target NeoForge no longer exposes RenderArmEvent.getPlayer() (removed after 26.2.0.6-beta)");
        assertFalse(method.contains("event.getPackedLight()"), "the target NeoForge renamed the arm light accessor (getPackedLight -> getLightCoords, after 26.2.0.6-beta)");
        assertFalse(method.contains("getPlayerRenderer("), "monster arms must not recurse through AvatarRenderer");
        assertFalse(method.contains("renderRightHand("), "monster arms must not fire an inner right-arm event");
        assertFalse(method.contains("renderLeftHand("), "monster arms must not fire an inner left-arm event");
        assertFalse(visuals.contains("renderingFirstPersonArm"), "direct model-part submission no longer needs a reentry guard");
        assertTrue(method.contains("event.getAvatar() instanceof AbstractClientPlayer player"),
                "the arm handler must safely narrow the event avatar before reading player data");
        assertTrue(method.contains("event.getLightCoords()"), "arm rendering must use the target light accessor");
        assertTrue(method.contains("HumanoidModel<?> monsterModel = models().firstPersonModelFor(form, baby)"),
                "the submitted part must come from the form/size-selected monster model");
        assertTrue(method.contains("ModelPart arm = monsterModel.getArm(armSide)"),
                "the submitted part must be the selected monster model's requested arm");
        assertTrue(method.contains("firstPersonTextureFor(form, baby)"),
                "the submitted part must use the matching first-person monster texture");
        assertTrue(method.contains("firstPersonArmOffset(event.getArmPart(), monsterModel.root(), arm, armSide)"),
                "the actual wide/slim event arm must supply the player hand-tip anchor");
        assertFalse(method.contains("submitModelPart(event.getArmPart()"),
                "the event player arm is an anchor only and must never be submitted as replacement geometry");
        assertFalse(method.contains("arm.loadPose(event.getArmPart()"),
                "the monster arm must not inherit the player arm pivot or geometry pose");
        assertTrue(compact.contains("arm.resetPose()"),
                "the monster arm must restore its own initial pose before first-person submission");
        assertTrue(compact.contains("arm.visible=true") && compact.contains("arm.skipDraw=false"),
                "the selected monster arm must be drawable");
        assertTrue(compact.contains("arm.zRot=armSide==HumanoidArm.RIGHT?0.1F:-0.1F"),
                "first-person right/left arm roll must match vanilla's stable pose");
        assertTrue(compact.contains("event.getSubmitNodeCollector().submitModelPart("),
                "the replacement arm must be submitted directly through the event collector");
        assertTrue(method.contains("RenderTypes.entityTranslucent(texture)"),
                "the replacement arm must use the vanilla translucent entity render type");
        assertTrue(method.contains("OverlayTexture.NO_OVERLAY"),
                "the replacement arm must use the vanilla no-overlay value");
        assertEquals(1, SourceScan.countOccurrences(compact, "submitModelPart("),
                "each RenderArmEvent must submit exactly one replacement arm");
        assertEquals(1, SourceScan.countOccurrences(compact, "event.setCanceled(true)"),
                "each successfully replaced arm event must be canceled exactly once");
        assertTrue(compact.contains("poseStack.pushPose()") && compact.contains("finally{poseStack.popPose();}"),
                "the dynamic alignment transform must be scoped and restored even when submission fails");
        assertTrue(compact.contains("poseStack.translate(offset.x(),offset.y(),offset.z())"),
                "the computed hand-tip offset must be applied through the snapshotted PoseStack");
        assertTrue(compact.contains("applyPartPose(poseStack,monsterModel.root().getInitialPose())"),
                "direct arm submission must include the selected monster model's official root initial transform");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "poseStack.translate(offset.x(),offset.y(),offset.z())",
                        "applyPartPose(poseStack,monsterModel.root().getInitialPose())"),
                "the hand-tip offset must be applied before the monster root initial transform");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "applyPartPose(poseStack,monsterModel.root().getInitialPose())",
                        "submitModelPart("),
                "the official monster root transform must be applied before direct arm submission");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "poseStack.popPose()",
                        "event.setCanceled(true)"),
                "the original event may only be canceled after submission and PoseStack restoration succeed");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "submitModelPart(",
                        "event.setCanceled(true)"),
                "the original event may only be canceled after replacement submission succeeds");
    }

    @Test
    void firstPersonArmRenderingPairsEveryFormAndSizeWithVanillaGeometryAndTexture() throws IOException {
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String selector = SourceScan.stripComments(SourceScan.methodBody(visuals, "HumanoidModel<?> firstPersonModelFor"))
                .replaceAll("\\s+", "");
        String texture = SourceScan.stripComments(SourceScan.methodBody(visuals, "private static Identifier firstPersonTextureFor"))
                .replaceAll("\\s+", "");
        String factory = SourceScan.stripComments(SourceScan.methodBody(visuals, "private static MonsterModels createMonsterModels"));

        assertTrue(selector.contains("caseNORMAL,GIANT->baby?babyNormal:normal"),
                "normal and giant arms must use matching zombie/baby-zombie geometry");
        assertTrue(selector.contains("caseDROWNED->baby?babyDrowned:drowned"),
                "drowned arms must use matching adult/baby drowned geometry");
        assertTrue(selector.contains("caseHUSK->baby?babyHusk:husk"),
                "husk arms must use matching adult/baby husk layers");
        assertTrue(selector.contains("caseZOMBIFIED_PIGLIN->baby?babyZombifiedPiglin:zombifiedPiglin"),
                "zombified piglin arms must use matching adult/baby piglin geometry");
        assertTrue(factory.contains("new AdultZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN))"),
                "adult zombified piglin first-person geometry must use its official model layer");
        assertTrue(factory.contains("new BabyZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN_BABY))"),
                "baby zombified piglin first-person geometry must use its official model layer");
        assertTrue(texture.contains("baby&&form==ZombieForm.ZOMBIFIED_PIGLIN"),
                "the baby zombified piglin texture override must be limited to that form and size");
        assertTrue(texture.contains("textures/entity/piglin/zombified_piglin_baby.png"),
                "baby zombified piglin arms must use the official baby texture");
        assertTrue(texture.contains("returntextureFor(form,baby)"),
                "all other first-person textures must preserve the existing form/size mapping");
    }

    @Test
    void zombiePlayerSleepingPoseMirrorsSleepingPosOntoShape() throws IOException {
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));
        String method = SourceScan.methodBody(shapeEntities, "private static void syncShape");

        // RC2: the LIVE third-person render submits the cached SHAPE entity through vanilla LivingEntityRenderer,
        // which lays a sleeper FLAT + CENTERED only when the render state's bedOrientation is non-null. Vanilla
        // derives that orientation from the entity's sleeping block position, so syncShape must mirror the player's
        // sleeping pos onto the shape -- and CLEAR it when awake, since the shape is cached and reused across frames
        // (a stale pos would keep the body flat after standing up). The visual itself is only confirmable via
        // runClient (GL), so this source scan pins the load-bearing data flow the headless harness can verify.
        assertTrue(method.contains("player.getSleepingPos()"),
                "syncShape must read the player's sleeping block position");
        assertTrue(method.contains("shape.setSleepingPos("),
                "syncShape must set the sleeping pos on the shape so vanilla derives a non-null bedOrientation (coffin facing)");
        assertTrue(method.contains("shape.clearSleepingPos()"),
                "syncShape must clear the shape's sleeping pos when the player is awake (the shape is cached/reused)");
    }
}
