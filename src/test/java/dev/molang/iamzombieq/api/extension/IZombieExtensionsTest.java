package dev.molang.iamzombieq.api.extension;
import dev.molang.iamzombieq.rules.food.FoodRule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Source-scan tests for the extension registry and its hooks (the repo's test source set has Minecraft types on
 * the classpath name-only but not loadable, so a hook lambda — whose signature names {@code ServerPlayer}/
 * {@code ItemStack} — cannot be constructed in a test; we assert structure via source scan instead). Verifies the
 * neutral-when-empty default and the first-non-null-wins contract the food handler depends on.
 */
class IZombieExtensionsTest {

    private static final String EXT_DIR = "src/main/java/dev/molang/iamzombieq/api/extension/";

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(EXT_DIR + file));
    }

    @Test
    void providerListsAreInitializedEmptyWithNoStaticBlockOrRegisterCall() throws IOException {
        String src = read("IZombieExtensions.java");
        // PLAN A2: CopyOnWriteArrayList initialized EMPTY, no static block, no self-registration inside the class.
        assertTrue(src.contains("new CopyOnWriteArrayList<>()"), "lists must be initialized empty");
        assertTrue(src.contains("CopyOnWriteArrayList<IFoodRuleProvider> FOOD")
                        && src.contains("CopyOnWriteArrayList<IAttackerHook> ATTACKER"),
                "both the food and attacker provider lists should exist");
        assertFalse(src.contains("static {"), "there must be NO static initializer block (no static side effects)");
        // register(...) is declared (addons call it) but never self-invoked inside the class body.
        assertTrue(src.contains("public static void register("), "register(...) entry points should exist for addons");
        assertFalse(src.contains("register(new "), "the registry must not register any provider itself");
    }

    @Test
    void accessorsAreInternalAndExposeTheRegisteredLists() throws IOException {
        String src = read("IZombieExtensions.java");
        assertTrue(src.contains("@ApiStatus.Internal"), "the accessor methods should be marked internal");
        assertTrue(src.contains("List<IFoodRuleProvider> foodRuleProviders()"), "food providers accessor should exist");
        assertTrue(src.contains("List<IAttackerHook> attackerHooks()"), "attacker hooks accessor should exist");
    }

    @Test
    void foodRuleProviderDefersWithNullAndReturnsAFoodRuleOtherwise() throws IOException {
        String src = read("IFoodRuleProvider.java");
        // Contract: @Nullable FoodRule ruleForStack(ServerPlayer, ItemStack, String); null => defer to built-in.
        assertTrue(src.contains("@Nullable"), "the provider should be able to return null to defer");
        assertTrue(src.contains("FoodRule ruleForStack(") && src.contains("ServerPlayer eater")
                        && src.contains("ItemStack stack") && src.contains("String itemId"),
                "the provider signature should match the design (ServerPlayer, ItemStack, String)");
        assertTrue(src.contains("first non-null") || src.contains("FIRST non-null"),
                "the javadoc should document first-non-null-wins");
    }

    @Test
    void attackerHookReturnsAttackerDecisionEnumIsExperimentalAndShipsForFutureUse() throws IOException {
        String src = read("IAttackerHook.java");
        // FIX 7: the hook returns the AttackerDecision enum (was @Nullable Boolean) and is @Experimental.
        assertTrue(src.contains("AttackerDecision shouldAttack("),
                "the attacker hook should return the AttackerDecision enum");
        assertTrue(src.contains("@ApiStatus.Experimental"),
                "the attacker hook should be marked @ApiStatus.Experimental");
        assertTrue(src.contains("DEFERRED") || src.contains("deferred") || src.contains("not yet wired"),
                "the attacker hook should document that its wiring is deferred in Phase-1");
        // The DEFAULT enum value is the explicit "no opinion / defer" replacement for the old null return.
        String decision = read("AttackerDecision.java");
        assertTrue(decision.contains("DEFAULT") && decision.contains("FORCE_TARGET")
                        && decision.contains("ALLOW_IF_PROVOKED") && decision.contains("IGNORE"),
                "the AttackerDecision enum should declare FORCE_TARGET/ALLOW_IF_PROVOKED/IGNORE/DEFAULT");
    }
}
