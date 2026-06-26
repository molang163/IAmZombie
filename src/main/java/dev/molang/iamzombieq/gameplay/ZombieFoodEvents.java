package dev.molang.iamzombieq.gameplay;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.api.event.ZombieAteEvent;
import dev.molang.iamzombieq.api.event.ZombieEatPreEvent;
import dev.molang.iamzombieq.api.extension.IFoodRuleProvider;
import dev.molang.iamzombieq.api.extension.IZombieExtensions;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.rules.EffectSpec;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class ZombieFoodEvents {
    private static final Map<UUID, ZombieFoodRules.PreservedFoodPunishments> PENDING_FOOD_PUNISHMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, ZombieFoodRules.PreservedGoldenAppleEffects> PENDING_GOLDEN_APPLE_EFFECTS = new ConcurrentHashMap<>();

    private ZombieFoodEvents() {
    }

    // G7: let a zombie player begin eating the special buff foods (pufferfish/spider eye/poisonous potato and the mod's
    // super rotten flesh) even at a full hunger bar. Vanilla's Consumable.startConsuming returns FAIL before
    // LivingEntity.startUsingItem when Player.canEat(false) is false, so the use never even reaches the
    // LivingEntityUseItemEvent.Start handler above. We intercept the server-side right-click, manually start the
    // multi-tick eat ourselves, and short-circuit vanilla's Item.use with a CONSUME cancellation result. No vanilla
    // item / FoodProperties is mutated; the mod's super rotten flesh is already always-edible via its registration,
    // but is handled here too so the behavior is uniform for all four ids.
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (!shouldProcessZombieFood(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!ZombieFoodRules.isAlwaysEdibleForZombies(itemId(stack)) || !isFood(stack)) {
            return;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (food == null || consumable == null) {
            return;
        }
        // Only step in when vanilla would actually refuse (full hunger and not always-edible/invulnerable); otherwise
        // leave the normal eat path untouched so the Start/Finish buff handling runs exactly as before.
        if (player.canEat(food.canAlwaysEat())) {
            return;
        }
        if (player.isUsingItem() || player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        if (consumable.consumeTicks() <= 0) {
            // Defensive: the four target foods all consume over time today, but if one were ever instant, applying it
            // directly here would bypass the Start/Finish buff substitution, so just let vanilla handle it.
            return;
        }
        // Begin the multi-tick eat exactly like Consumable.startConsuming's over-time branch; the Start/Tick/Finish
        // events then fire normally and the buff substitution + vanilla-side-effect removal run as usual.
        player.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    // Cake (and candle cake) is eaten as a BLOCK via CakeBlock#useWithoutItem, never as an ItemStack, so it never fires
    // LivingEntityUseItemEvent and its T3-sweet zombie-food debuff (Hunger II + Nausea + Slowness) was silently skipped.
    // We mirror the cake's own eat gate here on the server-side right-click of the block and apply the same human-food
    // punishment + zombie effects the finished-eat handler applies for an ItemStack food. We do NOT cancel the event, so
    // vanilla still runs its own eat (eats the slice, plays sound, advances BITES); we only add the missing zombie rules.
    @SubscribeEvent
    public static void onRightClickCakeBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (!shouldProcessZombieFood(player)) {
            return;
        }
        BlockState clickedState = event.getLevel().getBlockState(event.getPos());
        if (!(clickedState.getBlock() instanceof CakeBlock) && !(clickedState.getBlock() instanceof CandleCakeBlock)) {
            return;
        }
        // Vanilla CakeBlock.eat() refuses at a full hunger bar (canEat(false)); match it so we never punish a no-op click.
        if (!player.canEat(false)) {
            return;
        }
        // No slice left to eat means vanilla will not actually eat, so do not apply the debuff. A candle cake always has a
        // whole cake underneath (no BITES property), so only the plain CakeBlock can ever be at its last/empty state.
        if (clickedState.hasProperty(CakeBlock.BITES) && clickedState.getValue(CakeBlock.BITES) >= CakeBlock.MAX_BITES) {
            return;
        }
        if (!appliesFullZombieFoodRules(player)) {
            return;
        }

        FoodRule rule = ZombieFoodRules.ruleForStack(ItemStack.EMPTY, "minecraft:cake", configuredZombieFoods());
        ZombieFoodRules.HumanFoodPunishmentSettings punishmentSettings = ZombieFoodRules.humanFoodPunishmentSettings(
                IAmZombieConfig.HUMAN_FOOD_NAUSEA_DURATION_TICKS.get(),
                IAmZombieConfig.HUMAN_FOOD_HUNGER_DURATION_TICKS.get(),
                IAmZombieConfig.HUMAN_FOOD_HUNGER_AMPLIFIER.get()
        );
        if (rule.appliesHumanFoodPunishment()) {
            applyHumanFoodPunishment(player, punishmentSettings);
            if (player instanceof ServerPlayer serverPlayer) {
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.HUMAN_FOOD);
            }
        }
        applyZombieEffects(player, rule, "minecraft:cake");
        // Intentionally NOT cancelled: vanilla CakeBlock#useWithoutItem still eats the slice as usual.
    }

    @SubscribeEvent
    public static void onItemUseStarted(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player) || !shouldProcessZombieFood(player)) {
            return;
        }

        ItemStack eaten = event.getItem();
        String eatenId = itemId(eaten);
        if (!ZombieFoodRules.isFoodRuleTarget(eatenId) || !isFood(eaten)) {
            return;
        }
        // In creative the survival bookkeeping (human-food punishment / arbitrary food handling) makes no sense, so
        // only the special always-edible zombie foods get their buff substitution + vanilla-side-effect removal there.
        if (!appliesFullZombieFoodRules(player) && !ZombieFoodRules.isAlwaysEdibleForZombies(eatenId)) {
            return;
        }

        PENDING_FOOD_PUNISHMENTS.put(player.getUUID(), preserveExistingFoodPunishments(player));
        if (resolveFoodRule(player, eaten, eatenId).suppressesVanillaPositiveEffects()) {
            PENDING_GOLDEN_APPLE_EFFECTS.put(player.getUUID(), preserveExistingGoldenAppleEffects(player));
        }
    }

    @SubscribeEvent
    public static void onItemUseFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!shouldProcessZombieFood(player)) {
            // e.g. switched to spectator mid-eat: drop any Start snapshot instead of leaking it.
            clearPendingFoodSnapshots(player);
            return;
        }

        ItemStack eaten = event.getItem();
        String eatenItemId = itemId(eaten);
        // Mirror the Start-side creative filter: outside the full-rules modes only the special always-edible foods are
        // handled (and snapshotted), so drop any stale snapshot and bail for everything else to avoid leaking it.
        if (!appliesFullZombieFoodRules(player) && !ZombieFoodRules.isAlwaysEdibleForZombies(eatenItemId)) {
            clearPendingFoodSnapshots(player);
            return;
        }
        ZombieFoodRules.PreservedFoodPunishments preserved = PENDING_FOOD_PUNISHMENTS.remove(player.getUUID());
        ZombieFoodRules.PreservedGoldenAppleEffects preservedGoldenAppleEffects = PENDING_GOLDEN_APPLE_EFFECTS.remove(player.getUUID());
        if (!ZombieFoodRules.isFoodRuleTarget(eatenItemId) || !isFood(eaten)) {
            return;
        }

        FoodRule rule = resolveFoodRule(player, eaten, eatenItemId);
        // ADDITIVE (Phase-1 API): cancellable PRE fire on the item-eat path BEFORE any zombie-food effect applies,
        // carrying the real eaten stack (copied by the event) + resolved rule. If a listener cancels it, skip the
        // whole zombie-food handling for this eat. Server-side only; isolated via ZombieEventPublisher.
        if (player instanceof ServerPlayer prePlayer
                && ZombieEventPublisher.postCancelable(new ZombieEatPreEvent(prePlayer, eaten, rule))) {
            return;
        }
        ZombieFoodRules.HumanFoodPunishmentSettings punishmentSettings = ZombieFoodRules.humanFoodPunishmentSettings(
                IAmZombieConfig.HUMAN_FOOD_NAUSEA_DURATION_TICKS.get(),
                IAmZombieConfig.HUMAN_FOOD_HUNGER_DURATION_TICKS.get(),
                IAmZombieConfig.HUMAN_FOOD_HUNGER_AMPLIFIER.get()
        );
        if (player instanceof ServerPlayer serverPlayer) {
            if (eaten.is(Items.ROTTEN_FLESH)) {
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.ROTTEN_FLESH);
            }
            if (rule.appliesHumanFoodPunishment()) {
                IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.HUMAN_FOOD);
            }
        }
        int elapsedTicks = eaten.getUseDuration(player) - player.getUseItemRemainingTicks();
        if (rule.appliesHumanFoodPunishment()) {
            applyHumanFoodPunishment(player, punishmentSettings);
        } else {
            removeVanillaFoodPunishment(player, preserved, elapsedTicks);
        }
        if (rule.suppressesVanillaPositiveEffects()) {
            removeGoldenAppleEffects(player, preservedGoldenAppleEffects, elapsedTicks);
        }
        applyZombieEffects(player, rule, eatenItemId);
        // ADDITIVE (Phase-1 API): observer POST fire AFTER a successful zombie-food eat on the item path, carrying
        // the REAL eaten stack (copied by the event) + applied rule — for EVERY successful zombie-food eat, not
        // just baby->adult. Server-side only; isolated via ZombieEventPublisher.
        if (player instanceof ServerPlayer atePlayer) {
            ZombieEventPublisher.post(new ZombieAteEvent(atePlayer, eaten, rule));
        }
    }

    @SubscribeEvent
    public static void onItemUseStopped(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player) {
            clearPendingFoodSnapshots(player);
        }
    }

    // Vanilla die() calls stopUsingItem() (no Stop event) and a disconnect mid-eat does not fire Stop either,
    // so clear any pending snapshot on death/logout to avoid a per-UUID leak that never gets consumed.
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            clearPendingFoodSnapshots(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPendingFoodSnapshots(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_FOOD_PUNISHMENTS.clear();
        PENDING_GOLDEN_APPLE_EFFECTS.clear();
    }

    // Whether this player's food use should be inspected at all (server-side, not a spectator). Creative is included
    // here so the special always-edible zombie foods (G1) still get their buff substitution + vanilla-side-effect
    // removal; the creative-vs-survival distinction is applied per-item via {@link #appliesFullZombieFoodRules}.
    private static boolean shouldProcessZombieFood(Player player) {
        return !player.level().isClientSide() && !player.isSpectator();
    }

    // N6: the full zombie food rule set (including the human-food hunger/nausea debuff) now applies even in creative,
    // so being a zombie is consistent across game modes. Only spectators are excluded (already filtered upstream by
    // {@link #shouldProcessZombieFood}). Kept as a named predicate because the food handlers branch on it per item.
    private static boolean appliesFullZombieFoodRules(Player player) {
        return !player.isSpectator();
    }

    private static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    private static Set<String> configuredZombieFoods() {
        return IAmZombieConfig.ZOMBIE_FOODS.get()
                .stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    // ADDITIVE (Phase-1 API): the FOOD extension hook-query (design §5.b / PLAN A3). Addon-registered
    // IFoodRuleProviders are consulted in order, first non-null wins; otherwise this falls through to the existing
    // built-in ZombieFoodRules.ruleForStack(...) call UNCHANGED. The provider list is empty by default
    // (IZombieExtensions, neutral-when-empty), so with no addon present this is behavior-identical to calling
    // ruleForStack directly. Providers take a ServerPlayer, so they are only consulted for a ServerPlayer eater.
    private static FoodRule resolveFoodRule(Player player, ItemStack stack, String itemId) {
        if (player instanceof ServerPlayer serverPlayer) {
            for (IFoodRuleProvider provider : IZombieExtensions.foodRuleProviders()) {
                FoodRule provided = provider.ruleForStack(serverPlayer, stack, itemId);
                if (provided != null) {
                    return provided;
                }
            }
        }
        return ZombieFoodRules.ruleForStack(stack, itemId, configuredZombieFoods());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void clearPendingFoodSnapshots(Player player) {
        PENDING_FOOD_PUNISHMENTS.remove(player.getUUID());
        PENDING_GOLDEN_APPLE_EFFECTS.remove(player.getUUID());
    }

    private static void applyHumanFoodPunishment(Player player, ZombieFoodRules.HumanFoodPunishmentSettings settings) {
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, settings.nauseaDurationTicks(), 0));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, settings.hungerDurationTicks(), settings.hungerAmplifier()));
    }

    private static void removeVanillaFoodPunishment(Player player, ZombieFoodRules.PreservedFoodPunishments preserved, int elapsedTicks) {
        // No Start snapshot (e.g. creative-start/survival-finish) means we cannot tell bite-caused effects from
        // pre-existing ones, so do nothing rather than wiping the player's pre-existing Hunger/Nausea/Poison.
        if (preserved == null) {
            return;
        }
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.POISON);
        restoreFoodPunishments(player, preserved, elapsedTicks);
    }

    private static void removeGoldenAppleEffects(Player player, ZombieFoodRules.PreservedGoldenAppleEffects preserved, int elapsedTicks) {
        if (preserved == null) {
            return;
        }
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.RESISTANCE);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        restoreGoldenAppleEffects(player, preserved, elapsedTicks);
    }

    private static void applyZombieEffects(Player player, FoodRule rule, String eatenId) {
        // 1) Positive buffs from the rule's data table.
        for (EffectSpec spec : rule.buffs()) {
            player.addEffect(spec.toInstance());
        }
        // 2) Negative debuffs from the rule's data table (sweet Slowness, golden-apple Hunger, chorus/honey Nausea).
        for (EffectSpec spec : rule.debuffs()) {
            player.addEffect(spec.toInstance());
        }
        // 3) Poisonous potato's random small positive: chosen at runtime, so it cannot be a static EffectSpec.
        if ("minecraft:poisonous_potato".equals(eatenId)) {
            applyRandomSmallPositive(player);
        }
        // 4) Super rotten flesh: baby -> adult. (The ZombieAteEvent for this eat is fired by the caller —
        // onItemUseFinished — with the REAL eaten stack, covering this branch along with every other zombie-food
        // eat; it is no longer fired here with an EMPTY stack.)
        if (rule.restoresBabyState()) {
            PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            if (data.state().size() == ZombieSize.BABY) {
                player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data.withState(data.state().asAdult()));
                player.syncData(IAmZombieAttachments.PLAYER_ZOMBIE);
            }
        }
    }

    private static void applyRandomSmallPositive(Player player) {
        int dur = IAmZombieConfig.POISONOUS_POTATO_POSITIVE_DURATION_TICKS.get();
        int pick = player.getRandom().nextInt(3);
        Holder<MobEffect> effect = switch (pick) {
            case 0 -> MobEffects.SPEED;
            case 1 -> MobEffects.HASTE;
            default -> MobEffects.LUCK;
        };
        player.addEffect(new MobEffectInstance(effect, dur, 0));
    }

    private static ZombieFoodRules.PreservedFoodPunishments preserveExistingFoodPunishments(Player player) {
        return ZombieFoodRules.preserveExistingFoodPunishments(
                preserve(player.getEffect(MobEffects.HUNGER)),
                preserve(player.getEffect(MobEffects.NAUSEA)),
                preserve(player.getEffect(MobEffects.POISON))
        );
    }

    private static ZombieFoodRules.PreservedEffect preserve(MobEffectInstance effect) {
        if (effect == null) {
            return ZombieFoodRules.PreservedEffect.absent();
        }
        return new ZombieFoodRules.PreservedEffect(true, effect.getDuration(), effect.getAmplifier());
    }

    private static void restoreFoodPunishments(Player player, ZombieFoodRules.PreservedFoodPunishments preserved, int elapsedTicks) {
        if (preserved == null) {
            return;
        }
        restore(player, MobEffects.HUNGER, preserved.hunger(), elapsedTicks);
        restore(player, MobEffects.NAUSEA, preserved.nausea(), elapsedTicks);
        restore(player, MobEffects.POISON, preserved.poison(), elapsedTicks);
    }

    private static ZombieFoodRules.PreservedGoldenAppleEffects preserveExistingGoldenAppleEffects(Player player) {
        return ZombieFoodRules.preserveExistingGoldenAppleEffects(
                preserve(player.getEffect(MobEffects.REGENERATION)),
                preserve(player.getEffect(MobEffects.ABSORPTION)),
                preserve(player.getEffect(MobEffects.RESISTANCE)),
                preserve(player.getEffect(MobEffects.FIRE_RESISTANCE))
        );
    }

    private static void restoreGoldenAppleEffects(Player player, ZombieFoodRules.PreservedGoldenAppleEffects preserved, int elapsedTicks) {
        if (preserved == null) {
            return;
        }
        restore(player, MobEffects.REGENERATION, preserved.regeneration(), elapsedTicks);
        restore(player, MobEffects.ABSORPTION, preserved.absorption(), elapsedTicks);
        restore(player, MobEffects.RESISTANCE, preserved.resistance(), elapsedTicks);
        restore(player, MobEffects.FIRE_RESISTANCE, preserved.fireResistance(), elapsedTicks);
    }

    private static void restore(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, ZombieFoodRules.PreservedEffect preserved, int elapsedTicks) {
        if (!preserved.present()) {
            return;
        }
        int duration = preserved.durationTicks();
        if (duration > 0) {
            // Snapshot was taken at use Start; account for the ticks elapsed during the eat so the pre-existing
            // effect isn't silently extended. A finite effect that would have expired during the eat is not re-added.
            duration -= Math.max(0, elapsedTicks);
            if (duration <= 0) {
                return;
            }
        }
        // duration <= 0 here means the infinite-duration sentinel (-1), which is re-applied unchanged.
        player.addEffect(new MobEffectInstance(effect, duration, preserved.amplifier()));
    }
}
