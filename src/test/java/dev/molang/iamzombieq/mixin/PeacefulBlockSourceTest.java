package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-assert coverage for the "block Peaceful + fix the shape crash" change. The client/mixin classes cannot be
 * exercised in the no-Minecraft test sourceset (and runClient needs a display), so these assert the wiring is present.
 */
class PeacefulBlockSourceTest {
    private static String read(String p) throws IOException {
        return Files.readString(Path.of(p));
    }

    @Test
    void shapePipelineIsNullSafe() throws IOException {
        String shapes = read("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java");
        assertTrue(shapes.contains("cached.entity == null || cached.entity.level() != player.level()"),
                "cachedShapeFor must null-check the cached entity before dereferencing .level()");
        assertTrue(shapes.contains("if (cached.entity != null) {"),
                "syncShape must be skipped when the shape entity is null");
        assertTrue(shapes.contains("if (cached.entity == null) {"),
                "replacementFor must return null when the shape entity is null");

        String client = read("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java");
        assertTrue(client.contains("if (shape == null) {"), "the render-state modifier must guard a null shape");
        assertTrue(client.contains("if (replacement == null) {"), "the render-state modifier must guard a null replacement");
    }

    @Test
    void difficultyUiHidesPeaceful() throws IOException {
        for (String mixin : new String[]{
                "src/main/java/dev/molang/iamzombieq/mixin/client/CreateWorldScreenGameTabMixin.java",
                "src/main/java/dev/molang/iamzombieq/mixin/client/DifficultyButtonsMixin.java"}) {
            String s = read(mixin);
            assertTrue(s.contains("@Redirect"), mixin + " should @Redirect the difficulty values");
            assertTrue(s.contains("Lnet/minecraft/world/Difficulty;values()[Lnet/minecraft/world/Difficulty;"),
                    mixin + " should target Difficulty.values()");
            assertTrue(s.contains("Difficulty.EASY") && s.contains("Difficulty.NORMAL") && s.contains("Difficulty.HARD"),
                    mixin + " should return the non-Peaceful set {EASY, NORMAL, HARD}");
            assertTrue(!s.contains("Difficulty.PEACEFUL"), mixin + " must not include PEACEFUL");
            // Regression guard: the redirect is woven into a vanilla class, which cannot reference a class in the
            // mod's mixin package (IllegalClassLoadError). Build the array inline from the vanilla enum only.
            assertTrue(!s.contains("DifficultyFilters"),
                    mixin + " must not reference a mixin-package helper (would crash the screen when woven in)");
        }
    }

    @Test
    void difficultyCommandRejectsPeaceful() throws IOException {
        String mixin = read("src/main/java/dev/molang/iamzombieq/mixin/DifficultyCommandMixin.java");
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
        String json = read("src/main/resources/iamzombieq.mixins.json");
        assertTrue(json.contains("\"DifficultyCommandMixin\""), "DifficultyCommandMixin must be in the common mixins list");
        assertTrue(json.contains("\"client.CreateWorldScreenGameTabMixin\"")
                        && json.contains("\"client.DifficultyButtonsMixin\""),
                "both UI mixins must be in the client list");
        assertTrue(read("src/main/resources/assets/iamzombieq/lang/en_us.json").contains("iamzombieq.message.peaceful_rejected"),
                "en_us must define peaceful_rejected");
        assertTrue(read("src/main/resources/assets/iamzombieq/lang/zh_cn.json").contains("僵尸怎么能出现在和平模式呢？"),
                "zh_cn must define the peaceful_rejected message");
    }
}
