package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-invariant guards for fixes whose runtime behavior the GameTest harness
 * cannot cleanly drive (giant passive tick, dimension-change clone, projectile impact). Each asserts the fix marker is
 * present so a future refactor cannot silently regress it. Behavioral fixes (#2 nautilus saddle, #3/#4 piglin
 * conversion, #11 cake) are covered by runtime GameTests instead; #1's sweep clamp is guarded in
 * {@code ZombiePlayerEventsSourceTest}.
 */
class FixRegressionSourceTest {
    private static final Path ZPE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");
    // The reinforcement spawn + giant stomp-aura logic were split out of ZombiePlayerEvents into dedicated classes.
    private static final Path REINFORCEMENT = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieReinforcementEvents.java");
    private static final Path GIANT = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/GiantPlayerEvents.java");
    private static final Path HEROBRINE = Path.of("src/main/java/dev/molang/iamzombieq/entity/HerobrineEntity.java");

    @Test
    void herobrineIsHittableByProjectilesSoTheEncounterPathFires() throws IOException {
        // #5: canBeHitByProjectile() must return true so ProjectileImpactEvent -> HerobrineEvents.onProjectileImpact
        // can fire; invulnerability is enforced elsewhere (setInvulnerable + hurtServer override return false).
        String src = Files.readString(HEROBRINE);
        String method = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(src, "public boolean canBeHitByProjectile")));
        assertEquals(1, SourceScan.countOccurrences(method, "returntrue;"),
                "canBeHitByProjectile() must return true so the projectile-impact encounter path is reachable");
        assertEquals(1, SourceScan.countOccurrences(method, "return"),
                "canBeHitByProjectile() should have exactly one return path");
    }

    @Test
    void sunlightFireWindowIsClearedOnCloneAndDeath() throws IOException {
        // #8: the sun-fire window must be cleared on clone (death respawn / dimension change) AND on death, so a stale
        // window cannot mis-attribute a later ordinary fire to the sunlight death type after respawn. The window map
        // itself now lives in ZombieSunlightEvents; the core clone/death handlers clear it via clearSunFireWindow.
        String src = Files.readString(ZPE);
        String clone = SourceScan.methodBody(src, "public static void onPlayerClone");
        assertTrue(clone.contains("ZombieSunlightEvents.clearSunFireWindow(event.getEntity().getUUID())"),
                "onPlayerClone must clear the sun-fire window");
        // Bound the death window to the method's own braces (via SourceScan) instead of a brittle +3000 char count
        // or a following-handler anchor.
        String death = SourceScan.methodBody(src, "public static void onLivingDeath");
        assertTrue(death.contains("ZombieSunlightEvents.clearSunFireWindow(event.getEntity().getUUID())"),
                "onLivingDeath must clear the sun-fire window");
    }

    @Test
    void reinforcementSpawnHasAquaticAwareLiquidGuard() throws IOException {
        // #9: reinforcement placement must reject a partially-submerged spot via the public, aquatic-aware
        // checkSpawnObstruction() (Mob default rejects liquid; Drowned overrides to allow it).
        String src = Files.readString(REINFORCEMENT);
        assertTrue(src.contains("!reinforcement.checkSpawnObstruction(level)"),
                "reinforcement placement must include the aquatic-aware liquid guard (checkSpawnObstruction)");
    }

    @Test
    void giantStompAuraExcludesAllOwnedMountsNotJustSpiders() throws IOException {
        // #10: the stomp-aura exclusion must cover owned vanilla-tamed mounts (AbstractHorse, ZombieNautilus) via a
        // null-safe getOwnerReference() check, not only the custom Spider mount.
        String src = Files.readString(GIANT);
        assertTrue(src.contains("!isOwnedMount(target, player)"), "the aura filter should call isOwnedMount");
        assertFalse(src.contains("isOwnedSpiderMount"), "the narrower isOwnedSpiderMount should have been renamed away");
        String helper = SourceScan.methodBody(src, "private static boolean isOwnedMount");
        assertTrue(helper.contains("AbstractHorse") && helper.contains("ZombieNautilus") && helper.contains("getOwnerReference"),
                "isOwnedMount must exclude tamed AbstractHorse + ZombieNautilus via getOwnerReference");
    }
}
