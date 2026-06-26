package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombiePlayerEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java");

    @Test
    void zombifiedPiglinAndGiantGameplayHooksAreWired() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("ZombifiedPiglin"), "zombified piglin allies should be wired");
        assertTrue(source.contains("ZombieBalanceRules.hasFireResistance(data.state().form())"), "fire resistance should use the balance rule");
        assertTrue(source.contains("piglin.startPersistentAngerTimer()"), "nearby zombified piglins should join combat with persistent anger");
        assertTrue(source.contains("EntityTypes.GIANT"), "creative Giant kill should recognize vanilla Giant");
        assertTrue(source.contains("ZombieBalanceRules.giantAutoDamageRadius"), "giant area pressure should use balance rules");
        assertTrue(source.contains("ZombieBalanceRules.maxHealth(ZombieForm.GIANT) - 20.0"), "giant max health should be derived from balance rules");
        assertTrue(source.contains("canTransformFromGiantKill"), "creative-only Giant transform helper should be used");
    }

    @Test
    void fireResistanceRefreshesBeforeExpiryNotJustWhenAbsent() throws IOException {
        // N3: the zombified-piglin fire-resistance must refresh while the effect is still running but low
        // (duration < 220 of the 260-tick effect), mirroring the drowned night-vision <220 margin, instead of
        // waiting for it to fully expire. The old !hasEffect guard left a one-tick coverage gap each cycle.
        String source = Files.readString(SOURCE);
        String passive = source.substring(
                source.indexOf("private static void applyPassiveFormAbilities"),
                source.indexOf("private static void reinforceZombiePlayer"));
        assertTrue(passive.contains("ZombieBalanceRules.hasFireResistance(data.state().form())"),
                "fire resistance should be gated by the balance rule");
        assertTrue(passive.contains("player.getEffect(MobEffects.FIRE_RESISTANCE)"),
                "fire-res refresh should inspect the active effect's remaining duration");
        assertTrue(passive.contains("fireResistance == null || fireResistance.getDuration() < 220"),
                "fire resistance should refresh at duration < 220, mirroring the night-vision margin");
        assertFalse(passive.contains("!player.hasEffect(MobEffects.FIRE_RESISTANCE)"),
                "the old expire-only !hasEffect guard should be replaced");
        assertTrue(passive.contains("player.clearFire()"), "the fire-res refresh should still douse the player");
    }

    @Test
    void giantPassiveDestructionIsPerTickWithSweepVolumeAndDropFreeFlag34() throws IOException {
        // 设计指南 §4.2/§5.1: passive walk-destruction runs every tick (not every 20), spans a sweep volume from the
        // last to current position (no fast-run gaps), and removes blocks via setBlock(AIR, flag 34) = no drops, no
        // neighbour updates. The stomp-damage aura stays on the 20-tick cadence.
        String source = Files.readString(SOURCE);
        String giantTick = source.substring(
                source.indexOf("private static void handleGiantTick"),
                source.indexOf("private static void damageNearbyAsGiant"));
        assertTrue(giantTick.contains("smashBlocksWhileWalking(level, player)"), "passive destruction should run every tick");
        assertTrue(giantTick.contains("player.tickCount % 20 == 0"), "the stomp-damage aura should stay on the 20-tick cadence");
        assertFalse(source.contains("level.destroyBlock(cursor, true, player)"), "the old drop+neighbour-update destroyBlock(true) must be gone");
        String smash = source.substring(
                source.indexOf("private static void smashBlocksWhileWalking"),
                source.indexOf("private static int crushGiantBlocks"));
        assertTrue(smash.contains(".expandTowards(last.x - now.x, last.y - now.y, last.z - now.z)"),
                "the sweep volume should span last->current position to catch fast-run gaps");
        assertTrue(smash.contains("ZombieBalanceRules.GIANT_PASSIVE_MAX_HARDNESS"),
                "passive walking should use the stone-tier hardness cap so it razes terrain/villages while harder blocks (deepslate/obsidian) still stop it");
        String kernel = source.substring(
                source.indexOf("private static int crushGiantBlocks"),
                source.indexOf("public static void onGiantSwing"));
        assertTrue(kernel.contains("Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS"),
                "the destruction kernel must use the drop-free, neighbour-update-free flag 34");
        assertTrue(kernel.contains("Blocks.AIR.defaultBlockState()"), "blocks are replaced with air, not destroyBlock");
        assertTrue(kernel.contains("ZombieBalanceRules.giantCanCrush("), "the crush predicate should gate every block");
        assertTrue(kernel.contains("IAmZombieBlockTags.GIANT_SOFT") && kernel.contains("IAmZombieBlockTags.GIANT_IMMUNE"),
                "the kernel should consult the GIANT_SOFT whitelist and GIANT_IMMUNE blacklist");
    }

    @Test
    void giantHasActiveSwingAoeAndSuffocationImmunity() throws IOException {
        // 设计指南 §4.3 (active 一拳一大片) + §4.2 (suffocation immunity bound to the giant form).
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("public static void onGiantSwing(PlayerInteractEvent.LeftClickBlock event)"),
                "the active left-click AoE handler should exist");
        String swing = source.substring(
                source.indexOf("public static void onGiantSwing"),
                source.indexOf("private static void applyAddValueModifier"));
        assertTrue(swing.contains("PlayerInteractEvent.LeftClickBlock.Action.START"), "the swing should fire once on click START");
        assertTrue(swing.contains("GIANT_SWING_COOLDOWN"), "the swing should be rate-limited by a cooldown");
        assertTrue(swing.contains("ZombieBalanceRules.GIANT_SWING_MAX_HARDNESS"), "the swing should use the high hardness cap");
        assertTrue(swing.contains("ZombieBalanceRules.giantSwingMaxBlocks()"), "the swing should be capped to the per-swing block budget");
        assertTrue(source.contains("event.getSource().is(DamageTypes.IN_WALL)"), "suffocation (IN_WALL) immunity should be wired");
    }

    @Test
    void creativeZombiePlayersRunAllServerRules() throws IOException {
        // N6: creative zombies run every server-side rule; only flight + invulnerability stay creative-inherent.
        String source = Files.readString(SOURCE);
        String shouldApply = source.substring(
                source.indexOf("private static boolean shouldApplyZombieRules"),
                source.indexOf("private static void giveStartingItems"));
        assertFalse(shouldApply.contains("!player.isCreative()"),
                "shouldApplyZombieRules must no longer gate out creative players");
        assertTrue(shouldApply.contains("!player.level().isClientSide()") && shouldApply.contains("!player.isSpectator()"),
                "the server-side + non-spectator gates should remain");

        String playerTick = source.substring(
                source.indexOf("public static void onPlayerTick"),
                source.indexOf("public static void onBreakSpeed"));
        assertTrue(playerTick.contains("player.isCreative() && !player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)"),
                "the first early-return (creative non-zombie without data) should remain");
        assertFalse(playerTick.contains("player.isCreative() && data.state().form() != ZombieForm.GIANT"),
                "the creative non-giant early-return should be removed so creative zombies run all per-tick logic");
    }

    @Test
    void officialReinforcementAlertRetargetsFormMatchedUndeadWithoutLineOfSight() throws IOException {
        // N7 alert: a single class-filtered AABB scan (not per-block) retargets form-matched undead onto the
        // attacker, even without line of sight. The zombified piglin still gets persistent anger before setTarget.
        String source = Files.readString(SOURCE);
        String alert = source.substring(
                source.indexOf("private static void alertFormMatchedUndead"),
                source.indexOf("private static void attemptSpawnReinforcements"));
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
    }

    @Test
    void officialReinforcementSpawnMirrorsVanillaHardGateAndSpawnChecks() throws IOException {
        // N7 reinforce: HARD + doMobSpawning gate, per-player chance roll, vanilla spawn-placement/spawn-rule checks
        // (solid top + light<=9), no-player-within-7 + collision checks, mob-cap-ignoring add, and a -0.05 penalty.
        String source = Files.readString(SOURCE);
        String spawn = source.substring(
                source.indexOf("private static void attemptSpawnReinforcements"),
                source.indexOf("private static int reinforcementMagnitude"));
        assertTrue(spawn.contains("ZombieReinforcementRules.canSpawnReinforcements(gameDifficulty(level.getDifficulty()), level.isSpawningMonsters())"),
                "reinforcements should be gated on HARD + the doMobSpawning gamerule via the rule");
        assertTrue(spawn.contains("ZombieReinforcementRules.reinforcementRollSucceeds"),
                "a per-player reinforcement-chance roll should gate spawning");
        assertTrue(spawn.contains("EntitySpawnReason.REINFORCEMENT"), "reinforcements should use the REINFORCEMENT spawn reason");
        assertTrue(spawn.contains("SpawnPlacements.isSpawnPositionOk") && spawn.contains("SpawnPlacements.checkSpawnRules"),
                "solid-top + light<=9 should be enforced by the vanilla spawn-placement/spawn-rule checks");
        assertTrue(spawn.contains("level.hasNearbyAlivePlayer(xt, yt, zt, ZombieReinforcementRules.MIN_PLAYER_DISTANCE)"),
                "no player within 7 blocks should be required");
        assertTrue(spawn.contains("level.noCollision(reinforcement)") && spawn.contains("level.isUnobstructed(reinforcement)"),
                "the spawn must be collision-free / unobstructed");
        assertTrue(spawn.contains("zombie.setBaby(baby)"), "baby reinforcements should spawn when the player is a baby");
        assertTrue(spawn.contains("level.addFreshEntityWithPassengers(reinforcement)"),
                "reinforcements should ignore the mob cap via a direct fresh-entity add");
        assertTrue(spawn.contains("applyReinforcementPenalty(player.getUUID())"),
                "each successful spawn should apply the -0.05 reinforcement penalty");
        assertTrue(spawn.contains("IAmZombieConfig.REINFORCEMENT_SPAWN_ATTEMPTS.get()"),
                "the spawn-attempt count should be configurable");
    }

    @Test
    void reinforcementChanceIsTrackedPerPlayerAndClearedOnLogoutAndStop() throws IOException {
        String source = Files.readString(SOURCE);
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

        assertTrue(source.contains("case EVOLVE_TO_DROWNED"), "drowned evolution should have a reward branch");
        assertTrue(source.contains("!before.receivedFirstDrownedReward()"), "drowned reward should only happen once");
        assertTrue(source.contains("new ItemStack(Items.TRIDENT)"), "first drowned reward should be a normal full-durability trident stack");
        assertTrue(source.contains("after.withFirstDrownedRewardClaimed()"), "drowned reward flag should be recorded");
    }

    @Test
    void firstHuskEvolutionGrantsRandomizedDesertBundleOnlyOnce() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("case EVOLVE_TO_HUSK"), "husk evolution should have a reward branch");
        assertTrue(source.contains("!before.receivedFirstHuskReward()"), "husk reward should only happen once");
        assertTrue(source.contains("ZombieBalanceRules.huskFirstRewardBundle(new java.util.Random(player.getRandom().nextLong()))"),
                "first husk reward should use the randomized desert bundle seeded from the player's random source");
        assertTrue(source.contains("after.withFirstHuskRewardClaimed()"), "husk reward flag should be recorded");
        // The old fixed reward should be gone.
        assertFalse(source.contains("new ItemStack(Items.SAND, 16)"), "the old fixed husk reward should be replaced");
    }

    @Test
    void giantContactDestructionUsesSweepBoundsAndPreservesTheFootLayer() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("BlockPos.betweenClosed(sweep)"),
                "giant passive destruction should iterate the scaled body sweep volume, not a circular radius");
        assertTrue(source.contains("ZombieBalanceRules.giantDestroysBlockLayer(pos.getY(), footY)"),
                "the foot layer should be excluded via the testable bounds helper");
        assertTrue(source.contains("ZombieBalanceRules.giantCanCrush("),
                "protected-block exclusions should use the testable crush predicate");
        assertFalse(source.contains("pos.distSqr(center) > radius * radius"),
                "the old circular-radius destruction should be removed");
    }

    @Test
    void giantReachStepAndAttackAreExplicitScaledTargets() throws IOException {
        String source = Files.readString(SOURCE);

        // 设计指南 §2.4: scale does not auto-scale these; each is its own modifier sourced from the balance rules.
        assertTrue(source.contains("GIANT_ENTITY_RANGE_ID") && source.contains("ZombieBalanceRules.giantEntityInteractionRange()"),
                "giant entity reach should target the 18-block balance-rule value");
        assertTrue(source.contains("GIANT_BLOCK_RANGE_ID") && source.contains("ZombieBalanceRules.giantBlockInteractionRange()"),
                "giant block reach should target the 27-block balance-rule value");
        assertTrue(source.contains("GIANT_STEP_HEIGHT_ID") && source.contains("ZombieBalanceRules.giantStepHeight()"),
                "giant step height should be wired so it strides over short walls");
        assertTrue(source.contains("GIANT_ATTACK_ID") && source.contains("ZombieBalanceRules.giantAttackDamage()"),
                "the giant should get a flat attack target, not difficulty scaling");
        assertFalse(source.contains("ZombieBalanceRules.giantExtraReach()"), "the old symmetric extra-reach helper should be gone");
    }

    @Test
    void difficultyScaledAttackDamageModifierIsRefreshedWithFormAttributes() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("DIFFICULTY_ATTACK_DAMAGE_ID"), "a stable attack-damage modifier id should exist");
        assertTrue(source.contains("Attributes.ATTACK_DAMAGE"), "the modifier should target ATTACK_DAMAGE");
        assertTrue(source.contains("ZombieDamageRules.attackDamageBonusFraction(gameDifficulty(player.level().getDifficulty()))"),
                "the attack-damage bonus should be difficulty-scaled via the rule and refreshed with form attributes");
        // It must live inside refreshFormAttributes so it is reapplied on every form refresh.
        String refresh = source.substring(
                source.indexOf("private static void refreshFormAttributes"),
                source.indexOf("private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty"));
        assertTrue(refresh.contains("DIFFICULTY_ATTACK_DAMAGE_ID"),
                "attack-damage modifier should be applied inside refreshFormAttributes");
    }

    @Test
    void emptyHandWoodenDoorBreakBoostIsWiredWithoutConflictingWithDrownedBranch() throws IOException {
        String source = Files.readString(SOURCE);

        String breakSpeed = source.substring(
                source.indexOf("public static void onBreakSpeed"),
                source.indexOf("@SubscribeEvent", source.indexOf("public static void onBreakSpeed")));
        assertTrue(breakSpeed.contains("getMainHandItem().isEmpty()"), "the boost should require an empty main hand");
        assertTrue(breakSpeed.contains("event.getState().is(BlockTags.WOODEN_DOORS)"),
                "the boost should target vanilla wooden doors");
        assertTrue(breakSpeed.contains("ZombieBalanceRules.shouldBoostWoodenDoorBreak("),
                "the boost should be gated by the testable predicate");
        assertTrue(breakSpeed.contains("ZombieBalanceRules.WOODEN_DOOR_BREAK_MULTIPLIER"),
                "the boost should use the balance-rule multiplier");
        // The drowned underwater branch returns before the door branch, so they never stack.
        assertTrue(breakSpeed.indexOf("isUnderWater()") < breakSpeed.indexOf("getMainHandItem"),
                "the drowned branch should be evaluated (and return) before the door branch");
        assertTrue(breakSpeed.contains("return;"), "the drowned branch should return so the door boost doesn't stack");
    }

    @Test
    void drownedWetVisionAppliesWhenInWaterOrRainNotJustUnderwater() throws IOException {
        String source = Files.readString(SOURCE);

        String passive = source.substring(
                source.indexOf("private static void applyPassiveFormAbilities"),
                source.indexOf("private static void reinforceZombiePlayer"));
        assertTrue(passive.contains("player.isInWaterOrRain()"),
                "drowned clear vision should apply in any wet state (water OR rain)");
        assertFalse(passive.contains("player.isUnderWater()"),
                "the drowned vision effect should no longer be gated on full submersion only");
    }

    @Test
    void disguiseMaskDoesNotBlockSunlight() throws IOException {
        String source = Files.readString(SOURCE);

        String classify = source.substring(
                source.indexOf("private static HeadProtection classifyHeadProtection"),
                source.indexOf("private static BiomeContext biomeContext"));
        int maskCheck = classify.indexOf("IAmZombieItems.DISGUISE_MASK.get()");
        int pumpkinCheck = classify.indexOf("Items.CARVED_PUMPKIN");
        assertTrue(maskCheck >= 0, "the disguise mask should be special-cased in head-protection classification");
        assertTrue(maskCheck < pumpkinCheck, "the mask check must come before the pumpkin/helmet check");
        // The mask branch must return NONE (non-sun-blocking).
        String maskBranch = classify.substring(maskCheck, classify.indexOf("}", maskCheck));
        assertTrue(maskBranch.contains("HeadProtection.NONE"), "the mask must classify as NONE so it does not block sun");
    }

    @Test
    void firstEvolutionAwardsTheDeathBegetsLifeAdvancementOnNormalToNonNormal() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("isFirstEvolution(data.state(), nextData.state())"),
                "the first-evolution advancement should be gated by the isFirstEvolution helper");
        assertTrue(source.contains("IAmZombieAdvancements.award(player, IAmZombieAdvancements.FIRST_EVOLUTION)"),
                "the first evolution should award FIRST_EVOLUTION");
        // The helper detects leaving the NORMAL form for any non-normal form.
        String helper = source.substring(
                source.indexOf("private static boolean isFirstEvolution"),
                source.indexOf("private static void awardEvolutionAdvancement"));
        assertTrue(helper.contains("before.form() == ZombieForm.NORMAL"), "first evolution starts from the normal form");
        assertTrue(helper.contains("after.form() != ZombieForm.NORMAL"), "first evolution ends in a non-normal form");
    }

    @Test
    void babySpeedBoostIsKeyedOnSizeNotForm() throws IOException {
        String source = Files.readString(SOURCE);

        // The baby movement-speed boost in refreshFormAttributes is gated only on size==BABY, so it applies to
        // every form (including baby drowned/husk/zombified piglin). Nothing in the form refresh clears or
        // overrides MOVEMENT_SPEED for a specific form.
        String refresh = source.substring(
                source.indexOf("private static void refreshFormAttributes"),
                source.indexOf("private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty"));
        assertTrue(refresh.contains("BABY_SPEED_ID"), "the baby speed modifier should be applied in the form refresh");
        String babySpeedLine = refresh.substring(refresh.indexOf("BABY_SPEED_ID"));
        babySpeedLine = babySpeedLine.substring(0, babySpeedLine.indexOf(";"));
        assertTrue(babySpeedLine.contains("ZombieSize.BABY"), "baby speed should be keyed on the BABY size");
        assertFalse(babySpeedLine.contains("ZombieForm"), "baby speed must not be gated on a specific form");
        // MOVEMENT_SPEED should only ever be touched by the size-keyed baby modifier (no form-specific override).
        assertEquals(1, countOccurrences(refresh, "Attributes.MOVEMENT_SPEED"),
                "only the size-keyed baby modifier should touch MOVEMENT_SPEED");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test
    void firstZombifiedPiglinEvolutionGrantsEnchantedGoldSwordAndAdvancement() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("case EVOLVE_TO_ZOMBIFIED_PIGLIN"), "zombified piglin evolution should have a reward branch");
        assertTrue(source.contains("!before.receivedFirstZombifiedPiglinReward()"), "zombified piglin reward should only happen once");
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
        assertTrue(source.contains("oak_coffin"), "oak coffin recipe should be included");
        assertTrue(source.contains("warped_coffin"), "warped coffin recipe should be included");
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
        String clonePath = source.substring(
                source.indexOf("public static void onPlayerClone"),
                source.indexOf("@SubscribeEvent", source.indexOf("public static void onPlayerClone"))
        );

        assertTrue(clonePath.contains("event.isWasDeath() ? previous.resetStateForOrdinaryDeath() : previous"), "ordinary death resets to the normal zombie form; a non-death clone (dimension change / End return) preserves the form");
        // FORCED: the respawned/cloned entity has cleared transient modifiers, and a same-form NORMAL->NORMAL death
        // leaves the signature unchanged, so a cache-gated refresh would wrongly skip restoring innate attributes.
        assertTrue(clonePath.contains("refreshFormAttributesForced(player, nextData)"), "clone should force-refresh form attributes (cache-bypassing)");
        assertTrue(clonePath.contains("applyPassiveFormAbilities(player, nextData)"), "clone should immediately apply passive form abilities");
    }

    @Test
    void inPlaceEvolutionDeathImmediatelyAppliesNextZombieForm() throws IOException {
        String source = Files.readString(SOURCE);
        String evolutionDeathPath = source.substring(
                source.indexOf("PlayerZombieData nextData = data.withState(result.nextState())"),
                source.indexOf("player.setHealth(Math.max(1.0F")
        );

        assertTrue(evolutionDeathPath.contains("refreshFormAttributesForced(player, nextData)"), "in-place evolution should force-refresh form attributes (cache-bypassing, restores innate max-health before setHealth)");
        assertTrue(evolutionDeathPath.contains("applyPassiveFormAbilities(player, nextData)"), "in-place evolution should immediately apply passive form abilities");
    }

    @Test
    void sunlightExposureMirrorsVanillaMobSunBurnTickInputs() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("player.isAlive()"), "sunlight logic should only run for living players like vanilla mobs");
        assertTrue(source.contains("EnvironmentAttributes.MONSTERS_BURN"), "sunlight should respect the vanilla monster-burn environment attribute");
        assertTrue(source.contains("player.getLightLevelDependentMagicValue()"), "sunlight should use vanilla brightness sampling");
        // Eye-height sky check now uses a reused MutableBlockPos field (set with floored doubles == BlockPos.containing).
        assertTrue(source.contains("SUN_BURN_EYE_POS.set(player.getX(), player.getEyeY(), player.getZ())"), "sky visibility should be checked at eye height like vanilla mobs");
        assertTrue(source.contains("player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow"), "water, rain, and powder snow should block sunlight burning like vanilla mobs");
        assertTrue(source.contains("ZombieSunlightRules.isVanillaSunBurnTick("), "sunlight tick logic should delegate to the behavior-tested vanilla parity rule");
        assertTrue(source.contains("player.getRandom().nextFloat()"), "sunlight random tick chance should use the player's random source");
        assertTrue(source.contains("player.level().canSeeSky(SUN_BURN_EYE_POS)"), "sunlight should require direct sky visibility like vanilla mobs");
        // The random tick chance must still be gated behind the vanilla monster-burn + brightness preconditions so
        // the float overload consumes the player RNG exactly as the previous capturing DoubleSupplier did.
        assertTrue(source.contains("if (!monstersBurn || brightness <= 0.5F)"), "the RNG short-circuit before the float overload must preserve vanilla precondition gating");
        assertTrue(source.contains("player.igniteForSeconds(8.0F)"), "unprotected sunlight should ignite for the vanilla duration");
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
        String source = Files.readString(SOURCE);

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
        String source = Files.readString(SOURCE);

        // The sun-fire window is keyed by player UUID; without a logout cleanup it would accumulate for the
        // server's lifetime and could mis-attribute a fresh fire to sunlight after a reconnect within the window.
        assertTrue(source.contains("PlayerEvent.PlayerLoggedOutEvent"), "a logout handler should exist to clean up the sun-fire window");
        assertTrue(source.contains("SUNLIGHT_FIRE_UNTIL.remove(event.getEntity().getUUID())"), "logout should remove the player's sun-fire window entry");
    }
}
