package dev.molang.iamzombieq.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoffinBlockSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/block/CoffinBlock.java");
    private static final Path BLOCKSTATE = Path.of("src/main/resources/assets/iamzombieq/blockstates/coffin.json");
    private static final Path FOOT_MODEL = Path.of("src/main/resources/assets/iamzombieq/models/block/coffin_foot.json");
    private static final Path HEAD_MODEL = Path.of("src/main/resources/assets/iamzombieq/models/block/coffin_head.json");
    private static final Path FOOT_TEMPLATE = Path.of("src/main/resources/assets/iamzombieq/models/block/template_coffin_foot.json");
    private static final Path HEAD_TEMPLATE = Path.of("src/main/resources/assets/iamzombieq/models/block/template_coffin_head.json");

    @Test
    void coffinRestSafetyUsesZombieAttackerMatrix() throws IOException {
        String source = Files.readString(SOURCE);

        // A zombie is blocked from sleeping only by the creatures that PROACTIVELY attack the zombie player
        // (category ① of the undead relationship table), reusing the mod's ZombieMobTargetingRules — NOT vanilla's
        // monster rest-prevention (which counts fellow undead, the zombie's own kind).
        assertTrue(source.contains("ZombieMobTargetingRules"), "coffin safety should reuse the zombie-attacker matrix");
        assertTrue(source.contains("attacksZombiePlayer"), "coffin safety should decide threats via attacksZombiePlayer");
        assertTrue(source.contains("Mob.class"), "coffin safety should scan all mobs (golems/goat/axolotl/llama), not just Monster");
        assertTrue(source.contains("PLAYER_ZOMBIE"), "coffin safety should be form-aware via the player's zombie form");
        // Sleep is blocked only by PROACTIVE attackers via a coffin-owned whitelist; CONDITIONAL/provoked kinds
        // (BOSS = Warden/Wither; PROVOKED_SELF_TARGETING = enderman eye-contact / polar bear cub-defense) must NOT
        // block sleep, and the whitelist defaults to NOT blocking so future targeting kinds can't leak back in.
        assertTrue(source.contains("blocksCoffinSleep"), "coffin sleep-blocking should go through a proactive-attacker whitelist, not attacksZombiePlayer alone");
        assertTrue(source.contains("default -> false"), "the whitelist must default to NOT blocking so conditional/provoked kinds never keep a zombie from sleeping");
        assertTrue(source.contains("IRON_GOLEM"), "the proactive whitelist should include the real natural enemies (iron golem, etc.)");
        assertFalse(source.contains("isPreventingPlayerRest"), "the vanilla rest-prevention check should be gone");
    }

    @Test
    void coffinIsATwoBlockBedLikeStructure() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("BlockStateProperties.BED_PART"), "coffin should use the vanilla bed-part property");
        assertTrue(source.contains("BedPart.FOOT") && source.contains("BedPart.HEAD"), "coffin should distinguish foot and head halves");
        assertTrue(source.contains("builder.add(FACING, PART, OCCUPIED)"), "coffin state definition should register FACING, PART and OCCUPIED");
    }

    @Test
    void placementSetsFootAndPlacesHeadAtFacingNeighbor() throws IOException {
        String source = Files.readString(SOURCE);

        // getStateForPlacement: foot half, only when the facing neighbour is free.
        assertTrue(source.contains("setValue(PART, BedPart.FOOT)"), "placement should set the FOOT part");
        assertTrue(source.contains("canBeReplaced(context)"), "placement should verify the head neighbour space is replaceable");
        assertTrue(source.contains("getWorldBorder().isWithinBounds"), "placement should keep the head half inside the world border");
        // setPlacedBy: place the head half in the facing direction.
        assertTrue(source.contains("setPlacedBy"), "placement should place the second (head) half");
        assertTrue(source.contains("setValue(PART, BedPart.HEAD)"), "the placed neighbour should be the HEAD part");
    }

    @Test
    void breakingOneHalfBreaksBothLikeVanillaBed() throws IOException {
        String source = Files.readString(SOURCE);

        // playerWillDestroy removes the partner head when the foot is broken.
        assertTrue(source.contains("playerWillDestroy"), "destroying one half should remove the partner half");
        assertTrue(source.contains("preventsBlockDrops"), "partner removal should mirror vanilla bed drop suppression");
        // updateShape converts the half to air when its connected partner is gone.
        assertTrue(source.contains("updateShape"), "a coffin half should self-destruct when its partner is removed");
        assertTrue(source.contains("Blocks.AIR.defaultBlockState()"), "an orphaned coffin half should become air");
        assertTrue(source.contains("getNeighbourDirection"), "the connected-half direction should be derived from part + facing");
    }

    @Test
    void respawnAndSleepLogicReadsTheHeadHalf() throws IOException {
        String source = Files.readString(SOURCE);

        // useWithoutItem resolves to the head half before running the sleep/respawn logic.
        assertTrue(source.contains("state.getValue(PART) != BedPart.HEAD"), "interaction should resolve to the head half");
        assertTrue(source.contains("setCoffinRespawn(serverLevel, serverPlayer, headPos)"), "respawn should be set on the head half");
        assertTrue(source.contains("getRespawnPosition"), "respawn position lookup should remain wired");
    }

    @Test
    void restEntersTheRealNapDriverInsteadOfInstantTimeSkip() throws IOException {
        String source = Files.readString(SOURCE);

        // The REST branch now hands off to the multi-tick nap driver rather than skipping time on the spot.
        assertTrue(source.contains("CoffinNapManager.beginNap(serverLevel, serverPlayer, headPos)"),
                "the REST branch should start a real nap via CoffinNapManager.beginNap");
        // The old instant lie-down / wake / clock-skip helpers must be gone (moved into the nap driver).
        assertFalse(source.contains("moveDefaultClockToNight"), "the instant clock skip should be removed from the block");
        assertFalse(source.contains("playLieDownAnimation"), "the fake lie-down helper should be removed from the block");
        assertFalse(source.contains("wakeFromLieDown"), "the immediate wake helper should be removed from the block");
        // The respawn helper is still exposed for the nap driver to reuse.
        assertTrue(source.contains("public static void setCoffinRespawn"), "the respawn helper should be exposed for the nap driver");
    }

    @Test
    void perPartShapesExist() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("HEAD_SHAPE") && source.contains("FOOT_SHAPE"), "coffin should provide a per-part voxel shape");
        assertTrue(source.contains("state.getValue(PART) == BedPart.HEAD ? HEAD_SHAPE : FOOT_SHAPE"), "getShape should branch on the part");
    }

    @Test
    void blockstateBranchesOnPartAndFacingRotation() throws IOException {
        String blockstate = Files.readString(BLOCKSTATE);

        assertTrue(blockstate.contains("part=foot") && blockstate.contains("part=head"), "blockstate should branch on bed part");
        assertTrue(blockstate.contains("facing=north") && blockstate.contains("facing=south")
                && blockstate.contains("facing=east") && blockstate.contains("facing=west"), "blockstate should cover all four facings");
        assertTrue(blockstate.contains("block/coffin_foot") && blockstate.contains("block/coffin_head"), "blockstate should reference per-part models");
        assertTrue(blockstate.contains("\"y\": 90") && blockstate.contains("\"y\": 180") && blockstate.contains("\"y\": 270"),
                "blockstate should rotate the model per facing");
    }

    @Test
    void perPartModelsDeriveFromSharedTemplatesWithBespokeTextures() throws IOException {
        String footModel = Files.readString(FOOT_MODEL);
        String headModel = Files.readString(HEAD_MODEL);
        String footTemplate = Files.readString(FOOT_TEMPLATE);
        String headTemplate = Files.readString(HEAD_TEMPLATE);

        assertTrue(footModel.contains("template_coffin_foot"), "foot model should derive from the shared foot template");
        assertTrue(headModel.contains("template_coffin_head"), "head model should derive from the shared head template");
        // The bespoke, wood-agnostic coffin artwork lives in the shared templates.
        assertTrue(footTemplate.contains("iamzombieq:block/coffin_"), "foot template should reference the bespoke coffin textures");
        assertTrue(headTemplate.contains("iamzombieq:block/coffin_"), "head template should reference the bespoke coffin textures");
    }
}
