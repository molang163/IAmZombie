package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombiePlayerEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");
    // The five sub-systems were split out of ZombiePlayerEvents into dedicated classes (pure move). Assertions that
    // read the moved method bodies now read the class that owns them; the core ZombiePlayerEvents remains the
    // coordinator (per-tick / damage / login / clone / death) and keeps the sun-burn-tick + head-classification logic.
    private static final Path FORM_ATTRIBUTES = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFormAttributes.java");
    private static final Path REINFORCEMENT = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieReinforcementEvents.java");
    private static final Path GIANT = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/GiantPlayerEvents.java");
    private static final Path SUNLIGHT = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieSunlightEvents.java");
    private static final Path BALANCE_RULES =
            Path.of("src/main/java/dev/molang/iamzombieq/rules/ZombieBalanceRules.java");

    @Test
    void zombifiedPiglinAndGiantGameplayHooksAreWired() throws IOException {
        String source = Files.readString(SOURCE);
        String reinforcement = Files.readString(REINFORCEMENT);
        String giant = Files.readString(GIANT);
        String formAttributes = Files.readString(FORM_ATTRIBUTES);

        assertTrue(reinforcement.contains("ZombifiedPiglin"), "zombified piglin allies should be wired");
        assertTrue(source.contains("ZombieBalanceRules.hasFireResistance(data.state().form())"), "fire resistance should use the balance rule");
        assertTrue(reinforcement.contains("piglin.startPersistentAngerTimer()"), "nearby zombified piglins should join combat with persistent anger");
        assertTrue(source.contains("EntityTypes.GIANT"), "creative Giant kill should recognize vanilla Giant");
        assertTrue(giant.contains("GiantRules.giantAutoDamageRadius"), "giant area pressure should use balance rules");
        assertTrue(formAttributes.contains("case GIANT_MAX_HEALTH -> Attributes.MAX_HEALTH"),
                "the giant max-health semantic should map to the Minecraft max-health attribute");
        assertTrue(source.contains("canTransformFromGiantKill"), "creative-only Giant transform helper should be used");
    }

    @Test
    void passiveEffectsUseSharedRefreshHelperWithoutLosingTheirDistinctGates() throws IOException {
        String source = Files.readString(SOURCE);
        String passive = SourceScan.stripComments(
                SourceScan.methodBody(source, "private static void applyPassiveFormAbilities"));

        assertTrue(source.contains("private static boolean refreshEffectIfExpiring"),
                "passive effect renewal should use one shared helper");
        String refresh = SourceScan.methodBody(source, "private static boolean refreshEffectIfExpiring");
        String compactRefresh = SourceScan.compact(SourceScan.stripComments(refresh));
        assertTrue(compactRefresh.contains(
                        "activeEffect.getDuration()>=ZombieBalanceRules.EFFECT_REFRESH_MARGIN_TICKS"),
                "the helper should compare against the named rules margin at the duration gate");
        assertTrue(refresh.contains("player.getEffect(effect)") && refresh.contains("player.addEffect("),
                "the helper should inspect and renew the requested effect");
        assertEquals(2, SourceScan.countOccurrences(passive, "refreshEffectIfExpiring("),
                "night vision and fire resistance should share the helper exactly once each");

        String drowned = SourceScan.blockBody(
                passive, "if (data.state().form() == ZombieForm.DROWNED)");
        String wet = SourceScan.blockBody(drowned, "if (player.isInWaterOrRain())");
        assertTrue(wet.contains("refreshEffectIfExpiring(player, MobEffects.NIGHT_VISION, 20 * 15)"),
                "drowned night vision should retain its water-or-rain prerequisite");
        String fireResistance = SourceScan.blockBody(
                passive, "if (ZombieBalanceRules.hasFireResistance(data.state().form()))");
        assertTrue(fireResistance.contains("ZombieBalanceRules.hasFireResistance(data.state().form())"),
                "fire resistance should be gated by the balance rule");
        String renewedFireResistance = SourceScan.blockBody(
                fireResistance,
                "if (refreshEffectIfExpiring(player, MobEffects.FIRE_RESISTANCE, 20 * 13))");
        assertTrue(renewedFireResistance.contains("player.clearFire()"),
                "fire resistance should still clear fire only after a successful renewal");
        assertFalse(compactRefresh.contains("activeEffect.getDuration()>=220"),
                "the duration gate must not fall back to the raw refresh threshold");
    }

    @Test
    void playerBalanceValuesUseNamedRulesConstantsAtTheOriginalCallSites() throws IOException {
        String source = Files.readString(SOURCE);
        String sunlight = Files.readString(SUNLIGHT);

        String incomingDamage = SourceScan.methodBody(source, "public static void onIncomingDamage");
        assertTrue(incomingDamage.contains(
                        "MobEffects.HUNGER, ZombieBalanceRules.HUSK_MELEE_HUNGER_DURATION_TICKS, 0"),
                "the husk melee effect should use the named duration at the existing call site");
        assertFalse(incomingDamage.contains("MobEffects.HUNGER, 20 * 15, 0"),
                "the husk melee call site should not retain the raw duration");
        String livingDeath = SourceScan.methodBody(source, "public static void onLivingDeath");
        assertTrue(livingDeath.contains(
                        "player.getMaxHealth() * ZombieBalanceRules.EVOLUTION_RESPAWN_HEALTH_FRACTION"),
                "in-place evolution should use the named health fraction without moving the setHealth call");
        assertFalse(livingDeath.contains("player.getMaxHealth() * 0.5F"),
                "the evolution health write should not retain the raw fraction");
        String ignite = SourceScan.methodBody(sunlight, "static void igniteSunlightBurn");
        assertTrue(ignite.contains("player.igniteForSeconds(ZombieBalanceRules.SUNLIGHT_BURN_DURATION_SECONDS)"),
                "sunlight ignition should use the named duration at the existing call site");
        assertFalse(ignite.contains("player.igniteForSeconds(8.0F)"),
                "the sunlight ignition call should not retain the raw duration");
    }

    @Test
    void giantPassiveDestructionIsPerTickWithSweepVolumeAndDropFreeFlag34() throws IOException {
        // 设计指南 §4.2/§5.1: passive walk-destruction runs every tick (not every 20), spans a sweep volume from the
        // last to current position (no fast-run gaps), and removes blocks via setBlock(AIR, flag 34) = no drops, no
        // neighbour updates. The stomp-damage aura stays on the 20-tick cadence.
        String source = Files.readString(GIANT);
        String giantTick = SourceScan.methodBody(source, "void handleGiantTick");
        assertTrue(giantTick.contains("smashBlocksWhileWalking(level, player)"), "passive destruction should run every tick");
        assertTrue(giantTick.contains("player.tickCount % 20 == 0"), "the stomp-damage aura should stay on the 20-tick cadence");
        assertFalse(source.contains("level.destroyBlock(cursor, true, player)"), "the old drop+neighbour-update destroyBlock(true) must be gone");
        String smash = SourceScan.methodBody(source, "private static void smashBlocksWhileWalking");
        // The sweep still spans last->current position (to catch fast-run gaps), but the delta is now CLAMPED to the
        // per-tick reach so a STALE GIANT_LAST_POS (a long teleport / dimension change never seeds or clears the map)
        // cannot expand the sweep AABB across an unbounded, mostly-air volume every tick (#1 DoS fix).
        assertTrue(smash.contains(".expandTowards(deltaX, deltaY, deltaZ)"),
                "the sweep volume should span last->current position (via the clamped deltas) to catch fast-run gaps");
        assertTrue(smash.contains("Math.min(reachH, last.x - now.x)") && smash.contains("Math.min(reachV, last.y - now.y)"),
                "the sweep delta must be clamped to the giant's per-tick reach to bound a stale-teleport sweep (#1)");
        assertTrue(smash.contains("GiantRules.GIANT_PASSIVE_MAX_HARDNESS"),
                "passive walking should use the stone-tier hardness cap so it razes terrain/villages while harder blocks (deepslate/obsidian) still stop it");
        String kernel = SourceScan.methodBody(source, "private static int crushGiantBlocks");
        assertTrue(kernel.contains("Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS"),
                "the destruction kernel must use the drop-free, neighbour-update-free flag 34");
        assertTrue(kernel.contains("Blocks.AIR.defaultBlockState()"), "blocks are replaced with air, not destroyBlock");
        assertTrue(kernel.contains("GiantRules.giantCanCrush("), "the crush predicate should gate every block");
        assertTrue(kernel.contains("IAmZombieBlockTags.GIANT_SOFT") && kernel.contains("IAmZombieBlockTags.GIANT_IMMUNE"),
                "the kernel should consult the GIANT_SOFT whitelist and GIANT_IMMUNE blacklist");
    }

    @Test
    void giantHasActiveSwingAoeAndSuffocationImmunity() throws IOException {
        // 设计指南 §4.3 (active 一拳一大片) + §4.2 (suffocation immunity bound to the giant form).
        String source = Files.readString(SOURCE);
        String giant = Files.readString(GIANT);
        assertTrue(giant.contains("public static void onGiantSwing(PlayerInteractEvent.LeftClickBlock event)"),
                "the active left-click AoE handler should exist");
        String swing = SourceScan.methodBody(giant, "public static void onGiantSwing");
        assertTrue(swing.contains("PlayerInteractEvent.LeftClickBlock.Action.START"), "the swing should fire once on click START");
        assertTrue(swing.contains("GIANT_SWING_COOLDOWN"), "the swing should be rate-limited by a cooldown");
        assertTrue(swing.contains("GiantRules.GIANT_SWING_MAX_HARDNESS"), "the swing should use the high hardness cap");
        assertTrue(swing.contains("GiantRules.giantSwingMaxBlocks()"), "the swing should be capped to the per-swing block budget");
        assertTrue(source.contains("event.getSource().is(DamageTypes.IN_WALL)"), "suffocation (IN_WALL) immunity should be wired");
    }

    @Test
    void creativeZombiePlayersRunAllServerRules() throws IOException {
        // N6: creative zombies run every server-side rule; only flight + invulnerability stay creative-inherent.
        String source = Files.readString(SOURCE);
        String shouldApply = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "public static boolean shouldApplyZombieRules")));
        assertFalse(shouldApply.contains("!player.isCreative()"),
                "shouldApplyZombieRules must no longer gate out creative players");
        assertTrue(shouldApply.contains("returnZombiePlayerGates.isServerZombiePlayer(player);"),
                "shouldApplyZombieRules should delegate to the shared server-side admission gate");
        assertFalse(shouldApply.contains("return!ZombiePlayerGates.isServerZombiePlayer(player);"),
                "the shared server-side admission gate must not be inverted");
        assertFalse(shouldApply.contains(".isSpectator("),
                "shouldApplyZombieRules should not duplicate the canonical spectator check");

        String playerTick = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onPlayerTick")));
        assertTrue(playerTick.contains(
                        "if(!(event.getEntity()instanceofServerPlayerplayer)"
                                + "||!ZombiePlayerGates.isZombiePlayer(player)||!player.isAlive()){return;}"),
                "onPlayerTick should preserve ServerPlayer, shared admission, and alive short-circuit order");
        assertFalse(playerTick.contains(".isSpectator("),
                "onPlayerTick should not duplicate the canonical spectator check");
        assertTrue(playerTick.contains("player.isCreative()&&!player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)"),
                "the first early-return (creative non-zombie without data) should remain");
        assertFalse(playerTick.contains("player.isCreative()&&data.state().form()!=ZombieForm.GIANT"),
                "the creative non-giant early-return should be removed so creative zombies run all per-tick logic");
    }

    @Test
    void officialReinforcementAlertRetargetsFormMatchedUndeadWithoutLineOfSight() throws IOException {
        // N7 alert: a single class-filtered AABB scan (not per-block) retargets form-matched undead onto the
        // attacker, even without line of sight. The zombified piglin still gets persistent anger before setTarget.
        String source = Files.readString(REINFORCEMENT);
        String alert = SourceScan.methodBody(source, "private static void alertFormMatchedUndead");
        assertTrue(alert.contains("level.getEntitiesOfClass(Mob.class, area"),
                "the alert should use a single class-filtered AABB scan, not a per-block sweep");
        assertTrue(alert.contains("ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ")
                        && alert.contains("ZombieReinforcementRules.ALERT_BOX_INFLATE_Y"),
                "the alert box dimensions should come from the testable rule");
        assertTrue(alert.contains("candidate.getType() == reinforcementType"),
                "only form-matched undead should be alerted");
        assertTrue(alert.contains("ally.setTarget(attacker)"), "alerted undead should retarget the attacker");
        assertTrue(alert.contains("piglin.startPersistentAngerTimer()"),
                "zombified piglins (neutral) should get persistent anger before retargeting");
        assertTrue(alert.contains("candidate.getTarget() == null"),
                "Fix2: the call-for-help must alert only IDLE kin (target==null), matching vanilla alertOthers");
    }

    @Test
    void officialReinforcementSpawnMirrorsVanillaHardGateAndSpawnChecks() throws IOException {
        // N7 reinforce: HARD + doMobSpawning gate, per-player chance roll, vanilla spawn-placement/spawn-rule checks
        // (solid top + a dark-enough spot via checkSpawnRules' probabilistic uniform[0,7] test, not a flat light<=9),
        // no-player-within-7 + collision checks, mob-cap-ignoring add, and a -0.05 penalty.
        String source = Files.readString(REINFORCEMENT);
        String spawn = SourceScan.methodBody(source, "private static void attemptSpawnReinforcements");
        assertTrue(spawn.contains("ZombieReinforcementRules.canSpawnReinforcements(gameDifficulty(level.getDifficulty()), level.isSpawningMonsters())"),
                "reinforcements should be gated on HARD + the doMobSpawning gamerule via the rule");
        assertTrue(spawn.contains("ZombieReinforcementRules.reinforcementRollSucceeds"),
                "a per-player reinforcement-chance roll should gate spawning");
        assertTrue(spawn.contains("EntitySpawnReason.REINFORCEMENT"), "reinforcements should use the REINFORCEMENT spawn reason");
        assertTrue(spawn.contains("SpawnPlacements.isSpawnPositionOk") && spawn.contains("SpawnPlacements.checkSpawnRules"),
                "solid-top + a dark-enough spot should be enforced by the vanilla spawn-placement/spawn-rule checks");
        assertTrue(spawn.contains("level.hasNearbyAlivePlayer(xt, yt, zt, ZombieReinforcementRules.MIN_PLAYER_DISTANCE)"),
                "no player within 7 blocks should be required");
        assertTrue(spawn.contains("level.noCollision(reinforcement)") && spawn.contains("level.isUnobstructed(reinforcement)"),
                "the spawn must be collision-free / unobstructed");
        assertTrue(spawn.contains("zombie.setBaby(baby)"), "baby reinforcements should spawn when the player is a baby");
        assertTrue(spawn.contains("level.addFreshEntityWithPassengers(reinforcement)"),
                "reinforcements should ignore the mob cap via a direct fresh-entity add");
        assertTrue(spawn.contains("applyReinforcementPenalty(player.getUUID())"),
                "each successful spawn should apply the -0.05 reinforcement penalty");
        assertTrue(spawn.contains("IAmZombieServerConfig.REINFORCEMENT_SPAWN_ATTEMPTS.get()"),
                "the spawn-attempt count should use canonical SERVER authority");
    }

    @Test
    void reinforcementChanceIsTrackedPerPlayerAndClearedOnLogoutAndStop() throws IOException {
        String source = Files.readString(REINFORCEMENT);
        assertTrue(source.contains("Map<UUID, Double> REINFORCEMENT_CHANCE"),
                "the per-player reinforcement chance should be tracked off-entity");
        assertTrue(source.contains("REINFORCEMENT_CHANCE.remove(event.getEntity().getUUID())"),
                "logout should drop the player's reinforcement chance");
        assertTrue(source.contains("REINFORCEMENT_CHANCE.clear()"), "server stop should clear all reinforcement chances");
        // The giant form has no reinforcement, gated by the rule.
        assertTrue(source.contains("ZombieReinforcementRules.hasReinforcementForm(form)"),
                "the giant form should be excluded from reinforcement via the rule");
    }

    @Test
    void firstDrownedEvolutionGrantsOneFullDurabilityTridentOnlyOnce() throws IOException {
        String source = Files.readString(SOURCE);

        // A6: the once-only outcome/flag decision is now a pure rule; the trident side effect stays in the event layer.
        assertTrue(source.contains("ZombieEvolutionRules.resolveFirstEvolutionReward("), "the reward decision should be resolved by the pure rule");
        assertTrue(source.contains("before.receivedFirstDrownedReward()"), "the drowned once-only flag should feed the reward decision");
        assertTrue(source.contains("case TRIDENT"), "drowned evolution should have a trident reward branch");
        assertTrue(source.contains("new ItemStack(Items.TRIDENT)"), "first drowned reward should be a normal full-durability trident stack");
        assertTrue(source.contains("after.withFirstDrownedRewardClaimed()"), "drowned reward flag should be recorded");
    }

    @Test
    void firstHuskEvolutionGrantsRandomizedDesertBundleOnlyOnce() throws IOException {
        String source = Files.readString(SOURCE);

        // A6: the once-only outcome/flag decision is now a pure rule; the desert bundle side effect stays in events.
        assertTrue(source.contains("before.receivedFirstHuskReward()"), "the husk once-only flag should feed the reward decision");
        assertTrue(source.contains("case HUSK_DESERT_BUNDLE"), "husk evolution should have a desert-bundle reward branch");
        assertTrue(source.contains("ZombieBalanceRules.huskFirstRewardBundle(new java.util.Random(player.getRandom().nextLong()))"),
                "first husk reward should use the randomized desert bundle seeded from the player's random source");
        assertTrue(source.contains("after.withFirstHuskRewardClaimed()"), "husk reward flag should be recorded");
        // The old fixed reward should be gone.
        assertFalse(source.contains("new ItemStack(Items.SAND, 16)"), "the old fixed husk reward should be replaced");
    }

    @Test
    void giantContactDestructionUsesSweepBoundsAndPreservesTheFootLayer() throws IOException {
        String source = Files.readString(GIANT);

        assertTrue(source.contains("BlockPos.betweenClosed(sweep)"),
                "giant passive destruction should iterate the scaled body sweep volume, not a circular radius");
        assertTrue(source.contains("GiantRules.giantDestroysBlockLayer(pos.getY(), footY)"),
                "the foot layer should be excluded via the testable bounds helper");
        assertTrue(source.contains("GiantRules.giantCanCrush("),
                "protected-block exclusions should use the testable crush predicate");
        assertFalse(source.contains("pos.distSqr(center) > radius * radius"),
                "the old circular-radius destruction should be removed");
    }

    @Test
    void giantReachStepAndAttackRetainMinecraftAdapterWiring() throws IOException {
        String source = Files.readString(FORM_ATTRIBUTES);
        String rules = Files.readString(BALANCE_RULES);

        // 设计指南 §2.4: scale does not auto-scale these. Exact target/delta behavior belongs to
        // ZombieFormAttributeDeltasTest; this source guard keeps the Minecraft attribute and stable-ID adapter wiring.
        assertTrue(source.contains("case GIANT_BLOCK_INTERACTION_RANGE -> Attributes.BLOCK_INTERACTION_RANGE")
                        && source.contains("case GIANT_BLOCK_INTERACTION_RANGE -> GIANT_BLOCK_RANGE_ID"),
                "the giant block-reach semantic should retain its attribute and modifier ID");
        assertTrue(source.contains("case GIANT_ENTITY_INTERACTION_RANGE -> Attributes.ENTITY_INTERACTION_RANGE")
                        && source.contains("case GIANT_ENTITY_INTERACTION_RANGE -> GIANT_ENTITY_RANGE_ID"),
                "the giant entity-reach semantic should retain its attribute and modifier ID");
        assertTrue(source.contains("case GIANT_STEP_HEIGHT -> Attributes.STEP_HEIGHT")
                        && source.contains("case GIANT_STEP_HEIGHT -> GIANT_STEP_HEIGHT_ID"),
                "the giant step semantic should retain its attribute and modifier ID");
        assertTrue(source.contains("case GIANT_ATTACK_DAMAGE -> GIANT_ATTACK_ID"),
                "the giant attack semantic should retain its modifier ID");
        assertFalse(rules.contains("GiantRules.giantExtraReach()"),
                "the old symmetric extra-reach helper should stay absent");
    }

    @Test
    void difficultyScaledAttackDamageModifierIsRefreshedWithFormAttributes() throws IOException {
        String source = Files.readString(FORM_ATTRIBUTES);

        assertTrue(source.contains("NON_GIANT_ATTACK_DAMAGE_ID"),
                "non-giant forms need a stable attack-damage modifier id");
        assertTrue(source.contains("DIFFICULTY_ATTACK_DAMAGE_ID"), "a stable attack-damage modifier id should exist");
        assertTrue(source.contains("Attributes.ATTACK_DAMAGE"), "the modifier should target ATTACK_DAMAGE");
        assertTrue(source.contains("ZombieDamageRules.attackDamageBonusFraction("),
                "the attack-damage bonus should be difficulty-scaled via the rule and refreshed with form attributes");
        String apply = SourceScan.methodBody(source, "public static void applyFormAttributes");
        assertTrue(apply.contains("ZombieBalanceRules.formAttributeDeltas("),
                "the form refresh should obtain all modifier rows from the pure table");
        assertTrue(apply.contains("for (AttributeDelta delta :"),
                "every table row, including zero-valued cleanup rows, should reach the adapter");
        assertFalse(apply.contains(".filter("), "zero-valued rows must not be filtered before removal");
        assertFalse(source.contains(".setBaseValue("),
                "form attack damage must remain transient and must not overwrite the player's base value");

        String operation = SourceScan.methodBody(source, "private static AttributeModifier.Operation modifierOperation");
        assertTrue(operation.contains("case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE"),
                "flat deltas must retain ADD_VALUE semantics");
        assertTrue(operation.contains("case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE"),
                "baby speed and difficulty attack must retain ADD_MULTIPLIED_BASE semantics");
        String modifier = SourceScan.methodBody(source, "private static void applyModifier");
        assertTrue(modifier.contains("if (amount == 0.0)") && modifier.contains("attribute.removeModifier(id)"),
                "zero rows must remove stale stable-ID modifiers");
        assertTrue(modifier.contains("addOrUpdateTransientModifier"),
                "non-zero rows must remain transient and idempotent");

        assertTrue(source.contains("case NON_GIANT_ATTACK_DAMAGE -> NON_GIANT_ATTACK_DAMAGE_ID")
                        && source.contains("case GIANT_ATTACK_DAMAGE -> GIANT_ATTACK_ID")
                        && source.contains("case DIFFICULTY_ATTACK_DAMAGE -> DIFFICULTY_ATTACK_DAMAGE_ID"),
                "all three attack semantics must retain their stable modifier IDs");
    }

    @Test
    void allFormAttributeSemanticsRetainTheirStableModifierIds() throws IOException {
        String source = Files.readString(FORM_ATTRIBUTES);
        String attributes = SourceScan.compact(
                SourceScan.methodBody(source, "private static AttributeInstance attributeFor"));
        String[] attributeMappings = {
                "case INNATE_ARMOR -> Attributes.ARMOR;",
                "case BABY_SCALE, GIANT_SCALE -> Attributes.SCALE;",
                "case BABY_SPEED -> Attributes.MOVEMENT_SPEED;",
                "case DROWNED_SUBMERGED_MINING -> Attributes.SUBMERGED_MINING_SPEED;",
                "case GIANT_MAX_HEALTH -> Attributes.MAX_HEALTH;",
                "case GIANT_BLOCK_INTERACTION_RANGE -> Attributes.BLOCK_INTERACTION_RANGE;",
                "case GIANT_ENTITY_INTERACTION_RANGE -> Attributes.ENTITY_INTERACTION_RANGE;",
                "case GIANT_STEP_HEIGHT -> Attributes.STEP_HEIGHT;",
                "case GIANT_SAFE_FALL_DISTANCE -> Attributes.SAFE_FALL_DISTANCE;",
                "case NON_GIANT_ATTACK_DAMAGE, GIANT_ATTACK_DAMAGE, DIFFICULTY_ATTACK_DAMAGE"
                        + " -> Attributes.ATTACK_DAMAGE;"
        };
        for (String mapping : attributeMappings) {
            assertTrue(attributes.contains(SourceScan.compact(mapping)),
                    "missing semantic-to-Minecraft-attribute mapping: " + mapping);
        }

        String modifierIds = SourceScan.compact(
                SourceScan.methodBody(source, "private static Identifier modifierId"));
        String[] mappings = {
                "case INNATE_ARMOR -> INNATE_ARMOR_ID;",
                "case BABY_SCALE -> BABY_SCALE_ID;",
                "case BABY_SPEED -> BABY_SPEED_ID;",
                "case DROWNED_SUBMERGED_MINING -> DROWNED_MINING_ID;",
                "case GIANT_MAX_HEALTH -> GIANT_HEALTH_ID;",
                "case GIANT_SCALE -> GIANT_SCALE_ID;",
                "case GIANT_BLOCK_INTERACTION_RANGE -> GIANT_BLOCK_RANGE_ID;",
                "case GIANT_ENTITY_INTERACTION_RANGE -> GIANT_ENTITY_RANGE_ID;",
                "case GIANT_STEP_HEIGHT -> GIANT_STEP_HEIGHT_ID;",
                "case GIANT_SAFE_FALL_DISTANCE -> GIANT_SAFE_FALL_ID;",
                "case NON_GIANT_ATTACK_DAMAGE -> NON_GIANT_ATTACK_DAMAGE_ID;",
                "case GIANT_ATTACK_DAMAGE -> GIANT_ATTACK_ID;",
                "case DIFFICULTY_ATTACK_DAMAGE -> DIFFICULTY_ATTACK_DAMAGE_ID;"
        };
        for (String mapping : mappings) {
            assertTrue(modifierIds.contains(SourceScan.compact(mapping)),
                    "missing stable semantic-to-ID mapping: " + mapping);
        }
        assertEquals(13, SourceScan.countOccurrences(modifierIds, "case"),
                "the adapter should expose exactly one stable-ID case per table row");

        String[] stableIdPaths = {
                "innate_armor",
                "baby_scale",
                "baby_speed",
                "drowned_submerged_mining",
                "giant_health",
                "giant_scale",
                "giant_block_range",
                "giant_entity_range",
                "giant_step_height",
                "giant_safe_fall",
                "non_giant_attack_damage",
                "giant_attack",
                "difficulty_attack_damage"
        };
        for (String path : stableIdPaths) {
            assertEquals(1, SourceScan.countOccurrences(source, "ModIds.id(\"" + path + "\")"),
                    "stable modifier ID must remain declared exactly once: " + path);
        }
    }

    @Test
    void emptyHandWoodenDoorBreakBoostIsWiredWithoutConflictingWithDrownedBranch() throws IOException {
        String source = Files.readString(SOURCE);

        String breakSpeed = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onBreakSpeed"));
        assertTrue(breakSpeed.contains("getMainHandItem().isEmpty()"), "the boost should require an empty main hand");
        assertTrue(breakSpeed.contains("event.getState().is(BlockTags.WOODEN_DOORS)"),
                "the boost should target vanilla wooden doors");
        assertTrue(breakSpeed.contains("ZombieBalanceRules.shouldBoostWoodenDoorBreak("),
                "the boost should be gated by the testable predicate");
        assertTrue(breakSpeed.contains("ZombieBalanceRules.WOODEN_DOOR_BREAK_MULTIPLIER"),
                "the boost should use the balance-rule multiplier");
        // The drowned underwater branch returns before the door branch, so they never stack.
        String drowned = SourceScan.blockBody(
                breakSpeed,
                "if (data.state().form() == ZombieForm.DROWNED"
                        + " && event.getEntity().isUnderWater() && !event.getEntity().onGround())");
        assertTrue(drowned.contains("return;"),
                "the drowned branch should return so the door boost doesn't stack");
        assertTrue(SourceScan.containsInOrder(
                        breakSpeed,
                        "if (data.state().form() == ZombieForm.DROWNED",
                        "boolean mainHandEmpty = event.getEntity().getMainHandItem().isEmpty()"),
                "the drowned branch should be evaluated (and return) before the door branch");
    }

    @Test
    void drownedWetVisionAppliesWhenInWaterOrRainNotJustUnderwater() throws IOException {
        String source = Files.readString(SOURCE);

        String passive = SourceScan.methodBody(source, "private static void applyPassiveFormAbilities");
        assertTrue(passive.contains("player.isInWaterOrRain()"),
                "drowned clear vision should apply in any wet state (water OR rain)");
        assertFalse(passive.contains("player.isUnderWater()"),
                "the drowned vision effect should no longer be gated on full submersion only");
    }

    @Test
    void disguiseMaskDoesNotBlockSunlight() throws IOException {
        String source = Files.readString(SOURCE);

        String classify = SourceScan.stripComments(
                SourceScan.methodBody(source, "static HeadProtection classifyHeadProtection"));
        assertTrue(SourceScan.containsInOrder(
                        classify,
                        "if (headStack.is(IAmZombieItems.DISGUISE_MASK.get()))",
                        "if (headStack.is(Items.CARVED_PUMPKIN)"),
                "the mask check must exist before the pumpkin/helmet check");
        // The mask branch must return NONE (non-sun-blocking).
        String maskBranch = SourceScan.compact(SourceScan.blockBody(
                classify, "if (headStack.is(IAmZombieItems.DISGUISE_MASK.get()))"));
        assertEquals(
                "if(headStack.is(IAmZombieItems.DISGUISE_MASK.get())){returnHeadProtection.NONE;}",
                maskBranch,
                "the mask must classify directly as NONE so it does not block sun");
    }

    @Test
    void firstEvolutionAwardsTheDeathBegetsLifeAdvancementOnNormalToNonNormal() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("isFirstEvolution(data.state(), nextData.state())"),
                "the first-evolution advancement should be gated by the isFirstEvolution helper");
        assertTrue(source.contains("IAmZombieAdvancements.award(player, IAmZombieAdvancements.FIRST_EVOLUTION)"),
                "the first evolution should award FIRST_EVOLUTION");
        // The helper detects leaving the NORMAL form for any non-normal form.
        String helper = SourceScan.methodBody(source, "private static boolean isFirstEvolution");
        assertTrue(helper.contains("before.form() == ZombieForm.NORMAL"), "first evolution starts from the normal form");
        assertTrue(helper.contains("after.form() != ZombieForm.NORMAL"), "first evolution ends in a non-normal form");
    }

    @Test
    void babySpeedModifierRetainsMinecraftAdapterWiring() throws IOException {
        String source = Files.readString(FORM_ATTRIBUTES);

        // Size/form behavior belongs to ZombieFormAttributeDeltasTest; keep only the Minecraft adapter wiring here.
        assertTrue(source.contains("case BABY_SPEED -> Attributes.MOVEMENT_SPEED")
                        && source.contains("case BABY_SPEED -> BABY_SPEED_ID"),
                "the BABY_SPEED semantic must retain its Minecraft attribute and stable ID");
        String attributes = SourceScan.methodBody(source, "private static AttributeInstance attributeFor");
        assertEquals(1, SourceScan.countOccurrences(attributes, "Attributes.MOVEMENT_SPEED"),
                "only the size-keyed baby modifier should touch MOVEMENT_SPEED");
    }

    @Test
    void firstZombifiedPiglinEvolutionGrantsEnchantedGoldSwordAndAdvancement() throws IOException {
        String source = Files.readString(SOURCE);

        // A6: the once-only outcome/flag decision is now a pure rule; the enchanted-sword side effect stays in events.
        assertTrue(source.contains("before.receivedFirstZombifiedPiglinReward()"), "the piglin once-only flag should feed the reward decision");
        assertTrue(source.contains("case ENCHANTED_GOLD_SWORD"), "zombified piglin evolution should have an enchanted-sword reward branch");
        assertTrue(source.contains("new ItemStack(Items.GOLDEN_SWORD)"), "first piglin reward should be a golden sword");
        assertTrue(source.contains("sword.enchant"), "first piglin reward should be enchanted");
        assertTrue(source.contains("after.withFirstZombifiedPiglinRewardClaimed()"), "piglin reward flag should be recorded");
        assertTrue(source.contains("IAmZombieAdvancements.ZOMBIFIED_PIGLIN"), "piglin evolution should award an advancement");
    }

    @Test
    void firstZombieEntryUnlocksCoffinRecipesWhenConfigured() throws IOException {
        String source = Files.readString(SOURCE);
        String config = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java"));

        assertTrue(config.contains("UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN"), "coffin recipe unlock should be configurable");
        assertTrue(source.contains("unlockCoffinRecipes(player)"), "first zombie attach should unlock coffin recipes");
        assertTrue(source.contains("awardRecipesByKey"), "recipe-book unlock should use the server player recipe API");
        assertTrue(source.contains("Registries.RECIPE"), "coffin recipe ids should be typed as recipe resource keys");
        assertTrue(source.contains("iamzombieq.message.coffin.recipes_unlocked"), "first entry should prompt the player about coffin recipes");
        assertTrue(source.contains("coffinRecipe(\"coffin\")"), "coffin recipe should be awarded");
    }

    @Test
    void peacefulDifficultyShowsAndRecordsUnsupportedWarning() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("warnIfPeacefulUnsupported(player)"), "login should check Peaceful unsupported warning");
        assertTrue(source.contains("iamzombieq.message.peaceful_unsupported"), "player-facing Peaceful warning should be localized");
        assertTrue(source.contains("IAmZombieMod.LOGGER.warn"), "Peaceful warning should be recorded in logs");
        assertTrue(source.contains("DifficultyGuardRules.isGameplayEnabled(gameDifficulty("), "warning should be gated by Peaceful difficulty");
    }

    @Test
    void ordinaryDeathCloneImmediatelyAppliesResetZombieForm() throws IOException {
        String source = Files.readString(SOURCE);
        String clonePath = SourceScan.methodBody(source, "public static void onPlayerClone");

        assertTrue(clonePath.contains("event.isWasDeath() ? previous.resetStateForOrdinaryDeath() : previous"), "ordinary death resets to the normal zombie form; a non-death clone (dimension change / End return) preserves the form");
        // FORCED: the respawned/cloned entity has cleared transient modifiers, and a same-form NORMAL->NORMAL death
        // leaves the signature unchanged, so a cache-gated refresh would wrongly skip restoring innate attributes.
        assertTrue(clonePath.contains("refreshFormAttributesForced(player, nextData)"), "clone should force-refresh form attributes (cache-bypassing)");
        assertTrue(clonePath.contains("applyPassiveFormAbilities(player, nextData)"), "clone should immediately apply passive form abilities");
    }

    @Test
    void transformPreAtomicallyGatesGiantKillsAndRealDeathCloneResets() throws IOException {
        String source = Files.readString(SOURCE);
        String clonePath = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onPlayerClone"));
        String livingDeath = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onLivingDeath"));
        String giantPath = SourceScan.blockBody(
                livingDeath,
                "if (event.getEntity() instanceof Giant\n"
                        + "                && event.getEntity().getType() == EntityTypes.GIANT\n"
                        + "                && event.getSource().getEntity() instanceof ServerPlayer killer\n"
                        + "                && ZombieEvolutionRules.canTransformFromGiantKill(killer.isCreative(), "
                        + "BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString()))");

        assertTrue(SourceScan.containsInOrder(
                        giantPath,
                        "PlayerZombieData data = killer.getData(IAmZombieAttachments.PLAYER_ZOMBIE)",
                        "PlayerZombieData nextData = data.withState(",
                        "ZombieEventPublisher.postCancelable(new ZombieTransformPreEvent(",
                        "killer, data.state().form(), nextData.state().form()",
                        "return;",
                        "killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)",
                        "ZombieEventPublisher.post(new ZombieTransformedEvent(",
                        "ZombieFormAttributes.refreshFormAttributesForced(killer, nextData)",
                        "killer.setHealth(killer.getMaxHealth())"),
                "giant Transform Pre must gate the existing single write/Post/attribute/heal sequence");
        assertEquals(1, SourceScan.countOccurrences(giantPath,
                        "killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)"),
                "giant transform must retain exactly one attachment write");

        assertTrue(SourceScan.containsInOrder(
                        clonePath,
                        "PlayerZombieData previous = event.getOriginal().getData(",
                        "PlayerZombieData nextData = event.isWasDeath()",
                        "boolean formChanged = previous.state().form() != nextData.state().form()",
                        "boolean resetCanceled = formChanged && ZombieEventPublisher.postCancelable(",
                        "new ZombieTransformPreEvent(player, previous.state().form(), nextData.state().form())",
                        "if (resetCanceled)",
                        "nextData = previous",
                        "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)",
                        "ZombieSunlightEvents.clearSunFireWindow(event.getEntity().getUUID())",
                        "if (formChanged && !resetCanceled)",
                        "ZombieEventPublisher.post(new ZombieTransformedEvent(",
                        "ZombieFormAttributes.refreshFormAttributesForced(player, nextData)",
                        "applyPassiveFormAbilities(player, nextData)"),
                "death-clone Transform Pre must gate only a real form reset and retain one fresh-holder write");
        assertEquals(1, SourceScan.countOccurrences(clonePath,
                        "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)"),
                "clone pass and veto paths must share exactly one attachment write");

        int clonePre = clonePath.indexOf("new ZombieTransformPreEvent(player");
        assertTrue(clonePre >= 0, "clone must construct Transform Pre with the fresh holder");
        assertFalse(clonePath.substring(0, clonePre).contains("player.getData("),
                "base clone handling must not materialize the fresh holder attachment before Transform Pre");
        assertFalse(source.contains("NeoForge.EVENT_BUS.post"),
                "production Transform events must continue through ZombieEventPublisher");
        assertFalse(SourceScan.compact(SourceScan.stripComments(source)).contains(".syncData("),
                "base handlers must rely on setData automatic sync");
        assertFalse(source.contains("IZombiePlayerAPI") || source.contains("ServerZombiePlayer.of("),
                "handler-local DEC-2 wiring must not mechanically route through the facade");
    }

    @Test
    void evolvePreGateRunsBeforeDeathCancellationAndRewards() throws IOException {
        String source = Files.readString(SOURCE);
        String livingDeath = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onLivingDeath"));
        int evolveStart = livingDeath.indexOf(
                "PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE)");
        assertTrue(evolveStart >= 0, "the player death-evolution path must remain present");
        String evolvePath = livingDeath.substring(evolveStart);

        assertTrue(SourceScan.containsInOrder(
                        evolvePath,
                        "PlayerZombieData nextData = data.withState(result.nextState())",
                        "ZombieEventPublisher.postCancelable(new ZombieEvolvePreEvent(",
                        "player, data.state(), nextData.state(), result.outcome()",
                        "return;",
                        "event.setCanceled(true)",
                        "grantFirstEvolutionReward(player, data, nextData, result)"),
                "Evolve Pre must receive the fully resolved result and veto before death cancellation or rewards");
        assertEquals(1, SourceScan.countOccurrences(
                        evolvePath, "ZombieEventPublisher.postCancelable(new ZombieEvolvePreEvent("),
                "the built-in death-evolution path must post Evolve Pre exactly once");
        assertFalse(source.contains("NeoForge.EVENT_BUS.post"),
                "production Evolve events must continue through ZombieEventPublisher");
        assertFalse(source.contains("IZombiePlayerAPI") || source.contains("ServerZombiePlayer.of("),
                "handler-local DEC-2 wiring must not mechanically route through the facade");
    }

    @Test
    void evolveCommitAndPostOrderingRemainsAtomic() throws IOException {
        String source = Files.readString(SOURCE);
        String livingDeath = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onLivingDeath"));
        int evolveStart = livingDeath.indexOf(
                "PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE)");
        assertTrue(evolveStart >= 0, "the player death-evolution path must remain present");
        String evolvePath = livingDeath.substring(evolveStart);

        assertTrue(SourceScan.containsInOrder(
                        evolvePath,
                        "PlayerZombieData nextData = data.withState(result.nextState())",
                        "ZombieEventPublisher.postCancelable(new ZombieEvolvePreEvent(",
                        "event.setCanceled(true)",
                        "nextData = grantFirstEvolutionReward(player, data, nextData, result)",
                        "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)",
                        "ZombieEventPublisher.post(new ZombieEvolvedEvent(",
                        "ZombieFormAttributes.refreshFormAttributesForced(player, nextData)",
                        "applyPassiveFormAbilities(player, nextData)",
                        "awardEvolutionAdvancement(player, result)",
                        "IAmZombieAdvancements.FIRST_EVOLUTION",
                        "player.setHealth(Math.max(1.0F",
                        "player.setAirSupply(player.getMaxAirSupply())",
                        "player.clearFire()",
                        "player.resetFallDistance()",
                        "player.getFoodData().setFoodLevel(",
                        "player.getFoodData().setSaturation(0.0F)"),
                "accepted evolution must preserve reward -> one write -> Post -> attributes/passives -> "
                        + "advancements -> recovery");
        assertEquals(1, SourceScan.countOccurrences(
                        evolvePath, "player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)"),
                "the accepted evolution path must retain exactly one attachment write");
        assertFalse(SourceScan.compact(evolvePath).contains(".syncData("),
                "the evolution handler must rely on setData automatic sync");
    }

    @Test
    void inPlaceEvolutionDeathImmediatelyAppliesNextZombieForm() throws IOException {
        String source = Files.readString(SOURCE);
        String livingDeath = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onLivingDeath"));

        assertTrue(SourceScan.containsInOrder(
                        livingDeath,
                        "PlayerZombieData nextData = data.withState(result.nextState())",
                        "ZombieFormAttributes.refreshFormAttributesForced(player, nextData)",
                        "applyPassiveFormAbilities(player, nextData)",
                        "player.setHealth(Math.max(1.0F"),
                "in-place evolution should force-refresh attributes and passive abilities before restoring health");
    }

    @Test
    void sunlightExposureMirrorsVanillaMobSunBurnTickInputs() throws IOException {
        String source = Files.readString(SOURCE);
        String sunBurnTick = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "private static boolean isSunBurnTick")));
        String expectedContextConstruction =
                "newSunBurnContext(monstersBurn,brightness,randomValue,canSeeSky,inWaterRainOrPowderSnow)";

        assertTrue(source.contains("player.isAlive()"), "sunlight logic should only run for living players like vanilla mobs");
        assertEquals(1, SourceScan.countOccurrences(sunBurnTick, expectedContextConstruction),
                "gameplay should construct one context with the five named inputs in their exact order");
        assertEquals(1, SourceScan.countOccurrences(sunBurnTick, "newSunBurnContext("),
                "isSunBurnTick should not construct an additional or differently wired sunlight context");
        assertTrue(SourceScan.containsInOrder(
                        sunBurnTick,
                        "EnvironmentAttributes.MONSTERS_BURN",
                        "player.getLightLevelDependentMagicValue()",
                        "if(!monstersBurn||brightness<=0.5F){returnfalse;}",
                        "player.getRandom().nextFloat()",
                        "SUN_BURN_EYE_POS.set(player.getX(),player.getEyeY(),player.getZ())",
                        "player.level().canSeeSky(SUN_BURN_EYE_POS)",
                        "player.isInWaterOrRain()||player.isInPowderSnow||player.wasInPowderSnow",
                        expectedContextConstruction,
                        "ZombieSunlightRules.isVanillaSunBurnTick(context)"),
                "sunlight inputs should retain the vanilla precondition, single RNG draw, environment-read, context, and rule-call order");
        assertEquals(1, SourceScan.countOccurrences(sunBurnTick, "player.getRandom().nextFloat()"),
                "sunlight should consume the player's random source exactly once");
        assertEquals(1, SourceScan.countOccurrences(sunBurnTick, "ZombieSunlightRules.isVanillaSunBurnTick("),
                "gameplay should make one context-based sunlight rule call");
        assertFalse(sunBurnTick.contains("isVanillaSunBurnTick(monstersBurn,brightness,randomValue,"
                        + "canSeeSky,inWaterRainOrPowderSnow)"),
                "gameplay should not call the legacy five-position-argument overload");
        assertFalse(sunBurnTick.contains("->"), "gameplay should not allocate a supplier lambda for the RNG draw");
        // The actual ignition (and its sun-fire window marking) now lives in ZombieSunlightEvents.igniteSunlightBurn.
        String sunlight = Files.readString(SUNLIGHT);
        assertTrue(sunlight.contains("player.igniteForSeconds(ZombieBalanceRules.SUNLIGHT_BURN_DURATION_SECONDS)"),
                "unprotected sunlight should ignite for the named vanilla duration");
    }

    @Test
    void sunlightHeadSlotHandlingKeepsVanillaBlockerShape() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("player.getItemBySlot(EquipmentSlot.HEAD)"), "sun protection should use the vanilla head slot");
        assertTrue(source.contains("if (headStack.isEmpty())"), "only an empty head slot should count as no sun blocker");
        assertTrue(source.contains("return HeadProtection.OTHER_HELMET"), "non-empty non-special head items should still block sunlight like vanilla mobs");
        assertTrue(source.contains("headStack.isDamageableItem()"), "only damageable head blockers should spend durability like vanilla mobs");
    }

    @Test
    void sunlightFireDamageReattributedWithinSunFireWindow() throws IOException {
        String source = Files.readString(SUNLIGHT);

        // Vanilla fire burns with correct timing; on-fire ticks are relabeled to sunlight within a simple
        // sun-fire window (set from the remaining fire ticks at ignition), not via per-tick ownership accounting.
        assertTrue(source.contains("SUNLIGHT_FIRE_UNTIL"), "sunlight fire conversion should track a per-player sun-fire window");
        assertTrue(source.contains("player.getRemainingFireTicks()"), "the window should be sized from the active vanilla fire countdown");
        assertTrue(source.contains("ZombieDamageRules.shouldConvertOnFireDamageToSunlight("), "conversion should delegate to the behavior-tested rule");
        assertTrue(source.contains("event.setCanceled(true)"), "the vanilla on-fire tick should be cancelled when relabeled");
        assertTrue(source.contains("player.damageSources().source(SUNLIGHT_DAMAGE)"), "a converted tick should be re-dealt as the custom sunlight damage type");
        assertFalse(source.contains("SUNLIGHT_FIRE_MARKS"), "the intricate per-tick fire-mark protocol should be removed");
    }

    @Test
    void sunFireWindowIsClearedOnLogoutSoItCannotLeakOrLeakAcrossSessions() throws IOException {
        String source = Files.readString(SUNLIGHT);

        // The sun-fire window is keyed by player UUID; without a logout cleanup it would accumulate for the
        // server's lifetime and could mis-attribute a fresh fire to sunlight after a reconnect within the window.
        assertTrue(source.contains("PlayerEvent.PlayerLoggedOutEvent"), "a logout handler should exist to clean up the sun-fire window");
        assertTrue(source.contains("SUNLIGHT_FIRE_UNTIL.remove(event.getEntity().getUUID())"), "logout should remove the player's sun-fire window entry");
    }

    @Test
    void leavingGiantFormClearsGiantMapsInsideApplyFormAttributesNotAnEventHandler() throws IOException {
        // Gap 2 timing guard: the giant sweep-anchor + swing-cooldown cleanup MUST fire from the !giant branch of
        // ZombieFormAttributes.applyFormAttributes (the signature-cached per-form-change apply site), preserving the
        // pre-split timing byte-for-byte. It must NOT drift up to a @SubscribeEvent handler (that would change WHEN
        // the maps are cleared: applyFormAttributes runs from refreshFormAttributes early-return-gated by signature,
        // not per-event). ZombieFormAttributes is a non-event helper class, so it has no @SubscribeEvent at all.
        String formAttributes = Files.readString(FORM_ATTRIBUTES);
        assertFalse(formAttributes.contains("@SubscribeEvent"),
                "ZombieFormAttributes must stay a non-event helper (no @SubscribeEvent), so form-attr timing can't drift");
        String apply = SourceScan.stripComments(
                SourceScan.methodBody(formAttributes, "public static void applyFormAttributes"));
        String notGiantBranch = SourceScan.compact(SourceScan.blockBody(apply, "if (!giant)"));
        assertEquals(
                "if(!giant){GiantPlayerEvents.cleanupOnFormLeave(player.getUUID());}",
                notGiantBranch,
                "the giant sweep-anchor + swing-cooldown cleanup must run directly in the !giant branch");
        // Preserve the early-return timing contract: refreshFormAttributes is signature-cache gated (returns early
        // when unchanged), while refreshFormAttributesForced is unconditional. Neither may be "fixed" away.
        String refresh = SourceScan.methodBody(formAttributes, "public static void refreshFormAttributes");
        assertTrue(refresh.contains("if (previous != null && previous == signature) {") && refresh.contains("return;"),
                "refreshFormAttributes must keep its signature-cache early-return (unchanged timing)");
    }

    @Test
    void giantEventClassSelfCleansItsMapsOnLogoutAndServerStop() throws IOException {
        String giant = Files.readString(GIANT);
        assertTrue(giant.contains("public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)"),
                "GiantPlayerEvents should self-subscribe a logout cleanup for its maps");
        assertTrue(giant.contains("public static void onServerStopped(ServerStoppedEvent event)"),
                "GiantPlayerEvents should self-subscribe a server-stop cleanup for its maps");
        assertTrue(giant.contains("GIANT_LAST_POS.clear()") && giant.contains("GIANT_SWING_COOLDOWN.clear()"),
                "server stop should clear both giant maps");
    }

    @Test
    void sunlightEventClassSelfCleansItsMapOnServerStop() throws IOException {
        String sunlight = Files.readString(SUNLIGHT);
        assertTrue(sunlight.contains("public static void onServerStopped(ServerStoppedEvent event)"),
                "ZombieSunlightEvents should self-subscribe a server-stop cleanup for its window map");
        assertTrue(sunlight.contains("SUNLIGHT_FIRE_UNTIL.clear()"), "server stop should clear the sun-fire window map");
    }

    @Test
    void coreNoLongerHoldsTheFiveTransientSubsystemMaps() throws IOException {
        // The split must move all five per-player transient maps out of the coordinator. Core keeps only the
        // per-tick eye-position scratch (SUN_BURN_EYE_POS) and the peaceful-warning flag.
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("SUNLIGHT_FIRE_UNTIL"), "sun-fire window map moved to ZombieSunlightEvents");
        assertFalse(source.contains("FORM_ATTRIBUTE_SIGNATURE"), "form-attribute signature map moved to ZombieFormAttributes");
        assertFalse(source.contains("REINFORCEMENT_CHANCE"), "reinforcement chance map moved to ZombieReinforcementEvents");
        assertFalse(source.contains("GIANT_LAST_POS"), "giant sweep-anchor map moved to GiantPlayerEvents");
        assertFalse(source.contains("GIANT_SWING_COOLDOWN"), "giant swing-cooldown map moved to GiantPlayerEvents");
    }

    @Test
    void newEventClassesAreRegisteredButFormAttributesHelperIsNot() throws IOException {
        String mod = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieMod.java"));
        assertTrue(mod.contains("NeoForge.EVENT_BUS.register(ZombieSunlightEvents.class)"),
                "ZombieSunlightEvents (event class) should be registered");
        assertTrue(mod.contains("NeoForge.EVENT_BUS.register(ZombieReinforcementEvents.class)"),
                "ZombieReinforcementEvents (event class) should be registered");
        assertTrue(mod.contains("NeoForge.EVENT_BUS.register(GiantPlayerEvents.class)"),
                "GiantPlayerEvents (event class) should be registered");
        assertFalse(mod.contains("register(ZombieFormAttributes.class)"),
                "ZombieFormAttributes is a non-event helper and must NOT be registered on the event bus");
    }
}
