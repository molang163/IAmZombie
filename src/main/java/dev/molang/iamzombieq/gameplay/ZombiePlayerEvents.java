package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.ModIds;
import dev.molang.iamzombieq.util.Difficulties;
import dev.molang.iamzombieq.util.ZombiePlayerGates;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.IAmZombieMod;
import dev.molang.iamzombieq.api.event.ZombieEvolvePreEvent;
import dev.molang.iamzombieq.api.event.ZombieEvolvedEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformPreEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformedEvent;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.internal.logging.ZombieLog;
import dev.molang.iamzombieq.rules.BiomeContext;
import dev.molang.iamzombieq.rules.DeathTrigger;
import dev.molang.iamzombieq.rules.difficulty.DifficultyGuardRules;
import dev.molang.iamzombieq.rules.DimensionContext;
import dev.molang.iamzombieq.rules.EvolutionResult;
import dev.molang.iamzombieq.rules.HeadProtection;
import dev.molang.iamzombieq.rules.SunBurnContext;
import dev.molang.iamzombieq.rules.ZombieDamageRules;
import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.ZombieEvolutionRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieSunlightRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
// CROSS_VERSION-SUN-BURN-ENVIRONMENT-GATE:import
//? if >=1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@SuppressWarnings("deprecation")
public final class ZombiePlayerEvents {
    private static final int POST_EVOLUTION_FOOD_LEVEL = 6;
    // Reused per-tick eye-position scratch for the sky-visibility sun-burn check. onPlayerTick is single-threaded
    // (server thread), so a shared mutable instance is safe and avoids a per-tick BlockPos allocation.
    private static final BlockPos.MutableBlockPos SUN_BURN_EYE_POS = new BlockPos.MutableBlockPos();
    private static boolean peacefulWarningLogged;
    private static final List<ResourceKey<Recipe<?>>> COFFIN_RECIPES = List.of(coffinRecipe("coffin"));

