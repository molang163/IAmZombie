package dev.molang.iamzombieq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
//? if >=26.1
import net.minecraft.client.model.monster.piglin.AdultZombifiedPiglinModel;
//? if >=26.1
import net.minecraft.client.model.monster.piglin.BabyZombifiedPiglinModel;
//? if >=26.1
import net.minecraft.client.model.monster.zombie.BabyDrownedModel;
//? if >=26.1
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.piglin.PiglinModel;
//? if <1.21.11
//import net.minecraft.client.model.PiglinModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.piglin.ZombifiedPiglinModel;
//? if <1.21.11
//import net.minecraft.client.model.ZombifiedPiglinModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.zombie.DrownedModel;
//? if <1.21.11
//import net.minecraft.client.model.DrownedModel;
//? if >=1.21.11
import net.minecraft.client.model.monster.zombie.ZombieModel;
//? if <1.21.11
//import net.minecraft.client.model.ZombieModel;
//? if >=1.21.11
import net.minecraft.client.model.player.PlayerModel;
//? if <1.21.11
//import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FirstPersonArmGeometryAlignmentTest {
    private static final float EPSILON = 1.0E-5F;
    private static final float FACE_NORMAL_THRESHOLD = 0.999F;

    @ParameterizedTest(name = "{0} player, {1} {2}, {3} arm")
    @MethodSource("officialArmCases")
    void offsetAlignsOfficialPlayerAndMonsterHandTipsWithoutChangingMonsterDimensions(
            PlayerShape playerShape,
            MonsterShape monsterShape,
            boolean baby,
            HumanoidArm armSide
    ) {
        PlayerModel playerModel = playerModel(playerShape);
        HumanoidModel<?> monsterModel = monsterModel(monsterShape, baby);
        ModelPart playerArm = ZombiePlayerVisuals.humanoidArm(playerModel, armSide);
        ModelPart monsterArm = ZombiePlayerVisuals.humanoidArm(monsterModel, armSide);
        ModelPart monsterRoot = monsterModel.root();
        float roll = roll(armSide);

        monsterArm.resetPose();
        monsterArm.visible = true;
        monsterArm.skipDraw = false;
        monsterArm.zRot = roll;

        PartPose monsterRootInitial = monsterRoot.getInitialPose();
        PartPose monsterArmInitial = monsterArm.getInitialPose();
        float monsterLengthBefore = armLength(monsterArm, monsterRootInitial, roll);
        float monsterXScaleBefore = monsterArm.xScale;
        float monsterYScaleBefore = monsterArm.yScale;
        float monsterZScaleBefore = monsterArm.zScale;

        // RenderArmEvent is posted before vanilla resets the source arm. Dirty live state must not affect its anchor.
        playerArm.x += 37.0F;
        playerArm.y -= 19.0F;
        playerArm.zRot = 0.73F;
        playerArm.xScale = 0.25F;
        monsterRoot.x += 11.0F;
        monsterRoot.yScale = 0.5F;

        Vector3f offset = ZombiePlayerVisuals.firstPersonArmOffset(
                playerArm, monsterRoot, monsterArm, armSide
        );
        Vector3f expectedPlayerTip = oracleTip(playerArm, PartPose.ZERO, roll);
        Vector3f expectedMonsterTip = oracleTip(monsterArm, monsterRootInitial, roll);
        Vector3f alignedMonsterTip = new Vector3f(expectedMonsterTip).add(offset);

        assertVectorEquals(expectedPlayerTip, alignedMonsterTip,
                "the translated monster +Y hand-tip must coincide with the event player's +Y hand-tip");
        assertVectorEquals(expectedPlayerTip,
                ZombiePlayerVisuals.firstPersonArmTip(playerArm, PartPose.ZERO, roll),
                "the production player anchor must use initial pose and own-cube geometry only");
        assertVectorEquals(expectedMonsterTip,
                ZombiePlayerVisuals.firstPersonArmTip(monsterArm, monsterRootInitial, roll),
                "the production monster anchor must include root and arm initial transforms");

        float alignedMonsterLength = new Vector3f(oracleTip(monsterArm, monsterRootInitial, roll)).add(offset)
                .distance(new Vector3f(oracleShoulderFace(monsterArm, monsterRootInitial, roll)).add(offset));
        assertEquals(monsterLengthBefore, alignedMonsterLength, EPSILON,
                "alignment must translate the official monster geometry without normalizing its size");
        assertEquals(monsterArmInitial.xScale(), monsterArm.xScale, EPSILON,
                "alignment must preserve the official monster arm X scale");
        assertEquals(monsterXScaleBefore, monsterArm.xScale, EPSILON);
        assertEquals(monsterYScaleBefore, monsterArm.yScale, EPSILON);
        assertEquals(monsterZScaleBefore, monsterArm.zScale, EPSILON);
        assertTrue(monsterLengthBefore > 0.0F, "the baked official arm must retain a non-zero length");

        if (baby) {
            float playerLength = armLength(playerArm, PartPose.ZERO, roll);
            assertNotEquals(playerLength, monsterLengthBefore, EPSILON,
                    "baby monster arm length must not be normalized to the adult player arm");
        }
    }

    @Test
    void adultHuskRootScaleAndYOffsetParticipateInTheOffset() {
        PlayerModel playerModel = playerModel(PlayerShape.WIDE);
        HumanoidModel<?> huskModel = monsterModel(MonsterShape.HUSK, false);
        ModelPart playerArm = ZombiePlayerVisuals.humanoidArm(playerModel, HumanoidArm.RIGHT);
        ModelPart huskArm = ZombiePlayerVisuals.humanoidArm(huskModel, HumanoidArm.RIGHT);
        PartPose huskRootPose = huskModel.root().getInitialPose();
        float roll = roll(HumanoidArm.RIGHT);

        assertEquals(-1.501F, huskRootPose.y(), EPSILON,
                "the official adult husk layer must retain its root Y adjustment");
        assertEquals(1.0625F, huskRootPose.xScale(), EPSILON);
        assertEquals(1.0625F, huskRootPose.yScale(), EPSILON);
        assertEquals(1.0625F, huskRootPose.zScale(), EPSILON);

        Vector3f playerTip = oracleTip(playerArm, PartPose.ZERO, roll);
        Vector3f huskTipWithRoot = oracleTip(huskArm, huskRootPose, roll);
        Vector3f huskTipWithoutRoot = oracleTip(huskArm, PartPose.ZERO, roll);
        Vector3f offset = ZombiePlayerVisuals.firstPersonArmOffset(
                playerArm, huskModel.root(), huskArm, HumanoidArm.RIGHT
        );

        assertVectorEquals(new Vector3f(playerTip).sub(huskTipWithRoot), offset,
                "adult husk alignment must be calculated from the root-scaled hand tip");
        assertTrue(offset.distance(new Vector3f(playerTip).sub(huskTipWithoutRoot)) > EPSILON,
                "omitting the official husk root transform must produce a detectably different offset");

        float unscaledLength = armLength(huskArm, PartPose.ZERO, roll);
        float rootScaledLength = armLength(huskArm, huskRootPose, roll);
        assertEquals(unscaledLength * 1.0625F, rootScaledLength, EPSILON,
                "the aligned adult husk arm must retain the official 1.0625 root scale");
    }

    @Test
    void officialArmMatrixRetainsAllThirtyTwoCases() {
        assertEquals(32L, officialArmCases().count());
    }

    private static Stream<Arguments> officialArmCases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (PlayerShape playerShape : PlayerShape.values()) {
            for (MonsterShape monsterShape : MonsterShape.values()) {
                for (boolean baby : new boolean[]{false, true}) {
                    for (HumanoidArm armSide : HumanoidArm.values()) {
                        cases.add(Arguments.of(playerShape, monsterShape, baby, armSide));
                    }
                }
            }
        }
        return cases.build();
    }

    private static PlayerModel playerModel(PlayerShape playerShape) {
        boolean slim = playerShape == PlayerShape.SLIM;
        LayerDefinition layer = LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64
        );
        return new PlayerModel(layer.bakeRoot(), slim);
    }

    private static HumanoidModel<?> monsterModel(MonsterShape monsterShape, boolean baby) {
        return switch (monsterShape) {
            case NORMAL -> {
                if (baby) {
                    //? if >=26.1 {
                    yield new BabyZombieModel<ZombieRenderState>(
                            BabyZombieModel.createBodyLayer(CubeDeformation.NONE).bakeRoot());
                    //?} else {
                    /*yield new ZombieModel<ZombieRenderState>(
                            humanoidBodyLayer().apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot());
                    *///?}
                }
                yield new ZombieModel<ZombieRenderState>(humanoidBodyLayer().bakeRoot());
            }
            case DROWNED -> {
                if (baby) {
                    //? if >=26.1 {
                    yield new BabyDrownedModel(
                            BabyDrownedModel.createBodyLayer(CubeDeformation.NONE).bakeRoot());
                    //?} else {
                    /*yield new DrownedModel(DrownedModel.createBodyLayer(CubeDeformation.NONE)
                            .apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot());
                    *///?}
                }
                yield new DrownedModel(DrownedModel.createBodyLayer(CubeDeformation.NONE).bakeRoot());
            }
            case HUSK -> {
                if (baby) {
                    //? if >=26.1 {
                    yield new BabyZombieModel<ZombieRenderState>(
                            BabyZombieModel.createBodyLayer(CubeDeformation.NONE).bakeRoot());
                    //?} else {
                    /*yield new ZombieModel<ZombieRenderState>(humanoidBodyLayer()
                            .apply(HumanoidModel.BABY_TRANSFORMER)
                            .apply(MeshTransformer.scaling(1.0625F)).bakeRoot());
                    *///?}
                }
                yield new ZombieModel<ZombieRenderState>(
                        humanoidBodyLayer().apply(MeshTransformer.scaling(1.0625F)).bakeRoot());
            }
            case ZOMBIFIED_PIGLIN -> {
                if (baby) {
                    //? if >=26.1 {
                    yield new BabyZombifiedPiglinModel(
                            BabyZombifiedPiglinModel.createBodyLayer().bakeRoot());
                    //?} else {
                    /*yield new ZombifiedPiglinModel(LayerDefinition.create(
                            PiglinModel.createMesh(CubeDeformation.NONE), 64, 64)
                            .apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot());
                    *///?}
                }
                //? if >=26.1 {
                yield new AdultZombifiedPiglinModel(
                        AdultZombifiedPiglinModel.createBodyLayer().bakeRoot());
                //?} else {
                /*yield new ZombifiedPiglinModel(
                        LayerDefinition.create(PiglinModel.createMesh(CubeDeformation.NONE), 64, 64).bakeRoot());
                *///?}
            }
        };
    }

    private static LayerDefinition humanoidBodyLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
    }

    private static float armLength(ModelPart arm, PartPose rootPose, float roll) {
        return oracleTip(arm, rootPose, roll).distance(oracleShoulderFace(arm, rootPose, roll));
    }

    private static Vector3f oracleTip(ModelPart arm, PartPose rootPose, float roll) {
        return transform(faceCenter(arm, true), rootPose, rolledInitialPose(arm, roll));
    }

    private static Vector3f oracleShoulderFace(ModelPart arm, PartPose rootPose, float roll) {
        return transform(faceCenter(arm, false), rootPose, rolledInitialPose(arm, roll));
    }

    private static Vector3f faceCenter(ModelPart arm, boolean positiveY) {
        Vector3f[] selected = {null};
        arm.visit(new com.mojang.blaze3d.vertex.PoseStack(), (ignoredPose, path, ignoredIndex, cube) -> {
            if (!path.isEmpty()) {
                return;
            }
            for (ModelPart.Polygon polygon : cube.polygons) {
                float normalY = polygon.normal().y();
                if (positiveY ? normalY <= FACE_NORMAL_THRESHOLD : normalY >= -FACE_NORMAL_THRESHOLD) {
                    continue;
                }
                Vector3f center = new Vector3f();
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    //? if >=1.21.10 {
                    center.add(vertex.worldX(), vertex.worldY(), vertex.worldZ());
                    //?} else {
                    /*center.add(
                            vertex.pos().x() / 16.0F,
                            vertex.pos().y() / 16.0F,
                            vertex.pos().z() / 16.0F);
                    *///?}
                }
                center.div(polygon.vertices().length);
                if (selected[0] == null
                        || positiveY && center.y() > selected[0].y()
                        || !positiveY && center.y() < selected[0].y()) {
                    selected[0] = center;
                }
            }
        });
        if (selected[0] == null) {
            throw new IllegalStateException("Official arm has no own-cube " + (positiveY ? "+Y" : "-Y") + " face");
        }
        return selected[0];
    }

    private static Vector3f transform(Vector3f point, PartPose rootPose, PartPose armPose) {
        Matrix4f transform = new Matrix4f();
        applyPose(transform, rootPose);
        applyPose(transform, armPose);
        return transform.transformPosition(point, new Vector3f());
    }

    private static void applyPose(Matrix4f transform, PartPose pose) {
        transform.translate(pose.x() / 16.0F, pose.y() / 16.0F, pose.z() / 16.0F);
        transform.rotate(new Quaternionf().rotationZYX(pose.zRot(), pose.yRot(), pose.xRot()));
        transform.scale(pose.xScale(), pose.yScale(), pose.zScale());
    }

    private static PartPose rolledInitialPose(ModelPart arm, float roll) {
        PartPose initial = arm.getInitialPose();
        return new PartPose(
                initial.x(), initial.y(), initial.z(),
                initial.xRot(), initial.yRot(), roll,
                initial.xScale(), initial.yScale(), initial.zScale()
        );
    }

    private static float roll(HumanoidArm armSide) {
        return armSide == HumanoidArm.RIGHT ? 0.1F : -0.1F;
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, String message) {
        assertEquals(expected.x(), actual.x(), EPSILON, message + " (x)");
        assertEquals(expected.y(), actual.y(), EPSILON, message + " (y)");
        assertEquals(expected.z(), actual.z(), EPSILON, message + " (z)");
    }

    private enum PlayerShape {
        WIDE,
        SLIM
    }

    private enum MonsterShape {
        NORMAL,
        DROWNED,
        HUSK,
        ZOMBIFIED_PIGLIN
    }
}
