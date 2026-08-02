package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ZombieMobTargetingEventsSourceTest {
    @Test
    void serverAuthorityIsNeverReadBeforeTheLogicalServerGate()
            throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java");
        String onChangeTarget = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(
                        source, "public static void onChangeTarget")));
        String seedAttackers = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(
                        source,
                        "public static void seedAttackersOntoZombiePlayer")));
        String wouldDeny = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(
                        source,
                        "public static boolean wouldDenyZombiePlayerTarget")));
        String serverRead =
                "IAmZombieServerConfig.UNDEAD_IGNORE_ZOMBIE_PLAYER.get()";

        assertTrue(
                onChangeTarget.indexOf(
                                "mob.level()instanceofServerLevelserverLevel")
                        < onChangeTarget.indexOf(serverRead),
                "target changes must prove a logical ServerLevel before reading SERVER authority");
        assertTrue(
                seedAttackers.indexOf(
                                "event.getEntity()instanceofServerPlayerplayer")
                        < seedAttackers.indexOf(serverRead),
                "physical-client player ticks must short-circuit before reading SERVER authority");
        assertTrue(
                wouldDeny.indexOf("mob.level()instanceofServerLevel")
                        < wouldDeny.indexOf(serverRead),
                "the public pre-anger query must prove a logical ServerLevel before reading SERVER authority");
    }

    @Test
    void eventPathsPreserveOverrideSourcesAndTypedContextWiring() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java");
        String onChangeTarget = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onChangeTarget")));
        String expectedContext =
                "newTargetingOverrides(retaliating,angeredNeutral)";

        assertEquals(1, SourceScan.countOccurrences(onChangeTarget, expectedContext),
                "onChangeTarget should wire retaliating and angeredNeutral into one context in that order");
        assertEquals(1, SourceScan.countOccurrences(onChangeTarget, "newTargetingOverrides("),
                "onChangeTarget should construct exactly one targeting override context");
        assertFalse(onChangeTarget.contains("newTargetingOverrides(angeredNeutral,retaliating)"),
                "the two same-typed override inputs must not be swapped");

        int trueHit = onChangeTarget.indexOf("booleantrueHit=mob.getLastHurtByMob()==player;");
        int grudged = onChangeTarget.indexOf("booleangrudged=hasLiveGrudge(mob,player,serverLevel);");
        int grudgeWrite = onChangeTarget.indexOf("PLAYER_GRUDGE.put(", grudged);
        int retaliating = onChangeTarget.indexOf("booleanretaliating=trueHit||grudged;");
        int angeredNeutral = onChangeTarget.indexOf(
                "booleanangeredNeutral=mobinstanceofNeutralMobneutral"
                        + "&&neutral.isAngryAt(player,serverLevel);");
        int dataRead = onChangeTarget.indexOf(
                "PlayerZombieDatadata=player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);");
        int adapterCall = onChangeTarget.indexOf(
                "ZombieMobTargetingAdapter.shouldIgnoreZombiePlayer(mob,player,data,");
        int context = onChangeTarget.indexOf(expectedContext);
        assertTrue(
                trueHit >= 0
                        && grudged > trueHit
                        && grudgeWrite > grudged
                        && retaliating > grudgeWrite
                        && angeredNeutral > retaliating
                        && dataRead > angeredNeutral
                        && adapterCall > dataRead
                        && context > adapterCall,
                "true-hit, grudge refresh, retaliation, neutral anger, data, and typed-call order must stay unchanged");
        assertEquals(1, SourceScan.countOccurrences(
                        onChangeTarget, "ZombieMobTargetingAdapter.shouldIgnoreZombiePlayer("),
                "onChangeTarget should make one targeting decision");
        assertEquals(1, SourceScan.countOccurrences(
                        onChangeTarget,
                        "ZombieMobTargetingAdapter.shouldIgnoreZombiePlayer(mob,player,data,"
                                + expectedContext + ")"),
                "onChangeTarget should pass the correctly ordered context directly to the typed adapter entry");
        assertFalse(onChangeTarget.contains(
                        "shouldIgnoreZombiePlayer(mob,player,data,retaliating,angeredNeutral)"),
                "the event path should not call the legacy boolean adapter entry");

        String wouldDeny = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "public static boolean wouldDenyZombiePlayerTarget")));
        assertEquals(1, SourceScan.countOccurrences(
                        wouldDeny,
                        "returnZombieMobTargetingAdapter.shouldIgnoreZombiePlayer(mob,player,data,"
                                + "newTargetingOverrides(false,false));"),
                "the pre-anger query should use one explicit false/false context");
        assertEquals(1, SourceScan.countOccurrences(wouldDeny, "newTargetingOverrides(false,false)"),
                "the pre-anger query should construct exactly one false/false context");
    }
}
