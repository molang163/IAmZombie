package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieFoodEventsSourceTest {
    private static final Path FOOD_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFoodEvents.java");
    private static final Path CONFIG_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path CLIENT_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java");

    @Test
    void configuredZombieFoodIdsSplitServerAndRemoteClientAuthority() throws IOException {
        String config = Files.readString(CONFIG_SOURCE);
        String foodEvents = Files.readString(FOOD_SOURCE);
        String client = Files.readString(CLIENT_SOURCE);

        assertTrue(config.contains("public static Set<String> configuredZombieFoods()"),
                "the K1 facade must retain its public normalization helper");
        String normalizer = SourceScan.compact(
                SourceScan.stripComments(
                        SourceScan.methodBody(config, "public static Set<String> configuredZombieFoods")));
        int liveRead = normalizer.indexOf("ZOMBIE_FOODS.get()");
        int rootLowercase = normalizer.indexOf("toLowerCase(Locale.ROOT)");
        int immutableSet = normalizer.indexOf("Collectors.toUnmodifiableSet()");
        assertTrue(liveRead >= 0 && rootLowercase > liveRead && immutableSet > rootLowercase,
                "the K1 helper must still alias the canonical list and preserve its contract");

        String relevantSources = config + foodEvents + client;
        assertEquals(2, SourceScan.countOccurrences(relevantSources, "Set<String> configuredZombieFoods()"),
                "only the K1 facade and canonical SERVER consumer may normalize local values");
        assertEquals(2, SourceScan.countOccurrences(relevantSources, "toLowerCase(Locale.ROOT)"),
                "only the K1 facade and canonical SERVER consumer may lower-case food ids");
        assertEquals(2, SourceScan.countOccurrences(relevantSources, "Collectors.toUnmodifiableSet()"),
                "only the K1 facade and canonical SERVER consumer may collect the local list");
        assertEquals(3, SourceScan.countOccurrences(foodEvents, "configuredZombieFoods()"),
                "one helper plus cake and built-in provider paths must use canonical SERVER normalization");
        assertTrue(SourceScan.methodBody(foodEvents, "public static void onRightClickCakeBlock")
                        .contains("configuredZombieFoods()"),
                "the cake path must use canonical SERVER normalization");
        assertTrue(SourceScan.methodBody(foodEvents, "private static FoodRule resolveFoodRule")
                        .contains("configuredZombieFoods()"),
                "the built-in provider fallback must use canonical SERVER normalization");
        assertTrue(SourceScan.methodBody(client, "public static void onItemTooltip")
                        .contains("configuredZombieFoods(authorityConnection)"),
                "the client tooltip must use the current connection's READY remote19 payload");
        assertTrue(client.contains("\"configuredZombieFoods\"")
                        && client.contains("CONFIGURED_ZOMBIE_FOODS.invokeExact(")
                        && client.contains("MethodHandles.privateLookupIn("),
                "the remote19 helper must invoke the package-private authority runtime through its cached handle");
        assertTrue(foodEvents.contains("private static Set<String> configuredZombieFoods()"),
                "the logical server must own its canonical SERVER normalizer");
        assertFalse(client.contains("private static Set<String> configuredZombieFoods()"),
                "the physical client must not normalize a local SERVER value");
        assertFalse(foodEvents.contains("IAmZombieConfig."),
                "logical-server food gameplay must not call the legacy facade");
        assertFalse(client.contains("IAmZombieConfig."),
                "physical-client food UI must not call the legacy facade");
        assertFalse(client.replace("IAmZombieServerConfig.SPEC", "")
                        .contains("IAmZombieServerConfig."),
                "physical-client food UI may identity-check the SERVER spec "
                        + "but must not read a local SERVER ConfigValue");
    }

    @Test
    void safeZombieFoodsRestorePreExistingVanillaFoodPunishments() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        assertTrue(source.contains("onItemUseStarted"), "food effects should be snapshotted before vanilla consumption applies side effects");
        assertTrue(source.contains("PENDING_FOOD_PUNISHMENTS"), "pre-existing punishment effects should be remembered per player");
        assertTrue(source.contains("preserveExistingFoodPunishments"), "runtime should use the rule-layer preservation model");
        assertTrue(source.contains("restoreFoodPunishments"), "safe food should restore previous Hunger/Nausea/Poison instead of acting like milk");
    }

    @Test
    void humanFoodPunishmentUsesConfigValues() throws IOException {
        String source = Files.readString(FOOD_SOURCE);
        String config = Files.readString(CONFIG_SOURCE);

        assertTrue(config.contains("HUMAN_FOOD_NAUSEA_DURATION_TICKS"), "human food Nausea duration should be configurable");
        assertTrue(config.contains("HUMAN_FOOD_HUNGER_DURATION_TICKS"), "human food Hunger duration should be configurable");
        assertTrue(config.contains("HUMAN_FOOD_HUNGER_AMPLIFIER"), "human food Hunger amplifier should be configurable");
        assertTrue(source.contains("ZombieFoodRules.humanFoodPunishmentSettings"), "runtime should build human-food punishment from config");
    }

    @Test
    void zombieFoodEffectsAreAppliedFromTheGradedRuleSpectrum() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        // The old per-effect switch is gone; effects now come from the data-driven buffs()/debuffs() lists.
        // (The method name applyZombieEffects intentionally remains, so only the removed enum symbol is asserted gone.)
        assertFalse(source.contains("rules.ZombieEffect"), "the old ZombieEffect enum import should be removed");
        assertFalse(source.contains("ZombieEffect."), "the old ZombieEffect enum usages should be removed");
        assertFalse(source.contains("ZombieEffect "), "the old ZombieEffect enum type references should be removed");
        assertTrue(source.contains("applyZombieEffects"), "runtime should still funnel rule effects through applyZombieEffects");
        assertTrue(source.contains("rule.buffs()"), "positive buffs should be applied by iterating the rule's buffs list");
        assertTrue(source.contains("rule.debuffs()"), "negative debuffs should be applied by iterating the rule's debuffs list");
        assertTrue(source.contains("ZombieFoodRules.ruleForStack"), "the events layer should resolve rules via the ItemStack-aware catch-all");
    }

    @Test
    void specialFoodBuffsRemainConfigurable() throws IOException {
        String config = Files.readString(CONFIG_SOURCE);

        assertTrue(config.contains("SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS"), "Strength duration should be configurable");
        assertTrue(config.contains("SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS"), "Saturation duration should be configurable");
        assertTrue(config.contains("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS"), "spider eye Night Vision duration should be configurable");
        assertTrue(config.contains("PUFFERFISH_ABSORPTION_DURATION_TICKS"), "pufferfish Absorption duration should be configurable");
        assertTrue(config.contains("PUFFERFISH_REGENERATION_DURATION_TICKS"), "pufferfish Regeneration duration should be configurable");
        assertTrue(config.contains("POISONOUS_POTATO_POSITIVE_DURATION_TICKS"), "poisonous potato positive buff duration should be configurable");
        assertTrue(config.contains("GOLDEN_APPLE_ABSORPTION_DURATION_TICKS"), "golden apple Absorption duration should be configurable");
        assertTrue(config.contains("CHORUS_SLOW_FALLING_DURATION_TICKS"), "chorus fruit Slow Falling duration should be configurable");
    }

    @Test
    void goldenAppleSuppressionRestoresPreExistingPositiveEffects() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        assertTrue(source.contains("PENDING_GOLDEN_APPLE_EFFECTS"), "pre-existing golden apple-like buffs should be snapshotted");
        assertTrue(source.contains("preserveExistingGoldenAppleEffects"), "runtime should preserve positive effects before vanilla food applies");
        assertTrue(source.contains("restoreGoldenAppleEffects"), "golden apple suppression should not clear unrelated existing buffs");
    }

    @Test
    void pendingFoodEffectSnapshotsAreClearedWhenItemUseStopsBeforeFinish() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        assertTrue(source.contains("onItemUseStopped"), "canceled or interrupted eating should clear pending food snapshots");
        assertTrue(source.contains("clearPendingFoodSnapshots"), "snapshot cleanup should be centralized for all pending food maps");
        assertTrue(source.contains("PENDING_FOOD_PUNISHMENTS.remove(player.getUUID())"), "food punishment snapshots should not leak between item uses");
        assertTrue(source.contains("PENDING_GOLDEN_APPLE_EFFECTS.remove(player.getUUID())"), "golden apple snapshots should not leak into later finished food uses");
    }

    @Test
    void creativeStillReceivesTheSpecialZombieFoodBuffSubstitution() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        // G1: processing must no longer be blanket-skipped in creative; the special foods get the buff substitution.
        assertFalse(source.contains("shouldApplyZombieFoodRules"),
                "the old creative-excluding processing gate should be replaced");
        assertFalse(source.contains("&& !player.isCreative() &&"),
                "creative must no longer be blanket-excluded from the food-rule processing gate");
        assertTrue(source.contains("shouldProcessZombieFood"),
                "a server/non-spectator processing gate should replace the old creative-excluding gate");
        assertTrue(source.contains("appliesFullZombieFoodRules"),
                "creative-vs-survival bookkeeping should be decided per item via a dedicated helper");
        assertTrue(source.contains("ZombieFoodRules.isAlwaysEdibleForZombies(eatenId)")
                        && source.contains("ZombieFoodRules.isAlwaysEdibleForZombies(eatenItemId)"),
                "in creative only the special always-edible foods are processed, so the buff substitution still runs");
    }

    @Test
    void zombiePlayersCanStartEatingBuffFoodsAtFullHungerWithoutMutatingItems() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        // G7: a server-side right-click intercept force-starts the eat instead of changing vanilla FoodProperties.
        assertTrue(source.contains("PlayerInteractEvent.RightClickItem"),
                "full-hunger eating should be enabled via a server-side right-click intercept");
        assertTrue(source.contains("ZombieFoodRules.isAlwaysEdibleForZombies(itemId(stack))"),
                "only the rule-layer always-edible foods should be force-started");
        assertTrue(source.contains("player.canEat("),
                "the intercept should only act when vanilla would actually reject eating at full hunger");
        assertTrue(source.contains("player.startUsingItem(event.getHand())"),
                "the multi-tick eat should be started manually so the Start/Finish buff handling still runs");
        assertTrue(source.contains("setCancellationResult(InteractionResult.CONSUME)") && source.contains("event.setCanceled(true)"),
                "vanilla Item.use (which would FAIL at full hunger) must be short-circuited with a CONSUME result");
        assertFalse(source.contains("FoodProperties.Builder") || source.contains(".alwaysEdible("),
                "the vanilla items must not be mutated to achieve always-edible behavior");
    }
}
