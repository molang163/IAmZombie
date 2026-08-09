package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the local API seams needed only by the 1.21.8 production source. */
class Legacy1218ApiBoundarySourceTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void distributionAccessorUsesTheNodeNativeFmlBoundary() throws IOException {
        for (Path relative : List.of(
                Path.of("dev/molang/iamzombieq/IAmZombieMod.java"),
                Path.of("dev/molang/iamzombieq/config/ConfigMigrationBootstrap.java"))) {
            String source = Files.readString(MAIN_JAVA.resolve(relative));
            String methodAnchor = relative.endsWith("IAmZombieMod.java")
                    ? "public IAmZombieMod(IEventBus modEventBus, ModContainer modContainer)"
                    : "public static void migratePhysicalClientPreferences";
            String rawBoundary = SourceScan.methodBody(source, methodAnchor);
            assertExactLegacyBoundary(
                    rawBoundary, "FMLEnvironment.getDist()", "FMLEnvironment.dist");
        }
    }

    @Test
    void armFaceCenterUsesEquivalentVertexCoordinates() throws IOException {
        Path relative = Path.of("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java");
        String source = Files.readString(MAIN_JAVA.resolve(relative));
        String rawMethod = SourceScan.methodBody(source, "private static Vector3f positiveYTipCenter");
        String high = "center.add(vertex.worldX(), vertex.worldY(), vertex.worldZ())";
        String low = "center.add(vertex.pos().x() / 16.0F, vertex.pos().y() / 16.0F, vertex.pos().z() / 16.0F)";
        assertExactLegacyBoundary(rawMethod, high, low);
    }

    @Test
    void blockPropertiesKeepOneLazyDefinitionAcrossTheRegisterBoundary() throws IOException {
        Path relative = Path.of("dev/molang/iamzombieq/IAmZombieBlocks.java");
        String source = Files.readString(MAIN_JAVA.resolve(relative));
        assertEquals(1, SourceScan.countOccurrences(source, "Supplier<BlockBehaviour.Properties>"));
        String rawHelper = SourceScan.methodBody(source,
                "private static <B extends Block> DeferredBlock<B> registerBlock");
        String high = "return BLOCKS.registerBlock(name, factory, properties);";
        String low = SourceScan.compact(
                "return BLOCKS.register(name,"
                        + "key -> factory.apply(properties.get().setId("
                        + "ResourceKey.create(Registries.BLOCK, key))));");
        assertExactLegacyBoundary(rawHelper, high, low);
        assertEquals(3, SourceScan.countOccurrences(source, "() -> BlockBehaviour.Properties.ofFullCopy("),
                "all three property factories must remain lazy and single-sourced");
    }

    @Test
    void coffinRespawnUsesTheNodeNativeStructuresWithoutLosingAuthority() throws IOException {
        Path relative = Path.of("dev/molang/iamzombieq/block/CoffinBlock.java");
        String source = Files.readString(MAIN_JAVA.resolve(relative));

        String rawStand = SourceScan.methodBody(source,
                "public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition");
        String highStand = "ServerPlayer.RespawnPosAngle.of(standUpPos, pos, 0.0F)";
        String lowStand = "ServerPlayer.RespawnPosAngle.of(standUpPos, pos)";
        assertExactLegacyBoundary(rawStand, highStand, lowStand);

        String rawSet = SourceScan.methodBody(source, "public static void setCoffinRespawn");
        String highSet = SourceScan.compact(
                "ServerPlayer.RespawnConfig respawnConfig = new ServerPlayer.RespawnConfig("
                        + "LevelData.RespawnData.of(level.dimension(), pos, player.getYRot(), player.getXRot()),"
                        + "false);");
        String lowSet = SourceScan.compact(
                "ServerPlayer.RespawnConfig respawnConfig = "
                        + "new ServerPlayer.RespawnConfig(level.dimension(), pos, player.getYRot(), false);");
        assertExactLegacyBoundary(rawSet, highSet, lowSet);
        assertEquals(1, SourceScan.countOccurrences(
                SourceScan.compact(rawSet), "player.setRespawnPosition(respawnConfig,true)"));
    }

    @Test
    void reinforcementSpawnKeepsTheLegacyServerAndMobSpawningGate() throws IOException {
        Path relative = Path.of("dev/molang/iamzombieq/gameplay/ZombieReinforcementEvents.java");
        String source = Files.readString(MAIN_JAVA.resolve(relative));
        String rawMethod = SourceScan.methodBody(source,
                "private static void attemptSpawnReinforcements");
        String high = SourceScan.compact(
                "if (!ZombieReinforcementRules.canSpawnReinforcements("
                        + "gameDifficulty(level.getDifficulty()), level.isSpawningMonsters())) {");
        String low = SourceScan.compact(
                "if (!ZombieReinforcementRules.canSpawnReinforcements("
                        + "gameDifficulty(level.getDifficulty()),"
                        + "level.getServer().isSpawningMonsters()"
                        + "&& level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))) {");
        assertExactLegacyBoundary(rawMethod, high, low);
    }

    @Test
    void itemPropertiesStayLazyAcrossSupplierAndOperatorBoundaries() throws IOException {
        Path relative = Path.of("dev/molang/iamzombieq/IAmZombieItems.java");
        String source = Files.readString(MAIN_JAVA.resolve(relative));

        String rawSupplier = SourceScan.methodBody(source,
                "private static <I extends Item> DeferredItem<I> registerItem");
        String highSupplier = "return ITEMS.registerItem(name, factory, properties);";
        String lowSupplier = SourceScan.compact(
                "return ITEMS.register(name,"
                        + "key -> factory.apply(properties.get().setId("
                        + "ResourceKey.create(Registries.ITEM, key))));");
        assertExactLegacyBoundary(rawSupplier, highSupplier, lowSupplier);

        String rawOperator = SourceScan.methodBody(source,
                "private static DeferredItem<Item> registerSimpleItem");
        String highOperator = "return ITEMS.registerSimpleItem(name, properties);";
        String lowOperator = "return registerItem(name, Item::new, () -> properties.apply(new Item.Properties()));";
        assertExactLegacyBoundary(rawOperator, highOperator, lowOperator);

        assertTrue(source.contains("() -> new Item.Properties().stacksTo(1)"));
        assertTrue(source.contains(".alwaysEdible()") && source.contains(".nutrition(8)")
                && source.contains(".saturationModifier(1.2f)"));
        assertTrue(source.contains(".equippable(EquipmentSlot.HEAD)")
                && source.contains(".component(DataComponents.UNBREAKABLE, Unit.INSTANCE)"));
        assertTrue(source.contains(".durability(15)")
                && source.contains("ModIds.id(DisguiseRules.DISGUISE_MASK_PATH)"));
    }

    private static void assertExactLegacyBoundary(String rawMethod, String highBranch, String lowBranch) {
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//? if >=1.21.10 {"));
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//?} else {"));
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "*///?}"));
        String compactMethod = SourceScan.compact(rawMethod);
        String compactHigh = SourceScan.compact(highBranch);
        String compactLow = SourceScan.compact(lowBranch);
        assertEquals(1, SourceScan.countOccurrences(compactMethod, compactHigh));
        assertEquals(1, SourceScan.countOccurrences(compactMethod, compactLow));
        String opening = SourceScan.compact("//? if >=1.21.10 {");
        String alternative = SourceScan.compact("//?} else {");
        int openingIndex = compactMethod.indexOf(opening);
        int highIndex = compactMethod.indexOf(compactHigh);
        int alternativeIndex = compactMethod.indexOf(alternative);
        int lowIndex = compactMethod.indexOf(compactLow);
        int closingIndex = compactMethod.lastIndexOf("//?}");
        boolean highActive = compactMethod.contains(alternative + "/*")
                && compactMethod.indexOf("*///?}", lowIndex) > lowIndex;
        boolean lowActive = compactMethod.contains(opening + "/*")
                && compactMethod.indexOf("*///?}else{", highIndex) > highIndex
                && closingIndex > lowIndex;
        assertTrue(
                openingIndex >= 0
                        && highIndex > openingIndex
                        && alternativeIndex > highIndex
                        && lowIndex > alternativeIndex
                        && closingIndex > lowIndex
                        && (highActive || lowActive),
                "the high and 1.21.8 API literals must remain inside their declared Stonecutter branches");
    }
}
