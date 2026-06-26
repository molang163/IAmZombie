package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.ModIds;
import dev.molang.iamzombieq.util.Difficulties;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.IAmZombieMod;
import dev.molang.iamzombieq.api.event.ZombieEvolvedEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformedEvent;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.rules.BiomeContext;
import dev.molang.iamzombieq.rules.DeathTrigger;
import dev.molang.iamzombieq.rules.difficulty.DifficultyGuardRules;
import dev.molang.iamzombieq.rules.DimensionContext;
import dev.molang.iamzombieq.rules.EvolutionResult;
import dev.molang.iamzombieq.rules.HeadProtection;
import dev.molang.iamzombieq.rules.ZombieDamageRules;
import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.ZombieEvolutionRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieReinforcementRules;
import dev.molang.iamzombieq.rules.ZombieSunlightRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import dev.molang.iamzombieq.tags.IAmZombieBlockTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@SuppressWarnings("deprecation")
public final class ZombiePlayerEvents {
    private static final int POST_EVOLUTION_FOOD_LEVEL = 6;
    private static final Identifier INNATE_ARMOR_ID = ModIds.id("innate_armor");
    private static final Identifier BABY_SCALE_ID = ModIds.id("baby_scale");
    private static final Identifier BABY_SPEED_ID = ModIds.id("baby_speed");
    private static final Identifier DROWNED_MINING_ID = ModIds.id("drowned_submerged_mining");
    private static final Identifier GIANT_HEALTH_ID = ModIds.id("giant_health");
    private static final Identifier GIANT_SCALE_ID = ModIds.id("giant_scale");
    private static final Identifier GIANT_ENTITY_RANGE_ID = ModIds.id("giant_entity_range");
    private static final Identifier GIANT_BLOCK_RANGE_ID = ModIds.id("giant_block_range");
    private static final Identifier GIANT_STEP_HEIGHT_ID = ModIds.id("giant_step_height");
    private static final Identifier GIANT_SAFE_FALL_ID = ModIds.id("giant_safe_fall");
    private static final Identifier GIANT_ATTACK_ID = ModIds.id("giant_attack");
    private static final Identifier DIFFICULTY_ATTACK_DAMAGE_ID = ModIds.id("difficulty_attack_damage");
    // Per-player game-time until which the player's fire is sun-sourced; on-fire ticks within this window are
    // re-attributed to the sunlight death type. Refreshed each sun-burn tick. Server-side only; cleared on stop.
    private static final Map<UUID, Long> SUNLIGHT_FIRE_UNTIL = new HashMap<>();
    // Per-player packed (form, size, difficulty) signature of the form attributes last applied by
    // refreshFormAttributes. The per-tick path skips re-applying the (idempotent) modifiers when the signature is
    // unchanged — the common per-tick no-op. Event sites where the entity is fresh/modifier-less (login, respawn,
    // evolution, giant-kill) MUST route through the FORCED variant: transient attribute modifiers are NOT persisted
    // across respawn, and a same-form NORMAL->NORMAL ordinary death leaves the signature UNCHANGED, so a cache-gated
    // reapply would (incorrectly) skip restoring the fresh entity's innate armor/health/scale. Server-side only;
    // cleared on logout + server stop. Sentinel: no entry == never applied (force on first tick).
    private static final Map<UUID, Long> FORM_ATTRIBUTE_SIGNATURE = new HashMap<>();
    // Per-player reinforcement chance (vanilla SPAWN_REINFORCEMENTS_CHANCE), lazily rolled once (0-0.1, plus a
    // regional-difficulty-scaled leader bonus) and decayed by -0.05 per successful reinforcement spawn. Stored
    // off-entity (PlayerZombieData has no room). Server-side only; cleared on logout + server stop.
    private static final Map<UUID, Double> REINFORCEMENT_CHANCE = new HashMap<>();
    // Per-giant last-tick position, so the passive walk-destruction sweep volume spans from last to current position
    // (catches blocks a fast/sprinting giant would otherwise phase past). Server-side only; cleared on logout + stop.
    private static final Map<UUID, Vec3> GIANT_LAST_POS = new HashMap<>();
    // Per-giant game-time until which the active swing AoE is on cooldown, so left-clicking isn't an infinite
    // instant-miner. Server-side only; cleared on logout + server stop.
    private static final Map<UUID, Long> GIANT_SWING_COOLDOWN = new HashMap<>();
    // Reused per-tick eye-position scratch for the sky-visibility sun-burn check. onPlayerTick is single-threaded
    // (server thread), so a shared mutable instance is safe and avoids a per-tick BlockPos allocation.
    private static final BlockPos.MutableBlockPos SUN_BURN_EYE_POS = new BlockPos.MutableBlockPos();
    private static boolean peacefulWarningLogged;
    private static final List<ResourceKey<Recipe<?>>> COFFIN_RECIPES = List.of(
            coffinRecipe("oak_coffin"),
            coffinRecipe("spruce_coffin"),
            coffinRecipe("birch_coffin"),
            coffinRecipe("jungle_coffin"),
            coffinRecipe("acacia_coffin"),
            coffinRecipe("cherry_coffin"),
            coffinRecipe("dark_oak_coffin"),
            coffinRecipe("pale_oak_coffin"),
            coffinRecipe("mangrove_coffin"),
            coffinRecipe("bamboo_coffin"),
            coffinRecipe("crimson_coffin"),
            coffinRecipe("warped_coffin")
    );
    private static final ResourceKey<DamageType> SUNLIGHT_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ModIds.id("sunlight")
    );

    private ZombiePlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }

        // A creative player who has never been a zombie (no attachment) has nothing to run; bail before
        // materializing the default attachment. A creative ZOMBIE (has data) runs the full per-tick logic below
        // just like a survival zombie (N6) — only flight + invulnerability remain creative-inherent.
        if (player.isCreative() && !player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);

        refreshFormAttributes(player, data);
        applyPassiveFormAbilities(player, data);
        if (data.state().form() == ZombieForm.GIANT) {
            handleGiantTick(player);
        }

        if (!shouldApplyZombieRules(player)) {
            return;
        }

        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean sunBurnTick = isSunBurnTick(player);
        if (!sunBurnTick) {
            return;
        }

        HeadProtection headProtection = classifyHeadProtection(headStack);
        if (ZombieSunlightRules.shouldBurn(data.state().form(), true, headProtection)) {
            IAmZombieAdvancements.award(player, IAmZombieAdvancements.SUN);
            igniteSunlightBurn(player);
        } else if (ZombieSunlightRules.shouldDamageHeadProtection(data.state().form(), true, headProtection) && headStack.isDamageableItem()) {
            int damage = IAmZombieConfig.SUN_PROTECTION_HEADGEAR_DAMAGE.get();
            if (damage > 0) {
                headStack.hurtAndBreak(damage, player, EquipmentSlot.HEAD);
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!shouldApplyZombieRules(event.getEntity())) {
            return;
        }
        PlayerZombieData data = event.getEntity().getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        // Only counter the vanilla "not on ground" /5 mining penalty while floating. The SUBMERGED_MINING_SPEED
        // attribute already neutralizes the underwater 0.2x penalty, so applying x5 on the ground too would stack to ~5x.
        if (data.state().form() == ZombieForm.DROWNED && event.getEntity().isUnderWater() && !event.getEntity().onGround()) {
            event.setNewSpeed(Math.max(event.getNewSpeed(), event.getOriginalSpeed() * 5.0F));
            return;
        }

        // Vanilla-zombie flavor: bare-handed zombies claw through wooden doors faster. Independent of the
        // drowned underwater branch above (which already returned), so the two never stack.
        boolean mainHandEmpty = event.getEntity().getMainHandItem().isEmpty();
        boolean blockIsWoodenDoor = event.getState().is(BlockTags.WOODEN_DOORS);
        if (ZombieBalanceRules.shouldBoostWoodenDoorBreak(mainHandEmpty, blockIsWoodenDoor)) {
            event.setNewSpeed(event.getNewSpeed() * ZombieBalanceRules.WOODEN_DOOR_BREAK_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (replaceSunlightFireDamage(event)) {
            return;
        }

        // Suffocation immunity for the giant: its huge body is constantly embedded in the blocks it is mid-crushing,
        // so without this it would smother on its own path. Bound strictly to the GIANT form (revoked the instant
        // the form changes), so a reverted player can never sit invincibly inside solid blocks.
        if (event.getEntity() instanceof ServerPlayer giant
                && shouldApplyZombieRules(giant)
                && event.getSource().is(DamageTypes.IN_WALL)
                && giant.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form() == ZombieForm.GIANT) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player && shouldApplyZombieRules(player)
                && event.getSource().getEntity() instanceof LivingEntity attacker) {
            reinforceZombiePlayer(player, attacker);
        }

        if (!(event.getSource().getEntity() instanceof Player player) || !shouldApplyZombieRules(player)) {
            return;
        }
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        if (data.state().form() == ZombieForm.HUSK
                && event.getSource().getDirectEntity() == player
                && event.getEntity() instanceof LivingEntity target
                && target != player) {
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 15, 0), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && shouldApplyZombieRules(player)) {
            boolean firstZombieAttach = !player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE);
            PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            // FORCED: the fresh login entity needs its innate form attributes, and the signature cache entry was
            // cleared on the prior logout — force the apply so login never depends on a stale/absent cache entry.
            refreshFormAttributesForced(player, data);
            IAmZombieAdvancements.award(player, IAmZombieAdvancements.ROOT);
            warnIfPeacefulUnsupported(player);
            if (firstZombieAttach) {
                giveStartingItems(player);
                unlockCoffinRecipes(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop the player's sun-fire window on disconnect so it can't accumulate in the map for the server's
        // lifetime, nor mis-attribute a fresh (non-sun) fire to sunlight if they reconnect while it's still open.
        SUNLIGHT_FIRE_UNTIL.remove(event.getEntity().getUUID());
        // Drop the cached form-attribute signature so a reconnecting player (whose transient modifiers were
        // cleared with the old entity) re-applies on login via the forced refresh rather than trusting a stale entry.
        FORM_ATTRIBUTE_SIGNATURE.remove(event.getEntity().getUUID());
        // Drop the reinforcement chance so a reconnecting player re-rolls a fresh value (and so accumulated -0.05
        // penalties don't persist for the server's lifetime).
        REINFORCEMENT_CHANCE.remove(event.getEntity().getUUID());
        // Drop the giant walk-destruction sweep anchor + swing cooldown so they can't accumulate for the server's
        // lifetime (and a reconnecting player starts with a fresh sweep origin / no stale cooldown).
        GIANT_LAST_POS.remove(event.getEntity().getUUID());
        GIANT_SWING_COOLDOWN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SUNLIGHT_FIRE_UNTIL.clear();
        FORM_ATTRIBUTE_SIGNATURE.clear();
        REINFORCEMENT_CHANCE.clear();
        GIANT_LAST_POS.clear();
        GIANT_SWING_COOLDOWN.clear();
        peacefulWarningLogged = false;
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !shouldApplyZombieRules(player)) {
            return;
        }
        PlayerZombieData previous = event.getOriginal().getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        // Ordinary death resets the form to normal zombie (preserving the one-time evolution-reward flags);
        // a non-death clone (dimension change / End return) carries the form + flags over unchanged. We copy
        // explicitly here instead of relying on .copyOnDeath() so persistence is correct regardless of
        // NeoForge's default clone-copy behavior.
        PlayerZombieData nextData = event.isWasDeath() ? previous.resetStateForOrdinaryDeath() : previous;
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
        player.syncData(IAmZombieAttachments.PLAYER_ZOMBIE);
        // ADDITIVE (Phase-1 API): observer-only POST fire after the existing write+sync, ONLY when the form
        // actually changed (an ordinary-death reset from a non-NORMAL form; a NORMAL->NORMAL death or a non-death
        // dimension clone leaves the form unchanged and is not a transform). Neutral when no addon subscribes;
        // isolated via ZombieEventPublisher (try/catch). Does NOT gate or change the clone logic.
        if (previous.state().form() != nextData.state().form()) {
            ZombieEventPublisher.post(new ZombieTransformedEvent(player, previous.state().form(), nextData.state().form()));
        }
        // FORCED: the respawned entity has had its transient attribute modifiers cleared, and a same-form
        // NORMAL->NORMAL ordinary death leaves the signature unchanged — a cache-gated refresh would skip it.
        refreshFormAttributesForced(player, nextData);
        applyPassiveFormAbilities(player, nextData);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Giant
                && event.getEntity().getType() == EntityTypes.GIANT
                && event.getSource().getEntity() instanceof ServerPlayer killer
                && ZombieEvolutionRules.canTransformFromGiantKill(killer.isCreative(), BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString())) {
            PlayerZombieData data = killer.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            PlayerZombieData nextData = data.withState(ZombieEvolutionRules.giantStateAfterKill(data.state()));
            killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
            killer.syncData(IAmZombieAttachments.PLAYER_ZOMBIE);
            // ADDITIVE (Phase-1 API): observer-only POST fire for the giant-kill transform, after the existing
            // write+sync. Neutral when no addon subscribes; isolated via ZombieEventPublisher. Logic unchanged.
            ZombieEventPublisher.post(new ZombieTransformedEvent(killer, data.state().form(), nextData.state().form()));
            // FORCED: setHealth(getMaxHealth()) below relies on the GIANT max-health modifier being reapplied now,
            // even if the giant->giant signature happens to be unchanged.
            refreshFormAttributesForced(killer, nextData);
            killer.setHealth(killer.getMaxHealth());
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || !shouldApplyZombieRules(player)) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        EvolutionResult result = ZombieEvolutionRules.resolveDeath(
                data.state(),
                triggerFrom(event.getSource()),
                biomeContext(player),
                dimensionContext(player)
        );

        if (!result.inPlaceRespawn()) {
            return;
        }

        event.setCanceled(true);
        PlayerZombieData nextData = data.withState(result.nextState());
        nextData = grantFirstEvolutionReward(player, data, nextData, result);
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
        player.syncData(IAmZombieAttachments.PLAYER_ZOMBIE);
        // ADDITIVE (Phase-1 API): observer-only POST fire for the death-driven evolution, after the existing
        // write+sync, carrying the before/after state + outcome snapshot. Neutral when no addon subscribes;
        // isolated via ZombieEventPublisher. Does NOT add Pre-cancel gating; existing logic unchanged.
        ZombieEventPublisher.post(new ZombieEvolvedEvent(player, data.state(), nextData.state(), result.outcome()));
        // FORCED: setHealth(getMaxHealth()*0.5) below relies on the just-evolved form's max-health modifier being
        // reapplied now; the in-place respawn must restore innate attributes regardless of signature equality.
        refreshFormAttributesForced(player, nextData);
        applyPassiveFormAbilities(player, nextData);
        awardEvolutionAdvancement(player, result);
        if (isFirstEvolution(data.state(), nextData.state())) {
            IAmZombieAdvancements.award(player, IAmZombieAdvancements.FIRST_EVOLUTION);
        }

        player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.resetFallDistance();
        player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), POST_EVOLUTION_FOOD_LEVEL));
        player.getFoodData().setSaturation(0.0F);
    }

    private static boolean shouldApplyZombieRules(Player player) {
        // N6: creative zombie players run all server-side zombie rules (only flight + invulnerability remain
        // creative-inherent). Keep the server-side + non-spectator gates.
        return !player.level().isClientSide() && !player.isSpectator();
    }

    private static void giveStartingItems(ServerPlayer player) {
        int count = IAmZombieConfig.STARTING_ROTTEN_FLESH.get();
        if (count > 0) {
            player.addItem(new ItemStack(Items.ROTTEN_FLESH, count));
        }
    }

    private static void unlockCoffinRecipes(ServerPlayer player) {
        if (!IAmZombieConfig.UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN.get()) {
            return;
        }
        player.awardRecipesByKey(COFFIN_RECIPES);
        player.sendSystemMessage(Component.translatable("iamzombieq.message.coffin.recipes_unlocked"));
    }

    private static void warnIfPeacefulUnsupported(ServerPlayer player) {
        // Authoritative "is gameplay enabled?" check; deterministic because the server-side guard (PeacefulGuard +
        // MinecraftServerMixin + the startup correction) keeps the live difficulty out of Peaceful.
        if (DifficultyGuardRules.isGameplayEnabled(gameDifficulty(player.level().getDifficulty()))) {
            return;
        }
        player.sendSystemMessage(Component.translatable("iamzombieq.message.peaceful_unsupported"));
        if (!peacefulWarningLogged) {
            IAmZombieMod.LOGGER.warn("Peaceful difficulty is not supported by {}. Core zombie gameplay is not guaranteed.", IAmZombieMod.ENGLISH_NAME);
            peacefulWarningLogged = true;
        }
    }

    /**
     * Per-tick form-attribute refresh. Skips re-applying the (idempotent) modifiers when the player's
     * (form, size, world difficulty) signature is unchanged since the last apply — the common per-tick no-op.
     * Use {@link #refreshFormAttributesForced} at every event site where the entity is fresh / has had its
     * transient modifiers cleared (login, respawn/clone, evolution, giant-kill), because such a reapply may be
     * needed even when the signature has NOT changed.
     */
    private static void refreshFormAttributes(ServerPlayer player, PlayerZombieData data) {
        long signature = formAttributeSignature(player, data);
        Long previous = FORM_ATTRIBUTE_SIGNATURE.get(player.getUUID());
        if (previous != null && previous == signature) {
            return;
        }
        applyFormAttributes(player, data);
        FORM_ATTRIBUTE_SIGNATURE.put(player.getUUID(), signature);
    }

    /**
     * Unconditional form-attribute refresh for event sites where the entity is fresh / modifier-less or the
     * signature may not change despite needing a reapply (login, respawn/clone, in-place evolution, giant-kill).
     * Clears the cached signature first so the apply always runs, then records the fresh signature.
     */
    private static void refreshFormAttributesForced(ServerPlayer player, PlayerZombieData data) {
        FORM_ATTRIBUTE_SIGNATURE.remove(player.getUUID());
        applyFormAttributes(player, data);
        FORM_ATTRIBUTE_SIGNATURE.put(player.getUUID(), formAttributeSignature(player, data));
    }

    // Packs the (form, size, difficulty) signature that determines the form-attribute modifier values. Each
    // component is a small enum ordinal, so they pack losslessly into a single long for a cheap per-tick compare.
    private static long formAttributeSignature(ServerPlayer player, PlayerZombieData data) {
        long form = data.state().form().ordinal();
        long size = data.state().size().ordinal();
        long difficulty = player.level().getDifficulty().ordinal();
        return (form << 16) | (size << 8) | difficulty;
    }

    private static void applyFormAttributes(ServerPlayer player, PlayerZombieData data) {
        applyAddValueModifier(player.getAttribute(Attributes.ARMOR), INNATE_ARMOR_ID, IAmZombieConfig.configuredInnateArmor(data.state().form()));
        applyAddValueModifier(player.getAttribute(Attributes.SCALE), BABY_SCALE_ID, data.state().size() == dev.molang.iamzombieq.rules.core.ZombieSize.BABY ? -0.5 : 0.0);
        applyMultipliedBaseModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), BABY_SPEED_ID, data.state().size() == dev.molang.iamzombieq.rules.core.ZombieSize.BABY ? 0.5 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.SUBMERGED_MINING_SPEED), DROWNED_MINING_ID, data.state().form() == ZombieForm.DROWNED ? 0.8 : 0.0);
        // Giant identity (设计指南 §2.4): the SCALE attribute does NOT auto-scale reach/step/attack, so each below
        // is an explicit delta applied as its own modifier (vanilla bases: scale 1.0, block-reach 4.5, entity-reach
        // 3.0, step 0.6, attack 1.0). Modifiers are zeroed-out (removed) for every non-giant form.
        boolean giant = data.state().form() == ZombieForm.GIANT;
        if (!giant) {
            // Leaving the giant form: drop the sweep anchor + swing cooldown here (this runs once per form change via
            // the signature-cached refresh) rather than leaking them in the maps until the player logs out.
            GIANT_LAST_POS.remove(player.getUUID());
            GIANT_SWING_COOLDOWN.remove(player.getUUID());
        }
        applyAddValueModifier(player.getAttribute(Attributes.MAX_HEALTH), GIANT_HEALTH_ID, giant ? ZombieBalanceRules.maxHealth(ZombieForm.GIANT) - 20.0 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.SCALE), GIANT_SCALE_ID, giant ? 5.0 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), GIANT_BLOCK_RANGE_ID, giant ? ZombieBalanceRules.giantBlockInteractionRange() - 4.5 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), GIANT_ENTITY_RANGE_ID, giant ? ZombieBalanceRules.giantEntityInteractionRange() - 3.0 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.STEP_HEIGHT), GIANT_STEP_HEIGHT_ID, giant ? ZombieBalanceRules.giantStepHeight() - 0.6 : 0.0);
        applyAddValueModifier(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), GIANT_SAFE_FALL_ID, giant ? ZombieBalanceRules.giantSafeFallBonus() : 0.0);
        // Attack damage: the giant's punch is the vanilla Giant's flat 50 (constant, NOT difficulty-scaled); the
        // difficulty-scaled bonus applies to every OTHER form. Player base attack is 1.0, so the giant delta is 49.
        applyAddValueModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), GIANT_ATTACK_ID, giant ? ZombieBalanceRules.giantAttackDamage() - 1.0 : 0.0);
        applyMultipliedBaseModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                DIFFICULTY_ATTACK_DAMAGE_ID,
                giant ? 0.0 : ZombieDamageRules.attackDamageBonusFraction(gameDifficulty(player.level().getDifficulty()))
        );
    }

    private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    private static void applyPassiveFormAbilities(ServerPlayer player, PlayerZombieData data) {
        if (data.state().form() == ZombieForm.DROWNED) {
            player.setAirSupply(player.getMaxAirSupply());
            // Refresh before the duration drops into vanilla's <=200-tick night-vision pulse window, to avoid flicker.
            // Drowned see clearly in any wet state (touching water OR in rain), not just fully submerged.
            MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
            if (player.isInWaterOrRain() && (nightVision == null || nightVision.getDuration() < 220)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 15, 0, true, false, false));
            }
        }
        if (ZombieBalanceRules.hasFireResistance(data.state().form())) {
            // Refresh BEFORE the 260-tick (20*13) effect drains, mirroring the drowned night-vision pattern: with a
            // 40-tick margin (<220) a brief lapse can never expose the zombified piglin to a fire tick. The previous
            // !hasEffect guard waited for the effect to fully expire, leaving a one-tick coverage gap each cycle.
            MobEffectInstance fireResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
            if (fireResistance == null || fireResistance.getDuration() < 220) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 13, 0, true, false, false));
                player.clearFire();
            }
        }
    }

    /**
     * Official zombie-reinforcement applied to the zombie PLAYER, fired on each damage-by-living-entity event. Mirrors
     * vanilla {@code Zombie#hurtServer}: (1) ALERT nearby form-matched undead onto the attacker even without line of
     * sight, and (2) on HARD + {@code doMobSpawning} attempt to spawn matching-FORM reinforcements (capped, mob-cap
     * ignoring). The giant form has no vanilla counterpart and does neither.
     */
    private static void reinforceZombiePlayer(ServerPlayer player, LivingEntity attacker) {
        if (!(player.level() instanceof ServerLevel level) || attacker == player) {
            return;
        }
        if (!IAmZombieConfig.REINFORCEMENTS_ENABLED.get()) {
            return;
        }
        ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
        if (!ZombieReinforcementRules.hasReinforcementForm(form)) {
            return;
        }
        EntityType<? extends Mob> reinforcementType = reinforcementTypeFor(form);
        if (reinforcementType == null) {
            return;
        }

        alertFormMatchedUndead(level, player, attacker, reinforcementType);
        attemptSpawnReinforcements(level, player, attacker, reinforcementType);
    }

    /**
     * Retarget every alive, form-matched undead in the ~111x21x111 alert box onto the attacker, even without line of
     * sight (vanilla reinforcement retargeting). Uses a single class-filtered AABB scan (not a per-block sweep). The
     * zombified piglin (a neutral mob) needs persistent anger established before setTarget; the always-hostile zombie
     * family is retargeted directly.
     */
    private static void alertFormMatchedUndead(ServerLevel level, ServerPlayer player, LivingEntity attacker,
            EntityType<? extends Mob> reinforcementType) {
        AABB area = player.getBoundingBox().inflate(
                ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ,
                ZombieReinforcementRules.ALERT_BOX_INFLATE_Y,
                ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ);
        // Exclude the attacker itself: when a form-matched undead (e.g. a zombie) hurts the zombie player, the
        // attacker is also a reinforcement-type mob in range, and without this guard it would be told to
        // setTarget(itself) -> it attacks itself and dies ("the zombie suicides after hitting me"). Genuine kin
        // still rally onto the attacker; only the attacker is spared from targeting itself.
        for (Mob ally : level.getEntitiesOfClass(Mob.class, area, candidate ->
                candidate != attacker && candidate.getType() == reinforcementType && candidate.isAlive() && candidate.canAttack(attacker))) {
            if (ally instanceof ZombifiedPiglin piglin) {
                // Establish anger BEFORE setTarget: setTarget fires LivingChangeTargetEvent, and the
                // undead-ignore-zombie-player handler would otherwise null a target that is a zombie player before
                // anger is recorded (group help would no-op).
                piglin.setPersistentAngerTarget(EntityReference.of(attacker));
                piglin.startPersistentAngerTimer();
            }
            ally.setTarget(attacker);
        }
    }

    /**
     * Try to spawn matching-FORM reinforcements for the zombie player, mirroring vanilla {@code Zombie#hurtServer}:
     * only on HARD difficulty with {@code doMobSpawning} enabled, gated by a per-player reinforcement chance
     * (0-0.1, with possible leader bonus), at offsets (0 or +-7..40 on X/Y/Z), requiring a viable surface (light<=9,
     * solid top, no player within 7, no collision). One successful spawn per damage event; each costs a -0.05 penalty.
     * Reinforcements ignore the mob cap (direct {@code addFreshEntityWithPassengers}).
     */
    private static void attemptSpawnReinforcements(ServerLevel level, ServerPlayer player, LivingEntity attacker,
            EntityType<? extends Mob> reinforcementType) {
        if (!ZombieReinforcementRules.canSpawnReinforcements(gameDifficulty(level.getDifficulty()), level.isSpawningMonsters())) {
            return;
        }
        var random = player.getRandom();
        double chance = playerReinforcementChance(player, level, random);
        if (!ZombieReinforcementRules.reinforcementRollSucceeds(random.nextFloat(), chance)) {
            return;
        }

        Mob reinforcement = reinforcementType.create(level, EntitySpawnReason.REINFORCEMENT);
        if (reinforcement == null) {
            return;
        }
        // A player has no vanilla baby age, so a baby-FORM zombie player must be read from its size state.
        boolean baby = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().size() == dev.molang.iamzombieq.rules.core.ZombieSize.BABY
                && reinforcement instanceof net.minecraft.world.entity.monster.zombie.Zombie;
        int originX = net.minecraft.util.Mth.floor(player.getX());
        int originY = net.minecraft.util.Mth.floor(player.getY());
        int originZ = net.minecraft.util.Mth.floor(player.getZ());

        int attempts = IAmZombieConfig.REINFORCEMENT_SPAWN_ATTEMPTS.get();
        for (int i = 0; i < attempts; i++) {
            int xt = originX + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            int yt = originY + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            int zt = originZ + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            BlockPos spawnPos = new BlockPos(xt, yt, zt);
            // Solid top surface + light<=9 are enforced by the vanilla spawn-placement + spawn-rules checks for the
            // type (the testable predicate ZombieReinforcementRules.isSpawnPositionViable documents the contract).
            if (!net.minecraft.world.entity.SpawnPlacements.isSpawnPositionOk(reinforcementType, level, spawnPos)
                    || !net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(reinforcementType, level, EntitySpawnReason.REINFORCEMENT, spawnPos, level.getRandom())) {
                continue;
            }
            reinforcement.setPos(xt, yt, zt);
            if (level.hasNearbyAlivePlayer(xt, yt, zt, ZombieReinforcementRules.MIN_PLAYER_DISTANCE)
                    || !level.isUnobstructed(reinforcement)
                    || !level.noCollision(reinforcement)) {
                continue;
            }
            if (reinforcement instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                zombie.setBaby(baby);
            }
            reinforcement.finalizeSpawn(level, level.getCurrentDifficultyAt(reinforcement.blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
            reinforcement.setTarget(attacker);
            // Reinforcements ignore the mob cap: a direct add (not a natural-spawn-gated one).
            level.addFreshEntityWithPassengers(reinforcement);
            // Apply the caller penalty (-0.05) so chained calls quickly stop spawning, matching vanilla decay.
            applyReinforcementPenalty(player.getUUID());
            break;
        }
    }

    private static int reinforcementMagnitude(net.minecraft.util.RandomSource random) {
        return net.minecraft.util.Mth.nextInt(random,
                ZombieReinforcementRules.REINFORCEMENT_RANGE_MIN,
                ZombieReinforcementRules.REINFORCEMENT_RANGE_MAX);
    }

    private static int reinforcementSign(net.minecraft.util.RandomSource random) {
        return net.minecraft.util.Mth.nextInt(random, -1, 1);
    }

    /**
     * The per-player reinforcement chance (vanilla SPAWN_REINFORCEMENTS_CHANCE). Lazily rolled once per player into a
     * tracked per-UUID map (0-0.1, plus a regional-difficulty-scaled leader bonus of +0.5..0.75 and 40-100 max HP),
     * then decayed by -0.05 per successful spawn. Stored off-entity because PlayerZombieData has no room for it.
     */
    private static double playerReinforcementChance(ServerPlayer player, ServerLevel level, net.minecraft.util.RandomSource random) {
        UUID uuid = player.getUUID();
        Double existing = REINFORCEMENT_CHANCE.get(uuid);
        if (existing != null) {
            return existing;
        }
        double chance = ZombieReinforcementRules.baseReinforcementChance(random.nextDouble());
        // Vanilla leader chance = specialMultiplier (0..1) * 0.05, capping leaders at ~5% on the hardest regional
        // difficulty — matching Zombie#handleAttributes (not the larger effective-difficulty value).
        double regionalDifficulty = level.getCurrentDifficultyAt(player.blockPosition()).getSpecialMultiplier();
        if (ZombieReinforcementRules.isLeader(regionalDifficulty, random.nextFloat())) {
            chance += ZombieReinforcementRules.leaderReinforcementBonus(random.nextDouble());
        }
        REINFORCEMENT_CHANCE.put(uuid, chance);
        return chance;
    }

    private static void applyReinforcementPenalty(UUID uuid) {
        Double current = REINFORCEMENT_CHANCE.get(uuid);
        double base = current != null ? current : 0.0;
        REINFORCEMENT_CHANCE.put(uuid, ZombieReinforcementRules.applyReinforcementPenalty(base));
    }

    private static EntityType<? extends Mob> reinforcementTypeFor(ZombieForm form) {
        return switch (form) {
            case NORMAL -> EntityTypes.ZOMBIE;
            case DROWNED -> EntityTypes.DROWNED;
            case HUSK -> EntityTypes.HUSK;
            case ZOMBIFIED_PIGLIN -> EntityTypes.ZOMBIFIED_PIGLIN;
            case GIANT -> null;
        };
    }

    private static void handleGiantTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            GIANT_LAST_POS.remove(player.getUUID());
            return;
        }
        // Passive walk-destruction runs EVERY tick (碰哪哪坏): soft blocks in the body's sweep are cleared before the
        // movement collision resolves, so the giant never jams on them; hard/immune blocks survive and stop it.
        smashBlocksWhileWalking(level, player);
        // The stomp aura (area damage) stays on the cheaper 1-second cadence.
        if (player.tickCount % 20 == 0) {
            damageNearbyAsGiant(level, player);
        }
    }

    private static void damageNearbyAsGiant(ServerLevel level, ServerPlayer player) {
        double radius = ZombieBalanceRules.giantAutoDamageRadius();
        AABB area = player.getBoundingBox().inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target ->
                target != player
                        && target.isAlive()
                        && !target.isSpectator()
                        && target != player.getVehicle()
                        && !player.hasPassenger(target)
                        && !isOwnedSpiderMount(target, player))) {
            target.hurtServer(level, player.damageSources().playerAttack(player), (float) ZombieBalanceRules.giantAutoDamageAmount());
        }
    }

    private static boolean isOwnedSpiderMount(LivingEntity target, ServerPlayer player) {
        return target instanceof net.minecraft.world.entity.monster.spider.Spider spider
                && spider.getData(IAmZombieAttachments.SPIDER_MOUNT).isOwnedBy(player.getUUID());
    }

    // The giant's passive walk-destruction: crush the SOFT blocks (GIANT_SOFT tag / very-soft fallback) the scaled
    // body sweeps through, never the foot layer. The sweep volume spans last->current position so a sprinting giant
    // (>0.5 block/tick) doesn't phase past blocks. No drops (跑一路掉物 = 崩服 + 刷物). Hard/immune blocks survive and
    // naturally stop the giant — the "碾村但被天然大山挡住" balance valve.
    private static void smashBlocksWhileWalking(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        Vec3 now = player.position();
        Vec3 last = GIANT_LAST_POS.getOrDefault(id, now);
        GIANT_LAST_POS.put(id, now);
        if (now.distanceToSqr(last) < 0.05 * 0.05) {
            return;
        }
        AABB rawBody = player.getBoundingBox();
        double footY = rawBody.minY;
        AABB sweep = rawBody.inflate(
                        ZombieBalanceRules.giantPassiveReachHorizontal(),
                        ZombieBalanceRules.giantPassiveReachVertical(),
                        ZombieBalanceRules.giantPassiveReachHorizontal())
                .expandTowards(last.x - now.x, last.y - now.y, last.z - now.z);
        crushGiantBlocks(level, player, BlockPos.betweenClosed(sweep), footY, true,
                ZombieBalanceRules.giantPassiveDestroyCapPerTick(), ZombieBalanceRules.GIANT_PASSIVE_MAX_HARDNESS, false);
    }

    /**
     * The unified giant destruction kernel (设计指南 §4.1): crush a batch of positions, filtering air / fluids /
     * block entities / the GIANT_IMMUNE blacklist, keeping the GIANT_SOFT whitelist plus a hardness fallback, and
     * (for passive contact) preserving the foot layer. Removes blocks with {@code setBlock(AIR, flag 34)} =
     * UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS, so there are NO item drops and NO neighbour (redstone/physics)
     * cascades — the only safe way to delete blocks at giant scale. Returns the number destroyed.
     */
    private static int crushGiantBlocks(ServerLevel level, ServerPlayer player, Iterable<BlockPos> positions,
                                        double footY, boolean preserveFootLayer, int cap, float maxHardness,
                                        boolean dropToInventory) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int destroyed = 0;
        for (BlockPos pos : positions) {
            if (destroyed >= cap) {
                break;
            }
            if (preserveFootLayer && !ZombieBalanceRules.giantDestroysBlockLayer(pos.getY(), footY)) {
                continue;
            }
            cursor.set(pos);
            BlockState state = level.getBlockState(cursor);
            float destroySpeed = state.isAir() ? 0.0F : state.getDestroySpeed(level, cursor);
            if (!ZombieBalanceRules.giantCanCrush(
                    state.isAir(),
                    state.hasBlockEntity(),
                    !state.getFluidState().isEmpty(),
                    state.is(IAmZombieBlockTags.GIANT_SOFT),
                    state.is(IAmZombieBlockTags.GIANT_IMMUNE),
                    destroySpeed,
                    maxHardness)) {
                continue;
            }
            if (dropToInventory) {
                // The active swing rakes loot into the giant's pack; overflow is discarded (NOT scattered — a litter
                // of drops at giant scale risks lag/dupe). The setBlock(flag 34) below never drops anything itself.
                for (ItemStack drop : Block.getDrops(state, level, cursor.immutable(), level.getBlockEntity(cursor))) {
                    player.getInventory().add(drop);
                }
            }
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            destroyed++;
        }
        return destroyed;
    }

    @SubscribeEvent
    public static void onGiantSwing(PlayerInteractEvent.LeftClickBlock event) {
        // The giant's active 一拳一大片: a left-click on a block within its long reach blasts a cube centred on the
        // aimed block. Server-authoritative (ServerPlayer only), gated to the START of the click and a cooldown.
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !shouldApplyZombieRules(player)
                || player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form() != ZombieForm.GIANT) {
            return;
        }
        long now = level.getGameTime();
        Long cooldownUntil = GIANT_SWING_COOLDOWN.get(player.getUUID());
        if (cooldownUntil != null && now < cooldownUntil) {
            return;
        }
        BlockPos center = event.getPos();
        // Server-side reach validation: reject a block beyond the giant's (already-extended) block reach.
        double reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        if (player.getEyePosition(1.0F).distanceToSqr(Vec3.atCenterOf(center)) > (reach + 1.0) * (reach + 1.0)) {
            return;
        }
        int half = ZombieBalanceRules.giantSwingCubeEdge() / 2;
        List<BlockPos> cube = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, -half, -half), center.offset(half, half, half))) {
            cube.add(pos.immutable());
        }
        // Crush the blocks nearest the impact first, up to the per-swing cap; the punch breaks stone/ores (high
        // hardness cap) but never obsidian/bedrock/containers, and the loot rakes into the giant's pack.
        cube.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        // A committed swing always starts the cooldown — even one that only struck immune/obsidian blocks — so the
        // giant can't be turned into an infinite instant-miner by clicking unbreakable targets (设计指南 §4.3).
        GIANT_SWING_COOLDOWN.put(player.getUUID(), now + ZombieBalanceRules.giantSwingCooldownTicks());
        crushGiantBlocks(level, player, cube, 0.0, false,
                ZombieBalanceRules.giantSwingMaxBlocks(), ZombieBalanceRules.GIANT_SWING_MAX_HARDNESS, true);
    }

    private static void applyAddValueModifier(AttributeInstance attribute, Identifier id, double amount) {
        applyModifier(attribute, id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applyMultipliedTotalModifier(AttributeInstance attribute, Identifier id, double amount) {
        applyModifier(attribute, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void applyMultipliedBaseModifier(AttributeInstance attribute, Identifier id, double amount) {
        applyModifier(attribute, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void applyModifier(AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        if (amount == 0.0) {
            attribute.removeModifier(id);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static DeathTrigger triggerFrom(DamageSource source) {
        return ZombieDamageRules.triggerFromDamageTypeId(damageTypeId(source));
    }

    private static String damageTypeId(DamageSource source) {
        return source.typeHolder()
                .unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("");
    }

    private static boolean isSunBurnTick(ServerPlayer player) {
        boolean monstersBurn = player.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position());
        float brightness = player.getLightLevelDependentMagicValue();
        // Preserve the vanilla RNG short-circuit: the random tick chance is only sampled once the monster-burn and
        // brightness preconditions pass. Draw nextFloat() only behind that gate so the float overload (no per-tick
        // capturing lambda) consumes the player's random source exactly as the previous DoubleSupplier did.
        if (!monstersBurn || brightness <= 0.5F) {
            return false;
        }
        float randomFloat = player.getRandom().nextFloat();
        SUN_BURN_EYE_POS.set(player.getX(), player.getEyeY(), player.getZ());
        boolean canSeeSky = player.level().canSeeSky(SUN_BURN_EYE_POS);
        boolean inWaterRainOrPowderSnow = player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow;
        return ZombieSunlightRules.isVanillaSunBurnTick(
                monstersBurn,
                brightness,
                randomFloat,
                canSeeSky,
                inWaterRainOrPowderSnow
        );
    }

    private static boolean replaceSunlightFireDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !shouldApplyZombieRules(player)
                || event.getAmount() <= 0.0F) {
            return false;
        }

        boolean sourceIsOnFire = event.getSource().is(DamageTypes.ON_FIRE);
        Long sunlightFireUntil = SUNLIGHT_FIRE_UNTIL.get(player.getUUID());
        boolean withinSunlightFireWindow = sunlightFireUntil != null && player.level().getGameTime() <= sunlightFireUntil;
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        HeadProtection headProtection = classifyHeadProtection(player.getItemBySlot(EquipmentSlot.HEAD));
        boolean formBurnsInSunlight = ZombieSunlightRules.shouldBurn(data.state().form(), true, headProtection);
        if (!ZombieDamageRules.shouldConvertOnFireDamageToSunlight(sourceIsOnFire, withinSunlightFireWindow, formBurnsInSunlight)) {
            return false;
        }

        // Re-attribute this vanilla on-fire tick to the sunlight death type: cancel it and re-deal the same amount
        // as iamzombieq:sunlight (which is in minecraft:no_knockback, so no knockback / directional hurt indicator).
        event.setCanceled(true);
        player.hurtServer(player.level(), player.damageSources().source(SUNLIGHT_DAMAGE), event.getAmount());
        return true;
    }

    private static void igniteSunlightBurn(ServerPlayer player) {
        player.igniteForSeconds(8.0F);
        // Mark the resulting fire as sun-sourced for as long as it will burn, so its on-fire ticks are re-attributed
        // to sunlight. Refreshed every sun-burn tick; vanilla fire handles the actual burn timing.
        SUNLIGHT_FIRE_UNTIL.put(player.getUUID(), player.level().getGameTime() + player.getRemainingFireTicks());
    }

    private static HeadProtection classifyHeadProtection(ItemStack headStack) {
        if (headStack.isEmpty()) {
            return HeadProtection.NONE;
        }
        // The disguise mask is a cloth rag, not protective headgear: it must NOT block sunlight. Special-case it
        // before the pumpkin/helmet checks so a masked zombie still burns in the sun like a bare-headed one.
        if (headStack.is(IAmZombieItems.DISGUISE_MASK.get())) {
            return HeadProtection.NONE;
        }
        if (headStack.is(Items.CARVED_PUMPKIN) || headStack.is(IAmZombieItems.HEROBRINE_HEAD.get())) {
            return HeadProtection.PUMPKIN;
        }
        return HeadProtection.OTHER_HELMET;
    }

    private static BiomeContext biomeContext(ServerPlayer player) {
        var biomeHolder = player.level().getBiome(player.blockPosition());
        if (biomeHolder.is(Biomes.DESERT)) {
            return BiomeContext.DESERT;
        }

        Biome biome = biomeHolder.value();
        boolean hotDryNonDesert = biomeHolder.is(BiomeTags.HAS_DESERT_PYRAMID)
                || biomeHolder.is(BiomeTags.HAS_VILLAGE_DESERT)
                || biomeHolder.is(BiomeTags.IS_BADLANDS)
                || (biome.getBaseTemperature() >= 1.8F && !biome.hasPrecipitation());
        return hotDryNonDesert ? BiomeContext.HOT_DRY_NON_DESERT : BiomeContext.OTHER;
    }

    private static DimensionContext dimensionContext(ServerPlayer player) {
        if (player.level().dimension() == Level.NETHER) {
            return DimensionContext.NETHER;
        }
        if (player.level().dimension() == Level.OVERWORLD) {
            return DimensionContext.OVERWORLD;
        }
        return DimensionContext.OTHER;
    }

    private static PlayerZombieData grantFirstEvolutionReward(
            ServerPlayer player,
            PlayerZombieData before,
            PlayerZombieData after,
            EvolutionResult result
    ) {
        return switch (result.outcome()) {
            case EVOLVE_TO_DROWNED -> {
                if (!before.receivedFirstDrownedReward() && after.state().form() == ZombieForm.DROWNED) {
                    player.addItem(new ItemStack(Items.TRIDENT));
                    yield after.withFirstDrownedRewardClaimed();
                }
                yield after;
            }
            case EVOLVE_TO_HUSK -> {
                if (!before.receivedFirstHuskReward() && after.state().form() == ZombieForm.HUSK) {
                    grantHuskDesertReward(player);
                    yield after.withFirstHuskRewardClaimed();
                }
                yield after;
            }
            case EVOLVE_TO_ZOMBIFIED_PIGLIN -> {
                if (!before.receivedFirstZombifiedPiglinReward() && after.state().form() == ZombieForm.ZOMBIFIED_PIGLIN) {
                    player.addItem(randomEnchantedGoldenSword(player));
                    yield after.withFirstZombifiedPiglinRewardClaimed();
                }
                yield after;
            }
            case EVOLVE_TO_BABY, ORDINARY_DEATH_RESET -> after;
        };
    }

    private static void grantHuskDesertReward(ServerPlayer player) {
        for (ZombieBalanceRules.RewardEntry entry : ZombieBalanceRules.huskFirstRewardBundle(new java.util.Random(player.getRandom().nextLong()))) {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(
                    Identifier.parse(entry.itemId()));
            // The pool only lists vanilla items, but guard against a missing/renamed registry entry so a single
            // bad id can't crash the evolution reward (it resolves to AIR, which we skip).
            if (item == Items.AIR.asItem()) {
                continue;
            }
            player.addItem(new ItemStack(item, entry.count()));
        }
    }

    private static ItemStack randomEnchantedGoldenSword(ServerPlayer player) {
        ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
        Holder<Enchantment> enchantment = player.getRandom().nextBoolean()
                ? player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE)
                : player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        sword.enchant(enchantment, 1 + player.getRandom().nextInt(2));
        return sword;
    }

    /**
     * Whether this in-place evolution is a first-evolution event for the "向死而生" advancement: the player leaves
     * the NORMAL form for any non-normal form. The advancement award is one-time/idempotent, so re-entering this
     * branch after a later ordinary-death reset back to NORMAL does not re-grant it.
     */
    private static boolean isFirstEvolution(
            dev.molang.iamzombieq.rules.core.ZombieState before,
            dev.molang.iamzombieq.rules.core.ZombieState after
    ) {
        return before.form() == ZombieForm.NORMAL && after.form() != ZombieForm.NORMAL;
    }

    private static void awardEvolutionAdvancement(ServerPlayer player, EvolutionResult result) {
        switch (result.outcome()) {
            case EVOLVE_TO_DROWNED -> IAmZombieAdvancements.award(player, IAmZombieAdvancements.DROWNED);
            case EVOLVE_TO_HUSK -> IAmZombieAdvancements.award(player, IAmZombieAdvancements.HUSK);
            case EVOLVE_TO_BABY -> IAmZombieAdvancements.award(player, IAmZombieAdvancements.BABY);
            case EVOLVE_TO_ZOMBIFIED_PIGLIN -> IAmZombieAdvancements.award(player, IAmZombieAdvancements.ZOMBIFIED_PIGLIN);
            case ORDINARY_DEATH_RESET -> {
            }
        }
    }

    private static ResourceKey<Recipe<?>> coffinRecipe(String name) {
        return ResourceKey.create(Registries.RECIPE, ModIds.id(name));
    }
}
