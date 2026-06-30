package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZombieMountEventsTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMountEvents.java");
    private static final Path ATTACHMENTS = Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java");
    private static final Path RULES = Path.of("src/main/java/dev/molang/iamzombieq/rules/mount/ZombieMountRules.java");
    private static final Path MOUNT_KIND = Path.of("src/main/java/dev/molang/iamzombieq/rules/mount/MountKind.java");

    @Test
    void spiderMountAttachmentIsSyncedToTheClientSoRidingIsNotBlockedClientSide() throws IOException {
        String source = Files.readString(ATTACHMENTS);
        String spiderMount = source.substring(
                source.indexOf("SPIDER_MOUNT ="),
                source.indexOf(".build());", source.indexOf("SPIDER_MOUNT ="))
        );
        assertTrue(spiderMount.contains(".sync(SpiderMountDataSync.INSTANCE)"),
                "SPIDER_MOUNT must be .sync()'d so the client knows the spider is tamed and does not block the ride");
        assertTrue(source.contains("class SpiderMountDataSync implements StreamCodec<RegistryFriendlyByteBuf, SpiderMountData>"),
                "a StreamCodec must back the spider mount sync");
        assertTrue(source.contains("input.readUtf()") && source.contains("output.writeUtf(value.ownerUuid())"),
                "the sync codec must round-trip the owner uuid");
        // Save format unchanged: the disk serializer still uses the "owner" string key.
        assertTrue(source.contains("input.getStringOr(\"owner\", \"\")") && source.contains("output.putString(\"owner\""),
                "the on-disk serializer (save format) must be unchanged");
    }

    @Test
    void rideStaysServerAuthoritativeViaOnEntityMountGate() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("ZombieMountRules.canMount(true, zombieSize(player), mountKind, spiderOwnedBy(mounted, player))"),
                "onEntityMount must remain the server-authoritative canMount gate even with synced data");
    }

    @Test
    void spiderTamingAccumulatesProgressAndOnlyBindsOwnerOnSuccess() throws IOException {
        // B1: taming is no longer instant. Each feed consumes food + gives feedback and accumulates progress;
        // ownership is only bound when the progress threshold is reached.
        String source = Files.readString(SOURCE);
        String method = source.substring(
                source.indexOf("private static void handleSpiderFood"),
                source.indexOf("private static boolean isSpiderFood")
        );
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
        // Existing keys must not be removed (AGENTS.md preserve translation keys).
        assertTrue(en.contains("\"iamzombieq.message.mount.spider_tamed\""), "existing spider_tamed key must remain");
    }

    @Test
    void creativePlayersStillFollowZombieMountRules() throws IOException {
        String source = Files.readString(SOURCE);
        String method = source.substring(
                source.indexOf("private static boolean isZombiePlayer"),
                source.indexOf("private static boolean isZombieHorseFood")
        );
        assertTrue(method.contains("!player.isSpectator()"), "spectators are still excluded from zombie mount rules");
        assertTrue(!method.contains("isCreative"), "N6: creative players must follow zombie mount rules");
    }

    @Test
    void saddledStriderIsRideableMountKind() throws IOException {
        String mountKind = Files.readString(MOUNT_KIND);
        assertTrue(mountKind.contains("STRIDER"), "MountKind must include STRIDER");

        String rules = Files.readString(RULES);
        assertTrue(rules.contains("case STRIDER -> true;"), "a zombie player may mount a saddled strider");

        String events = Files.readString(SOURCE);
        assertTrue(events.contains("mounted instanceof Strider") && events.contains("MountKind.STRIDER"),
                "mountKindFor must detect striders");
        // Vanilla ItemSteerable steers striders; the mod must not drive them in onEntityTick.
        String tick = events.substring(
                events.indexOf("public static void onEntityTick"),
                events.indexOf("private static boolean isZombiePlayer")
        );
        assertTrue(!tick.contains("Strider"), "the mod must not override driveMount for striders (vanilla ItemSteerable steers them)");
    }

    @Test
    void babyOnlyZombieAndChickenMountsAreWiredToInteractionAndMountGuard() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("EntityMountEvent"), "mount guard should prevent non-baby bypasses");
        assertTrue(source.contains("Chicken"), "chicken mount interaction should be wired");
        assertTrue(source.contains("ZombieSize.BABY"), "runtime mount gate should read player zombie size");
        assertTrue(source.contains("MountKind.BIG_ZOMBIE"), "big zombie mount kind should be used at runtime");
        assertTrue(source.contains("MountKind.CHICKEN"), "chicken mount kind should be used at runtime");
    }

    @Test
    void tamedSpiderMountForcesTheRideSoSneakingDoesNotVetoIt() throws IOException {
        String source = Files.readString(SOURCE);
        String method = source.substring(
                source.indexOf("private static void handleSpiderInteract"),
                source.indexOf("private static void handleSpiderFood")
        );

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

        // Our own deliberate mounts force the ride; normal horses are never force-ridden and are refused.
        assertTrue(source.contains("player.startRiding(chicken, true, true)"), "chicken mount should be forced");
        assertTrue(source.contains("player.startRiding(zombie, true, true)"), "big-zombie mount should be forced");
        assertTrue(source.contains("MountKind.NORMAL_HORSE, false"), "normal horses must still be refused by canMount");
        // The mount guard remains the single rule gate (fires even for forced rides).
        assertTrue(source.contains("ZombieMountRules.canMount(true, zombieSize(player), mountKind, spiderOwnedBy(mounted, player))"),
                "onEntityMount should remain the canMount gate for every mount attempt");
    }

    @Test
    void fullHealthZombieHorseFeedIsAcknowledgedNotSilentlyDropped() throws IOException {
        // B7: feeding a full-health zombie horse used to do nothing AND not cancel (silent drop). Now the
        // food-in-hand interaction is always cancelled, and at full health it sends feedback (without wasting
        // the food).
        String source = Files.readString(SOURCE);
        int handlerStart = source.indexOf("event.getTarget() instanceof ZombieHorse zombieHorse");
        int handlerEnd = source.indexOf("event.getTarget() instanceof Spider spider");
        String handler = source.substring(handlerStart, handlerEnd);

        assertTrue(handler.contains("if (isZombieHorseFood(stack))"),
                "the zombie-horse feed handler must gate on holding the food, then branch on health");
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
        // B4: chicken/big-zombie lacked persistence, so they could despawn while ridden (spider/horses have it).
        String source = Files.readString(SOURCE);
        String chicken = source.substring(
                source.indexOf("private static void handleChickenInteract"),
                source.indexOf("private static void handleSpiderInteract"));
        assertTrue(chicken.contains("player.startRiding(chicken, true, true)") && chicken.contains("chicken.setPersistenceRequired()"),
                "mounting a chicken must mark it persistent so it does not despawn while ridden");

        String bigZombie = source.substring(
                source.indexOf("private static void handleBigZombieInteract"),
                source.indexOf("private static void handleChickenInteract"));
        assertTrue(bigZombie.contains("player.startRiding(zombie, true, true)") && bigZombie.contains("zombie.setPersistenceRequired()"),
                "mounting a big zombie must mark it persistent so it does not despawn while ridden");

        // Defensive backstop: MobMixin#removeWhenFarAway returns false for an actively player-ridden mod mount.
        String mobMixin = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/mixin/MobMixin.java"));
        assertTrue(mobMixin.contains("method = \"removeWhenFarAway\""),
                "MobMixin must override removeWhenFarAway as a despawn backstop for ridden mounts");
        assertTrue(mobMixin.contains("callback.setReturnValue(false)"),
                "a player-ridden mod mount must report removeWhenFarAway = false");
    }

    @Test
    void normalHorseRefusalDoesNotBlockUndeadHorseFeeding() throws IOException {
        // B5: ZombieHorse/SkeletonHorse extend AbstractHorse (siblings of Horse, not subclasses), so they are not
        // instanceof Horse; the early "normal horse refused" block must still exclude them via isNormalHorse so the
        // ZombieHorse feed handler below stays reachable.
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("isNormalHorse(event.getTarget())"),
                "the early horse-refusal block must use the undead-excluding isNormalHorse check, not a bare instanceof Horse");
        assertTrue(source.contains("!(target instanceof ZombieHorse) && !(target instanceof SkeletonHorse)"),
                "isNormalHorse must exclude ZombieHorse and SkeletonHorse so undead horses are not blocked");

        // The ZombieHorse feed handler must still exist and remain reachable (it is gated only by instanceof
        // ZombieHorse, no longer pre-empted by the normal-horse cancel+return).
        int refusalIndex = source.indexOf("isNormalHorse(event.getTarget())");
        int feedIndex = source.indexOf("event.getTarget() instanceof ZombieHorse zombieHorse");
        assertTrue(refusalIndex >= 0 && feedIndex >= 0 && refusalIndex < feedIndex,
                "the zombie-horse feed handler must follow (and be reachable after) the normal-horse refusal block");
    }

    @Test
    void successfulHorseInfectionAwardsTheHorseInfectionAdvancement() throws IOException {
        String source = Files.readString(SOURCE);

        int convertIndex = source.indexOf("convertHorseToZombieHorse(level, horse, player, pendingHorseHealthRatio)");
        int awardIndex = source.indexOf("IAmZombieAdvancements.HORSE_INFECTION");

        assertTrue(convertIndex >= 0, "horse should convert to a zombie horse on a zombie-player kill");
        assertTrue(awardIndex >= 0, "a successful horse conversion should award the HORSE_INFECTION advancement");
        assertTrue(convertIndex < awardIndex, "the advancement should be awarded after the conversion succeeds");
        assertTrue(source.contains("player instanceof ServerPlayer serverPlayer"),
                "the advancement should be awarded to the responsible server player");
    }

    @Test
    void mountedBigZombieAutoAttackCanPickOrdinaryHostileTargets() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private static LivingEntity findMountedBigZombieTarget");
        String method = source.substring(start, source.indexOf("\n    }", start));

        assertTrue(method.contains("instanceof Monster"), "big zombie mounts should auto-attack nearby ordinary hostile mobs too");
        assertTrue(method.contains("!(candidate instanceof Zombie)"), "big zombie mounts should not target fellow zombies through the broad hostile scan");
    }

    @Test
    void nautilusCanConvertToZombieNautilusOnZombiePlayerKill() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("Nautilus"), "vanilla nautilus should be recognized");
        assertTrue(source.contains("ZombieNautilus"), "zombie nautilus conversion should be created");
        assertTrue(source.contains("EntityTypes.ZOMBIE_NAUTILUS"), "conversion should use vanilla zombie nautilus entity type");
    }

    @Test
    void horseConversionPreservesUsefulHorseState() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("copyHorseStateToZombieHorse"), "horse conversion should copy useful state before discarding original");
        assertTrue(source.contains("EquipmentSlot.SADDLE"), "saddles should survive horse infection");
        assertTrue(source.contains("EquipmentSlot.BODY"), "horse armor/body equipment should survive horse infection");
        assertTrue(source.contains("horse.getAge()"), "age should be preserved across conversion");
        assertTrue(source.contains("horse.getHealth() / horse.getMaxHealth()"), "health ratio should be preserved instead of always healing to full");
    }

    @Test
    void horseConversionUsesPreDeathHealthRatioSnapshot() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("onIncomingDamage"), "horse health ratio should be captured before LivingDeathEvent");
        assertTrue(source.contains("PENDING_HORSE_HEALTH_RATIOS"), "pre-death horse health ratios should be stored temporarily");
        assertTrue(source.contains("pendingHorseHealthRatio"), "conversion should consume the pre-death ratio when available");
        assertTrue(source.contains("preDamageHorseHealthRatio"), "conversion should preserve the ratio before the lethal hit is applied");
        assertTrue(source.contains("Math.max(0.0F, horse.getHealth() - event.getAmount())"), "snapshot should account for incoming lethal damage");
    }

    @Test
    void failedHorseInfectionRollConsumesPendingHealthRatioSnapshot() throws IOException {
        String source = Files.readString(SOURCE);

        int consumeIndex = source.indexOf("Float pendingHorseHealthRatio = PENDING_HORSE_HEALTH_RATIOS.remove(horse.getUUID())");
        int rollIndex = source.indexOf("ZombieInfectionRules.shouldInfect");

        assertTrue(consumeIndex >= 0, "death handling should remove the pending horse health ratio");
        assertTrue(rollIndex >= 0, "death handling should still roll infection chance");
        assertTrue(consumeIndex < rollIndex, "pending horse health ratios should be removed even when infection fails");
    }
}
