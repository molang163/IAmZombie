package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Source-assert coverage for the "block Peaceful + fix the shape crash" change. The client/mixin classes cannot be
 * exercised in the no-Minecraft test sourceset (and runClient needs a display), so these assert the wiring is present.
 */
class PeacefulBlockSourceTest {
    @Test
    void shapePipelineIsNullSafe() throws IOException {
        String shapes = SourceScan.mainJava(
                "dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java");
        assertTrue(shapes.contains("cached.entity == null || cached.entity.level() != player.level()"),
                "cachedShapeFor must null-check the cached entity before dereferencing .level()");
        assertTrue(shapes.contains("if (cached.entity != null) {"),
                "syncShape must be skipped when the shape entity is null");
        assertTrue(shapes.contains("if (cached.entity == null) {"),
                "replacementFor must return null when the shape entity is null");

        String client = SourceScan.mainJava("dev/molang/iamzombieq/client/IAmZombieClient.java");
        assertTrue(client.contains("if (shape == null) {"), "the render-state modifier must guard a null shape");
        assertTrue(client.contains("if (replacement == null) {"), "the render-state modifier must guard a null replacement");
    }

    @Test
    void createWorldDifficultyHandlerDirectlyDelegatesToGameplayGuard() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/client/CreateWorldScreenGameTabMixin.java");
        assertTrue(source.contains("@Mixin(targets = \"net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab\")"),
                "the create-world redirect must keep its private GameTab target");
        assertTrue(source.contains("method = \"<init>\""),
                "the create-world redirect must remain on the GameTab constructor");
        assertDifficultyRedirectDelegates(
                source,
                "private Difficulty[] iamzombieq$onlyNonPeacefulDifficulties",
                "CreateWorldScreenGameTabMixin");
    }

    @Test
    void worldOptionsDifficultyHandlerDirectlyDelegatesToGameplayGuard() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/client/DifficultyButtonsMixin.java");
        assertTrue(source.contains("@Mixin(DifficultyButtons.class)"),
                "the World Options redirect must keep its DifficultyButtons target");
        assertTrue(source.contains("method = \"create\""),
                "the World Options redirect must remain on DifficultyButtons.create");
        assertDifficultyRedirectDelegates(
                source,
                "private static Difficulty[] iamzombieq$onlyNonPeacefulDifficulties",
                "DifficultyButtonsMixin");
    }

    private static void assertDifficultyRedirectDelegates(String source, String handlerSignature, String label) {
        assertTrue(source.contains("@Redirect"), label + " should @Redirect the difficulty values");
        assertTrue(source.contains("import dev.molang.iamzombieq.gameplay.PeacefulGuard;"),
                label + " must delegate to the ordinary gameplay helper, not a mixin-package class");
        assertTrue(source.contains("Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"),
                label + " should keep the Difficulty.values() redirect descriptor");

        String handler = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, handlerSignature)));
        String executableBody = handler.substring(handler.indexOf('{'));
        assertEquals("{returnPeacefulGuard.selectableDifficulties();}", executableBody,
                label + " handler should do nothing except directly delegate to the gameplay helper");
        assertFalse(handler.contains("newDifficulty[]"), label + " must not hard-code a local difficulty array");
        // Woven vanilla code may call an ordinary mod class such as gameplay.PeacefulGuard. It must not call a helper
        // inside the mixin package itself, which would trigger Mixin's IllegalClassLoadError protection.
        assertFalse(source.contains("DifficultyFilters"),
                label + " must not reference a helper in the mixin package");
    }

    @Test
    void difficultyCommandRejectsPeaceful() throws IOException {
        String mixin = SourceScan.mainJava("dev/molang/iamzombieq/mixin/DifficultyCommandMixin.java");
        assertTrue(mixin.contains("@Mixin(DifficultyCommand.class)"), "should target DifficultyCommand");
        assertTrue(mixin.contains("setDifficulty(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/Difficulty;)I"),
                "should inject into setDifficulty");
        assertTrue(mixin.contains("@At(\"HEAD\")") && mixin.contains("cancellable = true"), "should HEAD-inject cancellably");
        assertTrue(mixin.contains("PeacefulGuard.isForbidden(difficulty)"),
                "should act only on PEACEFUL via the shared PeacefulGuard predicate");
        assertTrue(mixin.contains("source.sendFailure") && mixin.contains("iamzombieq.message.peaceful_rejected"),
                "should send the rejection message");
        assertTrue(mixin.contains("cir.setReturnValue(0)"), "should cancel the command before the difficulty applies");
    }

    @Test
    void mixinsAndLangAreRegistered() throws IOException {
        String json = SourceScan.resource("iamzombieq.mixins.json");
        assertTrue(json.contains("\"DifficultyCommandMixin\""), "DifficultyCommandMixin must be in the common mixins list");
        assertTrue(json.contains("\"client.CreateWorldScreenGameTabMixin\"")
                        && json.contains("\"client.DifficultyButtonsMixin\""),
                "both UI mixins must be in the client list");
        assertTrue(SourceScan.resource("assets/iamzombieq/lang/en_us.json")
                        .contains("iamzombieq.message.peaceful_rejected"),
                "en_us must define peaceful_rejected");
        assertTrue(SourceScan.resource("assets/iamzombieq/lang/zh_cn.json")
                        .contains("僵尸怎么能出现在和平模式呢？"),
                "zh_cn must define the peaceful_rejected message");
    }
}
