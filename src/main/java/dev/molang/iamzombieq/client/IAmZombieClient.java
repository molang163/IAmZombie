package dev.molang.iamzombieq.client;
import dev.molang.iamzombieq.util.ModIds;

import dev.molang.iamzombieq.IAmZombieClientConfig;
import dev.molang.iamzombieq.IAmZombieMod;
import dev.molang.iamzombieq.IAmZombiePreferencesConfig;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.IAmZombieEntities;
import dev.molang.iamzombieq.block.HerobrineHeadType;
import dev.molang.iamzombieq.entity.HerobrineEntity;
import dev.molang.iamzombieq.gameplay.ZombieFoodEvents;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;
import dev.molang.iamzombieq.rules.ZombieRenderRules;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
// CROSS_VERSION-SKULL-MODEL-NAMESPACE:client-import
//? if >=1.21.11 {
import net.minecraft.client.model.object.skull.SkullModel;
//?} else {
/*import net.minecraft.client.model.SkullModel;
*///?}
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class IAmZombieClient {
    private static final String AUTHORITY_RUNTIME =
            "dev.molang.iamzombieq.config.ConfigAuthorityRuntime";
    private static final Class<?> AUTHORITY_RUNTIME_CLASS =
            authorityRuntimeClass();
    private static final MethodHandle CLEAR_AUTHORITY =
            authorityHandle("clear", void.class, Connection.class);
    private static final MethodHandle AUTHORITY_READY =
            authorityHandle("isReady", boolean.class, Connection.class);
    private static final MethodHandle REFRESH_AUTHORITY =
            authorityHandle(
                    "refreshClientFromSyncedServerConfig",
                    void.class,
                    Connection.class);
    private static final MethodHandle CONFIGURED_ZOMBIE_FOODS =
            authorityHandle(
                    "configuredZombieFoods", List.class, Connection.class);
    private static final MethodHandle RESOLVE_FOOD_CONFIG =
            authorityHandle(
                    "resolveFoodConfig",
                    int.class,
                    Connection.class,
                    String.class);
    private static final ZombiePlayerShapeEntities ZOMBIE_PLAYER_SHAPES = new ZombiePlayerShapeEntities();
    // The Herobrine head renders through the vanilla skull pipeline (block-entity renderer, worn CustomHeadLayer,
    // and the minecraft:head item model) using a 64x64 humanoid head model with a fixed Herobrine skin.
    private static final ModelLayerLocation HEROBRINE_HEAD_LAYER = new ModelLayerLocation(
            ModIds.id("herobrine_head"), "main");
    private static final Identifier HEROBRINE_HEAD_TEXTURE =
            ModIds.id("textures/entity/herobrine_head.png");
    private static boolean mutedByHerobrine;
    // Client-side count of live Herobrine entities, maintained via EntityJoinLevelEvent/EntityLeaveLevelEvent so the
    // per-tick mute proximity scan can be skipped entirely when no Herobrine is present. Clamped at >= 0; reset on
    // logout. Never used to MUTE (only to skip the no-op scan) — when a Herobrine IS present the real AABB scan runs.
    private static int herobrinePresenceCount;
    // HB-AUDIO-HEARTBEAT: ticks until the next phase-scaled heartbeat beat. Counts down each client tick while a
    // Herobrine is in the audible band and the local encounter phase >= ESCALATION. Reset on logout.
    private static int heartbeatCooldown;
    // HB-JOLT: remaining client frames to draw the red vignette after the lethal stinger is heard. Reset on logout.
    private static int joltVignetteTicks;
    // Identifier of the vanilla heartbeat sound, whitelisted through the Herobrine mute so it can layer under silence.
    private static final Identifier HEARTBEAT_ID = SoundEvents.WARDEN_HEARTBEAT.location();
    // Identifier of the lethal stinger; heard client-side it arms the jolt vignette and is allowed through the mute.
    private static final Identifier JOLT_STINGER_ID = SoundEvents.WARDEN_ROAR.location();

    private IAmZombieClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(IAmZombieClient::onServerConfigReloading);
        modEventBus.addListener(IAmZombieClient::registerRenderers);
        modEventBus.addListener(IAmZombieClient::registerRendererLayers);
        modEventBus.addListener(IAmZombieClient::registerSkullLayers);
        modEventBus.addListener(IAmZombieClient::createSkullModels);
        modEventBus.addListener(IAmZombieClient::registerRenderStateModifiers);
        NeoForge.EVENT_BUS.register(IAmZombieClient.class);
        NeoForge.EVENT_BUS.register(DrownedVisionEvents.class);
    }

    private static void onServerConfigReloading(
            ModConfigEvent.Reloading event) {
        ModConfig config = event.getConfig();
        if (!IAmZombieMod.MOD_ID.equals(config.getModId())
                || config.getType() != ModConfig.Type.SERVER
                || config.getSpec() != IAmZombieServerConfig.SPEC) {
            return;
        }
        var loaded = config.getLoadedConfig();
        if (loaded == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Connection captured = currentConnection(minecraft);
        if (captured == null) {
            return;
        }
        boolean inMemory =
                loaded.config().configFormat().isInMemory();
        if (captured.isMemoryConnection() == inMemory) {
            return;
        }

        ConfigAuthorityReloadQueue.enqueue(
                captured,
                () -> currentConnection(minecraft),
                minecraft::execute,
                IAmZombieClient::authorityReady,
                IAmZombieClient::refreshClientAuthority);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IAmZombieEntities.HEROBRINE.get(), HerobrineRenderer::new);
    }

    private static void registerSkullLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HEROBRINE_HEAD_LAYER, SkullModel::createHumanoidHeadLayer);
    }

    private static void createSkullModels(EntityRenderersEvent.CreateSkullModels event) {
        // Custom (non-vanilla) skull type → registered with a 64x64 humanoid model (SkullModel::new) and the fixed
        // Herobrine skin; this feeds SkullBlockRenderer's model/SKIN_BY_TYPE maps for block, worn and item rendering.
        // CROSS_VERSION-SKULL-MODEL-REGISTRATION-API
        //? if >=1.21.10 {
        event.registerSkullModel(HerobrineHeadType.HEROBRINE, HEROBRINE_HEAD_LAYER, HEROBRINE_HEAD_TEXTURE);
        //?} else {
        /*event.registerSkullModel(HerobrineHeadType.HEROBRINE, HEROBRINE_HEAD_LAYER);
        net.minecraft.client.renderer.blockentity.SkullBlockRenderer.SKIN_BY_TYPE.put(
                HerobrineHeadType.HEROBRINE, HEROBRINE_HEAD_TEXTURE);
        *///?}
    }

    private static void registerRendererLayers(EntityRenderersEvent.AddLayers event) {
        ZombiePlayerVisuals.initializeMonsterBodyLayers(event.getContext());
    }

    private static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        //? if >=26.1 {
        event.registerAvatarEntityModifier(new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
            @Override
            public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(
                    T avatar,
                    net.minecraft.client.renderer.entity.state.AvatarRenderState renderState
            ) {
                processAvatarRenderState(avatar, renderState);
            }
        });
        //?}
        //? if >=1.21.10 && <26.1 {
        /*event.<net.minecraft.world.entity.Entity,
                        net.minecraft.client.renderer.entity.state.AvatarRenderState>registerEntityModifier(
                new com.google.common.reflect.TypeToken<
                        net.minecraft.client.renderer.entity.player.AvatarRenderer<?>>() {},
                (entity, renderState) -> {
                    if (entity instanceof net.minecraft.world.entity.Avatar avatar) {
                        processAvatarRenderState(avatar, renderState);
                    }
                });
        *///?}
        //? if <1.21.10 {
        /*event.registerEntityModifier(
                net.minecraft.client.renderer.entity.player.PlayerRenderer.class,
                (avatar, renderState) -> processAvatarRenderState(avatar, renderState));
        *///?}
    }

    private static void processAvatarRenderState(
            //? if >=1.21.10 {
            net.minecraft.world.entity.Avatar avatar,
            //?} else {
            /*net.minecraft.client.player.AbstractClientPlayer avatar,
            *///?}
            //? if >=1.21.10
            net.minecraft.client.renderer.entity.state.AvatarRenderState renderState
            //? if <1.21.10
            //net.minecraft.client.renderer.entity.state.PlayerRenderState renderState
    ) {
        //? if >=1.21.10 {
        if (!(avatar instanceof AbstractClientPlayer player)
                || !ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())
                || !ZombiePlayerVisuals.shouldUseZombieVisuals(player)) {
        //?} else {
        /*AbstractClientPlayer player = avatar;
        if (!ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())
                || !ZombiePlayerVisuals.shouldUseZombieVisuals(player)) {
        *///?}
            ZombiePlayerRenderReplacement.set(renderState, null);
            return;
        }

        LivingEntity shape = ZOMBIE_PLAYER_SHAPES.shapeFor(player);
        if (shape == null) {
            // No shape entity could be created (EntityType.create returned null) -> vanilla avatar (no NPE).
            ZombiePlayerRenderReplacement.set(renderState, null);
            return;
        }
        @SuppressWarnings("unchecked")
        EntityRenderer<LivingEntity, EntityRenderState> renderer = (EntityRenderer<LivingEntity, EntityRenderState>) Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(shape);
        ZombiePlayerRenderReplacement replacement = ZOMBIE_PLAYER_SHAPES.replacementFor(player, renderer);
        if (replacement == null) {
            ZombiePlayerRenderReplacement.set(renderState, null);
            return;
        }
        EntityRenderState shapeState = replacement.renderState();
        ZombiePlayerRenderReplacement.copyAvatarAnimation(renderState, shapeState);
        ZombiePlayerRenderReplacement.set(renderState, replacement);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean shouldMute = isLocalPlayerNearHerobrine(minecraft);
        if (shouldMute) {
            muteForHerobrine(minecraft);
        } else {
            restoreHerobrineMutedAudio(minecraft);
        }
        tickHeartbeat(minecraft);
        if (joltVignetteTicks > 0) {
            joltVignetteTicks--;
        }
    }

    /**
     * HB-AUDIO-HEARTBEAT: while a Herobrine is in the heartbeat band and the local encounter phase has reached
     * ESCALATION (read from the synced phase on the nearest Herobrine), layer a phase/distance-scaled vanilla
     * heartbeat under the silence. OBSERVATION stays dead-silent (period 0 = no beat).
     */
    private static void tickHeartbeat(Minecraft minecraft) {
        if (!IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_ENABLED.get()
                || herobrinePresenceCount <= 0
                || minecraft.player == null
                || minecraft.level == null) {
            heartbeatCooldown = 0;
            return;
        }

        double far = IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_FAR_DISTANCE.get();
        HerobrineEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        AABB area = minecraft.player.getBoundingBox().inflate(far);
        for (HerobrineEntity herobrine : minecraft.level.getEntitiesOfClass(HerobrineEntity.class, area, Entity::isAlive)) {
            double dist = minecraft.player.distanceTo(herobrine);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = herobrine;
            }
        }

        double near = IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_NEAR_DISTANCE.get();
        if (nearest == null || nearestDist > far) {
            heartbeatCooldown = 0;
            return;
        }

        int period = HerobrineEncounter.heartbeatPeriodTicks(nearest.getEncounterPhase(), nearestDist);
        if (period <= 0) {
            heartbeatCooldown = 0; // OBSERVATION/DORMANT — keep the dead silence
            return;
        }

        if (heartbeatCooldown > 0) {
            heartbeatCooldown--;
            return;
        }

        // Closer = louder. Map the audible band to a [0.5, 1.0] volume ramp.
        float t = (float) Math.max(0.0, Math.min(1.0, (far - nearestDist) / Math.max(1.0, far - near)));
        float volume = 0.5F + 0.5F * t;
        float pitch = nearest.getEncounterPhase() == HerobrineEncounter.Phase.LETHAL ? 1.0F : 0.85F;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
        heartbeatCooldown = period;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (joltVignetteTicks <= 0
                || !IAmZombiePreferencesConfig.HEROBRINE_JOLT_VIGNETTE_ENABLED.get()) {
            return;
        }
        // HB-JOLT: a brief translucent red vignette. Fades over the armed frames.
        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        int alpha = Math.min(170, joltVignetteTicks * 22);
        int color = (alpha << 24) | 0x00B00000;
        event.getGuiGraphics().fill(0, 0, width, height, color);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getConnection() != null) {
            try {
                CLEAR_AUTHORITY.invokeExact(event.getConnection());
            } catch (Throwable failure) {
                throw failClosed(failure);
            }
        }
        ZOMBIE_PLAYER_SHAPES.clear();
        ZombiePlayerVisuals.clearSkins();
        herobrinePresenceCount = 0;
        heartbeatCooldown = 0;
        joltVignetteTicks = 0;
        restoreHerobrineMutedAudio(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof HerobrineEntity) {
            herobrinePresenceCount++;
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            ZOMBIE_PLAYER_SHAPES.remove(player);
            ZombiePlayerVisuals.invalidateSkin(player.getUUID());
        } else if (event.getLevel().isClientSide() && event.getEntity() instanceof HerobrineEntity) {
            herobrinePresenceCount = Math.max(0, herobrinePresenceCount - 1);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) {
            return;
        }
        Identifier id = event.getSound()
                //? if >=1.21.11 {
                .getIdentifier();
                //?} else {
                /*.getLocation();
                *///?}
        // The lethal stinger arms the brief jolt vignette (HB-JOLT) and is always allowed through.
        if (id.equals(JOLT_STINGER_ID)) {
            if (IAmZombiePreferencesConfig.HEROBRINE_JOLT_VIGNETTE_ENABLED.get()) {
                joltVignetteTicks = 8;
            }
            return;
        }
        // The Herobrine heartbeat must layer UNDER the silence, so let it pass while muted.
        if (id.equals(HEARTBEAT_ID)) {
            return;
        }
        if (mutedByHerobrine) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    //? if >=1.21.10
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
    //? if <1.21.10
    //public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        ZombiePlayerVisuals.applyPlayerSkin(event.getRenderState());
    }

    @SubscribeEvent
    //? if >=26.2
    public static void onRenderArm(RenderArmEvent<?> event) {
    //? if <26.2
    //public static void onRenderArm(RenderArmEvent event) {
        ZombiePlayerVisuals.renderFirstPersonArm(event);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!ZombieFoodRules.isFoodRuleTarget(itemId)) {
            return;
        }

        Connection authorityConnection =
                Minecraft.getInstance().getConnection().getConnection();
        FoodRule rule = ZombieFoodRules.ruleFor(
                itemId,
                Set.copyOf(configuredZombieFoods(authorityConnection)),
                ZombieFoodEvents::resolveEffect,
                key -> resolveFoodConfig(authorityConnection, key));
        event.getToolTip().add(Component.translatable(ZombieFoodRules.tooltipKey(rule)).withStyle(ChatFormatting.DARK_GREEN));
    }

    @SuppressWarnings("unchecked")
    private static List<String> configuredZombieFoods(
            Connection connection) {
        try {
            return (List<String>) CONFIGURED_ZOMBIE_FOODS.invokeExact(
                    connection);
        } catch (Throwable failure) {
            throw failClosed(failure);
        }
    }

    private static int resolveFoodConfig(
            Connection connection, String semanticKey) {
        try {
            return (int) RESOLVE_FOOD_CONFIG.invokeExact(
                    connection, semanticKey);
        } catch (Throwable failure) {
            throw failClosed(failure);
        }
    }

    private static boolean authorityReady(Connection connection) {
        try {
            return (boolean) AUTHORITY_READY.invokeExact(connection);
        } catch (Throwable failure) {
            throw failClosed(failure);
        }
    }

    private static void refreshClientAuthority(Connection connection) {
        try {
            REFRESH_AUTHORITY.invokeExact(connection);
        } catch (Throwable failure) {
            throw failClosed(failure);
        }
    }

    private static Connection currentConnection(Minecraft minecraft) {
        var listener = minecraft.getConnection();
        return listener == null ? null : listener.getConnection();
    }

    private static Class<?> authorityRuntimeClass() {
        try {
            return Class.forName(
                    AUTHORITY_RUNTIME,
                    true,
                    IAmZombieClient.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static MethodHandle authorityHandle(
            String methodName,
            Class<?> returnType,
            Class<?>... parameterTypes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    AUTHORITY_RUNTIME_CLASS, MethodHandles.lookup());
            return lookup.findStatic(
                    AUTHORITY_RUNTIME_CLASS,
                    methodName,
                    MethodType.methodType(returnType, parameterTypes));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static RuntimeException failClosed(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(
                "Configuration authority runtime invocation failed",
                failure);
    }

    private static boolean isLocalPlayerNearHerobrine(Minecraft minecraft) {
        if (herobrinePresenceCount <= 0 || minecraft.player == null || minecraft.level == null) {
            return false;
        }

        AABB area = minecraft.player.getBoundingBox().inflate(HerobrineRules.SILENCE_DISTANCE);
        return !minecraft.level.getEntitiesOfClass(HerobrineEntity.class, area, Entity::isAlive).isEmpty();
    }

    private static void muteForHerobrine(Minecraft minecraft) {
        if (!mutedByHerobrine) {
            minecraft.getSoundManager().pauseAllExcept();
        }
        mutedByHerobrine = true;
    }

    private static void restoreHerobrineMutedAudio(Minecraft minecraft) {
        if (mutedByHerobrine) {
            minecraft.getSoundManager().resume();
        }
        mutedByHerobrine = false;
    }

}
