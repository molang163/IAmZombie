package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieMountEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMountEvents.java");
    // The horse/nautilus death-conversion paths live in the shared infection pipeline.
    private static final Path INFECTION_EVENTS = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");
    private static final Path ATTACHMENTS = Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java");
    private static final Path TARGET_RULES = Path.of("src/main/java/dev/molang/iamzombieq/rules/mount/BigZombieTargetRules.java");
    private static final Path MOUNT_KIND = Path.of("src/main/java/dev/molang/iamzombieq/rules/mount/MountKind.java");

    @Test
    void spiderMountAttachmentIsSyncedToTheClientSoRidingIsNotBlockedClientSide() throws IOException {
        String source = Files.readString(ATTACHMENTS);
        String compact = SourceScan.compact(SourceScan.stripComments(source));
        String spiderMountRegistration =
                "publicstaticfinalSupplier<AttachmentType<SpiderMountData>>SPIDER_MOUNT="
                        + "ATTACHMENTS.register(\"spider_mount\",()->AttachmentType"
                        + ".builder(()->SpiderMountData.DEFAULT)"
                        + ".serialize(newSpiderMountDataSerializer())"
                        + ".sync(SpiderMountDataSync.INSTANCE)"
                        + ".build());";
        assertEquals(1, SourceScan.countOccurrences(compact, spiderMountRegistration),
                "SPIDER_MOUNT must retain its unique registration, serializer, and client sync chain");
        assertTrue(source.contains("class SpiderMountDataSync implements StreamCodec<RegistryFriendlyByteBuf, SpiderMountData>"),
                "a StreamCodec must back the spider mount sync");
        assertTrue(source.contains("input.readUtf()") && source.contains("output.writeUtf(value.ownerUuid())"),
                "the sync codec must round-trip the owner uuid");
        // Save format unchanged: the disk serializer still uses the "owner" string key.
        assertTrue(source.contains("input.getStringOr(\"owner\", \"\")") && source.contains("output.putString(\"owner\""),
                "the on-disk serializer (save format) must be unchanged");
    }

    @Test
    void clientMountAdmissionReturnsBeforeEveryGameplayRead() throws IOException {
        String source = Files.readString(SOURCE);
        String method = compactMethod(source, "public static void onEntityMount");
        String serverAuthoritativeEntry =
                "publicstaticvoidonEntityMount(EntityMountEventevent){"
                        + "if(!event.isMounting()"
                        + "||!(event.getEntityMounting()instanceofPlayerplayer)"
                        + "||player.level().isClientSide()"
                        + "||!isZombiePlayer(player)){return;}";

        assertTrue(method.startsWith(serverAuthoritativeEntry),
                "client mount notifications must return before zombie, attachment, config, or rule reads");
        assertEquals(1, SourceScan.countOccurrences(method, "player.level().isClientSide()"),
                "onEntityMount must have one explicit logical-client admission boundary");
        assertFalse(method.contains("26.2.0.12")
                        || method.contains("ClientboundSetPassengersPacket")
                        || method.contains("schedule(")
                        || method.contains("sleep("),
                "the authority boundary must not add loader-specific, delayed, or passenger-republish repair");
    }

    @Test
    void serverMountAdmissionRulesRemainComplete() throws IOException {
        String source = Files.readString(SOURCE);
        String method = compactMethod(source, "public static void onEntityMount");

        assertTrue(method.contains(
                        "Entitymounted=event.getEntityBeingMounted();"
                                + "if(mounted==null){return;}"
                                + "MountKindmountKind=mountKindFor(mounted);"
                                + "if(!ZombieMountRules.canMount("
                                + "true,zombieSize(player),mountKind,spiderOwnedBy(mounted,player))){"
                                + "event.setCanceled(true);return;}"),
                "server admission must retain mount kind, size, and owned-spider gates");
        assertTrue(method.contains(
                        "if(mountKind==MountKind.BIG_ZOMBIE"
                                + "&&mountedinstanceofZombiebigZombie"
                                + "&&isBigZombieProvokedBy(bigZombie,player)){event.setCanceled(true);}"),
                "the server-side provoked-big-zombie backstop must remain");
        assertFalse(source.contains("net.minecraft.client"),
                "the common event subscriber must remain safe to load on a dedicated server");
    }

    @Test
    void spiderTamingAccumulatesProgressAndOnlyBindsOwnerOnSuccess() throws IOException {
        // Taming is no longer instant. Each feed consumes food + gives feedback and accumulates progress;
        // ownership is only bound when the progress threshold is reached.
        String source = Files.readString(SOURCE);
        String method = SourceScan.methodBody(source, "private static void handleSpiderFood");
        // Progress accumulation drives the tame; ownership binding is gated on spiderIsTamed.
        assertTrue(method.contains("ZombieMountRules.spiderTameProgressAfterFeed(data.tameProgress()"),
                "each feed should accumulate taming progress via ZombieMountRules");
        assertTrue(method.contains("ZombieMountRules.spiderIsTamed(nextProgress)"),
                "ownership should only bind once progress reaches the tame threshold");
        assertTrue(method.contains("SpiderMountData.ownedBy(player.getUUID())"), "a successful tame should set the spider owner");
        assertTrue(method.contains("data.withProgress(nextProgress)"), "an incomplete tame should persist accumulated progress");
        assertTrue(method.contains("spider.setPersistenceRequired()"), "a tamed spider should be persistent");
        assertTrue(method.contains("spider.setTarget(null)"), "feeding should drop the spider's aggro");
        // Food is always consumed + feedback always given, so a feed is never silently dropped.
        assertTrue(method.contains("stack.consume(1, player)"), "every taming feed should consume the food");
        assertTrue(method.contains("spider.playSound(SoundEvents.GENERIC_EAT.value()"), "every feed should give audible feedback");
        assertTrue(method.contains("iamzombieq.message.mount.spider_tamed"), "a successful tame should send the tamed message");
        assertTrue(method.contains("iamzombieq.message.mount.spider_taming"), "an in-progress feed should send taming-progress feedback");
    }

    @Test
    void newSpiderTamingTranslationKeyIsLocalised() throws IOException {
        String en = Files.readString(Path.of("src/main/resources/assets/iamzombieq/lang/en_us.json"));
        String zh = Files.readString(Path.of("src/main/resources/assets/iamzombieq/lang/zh_cn.json"));
        assertTrue(en.contains("\"iamzombieq.message.mount.spider_taming\""),
                "the new spider_taming progress key must be present in en_us");
        assertTrue(zh.contains("\"iamzombieq.message.mount.spider_taming\""),
                "the new spider_taming progress key must be present in zh_cn");
        // Existing translation keys are part of the compatibility surface and must not be removed.
        assertTrue(en.contains("\"iamzombieq.message.mount.spider_tamed\""), "existing spider_tamed key must remain");
    }

    @Test
    void creativePlayersStillFollowZombieMountRules() throws IOException {
        String source = Files.readString(SOURCE);
        String method = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "private static boolean isZombiePlayer")));
        assertTrue(method.contains("returnZombiePlayerGates.isZombiePlayer(player);"),
                "the mount admission helper should delegate to the shared zombie-player gate");
        assertFalse(method.contains("return!ZombiePlayerGates.isZombiePlayer(player);"),
                "the shared admission gate must not be inverted");
        assertFalse(method.contains(".isSpectator("),
                "the mount helper should not duplicate the canonical spectator check");
        assertFalse(method.contains("isCreative"), "creative players must follow zombie mount rules");
    }

    @Test
    void striderMountKindKeepsVanillaSteeringWiring() throws IOException {
        String mountKind = Files.readString(MOUNT_KIND);
        assertTrue(mountKind.contains("STRIDER"), "MountKind must include STRIDER");

        String events = Files.readString(SOURCE);
        assertTrue(events.contains("mounted instanceof Strider") && events.contains("MountKind.STRIDER"),
                "mountKindFor must detect striders");
        // Vanilla ItemSteerable steers striders; the mod must not drive them in onEntityTick.
        String tick = SourceScan.methodBody(events, "public static void onEntityTick");
        assertTrue(!tick.contains("Strider"), "the mod must not override driveMount for striders (vanilla ItemSteerable steers them)");
    }

    @Test
    void babyOnlyZombieAndChickenMountsAreWiredToInteractionAndMountGuard() throws IOException {
        String source = Files.readString(SOURCE);
        String stripped = SourceScan.stripComments(source);
        String tick = compactMethod(source, "public static void onEntityTick");

        assertTrue(source.contains("EntityMountEvent"), "mount guard should prevent non-baby bypasses");
        assertTrue(source.contains("Chicken"), "chicken mount interaction should be wired");
        assertTrue(source.contains("MountKind.BIG_ZOMBIE"), "big zombie mount kind should be used at runtime");
        assertTrue(source.contains("MountKind.CHICKEN"), "chicken mount kind should be used at runtime");
        assertTrue(tick.contains(
                        "zombie.getFirstPassenger()instanceofPlayerplayer&&RideHelper.isBabyZombieRider(player)"),
                "onEntityTick should delegate baby-rider admission directly to RideHelper");
        assertFalse(tick.contains("isBabyZombiePlayer("),
                "onEntityTick should not call the deleted duplicate helper");
        assertFalse(SourceScan.compact(stripped).contains("privatestaticbooleanisBabyZombiePlayer("),
                "ZombieMountEvents should no longer define its own baby-zombie-player predicate");
    }

    @Test
    void simpleMountHandlersKeepTheirDedicatedGatesBeforeTheSharedSuffix() throws IOException {
        String source = Files.readString(SOURCE);
        String bigZombie = compactMethod(source, "private static void handleBigZombieInteract");
        String chicken = compactMethod(source, "private static void handleChickenInteract");

        String bigCall = "completeSimpleMountInteraction("
                + "event,player,zombie,MountKind.BIG_ZOMBIE,true);";
        assertTrue(SourceScan.containsInOrder(
                        bigZombie,
                        "if(!stack.isEmpty()||!isRideableBigZombie(zombie)){return;}",
                        "if(isBigZombieProvokedBy(zombie,player)){return;}",
                        bigCall),
                "big-zombie empty-hand, rideable, and provoked gates must remain before the shared suffix");
        assertFalse(bigZombie.contains("event.setCanceled(")
                        || bigZombie.contains("event.setCancellationResult(")
                        || bigZombie.contains("startRiding(")
                        || bigZombie.contains("setTarget(null)"),
                "a provoked big zombie must remain unmounted and its click must remain uncancelled");
        assertFalse(bigZombie.contains("ZombieMountRules.canMount(")
                        || bigZombie.contains("startRiding(")
                        || bigZombie.contains("setPersistenceRequired("),
                "the big-zombie handler should delegate only after its dedicated gates");

        String chickenCall = "completeSimpleMountInteraction("
                + "event,player,chicken,MountKind.CHICKEN,false);";
        assertTrue(SourceScan.containsInOrder(
                        chicken,
                        "if(!stack.isEmpty()){return;}",
                        chickenCall),
                "the chicken empty-hand gate must remain before the shared suffix");
        assertFalse(chicken.contains("setTarget(null)"),
                "the chicken handler must not acquire big-zombie clear-target behavior");
        assertFalse(chicken.contains("ZombieMountRules.canMount(")
                        || chicken.contains("startRiding(")
                        || chicken.contains("setPersistenceRequired("),
                "the chicken handler should delegate only after its empty-hand gate");
    }

    @Test
    void sharedSimpleMountSuffixPreservesRuleSideEffectAndCancellationOrder() throws IOException {
        String source = Files.readString(SOURCE);
        String stripped = SourceScan.compact(SourceScan.stripComments(source));
        assertTrue(stripped.contains(
                        "privatestaticvoidcompleteSimpleMountInteraction("
                                + "PlayerInteractEvent.EntityInteractevent,Playerplayer,Mobmount,"
                                + "MountKindmountKind,booleanclearTargetBeforeRiding)"),
                "the common suffix should be a private helper with an allocation-free clear-target flag");

        String shared = compactMethod(source, "private static void completeSimpleMountInteraction");
        assertTrue(shared.contains(
                        "if(!ZombieMountRules.canMount(true,zombieSize(player),mountKind,false)){"
                                + "event.setCanceled(true);"
                                + "event.setCancellationResult(InteractionResult.SUCCESS_SERVER);"
                                + "return;}"
                                + "if(!player.level().isClientSide()){"
                                + "if(clearTargetBeforeRiding){mount.setTarget(null);}"
                                + "player.startRiding(mount,true,true);"
                                + "mount.setPersistenceRequired();}"
                                + "event.setCanceled(true);"
                                + "event.setCancellationResult(InteractionResult.SUCCESS_SERVER);"),
                "the helper should preserve rejection, server-only side effects, optional clear, and final result order");
        assertFalse(shared.contains("->") || shared.contains("Consumer"),
                "the per-interaction suffix must not allocate a lambda or Consumer");
        assertTrue(SourceScan.countOccurrences(stripped, "completeSimpleMountInteraction(") == 3,
                "the private helper should have exactly two callers: big zombie and chicken");
    }

    @Test
    void tamedSpiderMountForcesTheRideSoSneakingDoesNotVetoIt() throws IOException {
        String source = Files.readString(SOURCE);
        String method = SourceScan.methodBody(source, "private static void handleSpiderInteract");

        // The root cause of "spider can't be ridden at all": Entity.canRide refuses to mount while the
        // rider is sneaking, and players sneak to approach hostile spiders. Our rule already approves the
        // ride, so we force it (which still routes through EntityMountEvent -> onEntityMount).
        assertTrue(method.contains("ZombieMountRules.canMount(true, MountKind.SPIDER"),
                "spider mount should still consult the canMount rule before riding");
        assertTrue(method.contains("player.startRiding(spider, true, true)"),
                "a rule-approved tamed-spider ride should be forced so sneaking cannot veto it");
    }

    @Test
    void deliberateMountsUseForcedStartRidingButHorsesStayRuleGated() throws IOException {
        String source = Files.readString(SOURCE);
        String stripped = SourceScan.stripComments(source);
        assertTrue(stripped.contains("private static void completeSimpleMountInteraction"),
                "chicken and big-zombie mounts should share the approved-ride suffix");
        String shared = compactMethod(source, "private static void completeSimpleMountInteraction");

        // Our own deliberate mounts force the ride; normal horses are never force-ridden and are refused.
        assertTrue(shared.contains("player.startRiding(mount,true,true)"),
                "rule-approved chicken and big-zombie mounts should still force the ride");
        assertTrue(source.contains("MountKind.NORMAL_HORSE, false"), "normal horses must still be refused by canMount");
        // The mount guard remains the single rule gate (fires even for forced rides).
        assertTrue(source.contains("ZombieMountRules.canMount(true, zombieSize(player), mountKind, spiderOwnedBy(mounted, player))"),
                "onEntityMount should remain the canMount gate for every mount attempt");
    }

    @Test
    void fullHealthZombieHorseFeedIsAcknowledgedNotSilentlyDropped() throws IOException {
        // Feeding a full-health zombie horse used to do nothing AND not cancel (silent drop). Now the
        // food-in-hand interaction is always cancelled, and at full health it sends feedback (without wasting
        // the food).
        String source = Files.readString(SOURCE);
        String interact = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onEntityInteract"));
        String handler = SourceScan.blockBody(
                interact, "if (event.getTarget() instanceof ZombieHorse zombieHorse)");

        assertTrue(handler.contains("if (isZombieHorseFood(stack))"),
                "the zombie-horse feed handler must gate on holding the food, then branch on health");
        assertTrue(handler.contains("ZombieMountRules.zombieHorseHealAmount("),
                "the existing heal call should obtain its amount from the pure mount rule");
        assertTrue(handler.contains("ZombieFoodRules.SUPER_ROTTEN_FLESH_ID")
                        && handler.contains("\"minecraft:rotten_flesh\""),
                "the event layer should preserve the original two food identities when calling the rule");
        assertFalse(handler.contains("? 10.0F : 4.0F"),
                "the event layer should not retain the raw heal-value choice");
        assertTrue(handler.contains("iamzombieq.message.mount.horse_full_health"),
                "feeding a full-health zombie horse must give feedback instead of silently dropping");
        // The cancel must apply for the whole food-in-hand path (both heal and full-health branches).
        assertTrue(handler.contains("event.setCanceled(true)") && handler.contains("InteractionResult.SUCCESS_SERVER"),
                "the food-in-hand interaction must be cancelled (not silently fall through)");

        String en = Files.readString(Path.of("src/main/resources/assets/iamzombieq/lang/en_us.json"));
        String zh = Files.readString(Path.of("src/main/resources/assets/iamzombieq/lang/zh_cn.json"));
        assertTrue(en.contains("\"iamzombieq.message.mount.horse_full_health\""), "en_us must localise horse_full_health");
        assertTrue(zh.contains("\"iamzombieq.message.mount.horse_full_health\""), "zh_cn must localise horse_full_health");
    }

    @Test
    void chickenAndBigZombieMountsArePersistedAndProtectedFromDespawnWhileRidden() throws IOException {
        // Chicken/big-zombie mounts must persist so they cannot despawn while ridden (spider/horses already do).
        String source = Files.readString(SOURCE);
        String stripped = SourceScan.stripComments(source);
        assertTrue(stripped.contains("private static void completeSimpleMountInteraction"),
                "chicken and big-zombie persistence should live in the shared approved-ride suffix");
        String shared = compactMethod(source, "private static void completeSimpleMountInteraction");
        assertTrue(shared.contains(
                        "player.startRiding(mount,true,true);mount.setPersistenceRequired();"),
                "mounting a chicken or big zombie must mark it persistent immediately after the forced ride");
        assertTrue(compactMethod(source, "private static void handleChickenInteract").contains(
                        "completeSimpleMountInteraction(event,player,chicken,MountKind.CHICKEN,false);"),
                "the chicken handler should reach the persistence suffix without clearing a target");
        assertTrue(compactMethod(source, "private static void handleBigZombieInteract").contains(
                        "completeSimpleMountInteraction(event,player,zombie,MountKind.BIG_ZOMBIE,true);"),
                "the big-zombie handler should reach the persistence suffix with target clearing enabled");

        // Defensive backstop: the MobDespawnEvent handler denies despawn for an actively-serving mod
        // mount; the old MobMixin#removeWhenFarAway injection is gone (the event covers a superset of it).
        assertTrue(source.contains("MobDespawnEvent"),
                "ZombieMountEvents must handle MobDespawnEvent as the despawn backstop for ridden mounts");
        assertTrue(source.contains("MountCapability.activeFor"),
                "the despawn backstop must gate on MountCapability.activeFor (actively-serving mod mounts)");
        assertTrue(source.contains("MobDespawnEvent.Result.DENY"),
                "an actively player-ridden mod mount must have its despawn DENIED");

        String mobMixin = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/mixin/MobMixin.java"));
        assertTrue(mobMixin.contains("getControllingPassenger"),
                "MobMixin must keep the getControllingPassenger injection");
        assertFalse(mobMixin.contains("removeWhenFarAway"),
                "MobMixin must no longer inject removeWhenFarAway (backstop moved to MobDespawnEvent)");
    }

    @Test
    void normalHorseRefusalDoesNotBlockUndeadHorseFeeding() throws IOException {
        // ZombieHorse/SkeletonHorse extend AbstractHorse (siblings of Horse, not subclasses), so they are not
        // instanceof Horse; the early "normal horse refused" block must still exclude them via isNormalHorse so the
        // ZombieHorse feed handler below stays reachable.
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("isNormalHorse(event.getTarget())"),
                "the early horse-refusal block must use the undead-excluding isNormalHorse check, not a bare instanceof Horse");
        assertTrue(source.contains("!(target instanceof ZombieHorse) && !(target instanceof SkeletonHorse)"),
                "isNormalHorse must exclude ZombieHorse and SkeletonHorse so undead horses are not blocked");

        // The ZombieHorse feed handler must still exist and remain reachable (it is gated only by instanceof
        // ZombieHorse, no longer pre-empted by the normal-horse cancel+return).
        String interact = SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onEntityInteract"));
        assertTrue(SourceScan.containsInOrder(
                        interact,
                        "isNormalHorse(event.getTarget())",
                        "event.getTarget() instanceof ZombieHorse zombieHorse"),
                "the zombie-horse feed handler must follow (and be reachable after) the normal-horse refusal block");
    }

    @Test
    void successfulHorseInfectionAwardsTheHorseInfectionAdvancement() throws IOException {
        // The horse death-conversion path and its advancement award live in ZombieInfectionEvents'
        // shared pipeline; the award now runs in the pipeline shell after the conversion callback succeeds.
        String source = Files.readString(INFECTION_EVENTS);

        assertTrue(source.contains("convertHorseToZombieHorse(level, horse, player, pendingHorseHealthRatio)"),
                "horse should convert to a zombie horse on a zombie-player kill");
        assertTrue(source.contains("IAmZombieAdvancements.HORSE_INFECTION"),
                "a successful horse conversion should award the HORSE_INFECTION advancement");
        String pipeline = SourceScan.stripComments(
                SourceScan.methodBody(source, "private static void runInfectionPipeline"));
        String converted = SourceScan.blockBody(pipeline, "if (conversion.getAsBoolean())");
        assertTrue(converted.contains("IAmZombieAdvancements.award(serverPlayer, advancement)"),
                "the advancement should be awarded only after the conversion succeeds");
        assertTrue(converted.contains("attacker instanceof ServerPlayer serverPlayer"),
                "the advancement should be awarded to the responsible server player");
    }

    @Test
    void mountedBigZombieTargetSelectionDelegatesToPureRulesWithoutChangingRuntimeGates() throws IOException {
        String source = Files.readString(SOURCE);
        String rules = SourceScan.stripComments(Files.readString(TARGET_RULES));
        String tick = SourceScan.methodBody(source, "private static void maybeAutoTargetForMountedBigZombie");
        String select = SourceScan.methodBody(source, "private static LivingEntity selectMountedBigZombieTarget");
        String combatAdapter = SourceScan.methodBody(source,
                "private static BigZombieTargetRules.RiderCombatTarget<LivingEntity> riderCombatTarget");
        String directGate = SourceScan.methodBody(source, "private static boolean isMountAttackable");
        String scan = SourceScan.methodBody(source, "private static List<BigZombieTargetRules.Candidate<LivingEntity>> scanMountedBigZombieTargets");
        String compactTick = SourceScan.compact(tick);
        String compactSelect = SourceScan.compact(select);
        String compactScan = SourceScan.compact(scan);

        assertFalse(rules.contains("net.minecraft."), "BigZombieTargetRules must stay Minecraft-free");
        assertTrue(select.contains("BigZombieTargetRules.pickTarget("),
                "the rider-target > rider-attacker > nearby priority must be owned by the pure rule");
        assertTrue(compactSelect.contains(
                        "()->riderCombatTarget(zombie,rider,rider.getLastHurtMob(),rider.tickCount-rider.getLastHurtMobTimestamp()),"
                                + "()->riderCombatTarget(zombie,rider,rider.getLastHurtByMob(),rider.tickCount-rider.getLastHurtByMobTimestamp()),"
                                + "()->scanMountedBigZombieTargets(level,zombie,rider)"),
                "combat targets, timestamps and lazy nearby fallback must stay paired in priority order");
        assertFalse(source.contains("RIDER_COMBAT_MEMORY_TICKS"),
                "the 100-tick combat-memory policy must no longer live in the event layer");

        assertTrue(SourceScan.compact(combatAdapter).contains(
                        "newBigZombieTargetRules.RiderCombatTarget<>(candidate,isMountAttackable(zombie,rider,candidate),ageTicks)"),
                "the live direct-target gate and computed age must feed the pure combat candidate");
        assertTrue(SourceScan.compact(directGate).contains(
                        "candidate!=null&&candidate!=rider&&candidate!=zombie&&candidate.isAlive()"
                                + "&&!MountCapability.isOwnedSpider(candidate,rider.getUUID())"),
                "direct targets must retain the null/rider/mount/alive/owned-spider gates");
        assertFalse(directGate.contains("instanceof Zombie") || directGate.contains("zombie.canAttack"),
                "rider-directed targets must continue to allow fellow zombies without the nearby canAttack gate");

        assertTrue(scan.contains("inflate(ZombieMountRules.BIG_ZOMBIE_AUTO_ATTACK_RANGE)"),
                "the nearby scan range must remain unchanged");
        assertTrue(scan.contains("candidate != rider && candidate.isAlive() && zombie.canAttack(candidate)"),
                "the nearby scan must retain its rider/alive/canAttack gate");
        assertTrue(compactScan.contains(
                        "BigZombieTargetRules.classify(candidateinstanceofAbstractVillager,"
                                + "candidateinstanceofIronGolem,candidateinstanceofMonster,"
                                + "candidateinstanceofZombie,riderOwnedSpider)"),
                "the adapter must map villager/golem/monster/zombie/owned-spider facts in the correct order");
        assertTrue(scan.contains("MountCapability.isOwnedSpider"),
                "the nearby type adapter must still detect and exclude the rider's own spider");
        assertFalse(scan.contains("nearestVillager") || scan.contains("nearestGolem") || scan.contains("nearestMonster"),
                "tier priority and nearest-candidate policy must not remain in the event layer");

        assertTrue(tick.contains("zombie.tickCount % 10 != 0"), "target selection cadence must remain 10 ticks");
        assertTrue(compactTick.contains("current!=null&&current.isAlive()"
                        + "&&ZombieMountRules.bigZombieShouldAutoAttack(Math.sqrt(zombie.distanceToSqr(current)))"),
                "the existing live/in-range target retention rule must remain unchanged");
        assertTrue(compactTick.contains("if(!haveValidTarget){current=selectMountedBigZombieTarget(level,zombie,rider);"
                        + "if(current!=null){zombie.setTarget(current);}}"),
                "selection must only replace an invalid target and must not clear it when no replacement exists");
        assertFalse(tick.contains("setTarget(null)"), "the auto-target loop must not clear an unreplaced target");
        assertTrue(compactTick.contains("current!=null&&current.isAlive()&&current!=rider"
                        + "&&zombie.tickCount%20==0&&zombie.isWithinMeleeAttackRange(current)"),
                "mounted attacks must retain the rider, 20-tick and melee-range gates");
        assertTrue(SourceScan.containsInOrder(
                        tick,
                        "zombie.swing(InteractionHand.MAIN_HAND)",
                        "zombie.doHurtTarget(level, current)"),
                "the mounted zombie must still swing before applying its attack");
    }

    @Test
    void nautilusCanConvertToZombieNautilusOnZombiePlayerKill() throws IOException {
        // The nautilus death-conversion path lives in ZombieInfectionEvents (the shared pipeline home).
        String source = Files.readString(INFECTION_EVENTS);

        assertTrue(source.contains("Nautilus"), "vanilla nautilus should be recognized");
        assertTrue(source.contains("ZombieNautilus"), "zombie nautilus conversion should be created");
        assertTrue(source.contains("EntityTypes.ZOMBIE_NAUTILUS"), "conversion should use vanilla zombie nautilus entity type");
    }

    @Test
    void horseConversionPreservesUsefulHorseState() throws IOException {
        // The horse conversion and its state-copy helper live in ZombieInfectionEvents.
        String source = Files.readString(INFECTION_EVENTS);

        assertTrue(source.contains("copyHorseStateToZombieHorse"), "horse conversion should copy useful state before discarding original");
        assertTrue(source.contains("EquipmentSlot.SADDLE"), "saddles should survive horse infection");
        assertTrue(source.contains("EquipmentSlot.BODY"), "horse armor/body equipment should survive horse infection");
        assertTrue(source.contains("horse.getAge()"), "age should be preserved across conversion");
        assertTrue(source.contains("horse.getHealth() / horse.getMaxHealth()"), "health ratio should be preserved instead of always healing to full");
    }

    @Test
    void horseConversionUsesPreDeathHealthRatioSnapshot() throws IOException {
        // The pre-death capture stays with the incoming-damage event in ZombieMountEvents (bridging
        // through the package-level accessor), while the snapshot map + its consumption live with the horse
        // infection path in ZombieInfectionEvents.
        String mountEvents = Files.readString(SOURCE);
        String infectionEvents = Files.readString(INFECTION_EVENTS);

        assertTrue(mountEvents.contains("onIncomingDamage"), "horse health ratio should be captured before LivingDeathEvent");
        assertTrue(mountEvents.contains("ZombieInfectionEvents.recordPendingHorseHealthRatio(horse.getUUID(), preDamageHorseHealthRatio(horse))"),
                "the capture must feed the infection pipeline's snapshot map through the package-level bridge");
        assertTrue(mountEvents.contains("Math.max(0.0F, horse.getHealth() - event.getAmount())"), "snapshot should account for incoming lethal damage");
        assertTrue(infectionEvents.contains("PENDING_HORSE_HEALTH_RATIOS"), "pre-death horse health ratios should be stored temporarily");
        assertTrue(infectionEvents.contains("pendingHorseHealthRatio"), "conversion should consume the pre-death ratio when available");
        assertTrue(infectionEvents.contains("recordPendingHorseHealthRatio"), "the snapshot map's write accessor should live with the map");
    }

    @Test
    void failedHorseInfectionRollConsumesPendingHealthRatioSnapshot() throws IOException {
        // The consume-then-roll ordering reads as "remove the snapshot BEFORE delegating to the shared
        // pipeline" (whose first gate is the infection roll), so a failed roll still consumed the entry.
        String source = Files.readString(INFECTION_EVENTS);
        String tryInfectHorse = SourceScan.stripComments(
                SourceScan.methodBody(source, "private static void tryInfectHorse"));

        assertTrue(SourceScan.containsInOrder(
                        tryInfectHorse,
                        "Float pendingHorseHealthRatio = PENDING_HORSE_HEALTH_RATIOS.remove(horse.getUUID())",
                        "runInfectionPipeline(event, level, horse, player, EntityTypes.ZOMBIE_HORSE"),
                "pending horse health ratios should be removed before the shared pipeline can reject infection");
        assertTrue(source.contains("ZombieInfectionRules.shouldInfect"), "the shared pipeline should still roll infection chance");
    }

    private static String compactMethod(String source, String signature) {
        return SourceScan.compact(SourceScan.stripComments(SourceScan.methodBody(source, signature)));
    }
}
