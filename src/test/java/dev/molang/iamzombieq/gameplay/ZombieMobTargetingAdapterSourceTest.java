package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.molang.iamzombieq.rules.TargetingOverrides;
import dev.molang.iamzombieq.state.PlayerZombieData;
import dev.molang.iamzombieq.util.SourceScan;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

/**
 * Narrow source-scan guard (no Minecraft bootstrap) pinning that {@code ZombieMobTargetingAdapter#classify}:
 * <ol>
 *   <li>delegates the fast path to the registry-free {@code ZombieMobTargetingRules.classifyByEntityTypeId(...)} —
 *       so the id map is the single source of truth for the known vanilla attacker types; and</li>
 *   <li>STILL keeps the {@code instanceof} fallback for the vanilla attacker classes — so a mod entity that subclasses
 *       one of them (and therefore has an unknown registry id) is not silently narrowed to IGNORED.</li>
 * </ol>
 * This is exactly the "don't quietly narrow behaviour by rewriting only by id" contract for T2 sub-item (2).
 */
class ZombieMobTargetingAdapterSourceTest {
    private static final Path SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMobTargetingAdapter.java");

    private static String classifyBody() throws IOException {
        String source = Files.readString(SOURCE);
        return SourceScan.stripComments(SourceScan.methodBody(source, "MobKind classify(LivingEntity mob)"));
    }

    /** The whole adapter source (comments stripped) — the instanceof fallback lives in a private helper. */
    private static String strippedSource() throws IOException {
        return SourceScan.stripComments(Files.readString(SOURCE));
    }

    @Test
    void classifyDelegatesToTheRegistryFreeIdEntry() throws IOException {
        String body = classifyBody();
        assertTrue(body.contains("classifyByEntityTypeId("),
                "classify must delegate the fast path to ZombieMobTargetingRules.classifyByEntityTypeId(...)");
        assertTrue(body.contains("getKey(") && body.contains("getType()"),
                "classify must derive the entity-type id (EntityType.getKey(mob.getType())) to feed the id entry");
    }

    @Test
    void adapterKeepsTheInstanceofFallbackForModSubclasses() throws IOException {
        String source = strippedSource();
        // Every vanilla attacker class must still be reachable via instanceof (in the fallback helper), so a mod
        // subclass with an unknown registry id keeps its original classification instead of collapsing to IGNORED.
        for (String type : new String[] {
                "IronGolem", "SnowGolem", "Zoglin", "Goat", "Creeper", "Endermite",
                "TraderLlama", "Axolotl", "Warden", "WitherBoss", "EnderMan", "PolarBear" }) {
            assertTrue(source.contains("instanceof " + type),
                    "the adapter must keep the instanceof fallback for " + type + " (mod-subclass compatibility)");
        }
    }

    @Test
    void classifyOnlyFallsBackWhenTheIdEntryReturnedIgnored() throws IOException {
        String body = classifyBody();
        // Guard the shape: classify branches on the id result being IGNORED and only then hands off to the
        // instanceof fallback helper — so known vanilla ids take the id map and only unknown ids reach the fallback.
        assertTrue(body.contains("MobKind.IGNORED"),
                "classify must branch on the id result being MobKind.IGNORED before using the instanceof fallback");
        assertTrue(body.contains("classifyBySubclass("),
                "classify must hand unknown ids to the instanceof fallback helper (classifyBySubclass)");
    }

    @Test
    void targetingBooleanEntryOnlyBridgesToTheTypedOverload() throws IOException {
        String source = strippedSource();
        String legacy = SourceScan.compact(SourceScan.methodBody(source, "boolean retaliating,"));
        String expectedBridge = "returnshouldIgnoreZombiePlayer(mob,player,data,"
                + "newTargetingOverrides(retaliating,angeredNeutral));";

        assertEquals(1, SourceScan.countOccurrences(legacy, expectedBridge),
                "the legacy adapter entry should only name and forward its two override inputs");
        assertEquals("{" + expectedBridge + "}", legacy.substring(legacy.indexOf('{')),
                "the legacy adapter entry should contain no behavior beyond the compatibility bridge");
        assertFalse(legacy.contains("ZombieMobTargetingRules.shouldIgnore("),
                "the legacy adapter entry should not bypass the typed overload");

        String typed = SourceScan.compact(SourceScan.methodBody(source, "TargetingOverrides overrides"));
        assertTrue(typed.contains(
                        "returnZombieMobTargetingRules.shouldIgnore(classify(mob),data.state().form(),overrides);"),
                "the typed adapter overload should pass its context to the pure rules core");
    }

    @Test
    void targetingAdapterKeepsLegacyAndTypedPublicEntries() throws NoSuchMethodException {
        Method legacy = ZombieMobTargetingAdapter.class.getMethod(
                "shouldIgnoreZombiePlayer",
                LivingEntity.class,
                Player.class,
                PlayerZombieData.class,
                boolean.class,
                boolean.class);
        Method typed = ZombieMobTargetingAdapter.class.getMethod(
                "shouldIgnoreZombiePlayer",
                LivingEntity.class,
                Player.class,
                PlayerZombieData.class,
                TargetingOverrides.class);

        for (Method entry : new Method[] {legacy, typed}) {
            assertEquals(boolean.class, entry.getReturnType());
            assertTrue(Modifier.isPublic(entry.getModifiers()));
            assertTrue(Modifier.isStatic(entry.getModifiers()));
        }
    }
}
