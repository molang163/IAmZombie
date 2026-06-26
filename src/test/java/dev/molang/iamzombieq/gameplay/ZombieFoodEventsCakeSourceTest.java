package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.food.FoodTier;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-scan pinning of the cake-block zombie-food fix in {@link ZombieFoodEvents}. NEW sibling to
 * {@link ZombieFoodEventsSourceTest} (which it does not modify).
 *
 * <p>The bug: cake is eaten as a BLOCK (CakeBlock#useWithoutItem), never as an ItemStack, so it never fired
 * LivingEntityUseItemEvent and its T3-sweet debuff (Hunger II + Nausea + Slowness) was silently skipped. The fix adds a
 * server-side {@code onRightClickCakeBlock(PlayerInteractEvent.RightClickBlock)} handler that mirrors the cake eat gate
 * and applies the same human-food punishment + zombie effects, WITHOUT cancelling the event (so vanilla still eats the
 * slice). These assertions guard that handler and the cake -> humanCooked(true) rule mapping against regressions.
 */
class ZombieFoodEventsCakeSourceTest {
    private static final Path FOOD_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFoodEvents.java");
    private static final Path RULES_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/rules/food/ZombieFoodRules.java");

    /** Extract the body of the onRightClickCakeBlock handler (from its declaration to the next @SubscribeEvent). */
    private static String cakeHandlerBody(String source) {
        int start = source.indexOf("onRightClickCakeBlock");
        assertTrue(start >= 0, "the cake handler onRightClickCakeBlock must exist");
        int next = source.indexOf("@SubscribeEvent", start);
        return next > start ? source.substring(start, next) : source.substring(start);
    }

    @Test
    void cakeHandlerIsAServerSideRightClickBlockSubscriber() throws IOException {
        String source = Files.readString(FOOD_SOURCE);

        assertTrue(source.contains("onRightClickCakeBlock"), "a dedicated cake-block handler must exist");
        assertTrue(source.contains("PlayerInteractEvent.RightClickBlock"),
                "the cake handler must subscribe to the block right-click event (cake is eaten as a block)");
        String body = cakeHandlerBody(source);
        assertTrue(body.contains("isClientSide()"),
                "the cake handler must bail on the client so effects only apply server-side");
        assertTrue(body.contains("shouldProcessZombieFood"),
                "the cake handler must reuse the existing server/non-spectator processing gate");
    }

    @Test
    void cakeHandlerChecksTheClickedBlockIsACakeOrCandleCake() throws IOException {
        String body = cakeHandlerBody(Files.readString(FOOD_SOURCE));

        assertTrue(body.contains("CakeBlock"), "the cake handler must verify the clicked block is a CakeBlock");
        assertTrue(body.contains("CandleCakeBlock"), "the cake handler must also handle a CandleCakeBlock");
        assertTrue(body.contains("CakeBlock.BITES"),
                "the cake handler must read the BITES property to skip a fully-eaten cake");
    }

    @Test
    void cakeHandlerMirrorsTheVanillaEatGateAndFullRulesGate() throws IOException {
        String body = cakeHandlerBody(Files.readString(FOOD_SOURCE));

        assertTrue(body.contains("player.canEat("),
                "the cake handler must mirror vanilla's full-hunger refusal so it never punishes a no-op click");
        assertTrue(body.contains("appliesFullZombieFoodRules"),
                "the cake handler must only apply the full zombie-food rules to a non-spectator zombie player");
    }

    @Test
    void cakeHandlerAppliesThePunishmentAndZombieEffectsViaTheSharedHelpers() throws IOException {
        String body = cakeHandlerBody(Files.readString(FOOD_SOURCE));

        assertTrue(body.contains("ZombieFoodRules.ruleForStack(ItemStack.EMPTY, \"minecraft:cake\""),
                "the cake handler must resolve the cake rule by id (no ItemStack available for a block)");
        assertTrue(body.contains("applyHumanFoodPunishment"),
                "the cake handler must reuse the shared human-food punishment helper");
        assertTrue(body.contains("ZombieFoodRules.humanFoodPunishmentSettings"),
                "the punishment must be built from the existing HUMAN_FOOD_* config like onItemUseFinished");
        assertTrue(body.contains("applyZombieEffects"),
                "the cake handler must reuse the shared applyZombieEffects helper (T3-sweet Slowness rides here)");
        assertTrue(body.contains("IAmZombieAdvancements.HUMAN_FOOD"),
                "eating a cake should award the HUMAN_FOOD advancement for a ServerPlayer");
    }

    @Test
    void cakeHandlerDoesNotCancelTheEventSoVanillaStillEatsTheSlice() throws IOException {
        String body = cakeHandlerBody(Files.readString(FOOD_SOURCE));

        assertFalse(body.contains("setCanceled(true)"),
                "the cake handler must NOT cancel the right-click so vanilla still eats the slice");
        assertFalse(body.contains("setCancellationResult"),
                "the cake handler must let vanilla's cake eat proceed unchanged");
    }

    @Test
    void cakeIsClassifiedAsSweetHumanCookedSoItPunishesAndAddsSlowness() throws IOException {
        String rules = Files.readString(RULES_SOURCE);

        assertTrue(rules.contains("Map.entry(\"minecraft:cake\", () -> humanCooked(true))"),
                "cake must map to humanCooked(true): HUMAN_COOKED punishment PLUS the sweet Slowness debuff");
        // Confirm the sweet branch of humanCooked is exactly what adds the configurable Slowness.
        assertTrue(rules.contains("MobEffects.SLOWNESS"),
                "the sweet HUMAN_COOKED branch must add a Slowness debuff");
        assertTrue(rules.contains("FoodTier.HUMAN_COOKED"),
                "the human-cooked tier (the only tier that punishes) must back the cake rule");
    }
}
