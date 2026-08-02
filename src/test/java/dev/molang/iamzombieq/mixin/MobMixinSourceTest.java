package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobMixinSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/mixin/MobMixin.java");

    @Test
    void mountedBigZombieChangesOnlyTheVictimDamageSourceAttribution() throws IOException {
        String source = Files.readString(SOURCE);
        String method = SourceScan.methodBody(source, "private boolean iamzombieq$creditBigZombieKillToRider");
        String compact = SourceScan.compact(method);

        assertTrue(source.contains("@WrapOperation("),
                "Mob#doHurtTarget must be wrapped without copying its attack implementation");
        assertTrue(source.contains(
                        "target = \"Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z\""),
                "the hook must target Mob#doHurtTarget's victim Entity.hurtServer call");
        assertTrue(compact.contains(
                        "MountCapability.activeFor(mount).orElse(null)!=MountCapability.BIG_ZOMBIE"),
                "kill attribution must be limited to an actively and validly ridden BIG_ZOMBIE");
        assertTrue(compact.contains(
                        "newDamageSource(originalSource.typeHolder(),mount,rider,originalSource.sourcePositionRaw())"),
                "the replacement must preserve type/raw position with direct=mount and causing=rider");
        assertTrue(compact.contains("original.call(target,level,originalSource,amount)"),
                "all out-of-scope mobs must delegate with their untouched native source and amount");
        assertTrue(compact.contains("original.call(target,level,attributedSource,attributedAmount)"),
                "the active mount must still execute the original victim call exactly once");
        assertFalse(method.contains("target.hurtServer("),
                "the wrapper must delegate through Operation instead of bypassing the original call");
    }

    @Test
    void playerTargetKeepsOriginalMobAttackDifficultyScaling() throws IOException {
        String method = SourceScan.methodBody(
                Files.readString(SOURCE), "private boolean iamzombieq$creditBigZombieKillToRider");
        String compact = SourceScan.compact(method);

        assertTrue(compact.contains(
                        "originalSource.type().scaling()==DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER"),
                "the 26.2 DamageType scaling mode must gate the one required compensation");
        assertTrue(compact.contains(
                        ".scaleDamage(originalSource,targetPlayer,amount,level.getDifficulty())"),
                "player-target damage must be scaled once using the original mob-caused source");
        assertFalse(method.contains("scalesWithDifficulty("),
                "the fix must not depend on overriding the deprecated DamageSource helper");
    }
}