    private ZombiePlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ZombiePlayerGates.isZombiePlayer(player)
                || !player.isAlive()) {
            return;
        }

        // A creative player who has never been a zombie (no attachment) has nothing to run; bail before
        // materializing the default attachment. A creative ZOMBIE (has data) runs the full per-tick logic below
        // just like a survival zombie; only flight and invulnerability remain creative-inherent.
        if (player.isCreative() && !player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);

        ZombieFormAttributes.refreshFormAttributes(player, data);
        applyPassiveFormAbilities(player, data);
        if (data.state().form() == ZombieForm.GIANT) {
            GiantPlayerEvents.handleGiantTick(player);
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
            ZombieSunlightEvents.igniteSunlightBurn(player);
        } else if (ZombieSunlightRules.shouldDamageHeadProtection(data.state().form(), true, headProtection) && headStack.isDamageableItem()) {
            int damage = IAmZombieServerConfig.SUN_PROTECTION_HEADGEAR_DAMAGE.get();
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
        if (ZombieSunlightEvents.replaceSunlightFireDamage(event)) {
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
            ZombieReinforcementEvents.reinforceZombiePlayer(player, attacker);
        }

        if (!(event.getSource().getEntity() instanceof Player player) || !shouldApplyZombieRules(player)) {
            return;
        }
        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        if (data.state().form() == ZombieForm.HUSK
                && event.getSource().getDirectEntity() == player
                && event.getEntity() instanceof LivingEntity target
                && target != player) {
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, ZombieBalanceRules.HUSK_MELEE_HUNGER_DURATION_TICKS, 0), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && shouldApplyZombieRules(player)) {
            boolean firstZombieAttach = !player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE);
            PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            // FORCED: the fresh login entity needs its innate form attributes, and the signature cache entry was
            // cleared on the prior logout — force the apply so login never depends on a stale/absent cache entry.
            ZombieFormAttributes.refreshFormAttributesForced(player, data);
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
        // Drop the cached form-attribute signature so a reconnecting player (whose transient modifiers were
        // cleared with the old entity) re-applies on login via the forced refresh rather than trusting a stale entry.
        // ZombieFormAttributes is a non-event helper class, so its map cleanup is driven from here; the sun-fire,
        // reinforcement, and giant maps are self-cleaned by their own event classes' logout handlers.
        ZombieFormAttributes.clearOnLogout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // ZombieFormAttributes (non-event helper) is cleared from here; the sun-fire, reinforcement, and giant maps
        // are self-cleaned by their own event classes' server-stop handlers.
        ZombieFormAttributes.clearOnServerStop();
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
        boolean formChanged = previous.state().form() != nextData.state().form();
        // A death clone exposes the fresh respawn holder. The immutable event fields, not that holder's absent
        // attachment, carry the authoritative before/after forms. Only a real form reset is cancellable; non-death
        // carry and NORMAL/BABY -> NORMAL/ADULT size-only reset remain outside the Transform lifecycle.
        boolean resetCanceled = formChanged && ZombieEventPublisher.postCancelable(
                new ZombieTransformPreEvent(player, previous.state().form(), nextData.state().form()));
        if (resetCanceled) {
            // Real death has already happened. Preserve the complete previous state/size/reward flags on the fresh
            // holder with the same single write used by the pass path, then refresh retained attributes below.
            nextData = previous;
        }
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
        // Clear the transient sun-fire window on clone (death respawn OR dimension change): it is keyed to the old
        // entity's fire ticks, and a stale window carried onto the fresh entity would mis-attribute the first
        // ordinary fire after respawn (lava/campfire) to the sunlight death type (#8).
        ZombieSunlightEvents.clearSunFireWindow(event.getEntity().getUUID());
        // Post remains after the applied write and its automatic sync, and fires only for an accepted real form
        // reset. A veto still writes retained data to the fresh holder but is not a completed transform.
        if (formChanged && !resetCanceled) {
            ZombieEventPublisher.post(new ZombieTransformedEvent(player, previous.state().form(), nextData.state().form()));
        }
        // FORCED: the respawned entity has had its transient attribute modifiers cleared, and a same-form
        // NORMAL->NORMAL ordinary death leaves the signature unchanged — a cache-gated refresh would skip it.
        ZombieFormAttributes.refreshFormAttributesForced(player, nextData);
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
            if (ZombieEventPublisher.postCancelable(new ZombieTransformPreEvent(
                    killer, data.state().form(), nextData.state().form()))) {
                // The veto controls only the killer's transform. The giant's LivingDeathEvent remains uncanceled.
                return;
            }
            killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
            // Post remains after the single applied write and its automatic sync.
            ZombieEventPublisher.post(new ZombieTransformedEvent(killer, data.state().form(), nextData.state().form()));
            // FORCED: setHealth(getMaxHealth()) below relies on the GIANT max-health modifier being reapplied now,
            // even if the giant->giant signature happens to be unchanged.
            ZombieFormAttributes.refreshFormAttributesForced(killer, nextData);
            killer.setHealth(killer.getMaxHealth());
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || !shouldApplyZombieRules(player)) {
            return;
        }

        // Clear the transient sun-fire window on death so the respawning player does not inherit a stale window
        // that would mis-attribute a later ordinary fire to the sunlight death type (#8).
        ZombieSunlightEvents.clearSunFireWindow(event.getEntity().getUUID());

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

        PlayerZombieData nextData = data.withState(result.nextState());
        if (ZombieEventPublisher.postCancelable(new ZombieEvolvePreEvent(
                player, data.state(), nextData.state(), result.outcome()))) {
            // The veto leaves the outer LivingDeathEvent uncanceled, so the player continues through real death.
            return;
        }
        event.setCanceled(true);
        nextData = grantFirstEvolutionReward(player, data, nextData, result);
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData);
        // Post remains after the reward and single applied state+flag write, but before attributes, passives,
        // advancements, and recovery.
        ZombieEventPublisher.post(new ZombieEvolvedEvent(player, data.state(), nextData.state(), result.outcome()));
        // FORCED: setHealth(getMaxHealth()*0.5) below relies on the just-evolved form's max-health modifier being
        // reapplied now; the in-place respawn must restore innate attributes regardless of signature equality.
        ZombieFormAttributes.refreshFormAttributesForced(player, nextData);
        applyPassiveFormAbilities(player, nextData);
        awardEvolutionAdvancement(player, result);
        if (isFirstEvolution(data.state(), nextData.state())) {
            IAmZombieAdvancements.award(player, IAmZombieAdvancements.FIRST_EVOLUTION);
        }

        player.setHealth(Math.max(1.0F, player.getMaxHealth() * ZombieBalanceRules.EVOLUTION_RESPAWN_HEALTH_FRACTION));
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.resetFallDistance();
        player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), POST_EVOLUTION_FOOD_LEVEL));
        player.getFoodData().setSaturation(0.0F);
        // Logs result.nextState() (not nextData.state()) so the lambda never captures the reassigned `nextData`.
        ZombieLog.debug(() -> "state.evolution uuid=" + player.getUUID()
                + " from=" + data.state() + " to=" + result.nextState() + " outcome=" + result.outcome());
    }

    public static boolean shouldApplyZombieRules(Player player) {
        // Creative zombie players run all server-side zombie rules (only flight and invulnerability remain
        // creative-inherent). Keep the server-side + non-spectator gates.
        return ZombiePlayerGates.isServerZombiePlayer(player);
    }

    private static void giveStartingItems(ServerPlayer player) {
        int count = IAmZombieServerConfig.STARTING_ROTTEN_FLESH.get();
        if (count > 0) {
            player.addItem(new ItemStack(Items.ROTTEN_FLESH, count));
        }
    }

    private static void unlockCoffinRecipes(ServerPlayer player) {
        if (!IAmZombieServerConfig.UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN.get()) {
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

    private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    private static void applyPassiveFormAbilities(ServerPlayer player, PlayerZombieData data) {
        if (data.state().form() == ZombieForm.DROWNED) {
            player.setAirSupply(player.getMaxAirSupply());
            // Refresh before the duration drops into vanilla's <=200-tick night-vision pulse window, to avoid flicker.
            // Drowned see clearly in any wet state (touching water OR in rain), not just fully submerged.
            if (player.isInWaterOrRain()) {
                refreshEffectIfExpiring(player, MobEffects.NIGHT_VISION, 20 * 15);
            }
        }
        if (ZombieBalanceRules.hasFireResistance(data.state().form())) {
            // Refresh BEFORE the 260-tick (20*13) effect drains, mirroring the drowned night-vision pattern: with a
            // 40-tick margin (<220) a brief lapse can never expose the zombified piglin to a fire tick. The previous
            // !hasEffect guard waited for the effect to fully expire, leaving a one-tick coverage gap each cycle.
            if (refreshEffectIfExpiring(player, MobEffects.FIRE_RESISTANCE, 20 * 13)) {
                player.clearFire();
            }
        }
    }

    private static boolean refreshEffectIfExpiring(
            ServerPlayer player, Holder<MobEffect> effect, int durationTicks) {
        MobEffectInstance activeEffect = player.getEffect(effect);
        if (activeEffect != null
                && activeEffect.getDuration() >= ZombieBalanceRules.EFFECT_REFRESH_MARGIN_TICKS) {
            return false;
        }
        player.addEffect(new MobEffectInstance(effect, durationTicks, 0, true, false, false));
        return true;
    }

    private static DeathTrigger triggerFrom(DamageSource source) {
        return ZombieDamageRules.triggerFromDamageTypeId(damageTypeId(source));
    }

    private static String damageTypeId(DamageSource source) {
        return source.typeHolder()
                .unwrapKey()
                //? if >=1.21.11 {
                .map(key -> key.identifier().toString())
                //?} else {
                /*.map(key -> key.location().toString())
                *///?}
                .orElse("");
    }

    private static boolean isSunBurnTick(ServerPlayer player) {
        // CROSS_VERSION-SUN-BURN-ENVIRONMENT-GATE:value
        //? if >=1.21.11 {
        boolean monstersBurn = player.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position());
        //?} else {
        /*boolean monstersBurn = player.level().isBrightOutside();
        *///?}
        float brightness = player.getLightLevelDependentMagicValue();
        // Preserve the vanilla RNG short-circuit: the random tick chance is only sampled once the monster-burn and
        // brightness preconditions pass. Draw nextFloat() only behind that gate, then pass the sampled value into the
        // context without a per-tick capturing lambda.
        if (!monstersBurn || brightness <= 0.5F) {
            return false;
        }
        double randomValue = player.getRandom().nextFloat();
        SUN_BURN_EYE_POS.set(player.getX(), player.getEyeY(), player.getZ());
        boolean canSeeSky = player.level().canSeeSky(SUN_BURN_EYE_POS);
        boolean inWaterRainOrPowderSnow = player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow;
        SunBurnContext context = new SunBurnContext(
                monstersBurn,
                brightness,
                randomValue,
                canSeeSky,
                inWaterRainOrPowderSnow
        );
        return ZombieSunlightRules.isVanillaSunBurnTick(context);
    }

    static HeadProtection classifyHeadProtection(ItemStack headStack) {
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
        // The "which outcome grants which reward (and only once)" decision is a pure rule; the item side effects
        // (addItem / RNG-seeded bundle / enchanted sword) stay here, byte-for-byte, gated by the resolved kind.
        ZombieEvolutionRules.FirstEvolutionReward reward = ZombieEvolutionRules.resolveFirstEvolutionReward(
                result.outcome(),
                after.state().form(),
                before.receivedFirstDrownedReward(),
                before.receivedFirstHuskReward(),
                before.receivedFirstZombifiedPiglinReward()
        );
        return switch (reward) {
            case TRIDENT -> {
                player.addItem(new ItemStack(Items.TRIDENT));
                yield after.withFirstDrownedRewardClaimed();
            }
            case HUSK_DESERT_BUNDLE -> {
                grantHuskDesertReward(player);
                yield after.withFirstHuskRewardClaimed();
            }
            case ENCHANTED_GOLD_SWORD -> {
                player.addItem(randomEnchantedGoldenSword(player));
                yield after.withFirstZombifiedPiglinRewardClaimed();
            }
            case NONE -> after;
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
