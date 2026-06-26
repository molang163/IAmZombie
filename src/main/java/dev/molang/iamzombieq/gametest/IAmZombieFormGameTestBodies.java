package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * FakePlayer-driven NeoForge GameTest bodies for the FORM (§2.1) and ATTR (§2.2) acceptance rows of {@code iamzombieq},
 * registered by {@link IAmZombieFormGameTests}. These are the runtime (B1/B2) counterparts of the pure-logic (L0)
 * coverage in {@code ZombieEvolutionRulesTest} / {@code ZombieBalanceRulesTest}: rather than asserting the rule
 * functions in isolation, each drives the REAL server-side seam the production handler subscribes to.
 *
 * <p>Kills/lethal damage run through the real damage pipeline ({@code hurtServer} with a vanilla damage source), so
 * NeoForge fires the real {@code LivingDeathEvent} that {@code ZombiePlayerEvents#onLivingDeath} acts on — the same
 * path an in-game death takes. For an in-place evolution the handler cancels the death and rewrites the attachment,
 * so the (still-alive) FakePlayer's resulting form/size is read back via {@link GameTestPlayers#stateOf}. The
 * handler also runs {@code refreshFormAttributesForced} on every such respawn, so the freshly-applied form
 * attributes (innate armor, giant max-health) are observable on the player's {@code Attributes} right after.
 *
 * <p>The bodies mirror the proven sibling harness ({@code IAmZombieGameTestBodies}) exactly: same spawn helper, same
 * tight per-test entity radius, same {@code hurtServer(...)} drive. Drowning/starvation triggers are biome- and
 * dimension-independent in {@code ZombieEvolutionRules#resolveDeath}, so they are deterministic in the GameTest level
 * regardless of its biome/sky; biome-/dimension-gated rows (desert husk, nether piglin) are deferred to the rules L0.
 */
final class IAmZombieFormGameTestBodies {

    private IAmZombieFormGameTestBodies() {
    }

    /**
     * FORM-001 (state portion): a freshly-attached zombie FakePlayer carries the default NORMAL/ADULT state. The
     * spawn helper performs the same attachment write the mod's first-login handler performs
     * ({@code PlayerZombieData.DEFAULT} state), so this asserts the attach-time invariant the runtime depends on. The
     * login-side effects (root advancement, starting items, recipe unlock) need a real {@code PlayerLoggedInEvent}
     * with a connection and are deferred to L0/manual.
     */
    static void formDefaultState(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        if (!player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)) {
            helper.fail("a zombie FakePlayer must carry the PLAYER_ZOMBIE attachment");
            return;
        }
        if (GameTestPlayers.stateOf(player).form() != ZombieForm.NORMAL
                || GameTestPlayers.stateOf(player).size() != ZombieSize.ADULT) {
            helper.fail("a freshly-attached zombie player must default to NORMAL/ADULT");
            return;
        }
        helper.succeed();
    }

    /**
     * FORM-007: a CREATIVE-mode zombie FakePlayer that kills a vanilla {@code minecraft:giant} transforms into the
     * GIANT form, respawned to full health (the handler sets health to the new GIANT max). Drives the real
     * player-attack death of the Giant so vanilla fires the {@code LivingDeathEvent} the giant-kill branch acts on.
     * Also covers ATTR-007 at runtime: the forced attribute refresh makes the GIANT +80 MAX_HEALTH modifier
     * observable, so the player's max health is 100 and it is healed to full.
     */
    static void formCreativeGiantKillBecomesGiant(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        // The giant-kill branch is gated on the killer being in creative mode (ZombieEvolutionRules.canTransformFromGiantKill).
        player.setGameMode(GameType.CREATIVE);
        // setGameMode re-applies creative invulnerability; clear it again so nothing downstream is skewed (the kill
        // itself is dealt TO the giant, not the player, so this is belt-and-braces).
        player.setInvulnerable(false);
        ServerLevel level = helper.getLevel();

        Giant giant = helper.spawn(EntityTypes.GIANT, new BlockPos(1, 2, 1));
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        giant.hurtServer(level, killedByPlayer, Float.MAX_VALUE);

        if (GameTestPlayers.stateOf(player).form() != ZombieForm.GIANT) {
            helper.fail("a creative player killing a vanilla giant must transform into the GIANT form");
            return;
        }
        // ATTR-007 (runtime): the forced refresh applied the +80 GIANT max-health modifier, and the handler healed to full.
        if (player.getMaxHealth() != 100.0F) {
            helper.fail("GIANT form max health should be 100 after the transform, was " + player.getMaxHealth());
            return;
        }
        if (player.getHealth() != player.getMaxHealth()) {
            helper.fail("a giant-kill transform must respawn the player at full health");
            return;
        }
        helper.succeed();
    }
}
