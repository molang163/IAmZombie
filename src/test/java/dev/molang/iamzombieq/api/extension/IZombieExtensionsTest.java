package dev.molang.iamzombieq.api.extension;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Source-shape guards for the extension registry contract and hook declarations. These tests read production source
 * text only: they do not load or initialize {@code IZombieExtensions}, and they do not claim to execute its registry.
 */
class IZombieExtensionsTest {

    private static final String EXT_DIR = "dev/molang/iamzombieq/api/extension/";

    @Test
    void providerListsAreInitializedEmptyWithNoStaticBlockOrRegisterCall() throws IOException {
        String src = SourceScan.mainJava(EXT_DIR + "IZombieExtensions.java");
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
    void accessorsAreEachDirectlyInternalLibrarySeams() throws IOException {
        String src = SourceScan.mainJava(EXT_DIR + "IZombieExtensions.java");
        assertDirectInternalAccessor(src,
                "public static List<IFoodRuleProvider> foodRuleProviders()",
                "food providers");
        assertDirectInternalAccessor(src,
                "public static List<IAttackerHook> attackerHooks()",
                "attacker hooks");
    }

    @Test
    void sourceShapeDocumentsSupportedRegistrationAndSetupOnlyLifecycle() throws IOException {
        String src = SourceScan.mainJava(EXT_DIR + "IZombieExtensions.java");
        String contract = src.replaceAll("(?m)^\\s*\\* ?", " ").replaceAll("\\s+", " ");
        assertSupportedRegisterEntryPoint(src,
                "public static void register(@NotNull IFoodRuleProvider provider)",
                "food provider");
        assertSupportedRegisterEntryPoint(src,
                "public static void register(@NotNull IAttackerHook hook)",
                "attacker hook");

        assertTrue(contract.contains("setup-only") && contract.contains("process/classloader scoped"),
                "the registry contract should define setup-only, process/classloader-scoped registration");
        assertTrue(contract.contains("World, server, datapack, and config reloads do not unload mods"),
                "reload and logical-server lifecycle must not be confused with mod unload");
        assertTrue(contract.contains("does not support runtime unregister"),
                "the contract should explicitly decline runtime unregister");
        assertTrue(contract.contains("actual registration completion order")
                        && contract.contains("Parallel addon setup does not guarantee cross-addon order"),
                "food ordering and parallel-setup limits should be explicit");
        assertTrue(contract.contains("Duplicate registrations participate repeatedly"),
                "duplicate registration should be documented without changing its behavior");
        assertTrue(contract.contains("Passing null violates this contract")
                        && contract.contains("does not specify a particular failure point"),
                "null should be contract-invalid without promising fail-fast behavior");
        assertTrue(contract.contains("STABLE contract does not include")
                        && contract.contains("@Internal accessors")
                        && contract.contains("Experimental attacker API"),
                "class-level STABLE scope should exclude Internal and Experimental members");
    }

    @Test
    void foodRuleProviderDefersWithNullAndReturnsAFoodRuleOtherwise() throws IOException {
        String src = SourceScan.mainJava(EXT_DIR + "IFoodRuleProvider.java");
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
        String src = SourceScan.mainJava(EXT_DIR + "IAttackerHook.java");
        // FIX 7: the hook returns the AttackerDecision enum (was @Nullable Boolean) and is @Experimental.
        assertTrue(src.contains("AttackerDecision shouldAttack("),
                "the attacker hook should return the AttackerDecision enum");
        assertTrue(src.contains("@ApiStatus.Experimental"),
                "the attacker hook should be marked @ApiStatus.Experimental");
        assertTrue(src.contains("DEFERRED") || src.contains("deferred") || src.contains("not yet wired"),
                "the attacker hook should document that its wiring is deferred in Phase-1");
        // The DEFAULT enum value is the explicit "no opinion / defer" replacement for the old null return.
        String decision = SourceScan.mainJava(EXT_DIR + "AttackerDecision.java");
        assertTrue(decision.contains("DEFAULT") && decision.contains("FORCE_TARGET")
                        && decision.contains("ALLOW_IF_PROVOKED") && decision.contains("IGNORE"),
                "the AttackerDecision enum should declare FORCE_TARGET/ALLOW_IF_PROVOKED/IGNORE/DEFAULT");
    }

    private static void assertDirectInternalAccessor(String source, String signature, String label) {
        String declaration = declarationPrefix(source, signature);
        int javadocEnd = declaration.lastIndexOf("*/");
        assertTrue(javadocEnd >= 0, label + " accessor should have a complete Javadoc block");
        String directAnnotations = declaration.substring(javadocEnd + 2);
        assertEquals(1, SourceScan.countOccurrences(directAnnotations, "@ApiStatus.Internal"),
                label + " accessor should directly carry exactly one @ApiStatus.Internal");
        String contract = normalizedDeclaration(source, signature);
        assertTrue(contract.contains("Library-internal consumption seam")
                        && contract.contains("Addons must not call")
                        && contract.contains("modify the returned list")
                        && contract.contains("retain its reference")
                        && contract.contains("depend on its implementation type"),
                label + " accessor should document the unsupported external seam contract");
    }

    private static void assertSupportedRegisterEntryPoint(String source, String signature, String label) {
        String declaration = normalizedDeclaration(source, signature);
        assertTrue(declaration.contains("Supported addon entry point"),
                label + " register overload should be documented as supported addon API");
    }

    private static String declarationPrefix(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "method signature should exist: " + signature);
        int javadocStart = source.lastIndexOf("\n    /**", signatureStart);
        assertTrue(javadocStart >= 0, "method should have directly preceding Javadoc: " + signature);
        return source.substring(javadocStart, signatureStart);
    }

    private static String normalizedDeclaration(String source, String signature) {
        return declarationPrefix(source, signature)
                .replace("*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
