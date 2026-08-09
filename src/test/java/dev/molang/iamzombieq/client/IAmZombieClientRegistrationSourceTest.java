package dev.molang.iamzombieq.client;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class IAmZombieClientRegistrationSourceTest {
    @Test
    void registersHerobrineRenderer() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("IAmZombieEntities.HEROBRINE.get()"), "missing Herobrine renderer registration");
        assertTrue(client.contains("HerobrineRenderer::new"), "Herobrine should use HerobrineRenderer");
    }

    @Test
    void registersHoverOnlyFoodClassificationTooltips() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("ItemTooltipEvent"), "missing item tooltip event hook");
        assertTrue(client.contains("ZombieFoodRules.tooltipKey"), "tooltip should use food classification rules");
    }

    @Test
    void herobrineRendererUsesEmissiveEyeLayer() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/HerobrineRenderer.java"));

        assertTrue(renderer.contains("EyesLayer"), "Herobrine should have a separate eye layer");
        assertTrue(renderer.contains("RenderTypes.eyes"), "Herobrine eyes should use the emissive eyes render type");
        assertTrue(renderer.contains("HEROBRINE_EYES"), "Herobrine eye texture should be a named renderer resource");
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/iamzombieq/textures/entity/herobrine_eyes.png")),
                "missing Herobrine eye-mask texture");
    }

    @Test
    void renderTypeFactoriesUseExactNodeBoundary() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes = Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean modernApi = Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);

        Path mainJava = Path.of("src/main/java");
        Path herobrinePath = mainJava.resolve("dev/molang/iamzombieq/client/HerobrineRenderer.java");
        Path visualsPath = mainJava.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java");
        String rawHerobrine = Files.readString(herobrinePath);
        String rawVisuals = Files.readString(visualsPath);
        String activeHerobrine = SourceScan.stripComments(rawHerobrine);
        String activeVisuals = SourceScan.stripComments(rawVisuals);

        String modernTypeImport =
                "import net.minecraft.client.renderer.rendertype.RenderType;";
        String modernFactoriesImport =
                "import net.minecraft.client.renderer.rendertype.RenderTypes;";
        String legacyTypeImport =
                "import net.minecraft.client.renderer.RenderType;";
        assertRenderTypeBoundary(
                rawHerobrine,
                "CROSS_VERSION-RENDER-TYPE-NAMESPACE:herobrine-imports",
                modernTypeImport + "\n" + modernFactoriesImport,
                legacyTypeImport);
        assertRenderTypeBoundary(
                rawHerobrine,
                "CROSS_VERSION-RENDER-TYPE-NAMESPACE:herobrine-eyes",
                "RenderTypes.eyes(HEROBRINE_EYES)",
                "RenderType.eyes(HEROBRINE_EYES)");
        assertRenderTypeBoundary(
                rawVisuals,
                "CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-import",
                modernFactoriesImport,
                legacyTypeImport);
        assertRenderTypeBoundary(
                rawVisuals,
                "CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-cutout",
                "RenderTypes.entityCutout(texture)",
                "RenderType.entityCutout(texture)");
        assertRenderTypeBoundary(
                rawVisuals,
                "CROSS_VERSION-RENDER-TYPE-NAMESPACE:visuals-translucent",
                "RenderTypes.entityTranslucent(texture)",
                "RenderType.entityTranslucent(texture)");

        assertEquals(2, SourceScan.countOccurrences(
                        rawHerobrine, "CROSS_VERSION-RENDER-TYPE-NAMESPACE:"),
                "HerobrineRenderer must own exactly its import and eyes seams");
        assertEquals(3, SourceScan.countOccurrences(
                        rawVisuals, "CROSS_VERSION-RENDER-TYPE-NAMESPACE:"),
                "ZombiePlayerVisuals must own exactly its import and two factory seams");
        assertEquals(1, SourceScan.countOccurrences(rawHerobrine, modernTypeImport));
        assertEquals(1, SourceScan.countOccurrences(rawHerobrine, modernFactoriesImport));
        assertEquals(1, SourceScan.countOccurrences(rawHerobrine, legacyTypeImport));
        assertEquals(1, SourceScan.countOccurrences(
                rawHerobrine, "RenderTypes.eyes(HEROBRINE_EYES)"));
        assertEquals(1, SourceScan.countOccurrences(
                rawHerobrine, "RenderType.eyes(HEROBRINE_EYES)"));
        assertEquals(1, SourceScan.countOccurrences(rawVisuals, modernFactoriesImport));
        assertEquals(1, SourceScan.countOccurrences(rawVisuals, legacyTypeImport));
        assertEquals(1, SourceScan.countOccurrences(
                rawVisuals, "RenderTypes.entityCutout(texture)"));
        assertEquals(2, SourceScan.countOccurrences(
                rawVisuals, "RenderType.entityCutout(texture)"),
                "canonical source must retain the high-pipeline fallback and low direct-buffer call");
        assertEquals(1, SourceScan.countOccurrences(
                rawVisuals, "RenderTypes.entityTranslucent(texture)"));
        assertEquals(2, SourceScan.countOccurrences(
                rawVisuals, "RenderType.entityTranslucent(texture)"),
                "canonical source must retain the high-pipeline fallback and low direct-buffer call");

        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(activeHerobrine, modernTypeImport));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(activeHerobrine, modernFactoriesImport));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(activeHerobrine, legacyTypeImport));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(
                        activeHerobrine, "RenderTypes.eyes(HEROBRINE_EYES)"));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(
                        activeHerobrine, "RenderType.eyes(HEROBRINE_EYES)"));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(activeVisuals, modernFactoriesImport));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(activeVisuals, legacyTypeImport));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(
                        activeVisuals, "RenderTypes.entityCutout(texture)"));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(
                        activeVisuals, "RenderType.entityCutout(texture)"));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(
                        activeVisuals, "RenderTypes.entityTranslucent(texture)"));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(
                        activeVisuals, "RenderType.entityTranslucent(texture)"));

        Set<String> boundaryTokens = Set.of(
                modernTypeImport,
                modernFactoriesImport,
                legacyTypeImport,
                "RenderTypes.eyes(",
                "RenderType.eyes(",
                "RenderTypes.entityCutout(",
                "RenderType.entityCutout(",
                "RenderTypes.entityTranslucent(",
                "RenderType.entityTranslucent(");
        Set<Path> observedFiles = new java.util.HashSet<>();
        try (var paths = Files.walk(mainJava)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (boundaryTokens.stream().anyMatch(source::contains)) {
                    observedFiles.add(mainJava.relativize(path));
                }
            }
        }
        assertEquals(
                Set.of(
                        Path.of("dev/molang/iamzombieq/client/HerobrineRenderer.java"),
                        Path.of("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java")),
                observedFiles,
                "the RenderType namespace boundary must remain local to the two production consumers");

        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("renderer.rendertype.RenderType")
                        || controller.contains("renderer.RenderType")
                        || controller.contains("RenderTypes.eyes")
                        || controller.contains("RenderType.eyes")
                        || controller.contains("RenderTypes.entityCutout")
                        || controller.contains("RenderType.entityCutout")
                        || controller.contains("RenderTypes.entityTranslucent")
                        || controller.contains("RenderType.entityTranslucent"),
                "RenderType adaptation must use local typed seams, never controller-wide replacements");
    }

    @Test
    void skullModelNamespaceUsesExactNodeBoundary() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes = Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean modernApi = Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);

        Path mainJava = Path.of("src/main/java");
        Path clientPath = mainJava.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java");
        String rawClient = Files.readString(clientPath);
        String activeClient = SourceScan.stripComments(rawClient);
        String marker = "CROSS_VERSION-SKULL-MODEL-NAMESPACE:client-import";
        String modernImport =
                "import net.minecraft.client.model.object.skull.SkullModel;";
        String legacyImport =
                "import net.minecraft.client.model.SkullModel;";

        assertEquals(1, SourceScan.countOccurrences(rawClient, marker),
                "the SkullModel namespace must have one local typed seam");
        int boundaryStart = rawClient.indexOf("// " + marker);
        int boundaryEnd = rawClient.indexOf(
                "import net.minecraft.client.player.AbstractClientPlayer;",
                boundaryStart);
        assertTrue(boundaryStart >= 0 && boundaryEnd > boundaryStart,
                "the SkullModel seam must stay directly before the next client import");
        String boundary = rawClient.substring(boundaryStart, boundaryEnd);
        String expectedBoundary = modernApi
                ? "// " + marker + "\n//? if >=1.21.11 {\n" + modernImport
                        + "\n//?} else {\n/*" + legacyImport + "\n*///?}\n"
                : "// " + marker + "\n//? if >=1.21.11 {\n/*" + modernImport
                        + "\n*///?} else {\n" + legacyImport + "\n//?}\n";
        assertEquals(expectedBoundary, boundary,
                "the SkullModel seam must be contiguous and use the exact active-node form");
        assertEquals(1, SourceScan.countOccurrences(rawClient, modernImport));
        assertEquals(1, SourceScan.countOccurrences(rawClient, legacyImport));
        assertEquals(modernApi ? 1 : 0,
                SourceScan.countOccurrences(activeClient, modernImport));
        assertEquals(modernApi ? 0 : 1,
                SourceScan.countOccurrences(activeClient, legacyImport));
        assertEquals(1, SourceScan.countOccurrences(
                        rawClient, "SkullModel::createHumanoidHeadLayer"),
                "all five nodes must retain the same typed humanoid-head layer factory");
        assertEquals(1, SourceScan.countOccurrences(
                        rawClient,
                        "modEventBus.addListener(IAmZombieClient::registerSkullLayers);"),
                "the skull layer registration listener must remain wired");

        Set<Path> observedFiles = new java.util.HashSet<>();
        try (var paths = Files.walk(mainJava)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains(modernImport) || source.contains(legacyImport)) {
                    observedFiles.add(mainJava.relativize(path));
                }
            }
        }
        assertEquals(
                Set.of(Path.of("dev/molang/iamzombieq/client/IAmZombieClient.java")),
                observedFiles,
                "the SkullModel namespace boundary must remain local to its production consumer");

        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("client.model.object.skull.SkullModel")
                        || controller.contains("client.model.SkullModel"),
                "SkullModel adaptation must use a local typed seam, never controller-wide replacement");
    }

    @Test
    void skullModelRegistrationUsesExactNodeBoundary() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes = Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean textureOverload =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10").contains(executingNode);
        assertEquals(textureOverload,
                StonecutterCapabilityMatrix.hasSkullModelTextureRegistrationOverload(),
                "the registration overload must use the independent high4/low1 capability boundary");

        String compactMatrix = SourceScan.compact(SourceScan.stripComments(Files.readString(
                Path.of("src/test/java/dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java"))));
        String capabilityNodes =
                "privatestaticfinalSet<String>SKULL_MODEL_TEXTURE_REGISTRATION_OVERLOAD_NODES="
                        + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\",\"1.21.10\");";
        String capabilityAccessor =
                "returnSKULL_MODEL_TEXTURE_REGISTRATION_OVERLOAD_NODES.contains(nodeId());";
        assertEquals(1, SourceScan.countOccurrences(compactMatrix, capabilityNodes),
                "the texture-overload capability must remain high4/low1");
        assertEquals(1, SourceScan.countOccurrences(compactMatrix, capabilityAccessor),
                "the capability accessor must read the one recorded node set");

        Path mainJava = Path.of("src/main/java");
        Path clientPath = mainJava.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java");
        String rawClient = Files.readString(clientPath);
        String rawMethod = SourceScan.methodBody(
                rawClient, "private static void createSkullModels");
        String activeMethod = SourceScan.stripComments(rawMethod);
        String compactRawMethod = SourceScan.compact(rawMethod);
        String compactActiveMethod = SourceScan.compact(activeMethod);
        String marker = "CROSS_VERSION-SKULL-MODEL-REGISTRATION-API";
        String textureCall =
                "event.registerSkullModel(HerobrineHeadType.HEROBRINE,"
                        + "HEROBRINE_HEAD_LAYER,HEROBRINE_HEAD_TEXTURE);";
        String modelCall =
                "event.registerSkullModel(HerobrineHeadType.HEROBRINE,"
                        + "HEROBRINE_HEAD_LAYER);";
        String textureMapCall =
                "net.minecraft.client.renderer.blockentity.SkullBlockRenderer.SKIN_BY_TYPE.put("
                        + "HerobrineHeadType.HEROBRINE,HEROBRINE_HEAD_TEXTURE);";

        assertEquals(1, SourceScan.countOccurrences(rawMethod, marker),
                "the skull registration overload must have one local typed seam");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//? if >=1.21.10 {"),
                "the registration overload seam must use the exact >=1.21.10 boundary");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//?} else {"),
                "the registration overload seam must have one legacy fallback");
        assertTrue(SourceScan.containsInOrder(
                        compactRawMethod,
                        marker,
                        "//?if>=1.21.10{",
                        textureCall,
                        "//?}else{",
                        modelCall,
                        textureMapCall),
                "both projections must stay ordered and bind the same type, layer, and texture");
        assertEquals(1, SourceScan.countOccurrences(compactRawMethod, textureCall));
        assertEquals(1, SourceScan.countOccurrences(compactRawMethod, modelCall));
        assertEquals(1, SourceScan.countOccurrences(compactRawMethod, textureMapCall));

        assertEquals(1, SourceScan.countOccurrences(
                        compactActiveMethod, "event.registerSkullModel("),
                "each active node must register exactly one skull model");
        assertEquals(textureOverload ? 1 : 0,
                SourceScan.countOccurrences(compactActiveMethod, textureCall));
        assertEquals(textureOverload ? 0 : 1,
                SourceScan.countOccurrences(compactActiveMethod, modelCall));
        assertEquals(textureOverload ? 0 : 1,
                SourceScan.countOccurrences(compactActiveMethod, textureMapCall));

        Set<Path> observedFiles = new java.util.HashSet<>();
        try (var paths = Files.walk(mainJava)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String compactSource = SourceScan.compact(Files.readString(path));
                if (compactSource.contains(textureCall)
                        || compactSource.contains(modelCall)
                        || compactSource.contains(textureMapCall)) {
                    observedFiles.add(mainJava.relativize(path));
                }
            }
        }
        assertEquals(
                Set.of(Path.of("dev/molang/iamzombieq/client/IAmZombieClient.java")),
                observedFiles,
                "the registration overload boundary must remain local to its production consumer");

        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("registerSkullModel")
                        || controller.contains("SKIN_BY_TYPE"),
                "skull registration adaptation must use a local typed seam, never controller replacements");
        assertFalse(rawMethod.contains("Class.forName")
                        || rawMethod.contains(".getMethod(")
                        || rawMethod.contains("java.lang.reflect"),
                "the registration overload boundary must remain compile-time typed");
    }

    @Test
    void herobrineHeadUsesVanillaSkullItemModel() throws IOException {
        String model = Files.readString(Path.of("src/main/resources/assets/iamzombieq/items/herobrine_head.json"));

        // The head now renders through the vanilla skull pipeline: a special head model keyed to the custom
        // "herobrine" skull type (in-hand/GUI + worn via CustomHeadLayer), not the old janky flat-textured cube.
        assertFalse(model.contains("\"elements\""), "the head should be a vanilla skull model, not a hand-built cube");
        assertTrue(model.contains("minecraft:special"), "Herobrine head item should use a minecraft:special model");
        assertTrue(model.contains("minecraft:head"), "Herobrine head should render via the minecraft:head special model");
        assertTrue(model.contains("\"herobrine\""), "the special head model must reference the custom 'herobrine' skull kind");
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/iamzombieq/textures/entity/herobrine_head.png")),
                "the Herobrine head entity texture (64x64 skin) must exist for the skull model");
    }

    @Test
    void herobrineHeadIsAPlaceableSkullBlock() throws IOException {
        String type = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/block/HerobrineHeadType.java"));
        assertTrue(type.contains("SkullBlock.Type.TYPES.put(\"herobrine\""),
                "the custom skull type must self-register into SkullBlock.Type.TYPES");

        String blocks = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieBlocks.java"));
        assertTrue(blocks.contains("HEROBRINE_HEAD") && blocks.contains("HEROBRINE_WALL_HEAD"),
                "floor + wall Herobrine head blocks must be registered");
        assertTrue(blocks.contains("BlockEntityTypeAddBlocksEvent") && blocks.contains("BlockEntityTypes.SKULL"),
                "the head blocks must join the vanilla SKULL block-entity type so they save/load and render");

        String items = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieItems.java"));
        assertTrue(items.contains("StandingAndWallBlockItem") && items.contains("equippable(EquipmentSlot.HEAD)"),
                "the head item must place the skull blocks yet stay equippable on the head (sun-block)");

        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        assertTrue(client.contains("registerSkullModel") && client.contains("HerobrineHeadType.HEROBRINE"),
                "the custom skull model+texture must be registered for the HEROBRINE type");
    }

    @Test
    void herobrineAudioMuteHasExplicitRestorePath() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));

        assertTrue(client.contains("muteForHerobrine"), "nearby Herobrine should enter a named mute path");
        assertTrue(client.contains("restoreHerobrineMutedAudio"), "leaving or resolving the encounter should have an explicit restore path");
        assertTrue(client.contains("minecraft.getSoundManager().pauseAllExcept()"), "mute path should pause active sounds so resume can restore them");
        assertFalse(client.contains("minecraft.getSoundManager().stop()"), "Herobrine mute must not stop and discard active sounds");
        assertTrue(client.contains("minecraft.getSoundManager().resume()"), "restore path should resume client sounds");
        assertTrue(client.contains("mutedByHerobrine = false"), "restore path should clear the local mute state");
        assertTrue(client.contains("event.setSound(null)"), "PlaySoundEvent should still block newly starting sounds while muted");
    }

    @Test
    void zombiePlayerVisualsRenderMonsterBodyInsteadOfOnlySwappingTextures() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String activeVisuals = SourceScan.stripComments(visuals);
        String activeBody = SourceScan.compact(
                SourceScan.methodBody(activeVisuals, "public static void renderMonsterBody"));
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        boolean splitModelClasses = executingNode.equals("26.2.x") || executingNode.equals("26.1.x");
        boolean submitPipeline = StonecutterCapabilityMatrix.hasPlayerRenderSubmitPipeline();

        assertFalse(client.contains("ZombiePlayerVisuals.renderMonsterBody(event)"), "player pre-render should not stack the old manual monster body path on top of the avatar submit mixin");
        assertTrue(activeVisuals.contains("ZombieModel<ZombieRenderState>"), "normal zombie players should use zombie model geometry");
        assertTrue(activeVisuals.contains("DrownedModel"), "drowned players should use drowned model geometry");
        assertTrue(activeVisuals.contains("ModelLayers.ZOMBIE_BABY"),
                "baby zombie players should bake the official baby model layer");
        assertEquals(splitModelClasses, activeVisuals.contains("BabyZombieModel"),
                "only nodes with split baby model classes may reference BabyZombieModel");
        assertTrue(activeVisuals.contains("HUSK"), "husk players should select husk geometry/texture");
        assertEquals(1, SourceScan.countOccurrences(activeBody, "event.setCanceled(true)"),
                "monster body rendering should replace the original player render exactly once");
        assertFalse(activeVisuals.contains("state.isSpectator = true"), "original player model must not be hidden by pretending the player is a spectator because that can leave spectator head rendering behind");
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeBody, "collector.submitModel("),
                "only the high-four player renderer pipeline may submit through a collector");
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeBody, "model.renderToBuffer("),
                "1.21.8 must render the same monster model through its direct buffer pipeline");
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeBody, "submitMonsterBodyLayers("));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeBody, "renderMonsterBodyLayers("));
        assertTrue(SourceScan.containsInOrder(
                        activeBody,
                        "poseStack.pushPose()",
                        "applyLivingBodyTransform(zombieState,poseStack)",
                        submitPipeline ? "collector.submitModel(" : "model.renderToBuffer(",
                        submitPipeline ? "submitMonsterBodyLayers(" : "renderMonsterBodyLayers(",
                        "poseStack.popPose()",
                        "event.setCanceled(true)"),
                "the active body path must render model and layers before restoring the pose and cancelling");
    }

    @Test
    void zombiePlayerMonsterBodyAlsoSubmitsEquipmentAndHandLayers() throws IOException {
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String activeVisuals = SourceScan.stripComments(visuals);
        boolean submitPipeline = StonecutterCapabilityMatrix.hasPlayerRenderSubmitPipeline();
        boolean heldItemStackPayload =
                StonecutterCapabilityMatrix.hasHeldItemStackPayloadApi();

        assertTrue(visuals.contains("submitMonsterBodyLayers"), "monster body rendering should submit layers before suppressing player layers");
        assertTrue(visuals.contains("renderMonsterBodyLayers"),
                "canonical source must retain the typed 1.21.8 direct-render layer path");
        assertTrue(visuals.contains("HumanoidArmorLayer<ZombieRenderState"), "zombie player armor should render on zombie geometry");
        assertTrue(visuals.contains("ItemInHandLayer<ZombieRenderState"), "zombie player held items should render from zombie arms");
        assertTrue(visuals.contains("ModelLayers.ZOMBIE_ARMOR"), "normal zombie armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.ZOMBIE_BABY_ARMOR"), "baby zombie armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.DROWNED_ARMOR"), "drowned armor model set should be used");
        assertTrue(visuals.contains("ModelLayers.HUSK_ARMOR"), "husk armor model set should be used");
        assertTrue(visuals.contains("setupAnim(zombieState)"), "layers need animated zombie model pose before submitting");
        assertTrue(visuals.contains("ZombiePlayerItemInHandLayer"), "held item layer should bridge avatar item states onto zombie arms");
        assertEquals(heldItemStackPayload,
                activeVisuals.contains("avatarState.rightHandItemState"),
                "only payload-capable nodes may use the modern right-hand item state");
        assertEquals(heldItemStackPayload,
                activeVisuals.contains("avatarState.leftHandItemState"),
                "only payload-capable nodes may use the modern left-hand item state");
        assertEquals(!heldItemStackPayload,
                activeVisuals.contains("avatarState.rightHandItem,"),
                "nodes without the raw-stack payload must use the direct right-hand item state");
        assertEquals(!heldItemStackPayload,
                activeVisuals.contains("avatarState.leftHandItem,"),
                "nodes without the raw-stack payload must use the direct left-hand item state");
        assertTrue(visuals.contains("babyHandItems"), "baby zombie players need baby-arm hand item placement");

        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeVisuals, "private static void submitMonsterBodyLayers("));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeVisuals, "private static void renderMonsterBodyLayers("));
        String activeLayers = SourceScan.compact(SourceScan.methodBody(
                activeVisuals,
                submitPipeline
                        ? "private static void submitMonsterBodyLayers"
                        : "private static void renderMonsterBodyLayers"));
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeLayers, "AvatarRenderStateavatarState"));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeLayers, "PlayerRenderStateavatarState"));
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeLayers, "SubmitNodeCollectorcollector"));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeLayers, "MultiBufferSourcebufferSource"));
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeLayers, "armor.submit("));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activeLayers, "armor.render("));
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(
                        activeLayers, "layers.handItems(zombieState.isBaby).submit("));
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(
                        activeLayers, "layers.handItems(zombieState.isBaby).render("));
    }

    @Test
    void armorModelBakingUsesExactNodeBoundaryAndAgeMatchedParents() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean armorModelSetApi =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10").contains(executingNode);
        assertEquals(armorModelSetApi,
                StonecutterCapabilityMatrix.hasArmorModelSetApi(),
                "the centralized capability matrix must retain the independent high4/low1 armor boundary");

        String compactMatrix = SourceScan.compact(Files.readString(
                Path.of("src/test/java/dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java")));
        assertEquals(1, SourceScan.countOccurrences(
                compactMatrix,
                "privatestaticfinalSet<String>ARMOR_MODEL_SET_NODES="
                        + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\",\"1.21.10\");"));
        assertEquals(1, SourceScan.countOccurrences(
                compactMatrix, "returnARMOR_MODEL_SET_NODES.contains(nodeId());"));

        String rawVisuals = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String activeVisuals = SourceScan.stripComments(rawVisuals);
        String compactRaw = SourceScan.compact(rawVisuals);
        String compactActive = SourceScan.compact(activeVisuals);
        String armorType =
                "HumanoidArmorLayer<ZombieRenderState,ZombieModel<ZombieRenderState>,"
                        + "HumanoidModel<ZombieRenderState>>";
        String modernImport =
                "import net.minecraft.client.renderer.entity.ArmorModelSet;";

        assertEquals(4, SourceScan.countOccurrences(
                rawVisuals, "CROSS_VERSION-ARMOR-MODEL-SET-API:"),
                "the import, factory, helper and age-selection seams must remain inventoried");
        for (String exactDirective : List.of(
                "// CROSS_VERSION-ARMOR-MODEL-SET-API:import\n//? if >=1.21.10",
                "// CROSS_VERSION-ARMOR-MODEL-SET-API:factory\n"
                        + "                //? if >=1.21.10 {",
                "// CROSS_VERSION-ARMOR-MODEL-SET-API:helper\n"
                        + "    private static MonsterLayerSet layerSet(\n"
                        + "            //? if >=1.21.10 {",
                "// CROSS_VERSION-ARMOR-MODEL-SET-API:age-selection\n"
                        + "    //? if >=1.21.10 {")) {
            assertEquals(1, SourceScan.countOccurrences(rawVisuals, exactDirective),
                    "the four capability seams must retain their exact >=1.21.10 directives");
        }
        assertEquals(1, SourceScan.countOccurrences(rawVisuals, modernImport));
        assertEquals(5, SourceScan.countOccurrences(rawVisuals, "ArmorModelSet"),
                "the canonical source must retain only the import, two parameter types and two bake calls");
        assertEquals(armorModelSetApi ? 1 : 0,
                SourceScan.countOccurrences(activeVisuals, modernImport));
        assertEquals(armorModelSetApi ? 5 : 0,
                SourceScan.countOccurrences(activeVisuals, "ArmorModelSet"),
                "only high4 active production may reference ArmorModelSet");

        for (String form : List.of("ZOMBIE", "DROWNED", "HUSK")) {
            String modernCall =
                    "layerSet(ModelLayers." + form + "_ARMOR,"
                            + "ModelLayers." + form + "_BABY_ARMOR,"
                            + "entityModels,equipmentRenderer)";
            String legacyCall =
                    "layerSet(ModelLayers." + form + "_INNER_ARMOR,"
                            + "ModelLayers." + form + "_OUTER_ARMOR,"
                            + "ModelLayers." + form + "_BABY_INNER_ARMOR,"
                            + "ModelLayers." + form + "_BABY_OUTER_ARMOR,"
                            + "entityModels,equipmentRenderer)";
            assertEquals(1, SourceScan.countOccurrences(compactRaw, modernCall));
            assertEquals(1, SourceScan.countOccurrences(compactRaw, legacyCall));
            assertEquals(armorModelSetApi ? 1 : 0,
                    SourceScan.countOccurrences(compactActive, modernCall));
            assertEquals(armorModelSetApi ? 0 : 1,
                    SourceScan.countOccurrences(compactActive, legacyCall));
        }

        String rawLayerSetSource = SourceScan.methodBody(
                rawVisuals, "private static MonsterLayerSet layerSet");
        String rawLayerSet = SourceScan.compact(rawLayerSetSource);
        String activeLayerSet = SourceScan.compact(SourceScan.methodBody(
                activeVisuals, "private static MonsterLayerSet layerSet"));
        assertEquals(2, SourceScan.countOccurrences(rawLayerSet,
                "ArmorModelSet<ModelLayerLocation>"));
        assertEquals(2, SourceScan.countOccurrences(rawLayerSet,
                "ArmorModelSet.bake("));
        assertEquals(1, SourceScan.countOccurrences(rawLayerSet,
                "ModelLayerLocationadultInnerArmor"));
        assertEquals(1, SourceScan.countOccurrences(rawLayerSet,
                "ModelLayerLocationadultOuterArmor"));
        assertEquals(1, SourceScan.countOccurrences(rawLayerSet,
                "ModelLayerLocationbabyInnerArmor"));
        assertEquals(1, SourceScan.countOccurrences(rawLayerSet,
                "ModelLayerLocationbabyOuterArmor"));
        assertEquals(armorModelSetApi ? 2 : 0,
                SourceScan.countOccurrences(activeLayerSet,
                        "ArmorModelSet<ModelLayerLocation>"));
        assertEquals(armorModelSetApi ? 2 : 0,
                SourceScan.countOccurrences(activeLayerSet, "ArmorModelSet.bake("));
        assertEquals(armorModelSetApi ? 1 : 2,
                SourceScan.countOccurrences(activeLayerSet,
                        "newHumanoidArmorLayer<>("));
        assertEquals(armorModelSetApi ? 1 : 0,
                SourceScan.countOccurrences(activeLayerSet, "armor,armor,"),
                "high4 must retain one existing layer instance for both age selectors");
        assertEquals(armorModelSetApi ? 1 : 2,
                SourceScan.countOccurrences(
                        activeLayerSet, "equipmentRenderer==null?null:"));
        assertEquals(2, SourceScan.countOccurrences(
                activeLayerSet,
                "RenderLayerParent<ZombieRenderState,ZombieModel<ZombieRenderState>>"),
                "both armor parents must retain their exact parameterized type");
        for (String age : List.of("adult", "baby")) {
            assertEquals(1, SourceScan.countOccurrences(
                    activeLayerSet,
                    "ZombieModel<ZombieRenderState>" + age + "ParentModel="),
                    "the adult and baby parent variables must retain their exact parameterized type");
        }
        String modernConstructor =
                armorType + "armor=equipmentRenderer==null?null:"
                        + "newHumanoidArmorLayer<>(adultParent,"
                        + "ArmorModelSet.bake(adultArmor,entityModels,HumanoidModel::new),"
                        + "ArmorModelSet.bake(babyArmor,entityModels,HumanoidModel::new),"
                        + "equipmentRenderer);";
        assertEquals(armorModelSetApi ? 1 : 0,
                SourceScan.countOccurrences(activeLayerSet, modernConstructor),
                "high4 must retain the exact adult-parent and adult/baby bake order");
        for (String age : List.of("adult", "baby")) {
            String parent = age + "Parent";
            String inner = age + "InnerArmor";
            String outer = age + "OuterArmor";
            String layer = age + "ArmorLayer";
            String constructor =
                    armorType + layer + "=equipmentRenderer==null?null:"
                            + "newHumanoidArmorLayer<>(" + parent + ","
                            + "newHumanoidModel<>(entityModels.bakeLayer(" + inner + ")),"
                            + "newHumanoidModel<>(entityModels.bakeLayer(" + outer + ")),"
                            + "equipmentRenderer)";
            assertEquals(armorModelSetApi ? 0 : 1,
                    SourceScan.countOccurrences(activeLayerSet, constructor),
                    "1.21.8 " + age + " armor must bind matching geometry and parent");
        }

        String rawLayerRecordSource = SourceScan.methodBody(
                rawVisuals, "private record MonsterLayerSet");
        String rawLayerRecord = SourceScan.compact(rawLayerRecordSource);
        assertEquals(1, SourceScan.countOccurrences(
                rawLayerRecord, armorType + "adultArmor"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayerRecord, armorType + "babyArmor"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayerRecord,
                armorType + "armor(booleanbaby){returnbaby?babyArmor:adultArmor;}"));

        String rawSubmitPipelineSource = SourceScan.methodBody(
                rawVisuals, "private static void submitMonsterBodyLayers");
        String rawRenderPipelineSource = SourceScan.methodBody(
                rawVisuals, "private static void renderMonsterBodyLayers");
        for (String rawPipelineSource : List.of(
                rawSubmitPipelineSource, rawRenderPipelineSource)) {
            String rawPipeline = SourceScan.compact(rawPipelineSource);
            assertEquals(1, SourceScan.countOccurrences(
                    rawPipeline,
                    "ZombieModel<ZombieRenderState>parentModel="
                            + "layers.parentModel(zombieState.isBaby);"),
                    "each armor pipeline parent must retain its exact parameterized type");
            assertEquals(1, SourceScan.countOccurrences(
                    rawPipeline,
                    armorType + "armor=layers.armor(zombieState.isBaby);"));
            assertTrue(SourceScan.containsInOrder(
                    rawPipeline,
                    "parentModel.setupAnim(zombieState);",
                    "armor=layers.armor(zombieState.isBaby);",
                    "if(armor!=null)"),
                    "the age-matched parent must be animated before selecting its armor layer");
        }

        String guardedSurface = rawLayerSetSource
                + rawLayerRecordSource
                + rawSubmitPipelineSource
                + rawRenderPipelineSource;
        String compactGuardedSurface = SourceScan.compact(guardedSurface);
        for (String forbiddenReflection : List.of(
                "Class.forName",
                "java.lang.reflect",
                ".getMethod(",
                ".getDeclaredMethod(",
                ".getMethods(",
                ".getDeclaredMethods(",
                ".getField(",
                ".getDeclaredField(",
                ".getFields(",
                ".getDeclaredFields(",
                ".getClass(",
                ".getConstructor(",
                ".getDeclaredConstructor(",
                ".getConstructors(",
                ".getDeclaredConstructors(",
                ".newInstance(",
                "Constructor<",
                "Class<",
                "Class ",
                "ClassLoader",
                "ServiceLoader",
                "MethodHandle",
                "VarHandle",
                "Proxy.newProxyInstance(",
                "LambdaMetafactory",
                "Unsafe",
                "setAccessible(",
                ".invoke(",
                "Class.cast(",
                "@Accessor",
                "@Invoker")) {
            assertFalse(rawVisuals.contains(forbiddenReflection),
                    "the armor adaptation must not use reflection or mixin accessors");
        }
        assertFalse(guardedSurface.contains("Class.forName")
                        || guardedSurface.contains("java.lang.reflect")
                        || guardedSurface.contains("@SuppressWarnings")
                        || guardedSurface.contains("Class.cast(")
                        || guardedSurface.contains("Object ")
                        || compactGuardedSurface.matches(
                                "(?s).*HumanoidArmorLayer(?!<).*")
                        || compactGuardedSurface.matches(
                                "(?s).*RenderLayerParent(?!<).*")
                        || compactGuardedSurface.matches(
                                "(?s).*ZombieModel(?!<).*")
                        || compactGuardedSurface.matches(
                                "(?s).*HumanoidModel(?!(?:<|::)).*"),
                "all armor helper, record and pipeline surfaces must reject raw generic types");
        assertFalse(compactRaw.contains("require=0")
                        || compactRaw.contains("required=0"),
                "the armor adaptation must not weaken mixin injection requirements");
        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("ArmorModelSet")
                        || controller.contains("CROSS_VERSION-ARMOR-MODEL-SET-API"),
                "the armor boundary must use local typed seams, not controller-wide replacement");
    }

    @Test
    void zombiePlayerVisualsUseCachedVanillaShapeEntities() throws IOException {
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("EntityTypes.ZOMBIE.create"), "normal zombie player visuals should be backed by a real vanilla zombie entity");
        assertTrue(shapeEntities.contains("EntityTypes.DROWNED.create"), "drowned player visuals should be backed by a real vanilla drowned entity");
        assertTrue(shapeEntities.contains("EntityTypes.HUSK.create"), "husk player visuals should be backed by a real vanilla husk entity");
        assertTrue(shapeEntities.contains("EntityTypes.ZOMBIFIED_PIGLIN.create"), "zombified piglin player visuals should be backed by a real vanilla zombified piglin entity");
        assertTrue(shapeEntities.contains("shape.setId(player.getId())"), "shape entities need the player entity id before vanilla render-state extraction touches held item rendering");
        assertTrue(shapeEntities.contains("setBaby"), "baby zombie player visuals should update the vanilla shape entity baby flag");
        assertTrue(shapeEntities.contains("setItemSlot"), "shape entities should receive player equipment before vanilla render state extraction");
    }

    @Test
    void zombiePlayerShapeCacheClearsOnClientLogout() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("public void clear()"), "shape cache needs a cache-wide clear method");
        assertTrue(shapeEntities.contains("shapes.clear()"), "clear method should drop cached shape entities");
        assertTrue(client.contains("ClientPlayerNetworkEvent.LoggingOut"), "client logout should clear cached shape entities");
        assertTrue(client.contains("ZOMBIE_PLAYER_SHAPES.clear()"), "logout handler should clear the shape cache");
    }

    @Test
    void zombiePlayerShapeCacheRemovesPlayersLeavingClientLevel() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));

        assertTrue(shapeEntities.contains("public void remove(AbstractClientPlayer player)"), "shape cache should support removing one player");
        assertTrue(client.contains("EntityLeaveLevelEvent"), "client should observe players leaving the level");
        assertTrue(client.contains("event.getEntity() instanceof AbstractClientPlayer player"), "leave handler should remove real client player entities");
        assertTrue(client.contains("ZOMBIE_PLAYER_SHAPES.remove(player)"), "leave handler should remove only the departed player's cached shape");
    }

    @Test
    void zombiePlayerReplacementRenderStateIsPreparedThroughNeoForgeModifier() throws IOException {
        String rawClient = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String client = SourceScan.stripComments(rawClient);
        String replacement = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerRenderReplacement.java"));
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean specializedAvatarModifier = Set.of("26.2.x", "26.1.x").contains(executingNode);
        boolean genericAvatarModifier = Set.of("1.21.11", "1.21.10").contains(executingNode);

        String rawRegistration = SourceScan.compact(
                SourceScan.methodBody(rawClient, "private static void registerRenderStateModifiers"));
        String registration = SourceScan.compact(
                SourceScan.methodBody(client, "private static void registerRenderStateModifiers"));
        assertTrue(rawRegistration.contains("//?if>=26.1{")
                        && rawRegistration.contains("//?if>=1.21.10&&<26.1{")
                        && rawRegistration.contains("//?if<1.21.10{"),
                "canonical source must retain specialized, generic, and PlayerRenderer registration branches");
        String specializedRegistration = "event.registerAvatarEntityModifier("
                + "newnet.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier()";
        String genericRegistration = "event.<net.minecraft.world.entity.Entity,"
                + "net.minecraft.client.renderer.entity.state.AvatarRenderState>registerEntityModifier(";
        String wildcardAvatarRenderer = "newcom.google.common.reflect.TypeToken<"
                + "net.minecraft.client.renderer.entity.player.AvatarRenderer<?>>(){}";
        String checkedAvatarNarrowing =
                "if(entityinstanceofnet.minecraft.world.entity.Avataravatar)";
        String playerRendererRegistration =
                "event.registerEntityModifier(net.minecraft.client.renderer.entity.player.PlayerRenderer.class";
        String specializedBound = "<Textendsnet.minecraft.world.entity.Avatar"
                + "&net.minecraft.client.entity.ClientAvatarEntity>voidaccept(";
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, specializedRegistration));
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, genericRegistration));
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, checkedAvatarNarrowing));
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, playerRendererRegistration),
                "canonical source must retain exactly one typed PlayerRenderer registration branch");
        assertEquals(specializedAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, specializedRegistration));
        assertEquals(genericAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, genericRegistration));
        assertEquals(genericAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, wildcardAvatarRenderer));
        assertEquals(genericAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, checkedAvatarNarrowing));
        assertEquals(specializedAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, "registerAvatarEntityModifier"));
        assertEquals(specializedAvatarModifier ? 0 : 1,
                SourceScan.countOccurrences(registration, "registerEntityModifier"));
        assertEquals(executingNode.equals("1.21.8") ? 1 : 0,
                SourceScan.countOccurrences(registration, playerRendererRegistration),
                "1.21.8 must eventually register its real PlayerRenderer modifier, never an inert substitute");
        assertEquals(specializedAvatarModifier ? 1 : 0,
                SourceScan.countOccurrences(registration, specializedBound));
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, wildcardAvatarRenderer));
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, "TypeToken<"),
                "canonical registration must retain exactly one parameterized wildcard TypeToken");
        assertEquals(1, SourceScan.countOccurrences(rawRegistration, "AvatarRenderer<"),
                "canonical registration must not hide a second concrete AvatarRenderer type argument");
        assertFalse(registration.contains("AvatarRenderer.class")
                        || registration.contains("TypeToken.of(")
                        || registration.contains("@SuppressWarnings")
                        || registration.contains("(java.lang.Class")
                        || registration.contains("Class<?>"),
                "registration adaptation must not bypass renderer bounds with raw or unchecked types");
        assertFalse(rawRegistration.contains("AvatarRenderer.class")
                        || rawRegistration.contains("TypeToken.of(")
                        || rawRegistration.contains("@SuppressWarnings")
                        || rawRegistration.contains("(java.lang.Class")
                        || rawRegistration.contains("Class<?>")
                        || rawRegistration.contains("AvatarRenderer<AbstractClientPlayer>"),
                "inactive registration branches must not hide a raw or unchecked renderer-bounds bypass");

        String handler = SourceScan.compact(
                SourceScan.methodBody(client, "private static void processAvatarRenderState"));
        String avatarHandlerPair = "net.minecraft.world.entity.Avataravatar,"
                + "net.minecraft.client.renderer.entity.state.AvatarRenderStaterenderState";
        String playerHandlerPair = "net.minecraft.client.player.AbstractClientPlayeravatar,"
                + "net.minecraft.client.renderer.entity.state.PlayerRenderStaterenderState";
        assertEquals(executingNode.equals("1.21.8") ? 0 : 1,
                SourceScan.countOccurrences(handler, avatarHandlerPair));
        assertEquals(executingNode.equals("1.21.8") ? 1 : 0,
                SourceScan.countOccurrences(handler, playerHandlerPair));
        assertEquals(3, SourceScan.countOccurrences(
                handler, "ZombiePlayerRenderReplacement.set(renderState,null)"));
        assertEquals(1, SourceScan.countOccurrences(
                handler, "ZombiePlayerRenderReplacement.set(renderState,replacement)"));
        int shape = handler.indexOf("ZOMBIE_PLAYER_SHAPES.shapeFor(player)");
        int renderer = handler.indexOf(".getRenderer(shape)");
        int replacementFor = handler.indexOf("ZOMBIE_PLAYER_SHAPES.replacementFor(player,renderer)");
        int copy = handler.indexOf("ZombiePlayerRenderReplacement.copyAvatarAnimation(renderState,shapeState)");
        int install = handler.indexOf("ZombiePlayerRenderReplacement.set(renderState,replacement)");
        assertTrue(shape >= 0 && shape < renderer && renderer < replacementFor
                        && replacementFor < copy && copy < install,
                "shared handler must preserve shape extraction, animation copy and replacement installation order");
        assertEquals(1, SourceScan.countOccurrences(handler, "@SuppressWarnings(\"unchecked\")"),
                "the frozen renderer cast must be moved intact without adding another unchecked bypass");

        String rawReplacement = SourceScan.compact(replacement);
        String activeReplacement = SourceScan.compact(SourceScan.stripComments(replacement));
        List<String> avatarSignatures = List.of(
                "publicstaticvoidset(AvatarRenderStatestate,"
                        + "ZombiePlayerRenderReplacementreplacement)",
                "publicstaticZombiePlayerRenderReplacementget(AvatarRenderStatestate)",
                "publicstaticvoidcopyAvatarAnimation("
                        + "AvatarRenderStateavatar,EntityRenderStateshape)");
        List<String> playerSignatures = List.of(
                "publicstaticvoidset(PlayerRenderStatestate,"
                        + "ZombiePlayerRenderReplacementreplacement)",
                "publicstaticZombiePlayerRenderReplacementget(PlayerRenderStatestate)",
                "publicstaticvoidcopyAvatarAnimation("
                        + "PlayerRenderStateavatar,EntityRenderStateshape)");
        for (String signature : avatarSignatures) {
            assertEquals(1, SourceScan.countOccurrences(rawReplacement, signature),
                    "canonical replacement source must retain one AvatarRenderState seam");
            assertEquals(executingNode.equals("1.21.8") ? 0 : 1,
                    SourceScan.countOccurrences(activeReplacement, signature));
        }
        for (String signature : playerSignatures) {
            assertEquals(1, SourceScan.countOccurrences(rawReplacement, signature),
                    "canonical replacement source must retain one PlayerRenderState seam");
            assertEquals(executingNode.equals("1.21.8") ? 1 : 0,
                    SourceScan.countOccurrences(activeReplacement, signature));
        }

        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("registerAvatarEntityModifier")
                        || controller.contains("registerEntityModifier")
                        || controller.contains("AvatarRenderer")
                        || controller.contains("TypeToken")
                        || controller.contains("processAvatarRenderState"),
                "render-state modifier registration must not use controller-wide replacements");
        assertEquals(1, SourceScan.countOccurrences(
                        client,
                        "modEventBus.addListener(IAmZombieClient::registerRenderStateModifiers)"),
                "the typed modifier must remain registered once on the client mod bus");

        assertTrue(client.contains("RegisterRenderStateModifiersEvent"), "client setup should register a render-state modifier for avatar render states");
        assertTrue(replacement.contains("ContextKey<ZombiePlayerRenderReplacement>"), "replacement data should use NeoForge render-state context keys");
        assertTrue(replacement.contains("setRenderData"), "replacement data should be written onto the avatar render state");
        assertTrue(replacement.contains("EntityRenderer"), "replacement data should carry the vanilla shape entity renderer");
        assertTrue(replacement.contains("EntityRenderState"), "replacement data should carry the vanilla shape render state");
    }

    @Test
    void zombiePlayerReplacementCopiesAvatarAnimationIntoShapeRenderState() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String replacement = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerRenderReplacement.java"));
        String visuals = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));

        // This was a 15-line field-by-field mirror of copyAvatarAnimation's assignments (every `x = avatar.x`).
        // That mirror had to be edited in lockstep with the production method and asserted nothing the method's own
        // existence didn't — a maintenance tax, not a real guard. Reduced to: the wiring (client calls it after
        // vanilla extraction), the method exists, and a few representative fields prove the copy is a real animation
        // sync (walk phase + body rotation + attack progress) rather than an empty stub.
        assertTrue(client.contains("ZombiePlayerRenderReplacement.copyAvatarAnimation(renderState, shapeState)"),
                "replacement render state should receive avatar animation data after vanilla extraction");
        assertTrue(replacement.contains("copyAvatarAnimation"), "replacement should expose a named avatar animation sync path");
        assertTrue(replacement.contains("walkAnimationPos = avatar.walkAnimationPos")
                        && replacement.contains("bodyRot = avatar.bodyRot")
                        && replacement.contains("attackTime = avatar.attackTime"),
                "copyAvatarAnimation must copy the avatar's live animation (walk phase, body rotation, attack progress) onto the shape");

        boolean submitPipeline = StonecutterCapabilityMatrix.hasPlayerRenderSubmitPipeline();
        String rawCopy = SourceScan.methodBody(
                visuals,
                "private static ZombieRenderState copyToZombieState("
                        + (submitPipeline ? "AvatarRenderState" : "PlayerRenderState"));
        String activeCopy = SourceScan.stripComments(rawCopy);
        List<String> submitOnlyStateCopies = List.of(
                "target.lightCoords = source.lightCoords;",
                "target.outlineColor = source.outlineColor;",
                "target.shadowRadius = source.shadowRadius;");
        Pattern lightAndOutlineSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.10 \\{\\R"
                        + "\\h*(?:/\\*)?target\\.lightCoords = source\\.lightCoords;\\R"
                        + "\\h*target\\.outlineColor = source\\.outlineColor;\\R"
                        + "\\h*(?:\\*/)?//\\?\\}");
        Pattern shadowSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.10\\R"
                        + "\\h*(?://)?target\\.shadowRadius = source\\.shadowRadius;");
        assertEquals(1, lightAndOutlineSeam.matcher(rawCopy).results().count(),
                "light and outline payloads must share one local high-four boundary");
        assertEquals(1, shadowSeam.matcher(rawCopy).results().count(),
                "shadow payload must use the same exact high-four boundary");
        for (String assignment : submitOnlyStateCopies) {
            assertEquals(1, SourceScan.countOccurrences(rawCopy, assignment),
                    "canonical source must retain exactly one guarded " + assignment);
            assertEquals(submitPipeline ? 1 : 0,
                    SourceScan.countOccurrences(activeCopy, assignment),
                    "only the submit-node pipeline may copy " + assignment);
        }
        assertFalse(rawCopy.contains("target.lightCoords = 0")
                        || rawCopy.contains("target.outlineColor = 0")
                        || rawCopy.contains("target.shadowRadius = 0")
                        || rawCopy.contains("Class." + "forName")
                        || rawCopy.contains("java.lang.reflect")
                        || rawCopy.contains("@Suppress" + "Warnings")
                        || rawCopy.contains("required = 0"),
                "the low renderer must receive packed light explicitly without fabricated state fields or bypasses");
    }

    @Test
    void avatarRendererCameraStateUsesTheNodeSpecificPackage() throws IOException {
        String rawMixin = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/mixin/client/AvatarRendererMixin.java"));
        String activeMixin = SourceScan.stripComments(rawMixin);
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null
                        && Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                                .contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");

        String nestedImport = "import net.minecraft.client.renderer.state.level.CameraRenderState;";
        String flatImport = "import net.minecraft.client.renderer.state.CameraRenderState;";
        boolean nestedCameraState = Set.of("26.2.x", "26.1.x").contains(executingNode);
        boolean flatCameraState = Set.of("1.21.11", "1.21.10").contains(executingNode);

        int nestedBoundary = rawMixin.indexOf("//? if >=26.1\n");
        int flatBoundary = rawMixin.indexOf("//? if >=1.21.10 && <26.1\n");
        int followingImport = rawMixin.indexOf("import org.spongepowered.asm.mixin.Mixin;");
        assertTrue(nestedBoundary >= 0 && nestedBoundary < flatBoundary && flatBoundary < followingImport,
                "canonical source must retain the ordered local CameraRenderState import boundaries");
        String nestedBlock = rawMixin.substring(nestedBoundary, flatBoundary);
        String flatBlock = rawMixin.substring(flatBoundary, followingImport);
        assertEquals(1, SourceScan.countOccurrences(nestedBlock, nestedImport));
        assertEquals(0, SourceScan.countOccurrences(nestedBlock, flatImport));
        assertEquals(0, SourceScan.countOccurrences(flatBlock, nestedImport));
        assertEquals(1, SourceScan.countOccurrences(flatBlock, flatImport));
        assertEquals(1, SourceScan.countOccurrences(rawMixin, nestedImport));
        assertEquals(1, SourceScan.countOccurrences(rawMixin, flatImport));
        assertEquals(nestedCameraState ? 1 : 0, SourceScan.countOccurrences(activeMixin, nestedImport));
        assertEquals(flatCameraState ? 1 : 0, SourceScan.countOccurrences(activeMixin, flatImport));
        assertEquals(executingNode.equals("1.21.8") ? 0 : 1,
                SourceScan.countOccurrences(activeMixin, "CameraRenderState camera"),
                "only nodes with AvatarRenderer#submit may retain its camera state parameter");

        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("CameraRenderState"),
                "the package seam must stay local to AvatarRendererMixin, not a controller-wide replacement");
    }

    @Test
    void playerRendererPipelineDelegatesZombiePlayersToVanillaShapeRenderer() throws IOException {
        String rawMixins = Files.readString(Path.of("src/main/resources/iamzombieq.mixins.json"));
        String compactBuild = SourceScan.compact(SourceScan.stripComments(
                Files.readString(Path.of("build.gradle"))));
        String centralProperties = Files.readString(Path.of("stonecutter.properties.toml"));
        String rawAvatarMixin = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/mixin/client/AvatarRendererMixin.java"));
        String activeAvatarMixin = SourceScan.stripComments(rawAvatarMixin);
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null
                        && Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                                .contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean avatarSubmitPipeline = !executingNode.equals("1.21.8");
        assertEquals(avatarSubmitPipeline,
                StonecutterCapabilityMatrix.hasPlayerRenderSubmitPipeline(),
                "the renderer/mixin boundary must use the centralized high-four capability");
        assertEquals(
                avatarSubmitPipeline
                        ? StonecutterCapabilityMatrix.SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT
                        : StonecutterCapabilityMatrix.SUBMIT_NODE_COLLECTOR_PIPELINE_ABSENT,
                StonecutterCapabilityMatrix.submitNodeCollectorRenderPipelineStatus());

        String rawRendererJavaSources = rawAvatarMixin;
        Path playerMixinPath =
                Path.of("src/main/java/dev/molang/iamzombieq/mixin/client/PlayerRendererMixin.java");
        assertTrue(Files.isRegularFile(playerMixinPath),
                "the canonical tree must retain the typed 1.21.8 renderer mixin source");
        String rawPlayerMixin = Files.readString(playerMixinPath);
        String activePlayerMixin = SourceScan.stripComments(rawPlayerMixin);
        rawRendererJavaSources += rawPlayerMixin;
        String compactRawRendererJavaSources = SourceScan.compact(rawRendererJavaSources);
        assertFalse(compactRawRendererJavaSources.contains("Screen.class")
                        || compactRawRendererJavaSources.contains(
                                "net.minecraft.client.gui.screens.Screen")
                        || compactRawRendererJavaSources.contains("Pseudo")
                        || compactRawRendererJavaSources.contains("require"),
                "renderer adaptation must never use an inert Screen target, pseudo mixin, or optional injection");

        assertEquals(0, SourceScan.countOccurrences(rawMixins, "//"),
                "the canonical mixin template must remain strict JSON");
        assertEquals(1, SourceScan.countOccurrences(rawMixins, "${player_renderer_mixin}"),
                "the canonical resource must expose exactly one node-expanded renderer slot");
        assertEquals(0, SourceScan.countOccurrences(rawMixins, "client.AvatarRendererMixin"));
        assertEquals(0, SourceScan.countOccurrences(rawMixins, "client.PlayerRendererMixin"));
        assertEquals(1, SourceScan.countOccurrences(
                compactBuild,
                "defsubmitNodeCollectorRenderPipelineCapability="
                        + "scProperty('platform.submit_node_collector_render_pipeline')"));
        assertEquals(1, SourceScan.countOccurrences(
                compactBuild, "'PRESENT':'client.AvatarRendererMixin'"));
        assertEquals(1, SourceScan.countOccurrences(
                compactBuild,
                "'N/A_PLATFORM_ABSENT':'client.PlayerRendererMixin'"));
        assertEquals(1, SourceScan.countOccurrences(
                compactBuild, "inputs.property('playerRendererMixin',playerRendererMixin)"));
        assertEquals(1, SourceScan.countOccurrences(
                compactBuild, "player_renderer_mixin:playerRendererMixin"));
        assertEquals(4, SourceScan.countOccurrences(
                centralProperties,
                "platform.submit_node_collector_render_pipeline = \"PRESENT\""));
        assertEquals(1, SourceScan.countOccurrences(
                centralProperties,
                "platform.submit_node_collector_render_pipeline = \"N/A_PLATFORM_ABSENT\""));
        assertEquals(1, SourceScan.countOccurrences(rawAvatarMixin, "//? if >=1.21.10 {"));
        assertEquals(1, SourceScan.countOccurrences(rawPlayerMixin, "//? if <1.21.10 {"));
        assertEquals(avatarSubmitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeAvatarMixin, "@Mixin(AvatarRenderer.class)"));
        assertEquals(avatarSubmitPipeline ? 1 : 0,
                SourceScan.countOccurrences(activeAvatarMixin, "private void iamzombieq$submitZombieShape"));
        assertEquals(avatarSubmitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activePlayerMixin, "@Mixin(PlayerRenderer.class)"));
        assertEquals(avatarSubmitPipeline ? 0 : 1,
                SourceScan.countOccurrences(activePlayerMixin, "private void iamzombieq$renderZombieShape"));

        if (avatarSubmitPipeline) {
            assertEquals(1, SourceScan.countOccurrences(
                    activeAvatarMixin,
                    "@Inject(method = \"submit\", at = @At(\"HEAD\"), cancellable = true)"),
                    "modern nodes must use one strict cancellable AvatarRenderer#submit injection");
            String submit = SourceScan.compact(SourceScan.methodBody(
                    activeAvatarMixin, "private void iamzombieq$submitZombieShape"));
            assertEquals(1, SourceScan.countOccurrences(
                    submit,
                    "privatevoidiamzombieq$submitZombieShape("
                            + "AvatarRenderStatestate,PoseStackposeStack,"
                            + "SubmitNodeCollectorcollector,CameraRenderStatecamera,CallbackInfocallback)"),
                    "the active injection must bind the real AvatarRenderer#submit descriptor");
            assertTrue(submit.contains("ZombiePlayerRenderReplacement.get(state)"),
                    "submit path should read replacement data from the avatar render state");
            assertTrue(submit.contains(
                            "replacement.renderer().submit(replacement.renderState(),poseStack,collector,camera)"),
                    "submit path should delegate to the vanilla shape renderer");
            assertTrue(submit.contains("callback.cancel()"),
                    "submit path should cancel the original player renderer after replacement");
            // At submit (after InventoryScreen overrides the avatar rotation to frontal), the shape must inherit the
            // avatar's FINAL rotation so the inventory zombie faces front while the in-world render is unchanged.
            assertTrue(submit.contains("livingShape.bodyRot=state.bodyRot"),
                    "submit must sync the avatar's final body rotation onto the shape (frontal in the inventory)");
            assertTrue(submit.contains("livingShape.yRot=state.yRot"),
                    "submit must sync the avatar's final head yaw onto the shape");
            assertTrue(submit.contains("livingShape.xRot=state.xRot"),
                    "submit must sync the avatar's final head pitch onto the shape");
            assertEquals(1, SourceScan.countOccurrences(
                    submit, "state.shadowRadius=replacement.renderState().shadowRadius"));
            assertTrue(SourceScan.containsInOrder(
                            submit,
                            "livingShape.bodyRot=state.bodyRot",
                            "livingShape.yRot=state.yRot",
                            "livingShape.xRot=state.xRot",
                            "replacement.renderer().submit(",
                            "state.shadowRadius=replacement.renderState().shadowRadius",
                            "callback.cancel()"),
                    "submit must synchronize final rotation, delegate, preserve shadow size, then cancel");
        } else {
            assertTrue(Files.isRegularFile(playerMixinPath),
                    "1.21.8 must provide a dedicated PlayerRendererMixin source");
            assertEquals(1, SourceScan.countOccurrences(activePlayerMixin, "@Mixin(PlayerRenderer.class)"));
            assertEquals(1, SourceScan.countOccurrences(
                    activePlayerMixin,
                    "@Inject(method = \"render\", at = @At(\"HEAD\"), cancellable = true)"),
                    "1.21.8 must use one strict cancellable PlayerRenderer#render injection");
            assertEquals(1, SourceScan.countOccurrences(
                    activePlayerMixin, "private void iamzombieq$renderZombieShape"));
            String render = SourceScan.compact(SourceScan.methodBody(
                    activePlayerMixin, "private void iamzombieq$renderZombieShape"));
            assertEquals(1, SourceScan.countOccurrences(
                    render,
                    "privatevoidiamzombieq$renderZombieShape("
                            + "PlayerRenderStatestate,PoseStackposeStack,MultiBufferSourcebufferSource,"
                            + "intpackedLight,CallbackInfocallback)"),
                    "the active injection must bind the real PlayerRenderer#render descriptor");
            assertTrue(render.contains("ZombiePlayerRenderReplacement.get(state)"));
            assertTrue(render.contains(
                            "replacement.renderer().render("
                                    + "replacement.renderState(),poseStack,bufferSource,packedLight)"),
                    "1.21.8 must delegate to the real vanilla shape renderer before cancelling the player render");
            assertEquals(1, SourceScan.countOccurrences(
                    render,
                    "replacement==null||state.isSpectator||state.isInvisible"
                            + "||state.isInvisibleToPlayer"));
            assertEquals(1, SourceScan.countOccurrences(
                    render, "livingShape.bodyRot=state.bodyRot"));
            assertEquals(1, SourceScan.countOccurrences(
                    render, "livingShape.yRot=state.yRot"));
            assertEquals(1, SourceScan.countOccurrences(
                    render, "livingShape.xRot=state.xRot"));
            assertEquals(1, SourceScan.countOccurrences(render, "callback.cancel()"));
            assertTrue(SourceScan.containsInOrder(
                            render,
                            "ZombiePlayerRenderReplacement.get(state)",
                            "replacement==null||state.isSpectator||state.isInvisible"
                                    + "||state.isInvisibleToPlayer",
                            "livingShape.bodyRot=state.bodyRot",
                            "livingShape.yRot=state.yRot",
                            "livingShape.xRot=state.xRot",
                            "replacement.renderer().render(",
                            "callback.cancel()"),
                    "1.21.8 must apply its visibility gate, sync final rotation, render, then cancel");
        }
    }

    @Test
    void humanoidModelArmAccessUsesExactNodeBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean publicArmAccess =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                publicArmAccess,
                StonecutterCapabilityMatrix.hasPublicHumanoidModelArmAccess(),
                "the centralized capability matrix must retain the >=1.21.11 access boundary");

        Path matrixPath =
                Path.of("src/test/java/dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>PUBLIC_HUMANOID_MODEL_ARM_ACCESS_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the arm-access capability must remain high3/low2 instead of drifting per callsite");
        assertTrue(compactMatrix.contains(
                        "returnPUBLIC_HUMANOID_MODEL_ARM_ACCESS_NODES.contains(nodeId());"),
                "the capability accessor must read the one recorded node set");

        Path visualsPath =
                Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java");
        String visuals = Files.readString(visualsPath);
        assertEquals(1, SourceScan.countOccurrences(
                        visuals, "CROSS_VERSION-HUMANOID-ARM-ACCESS-API"),
                "the production helper marker must exist exactly once before its body is inspected");
        String seam = SourceScan.methodBody(
                visuals, "static ModelPart humanoidArm(HumanoidModel<?> model, HumanoidArm arm)");
        String activeSeam = SourceScan.stripComments(seam);
        assertEquals(3, SourceScan.countOccurrences(seam, "//?"),
                "the one local helper must contain one complete if/else boundary");
        assertTrue(SourceScan.containsInOrder(
                        seam,
                        "//? if >=1.21.11 {",
                        "return model.getArm(arm);",
                        "//?} else {",
                        "return arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;",
                        "//?}"),
                "the raw helper must retain the exact >=1.21.11 access boundary and branch order");
        assertEquals(1, SourceScan.countOccurrences(seam, "//? if >=1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(seam, "//?} else {"));
        assertEquals(2, SourceScan.countOccurrences(seam, "//?}"),
                "the else marker and final boundary marker must both remain present");
        assertEquals(1, SourceScan.countOccurrences(seam, "return model.getArm(arm);"));
        assertEquals(1, SourceScan.countOccurrences(
                seam, "return arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;"));
        assertEquals(
                publicArmAccess
                        ? "staticModelParthumanoidArm(HumanoidModel<?>model,HumanoidArmarm)"
                                + "{returnmodel.getArm(arm);}"
                        : "staticModelParthumanoidArm(HumanoidModel<?>model,HumanoidArmarm)"
                                + "{returnarm==HumanoidArm.LEFT?model.leftArm:model.rightArm;}",
                SourceScan.compact(activeSeam),
                "the active helper must contain exactly one typed node-native arm lookup");
        assertFalse(seam.contains("public static ModelPart humanoidArm")
                        || seam.contains("protected static ModelPart humanoidArm")
                        || seam.contains("private static ModelPart humanoidArm"),
                "the typed compatibility helper must remain package-private");

        assertEquals(4, SourceScan.countOccurrences(visuals, "humanoidArm("),
                "the canonical source must retain the helper, monster arm, and both player-anchor branches");
        assertEquals(0, SourceScan.countOccurrences(visuals, ".getArm(armSide)"),
                "all side-selected production arm access must pass through the typed helper");
        assertEquals(1, SourceScan.countOccurrences(visuals, "model.getArm(arm)"),
                "the canonical source may retain only the high-node helper branch");
        String activeVisuals = SourceScan.stripComments(visuals);
        assertEquals(executingNode.equals("26.2.x") ? 2 : 3,
                SourceScan.countOccurrences(activeVisuals, "humanoidArm("),
                "the active source must retain the helper declaration and all applicable callsites");
        assertEquals(publicArmAccess ? 1 : 0,
                SourceScan.countOccurrences(activeVisuals, "model.getArm(arm)"));
        assertEquals(publicArmAccess ? 0 : 1,
                SourceScan.countOccurrences(activeVisuals, "model.leftArm"));
        assertEquals(publicArmAccess ? 0 : 1,
                SourceScan.countOccurrences(activeVisuals, "model.rightArm"));
        Path geometryPath =
                Path.of("src/test/java/dev/molang/iamzombieq/client/FirstPersonArmGeometryAlignmentTest.java");
        String geometry = Files.readString(geometryPath);
        assertEquals(4, SourceScan.countOccurrences(
                        geometry, "ZombiePlayerVisuals.humanoidArm("),
                "the 32-case geometry oracle and husk root check must share the production helper");
        assertEquals(0, SourceScan.countOccurrences(geometry, ".getArm("),
                "geometry tests must compile against the protected-access low nodes");

        String compactVisuals = SourceScan.compact(visuals);
        assertTrue(compactVisuals.contains(
                        "ModelPartarm=humanoidArm(monsterModel,armSide);"),
                "the submitted monster arm must use the local typed helper");
        assertTrue(compactVisuals.contains(
                        "ModelPartplayerArm=humanoidArm(Minecraft.getInstance()"
                                + ".getEntityRenderDispatcher().getPlayerRenderer(player)"
                                + ".getModel(),armSide);"),
                "the 1.21.10-26.1 player anchor must use the same typed helper");
        assertTrue(compactVisuals.contains(
                        "getEntityRenderDispatcher().getRenderer(player);"
                                + "ModelPartplayerArm=humanoidArm(playerRenderer.getModel(),armSide);"),
                "the 1.21.8 player anchor must use its concrete typed renderer and the same arm helper");
        assertFalse(visuals.contains("Class." + "forName")
                        || visuals.contains("Method" + "Handle")
                        || visuals.contains("java.lang.reflect")
                        || visuals.contains("@Accessor")
                        || visuals.contains("@Invoker")
                        || visuals.contains("@Suppress" + "Warnings"),
                "the arm access boundary must remain typed and warning-free");
        String controller = Files.readString(Path.of("stonecutter.gradle.kts"));
        assertFalse(controller.contains("humanoidArm")
                        || controller.contains("CROSS_VERSION-HUMANOID-ARM-ACCESS-API"),
                "the arm access API must use one local seam, not a global source replacement");
    }

    @Test
    void firstPersonArmRenderingDirectlySubmitsTheFormCorrectMonsterArm() throws IOException {
        String client = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String activeClient = SourceScan.stripComments(client);
        String activeVisuals = SourceScan.stripComments(visuals);
        String handler = SourceScan.methodBody(activeClient, "public static void onRenderArm");
        String method = SourceScan.methodBody(activeVisuals, "public static void renderFirstPersonArm");
        String rawMethod = SourceScan.methodBody(visuals, "public static void renderFirstPersonArm");
        String compact = method.replaceAll("\\s+", "");
        String rawCompact = rawMethod.replaceAll("\\s+", "");
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        boolean upperArmEventApi = executingNode.equals("26.2.x");
        boolean modernRenderTypeApi =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        boolean submitPipeline = StonecutterCapabilityMatrix.hasPlayerRenderSubmitPipeline();

        assertTrue(client.contains("public static void onRenderArm(RenderArmEvent<?> event)"));
        assertTrue(client.contains("public static void onRenderArm(RenderArmEvent event)"));
        assertTrue(visuals.contains("public static void renderFirstPersonArm(RenderArmEvent<?> event)"));
        assertTrue(visuals.contains("public static void renderFirstPersonArm(RenderArmEvent event)"));
        assertTrue(visuals.contains("AbstractClientPlayer player = event.getPlayer()"),
                "canonical source must retain the pre-26.2 event player fallback");
        assertTrue(visuals.contains("event.getPackedLight()"),
                "canonical source must retain the pre-26.2 light fallback");
        assertTrue(rawCompact.contains(
                        "humanoidArm(Minecraft.getInstance().getEntityRenderDispatcher()"
                                + ".getPlayerRenderer(player).getModel(),armSide)"),
                "canonical source must retain the 1.21.10-26.1 player-arm anchor lookup");
        assertTrue(rawCompact.contains(
                        "getEntityRenderDispatcher().getRenderer(player);"
                                + "ModelPartplayerArm=humanoidArm(playerRenderer.getModel(),armSide)"),
                "canonical source must retain the typed 1.21.8 player-renderer anchor");
        assertFalse(rawMethod.contains("renderRightHand("), "monster arms must not fire an inner right-arm event");
        assertFalse(rawMethod.contains("renderLeftHand("), "monster arms must not fire an inner left-arm event");
        assertFalse(visuals.contains("renderingFirstPersonArm"), "direct model-part submission no longer needs a reentry guard");
        assertFalse(visuals.contains("@SuppressWarnings(\"deprecation\")"),
                "arm adaptation must not route through a deprecated recursive renderer overload");
        if (upperArmEventApi) {
            assertTrue(handler.contains("RenderArmEvent<?> event"),
                    "26.2 handler must use the generic RenderArmEvent API");
            assertTrue(method.contains("RenderArmEvent<?> event"),
                    "26.2 renderer must use the generic RenderArmEvent API");
            assertFalse(method.contains("event.getPlayer()"),
                    "26.2 no longer exposes RenderArmEvent.getPlayer()");
            assertFalse(method.contains("event.getPackedLight()"),
                    "26.2 renamed the arm light accessor to getLightCoords");
            assertFalse(method.contains("getPlayerRenderer("),
                    "26.2 must use the event-provided player arm anchor");
            assertTrue(method.contains("event.getAvatar() instanceof AbstractClientPlayer player"),
                    "26.2 must safely narrow the event avatar before reading player data");
            assertTrue(method.contains("ModelPart playerArm = event.getArmPart()"),
                    "26.2 actual wide/slim event arm must supply the player hand-tip anchor");
            assertTrue(method.contains("int packedLight = event.getLightCoords()"),
                    "26.2 arm rendering must use the renamed light accessor");
        } else if (!executingNode.equals("1.21.8")) {
            assertTrue(handler.contains("RenderArmEvent event"),
                    "pre-26.2 handler must use that line's non-generic RenderArmEvent API");
            assertFalse(handler.contains("RenderArmEvent<?> event"),
                    "pre-26.2 RenderArmEvent is not generic");
            assertTrue(method.contains("AbstractClientPlayer player = event.getPlayer()"),
                    "pre-26.2 must obtain the same client player from the event");
            assertTrue(method.contains("int packedLight = event.getPackedLight()"),
                    "pre-26.2 must use that line's packed-light accessor");
            assertTrue(compact.contains(
                            "ModelPartplayerArm=humanoidArm(Minecraft.getInstance()"
                                    + ".getEntityRenderDispatcher().getPlayerRenderer(player)"
                                    + ".getModel(),armSide)"),
                    "1.21.10-26.1 must recover the vanilla player-model arm as the hand-tip anchor");
        } else {
            assertTrue(handler.contains("RenderArmEvent event"),
                    "1.21.8 handler must use its non-generic RenderArmEvent API");
            assertTrue(method.contains("AbstractClientPlayer player = event.getPlayer()"));
            assertTrue(method.contains("int packedLight = event.getPackedLight()"));
            assertTrue(compact.contains(
                            "getEntityRenderDispatcher().getRenderer(player);"
                                    + "ModelPartplayerArm=humanoidArm(playerRenderer.getModel(),armSide)"),
                    "1.21.8 must recover the vanilla player arm from its concrete typed renderer");
        }
        assertTrue(method.contains("HumanoidModel<?> monsterModel = models().firstPersonModelFor(form, baby)"),
                "the submitted part must come from the form/size-selected monster model");
        assertTrue(method.contains("ModelPart arm = humanoidArm(monsterModel, armSide)"),
                "the submitted part must be the selected monster model's requested arm");
        assertTrue(method.contains("firstPersonTextureFor(form, baby)"),
                "the submitted part must use the matching first-person monster texture");
        assertTrue(method.contains("firstPersonArmOffset(playerArm, monsterModel.root(), arm, armSide)"),
                "the active vanilla player-model arm must supply the hand-tip anchor");
        assertFalse(method.contains("submitModelPart(event.getArmPart()"),
                "the event player arm is an anchor only and must never be submitted as replacement geometry");
        assertFalse(method.contains("arm.loadPose(event.getArmPart()"),
                "the monster arm must not inherit the player arm pivot or geometry pose");
        assertFalse(compact.contains("submitModelPart(playerArm"),
                "the resolved player arm is an anchor only and must never be submitted as replacement geometry");
        assertFalse(compact.contains("arm.loadPose(playerArm"),
                "the monster arm must not inherit the resolved player arm's pivot or geometry pose");
        assertFalse(rawCompact.contains("submitModelPart(playerArm"),
                "inactive fallbacks must not submit player geometry either");
        assertFalse(rawCompact.contains("arm.loadPose(playerArm"),
                "inactive fallbacks must not copy the player arm pose either");
        assertTrue(compact.contains("arm.resetPose()"),
                "the monster arm must restore its own initial pose before first-person submission");
        assertTrue(compact.contains("arm.visible=true") && compact.contains("arm.skipDraw=false"),
                "the selected monster arm must be drawable");
        assertTrue(compact.contains("arm.zRot=armSide==HumanoidArm.RIGHT?0.1F:-0.1F"),
                "first-person right/left arm roll must match vanilla's stable pose");
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(compact, "event.getSubmitNodeCollector().submitModelPart("),
                "only the high-four pipeline may submit the replacement arm through a collector");
        assertEquals(submitPipeline ? 0 : 1,
                SourceScan.countOccurrences(compact, "arm.render("),
                "1.21.8 must draw the same replacement monster arm through its direct buffer pipeline");
        if (!submitPipeline) {
            assertTrue(compact.contains(
                            "event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture))"),
                    "1.21.8 must use the event buffer and the same translucent monster texture");
            assertEquals(1, SourceScan.countOccurrences(
                    compact,
                    "arm.render(poseStack,event.getMultiBufferSource().getBuffer("
                            + "RenderType.entityTranslucent(texture)),packedLight,"
                            + "OverlayTexture.NO_OVERLAY,-1)"),
                    "1.21.8 must render exactly one real monster arm with the frozen light/overlay payload");
        }
        assertTrue(method.contains(
                        (modernRenderTypeApi ? "RenderTypes" : "RenderType")
                                + ".entityTranslucent(texture)"),
                "the replacement arm must use the vanilla translucent entity render type");
        assertTrue(method.contains("OverlayTexture.NO_OVERLAY"),
                "the replacement arm must use the vanilla no-overlay value");
        assertEquals(submitPipeline ? 1 : 0,
                SourceScan.countOccurrences(compact, "submitModelPart("));
        assertEquals(1, SourceScan.countOccurrences(compact, "event.setCanceled(true)"),
                "each successfully replaced arm event must be canceled exactly once");
        assertTrue(compact.contains("poseStack.pushPose()") && compact.contains("finally{poseStack.popPose();}"),
                "the dynamic alignment transform must be scoped and restored even when submission fails");
        assertTrue(compact.contains("poseStack.translate(offset.x(),offset.y(),offset.z())"),
                "the computed hand-tip offset must be applied through the snapshotted PoseStack");
        assertTrue(compact.contains("applyPartPose(poseStack,monsterModel.root().getInitialPose())"),
                "direct arm submission must include the selected monster model's official root initial transform");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "poseStack.translate(offset.x(),offset.y(),offset.z())",
                        "applyPartPose(poseStack,monsterModel.root().getInitialPose())"),
                "the hand-tip offset must be applied before the monster root initial transform");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "applyPartPose(poseStack,monsterModel.root().getInitialPose())",
                        submitPipeline ? "submitModelPart(" : "arm.render("),
                "the official monster root transform must be applied before direct arm submission");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        "poseStack.popPose()",
                        "event.setCanceled(true)"),
                "the original event may only be canceled after submission and PoseStack restoration succeed");
        assertTrue(SourceScan.containsInOrder(
                        compact,
                        submitPipeline ? "submitModelPart(" : "arm.render(",
                        "event.setCanceled(true)"),
                "the original event may only be canceled after replacement submission succeeds");
    }

    private static void assertRenderTypeBoundary(
            String source,
            String marker,
            String modernToken,
            String legacyToken) {
        assertEquals(1, SourceScan.countOccurrences(source, marker),
                "missing or duplicate local RenderType seam " + marker);
        int start = source.indexOf(marker);
        int end = source.indexOf("CROSS_VERSION-RENDER-TYPE-NAMESPACE:", start + marker.length());
        String boundary = source.substring(start, end < 0 ? source.length() : end);
        String upperDirective = "/" + "/? if >=1.21.11";
        int upper = boundary.indexOf(upperDirective);
        int modern = boundary.indexOf(modernToken);
        int lower = boundary.indexOf("/" + "/? if <1.21.11", modern + modernToken.length());
        if (lower < 0) {
            lower = boundary.indexOf("/" + "/?} else {", modern + modernToken.length());
        }
        int legacy = boundary.indexOf(legacyToken);
        assertTrue(upper >= 0 && upper < modern && modern < lower && lower < legacy,
                "local seam must order >=1.21.11 modern code before the lower-node fallback: "
                        + marker);
        String localSeam = boundary.substring(0, legacy + legacyToken.length());
        assertEquals(1, SourceScan.countOccurrences(localSeam, modernToken));
        assertEquals(1, SourceScan.countOccurrences(localSeam, legacyToken));
    }

    @Test
    void firstPersonArmRenderingPairsEveryFormAndSizeWithVanillaGeometryAndTexture() throws IOException {
        String visuals = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        boolean splitModelClasses = executingNode.equals("26.2.x") || executingNode.equals("26.1.x");
        boolean distinctBabyTextures =
                StonecutterCapabilityMatrix.hasDistinctBabyMonsterTextures();
        String selector = SourceScan.stripComments(SourceScan.methodBody(visuals, "HumanoidModel<?> firstPersonModelFor"))
                .replaceAll("\\s+", "");
        String texture = SourceScan.stripComments(SourceScan.methodBody(visuals, "private static Identifier firstPersonTextureFor"))
                .replaceAll("\\s+", "");
        String factory = SourceScan.stripComments(SourceScan.methodBody(visuals, "private static MonsterModels createMonsterModels"));

        assertTrue(selector.contains("caseNORMAL,GIANT->baby?babyNormal:normal"),
                "normal and giant arms must use matching zombie/baby-zombie geometry");
        assertTrue(selector.contains("caseDROWNED->baby?babyDrowned:drowned"),
                "drowned arms must use matching adult/baby drowned geometry");
        assertTrue(selector.contains("caseHUSK->baby?babyHusk:husk"),
                "husk arms must use matching adult/baby husk layers");
        assertTrue(selector.contains("caseZOMBIFIED_PIGLIN->baby?babyZombifiedPiglin:zombifiedPiglin"),
                "zombified piglin arms must use matching adult/baby piglin geometry");
        if (splitModelClasses) {
            assertTrue(factory.contains("new AdultZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN))"),
                    "26.x adult zombified piglin geometry must use its split model class");
            assertTrue(factory.contains("new BabyZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN_BABY))"),
                    "26.x baby zombified piglin geometry must use its split model class");
        } else {
            assertTrue(factory.contains("new ZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN))"),
                    "1.21.x adult zombified piglin geometry must use the unified model class");
            assertTrue(factory.contains("new ZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN_BABY))"),
                    "1.21.x baby zombified piglin geometry must use the unified class with its baby layer");
        }
        if (distinctBabyTextures) {
            assertTrue(texture.contains("baby&&form==ZombieForm.ZOMBIFIED_PIGLIN"),
                    "26.x baby zombified piglin arms must use their distinct texture");
            assertTrue(texture.contains("textures/entity/piglin/zombified_piglin_baby.png"),
                    "26.x baby zombified piglin arms must use the official baby texture");
        } else {
            assertFalse(texture.contains("zombified_piglin_baby.png"),
                    "1.21.x must not request a vanilla texture absent from that platform");
        }
        assertTrue(texture.contains("returntextureFor(form,baby)"),
                "all other first-person textures must preserve the existing form/size mapping");
    }

    @Test
    void babyMonsterTextureSelectionUsesExactNodeBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean distinctBabyTextures =
                Set.of("26.2.x", "26.1.x").contains(executingNode);
        assertEquals(
                distinctBabyTextures,
                StonecutterCapabilityMatrix.hasDistinctBabyMonsterTextures(),
                "the centralized capability matrix must retain the >=26.1 texture boundary");

        String compactMatrix = SourceScan.compact(SourceScan.stripComments(Files.readString(
                Path.of("src/test/java/dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java"))));
        String capabilityNodes =
                "privatestaticfinalSet<String>DISTINCT_BABY_MONSTER_TEXTURE_NODES="
                        + "Set.of(\"26.2.x\",\"26.1.x\");";
        String capabilityAccessor =
                "returnDISTINCT_BABY_MONSTER_TEXTURE_NODES.contains(nodeId());";
        assertEquals(1, SourceScan.countOccurrences(compactMatrix, capabilityNodes),
                "the baby-texture capability must remain high2/legacy3");
        assertEquals(1, SourceScan.countOccurrences(compactMatrix, capabilityAccessor),
                "the capability accessor must read the one recorded node set");

        String rawVisuals = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String rawRules = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/rules/ZombieRenderRules.java"));
        for (String marker : Set.of(
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-form",
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-first-person-piglin")) {
            assertEquals(1, SourceScan.countOccurrences(rawVisuals, marker),
                    "each visual texture seam must have one stable marker");
        }
        for (String marker : Set.of(
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-normal",
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-drowned",
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-husk")) {
            assertEquals(1, SourceScan.countOccurrences(rawRules, marker),
                    "each render-plan texture seam must have one stable marker");
        }

        String rawTextureFor = SourceScan.methodBody(
                rawVisuals, " textureFor(ZombieForm form, boolean baby)");
        String rawFirstPersonTextureFor = SourceScan.methodBody(
                rawVisuals, " firstPersonTextureFor(ZombieForm form, boolean baby)");
        assertTrue(SourceScan.containsInOrder(
                        rawTextureFor,
                        "CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-form",
                        "//? if >=26.1 {",
                        "if (baby)",
                        "case DROWNED -> Identifier.withDefaultNamespace("
                                + "\"textures/entity/zombie/drowned_baby.png\")",
                        "case HUSK -> Identifier.withDefaultNamespace("
                                + "\"textures/entity/zombie/husk_baby.png\")",
                        "case ZOMBIFIED_PIGLIN -> Identifier.withDefaultNamespace("
                                + "\"textures/entity/piglin/zombified_piglin.png\")",
                        "default -> Identifier.withDefaultNamespace("
                                + "\"textures/entity/zombie/zombie_baby.png\")",
                        "//?}"),
                "the shared selector must gate the exact vanilla form-to-texture mapping");
        assertTrue(SourceScan.containsInOrder(
                        rawFirstPersonTextureFor,
                        "CROSS_VERSION-BABY-MONSTER-TEXTURE:visuals-first-person-piglin",
                        "//? if >=26.1 {",
                        "if (baby && form == ZombieForm.ZOMBIFIED_PIGLIN)",
                        "return Identifier.withDefaultNamespace("
                                + "\"textures/entity/piglin/zombified_piglin_baby.png\")",
                        "//?}"),
                "the first-person piglin override must bind its baby texture inside the same boundary");

        String rawPlan = SourceScan.methodBody(
                rawRules, "public static ZombieRenderPlan monsterBodyPlan");
        assertEquals(3, SourceScan.countOccurrences(rawPlan, "//? if >=26.1 {"),
                "normal, drowned, and husk plans each require one high-node branch");
        assertEquals(3, SourceScan.countOccurrences(rawPlan, "//?} else {"),
                "normal, drowned, and husk plans each require one legacy fallback");
        assertTrue(SourceScan.containsInOrder(
                        rawPlan,
                        "case DROWNED ->",
                        "case HUSK ->",
                        "case ZOMBIFIED_PIGLIN ->",
                        "case GIANT ->",
                        "case NORMAL ->"),
                "the render-plan form arms must remain uniquely ordered for exact seam slicing");
        for (String arm : Set.of(
                "case DROWNED ->",
                "case HUSK ->",
                "case ZOMBIFIED_PIGLIN ->",
                "case GIANT ->",
                "case NORMAL ->")) {
            assertEquals(1, SourceScan.countOccurrences(rawPlan, arm),
                    "each render-plan form arm must remain unique: " + arm);
        }
        int drownedPlanStart = rawPlan.indexOf("case DROWNED ->");
        int huskPlanStart = rawPlan.indexOf("case HUSK ->");
        int zombifiedPiglinPlanStart = rawPlan.indexOf("case ZOMBIFIED_PIGLIN ->");
        int normalPlanStart = rawPlan.indexOf("case NORMAL ->");
        String drownedPlanArm = rawPlan.substring(drownedPlanStart, huskPlanStart);
        String huskPlanArm = rawPlan.substring(huskPlanStart, zombifiedPiglinPlanStart);
        String normalPlanArm = rawPlan.substring(normalPlanStart);
        assertBabyTexturePlanSeam(
                drownedPlanArm,
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-drowned",
                "minecraft:textures/entity/zombie/drowned_baby.png",
                "DROWNED",
                distinctBabyTextures);
        assertBabyTexturePlanSeam(
                huskPlanArm,
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-husk",
                "minecraft:textures/entity/zombie/husk_baby.png",
                "HUSK",
                distinctBabyTextures);
        assertBabyTexturePlanSeam(
                normalPlanArm,
                "CROSS_VERSION-BABY-MONSTER-TEXTURE:rules-normal",
                "minecraft:textures/entity/zombie/zombie_baby.png",
                "NORMAL",
                distinctBabyTextures);

        String activeTextureFor = SourceScan.stripComments(rawTextureFor);
        String activeFirstPersonTextureFor =
                SourceScan.stripComments(rawFirstPersonTextureFor);
        String activePlan = SourceScan.stripComments(rawPlan);
        String compactActiveTextureFor = SourceScan.compact(activeTextureFor);
        String compactActiveFirstPersonTextureFor =
                SourceScan.compact(activeFirstPersonTextureFor);
        if (distinctBabyTextures) {
            assertTrue(compactActiveTextureFor.contains(
                            "caseDROWNED->Identifier.withDefaultNamespace("
                                    + "\"textures/entity/zombie/drowned_baby.png\")"),
                    "the live drowned selector must bind the drowned baby texture");
            assertTrue(compactActiveTextureFor.contains(
                            "caseHUSK->Identifier.withDefaultNamespace("
                                    + "\"textures/entity/zombie/husk_baby.png\")"),
                    "the live husk selector must bind the husk baby texture");
            assertTrue(compactActiveTextureFor.contains(
                            "caseZOMBIFIED_PIGLIN->Identifier.withDefaultNamespace("
                                    + "\"textures/entity/piglin/zombified_piglin.png\")"),
                    "the shared selector must keep the adult piglin texture");
            assertTrue(compactActiveTextureFor.contains(
                            "default->Identifier.withDefaultNamespace("
                                    + "\"textures/entity/zombie/zombie_baby.png\")"),
                    "the live default selector must bind the zombie baby texture");
            assertTrue(compactActiveFirstPersonTextureFor.contains(
                            "if(baby&&form==ZombieForm.ZOMBIFIED_PIGLIN){"
                                    + "returnIdentifier.withDefaultNamespace("
                                    + "\"textures/entity/piglin/zombified_piglin_baby.png\");}"),
                    "the live first-person override must bind the piglin baby texture to its condition");
        }
        for (String path : Set.of(
                "textures/entity/zombie/zombie_baby.png",
                "textures/entity/zombie/drowned_baby.png",
                "textures/entity/zombie/husk_baby.png")) {
            assertEquals(distinctBabyTextures ? 1 : 0,
                    SourceScan.countOccurrences(activeTextureFor, path),
                    "active textureFor must match the node's vanilla resources: " + path);
            assertEquals(distinctBabyTextures ? 1 : 0,
                    SourceScan.countOccurrences(activePlan, path),
                    "active render plan must match the node's vanilla resources: " + path);
        }
        String piglinBaby =
                "textures/entity/piglin/zombified_piglin_baby.png";
        assertEquals(distinctBabyTextures ? 1 : 0,
                SourceScan.countOccurrences(activeFirstPersonTextureFor, piglinBaby),
                "active first-person selector must match the node's vanilla resources");
        assertTrue(activeTextureFor.contains("ZombieRenderRules.monsterTexturePath(form)"),
                "legacy nodes must fall through to the existing adult form texture selector");
        assertTrue(activeFirstPersonTextureFor.contains("return textureFor(form, baby)"),
                "all first-person forms must retain the shared texture fallback");
        for (String body : Set.of(
                "ZombieMonsterBody.ZOMBIE_BABY",
                "ZombieMonsterBody.DROWNED_BABY",
                "ZombieMonsterBody.HUSK_BABY")) {
            assertTrue(activePlan.contains(body),
                    "texture fallback must not remove or replace baby geometry: " + body);
        }
    }

    @Test
    void zombiePlayerSleepingPoseMirrorsSleepingPosOntoShape() throws IOException {
        String shapeEntities = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"));
        String method = SourceScan.methodBody(shapeEntities, "private static void syncShape");

        // The live third-person render submits the cached shape entity through vanilla LivingEntityRenderer,
        // which lays a sleeper FLAT + CENTERED only when the render state's bedOrientation is non-null. Vanilla
        // derives that orientation from the entity's sleeping block position, so syncShape must mirror the player's
        // sleeping pos onto the shape -- and CLEAR it when awake, since the shape is cached and reused across frames
        // (a stale pos would keep the body flat after standing up). The visual itself is only confirmable via
        // runClient (GL), so this source scan pins the load-bearing data flow the headless harness can verify.
        assertTrue(method.contains("player.getSleepingPos()"),
                "syncShape must read the player's sleeping block position");
        assertTrue(method.contains("shape.setSleepingPos("),
                "syncShape must set the sleeping pos on the shape so vanilla derives a non-null bedOrientation (coffin facing)");
        assertTrue(method.contains("shape.clearSleepingPos()"),
                "syncShape must clear the shape's sleeping pos when the player is awake (the shape is cached/reused)");
    }

    private static void assertBabyTexturePlanSeam(
            String arm,
            String marker,
            String highTexture,
            String form,
            boolean distinctBabyTextures) {
        String highExpression =
                "baby ? \"" + highTexture + "\" : monsterTexturePath(ZombieForm." + form + ")";
        String lowExpression = "monsterTexturePath(ZombieForm." + form + ")";
        if (distinctBabyTextures) {
            assertTrue(SourceScan.containsInOrder(
                            arm,
                            marker,
                            "//? if >=26.1 {",
                            highExpression,
                            "//?} else {",
                            "/*" + lowExpression,
                            "*///?}"),
                    "the high-node seam must bind both projections in one form arm: " + form);
        } else {
            assertTrue(SourceScan.containsInOrder(
                            arm,
                            marker,
                            "//? if >=26.1 {",
                            "/*" + highExpression,
                            "*///?} else {",
                            lowExpression,
                            "//?}"),
                    "the low-node seam must bind both projections in one form arm: " + form);
        }
    }
}
