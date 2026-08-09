package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.IAmZombieMod;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Carries the 26.1+ GameTest padding metadata on legacy nodes whose {@code TestData} record has no padding field.
 *
 * <p>The compatibility mixins are loaded only by the three legacy {@code runGameTestServer} configurations. The
 * registration table is built from the same nine helpers that register the active programmatic tests, then sealed
 * before the runner can place a structure. This avoids a copied ID inventory while still failing closed if a test is
 * omitted or assigned a different padding.
 */
final class LegacyGameTestPadding {
    // CROSS_VERSION-NAUTILUS-CAPABILITY:legacy-padding-count
    //? if >=1.21.11 {
    private static final int NAUTILUS_REQUIRED_TESTS = 2;
    //?} else {
    /*private static final int NAUTILUS_REQUIRED_TESTS = 0;
    *///?}
    private static final int EXPECTED_TESTS = 83 + NAUTILUS_REQUIRED_TESTS;
    private static final int EXPECTED_PADDING_8 = 80 + NAUTILUS_REQUIRED_TESTS;
    private static final Map<ResourceKey<GameTestInstance>, Integer> PADDINGS = new HashMap<>();
    private static volatile boolean sealed;

    private LegacyGameTestPadding() {
    }

    static synchronized void register(Identifier id, int padding) {
        if (padding < 0 || padding > 128) {
            throw new IllegalArgumentException("GameTest padding must be in [0, 128]: " + padding);
        }

        ResourceKey<GameTestInstance> key = ResourceKey.create(Registries.TEST_INSTANCE, id);
        Integer oldPadding = PADDINGS.putIfAbsent(key, padding);
        if (oldPadding != null && oldPadding != padding) {
            throw new IllegalStateException(
                    "Conflicting GameTest padding for " + id + ": " + oldPadding + " != " + padding);
        }
        if (sealed && oldPadding == null) {
            PADDINGS.remove(key);
            throw new IllegalStateException("GameTest padding was registered after the table was sealed: " + id);
        }
    }

    static synchronized void seal() {
        long padding8 = PADDINGS.values().stream().filter(value -> value == 8).count();
        long padding24 = PADDINGS.values().stream().filter(value -> value == 24).count();
        long padding48 = PADDINGS.values().stream().filter(value -> value == 48).count();
        if (PADDINGS.size() != EXPECTED_TESTS
                || padding8 != EXPECTED_PADDING_8
                || padding24 != 2
                || padding48 != 1) {
            throw new IllegalStateException(
                    "Legacy GameTest padding inventory must be " + EXPECTED_TESTS
                            + " entries (8x" + EXPECTED_PADDING_8 + ", 24x2, 48x1), but was "
                            + PADDINGS.size() + " entries (8x" + padding8 + ", 24x" + padding24
                            + ", 48x" + padding48 + ")");
        }
        sealed = true;
    }

    static int padding(TestInstanceBlockEntity testBlock) {
        if (!sealed) {
            throw new IllegalStateException("Legacy GameTest padding was read before registration was sealed");
        }

        var testKey = testBlock.test();
        if (testKey.isEmpty()) {
            return 0;
        }
        if (testBlock.getLevel() == null
                || testBlock.getLevel().registryAccess().get(testKey.get()).isEmpty()) {
            return 0;
        }

        Integer padding = PADDINGS.get(testKey.get());
        if (padding != null) {
            return padding;
        }
        String testName = testBlock.getTestName().getString();
        if (testName.startsWith(IAmZombieMod.MOD_ID + ":")) {
            throw new IllegalStateException("Missing legacy GameTest padding for " + testName);
        }
        return 0;
    }

    static BlockPos structurePos(TestInstanceBlockEntity testBlock, BlockPos unpaddedPos) {
        int padding = padding(testBlock);
        return unpaddedPos.offset(padding, padding, padding);
    }

    static BoundingBox testBoundingBox(TestInstanceBlockEntity testBlock) {
        return testBlock.getStructureBoundingBox().inflatedBy(padding(testBlock));
    }

    static AABB testBounds(TestInstanceBlockEntity testBlock) {
        return testBlock.getStructureBounds().inflate(padding(testBlock));
    }

    /**
     * The 1.21.8 {@code StructureUtils} helper expands its input by an additional asymmetric margin, which would
     * erase the TestInstanceBlock when given a 26.x-style padded box. Reproduce the later helper against the exact
     * box instead.
     */
    static void clearExactTestBox(TestInstanceBlockEntity testBlock, ServerLevel level) {
        int padding = padding(testBlock);
        if (padding == 0) {
            return;
        }
        BoundingBox testBox = testBlock.getStructureBoundingBox().inflatedBy(padding);
        int groundHeight = testBox.minY() - 1;
        BlockPos.betweenClosedStream(testBox).forEach(pos -> {
            BlockState state = pos.getY() < groundHeight
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            new BlockInput(state, Collections.emptySet(), null).place(level, pos, 818);
            level.updateNeighborsAt(pos, state.getBlock());
        });
        level.getBlockTicks().clearArea(testBox);
        level.clearBlockEvents(testBox);
        AABB bounds = AABB.of(testBox);
        List<Entity> entities =
                level.getEntitiesOfClass(Entity.class, bounds, entity -> !(entity instanceof Player));
        entities.forEach(Entity::discard);
    }
}
