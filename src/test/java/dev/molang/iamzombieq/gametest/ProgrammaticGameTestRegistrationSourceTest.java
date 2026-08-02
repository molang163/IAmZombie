package dev.molang.iamzombieq.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProgrammaticGameTestRegistrationSourceTest {
    private static final Path INSTANCE = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/ConsumerGameTestInstance.java");
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieGameTestRegistry.java");
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
    private static final Pattern TEST_ID = Pattern.compile(
            "(?:register|registerPadded)\\(\\s*event,\\s*\"([^\"]+)\"");
    private static final Pattern FIX_REGRESSION_BODY = Pattern.compile(
            "\\bstatic\\s+void\\s+(\\w+)\\s*\\(\\s*GameTestHelper\\s+helper\\s*\\)");
    private static final Pattern STATIC_VOID_METHOD = Pattern.compile(
            "\\b(?:public\\s+|protected\\s+|private\\s+)?static\\s+void\\s+(\\w+)\\s*\\(");
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
        Matcher matcher = TEST_ID.matcher(allSuites);
        HashSet<String> ids = new HashSet<>();
        int registrations = 0;
        while (matcher.find()) {
            registrations++;
            ids.add(matcher.group(1));
        }

        assertEquals(84, registrations, "the mod contributes 84 tests; vanilla always_pass is the 85th");
        assertEquals(85, registrations + 1, "84 mod tests plus vanilla always_pass must remain 85 total");
        assertEquals(registrations, ids.size(), "programmatic GameTest IDs must remain unique");
        assertTrue(ids.contains("trade_undisguised_zombie_is_denied"),
                "the undisguised trade gate must remain registered under its exact ID");
        assertTrue(ids.contains("trade_disguised_zombie_opens_and_damages_mask"),
                "the disguised real-trade path must remain registered under its exact ID");
        assertTrue(ids.contains("herobrine_lethal_attack_respawns_in_place"),
                "the lethal Herobrine respawn path must remain registered under its exact ID");
        assertTrue(ids.contains("villager_fear_respects_disguise"),
                "the villager disguise fear path must remain registered under its exact ID");
        assertTrue(ids.contains("wandering_trader_fear_respects_disguise"),
                "the wandering-trader disguise fear path must remain registered under its exact ID");
        assertTrue(ids.contains("coffin_sleep_vote_advances_and_wakes_all"),
                "the coffin sleep vote must remain registered under its exact ID");
        assertTrue(ids.contains("coffin_sleep_timeout_wakes_without_skip"),
                "the coffin sleep timeout must remain registered under its exact ID");
        assertTrue(ids.contains("s1_transform_pre_giant_kill_veto"),
                "the S1 giant-kill Transform Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_giant_kill_pass"),
                "the S1 giant-kill Transform Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_clone_reset_veto_preserves_state"),
                "the S1 clone-reset Transform Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_transform_pre_clone_reset_pass"),
                "the S1 clone-reset Transform Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_drowning_veto_real_death"),
                "the S1 drowning Evolve Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_drowning_pass_once"),
                "the S1 drowning Evolve Pre pass path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_starvation_veto_real_death"),
                "the S1 starvation Evolve Pre veto path must retain its exact ID");
        assertTrue(ids.contains("s1_evolve_pre_starvation_pass"),
                "the S1 starvation Evolve Pre pass path must retain its exact ID");
        assertEquals(9, SourceScan.countOccurrences(
                allSuites, "new ConsumerGameTestInstance(id, info, body)"));
        assertEquals(9, SourceScan.countOccurrences(allSuites, "event.registerTest(id,"));
        assertEquals(9, SourceScan.countOccurrences(registry, ".registerAll("),
                "all nine GameTest suites must remain wired through the shared registry");
        assertFalse(allSuites.contains("new ConsumerGameTestInstance(info, body)"));
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
        String compactRegistration = SourceScan.compact(SourceScan.stripComments(registration));
        String compactBodies = SourceScan.compact(SourceScan.stripComments(bodies));
        HashSet<String> expectedBodies = new HashSet<>();
        for (FixRegressionRegistration expected : FIX_REGRESSION_REGISTRATIONS) {
            expectedBodies.add(expected.body());
            String call = expected.padded()
                    ? "registerPadded(event,\"" + expected.id()
                            + "\",hardEnv,48,IAmZombieFixRegressionGameTestBodies::"
                            + expected.body() + ");"
                    : "register(event,\"" + expected.id()
                            + "\",hardEnv,IAmZombieFixRegressionGameTestBodies::"
                            + expected.body() + ");";
            assertEquals(1, SourceScan.countOccurrences(compactRegistration, call),
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
        assertEquals(10, bodyCount, "FixRegression Bodies must expose exactly ten GameTest behavior methods");
        assertEquals(expectedBodies, actualBodies,
                "FixRegression Bodies must expose exactly the ten registered behavior methods");
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
        String registerPadded = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(registration, "private static void registerPadded(")));
        assertTrue(registerPadded.contains(
                        "newTestData<>(environment,modId(STRUCTURE),200,0,true,"
                                + "Rotation.NONE,false,1,1,false,padding)"),
                "FixRegression metadata must retain maxTicks=200, setupTicks=0, required=true, and padding");
        assertTrue(registerPadded.contains(
                        "Identifierid=modId(name);"
                                + "event.registerTest(id,newConsumerGameTestInstance(id,info,body));"),
                "FixRegression must retain the same Identifier for registration and body dispatch");
    }

    private record FixRegressionRegistration(String id, String body, boolean padded) {
    }
}
