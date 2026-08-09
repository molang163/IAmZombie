package dev.molang.iamzombieq.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProgrammaticGameTestRegistrationSourceTest {
    private static final String LEGACY_MIXIN_PACKAGE = "dev.molang.iamzombieq.gametest.mixin";
    private static final Path LEGACY_MIXIN_DIRECTORY = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin");
    private static final Path INSTANCE = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/ConsumerGameTestInstance.java");
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieGameTestRegistry.java");
    private static final Path RESTORING_HARD_ENVIRONMENT = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/RestoringHardDifficultyEnvironment.java");
    private static final Path LEGACY_PADDING = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/LegacyGameTestPadding.java");
    private static final Path LEGACY_PADDING_BLOCK_ENTITY_MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin/LegacyGameTestPaddingBlockEntityMixin.java");
    private static final Path LEGACY_PADDING_EXISTING_CLEAR_MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin/LegacyGameTestPaddingExistingClearMixin.java");
    private static final Path LEGACY_PADDING_1218_CLEAR_MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin/LegacyGameTestPadding1218ClearMixin.java");
    private static final Path LEGACY_PADDING_GRID_MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin/LegacyGameTestPaddingGridSpawnerMixin.java");
    private static final Path LEGACY_PADDING_INFO_MIXIN = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/mixin/LegacyGameTestPaddingInfoMixin.java");
    private static final Path LEGACY_PADDING_MIXIN_CONFIG = Path.of(
            "src/main/resources/iamzombieq.legacy-gametest.mixins.json");
    private static final Path LEGACY_PADDING_1218_MIXIN_CONFIG = Path.of(
            "src/main/resources/iamzombieq.legacy-gametest-1.21.8.mixins.json");
    private static final Path BUILD = Path.of("build.gradle");
    private static final Path CONTROLLER = Path.of("stonecutter.gradle.kts");
    private static final Path FIX_REGRESSION_BODIES = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTestBodies.java");
    private static final List<Path> SUITES = List.of(
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieDisguiseGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieFoodInfGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieFormGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieGiantSunGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieMountGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTests.java"),
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieHerobrineGameTests.java"));

    @Test
    void legacyMixinPackageContainsOnlyTheExactMixinInventory() throws IOException {
        Set<String> expected = Set.of(
                "LegacyGameTestPaddingBlockEntityMixin.java",
                "LegacyGameTestPaddingExistingClearMixin.java",
                "LegacyGameTestPadding1218ClearMixin.java",
                "LegacyGameTestPaddingGridSpawnerMixin.java",
                "LegacyGameTestPaddingInfoMixin.java");

        assertTrue(Files.isDirectory(LEGACY_MIXIN_DIRECTORY),
                "legacy mixins must use a package that cannot cover ordinary GameTest subscribers or helpers");
        Set<String> actual;
        try (var files = Files.list(LEGACY_MIXIN_DIRECTORY)) {
            actual = files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
        assertEquals(expected, actual, "the dedicated package must contain exactly the five real mixins");
        for (String file : expected) {
            String source = Files.readString(LEGACY_MIXIN_DIRECTORY.resolve(file));
            assertTrue(source.contains("package " + LEGACY_MIXIN_PACKAGE + ";"));
            assertEquals(1, SourceScan.countOccurrences(source, "@Mixin("));
        }
        for (Path config : List.of(LEGACY_PADDING_MIXIN_CONFIG, LEGACY_PADDING_1218_MIXIN_CONFIG)) {
            assertTrue(SourceScan.compact(Files.readString(config))
                            .contains("\"package\":\"" + LEGACY_MIXIN_PACKAGE + "\""),
                    "mixin configuration must not claim the ordinary GameTest package");
        }
        assertTrue(Files.exists(LEGACY_PADDING), "the padding registry/helper remains in the ordinary package");
        assertTrue(Files.exists(REGISTRY), "the FML subscriber remains outside the mixin-owned package");
        String bridge = Files.readString(Path.of(
                "src/main/java/dev/molang/iamzombieq/gametest/LegacyGameTestPaddingBridge.java"));
        assertTrue(bridge.contains("@ApiStatus.Internal"));
        assertFalse(bridge.contains("java.lang.reflect") || bridge.contains("MethodHandle"));
        assertEquals(4, SourceScan.countOccurrences(bridge, "public static "),
                "the internal bridge exposes only the four operations needed by the mixins");
    }
    private static final Pattern TEST_ID = Pattern.compile(
            "(?:register|registerPadded)\\(\\s*event,\\s*\"([^\"]+)\"");
    private static final Pattern ENVIRONMENT_ID = Pattern.compile(
            "event\\.registerEnvironment\\(\\s*modId\\(\"([^\"]+)\"\\)");
    private static final Pattern FIX_REGRESSION_BODY = Pattern.compile(
            "\\bstatic\\s+void\\s+(\\w+)\\s*\\(\\s*GameTestHelper\\s+helper\\s*\\)");
    private static final Pattern STATIC_VOID_METHOD = Pattern.compile(
            "\\b(?:public\\s+|protected\\s+|private\\s+)?static\\s+void\\s+(\\w+)\\s*\\(");
    private static final String FULL_MOD_TEST_ID_SHA256 =
            "7922df9c8c8e93122152f95fcc1541e9615ed2e0f8332a180168ddeac609ac8b";
    private static final String LOW_NODE_MOD_TEST_ID_SHA256 =
            "cb3304b1633949cf11bc1db97a751abddc4687b451956fe699fd903f8cd6b5ac";
    private static final List<FixRegressionRegistration> FIX_REGRESSION_REGISTRATIONS = List.of(
            new FixRegressionRegistration(
                    "reg_nautilus_saddle_not_fabricated", "nautilusSaddleNotFabricated", false),
            new FixRegressionRegistration(
                    "reg_piglin_conversion_not_baby_and_armed", "piglinConversionNotBabyAndArmed", false),
            new FixRegressionRegistration(
                    "reg_cake_candle_place_not_punished", "cakeCandlePlaceNotPunished", false),
            new FixRegressionRegistration(
                    "reg_cake_normal_bite_still_punished", "cakeNormalBiteStillPunished", false),
            new FixRegressionRegistration(
                    "reg_cake_candle_on_bitten_cake_still_punished",
                    "cakeCandleOnBittenCakeStillPunished",
                    false),
            new FixRegressionRegistration(
                    "reg_lit_candlecake_body_eat_still_punished",
                    "litCandleCakeBodyEatStillPunished",
                    false),
            new FixRegressionRegistration(
                    "reg_lit_candlecake_extinguish_not_punished",
                    "litCandleCakeExtinguishNotPunished",
                    false),
            new FixRegressionRegistration(
                    "reg_giant_aura_spares_owned_horse_stomps_wild",
                    "giantAuraSparesOwnedHorseStompsWild",
                    false),
            new FixRegressionRegistration(
                    "reg_giant_aura_spares_owned_nautilus_stomps_wild",
                    "giantAuraSparesOwnedNautilusStompsWild",
                    false),
            new FixRegressionRegistration(
                    "reg_giant_sweep_clamp_bounds_teleport", "giantSweepClampBoundsTeleport", true));

    @Test
    void programmaticTestsUseVanillaFunctionCodecAndRegisteredDispatcher() throws IOException {
        String instance = Files.readString(INSTANCE);
        String registry = Files.readString(REGISTRY);

        assertTrue(instance.contains("extends FunctionGameTestInstance"));
        assertTrue(instance.contains("super(DISPATCHER_KEY, info)"));
        assertFalse(instance.contains("MapCodec"));
        assertFalse(instance.contains("UnsupportedOperationException"));
        assertTrue(registry.contains("Registries.TEST_FUNCTION"));
        assertTrue(registry.contains("ConsumerGameTestInstance::dispatch"));

        String dispatch = SourceScan.methodBody(instance, "static void dispatch");
        assertTrue(dispatch.contains("helper.testInfo.id()"));
        assertTrue(dispatch.contains("throw new IllegalStateException"),
                "a missing body must fail loudly instead of running an empty or incorrect test");
    }

    @Test
    void allExistingModTestsKeepUniqueIdsAndShareTheIdWithTheirBodyMapping() throws IOException {
        String registry = Files.readString(REGISTRY);
        String activeRegistry = SourceScan.stripComments(registry);
        StringBuilder sources = new StringBuilder();
        for (Path suite : SUITES) {
            sources.append(Files.readString(suite));
        }
        HashSet<Path> discoveredSuites = new HashSet<>();
        try (var paths = Files.list(Path.of("src/main/java/dev/molang/iamzombieq/gametest"))) {
            paths.filter(path -> path.getFileName().toString().matches("IAmZombie.*GameTests\\.java"))
                    .forEach(discoveredSuites::add);
        }
        assertEquals(new HashSet<>(SUITES), discoveredSuites,
                "the explicit suite inventory must match all *GameTests registration classes");

        String allSuites = sources.toString();
        String activeSuites = SourceScan.stripComments(allSuites);
        String compactActiveSuites = SourceScan.compact(activeSuites);
        Matcher authorityMatcher = TEST_ID.matcher(allSuites);
        HashSet<String> authorityIds = new HashSet<>();
        int authorityRegistrations = 0;
        while (authorityMatcher.find()) {
            authorityRegistrations++;
            authorityIds.add(authorityMatcher.group(1));
        }
        assertEquals(85, authorityRegistrations,
                "raw canonical source must retain the complete 85-test authority");
        assertEquals(authorityRegistrations, authorityIds.size(),
                "raw canonical GameTest authority IDs must remain unique");
        assertEquals(FULL_MOD_TEST_ID_SHA256, normalizedModIdSha256(authorityIds),
                "raw canonical GameTest authority must retain every exact ID");

        Matcher matcher = TEST_ID.matcher(activeSuites);
        HashSet<String> ids = new HashSet<>();
        int registrations = 0;
        while (matcher.find()) {
            registrations++;
            ids.add(matcher.group(1));
        }

        assertEquals(StonecutterCapabilityMatrix.expectedModGameTests(), registrations,
                "active mod GameTests must match the frozen platform capability");
        assertEquals(StonecutterCapabilityMatrix.expectedTotalGameTests(), registrations + 1,
                "active mod GameTests plus vanilla always_pass must match the frozen platform capability");
        assertEquals(registrations, ids.size(), "programmatic GameTest IDs must remain unique");
        HashSet<String> inactiveAuthorityIds = new HashSet<>(authorityIds);
        inactiveAuthorityIds.removeAll(ids);
        Set<String> expectedInactiveIds = StonecutterCapabilityMatrix.hasNautilusEntityApi()
                ? Set.of()
                : StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS;
        assertEquals(expectedInactiveIds, inactiveAuthorityIds,
                "the active GameTest surface may differ from canonical authority only by the two owner-approved N/A IDs");
        assertTrue(authorityIds.containsAll(ids),
                "active GameTest registrations must be a strict subset of canonical authority");
        assertEquals(
                StonecutterCapabilityMatrix.hasNautilusEntityApi()
                        ? FULL_MOD_TEST_ID_SHA256
                        : LOW_NODE_MOD_TEST_ID_SHA256,
                normalizedModIdSha256(ids),
                "active GameTest IDs must equal the exact frozen high or owner-approved low inventory");
        assertTrue(ids.contains("trade_undisguised_zombie_is_denied"),
                "the undisguised trade gate must remain registered under its exact ID");
        assertTrue(ids.contains("trade_disguised_zombie_opens_and_damages_mask"),
                "the disguised real-trade path must remain registered under its exact ID");
        assertTrue(ids.contains("herobrine_lethal_attack_respawns_in_place"),
                "the lethal Herobrine respawn path must remain registered under its exact ID");
        assertTrue(ids.contains("herobrine_right_click_is_cancelled"),
                "the real Herobrine right-click cancellation path must remain registered under its exact ID");
        assertTrue(ids.contains("villager_fear_respects_disguise"),
                "the villager disguise fear path must remain registered under its exact ID");
        assertTrue(ids.contains("wandering_trader_fear_respects_disguise"),
                "the wandering-trader disguise fear path must remain registered under its exact ID");
        assertTrue(ids.contains("coffin_sleep_vote_advances_and_wakes_all"),
                "the coffin sleep vote must remain registered under its exact ID");
        assertTrue(ids.contains("coffin_sleep_timeout_wakes_without_skip"),
                "the coffin sleep timeout must remain registered under its exact ID");
        assertTrue(ids.contains("s1_transform_pre_giant_kill_veto"),
                "the giant-kill Transform Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_giant_kill_pass"),
                "the giant-kill Transform Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_clone_reset_veto_preserves_state"),
                "the clone-reset Transform Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_clone_reset_pass"),
                "the clone-reset Transform Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_drowning_veto_real_death"),
                "the drowning Evolve Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_drowning_pass_once"),
                "the drowning Evolve Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_starvation_veto_real_death"),
                "the starvation Evolve Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_starvation_pass"),
                "the starvation Evolve Pre pass path must retain its exact ID");
        assertEquals(9, SourceScan.countOccurrences(
                activeSuites, "new ConsumerGameTestInstance(id, info, body)"));
        assertEquals(9, SourceScan.countOccurrences(activeSuites, "event.registerTest(id,"));
        assertEquals(9, SourceScan.countOccurrences(activeRegistry, ".registerAll("),
                "all nine GameTest suites must remain wired through the shared registry");
        assertEquals(9, SourceScan.countOccurrences(compactActiveSuites, "newTestData<>("),
                "all active mod tests must flow through exactly the nine suite registration helpers");
        assertEquals(9, SourceScan.countOccurrences(
                        compactActiveSuites, ",true,Rotation.NONE,false,1,1,"),
                "every suite helper must keep required, non-manual, single-attempt metadata");
        assertFalse(activeSuites.contains("new ConsumerGameTestInstance(info, body)"));
    }

    @Test
    void gameTestEnvironmentsKeepExactIdsAndNodeNativeDifficultyRestoration() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes = Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(knownNodes.contains(executingNode), "unknown Stonecutter test node: " + executingNode);
        boolean nativeDifficultyEnvironment = executingNode.equals("26.2.x");

        String registry = SourceScan.stripComments(Files.readString(REGISTRY));
        String gameRegistration = SourceScan.methodBody(registry, "public static void onRegisterGameTests");
        String compactGameRegistration = SourceScan.compact(gameRegistration);
        String functionRegistration =
                SourceScan.methodBody(registry, "public static void onRegisterTestFunctions");
        String compactFunctionRegistration = SourceScan.compact(functionRegistration);

        Matcher environmentMatcher = ENVIRONMENT_ID.matcher(gameRegistration);
        HashSet<String> environmentIds = new HashSet<>();
        int environmentRegistrations = 0;
        while (environmentMatcher.find()) {
            environmentRegistrations++;
            environmentIds.add(environmentMatcher.group(1));
        }
        Set<String> expectedEnvironmentIds = Set.of(
                "env_default",
                "env_hard",
                "env_herobrine_lethal",
                "env_herobrine_gaze",
                "env_herobrine_interact",
                "env_herobrine_cave",
                "env_herobrine_lifetime",
                "env_coffin_vote",
                "env_coffin_timeout");
        assertEquals(9, environmentRegistrations);
        assertEquals(expectedEnvironmentIds, environmentIds);

        String nativeProvider =
                "newTestEnvironmentDefinition." + "SetDifficulty(Difficulty.HARD)";
        String restoringProvider = "newRestoringHardDifficulty" + "Environment()";
        String expectedProvider = nativeDifficultyEnvironment ? nativeProvider : restoringProvider;
        for (String id : List.of(
                "env_hard",
                "env_herobrine_lethal",
                "env_herobrine_gaze",
                "env_herobrine_interact",
                "env_herobrine_lifetime")) {
            assertEquals(1, SourceScan.countOccurrences(
                            compactGameRegistration,
                            "event.registerEnvironment(modId(\"" + id + "\")," + expectedProvider + ")"),
                    "HARD provider must remain attached to its exact environment ID: " + id);
        }
        assertEquals(1, SourceScan.countOccurrences(
                        compactGameRegistration,
                        "event.registerEnvironment(modId(\"env_herobrine_cave\"),"
                                + "HerobrineCaveSeaLevelEnvironment.INSTANCE,"
                                + expectedProvider + ")"),
                "the cave batch must compose its sea-level environment before its HARD provider");
        assertEquals(nativeDifficultyEnvironment ? 6 : 0,
                SourceScan.countOccurrences(compactGameRegistration, nativeProvider));
        assertEquals(nativeDifficultyEnvironment ? 0 : 6,
                SourceScan.countOccurrences(compactGameRegistration, restoringProvider));
        assertFalse(compactGameRegistration.contains("TestEnvironmentDefinition.Functions"));

        String restoringCodec =
                "event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,"
                        + "modId(\"restoring_hard_difficulty\"),"
                        + "()->RestoringHardDifficultyEnvironment.CODEC)";
        assertEquals(nativeDifficultyEnvironment ? 0 : 1,
                SourceScan.countOccurrences(compactFunctionRegistration, restoringCodec));
        assertEquals(nativeDifficultyEnvironment ? 1 : 2,
                SourceScan.countOccurrences(
                        compactFunctionRegistration, "Registries.TEST_ENVIRONMENT_DEFINITION_TYPE"));

        String environmentSource = SourceScan.stripComments(Files.readString(RESTORING_HARD_ENVIRONMENT));
        String compactEnvironment = SourceScan.compact(environmentSource);
        assertTrue(compactEnvironment.contains(
                "MapCodec.unit(RestoringHardDifficultyEnvironment::new)"));
        assertFalse(compactEnvironment.contains("MapCodec.unit(INSTANCE)"));
        assertFalse(compactEnvironment.contains("staticfinalMap<"));

        if (executingNode.equals("26.1.x")) {
            String setup = SourceScan.compact(
                    SourceScan.methodBody(environmentSource, "public Difficulty setup"));
            assertTrue(SourceScan.containsInOrder(
                    setup,
                    "DifficultyoldDifficulty=level.getDifficulty();",
                    "level.getServer().setDifficulty(Difficulty.HARD,true);",
                    "returnoldDifficulty;"));
            String teardown = SourceScan.compact(
                    SourceScan.methodBody(environmentSource, "public void teardown"));
            assertTrue(teardown.contains(
                    "level.getServer().setDifficulty(savedDifficulty,true);"));
        } else if (!nativeDifficultyEnvironment) {
            String setup = SourceScan.compact(
                    SourceScan.methodBody(environmentSource, "public void setup"));
            assertTrue(SourceScan.containsInOrder(
                    setup,
                    ".push(level.getDifficulty());",
                    "server.setDifficulty(Difficulty.HARD,true);"));
            String teardown = SourceScan.compact(
                    SourceScan.methodBody(environmentSource, "public void teardown"));
            assertTrue(SourceScan.containsInOrder(
                    teardown,
                    "if(stack==null||stack.isEmpty())",
                    "DifficultysavedDifficulty=stack.peek();",
                    "server.setDifficulty(savedDifficulty,true);",
                    "stack.pop();"));
            assertTrue(teardown.contains("thrownewIllegalStateException("));
        }

        String compactController = SourceScan.compact(Files.readString(CONTROLLER));
        assertEquals(1, SourceScan.countOccurrences(
                compactController,
                "replace(\"" + nativeProvider + "\",\"" + restoringProvider + "\")"));

        for (Path forbidden : List.of(
                Path.of("src/main/resources/data/iamzombieq/function/set_hard_difficulty.mcfunction"),
                Path.of("src/main/resources/data/iamzombieq/functions/set_hard_difficulty.mcfunction"),
                Path.of("src/generated/resources/data/iamzombieq/function/set_hard_difficulty.mcfunction"),
                Path.of("src/generated/resources/data/iamzombieq/functions/set_hard_difficulty.mcfunction"))) {
            assertFalse(Files.exists(forbidden), "difficulty setup must not leak through a function: " + forbidden);
        }
    }

    @Test
    void testEnvironmentHolderGenericTracksDefinitionApi() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> genericNodes = Set.of("26.2.x", "26.1.x");
        Set<String> legacyNodes = Set.of("1.21.11", "1.21.10", "1.21.8");
        assertTrue(genericNodes.contains(executingNode) || legacyNodes.contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);

        StringBuilder activeSources = new StringBuilder()
                .append(SourceScan.stripComments(Files.readString(INSTANCE)))
                .append(SourceScan.stripComments(Files.readString(REGISTRY)));
        for (Path suite : SUITES) {
            activeSources.append(SourceScan.stripComments(Files.readString(suite)));
        }

        String genericHolder = "Holder<TestEnvironmentDefinition<" + "?>>";
        String legacyHolder = "Holder<TestEnvironment" + "Definition>";
        String genericTestData = "TestData<" + genericHolder + ">";
        String legacyTestData = "TestData<" + legacyHolder + ">";
        boolean genericApi = genericNodes.contains(executingNode);
        assertEquals(genericApi ? 53 : 0,
                SourceScan.countOccurrences(activeSources.toString(), genericHolder));
        assertEquals(genericApi ? 0 : 53,
                SourceScan.countOccurrences(activeSources.toString(), legacyHolder));
        assertEquals(genericApi ? 10 : 0,
                SourceScan.countOccurrences(activeSources.toString(), genericTestData));
        assertEquals(genericApi ? 0 : 10,
                SourceScan.countOccurrences(activeSources.toString(), legacyTestData));

        String compactController = SourceScan.compact(Files.readString(CONTROLLER));
        assertEquals(1, SourceScan.countOccurrences(
                compactController,
                "replace(\"" + genericHolder + "\",\"" + legacyHolder + "\")"));
        assertTrue(compactController.contains(
                "string(current.parsed<\"26.1\"){"
                        + "replace(\"" + genericHolder + "\",\"" + legacyHolder + "\")}"),
                "the Holder compatibility rule must begin only at the non-generic 1.21 boundary");
        String broadGeneric = "TestEnvironmentDefinition<" + "?>";
        assertFalse(compactController.contains(
                "replace(\"" + broadGeneric + "\",\"TestEnvironmentDefinition\")"),
                "the compatibility rule must stay scoped to Holder declarations");
    }

    @Test
    void legacyNodesBackportTheExactMandatoryRunnerPaddingPath() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> legacyNodes = Set.of("1.21.11", "1.21.10", "1.21.8");
        Set<String> nativeNodes = Set.of("26.2.x", "26.1.x");
        assertTrue(legacyNodes.contains(executingNode) || nativeNodes.contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);
        boolean legacy = legacyNodes.contains(executingNode);

        StringBuilder suites = new StringBuilder();
        for (Path suite : SUITES) {
            suites.append(SourceScan.stripComments(Files.readString(suite)));
        }
        String activeSuites = SourceScan.compact(suites.toString());
        String activeRegistry = SourceScan.compact(SourceScan.stripComments(Files.readString(REGISTRY)));
        assertEquals(legacy ? 9 : 0,
                SourceScan.countOccurrences(activeSuites, "LegacyGameTestPadding.register(id,"));
        assertEquals(legacy ? 1 : 0,
                SourceScan.countOccurrences(activeRegistry, "LegacyGameTestPadding.seal();"));
        assertFalse(activeSuites.contains("Holder.direct("),
                "legacy spatial isolation must not be replaced with anonymous one-test environment batches");

        String padding = SourceScan.compact(SourceScan.stripComments(Files.readString(LEGACY_PADDING)));
        assertTrue(padding.contains(
                "privatestaticfinalintNAUTILUS_REQUIRED_TESTS="
                        + StonecutterCapabilityMatrix.activeNautilusRequiredGameTests() + ";"));
        assertTrue(padding.contains("privatestaticfinalintEXPECTED_TESTS=83+NAUTILUS_REQUIRED_TESTS;"));
        assertTrue(padding.contains("privatestaticfinalintEXPECTED_PADDING_8=80+NAUTILUS_REQUIRED_TESTS;"));
        assertTrue(padding.contains(
                "padding8!=EXPECTED_PADDING_8||padding24!=2||padding48!=1"));
        assertTrue(padding.contains("PADDINGS.putIfAbsent(key,padding)"));
        assertTrue(padding.contains("if(sealed&&oldPadding==null)"));
        assertTrue(padding.contains("testBlock.getLevel().registryAccess().get(testKey.get()).isEmpty()"),
                "stale or unresolved test keys must match the native zero-padding behavior");
        assertTrue(padding.contains("testName.startsWith(IAmZombieMod.MOD_ID+\":\")"));
        assertTrue(padding.contains(
                "thrownewIllegalStateException(\"MissinglegacyGameTestpaddingfor\"+testName)"));
        assertTrue(padding.contains(
                "returntestBlock.getStructureBoundingBox().inflatedBy(padding(testBlock));"));
        assertTrue(padding.contains(
                "returntestBlock.getStructureBounds().inflate(padding(testBlock));"));
        assertTrue(SourceScan.containsInOrder(
                padding,
                "intpadding=padding(testBlock);",
                "if(padding==0){return;}",
                "BoundingBoxtestBox=testBlock.getStructureBoundingBox().inflatedBy(padding);",
                "BlockPos.betweenClosedStream(testBox).forEach(",
                "newBlockInput(state,Collections.emptySet(),null).place(level,pos,818);",
                "level.getBlockTicks().clearArea(testBox);",
                "level.clearBlockEvents(testBox);",
                "level.getEntitiesOfClass(Entity.class,bounds,entity->!(entityinstanceofPlayer))"));
        assertFalse(padding.contains("StructureUtils.clearSpaceForStructure"),
                "1.21.8's over-expanding helper must not be used with the later exact test box");

        String blockEntityMixin = SourceScan.compact(SourceScan.stripComments(
                Files.readString(LEGACY_PADDING_BLOCK_ENTITY_MIXIN)));
        String existingClearMixin = SourceScan.compact(SourceScan.stripComments(
                Files.readString(LEGACY_PADDING_EXISTING_CLEAR_MIXIN)));
        String clear1218Mixin = SourceScan.compact(SourceScan.stripComments(
                Files.readString(LEGACY_PADDING_1218_CLEAR_MIXIN)));
        String gridMixin = SourceScan.compact(SourceScan.stripComments(
                Files.readString(LEGACY_PADDING_GRID_MIXIN)));
        String infoMixin = SourceScan.compact(SourceScan.stripComments(
                Files.readString(LEGACY_PADDING_INFO_MIXIN)));
        for (String mixin : List.of(
                blockEntityMixin, existingClearMixin, clear1218Mixin, gridMixin, infoMixin)) {
            assertFalse(mixin.contains("require=0"),
                    "legacy GameTest compatibility injections must remain mandatory");
        }
        assertTrue(blockEntityMixin.contains(
                "method=\"getStructurePos()Lnet/minecraft/core/BlockPos;\""));
        assertTrue(blockEntityMixin.contains("method=\"removeEntities()V\""));
        assertTrue(existingClearMixin.contains(
                "method=\"placeStructure(Lnet/minecraft/server/level/ServerLevel;\"+"
                        + "\"Lnet/minecraft/world/level/levelgen/structure/templatesystem/"
                        + "StructureTemplate;)V\""));
        assertTrue(existingClearMixin.contains("LegacyGameTestPaddingBridge.testBoundingBox(testBlock)"));
        assertTrue(clear1218Mixin.contains(
                "target=\"Lnet/minecraft/world/level/block/entity/TestInstanceBlockEntity;"
                        + "removeEntities()V\""));
        assertTrue(clear1218Mixin.contains("LegacyGameTestPaddingBridge.clearExactTestBox("));
        assertTrue(gridMixin.contains(
                "method=\"spawnStructure(Lnet/minecraft/gametest/framework/GameTestInfo;)Ljava/util/Optional;\""));
        assertTrue(gridMixin.contains("LegacyGameTestPaddingBridge.testBounds(testBlock)"));
        assertTrue(infoMixin.contains("method=\"placeStructure()V\""));
        assertFalse(infoMixin.contains("method=\"tick"),
                "the chunk-ready structure bound remains deliberately unpadded in the native implementation");

        String standardConfig = SourceScan.compact(Files.readString(LEGACY_PADDING_MIXIN_CONFIG));
        String config1218 = SourceScan.compact(Files.readString(LEGACY_PADDING_1218_MIXIN_CONFIG));
        for (String config : List.of(standardConfig, config1218)) {
            assertTrue(config.contains("\"required\":true"));
            assertTrue(config.contains("\"defaultRequire\":1"));
            assertFalse(config.contains("required=0"));
            assertFalse(config.contains("\"plugin\""));
            assertEquals(4, SourceScan.countOccurrences(config, "\"LegacyGameTestPadding"));
        }
        assertTrue(standardConfig.contains("\"LegacyGameTestPaddingExistingClearMixin\""));
        assertFalse(standardConfig.contains("\"LegacyGameTestPadding1218ClearMixin\""));
        assertTrue(config1218.contains("\"LegacyGameTestPadding1218ClearMixin\""));
        assertFalse(config1218.contains("\"LegacyGameTestPaddingExistingClearMixin\""));

        String build = SourceScan.compact(SourceScan.stripComments(Files.readString(BUILD)));
        assertTrue(build.contains(
                "'1.21.11':'iamzombieq.legacy-gametest.mixins.json',"
                        + "'1.21.10':'iamzombieq.legacy-gametest.mixins.json',"
                        + "'1.21.8':'iamzombieq.legacy-gametest-1.21.8.mixins.json',"));
        assertTrue(build.contains(
                "if(legacyGameTestMixinConfig!=null){"
                        + "programArguments.addAll'--mixin',legacyGameTestMixinConfig}"),
                "only the three legacy GameTestServer launches may activate the compatibility config");
        assertFalse(Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml"))
                        .contains("legacy-gametest"),
                "ordinary client and dedicated-server metadata must not activate GameTest-only mixins");
    }

    @Test
    void fixRegressionSuiteSeparatesRegistrationFromItsTenBehaviorBodies() throws IOException {
        Path registrationPath = SUITES.stream()
                .filter(path -> path.endsWith("IAmZombieFixRegressionGameTests.java"))
                .findFirst()
                .orElseThrow();
        assertTrue(Files.exists(FIX_REGRESSION_BODIES),
                "FixRegression must follow the suite registration + package-private Bodies structure");

        String registration = Files.readString(registrationPath);
        String bodies = Files.readString(FIX_REGRESSION_BODIES);
        String compactRawRegistration = SourceScan.compact(registration);
        String compactRegistration = SourceScan.compact(SourceScan.stripComments(registration));
        String compactBodies = SourceScan.compact(SourceScan.stripComments(bodies));
        for (FixRegressionRegistration authority : FIX_REGRESSION_REGISTRATIONS) {
            assertEquals(1, SourceScan.countOccurrences(
                            compactRawRegistration, registrationCall(authority)),
                    "raw canonical FixRegression authority must retain: " + authority.id());
        }
        assertEquals(10, FIX_REGRESSION_BODY.matcher(bodies).results().count(),
                "raw canonical FixRegression authority must retain all ten behavior bodies");

        List<FixRegressionRegistration> activeRegistrations = FIX_REGRESSION_REGISTRATIONS.stream()
                .filter(registrationEntry -> StonecutterCapabilityMatrix.hasNautilusEntityApi()
                        || !StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS.contains(
                                registrationEntry.id()))
                .toList();
        HashSet<String> expectedBodies = new HashSet<>();
        for (FixRegressionRegistration expected : activeRegistrations) {
            expectedBodies.add(expected.body());
            assertEquals(1, SourceScan.countOccurrences(
                            compactRegistration, registrationCall(expected)),
                    "FixRegression ID must remain wired exactly once to its matching Bodies method: "
                            + expected.id());
        }
        Matcher registrationMethodMatcher =
                STATIC_VOID_METHOD.matcher(SourceScan.stripComments(registration));
        HashSet<String> registrationMethods = new HashSet<>();
        int registrationMethodCount = 0;
        while (registrationMethodMatcher.find()) {
            registrationMethodCount++;
            registrationMethods.add(registrationMethodMatcher.group(1));
        }
        assertEquals(3, registrationMethodCount,
                "FixRegression registration must contain only registerAll/register/registerPadded static methods");
        assertEquals(new HashSet<>(List.of("registerAll", "register", "registerPadded")), registrationMethods,
                "FixRegression registration class must not retain any behavior or event helper methods");

        Matcher bodyMatcher = FIX_REGRESSION_BODY.matcher(SourceScan.stripComments(bodies));
        HashSet<String> actualBodies = new HashSet<>();
        int bodyCount = 0;
        while (bodyMatcher.find()) {
            bodyCount++;
            actualBodies.add(bodyMatcher.group(1));
        }
        assertEquals(StonecutterCapabilityMatrix.expectedFixRegressionBodies(), bodyCount,
                "active FixRegression Bodies count must match the platform capability");
        assertEquals(expectedBodies, actualBodies,
                "FixRegression Bodies must expose exactly the active registered behavior methods");
        assertTrue(bodies.contains("final class IAmZombieFixRegressionGameTestBodies"));
        assertFalse(bodies.contains("public final class IAmZombieFixRegressionGameTestBodies"),
                "FixRegression Bodies must remain package-private");
        assertTrue(compactBodies.contains("privatestaticvoidpostRightClick("));
        assertTrue(compactBodies.contains("privatestaticvoidpostRightClickAt("));
        assertFalse(compactBodies.contains("RegisterGameTestsEvent"));
        assertFalse(compactBodies.contains("ConsumerGameTestInstance"));
        assertFalse(compactBodies.contains("registerTest("));

        String register = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(registration, "private static void register(")));
        assertTrue(register.contains("registerPadded(event,name,environment,8,body);"),
                "ordinary FixRegression registrations must retain padding 8");
        String registerPaddedSource = SourceScan.stripComments(
                SourceScan.methodBody(
                        registration, "private static void registerPadded("));
        String registerPadded = SourceScan.compact(registerPaddedSource);
        String executingNode =
                System.getProperty("iamzombieq.test.nodeId");
        Set<String> nativeNodes = Set.of("26.2.x", "26.1.x");
        Set<String> legacyNodes =
                Set.of("1.21.11", "1.21.10", "1.21.8");
        assertTrue(
                nativeNodes.contains(executingNode)
                        || legacyNodes.contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);
        boolean nativePadding = nativeNodes.contains(executingNode);
        String expectedTestData = "newTestData<>(environment,modId(STRUCTURE),200,0,true,"
                + "Rotation.NONE,false,1,1,false" + (nativePadding ? ",padding" : "") + ")";
        assertTrue(registerPadded.contains(expectedTestData),
                "FixRegression metadata must retain required execution fields and the node-native padding form");
        String javaName = "[A-Za-z_$][A-Za-z0-9_$]*";
        String idType = javaName + "(?:\\." + javaName + ")*";
        String legacyPaddingThread = nativePadding
                ? ""
                : "LegacyGameTestPadding\\s*\\.\\s*register\\s*\\("
                        + "\\s*\\k<id>\\s*,\\s*padding\\s*\\)\\s*;\\s*";
        Matcher idThreading = Pattern.compile(
                        "(?<![A-Za-z0-9_$])"
                                + idType
                                + "\\s+(?<id>"
                                + javaName
                                + ")\\s*=\\s*"
                                + "modId\\s*\\(\\s*name\\s*\\)\\s*;\\s*"
                                + legacyPaddingThread
                                + "event\\s*\\.\\s*registerTest\\s*\\("
                                + "\\s*\\k<id>\\s*,\\s*new\\s+ConsumerGameTestInstance\\s*\\("
                                + "\\s*\\k<id>\\s*,\\s*info\\s*,\\s*body\\s*\\)\\s*\\)\\s*;")
                .matcher(registerPaddedSource);
        assertTrue(idThreading.find(),
                "FixRegression must thread the same node-native ID through padding, registration, and body dispatch");
        assertFalse(idThreading.find(),
                "FixRegression must contain exactly one node-native ID registration thread");
        assertEquals(
                nativePadding ? 0 : 1,
                SourceScan.countOccurrences(
                        registerPadded, "LegacyGameTestPadding.register("),
                "legacy nodes must register exactly one mandatory padding value");
    }

    private static String registrationCall(FixRegressionRegistration registration) {
        return registration.padded()
                ? "registerPadded(event,\"" + registration.id()
                        + "\",hardEnv,48,IAmZombieFixRegressionGameTestBodies::"
                        + registration.body() + ");"
                : "register(event,\"" + registration.id()
                        + "\",hardEnv,IAmZombieFixRegressionGameTestBodies::"
                        + registration.body() + ");";
    }

    private static String normalizedModIdSha256(Set<String> ids) {
        String normalized = ids.stream()
                .map(id -> "iamzombieq:" + id)
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private record FixRegressionRegistration(String id, String body, boolean padded) {
    }
}
