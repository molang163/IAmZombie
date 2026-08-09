package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards collision-safe Stonecutter API boundaries while the canonical source remains authored for 26.2.
 */
class StonecutterApiBoundarySourceTest {
    private static final Path CONTROLLER = Path.of("stonecutter.gradle.kts");
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path TEST_JAVA = Path.of("src/test/java");
    private static final String ENTITY_PLURAL = "Entity" + "Types";
    private static final String ENTITY_SINGULAR = "Entity" + "Type";
    private static final String BLOCK_ENTITY_PLURAL = "BlockEntity" + "Types";
    private static final String BLOCK_ENTITY_SINGULAR = "BlockEntity" + "Type";

    @Test
    void resourceIdentifierBoundaryUsesOneReversibleTokenSwap() throws IOException {
        String controller = Files.readString(CONTROLLER);
        String upper = "Ident" + "ifier";
        String lower = "Resource" + "Location";
        String boundary = "regex(current.parsed < \"1.21.11\")";
        String replacement = "replace(\"\\\\b" + upper + "\\\\b\", \"" + lower
                + "\", \"\\\\b" + lower + "\\\\b\", \"" + upper + "\")";

        int boundaryIndex = controller.indexOf(boundary);
        int replacementIndex = controller.indexOf(replacement);
        int nextBoundaryIndex = controller.indexOf("current.parsed <", boundaryIndex + boundary.length());
        assertTrue(boundaryIndex >= 0, "the resource-name API boundary must be explicit");
        assertEquals(1, SourceScan.countOccurrences(controller, replacement),
                "the whole-word type rename must have exactly one explicitly reversible replacement");
        assertTrue(replacementIndex > boundaryIndex
                        && (nextBoundaryIndex < 0 || replacementIndex < nextBoundaryIndex),
                "the resource-name replacement must belong to the <1.21.11 boundary");
        Pattern substringReplacement = Pattern.compile(
                "replace\\(\\s*\"" + upper + "\"\\s*,\\s*\"" + lower + "\"\\s*\\)");
        assertFalse(substringReplacement.matcher(controller).find(),
                "a substring replacement would corrupt accessors such as getIdentifier()");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8")
                        .contains(executingNode),
                "unknown Stonecutter node: " + executingNode);
        boolean legacyName = Set.of("1.21.10", "1.21.8").contains(executingNode);
        int upperOccurrences = 0;
        int lowerOccurrences = 0;
        int corruptedAccessorOccurrences = 0;
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                String code = SourceScan.stripComments(source);
                upperOccurrences += Math.toIntExact(
                        Pattern.compile("\\b" + upper + "\\b").matcher(code).results().count());
                lowerOccurrences += Math.toIntExact(
                        Pattern.compile("\\b" + lower + "\\b").matcher(code).results().count());
                corruptedAccessorOccurrences +=
                        SourceScan.countOccurrences(code, "get" + lower + "(");
            }
        }
        assertTrue((legacyName ? lowerOccurrences : upperOccurrences) > 0,
                "active production source must contain the node's resource-name API");
        assertEquals(0, legacyName ? upperOccurrences : lowerOccurrences,
                "active production source must not mix resource-name APIs");
        assertEquals(0, corruptedAccessorOccurrences,
                "whole-word type replacement must never rewrite an accessor name");
    }

    @Test
    void resourceIdentifierAccessorsUseExplicitLocalSeams() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        boolean legacyName = Set.of("1.21.10", "1.21.8").contains(executingNode);

        assertVersionedAccessor(
                SourceScan.mainJava("dev/molang/iamzombieq/client/IAmZombieClient.java"),
                "public static void onPlaySound",
                ".getIdentifier()",
                ".getLocation()",
                legacyName);
        assertVersionedAccessor(
                SourceScan.mainJava("dev/molang/iamzombieq/IAmZombieItems.java"),
                "private static void addCreativeTabItems",
                "tab.identifier()",
                "tab.location()",
                legacyName);
        assertVersionedAccessor(
                SourceScan.mainJava("dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java"),
                "private static String damageTypeId",
                "key.identifier()",
                "key.location()",
                legacyName);
        assertVersionedAccessor(
                SourceScan.mainJava("dev/molang/iamzombieq/gameplay/CoffinNapManager.java"),
                "public static void onPlayerTick",
                "level.dimension().identifier()",
                "level.dimension().location()",
                legacyName);
    }

    private static void assertVersionedAccessor(
            String source,
            String methodSignature,
            String modernAccessor,
            String legacyAccessor,
            boolean legacyName) {
        String rawMethod = SourceScan.methodBody(source, methodSignature);
        assertTrue(rawMethod.contains("//? if >=1.21.11")
                        && rawMethod.contains("//?} else {"),
                methodSignature + " must retain an explicit <1.21.11 accessor seam");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, modernAccessor));
        assertEquals(1, SourceScan.countOccurrences(rawMethod, legacyAccessor));

        String activeMethod = SourceScan.stripComments(rawMethod);
        assertTrue(activeMethod.contains(legacyName ? legacyAccessor : modernAccessor),
                methodSignature + " must use the node's active accessor");
        assertFalse(activeMethod.contains(legacyName ? modernAccessor : legacyAccessor),
                methodSignature + " must not mix resource accessors");
        assertFalse(activeMethod.contains("getResourceLocation("),
                "whole-word type replacement must never rewrite an accessor name");
    }

    @Test
    void nullableAnnotationBoundaryUsesNodeNativeClasspath() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "unknown Stonecutter node: " + executingNode);
        boolean jspecifyNode = Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        String jspecifyImport = "import org.jspecify.annotations.Nullable;";
        String jetbrainsImport = "import org.jetbrains.annotations.Nullable;";
        Set<Path> expectedBridges = Set.of(
                Path.of("dev/molang/iamzombieq/block/CoffinBlock.java"),
                Path.of("dev/molang/iamzombieq/entity/HerobrineEntity.java"),
                Path.of("dev/molang/iamzombieq/mixin/ItemStackMixin.java"));
        Set<Path> permanentJetbrainsImports = Set.of(
                Path.of("dev/molang/iamzombieq/api/extension/IFoodRuleProvider.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java"));
        Set<Path> observedBridges = new HashSet<>();
        Set<Path> activeJspecifyImports = new HashSet<>();
        Set<Path> activeJetbrainsImports = new HashSet<>();

        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                Path relative = MAIN_JAVA.relativize(path);
                if (source.contains(jspecifyImport)) {
                    observedBridges.add(relative);
                }
                String activeSource = SourceScan.stripComments(source);
                if (activeSource.contains(jspecifyImport)) {
                    activeJspecifyImports.add(relative);
                }
                if (activeSource.contains(jetbrainsImport)) {
                    activeJetbrainsImports.add(relative);
                }
            }
        }
        assertEquals(expectedBridges, observedBridges,
                "every jspecify production import must participate in the exact legacy bridge");
        assertEquals(jspecifyNode ? expectedBridges : Set.of(), activeJspecifyImports);
        Set<Path> expectedActiveJetbrainsImports = new HashSet<>(permanentJetbrainsImports);
        if (!jspecifyNode) {
            expectedActiveJetbrainsImports.addAll(expectedBridges);
        }
        assertEquals(expectedActiveJetbrainsImports, activeJetbrainsImports,
                "active JetBrains nullable imports must equal the permanent plus legacy surfaces");

        for (Path relative : expectedBridges) {
            String rawSource = Files.readString(MAIN_JAVA.resolve(relative));
            assertEquals(1, SourceScan.countOccurrences(rawSource, jspecifyImport));
            assertEquals(1, SourceScan.countOccurrences(rawSource, jetbrainsImport));
            String expectedSeam = jspecifyNode
                    ? "//? if >=1.21.11 {\n" + jspecifyImport
                            + "\n//?} else {\n/*" + jetbrainsImport + "\n*///?}"
                    : "//? if >=1.21.11 {\n/*" + jspecifyImport
                            + "\n*///?} else {\n" + jetbrainsImport + "\n//?}";
            assertEquals(1, SourceScan.countOccurrences(rawSource, expectedSeam),
                    relative + " must retain one contiguous nullable-annotation seam");
            assertEquals(0, SourceScan.countOccurrences(rawSource, "javax.annotation.Nullable"),
                    relative + " must use an annotation already supplied by the node classpath");

            String activeSource = SourceScan.stripComments(rawSource);
            assertEquals(jspecifyNode ? 1 : 0,
                    SourceScan.countOccurrences(activeSource, jspecifyImport));
            assertEquals(jspecifyNode ? 0 : 1,
                    SourceScan.countOccurrences(activeSource, jetbrainsImport));
        }

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("jspecify.annotations.Nullable")
                        || controller.contains("jetbrains.annotations.Nullable"),
                "nullable annotation imports must remain local seams, never global replacements");
    }

    @Test
    void entityPackageBoundaryUsesOnlyWholeClassTokens() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "unknown Stonecutter node: " + executingNode);
        boolean modernNode = Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        String entityRoot = "net.minecraft.world.entity.";
        record PackageMove(String modernTail, String legacyTail, int occurrences) {}
        List<PackageMove> moves = List.of(
                new PackageMove("animal.chicken." + "Chicken", "animal." + "Chicken", 3),
                new PackageMove("animal.equine." + "AbstractHorse", "animal.horse." + "AbstractHorse", 2),
                new PackageMove("animal.equine." + "Horse", "animal.horse." + "Horse", 4),
                new PackageMove("animal.equine." + "SkeletonHorse", "animal.horse." + "SkeletonHorse", 2),
                new PackageMove("animal.equine." + "TraderLlama", "animal.horse." + "TraderLlama", 2),
                new PackageMove("animal.equine." + "ZombieHorse", "animal.horse." + "ZombieHorse", 4),
                new PackageMove("animal.golem." + "IronGolem", "animal." + "IronGolem", 4),
                new PackageMove("animal.golem." + "SnowGolem", "animal." + "SnowGolem", 2),
                new PackageMove("animal.pig." + "Pig", "animal." + "Pig", 3),
                new PackageMove("animal.polarbear." + "PolarBear", "animal." + "PolarBear", 1),
                new PackageMove("monster.skeleton." + "Skeleton", "monster." + "Skeleton", 1),
                new PackageMove("monster.spider." + "Spider", "monster." + "Spider", 11),
                new PackageMove("monster.zombie." + "Drowned", "monster." + "Drowned", 2),
                new PackageMove("monster.zombie." + "Zombie", "monster." + "Zombie", 11),
                new PackageMove("monster.zombie." + "ZombieVillager", "monster." + "ZombieVillager", 4),
                new PackageMove("monster.zombie." + "ZombifiedPiglin", "monster." + "ZombifiedPiglin", 5),
                new PackageMove("npc.villager." + "AbstractVillager", "npc." + "AbstractVillager", 2),
                new PackageMove("npc.villager." + "Villager", "npc." + "Villager", 3),
                new PackageMove("npc.wanderingtrader." + "WanderingTrader", "npc." + "WanderingTrader", 2));
        Set<Path> expectedFiles = Set.of(
                Path.of("dev/molang/iamzombieq/block/CoffinBlock.java"),
                Path.of("dev/molang/iamzombieq/client/ZombiePlayerShapeEntities.java"),
                Path.of("dev/molang/iamzombieq/gameplay/GiantPlayerEvents.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieMobTargetingAdapter.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieMountEvents.java"),
                Path.of("dev/molang/iamzombieq/gameplay/ZombieReinforcementEvents.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieDisguiseGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieFoodInfGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/IAmZombieMountGameTestBodies.java"),
                Path.of("dev/molang/iamzombieq/gametest/MountedZombieKillCreditGameTest.java"),
                Path.of("dev/molang/iamzombieq/internal/mount/SpiderVehicleAuthoritySession.java"),
                Path.of("dev/molang/iamzombieq/internal/mount/SpiderVehicleMovementContext.java"),
                Path.of("dev/molang/iamzombieq/mixin/EntitySpiderPassengerRestorationMixin.java"),
                Path.of("dev/molang/iamzombieq/mixin/ServerEntitySpiderImpulseMixin.java"),
                Path.of("dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java"),
                Path.of("dev/molang/iamzombieq/mixin/ServerPlayerSpiderPassengerPacketMixin.java"),
                Path.of("dev/molang/iamzombieq/mixin/SpiderMixin.java"),
                Path.of("dev/molang/iamzombieq/util/MountCapability.java"),
                Path.of("dev/molang/iamzombieq/util/RideHelper.java"));
        List<Path> productionFiles;
        try (var paths = Files.walk(MAIN_JAVA)) {
            productionFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        String controller = Files.readString(CONTROLLER);
        String configuredPatternPrefix = "\\\\bnet\\\\.minecraft\\\\.world\\\\.entity\\\\.";
        assertEquals(moves.size() * 2,
                SourceScan.countOccurrences(controller, configuredPatternPrefix),
                "controller entity-package rules must equal the complete class-token map");
        Set<Path> observedFiles = new HashSet<>();
        int observedActiveOccurrences = 0;
        for (PackageMove move : moves) {
            String modern = entityRoot + move.modernTail();
            String legacy = entityRoot + move.legacyTail();
            String directPattern = controllerWholeTokenPattern(modern);
            String reversePattern = controllerWholeTokenPattern(legacy);
            Pattern declaration = Pattern.compile(
                    "replace\\(\\s*\"" + Pattern.quote(directPattern)
                            + "\"\\s*,\\s*\"" + Pattern.quote(legacy)
                            + "\"\\s*,\\s*\"" + Pattern.quote(reversePattern)
                            + "\"\\s*,\\s*\"" + Pattern.quote(modern) + "\"\\s*\\)");
            assertEquals(1, declaration.matcher(controller).results().count(),
                    "missing or duplicate entity-package rule for " + modern);
            String simpleName = modern.substring(modern.lastIndexOf('.') + 1);
            assertFalse(controller.contains("\"\\\\b" + simpleName + "\\\\b\""),
                    "bare class replacement is forbidden for " + simpleName);

            Pattern modernToken = Pattern.compile(
                    "(?<![A-Za-z0-9_$.])" + Pattern.quote(modern) + "(?![A-Za-z0-9_$])");
            Pattern legacyToken = Pattern.compile(
                    "(?<![A-Za-z0-9_$.])" + Pattern.quote(legacy) + "(?![A-Za-z0-9_$])");
            int modernOccurrences = 0;
            int legacyOccurrences = 0;
            for (Path path : productionFiles) {
                String source = Files.readString(path);
                int modernInFile = Math.toIntExact(modernToken.matcher(source).results().count());
                int legacyInFile = Math.toIntExact(legacyToken.matcher(source).results().count());
                modernOccurrences += modernInFile;
                legacyOccurrences += legacyInFile;
                if ((modernNode ? modernInFile : legacyInFile) > 0) {
                    observedFiles.add(MAIN_JAVA.relativize(path));
                }
            }
            assertEquals(modernNode ? move.occurrences() : 0, modernOccurrences,
                    "wrong modern occurrence count for " + modern);
            assertEquals(modernNode ? 0 : move.occurrences(), legacyOccurrences,
                    "wrong legacy occurrence count for " + legacy);
            observedActiveOccurrences += modernNode ? modernOccurrences : legacyOccurrences;
        }
        assertEquals(68, observedActiveOccurrences);
        assertEquals(expectedFiles, observedFiles,
                "entity package mapping must cover the exact production surface");
        assertFalse(controller.contains("animal\\\\.nautilus"),
                "Nautilus absence is not an entity-package rename");
    }

    private static String controllerWholeTokenPattern(String fqcn) {
        return "\\\\b" + fqcn.replace(".", "\\\\.") + "\\\\b";
    }

    @Test
    void gameRulesBoundaryUsesOnlyNodeNativeTypedApis() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> knownNodes =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8");
        assertTrue(executingNode != null && knownNodes.contains(executingNode),
                "unknown Stonecutter node: " + executingNode);
        boolean legacy10 = executingNode.equals("1.21.10");
        boolean legacy8 = executingNode.equals("1.21.8");
        boolean modern = !legacy10 && !legacy8;

        String rules = "Game" + "Rules";
        String pvpAllowed = "isPvp" + "Allowed";
        String setPvpAllowed = "setPvp" + "Allowed";
        String levelRules = "level.get" + rules + "()";
        String server = "level.getServer()";
        String serverRules = server + ".get" + rules + "()";
        String sleepModern = rules + ".PLAYERS_" + "SLEEPING_PERCENTAGE";
        String sleepLegacy = rules + ".RULE_PLAYERS_" + "SLEEPING_PERCENTAGE";
        String timeModern = rules + ".ADVANCE_" + "TIME";
        String timeLegacy = rules + ".RULE_" + "DAYLIGHT";
        String weatherModern = rules + ".ADVANCE_" + "WEATHER";
        String weatherLegacy = rules + ".RULE_WEATHER_" + "CYCLE";
        String pvpModern = rules + ".P" + "VP";
        String pvpLegacy = rules + ".RULE_" + "PVP";

        record ApiFamily(
                String name,
                Pattern modernPattern,
                Pattern legacy10Pattern,
                Pattern legacy8Pattern,
                int occurrences) {}
        List<ApiFamily> families = List.of(
                new ApiFamily(
                        "sleeping-percentage getter",
                        Pattern.compile(Pattern.quote(levelRules + ".get(" + sleepModern + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getInt(" + sleepLegacy + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getInt(" + sleepLegacy + ")")),
                        4),
                new ApiFamily(
                        "advance-time getter",
                        Pattern.compile(Pattern.quote(levelRules + ".get(" + timeModern + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getBoolean(" + timeLegacy + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getBoolean(" + timeLegacy + ")")),
                        4),
                new ApiFamily(
                        "advance-weather getter",
                        Pattern.compile(Pattern.quote(levelRules + ".get(" + weatherModern + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getBoolean(" + weatherLegacy + ")")),
                        Pattern.compile(Pattern.quote(levelRules + ".getBoolean(" + weatherLegacy + ")")),
                        1),
                new ApiFamily(
                        "PVP getter",
                        Pattern.compile(Pattern.quote(levelRules + ".get(" + pvpModern + ")")),
                        Pattern.compile(Pattern.quote(server + "." + pvpAllowed + "()")),
                        Pattern.compile(Pattern.quote(server + "." + pvpAllowed + "()")),
                        2),
                new ApiFamily(
                        "sleeping-percentage setter",
                        exactAlternation(
                                levelRules + ".set(" + sleepModern + ",",
                                "100|sleepingPercentage",
                                "," + server + ")"),
                        exactAlternation(
                                levelRules + ".getRule(" + sleepLegacy + ").set(",
                                "100|sleepingPercentage",
                                "," + server + ")"),
                        exactAlternation(
                                levelRules + ".getRule(" + sleepLegacy + ").set(",
                                "100|sleepingPercentage",
                                "," + server + ")"),
                        2),
                new ApiFamily(
                        "advance-time setter",
                        exactAlternation(
                                levelRules + ".set(" + timeModern + ",",
                                "true|advanceTime",
                                "," + server + ")"),
                        exactAlternation(
                                levelRules + ".getRule(" + timeLegacy + ").set(",
                                "true|advanceTime",
                                "," + server + ")"),
                        exactAlternation(
                                levelRules + ".getRule(" + timeLegacy + ").set(",
                                "true|advanceTime",
                                "," + server + ")"),
                        2));

        List<Path> productionFiles;
        try (var paths = Files.walk(MAIN_JAVA)) {
            productionFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        Map<Path, String> compactSources = new HashMap<>();
        for (Path path : productionFiles) {
            compactSources.put(path, SourceScan.compact(SourceScan.stripComments(Files.readString(path))));
        }

        Map<Path, Integer> activeCallsByFile = new HashMap<>();
        int totalActiveCalls = 0;
        for (ApiFamily family : families) {
            int modernOccurrences = 0;
            int legacy10Occurrences = 0;
            int legacy8Occurrences = 0;
            int activeOccurrences = 0;
            boolean sharedLegacyPattern =
                    family.legacy10Pattern().pattern().equals(family.legacy8Pattern().pattern());
            for (Map.Entry<Path, String> source : compactSources.entrySet()) {
                int modernInFile = countMatches(family.modernPattern(), source.getValue());
                int legacy10InFile = countMatches(family.legacy10Pattern(), source.getValue());
                int legacy8InFile = countMatches(family.legacy8Pattern(), source.getValue());
                modernOccurrences += modernInFile;
                legacy10Occurrences += legacy10InFile;
                legacy8Occurrences += legacy8InFile;
                int activeInFile = modern
                        ? modernInFile
                        : legacy10 ? legacy10InFile : legacy8InFile;
                activeOccurrences += activeInFile;
                if (activeInFile > 0) {
                    activeCallsByFile.merge(
                            MAIN_JAVA.relativize(source.getKey()), activeInFile, Integer::sum);
                }
            }
            assertEquals(modern ? family.occurrences() : 0, modernOccurrences,
                    "wrong modern occurrence count for " + family.name());
            if (legacy10) {
                assertEquals(family.occurrences(), legacy10Occurrences,
                        "wrong 1.21.10 occurrence count for " + family.name());
                if (!sharedLegacyPattern) {
                    assertEquals(0, legacy8Occurrences,
                            "1.21.10 must not retain the 1.21.8 form for " + family.name());
                }
            } else if (legacy8) {
                assertEquals(family.occurrences(), legacy8Occurrences,
                        "wrong 1.21.8 occurrence count for " + family.name());
                if (!sharedLegacyPattern) {
                    assertEquals(0, legacy10Occurrences,
                            "1.21.8 must not retain the 1.21.10 form for " + family.name());
                }
            } else {
                assertEquals(0, legacy10Occurrences,
                        "modern nodes must not retain the legacy form for " + family.name());
                if (!sharedLegacyPattern) {
                    assertEquals(0, legacy8Occurrences,
                            "modern nodes must not retain the 1.21.8 form for " + family.name());
                }
            }
            totalActiveCalls += activeOccurrences;
        }
        assertEquals(15, totalActiveCalls);
        assertEquals(Map.of(
                        Path.of("dev/molang/iamzombieq/gameplay/CoffinNapManager.java"), 3,
                        Path.of("dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTestBodies.java"), 10,
                        Path.of("dev/molang/iamzombieq/gametest/MountedZombieKillCreditGameTest.java"), 2),
                activeCallsByFile,
                "the typed gamerule bridge must cover the exact production and required-GameTest surface");

        String modernImport = "import" + "net.minecraft.world.level.gamerules." + rules + ";";
        String legacyImport = "import" + "net.minecraft.world.level." + rules + ";";
        int modernImports = 0;
        int legacyImports = 0;
        for (String source : compactSources.values()) {
            modernImports += SourceScan.countOccurrences(source, modernImport);
            legacyImports += SourceScan.countOccurrences(source, legacyImport);
        }
        assertEquals(modern ? 3 : 0, modernImports);
        assertEquals(modern ? 0 : legacy8 ? 4 : 3, legacyImports);

        Pattern rulesToken = Pattern.compile("\\b" + Pattern.quote(rules) + "\\b");
        Pattern pvpAllowedToken = Pattern.compile("\\b" + Pattern.quote(pvpAllowed) + "\\b");
        Pattern setPvpAllowedToken = Pattern.compile("\\b" + Pattern.quote(setPvpAllowed) + "\\b");
        int rulesTokens = 0;
        int pvpAllowedTokens = 0;
        int setPvpAllowedTokens = 0;
        for (String source : compactSources.values()) {
            rulesTokens += countMatches(rulesToken, source);
            pvpAllowedTokens += countMatches(pvpAllowedToken, source);
            setPvpAllowedTokens += countMatches(setPvpAllowedToken, source);
        }
        assertEquals(modern ? 19 : legacy10 ? 17 : 18, rulesTokens,
                "the complete typed gamerule token surface must stay classified");
        assertEquals(modern ? 1 : 3, pvpAllowedTokens,
                "the complete PVP getter/accessor surface must stay classified");
        assertEquals(legacy8 ? 1 : 0, setPvpAllowedTokens,
                "only the 1.21.8 local helper may use the server PVP setter field API");

        Path mountedPath = MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/gametest/MountedZombieKillCreditGameTest.java");
        String rawMounted = Files.readString(mountedPath);
        String mounted = compactSources.get(mountedPath);
        String modernAccessor = "level()." + pvpAllowed + "()";
        String legacyAccessor = "level().getServer()." + pvpAllowed + "()";
        assertEquals(modern ? 1 : 0, SourceScan.countOccurrences(mounted, modernAccessor));
        assertEquals(modern ? 0 : 1, SourceScan.countOccurrences(mounted, legacyAccessor));
        assertEquals(5, SourceScan.countOccurrences(rawMounted, "setPvp(level,"),
                "all fixture PVP mutations must route through the local typed helper");
        String rawSetPvp = SourceScan.methodBody(
                rawMounted, "private static void setPvp(ServerLevel level, boolean enabled)");
        String activeSetPvp = SourceScan.compact(SourceScan.stripComments(rawSetPvp));
        String modernPvpSet = levelRules + ".set(" + pvpModern + ",enabled," + server + ")";
        String legacy10PvpSet = serverRules + ".getRule(" + pvpLegacy + ").set(enabled," + server + ")";
        String legacy8PvpSet = server + "." + setPvpAllowed + "(enabled)";
        assertEquals(modern ? 1 : 0, SourceScan.countOccurrences(activeSetPvp, modernPvpSet));
        assertEquals(legacy10 ? 1 : 0, SourceScan.countOccurrences(activeSetPvp, legacy10PvpSet));
        assertEquals(legacy8 ? 1 : 0, SourceScan.countOccurrences(activeSetPvp, legacy8PvpSet));
        assertEquals(1, SourceScan.countOccurrences(rawSetPvp, "//? if >=1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(rawSetPvp, "//? if >=1.21.10 && <1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(rawSetPvp, "//? if <1.21.10 {"));
        assertFalse(rawSetPvp.contains("reflect") || rawSetPvp.contains("@SuppressWarnings"),
                "the local PVP helper must remain fully typed");

        String controller = Files.readString(CONTROLLER);
        String marker = "// 1.21.11 moved " + rules + " and introduced the generic get/set API.";
        String nextMarker = "// 1.21.11 reorganized entity classes into narrower packages.";
        int start = controller.indexOf(marker);
        int end = controller.indexOf(nextMarker, Math.max(0, start));
        assertTrue(start >= 0 && end > start,
                "the typed gamerule controller section must be present and locally bounded");
        String section = controller.substring(start, end);
        String outside = controller.substring(0, start) + controller.substring(end);
        String sectionCode = SourceScan.stripComments(section);
        String outsideCode = SourceScan.stripComments(outside);
        assertEquals(1, SourceScan.countOccurrences(sectionCode, "regex("));
        assertEquals(0, SourceScan.countOccurrences(sectionCode, "string("),
                "typed gamerule calls must use explicitly reversible regex rules");
        assertEquals(8, SourceScan.countOccurrences(sectionCode, "replace("),
                "the six shared typed families, import, and PVP accessor require eight rules");
        assertEquals(1, SourceScan.countOccurrences(sectionCode,
                "regex(current.parsed < \"1.21.11\")"));
        assertEquals(0, SourceScan.countOccurrences(sectionCode,
                "regex(current.parsed >= \"1.21.10\" && current.parsed < \"1.21.11\")"));
        assertEquals(0, SourceScan.countOccurrences(sectionCode,
                "regex(current.parsed < \"1.21.10\")"));
        assertFalse(outsideCode.contains(rules)
                        || outsideCode.contains(pvpAllowed)
                        || outsideCode.contains(setPvpAllowed),
                "all global gamerule adaptations must stay inside the audited section");

        String dollarOne = "${'$'}1";
        String dollarTwo = "${'$'}2";
        String modernRulesClass = "net.minecraft.world.level.gamerules." + rules;
        String legacyRulesClass = "net.minecraft.world.level." + rules;
        String modernSleepGet = levelRules + ".get(" + sleepModern + ")";
        String legacySleepGet = levelRules + ".getInt(" + sleepLegacy + ")";
        String modernTimeGet = levelRules + ".get(" + timeModern + ")";
        String legacyTimeGet = levelRules + ".getBoolean(" + timeLegacy + ")";
        String modernWeatherGet = levelRules + ".get(" + weatherModern + ")";
        String legacyWeatherGet = levelRules + ".getBoolean(" + weatherLegacy + ")";
        String modernPvpGet = levelRules + ".get(" + pvpModern + ")";
        String legacyPvpGet = server + "." + pvpAllowed + "()";
        String modernPvpAccessor = "level()." + pvpAllowed + "()";
        String legacyPvpAccessor = "level().getServer()." + pvpAllowed + "()";

        String modernSleepSetPattern = controllerCallPattern(levelRules + ".set(")
                + "(\\\\s*)" + controllerRegexEscape(sleepModern + ", ")
                + "(100|sleepingPercentage)" + controllerRegexEscape(", " + server + ")");
        String legacySleepSet = levelRules + ".getRule(" + sleepLegacy + ").set(";
        String legacySleepSetPattern = controllerCallPattern(legacySleepSet)
                + "(\\\\s*)(100|sleepingPercentage)"
                + controllerRegexEscape(", " + server + ")");
        String legacySleepSetTarget = legacySleepSet + dollarOne + dollarTwo + ", " + server + ")";
        String modernSleepSetTarget = levelRules + ".set(" + dollarOne + sleepModern + ", "
                + dollarTwo + ", " + server + ")";

        String modernTimeSetPattern = controllerCallPattern(levelRules + ".set(")
                + "(\\\\s*)" + controllerRegexEscape(timeModern + ", ")
                + "(true|advanceTime)" + controllerRegexEscape(", " + server + ")");
        String legacyTimeSet = levelRules + ".getRule(" + timeLegacy + ").set(";
        String legacyTimeSetPattern = controllerCallPattern(legacyTimeSet)
                + "(\\\\s*)(true|advanceTime)"
                + controllerRegexEscape(", " + server + ")");
        String legacyTimeSetTarget = legacyTimeSet + dollarOne + dollarTwo + ", " + server + ")";
        String modernTimeSetTarget = levelRules + ".set(" + dollarOne + timeModern + ", "
                + dollarTwo + ", " + server + ")";

        String commonAnchor = "regex(current.parsed < \"1.21.11\")";
        List<List<String>> expectedCommon = List.of(
                List.of(
                        controllerWholeTokenPattern(modernRulesClass),
                        legacyRulesClass,
                        controllerWholeTokenPattern(legacyRulesClass),
                        modernRulesClass),
                List.of(
                        controllerCallPattern(modernSleepGet),
                        legacySleepGet,
                        controllerCallPattern(legacySleepGet),
                        modernSleepGet),
                List.of(
                        controllerCallPattern(modernTimeGet),
                        legacyTimeGet,
                        controllerCallPattern(legacyTimeGet),
                        modernTimeGet),
                List.of(
                        controllerCallPattern(modernWeatherGet),
                        legacyWeatherGet,
                        controllerCallPattern(legacyWeatherGet),
                        modernWeatherGet),
                List.of(
                        controllerCallPattern(modernPvpGet),
                        legacyPvpGet,
                        controllerCallPattern(legacyPvpGet),
                        modernPvpGet),
                List.of(
                        controllerCallPattern(modernPvpAccessor),
                        legacyPvpAccessor,
                        controllerCallPattern(legacyPvpAccessor),
                        modernPvpAccessor),
                List.of(
                        modernSleepSetPattern,
                        legacySleepSetTarget,
                        legacySleepSetPattern,
                        modernSleepSetTarget),
                List.of(
                        modernTimeSetPattern,
                        legacyTimeSetTarget,
                        legacyTimeSetPattern,
                        modernTimeSetTarget));
        assertControllerRules(
                SourceScan.blockBody(sectionCode, commonAnchor), expectedCommon, "common <1.21.11 rules");
        assertFalse(sectionCode.contains(setPvpAllowed)
                        || sectionCode.contains(pvpLegacy + ").set")
                        || sectionCode.contains(".set(" + pvpModern + ","),
                "PVP setters must use the local three-node helper, never a global replacement");
        assertFalse(sectionCode.contains("required=0")
                        || sectionCode.contains("@SuppressWarnings")
                        || sectionCode.contains("unchecked"),
                "the boundary must remain strict and fully typed");
    }

    private static Pattern exactAlternation(String prefix, String alternatives, String suffix) {
        return Pattern.compile(Pattern.quote(prefix) + "(?:" + alternatives + ")" + Pattern.quote(suffix));
    }

    private static String controllerCallPattern(String call) {
        return "\\\\b" + controllerRegexEscape(call);
    }

    private static String controllerRegexEscape(String literal) {
        if (literal.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("controller regex literal must not contain a backslash");
        }
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char character = literal.charAt(i);
            if ("\\\\.^$|?*+()[]{}".indexOf(character) >= 0) {
                escaped.append("\\\\");
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static void assertControllerRules(
            String block, List<List<String>> expected, String boundary) {
        Pattern declaration = Pattern.compile(
                "replace\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*"
                        + "\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*"
                        + "\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*"
                        + "\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)",
                Pattern.DOTALL);
        List<List<String>> observed = new ArrayList<>();
        var matcher = declaration.matcher(block);
        while (matcher.find()) {
            observed.add(List.of(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
        }
        assertEquals(expected.size(), observed.size(), boundary + " declaration count");
        assertEquals(Set.copyOf(expected), Set.copyOf(observed),
                boundary + " must contain only the exact direct/reverse declarations");
    }

    @Test
    void constantsHolderBoundaryUsesOnlyCompleteTokens() throws IOException {
        String controller = Files.readString(CONTROLLER);
        List<String> constants = List.of(
                "AXOLOTL",
                "CHICKEN",
                "DROWNED",
                "GIANT",
                "HORSE",
                "HUSK",
                "IRON_GOLEM",
                "ITEM",
                "NAUTILUS",
                "PIG",
                "SKELETON_HORSE",
                "SKELETON",
                "SPIDER",
                "TRADER_LLAMA",
                "VILLAGER",
                "WANDERING_TRADER",
                "ZOMBIE_HORSE",
                "ZOMBIE_NAUTILUS",
                "ZOMBIE_VILLAGER",
                "ZOMBIFIED_PIGLIN",
                "ZOMBIE");
        Set<String> expectedConstants = Set.copyOf(constants);

        assertTrue(controller.contains("string(current.parsed < \"26.2\")"));
        for (String constant : constants) {
            String replacement = "replace(\"" + ENTITY_PLURAL + "." + constant
                    + "\", \"" + ENTITY_SINGULAR + "." + constant + "\")";
            assertEquals(1, SourceScan.countOccurrences(controller, replacement),
                    "missing or duplicate exact constants-holder replacement for " + constant);
        }
        String blockReplacement = "replace(\"" + BLOCK_ENTITY_PLURAL + ".SKULL\", \""
                + BLOCK_ENTITY_SINGULAR + ".SKULL\")";
        assertEquals(1, SourceScan.countOccurrences(controller, blockReplacement));
        assertFalse(controller.contains(
                "replace(\"" + ENTITY_PLURAL + ".\", \"" + ENTITY_SINGULAR + ".\")"),
                "a broad dotted replacement would corrupt legitimate singular EntityType APIs on round-trip");
        Pattern configuredReplacement = Pattern.compile(
                "replace\\(\\\"" + ENTITY_PLURAL + "\\.([A-Z][A-Z0-9_]*)\\\", \\\""
                        + ENTITY_SINGULAR + "\\.[A-Z][A-Z0-9_]*\\\"\\)");
        Set<String> configuredConstants = new HashSet<>();
        configuredReplacement.matcher(controller).results()
                .forEach(result -> configuredConstants.add(result.group(1)));
        assertEquals(expectedConstants, configuredConstants,
                "controller replacements must equal the production constants-holder surface");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        String activeEntityHolder = executingNode.equals("26.2.x") ? ENTITY_PLURAL : ENTITY_SINGULAR;
        Pattern productionConstant =
                Pattern.compile("\\b" + activeEntityHolder + "\\.([A-Z][A-Z0-9_]*)");
        Set<String> productionConstants = new HashSet<>();
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                productionConstant.matcher(SourceScan.stripComments(Files.readString(path))).results()
                        .forEach(result -> productionConstants.add(result.group(1)));
            }
        }
        Set<String> expectedActiveConstants = new HashSet<>(expectedConstants);
        if (!StonecutterCapabilityMatrix.hasNautilusEntityApi()) {
            expectedActiveConstants.removeAll(Set.of("NAUTILUS", "ZOMBIE_NAUTILUS"));
        }
        assertEquals(expectedActiveConstants, productionConstants,
                "active production constants must equal the full controller authority minus exact platform N/A");

        String targeting = Files.readString(
                Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMobTargetingAdapter.java"));
        assertTrue(targeting.contains(ENTITY_SINGULAR + ".getKey("));
        assertFalse(targeting.contains(ENTITY_PLURAL + ".getKey("));
    }

    @Test
    void goldenSpearEquipmentPoolUsesExactNodeBoundary() throws IOException {
        String source = Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTestBodies.java"));
        String rawMethod = SourceScan.methodBody(source, "static void piglinConversionNotBabyAndArmed");
        String activeMethod = SourceScan.stripComments(rawMethod);
        String marker = "// CROSS_VERSION-GOLDEN-SPEAR-EQUIPMENT-POOL:high-node-vanilla-roll";
        String swordCheck = "mainhand.is(Items.GOLDEN_SWORD)";
        String spearCheck = "mainhand.is(Items.GOLDEN_SPEAR)";
        boolean hasGoldenSpear =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(StonecutterCapabilityMatrix.nodeId());
        String spearAssignment =
                "hasNodeNativeGoldenWeapon = hasNodeNativeGoldenWeapon || " + spearCheck + ";";
        String activeNodeSeam = marker + "\n"
                + "                    //? if >=1.21.11\n"
                + "                    " + (hasGoldenSpear ? "" : "//") + spearAssignment;

        assertEquals(1, SourceScan.countOccurrences(rawMethod, marker),
                "the golden-spear equipment-pool boundary must have one local marker");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//? if >=1.21.11"),
                "the golden-spear equipment pool must use the exact version boundary");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, activeNodeSeam),
                "the node source must retain the exact active or commented spear assignment");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, swordCheck),
                "every node must strictly accept the node-native GOLDEN_SWORD");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, spearCheck),
                "node source must retain exactly one Stonecutter GOLDEN_SPEAR token");
        assertEquals(1, SourceScan.countOccurrences(activeMethod, swordCheck));
        assertEquals(hasGoldenSpear ? 1 : 0, SourceScan.countOccurrences(activeMethod, spearCheck),
                "active production source must match the node-native zombified-piglin equipment pool");
        assertEquals(1, SourceScan.countOccurrences(activeMethod,
                "ItemStack mainhand = piglin.getItemBySlot(EquipmentSlot.MAINHAND);"),
                "the assertion must read the real converted entity equipment slot");
        assertEquals(1, SourceScan.countOccurrences(activeMethod,
                "boolean hasNodeNativeGoldenWeapon = " + swordCheck + ";"),
                "the node-native predicate must derive from the real mainhand stack");
        assertTrue(activeMethod.contains("GameTestSeams.killByPlayerAttack(level, player, pig);"),
                "the required test must still exercise the real infection path");
        assertTrue(activeMethod.contains("if (piglin.isBaby())"),
                "the adjacent adult-state regression assertion must remain");
        String failureBlock = SourceScan.blockBody(activeMethod, "if (!hasNodeNativeGoldenWeapon)");
        assertEquals(1, SourceScan.countOccurrences(
                        failureBlock, "GameTestAssertions.fail(helper,"),
                "the equipment assertion must fail closed");
        assertEquals(0, SourceScan.countOccurrences(activeMethod, "helper.succeed("),
                "the required test must not become a no-op success");

        String registration = Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTests.java"));
        String activeRegistration =
                SourceScan.stripComments(SourceScan.methodBody(registration, "static void registerAll"));
        String requiredRegistration =
                "register(event, \"reg_piglin_conversion_not_baby_and_armed\", hardEnv,\n"
                        + "                IAmZombieFixRegressionGameTestBodies::piglinConversionNotBabyAndArmed);";
        assertEquals(1, SourceScan.countOccurrences(activeRegistration, requiredRegistration),
                "the same required GameTest must remain registered on every node");
        assertFalse(StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS
                        .contains("reg_piglin_conversion_not_baby_and_armed"),
                "the golden-weapon equipment pool is not a platform-absent GameTest");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("GOLDEN_SPEAR"),
                "the local typed seam must never become a controller-wide token replacement");
    }

    @Test
    void everyPluralHolderImportHasAnExplicitVersionGuard() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        boolean upperHolderApi = executingNode.equals("26.2.x");
        int entityImports = 0;
        int permanentSingularImports = 0;
        int blockImports = 0;
        Set<String> permanentSingularFiles =
                Set.of("ZombieInfectionEvents.java", "ZombieReinforcementEvents.java");

        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(path);
                String source = String.join("\n", lines);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    String pluralEntityImport =
                            "import net.minecraft.world.entity." + ENTITY_PLURAL + ";";
                    if (line.equals(pluralEntityImport) || line.equals("//" + pluralEntityImport)) {
                        entityImports++;
                        assertTrue(index > 0 && lines.get(index - 1).equals("//? if >=26.2"),
                                "plural entity holder import must be guarded in " + path);
                        assertEquals(upperHolderApi ? pluralEntityImport : "//" + pluralEntityImport, line,
                                "plural entity holder import has the wrong active form in " + path);
                        if (permanentSingularFiles.contains(path.getFileName().toString())) {
                            permanentSingularImports++;
                            assertTrue(source.contains(
                                    "import net.minecraft.world.entity." + ENTITY_SINGULAR + ";"));
                        } else {
                            String singularEntityImport =
                                    "import net.minecraft.world.entity." + ENTITY_SINGULAR + ";";
                            assertTrue(source.contains(
                                    "//? if <26.2\n"
                                            + (upperHolderApi ? "//" : "")
                                            + singularEntityImport));
                        }
                    }
                    String pluralBlockImport =
                            "import net.minecraft.world.level.block.entity." + BLOCK_ENTITY_PLURAL + ";";
                    if (line.equals(pluralBlockImport) || line.equals("//" + pluralBlockImport)) {
                        blockImports++;
                        assertTrue(index > 0 && lines.get(index - 1).equals("//? if >=26.2"),
                                "plural block-entity holder import must be guarded in " + path);
                        assertEquals(upperHolderApi ? pluralBlockImport : "//" + pluralBlockImport, line,
                                "plural block-entity holder import has the wrong active form in " + path);
                        String singularBlockImport =
                                "import net.minecraft.world.level.block.entity." + BLOCK_ENTITY_SINGULAR + ";";
                        assertTrue(source.contains(
                                "//? if <26.2\n"
                                        + (upperHolderApi ? "//" : "")
                                        + singularBlockImport));
                    }
                }
            }
        }

        assertEquals(13, entityImports);
        assertEquals(2, permanentSingularImports);
        assertEquals(1, blockImports);
    }

    @Test
    void sourceGuardsFollowTheActiveConstantsHolder() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(executingNode != null && !executingNode.isBlank(),
                "Gradle must inject the executing Stonecutter node");
        boolean upperHolderApi = executingNode.equals("26.2.x");
        String activeEntityHolder = upperHolderApi ? ENTITY_PLURAL : ENTITY_SINGULAR;
        String inactiveEntityHolder = upperHolderApi ? ENTITY_SINGULAR : ENTITY_PLURAL;
        String activeBlockHolder = upperHolderApi ? BLOCK_ENTITY_PLURAL : BLOCK_ENTITY_SINGULAR;
        String inactiveBlockHolder = upperHolderApi ? BLOCK_ENTITY_SINGULAR : BLOCK_ENTITY_PLURAL;
        Pattern activeEntityLiteral =
                Pattern.compile("\\b" + activeEntityHolder + "\\.[A-Z][A-Z0-9_]*");
        Pattern inactiveEntityLiteral =
                Pattern.compile("\\b" + inactiveEntityHolder + "\\.[A-Z][A-Z0-9_]*");
        Pattern activeBlockLiteral =
                Pattern.compile("\\b" + activeBlockHolder + "\\.[A-Z][A-Z0-9_]*");
        Pattern inactiveBlockLiteral =
                Pattern.compile("\\b" + inactiveBlockHolder + "\\.[A-Z][A-Z0-9_]*");
        Pattern activeEntitySelector = Pattern.compile(
                "(?<!\")StonecutterCapabilityMatrix\\.activeEntityTypeHolder\\(\\)");
        int activeEntityCount = 0;
        int inactiveEntityCount = 0;
        int activeBlockCount = 0;
        int inactiveBlockCount = 0;
        int activeEntitySelectorCount = 0;
        try (var paths = Files.walk(TEST_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                activeEntityCount += countMatches(activeEntityLiteral, source);
                inactiveEntityCount += countMatches(inactiveEntityLiteral, source);
                activeBlockCount += countMatches(activeBlockLiteral, source);
                inactiveBlockCount += countMatches(inactiveBlockLiteral, source);
                activeEntitySelectorCount += countMatches(activeEntitySelector, source);
            }
        }
        assertEquals(7, activeEntityCount,
                "source guards must retain the seven direct active entity constants-holder assertions");
        assertEquals(3, activeEntitySelectorCount,
                "the five cross-node holder assertions must use exactly three centralized selector calls");
        assertEquals(0, inactiveEntityCount,
                "source guards must not retain the inactive entity constants holder");
        assertEquals(1, activeBlockCount,
                "source guards must follow the active block-entity constants holder");
        assertEquals(0, inactiveBlockCount,
                "source guards must not retain the inactive block-entity constants holder");
        String capabilityMatrix = SourceScan.compact(Files.readString(TEST_JAVA.resolve(
                "dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java")));
        assertTrue(capabilityMatrix.contains(
                "returnnodeId().equals(\"26.2.x\")?\"Entity\"+\"Types\":\"Entity\"+\"Type\";"),
                "the centralized selector must keep the exact 26.2 plural versus legacy singular boundary");
    }

    @Test
    void instantEffectSpellingBoundaryPreservesTheRuntimeGameTestSeam() throws IOException {
        String source = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieGiantSunGameTestBodies.java"));
        String rawMethod = SourceScan.methodBody(
                source, "static void zombiePlayerInvertsInstantDamageToHealing");
        String activeMethod = SourceScan.stripComments(rawMethod);
        String corrected = "applyInstant" + "aneousEffect";
        String legacy = "applyInstant" + "enousEffect";
        String callPrefix = "MobEffects.INSTANT_DAMAGE.value().";
        String arguments = "(level, null, null, player, 0, 1.0);";

        assertEquals(1, SourceScan.countOccurrences(rawMethod, callPrefix + corrected + arguments),
                "canonical source must retain the 26.2 instant-effect seam");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, callPrefix + legacy + arguments),
                "canonical source must retain the pre-26.2 spelling of the same seam");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean correctedApi = executingNode.equals("26.2.x");
        assertEquals(correctedApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, callPrefix + corrected + arguments));
        assertEquals(correctedApi ? 0 : 1,
                SourceScan.countOccurrences(activeMethod, callPrefix + legacy + arguments));
    }

    @Test
    void dyedBedHolderBoundarySelectsTheNodeNativeRedBed() throws IOException {
        String source = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTestBodies.java"));
        String rawMethod = SourceScan.methodBody(source, "static void sleepBedExplodesOnRightClick");
        String activeMethod = SourceScan.stripComments(rawMethod);
        String upper = "Blocks." + "BED.pick(DyeColor.RED)";
        String lower = "Blocks." + "RED_BED";

        assertEquals(1, SourceScan.countOccurrences(rawMethod, upper),
                "canonical source must select the 26.2 red bed exactly once");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, lower),
                "canonical source must retain the older nodes' red-bed field exactly once");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean collectionApi = executingNode.equals("26.2.x");
        assertEquals(collectionApi ? 1 : 0, SourceScan.countOccurrences(activeMethod, upper));
        assertEquals(collectionApi ? 0 : 1, SourceScan.countOccurrences(activeMethod, lower));
        assertEquals(1, SourceScan.countOccurrences(
                activeMethod, "Block bed = " + (collectionApi ? upper : lower) + ";"));

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("replace(\"" + upper + "\", \"" + lower + "\")"),
                "bed holder adaptation must stay local so comments and source guards cannot be rewritten");
        assertFalse(controller.contains("replace(\"" + lower + "\", \"" + upper + "\")"),
                "bed holder adaptation must not rely on a collision-prone reverse replacement");
    }

    @Test
    void playerListUuidAccessorBoundaryPreservesAllSevenAssertions() throws IOException {
        String players = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/GameTestPlayers.java"));
        String forms = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieFormGameTestBodies.java"));
        String herobrine = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieHerobrineGameTestBodies.java"));
        String sleep = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTestBodies.java"));

        assertEquals(1, SourceScan.countOccurrences(
                activeMethod(players, "static void disconnectConnectedPlayer"),
                "playerList.getPlayer(playerId) == null"));
        assertEquals(1, SourceScan.countOccurrences(
                activeMethod(forms, "private static void assertInPlacePlayer"),
                "level.getServer().getPlayerList().getPlayer(playerId) == player"));
        assertEquals(1, SourceScan.countOccurrences(
                activeMethod(forms, "private static void assertRealRespawnReplacement"),
                "level.getServer().getPlayerList().getPlayer(playerId) == newPlayer"));
        assertEquals(1, SourceScan.countOccurrences(
                activeMethod(herobrine, "private static void assertPlayerReplacement"),
                "playerList.getPlayer(playerId) == newPlayer"));
        assertEquals(1, SourceScan.countOccurrences(
                activeMethod(herobrine, "private static void completeDeadRespawnForCleanup"),
                "ServerPlayer current = playerList.getPlayer(playerId);"));

        String populationRaw = SourceScan.methodBody(sleep, "private static void assertConnectedPopulation");
        String cleanupRaw = SourceScan.methodBody(sleep, "private static void cleanupCoffinSleepTest");
        String uuidAccessor = "getPlayersBy" + "UUID()";
        String uuidSize = "getPlayerList()." + uuidAccessor + ".size() == 2";
        String listSize = "getPlayerList().getPlayers().size() == 2";
        String uuidEmpty = "getPlayerList()." + uuidAccessor + ".isEmpty()";
        String listEmpty = "getPlayerList().getPlayers().isEmpty()";
        assertEquals(1, SourceScan.countOccurrences(populationRaw, uuidSize));
        assertEquals(1, SourceScan.countOccurrences(populationRaw, listSize));
        assertEquals(1, SourceScan.countOccurrences(cleanupRaw, uuidEmpty));
        assertEquals(1, SourceScan.countOccurrences(cleanupRaw, listEmpty));

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean uuidCollectionApi = executingNode.equals("26.2.x");
        String populationActive = SourceScan.stripComments(populationRaw);
        String cleanupActive = SourceScan.stripComments(cleanupRaw);
        assertEquals(uuidCollectionApi ? 1 : 0, SourceScan.countOccurrences(populationActive, uuidSize));
        assertEquals(uuidCollectionApi ? 0 : 1, SourceScan.countOccurrences(populationActive, listSize));
        assertEquals(uuidCollectionApi ? 1 : 0, SourceScan.countOccurrences(cleanupActive, uuidEmpty));
        assertEquals(uuidCollectionApi ? 0 : 1, SourceScan.countOccurrences(cleanupActive, listEmpty));

        int directMapLookups = 0;
        int directMapContains = 0;
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                directMapLookups += SourceScan.countOccurrences(source, uuidAccessor + ".get(");
                directMapContains += SourceScan.countOccurrences(source, uuidAccessor + ".containsKey(");
            }
        }
        assertEquals(0, directMapLookups,
                "UUID identity lookups must use the stable getPlayer(UUID) API");
        assertEquals(0, directMapContains,
                "UUID disconnect checks must use the stable getPlayer(UUID) API");
    }

    @Test
    void villagerFinalizationBoundaryPreservesConversionDataOrdering() throws IOException {
        String source = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java"));
        String rawMethod = SourceScan.methodBody(source, "private static boolean convertVillagerToZombieVillager");
        String activeMethod = SourceScan.stripComments(rawMethod);
        String finalizedCopy =
                "zombie.setVillagerDataFinalized(villager.getVillagerDataFinalized());";
        String dataCopy = "zombie.setVillagerData(villager.getVillagerData());";
        String finalizeSpawn = "zombie.finalizeSpawn(";

        assertEquals(1, SourceScan.countOccurrences(rawMethod, finalizedCopy),
                "canonical source must retain the 26.2 finalization-state copy");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, dataCopy),
                "all nodes must retain the vanilla post-finalize villager-data copy");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean finalizedAccessorApi = executingNode.equals("26.2.x");
        assertEquals(finalizedAccessorApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, finalizedCopy));
        assertEquals(1, SourceScan.countOccurrences(activeMethod, dataCopy));
        if (finalizedAccessorApi) {
            assertTrue(SourceScan.containsInOrder(activeMethod, finalizedCopy, finalizeSpawn, dataCopy),
                    "26.2 must copy the finalization state before spawn finalization, then copy villager data");
        } else {
            assertTrue(SourceScan.containsInOrder(activeMethod, finalizeSpawn, dataCopy),
                    "older nodes must preserve their vanilla finalize-then-copy conversion order");
        }

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("replace(\"VillagerDataFinalized\", \"VillagerData\")"),
                "the finalized-data boundary must stay local because VillagerData is a separate stable API");
    }

    @Test
    void runBeforeTestEndBoundaryPreservesTheExactDeadlineCallback() throws IOException {
        String disguise = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieDisguiseGameTestBodies.java"));
        String herobrine = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieHerobrineGameTestBodies.java"));
        List<String> rawMethods = List.of(
                SourceScan.methodBody(disguise, "static void villagerFearRespectsDisguise"),
                SourceScan.methodBody(herobrine, "static void herobrineNaturalCaveSpawnSetsPhase"),
                SourceScan.methodBody(herobrine, "static void herobrineDiscardsAfterMaxLifetime"));
        String nativeCallback = "helper.runBeforeTestEnd(() -> {";
        String legacyCallback =
                "helper.runAtTickTime(helper.testInfo.getTimeoutTicks() - 1, () -> {";

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean nativeApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        for (String rawMethod : rawMethods) {
            assertEquals(1, SourceScan.countOccurrences(rawMethod, nativeCallback),
                    "canonical source must retain one native deadline callback per guarded GameTest");
            assertEquals(1, SourceScan.countOccurrences(rawMethod, legacyCallback),
                    "canonical source must retain one exact legacy deadline callback per guarded GameTest");
            String activeMethod = SourceScan.stripComments(rawMethod);
            assertEquals(nativeApi ? 1 : 0,
                    SourceScan.countOccurrences(activeMethod, nativeCallback));
            assertEquals(nativeApi ? 0 : 1,
                    SourceScan.countOccurrences(activeMethod, legacyCallback));
        }

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("replace(\"runBeforeTestEnd"),
                "the GameTest deadline bridge must remain local to its three call sites");
        assertFalse(controller.contains("replace(\"testInfo.getTimeoutTicks"),
                "the exact deadline expression must not rely on a global source replacement");
    }

    @Test
    void playerInteractOnBoundaryPreservesTheNodeNativeEntityInteractionPath() throws IOException {
        String disguise = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieDisguiseGameTestBodies.java"));
        String mount = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieMountGameTestBodies.java"));
        List<String> rawMethods = List.of(
                SourceScan.methodBody(disguise, "static void undisguisedZombieIsDenied"),
                SourceScan.methodBody(disguise, "static void disguisedZombieOpensAndDamagesMask"),
                SourceScan.methodBody(mount, "static void ownedSpiderRideAllowed"),
                SourceScan.methodBody(mount, "static void babyCanRideChicken"));
        List<String> targets = List.of("villager", "villager", "spider", "chicken");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean locationApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        for (int index = 0; index < rawMethods.size(); index++) {
            String rawMethod = rawMethods.get(index);
            String structuralMethod = rawMethod.replaceAll("(?s)/\\*.*?\\*/", "");
            String target = targets.get(index);
            Pattern rawSeam = Pattern.compile(
                    "(?m)^\\h*InteractionResult result = player\\.interactOn\\(\\s*" + target
                            + "\\s*,\\s*InteractionHand\\.MAIN_HAND\\s*\\R"
                            + "\\s*//\\? if >=26\\.1\\s*\\R"
                            + "\\s*(?://)?\\s*,\\s*Vec3\\.ZERO\\s*\\R"
                            + "\\s*\\);");
            assertEquals(1, countMatches(rawSeam, structuralMethod),
                    "canonical source must retain one local interactOn tail seam for " + target);

            String activeMethod = SourceScan.compact(SourceScan.stripComments(rawMethod));
            String locationCall =
                    "player.interactOn(" + target + ",InteractionHand.MAIN_HAND,Vec3.ZERO);";
            String legacyCall =
                    "player.interactOn(" + target + ",InteractionHand.MAIN_HAND);";
            assertEquals(locationApi ? 1 : 0,
                    SourceScan.countOccurrences(activeMethod, locationCall));
            assertEquals(locationApi ? 0 : 1,
                    SourceScan.countOccurrences(activeMethod, legacyCall));
            assertEquals(1, SourceScan.countOccurrences(activeMethod, "player.interactOn("),
                    "each GameTest must exercise exactly one node-native Player.interactOn path");
            assertEquals(0, SourceScan.countOccurrences(activeMethod, "interactAt("),
                    "the legacy adapter must not switch to the semantically different interactAt path");
        }

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("interactOn"),
                "Player.interactOn adaptation must remain local to the real GameTest calls");
        assertFalse(controller.contains("InteractionHand"),
                "the interaction-hand portion of the call must not use a global source replacement");
        assertFalse(controller.contains("Vec3"),
                "the optional interaction location must not use a global source replacement");
    }

    @Test
    void herobrineInteractionOverrideBoundaryPreservesPassiveFallbackAndEventCancellation() throws IOException {
        String entity = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/entity/HerobrineEntity.java"));
        String structuralEntity = entity.replaceAll("(?s)/\\*.*?\\*/", "");
        String locationSignature =
                "public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation)";
        String legacySignature =
                "public InteractionResult interactAt(Player player, Vec3 hitLocation, InteractionHand hand)";
        Pattern rawSeam = Pattern.compile(
                "(?m)^\\h*@Override\\R"
                        + "\\h*//\\? if >=26\\.1\\R"
                        + "\\h*(?://)?" + Pattern.quote(locationSignature) + "\\h*\\{\\R"
                        + "\\h*//\\? if <26\\.1\\R"
                        + "\\h*(?://)?" + Pattern.quote(legacySignature) + "\\h*\\{\\R"
                        + "\\h*return InteractionResult\\.PASS;\\R"
                        + "\\h*\\}");
        assertEquals(1, countMatches(rawSeam, structuralEntity),
                "canonical source must retain one local dual-signature Herobrine interaction seam");
        assertEquals(1, SourceScan.countOccurrences(entity, locationSignature));
        assertEquals(1, SourceScan.countOccurrences(entity, legacySignature));

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean locationApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        String activeEntity = SourceScan.stripComments(entity);
        assertEquals(locationApi ? 1 : 0,
                SourceScan.countOccurrences(activeEntity, locationSignature));
        assertEquals(locationApi ? 0 : 1,
                SourceScan.countOccurrences(activeEntity, legacySignature));
        String activeSignature = locationApi ? locationSignature : legacySignature;
        assertEquals(
                SourceScan.compact(activeSignature + " { return InteractionResult.PASS; }"),
                SourceScan.compact(SourceScan.methodBody(activeEntity, activeSignature)),
                "the node-native override must remain a passive PASS fallback");

        String events = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/HerobrineEvents.java"));
        String activeEvents = SourceScan.stripComments(events);
        String generalSignature =
                "public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)";
        String specificSignature =
                "public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)";
        String generalExpected = generalSignature
                + " { cancelHerobrineInteraction("
                + "event.getTarget(), event, event::setCancellationResult); }";
        assertEquals(1, SourceScan.countOccurrences(
                activeEvents, "@SubscribeEvent\n    " + generalSignature));
        assertEquals(
                SourceScan.compact(generalExpected),
                SourceScan.compact(SourceScan.methodBody(activeEvents, generalSignature)));
        boolean splitEvents = !executingNode.equals("26.2.x");
        assertEquals(splitEvents ? 1 : 0,
                SourceScan.countOccurrences(activeEvents, specificSignature));
        if (splitEvents) {
            String specificExpected = specificSignature
                    + " { cancelHerobrineInteraction("
                    + "event.getTarget(), event, event::setCancellationResult); }";
            assertEquals(
                    SourceScan.compact(specificExpected),
                    SourceScan.compact(SourceScan.methodBody(activeEvents, specificSignature)));
        }
        String cancellation = SourceScan.compact(SourceScan.methodBody(
                activeEvents, "private static void cancelHerobrineInteraction"));
        assertEquals(1, SourceScan.countOccurrences(cancellation, "event.setCanceled(true);"));
        assertEquals(1, SourceScan.countOccurrences(
                cancellation, "setCancellationResult.accept(InteractionResult.SUCCESS_SERVER);"));
        assertFalse(cancellation.contains("handleEncounter("),
                "right-click cancellation must not advance the encounter state machine");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("InteractionResult interact"),
                "Herobrine interaction adaptation must not use a global signature replacement");
        assertFalse(controller.contains("interactAt"),
                "the legacy Entity override must remain local to HerobrineEntity");
    }

    @Test
    void playerMessageBoundaryUsesStableServerPlayerPacketPaths() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");

        String coffinBlock = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/block/CoffinBlock.java")));
        String coffinUse = SourceScan.compact(
                SourceScan.methodBody(coffinBlock, "protected InteractionResult useWithoutItem"));
        String coffinFalse = SourceScan.compact(
                "serverPlayer.sendSystemMessage(Component.translatable("
                        + "ZombieSleepRules.coffinMessageKey(action, false)), true);");
        String coffinNap = SourceScan.compact(
                "serverPlayer.sendSystemMessage(Component.translatable("
                        + "ZombieSleepRules.coffinMessageKey(action, napBegan)), true);");
        assertEquals(3, SourceScan.countOccurrences(coffinUse, coffinFalse));
        assertEquals(1, SourceScan.countOccurrences(coffinUse, coffinNap));
        assertEquals(4, SourceScan.countOccurrences(coffinUse, "serverPlayer.sendSystemMessage("));
        assertFalse(coffinUse.contains("sendOverlayMessage("),
                "coffin action-bar packets must use the five-node ServerPlayer overload");

        String napManager = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/CoffinNapManager.java")));
        String napTick = SourceScan.compact(
                SourceScan.methodBody(napManager, "public static void onPlayerTick"));
        String wake = SourceScan.compact(
                SourceScan.methodBody(
                        napManager, "private static void wake(ServerPlayer player, NapWakeReason reason)"));
        String wakeAll = SourceScan.compact(
                SourceScan.methodBody(
                        napManager, "private static void wakeAllInLevel(ServerLevel level, boolean skipped)"));
        assertEquals(1, SourceScan.countOccurrences(
                napTick,
                SourceScan.compact(
                        "player.sendSystemMessage(Component.translatable("
                                + "ZombieSleepRules.coffinVoteProgressMessageKey(), deep, needed), true);")));
        assertEquals(1, SourceScan.countOccurrences(
                wake,
                SourceScan.compact(
                        "player.sendSystemMessage(Component.translatable("
                                + "ZombieSleepRules.napWakeMessageKey(reason)), true);")));
        assertEquals(1, SourceScan.countOccurrences(
                wakeAll,
                SourceScan.compact(
                        "p.sendSystemMessage(Component.translatable("
                                + "ZombieSleepRules.napWakeMessageKey(reason)), true);")));
        assertFalse(napManager.contains("sendOverlayMessage("),
                "all nap lifecycle overlays must use the stable ServerPlayer packet overload");

        String mountEvents = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/ZombieMountEvents.java")));
        String helperSignature =
                "private static void sendPlayerSystemMessage(Player player, Component message)";
        assertEquals(1, SourceScan.countOccurrences(mountEvents, helperSignature),
                "the Player-typed mount paths must share one private server-side message bridge");
        assertEquals(
                SourceScan.compact(
                        helperSignature
                                + " { if (player instanceof ServerPlayer serverPlayer) {"
                                + " serverPlayer.sendSystemMessage(message); } }"),
                SourceScan.compact(SourceScan.methodBody(mountEvents, helperSignature)));
        List<String> mountMessages = List.of(
                "sendPlayerSystemMessage(player, Component.translatable("
                        + "\"iamzombieq.message.mount.horse_refused\"));",
                "sendPlayerSystemMessage(player, Component.translatable("
                        + "\"iamzombieq.message.mount.horse_full_health\"));",
                "sendPlayerSystemMessage(player, Component.translatable("
                        + "\"iamzombieq.message.mount.spider_tamed\"));",
                "sendPlayerSystemMessage(player, Component.translatable("
                        + "\"iamzombieq.message.mount.spider_taming\", percent));",
                "sendPlayerSystemMessage(player, Component.translatable("
                        + "\"iamzombieq.message.mount.spider_owned\"));");
        for (String message : mountMessages) {
            assertEquals(1, SourceScan.countOccurrences(
                    SourceScan.compact(mountEvents), SourceScan.compact(message)));
        }
        assertEquals(6, SourceScan.countOccurrences(mountEvents, "sendPlayerSystemMessage("),
                "five call sites plus one private bridge must exist");
        assertFalse(mountEvents.contains("player.sendSystemMessage("),
                "Player-typed calls must not rely on the 26.x-only base-class API");
        assertFalse(mountEvents.contains("displayClientMessage("),
                "system messages must not be rerouted through a client-display API");
        assertFalse(mountEvents.contains("net.minecraft.client."),
                "the server message bridge must not load client-only classes");

        String herobrineEvents = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/HerobrineEvents.java")));
        assertEquals(1, SourceScan.countOccurrences(
                herobrineEvents,
                "player.sendSystemMessage(Component.translatable(cue.subtitleKey()), true);"),
                "Herobrine's existing action-bar cue must retain overlay=true");
        String playerEvents = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/ZombiePlayerEvents.java")));
        assertEquals(1, SourceScan.countOccurrences(
                playerEvents,
                "player.sendSystemMessage(Component.translatable("
                        + "\"iamzombieq.message.coffin.recipes_unlocked\"));"));
        assertEquals(1, SourceScan.countOccurrences(
                playerEvents,
                "player.sendSystemMessage(Component.translatable("
                        + "\"iamzombieq.message.peaceful_unsupported\"));"));

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("sendOverlayMessage")
                        || controller.contains("sendSystemMessage")
                        || controller.contains("displayClientMessage"),
                "message delivery adaptation must not use a global source replacement");
    }

    @Test
    void playerSkinConstructionUsesExactNodeBoundary() throws IOException {
        String visuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String rawMethod = SourceScan.methodBody(visuals, "private static PlayerSkin cachedZombieSkin");
        boolean textureAssetApi = !StonecutterCapabilityMatrix.nodeId().equals("1.21.8");

        String assetCape = "ClientAsset.Texture cape = original.cape();";
        String assetElytra = "ClientAsset.Texture elytra = original.elytra();";
        String legacyCape = "Identifier cape = original.capeTexture();";
        String legacyElytra = "Identifier elytra = original.elytraTexture();";
        String assetConstructor =
                "new PlayerSkin(new FixedTexture(texture), cape, elytra, PlayerModelType.WIDE, false)";
        String legacyConstructor =
                "new PlayerSkin(texture, null, cape, elytra, PlayerSkin.Model.WIDE, false)";
        String fixedTextureDeclaration =
                "private record FixedTexture(Identifier texturePath) implements ClientAsset.Texture";

        assertEquals(1, SourceScan.countOccurrences(visuals, assetConstructor));
        assertEquals(1, SourceScan.countOccurrences(visuals, legacyConstructor));
        assertEquals(1, SourceScan.countOccurrences(visuals, fixedTextureDeclaration));
        assertEquals(1, SourceScan.countOccurrences(visuals, "return texturePath;"));
        assertTrue(SourceScan.containsInOrder(
                        rawMethod,
                        "//? if >=1.21.10 {",
                        assetCape,
                        assetElytra,
                        "//?} else {",
                        legacyCape,
                        legacyElytra,
                        "CachedSkin cached = SKIN_CACHE.get(id);",
                        "Identifier texture = textureFor(form, baby);",
                        "//? if >=1.21.10 {",
                        assetConstructor,
                        "//?} else {",
                        legacyConstructor,
                        "SKIN_CACHE.put(id, new CachedSkin(form, baby, cape, elytra, skin));"),
                "the cache miss path must retain one >=1.21.10 texture-asset constructor and one 1.21.8 flat constructor");

        String activeSource = SourceScan.stripComments(visuals);
        String activeMethod = SourceScan.methodBody(
                activeSource, "private static PlayerSkin cachedZombieSkin");
        assertEquals(1, SourceScan.countOccurrences(activeMethod, "new PlayerSkin("),
                "a cache miss must allocate exactly one node-native PlayerSkin");
        assertEquals(textureAssetApi ? 1 : 0, SourceScan.countOccurrences(activeMethod, assetConstructor));
        assertEquals(textureAssetApi ? 0 : 1, SourceScan.countOccurrences(activeMethod, legacyConstructor));
        assertEquals(textureAssetApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource, fixedTextureDeclaration),
                "FixedTexture may only exist where ClientAsset.Texture is an interface");
        assertEquals(textureAssetApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource, "return texturePath;"));
        assertEquals(textureAssetApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource, "import net.minecraft.core.ClientAsset;"));
        assertEquals(textureAssetApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource,
                        "import net.minecraft.world.entity.player.PlayerModelType;"));
        assertEquals(textureAssetApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource,
                        "import net.minecraft.world.entity.player.PlayerSkin;"));
        assertEquals(textureAssetApi ? 0 : 1,
                SourceScan.countOccurrences(activeSource,
                        "import net.minecraft.client.resources.PlayerSkin;"));

        assertTrue(SourceScan.containsInOrder(
                        activeMethod,
                        "CachedSkin cached = SKIN_CACHE.get(id);",
                        "return cached.skin;",
                        "PlayerSkin skin = new PlayerSkin(",
                        "SKIN_CACHE.put(id, new CachedSkin(form, baby, cape, elytra, skin));",
                        "return skin;"),
                "the node seam must preserve cache hits and allocate only after a miss");
        assertTrue(activeMethod.contains(textureAssetApi ? assetCape : legacyCape));
        assertTrue(activeMethod.contains(textureAssetApi ? assetElytra : legacyElytra));
        assertTrue(activeMethod.contains("java.util.Objects.equals(cached.cape, cape)"));
        assertTrue(activeMethod.contains("java.util.Objects.equals(cached.elytra, elytra)"));
        assertFalse(activeMethod.contains("Class.forName")
                        || activeMethod.contains("java.lang.reflect")
                        || activeMethod.contains("@SuppressWarnings"),
                "the skin API boundary must remain typed and reflection-free");

        assertTrue(SourceScan.methodBody(activeSource, "static void invalidateSkin")
                .contains("SKIN_CACHE.remove(id);"));
        assertTrue(SourceScan.methodBody(activeSource, "static void clearSkins")
                .contains("SKIN_CACHE.clear();"));
    }

    @Test
    void renderPlayerEventPreGenericityUsesExactNodeBoundary() throws IOException {
        boolean genericEvent = StonecutterCapabilityMatrix.hasGenericRenderPlayerEvent();
        String client = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java"));
        String visuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));

        String genericClientSignature =
                "public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {";
        String legacyClientSignature =
                "public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {";
        String genericVisualsSignature =
                "public static void renderMonsterBody(RenderPlayerEvent.Pre<?> event) {";
        String legacyVisualsSignature =
                "public static void renderMonsterBody(RenderPlayerEvent.Pre event) {";

        assertEquals(1, SourceScan.countOccurrences(client, genericClientSignature));
        assertEquals(1, SourceScan.countOccurrences(client, legacyClientSignature));
        assertEquals(1, SourceScan.countOccurrences(visuals, genericVisualsSignature));
        assertEquals(1, SourceScan.countOccurrences(visuals, legacyVisualsSignature));

        String expectedGenericPrefix = genericEvent ? "" : "//";
        String expectedLegacyPrefix = genericEvent ? "//" : "";
        Pattern clientSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.10\\R"
                        + "\\h*" + Pattern.quote(expectedGenericPrefix + genericClientSignature) + "\\R"
                        + "\\h*//\\? if <1\\.21\\.10\\R"
                        + "\\h*" + Pattern.quote(expectedLegacyPrefix + legacyClientSignature) + "\\R");
        Pattern visualsSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.10\\R"
                        + "\\h*" + Pattern.quote(expectedGenericPrefix + genericVisualsSignature) + "\\R"
                        + "\\h*//\\? if <1\\.21\\.10\\R"
                        + "\\h*" + Pattern.quote(expectedLegacyPrefix + legacyVisualsSignature) + "\\R");
        assertEquals(1, countMatches(clientSeam, client),
                "the event subscriber must keep one local genericity boundary");
        assertEquals(1, countMatches(visualsSeam, visuals),
                "the dormant renderer must keep one local genericity boundary");

        String activeClient = SourceScan.stripComments(client);
        String activeRenderPre = SourceScan.methodBody(
                activeClient, "public static void onRenderPlayerPre");
        assertEquals(
                SourceScan.compact(
                        (genericEvent ? genericClientSignature : legacyClientSignature)
                                + " ZombiePlayerVisuals.applyPlayerSkin(event.getRenderState()); }"),
                SourceScan.compact(activeRenderPre));

        String activeVisuals = SourceScan.stripComments(visuals);
        String activeRenderBody = SourceScan.methodBody(
                activeVisuals, "public static void renderMonsterBody");
        assertTrue(activeRenderBody.startsWith(
                        genericEvent ? genericVisualsSignature : legacyVisualsSignature),
                "the active renderer must expose exactly the node-native event signature");
        assertEquals(1, SourceScan.countOccurrences(
                activeRenderBody,
                "ZombieRenderRules.usesMonsterTexture(IAmZombieClientConfig.PLAYER_SKIN_MODE.get())"));
        assertFalse(activeRenderBody.contains("Class.forName")
                        || activeRenderBody.contains("java.lang.reflect")
                        || activeRenderBody.contains("@SuppressWarnings")
                        || activeRenderBody.contains("required = 0"),
                "the event genericity seam must remain typed and strict");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("RenderPlayerEvent.Pre"),
                "the event genericity boundary must not use a global source replacement");
    }

    @Test
    void dormantZombieStateScoreTextBoundaryTracksTheTargetStateApi() throws IOException {
        String visuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String rawMethod = SourceScan.methodBody(
                visuals, "private static ZombieRenderState copyToZombieState");
        String scoreCopy = "target.scoreText = source.scoreText;";
        Pattern rawSeam = Pattern.compile(
                "(?m)^\\h*target\\.nameTag = source\\.nameTag;\\R"
                        + "\\h*//\\? if >=26\\.1\\R"
                        + "\\h*(?://)?target\\.scoreText = source\\.scoreText;\\R"
                        + "\\h*target\\.nameTagAttachment = source\\.nameTagAttachment;");
        assertEquals(1, countMatches(rawSeam, rawMethod),
                "the dormant adapter must retain one local scoreText target-state boundary");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, scoreCopy));

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean targetHasScoreText = Set.of("26.2.x", "26.1.x").contains(executingNode);
        String activeMethod = SourceScan.stripComments(rawMethod);
        assertEquals(targetHasScoreText ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, scoreCopy));
        if (targetHasScoreText) {
            assertTrue(SourceScan.containsInOrder(
                    activeMethod,
                    "target.nameTag = source.nameTag;",
                    scoreCopy,
                    "target.nameTagAttachment = source.nameTagAttachment;"));
        } else {
            assertTrue(SourceScan.containsInOrder(
                    activeMethod,
                    "target.nameTag = source.nameTag;",
                    "target.nameTagAttachment = source.nameTagAttachment;"));
        }

        String client = SourceScan.stripComments(Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/IAmZombieClient.java")));
        String renderPre = SourceScan.methodBody(
                client, "public static void onRenderPlayerPre");
        String renderPreEventType = StonecutterCapabilityMatrix.hasGenericRenderPlayerEvent()
                ? "RenderPlayerEvent.Pre<?>"
                : "RenderPlayerEvent.Pre";
        assertEquals(
                SourceScan.compact(
                        "public static void onRenderPlayerPre(" + renderPreEventType + " event) {"
                                + " ZombiePlayerVisuals.applyPlayerSkin(event.getRenderState()); }"),
                SourceScan.compact(renderPre),
                "the legacy copyToZombieState path may only omit scoreText while it remains dormant");

        Pattern dormantRendererReference = Pattern.compile("\\brenderMonsterBody\\b");
        Pattern dormantRendererDeclaration =
                Pattern.compile("\\bpublic\\h+static\\h+void\\h+renderMonsterBody\\h*\\(");
        int productionReferences = 0;
        int productionDeclarations = 0;
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String productionSource = SourceScan.stripComments(Files.readString(path));
                productionReferences += countMatches(dormantRendererReference, productionSource);
                productionDeclarations += countMatches(dormantRendererDeclaration, productionSource);
            }
        }
        assertEquals(1, productionDeclarations,
                "the dormant renderer must retain exactly one production declaration");
        assertEquals(0, productionReferences - productionDeclarations,
                "the scoreText omission is valid only while production caller count remains zero");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("scoreText"),
                "the target-state field boundary must not use a global source replacement");
    }

    @Test
    void livingEntityRenderStateKineticFeedbackUsesExactNodeBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean kineticFeedbackApi =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                kineticFeedbackApi,
                StonecutterCapabilityMatrix.hasKineticHitFeedbackRenderStateApi(),
                "the centralized capability matrix must retain the >=1.21.11 render-state boundary");

        Path matrixPath =
                Path.of("src/test/java/dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>KINETIC_HIT_FEEDBACK_RENDER_STATE_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the kinetic-feedback capability must remain high3/low2");
        assertTrue(compactMatrix.contains(
                        "returnKINETIC_HIT_FEEDBACK_RENDER_STATE_NODES.contains(nodeId());"),
                "the capability accessor must read the one recorded node set");

        String visuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String marker = "CROSS_VERSION-LIVING-RENDER-KINETIC-FEEDBACK-API";
        assertEquals(1, SourceScan.countOccurrences(visuals, marker),
                "the production field boundary marker must exist exactly once");

        String rawMethod = SourceScan.methodBody(
                visuals, "private static ZombieRenderState copyToZombieState");
        String assignment =
                "target.ticksSinceKineticHitFeedback = source.ticksSinceKineticHitFeedback;";
        assertEquals(1, SourceScan.countOccurrences(rawMethod, marker),
                "the marker must stay inside the one state-copy method");
        Pattern rawSeam = Pattern.compile(
                "(?m)^\\h*target\\.ageScale = source\\.ageScale;\\R"
                        + "\\h*// CROSS_VERSION-LIVING-RENDER-KINETIC-FEEDBACK-API\\R"
                        + "\\h*//\\? if >=1\\.21\\.11\\R"
                        + "\\h*(?://)?target\\.ticksSinceKineticHitFeedback"
                        + " = source\\.ticksSinceKineticHitFeedback;\\R"
                        + "\\h*target\\.isUpsideDown = source\\.isUpsideDown;");
        assertEquals(1, countMatches(rawSeam, rawMethod),
                "the exact conditional copy must stay between ageScale and isUpsideDown");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, assignment),
                "the canonical source must retain the high-node value-preserving copy");
        assertEquals(2, SourceScan.countOccurrences(
                rawMethod, "ticksSinceKineticHitFeedback"),
                "the one assignment must contain exactly one source and one target field reference");
        assertEquals(1, SourceScan.countOccurrences(visuals, assignment),
                "the compatibility boundary must not spread into the live replacement path");

        String activeMethod = SourceScan.stripComments(rawMethod);
        assertEquals(kineticFeedbackApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, assignment));
        assertEquals(kineticFeedbackApi ? 2 : 0,
                SourceScan.countOccurrences(activeMethod, "ticksSinceKineticHitFeedback"));
        if (kineticFeedbackApi) {
            assertTrue(SourceScan.containsInOrder(
                    activeMethod,
                    "target.ageScale = source.ageScale;",
                    assignment,
                    "target.isUpsideDown = source.isUpsideDown;"),
                    "high nodes must preserve the actual kinetic-feedback animation state");
        } else {
            assertTrue(SourceScan.containsInOrder(
                    activeMethod,
                    "target.ageScale = source.ageScale;",
                    "target.isUpsideDown = source.isUpsideDown;"),
                    "low nodes must omit only the platform-absent field copy");
        }
        assertFalse(rawMethod.contains("ticksSinceKineticHitFeedback = 0")
                        || rawMethod.contains("ticksSinceKineticHitFeedback = source.deathTime")
                        || rawMethod.contains("ticksSinceKineticHitFeedback = source.attackTime")
                        || rawMethod.contains("ticksSinceKineticHitFeedback = source.hasRedOverlay")
                        || rawMethod.contains("Class." + "forName")
                        || rawMethod.contains("java.lang.reflect")
                        || rawMethod.contains("@Accessor")
                        || rawMethod.contains("@Invoker")
                        || rawMethod.contains("@Suppress" + "Warnings")
                        || rawMethod.contains("required = 0"),
                "the boundary must not fabricate a substitute field or use a bypass");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains(marker)
                        || controller.contains("ticksSinceKineticHitFeedback"),
                "the field boundary must remain local instead of using a global replacement");
    }

    @Test
    void heldItemStackPayloadAndCollectorSubmitUseExactNodeBoundaries() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean stackPayload =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        boolean collectorSubmit =
                Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10").contains(executingNode);
        assertEquals(
                stackPayload,
                StonecutterCapabilityMatrix.hasHeldItemStackPayloadApi(),
                "the centralized capability matrix must retain the >=1.21.11 stack-payload boundary");
        assertEquals(
                collectorSubmit,
                StonecutterCapabilityMatrix.hasHeldItemCollectorSubmitApi(),
                "the collector-submit boundary must include 1.21.10 but exclude 1.21.8");

        Path matrixPath =
                TEST_JAVA.resolve("dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>HELD_ITEM_STACK_PAYLOAD_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the raw stack and swing payload must remain high3/low2");
        assertTrue(compactMatrix.contains(
                        "returnHELD_ITEM_STACK_PAYLOAD_NODES.contains(nodeId());"));
        assertEquals(2, SourceScan.countOccurrences(
                        compactMatrix,
                        "returnsubmitNodeCollectorRenderPipelineStatus()"
                                + ".equals(SUBMIT_NODE_COLLECTOR_PIPELINE_PRESENT);"),
                "held-item and player rendering must consume the same central pipeline capability");

        String visuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        String stateMarker = "CROSS_VERSION-HELD-ITEM-RENDER-STATE-API";
        String submitMarker = "CROSS_VERSION-HELD-ITEM-SUBMIT-API";
        assertEquals(1, SourceScan.countOccurrences(visuals, stateMarker),
                "the state-copy boundary marker must exist exactly once");
        assertEquals(1, SourceScan.countOccurrences(visuals, submitMarker),
                "the submit-overload boundary marker must exist exactly once");

        String rawCopy = SourceScan.methodBody(
                visuals,
                "private static ZombieRenderState copyToZombieState("
                        + (collectorSubmit ? "AvatarRenderState" : "PlayerRenderState"));
        assertEquals(1, SourceScan.countOccurrences(rawCopy, stateMarker));
        Pattern rawRightHandStackSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.11\\R"
                        + "\\h*(?://)?target\\.rightHandItemStack"
                        + " = source\\.rightHandItemStack;");
        Pattern rawLeftHandPayloadSeam = Pattern.compile(
                "(?m)^\\h*//\\? if >=1\\.21\\.11 \\{\\R"
                        + "\\h*(?:/\\*)?target\\.leftHandItemStack"
                        + " = source\\.leftHandItemStack;\\R"
                        + "\\h*target\\.swingAnimationType"
                        + " = source\\.swingAnimationType;\\R"
                        + "\\h*(?:\\*/)?//\\?\\}");
        assertEquals(1, countMatches(rawRightHandStackSeam, rawCopy));
        assertEquals(1, countMatches(rawLeftHandPayloadSeam, rawCopy));
        assertEquals(2, SourceScan.countOccurrences(rawCopy, "rightHandItemStack"));
        assertEquals(2, SourceScan.countOccurrences(rawCopy, "leftHandItemStack"));
        assertEquals(2, SourceScan.countOccurrences(rawCopy, "swingAnimationType"));

        String activeCopy = SourceScan.stripComments(rawCopy);
        assertEquals(stackPayload ? 2 : 0,
                SourceScan.countOccurrences(activeCopy, "rightHandItemStack"));
        assertEquals(stackPayload ? 2 : 0,
                SourceScan.countOccurrences(activeCopy, "leftHandItemStack"));
        assertEquals(stackPayload ? 2 : 0,
                SourceScan.countOccurrences(activeCopy, "swingAnimationType"));
        if (stackPayload) {
            assertTrue(SourceScan.containsInOrder(
                    activeCopy,
                    "target.rightArmPose = source.rightArmPose;",
                    "target.rightHandItemStack = source.rightHandItemStack;",
                    "target.leftArmPose = source.leftArmPose;",
                    "target.leftHandItemStack = source.leftHandItemStack;",
                    "target.swingAnimationType = source.swingAnimationType;",
                    "target.attackTime = source.attackTime;"));
        } else {
            assertTrue(SourceScan.containsInOrder(
                    activeCopy,
                    "target.rightArmPose = source.rightArmPose;",
                    "target.leftArmPose = source.leftArmPose;",
                    "target.attackTime = source.attackTime;"));
        }

        String layerAnchor =
                "private static final class ZombiePlayerItemInHandLayer extends "
                        + "ItemInHandLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {";
        String rawLayer = SourceScan.blockBody(visuals, layerAnchor);
        String compactRawLayer = SourceScan.compact(rawLayer);
        assertEquals(1, SourceScan.countOccurrences(rawLayer, submitMarker));
        assertEquals(1, SourceScan.countOccurrences(rawLayer, "//? if >=1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer, "//? if >=1.21.10 && <1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(rawLayer, "//? if <1.21.10 {"));
        assertEquals(8, SourceScan.countOccurrences(rawLayer, "submitArmWithItem("));
        assertEquals(4, SourceScan.countOccurrences(rawLayer, "renderArmWithItem("),
                "the canonical source must retain the complete typed 1.21.8 direct-render transport");
        assertEquals(1, SourceScan.countOccurrences(rawLayer, "private void render("));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer, "avatarState.rightHandItemState"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer, "avatarState.leftHandItemState"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer, "avatarState.rightHandItemStack"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer, "avatarState.leftHandItemStack"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "submitArmWithItem(zombieState, avatarState.rightHandItemState, "
                        + "avatarState.rightHandItemStack, HumanoidArm.RIGHT, "
                        + "poseStack, collector, lightCoords);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "submitArmWithItem(zombieState, avatarState.leftHandItemState, "
                        + "avatarState.leftHandItemStack, HumanoidArm.LEFT, "
                        + "poseStack, collector, lightCoords);"));
        Pattern legacyRight = Pattern.compile("avatarState\\.rightHandItem\\b");
        Pattern legacyLeft = Pattern.compile("avatarState\\.leftHandItem\\b");
        assertEquals(2, countMatches(legacyRight, rawLayer));
        assertEquals(2, countMatches(legacyLeft, rawLayer));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "submitArmWithItem(zombieState, avatarState.rightHandItem, "
                        + "HumanoidArm.RIGHT, poseStack, collector, lightCoords);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "submitArmWithItem(zombieState, avatarState.leftHandItem, "
                        + "HumanoidArm.LEFT, poseStack, collector, lightCoords);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "super.submitArmWithItem(state, item, itemStack, arm, poseStack, "
                        + "submitNodeCollector, lightCoords);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "super.submitArmWithItem(state, item, arm, poseStack, collector, lightCoords);"));
        assertEquals(1, SourceScan.countOccurrences(
                compactRawLayer,
                "renderArmWithItem(zombieState,avatarState.rightHandItem,"
                        + "HumanoidArm.RIGHT,poseStack,bufferSource,packedLight);"));
        assertEquals(1, SourceScan.countOccurrences(
                compactRawLayer,
                "renderArmWithItem(zombieState,avatarState.leftHandItem,"
                        + "HumanoidArm.LEFT,poseStack,bufferSource,packedLight);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayer,
                "super.renderArmWithItem(state, item, arm, poseStack, bufferSource, packedLight);"));

        String activeLayer = SourceScan.stripComments(rawLayer);
        assertEquals(collectorSubmit ? 4 : 0,
                SourceScan.countOccurrences(activeLayer, "submitArmWithItem("));
        assertEquals(collectorSubmit ? 0 : 4,
                SourceScan.countOccurrences(activeLayer, "renderArmWithItem("));
        assertEquals(1, SourceScan.countOccurrences(activeLayer, "@Override"));
        assertEquals(collectorSubmit ? 1 : 0,
                SourceScan.countOccurrences(activeLayer, "super.submitArmWithItem("));
        assertEquals(collectorSubmit ? 0 : 1,
                SourceScan.countOccurrences(activeLayer, "super.renderArmWithItem("));
        if (stackPayload) {
            assertEquals(1, SourceScan.countOccurrences(
                    activeLayer, "avatarState.rightHandItemState"));
            assertEquals(1, SourceScan.countOccurrences(
                    activeLayer, "avatarState.leftHandItemState"));
            assertEquals(1, SourceScan.countOccurrences(
                    activeLayer, "avatarState.rightHandItemStack"));
            assertEquals(1, SourceScan.countOccurrences(
                    activeLayer, "avatarState.leftHandItemStack"));
            assertEquals(0, countMatches(legacyRight, activeLayer));
            assertEquals(0, countMatches(legacyLeft, activeLayer));
            assertEquals(1, SourceScan.countOccurrences(activeLayer, "ItemStack itemStack"));
        } else if (collectorSubmit) {
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "rightHandItemState"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "leftHandItemState"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "rightHandItemStack"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "leftHandItemStack"));
            assertEquals(1, countMatches(legacyRight, activeLayer));
            assertEquals(1, countMatches(legacyLeft, activeLayer));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "ItemStack itemStack"));
        } else {
            assertEquals("1.21.8", executingNode);
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "private void submit("));
            assertEquals(1, SourceScan.countOccurrences(activeLayer, "private void render("));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "SubmitNodeCollector"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "AvatarRenderState"));
            assertEquals(2, SourceScan.countOccurrences(activeLayer, "MultiBufferSource"));
            assertEquals(1, SourceScan.countOccurrences(activeLayer, "PlayerRenderState"));
            assertEquals(1, countMatches(legacyRight, activeLayer));
            assertEquals(1, countMatches(legacyLeft, activeLayer));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "rightHandItemState"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "leftHandItemState"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "rightHandItemStack"));
            assertEquals(0, SourceScan.countOccurrences(activeLayer, "leftHandItemStack"));
        }

        assertFalse(rawLayer.contains("ItemStack.EMPTY")
                        || rawLayer.contains("new ItemStackRenderState")
                        || rawLayer.contains("null")
                        || rawLayer.contains("Class." + "forName")
                        || rawLayer.contains("java.lang.reflect")
                        || rawLayer.contains("@Suppress" + "Warnings")
                        || rawLayer.contains("required = 0"),
                "the boundary must preserve the real per-hand state without a bypass or fabricated stack");
        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains(stateMarker)
                        || controller.contains(submitMarker)
                        || controller.contains("rightHandItemStack")
                        || controller.contains("leftHandItemStack")
                        || controller.contains("submitArmWithItem")
                        || controller.contains("renderArmWithItem"),
                "the held-item API family must use local typed seams, not a global replacement");
    }

    @Test
    void coffinWorldClockBoundaryPreservesProductAndFixtureSemantics() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean worldClockApi = Set.of("26.2.x", "26.1.x").contains(executingNode);

        String rawManager = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/CoffinNapManager.java"));
        String rawBlock = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/block/CoffinBlock.java"));
        String rawFixture = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/IAmZombieMobSleepGameTestBodies.java"));
        assertTrue(rawManager.contains("//? if >=26.1") && rawManager.contains("//? if <26.1"),
                "the product clock bridge must be a local Stonecutter seam");
        assertTrue(rawBlock.contains("//? if >=26.1") && rawBlock.contains("//?} else {"),
                "the coffin availability check must retain both clock APIs");
        assertTrue(rawFixture.contains("//? if >=26.1") && rawFixture.contains("//? if <26.1"),
                "the required GameTest fixture must retain both clock APIs");

        String manager = SourceScan.stripComments(rawManager);
        String advance = SourceScan.methodBody(manager, "private static boolean advanceToNight");
        assertTrue(advance.contains("EventHooks.onSleepFinished"));
        assertTrue(advance.contains("level.resetWeatherCycle()"));
        assertTrue(advance.contains("return true;"));
        boolean genericGameRules = !Set.of("1.21.10", "1.21.8").contains(executingNode);
        String rules = "Game" + "Rules";
        String modernTime = rules + ".ADVANCE_" + "TIME";
        String modernWeather = rules + ".ADVANCE_" + "WEATHER";
        String legacyTime = rules + ".RULE_" + "DAYLIGHT";
        String legacyWeather = rules + ".RULE_WEATHER_" + "CYCLE";
        if (genericGameRules) {
            assertTrue(advance.contains(modernTime));
            assertTrue(advance.contains(modernWeather));
        } else {
            assertTrue(advance.contains(legacyTime));
            assertTrue(advance.contains(legacyWeather));
        }
        assertEquals(worldClockApi ? 1 : 0,
                SourceScan.countOccurrences(manager, "import net.minecraft.world.clock.WorldClock;"));
        assertEquals(worldClockApi ? 1 : 0,
                SourceScan.countOccurrences(manager, "import net.minecraft.world.clock.ClockTimeMarkers;"));
        assertEquals(worldClockApi ? 1 : 0,
                SourceScan.countOccurrences(manager,
                        "import net.neoforged.neoforge.common.util.ClockAdjustment;"));
        if (worldClockApi) {
            assertTrue(SourceScan.containsInOrder(
                    advance,
                    "level.dimensionType().defaultClock()",
                    "EventHooks.onSleepFinished",
                    "new ClockAdjustment.Marker(ClockTimeMarkers.NIGHT)",
                    "adjustment.apply(level.clockManager(), defaultClock.get())"));
            assertFalse(advance.contains("level.getDayTime()"));
            assertFalse(advance.contains("level.setDayTime("));
            assertFalse(advance.contains("level.dimensionType().hasFixedTime()"));
        } else {
            assertTrue(SourceScan.containsInOrder(
                    advance,
                    "level.dimensionType().hasFixedTime()",
                    "long current = level.getDayTime();",
                    "long target = nextLegacyNight(current);",
                    "EventHooks.onSleepFinished(level, target, current)",
                    "level.setDayTime(adjusted)"));
            assertFalse(advance.contains("WorldClock"));
            assertFalse(advance.contains("ClockAdjustment"));
            assertFalse(advance.contains("defaultClock()"));
            assertFalse(advance.contains("clockManager()"));

            String nextNight = SourceScan.compact(
                    SourceScan.methodBody(manager, "private static long nextLegacyNight"));
            assertTrue(nextNight.contains("Math.floorMod(current,24000L)"));
            assertTrue(nextNight.contains("LEGACY_NIGHT_TICK"));
            assertTrue(nextNight.contains("target<=current?target+24000L:target"),
                    "the legacy target must always select the next reachable night");
        }
        assertFalse(manager.contains("Services.SLEEP_CLOCK")
                        || manager.contains("SleepClockService")
                        || manager.contains("@SuppressWarnings"),
                "the clock bridge must stay local and fully typed");

        String block = SourceScan.stripComments(rawBlock);
        String canRest = SourceScan.methodBody(block, "private static boolean canRestToNight");
        assertTrue(canRest.contains("Math.floorMod("));
        assertTrue(canRest.contains("clockTime < DAY_END_TICK"));
        if (worldClockApi) {
            assertTrue(canRest.contains("level.dimensionType().defaultClock().isEmpty()"));
            assertTrue(canRest.contains("level.getDefaultClockTime()"));
            assertFalse(canRest.contains("level.getDayTime()"));
            assertFalse(canRest.contains("level.dimensionType().hasFixedTime()"));
        } else {
            assertTrue(canRest.contains("level.dimensionType().hasFixedTime()"));
            assertTrue(canRest.contains("level.getDayTime()"));
            assertFalse(canRest.contains("defaultClock()"));
            assertFalse(canRest.contains("getDefaultClockTime()"));
        }

        String fixture = SourceScan.stripComments(rawFixture);
        String vote = SourceScan.methodBody(
                fixture, "static void coffinSleepVoteAdvancesAndWakesAll");
        String timeout = SourceScan.methodBody(
                fixture, "static void coffinSleepTimeoutWakesWithoutSkip");
        assertTrue(vote.contains("CoffinSleepWorldState.capture(helper)"));
        assertTrue(vote.contains("worldState.prepare(helper)"));
        assertTrue(vote.contains("worldState.isNight(level)"));
        assertTrue(vote.contains("worldState.currentClockTicks(level) != COFFIN_TEST_DAY_CLOCK"));
        assertTrue(timeout.contains("CoffinSleepWorldState.capture(helper)"));
        assertTrue(timeout.contains("worldState.prepare(helper)"));
        assertEquals(3, SourceScan.countOccurrences(
                timeout, "assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK"),
                "the timeout fixture must retain all three no-time-skip checkpoints");
        String cleanup = SourceScan.methodBody(fixture, "private static void cleanupCoffinSleepTest");
        assertTrue(cleanup.contains("worldState.restore(level)"),
                "both clock GameTests must restore the captured fixture state");
        assertFalse(fixture.contains("Assumptions.")
                        || fixture.contains("@Disabled")
                        || fixture.contains("helper.succeedIf("),
                "the clock bridge must not shrink or skip the required GameTest surface");

        String currentClock = SourceScan.methodBody(fixture, "long currentClockTicks");
        String setClock = SourceScan.methodBody(fixture, "void setClockTicks");
        String nightClock = SourceScan.methodBody(fixture, "boolean isNight");
        if (worldClockApi) {
            assertTrue(currentClock.contains("level.clockManager().getTotalTicks(clock)"));
            assertTrue(setClock.contains("level.clockManager().setTotalTicks(clock, ticks)"));
            assertTrue(nightClock.contains(
                    "level.clockManager().isAtTimeMarker(clock, ClockTimeMarkers.NIGHT)"));
            assertEquals(1, SourceScan.countOccurrences(
                    fixture, "import net.minecraft.world.clock.WorldClock;"));
        } else {
            assertTrue(currentClock.contains("level.getDayTime()"));
            assertTrue(setClock.contains("level.setDayTime(ticks)"));
            assertTrue(nightClock.contains(
                    "Math.floorMod(level.getDayTime(), 24000L) == COFFIN_TEST_NIGHT_CLOCK"));
            assertFalse(fixture.contains("WorldClock"));
            assertFalse(fixture.contains("ClockTimeMarkers"));
            assertFalse(fixture.contains("clockManager()"));
        }
        String capture = SourceScan.methodBody(
                fixture, "static CoffinSleepWorldState capture");
        String restore = SourceScan.methodBody(fixture, "void restore");
        String restored = SourceScan.methodBody(fixture, "void assertRestored");
        assertTrue(capture.contains("level.getGameTime()"),
                "the fixture must capture gameTime separately from its day clock");
        assertTrue(restore.contains("setGameTime(level, gameTime)"));
        assertTrue(restore.contains("setClockTicks(level, clockTicks)"));
        assertTrue(restored.contains("level.getGameTime() == gameTime"));
        assertTrue(restored.contains("assertClockTicks(helper, this, clockTicks,"));

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("WorldClock")
                        || controller.contains("ClockAdjustment")
                        || controller.contains("getDayTime")
                        || controller.contains("setDayTime")
                        || controller.contains("defaultClock"),
                "clock adaptation must not use a global replacement");
    }

    @Test
    void monsterModelBoundaryKeepsTypedOfficialAdultAndBabyLayers() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean splitModelClasses = Set.of("26.2.x", "26.1.x").contains(executingNode);
        boolean nestedModelPackages = !Set.of("1.21.10", "1.21.8").contains(executingNode);

        String rawVisuals = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/client/ZombiePlayerVisuals.java"));
        assertTrue(rawVisuals.contains("//? if >=26.1") && rawVisuals.contains("//? if <26.1"),
                "the split-vs-unified model classes must use a local boundary");
        assertTrue(rawVisuals.contains("//? if >=1.21.11") && rawVisuals.contains("//? if <1.21.11"),
                "the nested-vs-flat model packages must use a local boundary");

        String visuals = SourceScan.stripComments(rawVisuals);
        String nestedZombieImport =
                "import net.minecraft.client.model.monster.zombie.ZombieModel;";
        String flatZombieImport =
                "import net.minecraft.client.model.ZombieModel;";
        String nestedDrownedImport =
                "import net.minecraft.client.model.monster.zombie.DrownedModel;";
        String flatDrownedImport =
                "import net.minecraft.client.model.DrownedModel;";
        String nestedPiglinImport =
                "import net.minecraft.client.model.monster.piglin.ZombifiedPiglinModel;";
        String flatPiglinImport =
                "import net.minecraft.client.model.ZombifiedPiglinModel;";
        List<String> nestedModelImports =
                List.of(nestedZombieImport, nestedDrownedImport, nestedPiglinImport);
        List<String> flatModelImports =
                List.of(flatZombieImport, flatDrownedImport, flatPiglinImport);
        List<String> splitModelImports = List.of(
                "import net.minecraft.client.model.monster.piglin.AdultZombifiedPiglinModel;",
                "import net.minecraft.client.model.monster.piglin.BabyZombifiedPiglinModel;",
                "import net.minecraft.client.model.monster.zombie.BabyDrownedModel;",
                "import net.minecraft.client.model.monster.zombie.BabyZombieModel;");
        for (String modelImport : nestedModelImports) {
            assertEquals(1, SourceScan.countOccurrences(rawVisuals, modelImport),
                    "canonical source must retain nested import " + modelImport);
            assertEquals(nestedModelPackages ? 1 : 0,
                    SourceScan.countOccurrences(visuals, modelImport));
        }
        for (String modelImport : flatModelImports) {
            assertEquals(1, SourceScan.countOccurrences(rawVisuals, modelImport),
                    "canonical source must retain flat import " + modelImport);
            assertEquals(nestedModelPackages ? 0 : 1,
                    SourceScan.countOccurrences(visuals, modelImport));
        }
        for (String splitImport : splitModelImports) {
            assertEquals(1, SourceScan.countOccurrences(rawVisuals, splitImport),
                    "canonical source must retain split import " + splitImport);
            assertEquals(splitModelClasses ? 1 : 0,
                    SourceScan.countOccurrences(visuals, splitImport));
        }

        String factory = SourceScan.compact(
                SourceScan.methodBody(visuals, "private static MonsterModels createMonsterModels"));
        String rawFactory = SourceScan.compact(
                SourceScan.methodBody(rawVisuals, "private static MonsterModels createMonsterModels"));
        for (String babyLayer : List.of(
                "ModelLayers.ZOMBIE_BABY",
                "ModelLayers.DROWNED_BABY",
                "ModelLayers.HUSK_BABY",
                "ModelLayers.ZOMBIFIED_PIGLIN_BABY")) {
            assertEquals(1, SourceScan.countOccurrences(
                            factory, "entityModels.bakeLayer(" + babyLayer + ")"),
                    "every node must bake the official " + babyLayer + " body layer");
        }
        List<String> splitConstructors = List.of(
                "new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY))",
                "new BabyDrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED_BABY))",
                "new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK_BABY))",
                "new AdultZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN))",
                "new BabyZombifiedPiglinModel(entityModels.bakeLayer("
                        + "ModelLayers.ZOMBIFIED_PIGLIN_BABY))");
        List<String> unifiedConstructors = List.of(
                "new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY))",
                "new DrownedModel(entityModels.bakeLayer(ModelLayers.DROWNED_BABY))",
                "new ZombieModel<>(entityModels.bakeLayer(ModelLayers.HUSK_BABY))",
                "new ZombifiedPiglinModel(entityModels.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN))",
                "new ZombifiedPiglinModel(entityModels.bakeLayer("
                        + "ModelLayers.ZOMBIFIED_PIGLIN_BABY))");
        for (String constructor : splitConstructors) {
            assertEquals(1, SourceScan.countOccurrences(
                    rawFactory, SourceScan.compact(constructor)));
        }
        for (String constructor : unifiedConstructors) {
            assertEquals(1, SourceScan.countOccurrences(
                    rawFactory, SourceScan.compact(constructor)));
        }
        if (splitModelClasses) {
            for (String splitConstructor : splitConstructors) {
                assertEquals(1, SourceScan.countOccurrences(factory, SourceScan.compact(splitConstructor)));
            }
        } else {
            for (String unifiedConstructor : unifiedConstructors) {
                assertEquals(1, SourceScan.countOccurrences(factory, SourceScan.compact(unifiedConstructor)));
            }
        }

        String layerSet = SourceScan.compact(
                SourceScan.methodBody(visuals, "private static MonsterLayerSet layerSet"));
        String rawLayerSet = SourceScan.compact(
                SourceScan.methodBody(rawVisuals, "private static MonsterLayerSet layerSet"));
        String splitBabyParent =
                "new BabyZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY))";
        String unifiedBabyParent =
                "new ZombieModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE_BABY))";
        assertEquals(1, SourceScan.countOccurrences(
                rawLayerSet, SourceScan.compact(splitBabyParent)));
        assertEquals(1, SourceScan.countOccurrences(
                rawLayerSet, SourceScan.compact(unifiedBabyParent)));
        assertEquals(splitModelClasses ? 1 : 0,
                SourceScan.countOccurrences(layerSet, SourceScan.compact(splitBabyParent)));
        assertEquals(splitModelClasses ? 0 : 1,
                SourceScan.countOccurrences(layerSet, SourceScan.compact(unifiedBabyParent)));

        String modelRecord = SourceScan.methodBody(visuals, "private record MonsterModels");
        for (String typedField : List.of(
                "ZombieModel<ZombieRenderState> normal",
                "ZombieModel<ZombieRenderState> babyNormal",
                "DrownedModel drowned",
                "DrownedModel babyDrowned",
                "ZombieModel<ZombieRenderState> husk",
                "ZombieModel<ZombieRenderState> babyHusk",
                "ZombifiedPiglinModel zombifiedPiglin",
                "ZombifiedPiglinModel babyZombifiedPiglin")) {
            assertEquals(1, SourceScan.countOccurrences(modelRecord, typedField));
        }
        String typedSurface = factory + layerSet + modelRecord;
        assertFalse(typedSurface.contains("@SuppressWarnings")
                        || typedSurface.contains("Class.cast(")
                        || typedSurface.contains("Object "),
                "the model bridge must remain fully typed without raw or unchecked escape hatches");

        String rawGeometry = Files.readString(
                TEST_JAVA.resolve("dev/molang/iamzombieq/client/FirstPersonArmGeometryAlignmentTest.java"));
        assertTrue(rawGeometry.contains("//? if >=26.1") && rawGeometry.contains("//?} else {"),
                "the geometry regression must construct node-native official models");
        assertTrue(rawGeometry.contains("//? if >=1.21.11") && rawGeometry.contains("//? if <1.21.11"),
                "the geometry regression must use the node-native model packages");
        String geometry = SourceScan.stripComments(rawGeometry);
        List<String> nestedGeometryImports = List.of(
                nestedZombieImport,
                nestedDrownedImport,
                nestedPiglinImport,
                "import net.minecraft.client.model.monster.piglin.PiglinModel;",
                "import net.minecraft.client.model.player.PlayerModel;");
        List<String> flatGeometryImports = List.of(
                flatZombieImport,
                flatDrownedImport,
                flatPiglinImport,
                "import net.minecraft.client.model.PiglinModel;",
                "import net.minecraft.client.model.PlayerModel;");
        for (String modelImport : nestedGeometryImports) {
            assertEquals(1, SourceScan.countOccurrences(rawGeometry, modelImport));
            assertEquals(nestedModelPackages ? 1 : 0,
                    SourceScan.countOccurrences(geometry, modelImport));
        }
        for (String modelImport : flatGeometryImports) {
            assertEquals(1, SourceScan.countOccurrences(rawGeometry, modelImport));
            assertEquals(nestedModelPackages ? 0 : 1,
                    SourceScan.countOccurrences(geometry, modelImport));
        }
        for (String splitImport : splitModelImports) {
            assertEquals(1, SourceScan.countOccurrences(rawGeometry, splitImport));
            assertEquals(splitModelClasses ? 1 : 0,
                    SourceScan.countOccurrences(geometry, splitImport));
        }
        String cases = SourceScan.methodBody(geometry, "private static Stream<Arguments> officialArmCases");
        for (String axis : List.of(
                "PlayerShape.values()",
                "MonsterShape.values()",
                "new boolean[]{false, true}",
                "HumanoidArm.values()")) {
            assertEquals(1, SourceScan.countOccurrences(cases, axis),
                    "the 2x4x2x2 official arm matrix must retain axis " + axis);
        }
        assertFalse(geometry.contains("@Disabled")
                        || geometry.contains("Assumptions.")
                        || geometry.contains("@SuppressWarnings"),
                "the 32-case geometry regression must not be skipped or weakened");
        assertTrue(geometry.contains(
                "assertEquals(32L, officialArmCases().count())"),
                "the test suite must count the complete 2x4x2x2 matrix directly");
        String geometryFactory = SourceScan.compact(
                SourceScan.methodBody(geometry, "private static HumanoidModel<?> monsterModel"));
        String rawGeometryFactory = SourceScan.compact(
                SourceScan.methodBody(rawGeometry, "private static HumanoidModel<?> monsterModel"));
        String splitBabyZombieGeometry =
                "new BabyZombieModel<ZombieRenderState>("
                        + "BabyZombieModel.createBodyLayer(CubeDeformation.NONE).bakeRoot())";
        List<String> splitGeometryConstructors = List.of(
                "new BabyDrownedModel("
                        + "BabyDrownedModel.createBodyLayer(CubeDeformation.NONE).bakeRoot())",
                "new BabyZombifiedPiglinModel("
                        + "BabyZombifiedPiglinModel.createBodyLayer().bakeRoot())",
                "new AdultZombifiedPiglinModel("
                        + "AdultZombifiedPiglinModel.createBodyLayer().bakeRoot())");
        List<String> unifiedGeometryConstructors = List.of(
                "new ZombieModel<ZombieRenderState>("
                        + "humanoidBodyLayer().apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot())",
                "new DrownedModel(DrownedModel.createBodyLayer(CubeDeformation.NONE)"
                        + ".apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot())",
                "new ZombieModel<ZombieRenderState>("
                        + "humanoidBodyLayer().apply(HumanoidModel.BABY_TRANSFORMER)"
                        + ".apply(MeshTransformer.scaling(1.0625F)).bakeRoot())",
                "new ZombifiedPiglinModel("
                        + "LayerDefinition.create(PiglinModel.createMesh(CubeDeformation.NONE),64,64)"
                        + ".apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot())",
                "new ZombifiedPiglinModel("
                        + "LayerDefinition.create(PiglinModel.createMesh(CubeDeformation.NONE),64,64)"
                        + ".bakeRoot())");
        assertEquals(2, SourceScan.countOccurrences(
                rawGeometryFactory, SourceScan.compact(splitBabyZombieGeometry)));
        for (String constructor : splitGeometryConstructors) {
            assertEquals(1, SourceScan.countOccurrences(
                    rawGeometryFactory, SourceScan.compact(constructor)));
        }
        for (String constructor : unifiedGeometryConstructors) {
            assertEquals(1, SourceScan.countOccurrences(
                    rawGeometryFactory, SourceScan.compact(constructor)));
        }
        assertEquals(splitModelClasses ? 0 : 4,
                SourceScan.countOccurrences(geometryFactory, "HumanoidModel.BABY_TRANSFORMER"),
                "unified model nodes must reproduce all four registered baby layer transforms");
        if (splitModelClasses) {
            assertEquals(2, SourceScan.countOccurrences(
                    geometryFactory, SourceScan.compact(splitBabyZombieGeometry)));
            for (String constructor : splitGeometryConstructors) {
                assertEquals(1, SourceScan.countOccurrences(
                        geometryFactory, SourceScan.compact(constructor)));
            }
            for (String splitClass : List.of(
                    "BabyZombieModel", "BabyDrownedModel",
                    "AdultZombifiedPiglinModel", "BabyZombifiedPiglinModel")) {
                assertTrue(geometryFactory.contains(splitClass));
            }
        } else {
            for (String constructor : unifiedGeometryConstructors) {
                assertEquals(1, SourceScan.countOccurrences(
                        geometryFactory, SourceScan.compact(constructor)));
            }
            assertTrue(geometryFactory.contains("newZombieModel<ZombieRenderState>"));
            assertTrue(geometryFactory.contains("newDrownedModel"));
            assertTrue(geometryFactory.contains("newZombifiedPiglinModel"));
            assertFalse(geometryFactory.contains("BabyZombieModel")
                            || geometryFactory.contains("BabyDrownedModel")
                            || geometryFactory.contains("AdultZombifiedPiglinModel")
                            || geometryFactory.contains("BabyZombifiedPiglinModel"));
        }

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("BabyZombieModel")
                        || controller.contains("BabyDrownedModel")
                        || controller.contains("ZombifiedPiglinModel")
                        || controller.contains("net.minecraft.client.model.monster")
                        || controller.contains("net.minecraft.client.model.player"),
                "model adaptation must not use global replacements");
    }

    @Test
    void enchantedLootObservationBoundaryPreservesTheActualNodeQueryPath() throws IOException {
        String source = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest/MountedZombieKillCreditGameTest.java"));
        String activeSource = SourceScan.stripComments(source);
        String compactSource = SourceScan.compact(source);
        String activeCompactSource = SourceScan.compact(activeSource);
        String contextualImport =
                "import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;";
        String legacyImport =
                "import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;";
        String contextualHandler =
                "public void onEnchantedLoot(EnchantedEntityLootEvent event)";
        String legacyHandler =
                "public void onLegacyEnchantmentLevel(GetEnchantmentLevelEvent event)";

        assertEquals(1, SourceScan.countOccurrences(source, contextualImport));
        assertEquals(1, SourceScan.countOccurrences(source, legacyImport));
        assertEquals(1, SourceScan.countOccurrences(source, contextualHandler));
        assertEquals(1, SourceScan.countOccurrences(source, legacyHandler));
        assertTrue(compactSource.contains(
                "matches(event.getEntity())&&event.getEnchantment().is(Enchantments.LOOTING)"));
        assertTrue(compactSource.contains("event.getEnchantmentLevel()"));
        assertTrue(compactSource.contains("event.getStack()==active.lootingStack"));
        assertTrue(compactSource.contains("target!=null&&target.is(Enchantments.LOOTING)"));
        assertTrue(compactSource.contains("event.getEnchantments().getLevel(target)"));
        assertEquals(1, SourceScan.countOccurrences(
                source, "void begin(Entity victim, ItemStack lootingStack)"));
        assertEquals(1, SourceScan.countOccurrences(source, "ItemStack lootingStack;"));

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean contextualApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        assertEquals(contextualApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource, contextualImport));
        assertEquals(contextualApi ? 0 : 1,
                SourceScan.countOccurrences(activeSource, legacyImport));
        assertEquals(contextualApi ? 1 : 0,
                SourceScan.countOccurrences(activeSource, contextualHandler));
        assertEquals(contextualApi ? 0 : 1,
                SourceScan.countOccurrences(activeSource, legacyHandler));
        if (contextualApi) {
            assertTrue(activeCompactSource.contains(
                    "matches(event.getEntity())&&event.getEnchantment().is(Enchantments.LOOTING)"));
            assertTrue(activeCompactSource.contains("event.getEnchantmentLevel()"));
            assertFalse(activeCompactSource.contains("event.getStack()==active.lootingStack"));
        } else {
            assertTrue(activeCompactSource.contains("event.getStack()==active.lootingStack"));
            assertTrue(activeCompactSource.contains(
                    "target!=null&&target.is(Enchantments.LOOTING)"));
            assertTrue(activeCompactSource.contains("event.getEnchantments().getLevel(target)"));
            assertFalse(activeCompactSource.contains("event.getEnchantmentLevel()"));
        }

        String rawKill = SourceScan.methodBody(source, "private static void verifyIndependentMountedKill");
        String activeKill = SourceScan.stripComments(rawKill);
        String contextualBegin = "observer.begin(victim);";
        String legacyBegin = "observer.begin(victim, rider.getMainHandItem());";
        assertEquals(1, SourceScan.countOccurrences(rawKill, contextualBegin));
        assertEquals(1, SourceScan.countOccurrences(rawKill, legacyBegin));
        assertEquals(contextualApi ? 1 : 0,
                SourceScan.countOccurrences(activeKill, contextualBegin));
        assertEquals(contextualApi ? 0 : 1,
                SourceScan.countOccurrences(activeKill, legacyBegin));
        assertEquals(1, SourceScan.countOccurrences(
                activeKill,
                "result.lootingQueries > 0 && result.minLootingLevel == 3"
                        + " && result.maxLootingLevel == 3"));

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("replace(\"EnchantedEntityLootEvent"),
                "loot-query observation must use a local API seam");
        assertFalse(controller.contains("replace(\"GetEnchantmentLevelEvent"),
                "loot-query observation must not rely on a global event-name replacement");
    }

    @Test
    void gameTestMessagesUseTypedComponentAdapterAcrossAllFiveNodes()
            throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean nativeBooleanStringOverloads =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                nativeBooleanStringOverloads,
                StonecutterCapabilityMatrix.hasNativeGameTestBooleanStringAssertionOverloads(),
                "the centralized capability matrix must retain the >=1.21.11 String-overload boundary");

        Path gameTestRoot = MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest");
        Path assertionsPath = gameTestRoot.resolve("GameTestAssertions.java");
        assertTrue(Files.isRegularFile(assertionsPath),
                "GameTest assertion messages must route through one typed compatibility adapter");
        String assertions = Files.readString(assertionsPath);
        String assertTrueMethod = SourceScan.methodBody(
                assertions,
                "static void assertTrue(GameTestHelper delegate, boolean condition, String message)");
        String assertFalseMethod = SourceScan.methodBody(
                assertions,
                "static void assertFalse(GameTestHelper delegate, boolean condition, String message)");
        String failMethod = SourceScan.methodBody(
                assertions,
                "static void fail(GameTestHelper delegate, String message)");
        assertEquals(
                SourceScan.compact(
                        "static void assertTrue(GameTestHelper delegate, boolean condition, String message) {"
                                + " delegate.assertTrue(condition, Component.literal(message)); }"),
                SourceScan.compact(assertTrueMethod));
        assertEquals(
                SourceScan.compact(
                        "static void assertFalse(GameTestHelper delegate, boolean condition, String message) {"
                                + " delegate.assertFalse(condition, Component.literal(message)); }"),
                SourceScan.compact(assertFalseMethod));
        assertEquals(
                SourceScan.compact(
                        "static void fail(GameTestHelper delegate, String message) {"
                                + " delegate.fail(Component.literal(message)); }"),
                SourceScan.compact(failMethod));
        assertEquals(3, SourceScan.countOccurrences(assertions, "Component.literal(message)"),
                "all three message APIs must use the Component overload shared by all five nodes");
        assertEquals(1, SourceScan.countOccurrences(
                assertions, "final class GameTestAssertions {"));
        assertEquals(1, SourceScan.countOccurrences(
                assertions, "private GameTestAssertions() {"));
        assertFalse(assertions.contains("public class GameTestAssertions")
                        || assertions.contains("public final class GameTestAssertions"),
                "the compatibility adapter must not add public API or ABI");
        assertFalse(assertions.contains("public static void assertTrue")
                        || assertions.contains("protected static void assertTrue")
                        || assertions.contains("private static void assertTrue")
                        || assertions.contains("public static void assertFalse")
                        || assertions.contains("protected static void assertFalse")
                        || assertions.contains("private static void assertFalse")
                        || assertions.contains("public static void fail")
                        || assertions.contains("protected static void fail")
                        || assertions.contains("private static void fail"),
                "all adapter methods must remain package-private");
        assertEquals(0, SourceScan.countOccurrences(assertions, "//?"),
                "the shared typed adapter must not duplicate the node boundary in production source");
        assertFalse(assertions.contains("reflect")
                        || assertions.contains("Class.forName")
                        || assertions.contains("assertionException")
                        || assertions.contains("translatable"),
                "the adapter must remain typed and preserve literal-message semantics");

        record AssertionCallsite(Path path, int trueCalls, int falseCalls) {}
        List<AssertionCallsite> expectedCallsites = List.of(
                new AssertionCallsite(Path.of("GameTestPlayers.java"), 3, 5),
                new AssertionCallsite(Path.of("IAmZombieDisguiseGameTestBodies.java"), 36, 4),
                new AssertionCallsite(Path.of("IAmZombieFormGameTestBodies.java"), 145, 24),
                new AssertionCallsite(Path.of("IAmZombieHerobrineGameTestBodies.java"), 56, 9),
                new AssertionCallsite(Path.of("IAmZombieMobSleepGameTestBodies.java"), 39, 10),
                new AssertionCallsite(Path.of("IAmZombieMountGameTestBodies.java"), 6, 1));
        Set<Path> expectedFiles = expectedCallsites.stream()
                .map(AssertionCallsite::path)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Path> observedFiles = new HashSet<>();
        int rawTrueCalls = 0;
        int rawFalseCalls = 0;
        int activeTrueCalls = 0;
        int activeFalseCalls = 0;
        int directTrueCalls = 0;
        int directFalseCalls = 0;
        boolean dimensionWorldClockApi =
                Set.of("26.2.x", "26.1.x").contains(executingNode);
        for (AssertionCallsite expected : expectedCallsites) {
            String source = Files.readString(gameTestRoot.resolve(expected.path()));
            String activeSource = SourceScan.stripComments(source);
            int sourceTrueCalls =
                    SourceScan.countOccurrences(source, "GameTestAssertions.assertTrue(helper,");
            int sourceFalseCalls =
                    SourceScan.countOccurrences(source, "GameTestAssertions.assertFalse(helper,");
            assertEquals(expected.trueCalls(), sourceTrueCalls, expected.path().toString());
            assertEquals(expected.falseCalls(), sourceFalseCalls, expected.path().toString());
            rawTrueCalls += sourceTrueCalls;
            rawFalseCalls += sourceFalseCalls;
            activeTrueCalls += SourceScan.countOccurrences(
                    activeSource, "GameTestAssertions.assertTrue(helper,");
            activeFalseCalls += SourceScan.countOccurrences(
                    activeSource, "GameTestAssertions.assertFalse(helper,");
            boolean coffinSleepSource =
                    expected.path().equals(Path.of("IAmZombieMobSleepGameTestBodies.java"));
            boolean herobrinePacketSource =
                    expected.path().equals(Path.of("IAmZombieHerobrineGameTestBodies.java"));
            assertEquals(
                    coffinSleepSource ? 37 : herobrinePacketSource ? 52 : expected.trueCalls(),
                    SourceScan.countOccurrences(
                            activeSource, "GameTestAssertions.assertTrue(helper,"),
                    expected.path() + " active assertTrue inventory");
            assertEquals(
                    coffinSleepSource && dimensionWorldClockApi ? 9 : expected.falseCalls(),
                    SourceScan.countOccurrences(
                            activeSource, "GameTestAssertions.assertFalse(helper,"),
                    expected.path() + " active assertFalse inventory");
            directTrueCalls += SourceScan.countOccurrences(source, "helper.assertTrue(");
            directFalseCalls += SourceScan.countOccurrences(source, "helper.assertFalse(");
            if (sourceTrueCalls + sourceFalseCalls > 0) {
                observedFiles.add(expected.path());
            }
        }
        assertEquals(expectedFiles, observedFiles,
                "the adapter must cover the exact six canonical GameTest sources");
        assertEquals(285, rawTrueCalls);
        assertEquals(53, rawFalseCalls);
        assertEquals(279, activeTrueCalls,
                "inactive PlayerList and merged-interaction alternatives must remain comment-only");
        int expectedActiveFalseCalls = dimensionWorldClockApi ? 52 : 53;
        assertEquals(expectedActiveFalseCalls, activeFalseCalls,
                "only nodes with the dimension WorldClock API omit the legacy fixed-time assertion");
        assertEquals(0, directTrueCalls);
        assertEquals(0, directFalseCalls);

        int unexpectedAdapterCalls = 0;
        int directCallsOutsideInventory = 0;
        Map<Path, Integer> expectedFailCallsites = Map.ofEntries(
                Map.entry(Path.of("IAmZombieDisguiseGameTestBodies.java"), 2),
                Map.entry(Path.of("IAmZombieFixRegressionGameTestBodies.java"), 16),
                Map.entry(Path.of("IAmZombieFoodInfGameTestBodies.java"), 7),
                Map.entry(Path.of("IAmZombieFormGameTestBodies.java"), 18),
                Map.entry(Path.of("IAmZombieGameTestBodies.java"), 18),
                Map.entry(Path.of("IAmZombieGiantSunGameTestBodies.java"), 8),
                Map.entry(Path.of("IAmZombieHerobrineGameTestBodies.java"), 24),
                Map.entry(Path.of("IAmZombieMobSleepGameTestBodies.java"), 23),
                Map.entry(Path.of("IAmZombieMountGameTestBodies.java"), 27),
                Map.entry(Path.of("MountedZombieKillCreditGameTest.java"), 1));
        Set<Path> observedFailCallsites = new HashSet<>();
        int directFailCalls = 0;
        int literalStringFailCalls = 0;
        int dynamicStringFailCalls = 0;
        int literalWrappedFailCalls = 0;
        int fullyQualifiedLiteralWrappedFailCalls = 0;
        int adapterFailCalls = 0;
        int activeAdapterFailCalls = 0;
        int rawAssertionExceptionCalls = 0;
        int adapterAssertionExceptionCalls = 0;
        try (var paths = Files.walk(gameTestRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Path relative = gameTestRoot.relativize(path);
                String source = Files.readString(path);
                String activeSource = SourceScan.stripComments(source);
                directFailCalls += SourceScan.countOccurrences(source, "helper.fail(");
                literalStringFailCalls +=
                        SourceScan.countOccurrences(source, "GameTestAssertions.fail(helper, \"");
                dynamicStringFailCalls +=
                        SourceScan.countOccurrences(source, "GameTestAssertions.fail(helper, form +");
                literalWrappedFailCalls +=
                        SourceScan.countOccurrences(source, "helper.fail(Component.literal(");
                fullyQualifiedLiteralWrappedFailCalls += SourceScan.countOccurrences(
                        source, "helper.fail(net.minecraft.network.chat.Component.literal(");
                int sourceAdapterFailCalls =
                        SourceScan.countOccurrences(source, "GameTestAssertions.fail(helper,");
                adapterFailCalls += sourceAdapterFailCalls;
                activeAdapterFailCalls +=
                        SourceScan.countOccurrences(activeSource, "GameTestAssertions.fail(helper,");
                if (sourceAdapterFailCalls > 0) {
                    assertTrue(expectedFailCallsites.containsKey(relative),
                            "unexpected fail adapter callsite: " + relative);
                    observedFailCallsites.add(relative);
                    assertEquals(expectedFailCallsites.get(relative).intValue(),
                            sourceAdapterFailCalls, relative.toString());
                }
                rawAssertionExceptionCalls +=
                        SourceScan.countOccurrences(source, "helper.assertionException(");
                adapterAssertionExceptionCalls += SourceScan.countOccurrences(
                        source, "GameTestAssertions.assertionException(");
                if (!expectedFiles.contains(relative)) {
                    unexpectedAdapterCalls += SourceScan.countOccurrences(
                            source, "GameTestAssertions.assertTrue(");
                    unexpectedAdapterCalls += SourceScan.countOccurrences(
                            source, "GameTestAssertions.assertFalse(");
                    directCallsOutsideInventory +=
                            SourceScan.countOccurrences(source, "helper.assertTrue(");
                    directCallsOutsideInventory +=
                            SourceScan.countOccurrences(source, "helper.assertFalse(");
                }
            }
        }
        assertEquals(0, unexpectedAdapterCalls,
                "no unrecorded source may join the assertion-message API family");
        assertEquals(0, directCallsOutsideInventory,
                "all direct GameTestHelper assertion calls must belong to the exact migrated inventory");
        assertEquals(expectedFailCallsites.keySet(), observedFailCallsites,
                "the fail adapter must cover the exact ten canonical GameTest sources");
        assertEquals(0, directFailCalls,
                "no direct GameTestHelper.fail(String) call may bypass the shared Component adapter");
        assertEquals(143, literalStringFailCalls);
        assertEquals(1, dynamicStringFailCalls);
        assertEquals(0, literalWrappedFailCalls,
                "this root must not preemptively adapt the independent fail(String) boundary");
        assertEquals(0, fullyQualifiedLiteralWrappedFailCalls,
                "this root must not import the old port's fully-qualified fail adaptation");
        assertEquals(144, adapterFailCalls);
        assertEquals(StonecutterCapabilityMatrix.hasNautilusEntityApi() ? 144 : 140,
                activeAdapterFailCalls,
                "only the four Nautilus fixture failures may be inactive on 1.21.8");
        assertEquals(12, rawAssertionExceptionCalls,
                "the independent assertionException API surface must remain untouched");
        assertEquals(0, adapterAssertionExceptionCalls,
                "the boolean assertion adapter must never absorb assertionException calls");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("GameTestAssertions")
                        || controller.contains("helper.assertTrue")
                        || controller.contains("helper.assertFalse")
                        || controller.contains("helper.fail"),
                "assertion messages must not use a global source replacement");
    }

    @Test
    void gameTestPlayerLoadedStateUsesOneTypedOwnerBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean connectionOwnedState =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                connectionOwnedState,
                StonecutterCapabilityMatrix.hasConnectionOwnedClientLoadedState(),
                "the centralized capability matrix must retain the >=1.21.11 ownership boundary");

        Path gameTestRoot = MAIN_JAVA.resolve("dev/molang/iamzombieq/gametest");
        Path playersPath = gameTestRoot.resolve("GameTestPlayers.java");
        String players = Files.readString(playersPath);
        String seam = SourceScan.methodBody(
                players,
                "static boolean hasClientLoaded(ServerGamePacketListenerImpl listener)");
        String activeSeam = SourceScan.stripComments(seam);
        assertEquals(1, SourceScan.countOccurrences(
                seam, "CROSS_VERSION-GAME-TEST-PLAYER-LOADED-API"));
        assertEquals(3, SourceScan.countOccurrences(seam, "//?"),
                "the one local seam must contain one complete if/else boundary");
        assertTrue(SourceScan.containsInOrder(
                        seam,
                        "//? if >=1.21.11 {",
                        "return listener.hasClientLoaded();",
                        "//?} else {",
                        "return listener.getPlayer().hasClientLoaded();",
                        "//?}"),
                "the raw seam must retain the exact >=1.21.11 owner boundary and branch order");
        assertEquals(1, SourceScan.countOccurrences(seam, "//? if >=1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(seam, "//?} else {"));
        assertEquals(2, SourceScan.countOccurrences(seam, "//?}"),
                "the else marker and final boundary marker must both remain present");
        assertEquals(1, SourceScan.countOccurrences(
                seam, "return listener.hasClientLoaded();"));
        assertEquals(1, SourceScan.countOccurrences(
                seam, "return listener.getPlayer().hasClientLoaded();"));
        assertEquals(connectionOwnedState ? 1 : 0, SourceScan.countOccurrences(
                activeSeam, "return listener.hasClientLoaded();"));
        assertEquals(connectionOwnedState ? 0 : 1, SourceScan.countOccurrences(
                activeSeam, "return listener.getPlayer().hasClientLoaded();"));
        assertEquals(
                connectionOwnedState
                        ? "staticbooleanhasClientLoaded(ServerGamePacketListenerImpllistener)"
                                + "{returnlistener.hasClientLoaded();}"
                        : "staticbooleanhasClientLoaded(ServerGamePacketListenerImpllistener)"
                                + "{returnlistener.getPlayer().hasClientLoaded();}",
                SourceScan.compact(activeSeam),
                "the active typed seam must contain exactly one node-native query and no side effect");
        assertEquals(1, SourceScan.countOccurrences(
                seam, "static boolean hasClientLoaded(ServerGamePacketListenerImpl listener)"));
        assertFalse(seam.contains("public static boolean hasClientLoaded")
                        || seam.contains("protected static boolean hasClientLoaded")
                        || seam.contains("private static boolean hasClientLoaded"),
                "the typed compatibility seam must remain package-private");
        assertFalse(seam.contains("reflect")
                        || seam.contains("Class." + "forName")
                        || seam.contains("Method" + "Handle")
                        || seam.contains("@Suppress" + "Warnings"),
                "the ownership boundary must remain typed and warning-free");

        record LoadedStateCallsite(Path path, int calls, int trueCalls, int falseCalls) {}
        List<LoadedStateCallsite> expectedCallsites = List.of(
                new LoadedStateCallsite(Path.of("GameTestPlayers.java"), 1, 1, 0),
                new LoadedStateCallsite(Path.of("IAmZombieFormGameTestBodies.java"), 5, 2, 3),
                new LoadedStateCallsite(Path.of("IAmZombieHerobrineGameTestBodies.java"), 2, 1, 1));
        Set<Path> expectedFiles = expectedCallsites.stream()
                .map(LoadedStateCallsite::path)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Path> observedFiles = new HashSet<>();
        int wrapperCalls = 0;
        int rawNativeCalls = 0;
        int activeNativeCalls = 0;
        int nativeCallsOutsideSeamFile = 0;
        int directStateWrites = 0;
        int playerLoadedPackets = 0;
        StringBuilder callsiteSources = new StringBuilder();
        try (var paths = Files.walk(gameTestRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Path relative = gameTestRoot.relativize(path);
                String source = Files.readString(path);
                String activeSource = SourceScan.stripComments(source);
                int sourceWrapperCalls =
                        SourceScan.countOccurrences(source, "GameTestPlayers.hasClientLoaded(");
                if (sourceWrapperCalls > 0) {
                    observedFiles.add(relative);
                    callsiteSources.append(source);
                }
                wrapperCalls += sourceWrapperCalls;
                rawNativeCalls += SourceScan.countOccurrences(source, ".hasClientLoaded()");
                activeNativeCalls +=
                        SourceScan.countOccurrences(activeSource, ".hasClientLoaded()");
                if (!relative.equals(Path.of("GameTestPlayers.java"))) {
                    nativeCallsOutsideSeamFile +=
                            SourceScan.countOccurrences(source, ".hasClientLoaded()");
                }
                directStateWrites += SourceScan.countOccurrences(source, ".setClientLoaded(")
                        + SourceScan.countOccurrences(source, ".markClientLoaded(")
                        + SourceScan.countOccurrences(source, ".markClientUnloadedAfterDeath(");
                playerLoadedPackets += SourceScan.countOccurrences(
                        source,
                        "handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket())");
            }
        }
        for (LoadedStateCallsite expected : expectedCallsites) {
            String source = Files.readString(gameTestRoot.resolve(expected.path()));
            String compactSource = SourceScan.compact(source);
            assertEquals(expected.calls(), SourceScan.countOccurrences(
                    source, "GameTestPlayers.hasClientLoaded("), expected.path().toString());
            assertEquals(expected.trueCalls(), SourceScan.countOccurrences(
                    compactSource,
                    "GameTestAssertions.assertTrue(helper,GameTestPlayers.hasClientLoaded("),
                    expected.path() + " true assertions");
            assertEquals(expected.falseCalls(), SourceScan.countOccurrences(
                    compactSource,
                    "GameTestAssertions.assertFalse(helper,GameTestPlayers.hasClientLoaded("),
                    expected.path() + " false assertions");
        }
        assertEquals(expectedFiles, observedFiles,
                "the compatibility seam must cover the exact three connected-fixture sources");
        assertEquals(8, wrapperCalls);
        assertEquals(2, rawNativeCalls,
                "only the two mutually exclusive seam branches may call a native ownership API");
        assertEquals(1, activeNativeCalls,
                "exactly one node-native ownership query must remain active");
        assertEquals(0, nativeCallsOutsideSeamFile);
        assertEquals(0, directStateWrites,
                "tests must drive the real PlayerLoaded packet handler instead of using either generation's writes");
        assertEquals(3, playerLoadedPackets,
                "all three real test-side PlayerLoaded handshakes must remain present");
        String compactCallsites = SourceScan.compact(callsiteSources.toString());
        assertEquals(4, SourceScan.countOccurrences(
                compactCallsites,
                "GameTestAssertions.assertTrue(helper,GameTestPlayers.hasClientLoaded("));
        assertEquals(4, SourceScan.countOccurrences(
                compactCallsites,
                "GameTestAssertions.assertFalse(helper,GameTestPlayers.hasClientLoaded("));

        String formRespawn = SourceScan.compact(SourceScan.methodBody(
                Files.readString(gameTestRoot.resolve("IAmZombieFormGameTestBodies.java")),
                "private static void runS1TransformPreCloneReset"));
        int formFalse = formRespawn.lastIndexOf(
                "GameTestAssertions.assertFalse(helper,GameTestPlayers.hasClientLoaded(listener),");
        int formPacket = formRespawn.indexOf(
                "listener.handleAcceptPlayerLoad(newServerboundPlayerLoadedPacket())",
                formFalse);
        int formTrue = formRespawn.indexOf(
                "GameTestAssertions.assertTrue(helper,GameTestPlayers.hasClientLoaded(listener),",
                formPacket);
        assertTrue(formFalse >= 0 && formFalse < formPacket && formPacket < formTrue,
                "clone respawn must observe false, process PlayerLoaded, then observe true");

        String herobrineRespawn = SourceScan.compact(SourceScan.methodBody(
                Files.readString(gameTestRoot.resolve("IAmZombieHerobrineGameTestBodies.java")),
                "static void herobrineLethalAttackRespawnsInPlace"));
        int herobrineFalse = herobrineRespawn.indexOf(
                "GameTestAssertions.assertFalse(helper,GameTestPlayers.hasClientLoaded(listener),");
        int herobrinePacket = herobrineRespawn.indexOf(
                "listener.handleAcceptPlayerLoad(newServerboundPlayerLoadedPacket())",
                herobrineFalse);
        int herobrineTrue = herobrineRespawn.indexOf(
                "GameTestAssertions.assertTrue(helper,GameTestPlayers.hasClientLoaded(listener),",
                herobrinePacket);
        assertTrue(
                herobrineFalse >= 0
                        && herobrineFalse < herobrinePacket
                        && herobrinePacket < herobrineTrue,
                "Herobrine respawn must observe false, process PlayerLoaded, then observe true");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("hasClientLoaded"),
                "the ownership API must use one local typed seam, not a global replacement");
    }

    @Test
    void persistentAngerTimerUsesExactNodeBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean endTimeApi =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                endTimeApi,
                StonecutterCapabilityMatrix.hasPersistentAngerEndTimeApi(),
                "the centralized capability matrix must retain the high3/low2 timer model");

        Path matrixPath =
                TEST_JAVA.resolve("dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>PERSISTENT_ANGER_END_TIME_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the absolute-end-time capability must remain high3/low2");
        assertTrue(compactMatrix.contains(
                        "returnPERSISTENT_ANGER_END_TIME_NODES.contains(nodeId());"),
                "the timer capability accessor must read the one recorded node set");

        Path targetingPath =
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java");
        String targeting = Files.readString(targetingPath);
        String marker = "CROSS_VERSION-PERSISTENT-ANGER-TIMER-API";
        String endTimeCall =
                "neutral.setPersistentAngerEndTime(NeutralMob.NO_ANGER_END_TIME);";
        String remainingTimeCall = "neutral.setRemainingPersistentAngerTime(0);";
        String method = SourceScan.methodBody(
                targeting,
                "public static void onChangeTarget(LivingChangeTargetEvent event)");
        String graceBlock = SourceScan.blockBody(
                method,
                "if (grace != null && grace.convertingPlayer().equals(player.getUUID()))");

        assertEquals(1, SourceScan.countOccurrences(targeting, marker),
                "the timer model boundary marker must exist exactly once in production");
        assertEquals(1, SourceScan.countOccurrences(graceBlock, marker),
                "the timer seam must remain in the conversion-grace branch");
        assertEquals(1, SourceScan.countOccurrences(graceBlock, "//? if <1.21.11 {"));
        assertEquals(0, SourceScan.countOccurrences(graceBlock, "//? if >=1.21.11 {"),
                "the local timer seam uses the low-first form");
        assertEquals(1, SourceScan.countOccurrences(graceBlock, "//?} else {"));
        assertEquals(1, SourceScan.countOccurrences(graceBlock, endTimeCall));
        assertEquals(1, SourceScan.countOccurrences(graceBlock, remainingTimeCall));
        assertEquals(1, SourceScan.countOccurrences(
                graceBlock, "neutral.setPersistentAngerTarget(null);"),
                "the adjacent target clear from the prior API root must remain exact");
        assertTrue(SourceScan.containsInOrder(
                        graceBlock,
                        "mob.setLastHurtByMob(null);",
                        "neutral.setPersistentAngerTarget(null);",
                        marker,
                        "//? if <1.21.11 {",
                        remainingTimeCall,
                        "//?} else {",
                        endTimeCall,
                        "//?}",
                        "event.setNewAboutToBeSetTarget(null);",
                        "return;"),
                "the timer clear must remain between target cleanup and event denial");

        String activeGraceBlock = SourceScan.stripComments(graceBlock);
        assertEquals(endTimeApi ? 1 : 0,
                SourceScan.countOccurrences(activeGraceBlock, endTimeCall));
        assertEquals(endTimeApi ? 0 : 1,
                SourceScan.countOccurrences(activeGraceBlock, remainingTimeCall));
        assertEquals(endTimeApi ? 1 : 0,
                SourceScan.countOccurrences(activeGraceBlock, "NeutralMob.NO_ANGER_END_TIME"));
        assertEquals(1, SourceScan.countOccurrences(
                activeGraceBlock, "neutral.setPersistentAngerTarget(null);"));
        Pattern forbiddenTimerBypass = Pattern.compile(
                "stopBeingAngry\\s*\\("
                        + "|setTimeToRemainAngry\\s*\\("
                        + "|setRemainingPersistentAngerTime\\s*\\(\\s*-1"
                        + "|setPersistentAngerEndTime\\s*\\(\\s*-1"
                        + "|Class\\s*\\.\\s*forName"
                        + "|java\\.lang\\.reflect"
                        + "|MethodHandles?"
                        + "|setAccessible\\s*\\("
                        + "|@Accessor"
                        + "|@Invoker"
                        + "|@SuppressWarnings"
                        + "|required\\s*=\\s*0");
        assertEquals(0, countMatches(forbiddenTimerBypass, graceBlock),
                "the seam must use only the two typed vanilla timer APIs");

        Pattern endTimeCallFamily =
                Pattern.compile("[.]setPersistentAngerEndTime\\s*\\(");
        Pattern remainingTimeCallFamily =
                Pattern.compile("[.]setRemainingPersistentAngerTime\\s*\\(");
        Set<Path> observedFiles = new HashSet<>();
        int rawMarkers = 0;
        int rawEndTimeCalls = 0;
        int rawRemainingTimeCalls = 0;
        int rawConstants = 0;
        int activeEndTimeCalls = 0;
        int activeRemainingTimeCalls = 0;
        int activeConstants = 0;
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                String activeSource = SourceScan.stripComments(source);
                int markers = SourceScan.countOccurrences(source, marker);
                int endCalls = countMatches(endTimeCallFamily, source);
                int remainingCalls = countMatches(remainingTimeCallFamily, source);
                int constants =
                        SourceScan.countOccurrences(source, "NeutralMob.NO_ANGER_END_TIME");
                if (markers + endCalls + remainingCalls + constants > 0) {
                    observedFiles.add(MAIN_JAVA.relativize(path));
                }
                rawMarkers += markers;
                rawEndTimeCalls += endCalls;
                rawRemainingTimeCalls += remainingCalls;
                rawConstants += constants;
                activeEndTimeCalls += countMatches(endTimeCallFamily, activeSource);
                activeRemainingTimeCalls += countMatches(remainingTimeCallFamily, activeSource);
                activeConstants +=
                        SourceScan.countOccurrences(activeSource, "NeutralMob.NO_ANGER_END_TIME");
            }
        }
        assertEquals(
                Set.of(Path.of(
                        "dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java")),
                observedFiles,
                "the timer model boundary must stay in one production file");
        assertEquals(1, rawMarkers);
        assertEquals(1, rawEndTimeCalls);
        assertEquals(1, rawRemainingTimeCalls);
        assertEquals(1, rawConstants);
        assertEquals(endTimeApi ? 1 : 0, activeEndTimeCalls);
        assertEquals(endTimeApi ? 0 : 1, activeRemainingTimeCalls);
        assertEquals(endTimeApi ? 1 : 0, activeConstants);

        String registrations = SourceScan.stripComments(Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/gametest/IAmZombieGameTests.java")));
        assertEquals(1, SourceScan.countOccurrences(
                registrations, "\"infection_piglin_sweep_grace\""),
                "the required real conversion-grace GameTest must remain registered");
        assertFalse(StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS
                        .contains("infection_piglin_sweep_grace"),
                "piglin conversion grace is never part of the Nautilus platform N/A");

        String gameTestBody = SourceScan.methodBody(
                Files.readString(MAIN_JAVA.resolve(
                        "dev/molang/iamzombieq/gametest/IAmZombieGameTestBodies.java")),
                "static void infectionPiglinSweepGrace(GameTestHelper helper)");
        String activeGameTestBody = SourceScan.stripComments(gameTestBody);
        assertEquals(2, SourceScan.countOccurrences(
                activeGameTestBody, "kin.setLastHurtByMob(player);"));
        assertEquals(1, countMatches(
                Pattern.compile("kin[.]setPersistentAngerTarget\\s*\\("),
                activeGameTestBody));
        assertEquals(1, SourceScan.countOccurrences(
                activeGameTestBody, "kin.startPersistentAngerTimer();"));
        assertEquals(3, SourceScan.countOccurrences(
                activeGameTestBody, "GameTestSeams.targetDenied(kin, player)"));
        assertEquals(1, SourceScan.countOccurrences(
                activeGameTestBody, "kin.getLastHurtByMob() != null"));
        assertEquals(1, SourceScan.countOccurrences(
                activeGameTestBody, "kin.isAngryAt(player, level)"));
        assertEquals(1, SourceScan.countOccurrences(
                activeGameTestBody, ".thenExecuteAfter(28,"));
        assertEquals(1, SourceScan.countOccurrences(
                activeGameTestBody, ".thenSucceed()"));
        assertTrue(SourceScan.containsInOrder(
                        SourceScan.compact(activeGameTestBody),
                        "kin.setLastHurtByMob(player);",
                        "kin.setPersistentAngerTarget(",
                        "kin.startPersistentAngerTimer();",
                        "GameTestSeams.targetDenied(kin,player)",
                        "kin.getLastHurtByMob()!=null",
                        "kin.isAngryAt(player,level)",
                        ".thenExecuteAfter(28,",
                        "GameTestSeams.targetDenied(kin,player)",
                        "kin.setLastHurtByMob(player);",
                        "GameTestSeams.targetDenied(kin,player)",
                        ".thenSucceed()"),
                "the required GameTest must retain its non-vacuous anger-clear progression");

        assertFalse(Files.exists(Path.of("src/main/resources/META-INF/accesstransformer.cfg")));
        String activeBuild = SourceScan.stripComments(Files.readString(Path.of("build.gradle")));
        assertEquals(0, SourceScan.countOccurrences(activeBuild, "accessTransformers"));
        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains(marker)
                        || controller.contains("setPersistentAngerEndTime")
                        || controller.contains("setRemainingPersistentAngerTime"),
                "the timer model boundary must remain a local typed seam");
    }

    @Test
    void zombifiedPiglinDefaultEquipmentUsesExactVisibilityBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean publicDefaultEquipmentApi =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                publicDefaultEquipmentApi,
                StonecutterCapabilityMatrix.hasPublicZombifiedPiglinDefaultEquipmentApi(),
                "the centralized capability matrix must retain the >=1.21.11 visibility boundary");

        Path matrixPath =
                TEST_JAVA.resolve("dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>"
                                + "PUBLIC_ZOMBIFIED_PIGLIN_DEFAULT_EQUIPMENT_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the public default-equipment capability must remain high3/low2");
        assertTrue(compactMatrix.contains(
                        "returnPUBLIC_ZOMBIFIED_PIGLIN_DEFAULT_EQUIPMENT_NODES"
                                + ".contains(nodeId());"),
                "the capability accessor must read the one recorded node set");

        Path infectionPath =
                MAIN_JAVA.resolve("dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");
        String infection = Files.readString(infectionPath);
        String marker = "CROSS_VERSION-ZOMBIFIED-PIGLIN-DEFAULT-EQUIPMENT-API";
        String nativeCall =
                "piglin.populateDefaultEquipmentSlots(victim.getRandom(),"
                        + " level.getCurrentDifficultyAt(piglin.blockPosition()));";
        String legacyAssignment =
                "piglin.setItemSlot(EquipmentSlot.MAINHAND,"
                        + " new ItemStack(Items.GOLDEN_SWORD));";
        String rawMethod = SourceScan.methodBody(
                infection, "private static boolean convertToZombifiedPiglin");

        assertEquals(1, SourceScan.countOccurrences(infection, marker),
                "the visibility boundary marker must exist exactly once in production");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, marker),
                "the marker must remain inside the one conversion callback");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//? if <1.21.11 {"));
        assertEquals(0, SourceScan.countOccurrences(rawMethod, "//? if >=1.21.11 {"),
                "the local API seam must not inflate the Nautilus high-boundary inventory");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "//?} else {"));
        assertEquals(1, SourceScan.countOccurrences(rawMethod, nativeCall),
                "canonical source must retain the node-native public call");
        assertEquals(1, SourceScan.countOccurrences(rawMethod, legacyAssignment),
                "canonical source must retain the exact protected legacy method body");
        assertTrue(SourceScan.containsInOrder(
                rawMethod,
                marker,
                "//? if <1.21.11 {",
                legacyAssignment,
                "//?} else {",
                nativeCall,
                "//?}",
                "piglin.setPersistenceRequired();",
                "EventHooks.onLivingConvert(victim, piglin);"),
                "the local equipment seam must remain before the unchanged conversion callbacks");

        String activeMethod = SourceScan.stripComments(rawMethod);
        assertEquals(publicDefaultEquipmentApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, nativeCall));
        assertEquals(publicDefaultEquipmentApi ? 0 : 1,
                SourceScan.countOccurrences(activeMethod, legacyAssignment));
        assertEquals(publicDefaultEquipmentApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, "victim.getRandom()"));
        assertEquals(publicDefaultEquipmentApi ? 1 : 0,
                SourceScan.countOccurrences(activeMethod, "level.getCurrentDifficultyAt("));
        assertEquals(publicDefaultEquipmentApi ? 0 : 1,
                SourceScan.countOccurrences(activeMethod, "Items.GOLDEN_SWORD"));
        assertEquals(0, SourceScan.countOccurrences(rawMethod, "Items.GOLDEN_SPEAR"),
                "the low branch must not fabricate the high-node spear roll");

        assertEquals(1, SourceScan.countOccurrences(rawMethod,
                "ConversionParams.single(victim, false, true)"));
        assertEquals(1, SourceScan.countOccurrences(rawMethod, "piglin.setPersistenceRequired();"));
        assertEquals(1, SourceScan.countOccurrences(
                rawMethod, "EventHooks.onLivingConvert(victim, piglin);"));
        assertEquals(1, SourceScan.countOccurrences(
                rawMethod, "ZombieMobTargetingEvents.recordConversionGrace(zombifiedPiglin, player);"));
        assertFalse(rawMethod.contains("finalizeSpawn(")
                        || rawMethod.contains("getMainHandItem().isEmpty()")
                        || rawMethod.contains("ItemStack.EMPTY")
                        || rawMethod.contains(".setBaby(")
                        || rawMethod.contains(".setAge(")
                        || rawMethod.contains("Attributes.")
                        || rawMethod.contains("Class." + "forName")
                        || rawMethod.contains("java.lang.reflect")
                        || rawMethod.contains("MethodHandles")
                        || rawMethod.contains("@Accessor")
                        || rawMethod.contains("@Invoker")
                        || rawMethod.contains("@Suppress" + "Warnings")
                        || rawMethod.contains("required = 0"),
                "the seam must not restore finalizeSpawn, conditionally omit equipment, or bypass access");

        String itemStackImport = "import net.minecraft.world.item.ItemStack;";
        String itemsImport = "import net.minecraft.world.item.Items;";
        assertEquals(1, SourceScan.countOccurrences(infection, itemStackImport));
        assertEquals(1, SourceScan.countOccurrences(infection, itemsImport));
        Pattern legacyImports = Pattern.compile(
                "(?m)^//\\? if <1\\.21\\.11 \\{\\R"
                        + "(?:/\\*)?import net\\.minecraft\\.world\\.item\\.ItemStack;\\R"
                        + "import net\\.minecraft\\.world\\.item\\.Items;\\R"
                        + "(?:\\*/)?//\\?\\}");
        assertEquals(1, countMatches(legacyImports, infection),
                "the two typed legacy imports must share the exact low2 boundary");

        Path accessTransformer =
                Path.of("src/main/resources/META-INF/accesstransformer.cfg");
        assertFalse(Files.exists(accessTransformer),
                "the local typed seam must not widen vanilla access globally");
        String activeBuild = SourceScan.stripComments(Files.readString(Path.of("build.gradle")));
        assertEquals(0, SourceScan.countOccurrences(activeBuild, "accessTransformers"));

        String registration = SourceScan.stripComments(Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTests.java")));
        assertEquals(1, SourceScan.countOccurrences(
                registration, "\"reg_piglin_conversion_not_baby_and_armed\""),
                "the required real infection GameTest must remain registered on every node");
        assertFalse(StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS
                        .contains("reg_piglin_conversion_not_baby_and_armed"),
                "pig/piglin infection is never part of the Nautilus platform N/A");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains(marker)
                        || controller.contains("populateDefaultEquipmentSlots"),
                "the local typed seam must not become a controller-wide replacement");
    }

    @Test
    void persistentAngerTargetUsesExactNodeBoundary() throws IOException {
        String executingNode = StonecutterCapabilityMatrix.nodeId();
        boolean entityReferenceApi =
                Set.of("26.2.x", "26.1.x", "1.21.11").contains(executingNode);
        assertEquals(
                entityReferenceApi,
                StonecutterCapabilityMatrix.hasEntityReferencePersistentAngerTargetApi(),
                "the centralized capability matrix must retain the >=1.21.11 setter boundary");

        Path matrixPath =
                TEST_JAVA.resolve("dev/molang/iamzombieq/util/StonecutterCapabilityMatrix.java");
        String compactMatrix = SourceScan.compact(Files.readString(matrixPath));
        assertTrue(compactMatrix.contains(
                        "privatestaticfinalSet<String>"
                                + "ENTITY_REFERENCE_PERSISTENT_ANGER_TARGET_NODES="
                                + "Set.of(\"26.2.x\",\"26.1.x\",\"1.21.11\");"),
                "the capability must remain high3/low2 instead of drifting per callsite");
        assertTrue(compactMatrix.contains(
                        "returnENTITY_REFERENCE_PERSISTENT_ANGER_TARGET_NODES"
                                + ".contains(nodeId());"),
                "the capability accessor must read the one recorded node set");

        record AngerTargetCallsite(
                Path path,
                String methodSignature,
                String receiver,
                String target,
                String before,
                String after) {
        }
        List<AngerTargetCallsite> expectedCallsites = List.of(
                new AngerTargetCallsite(
                        Path.of("dev/molang/iamzombieq/gametest/IAmZombieGameTestBodies.java"),
                        "static void infectionPiglinSweepGrace(GameTestHelper helper)",
                        "kin",
                        "player",
                        "kin.setLastHurtByMob(player);",
                        "GameTestSeams.targetDenied(kin, player)"),
                new AngerTargetCallsite(
                        Path.of(
                                "dev/molang/iamzombieq/gametest/"
                                        + "IAmZombieMobSleepGameTestBodies.java"),
                        "static void mobPiglinAngeredKinAllowed(GameTestHelper helper)",
                        "kin",
                        "player",
                        null,
                        "GameTestSeams.targetDenied(kin, player)"),
                new AngerTargetCallsite(
                        Path.of(
                                "dev/molang/iamzombieq/mixin/"
                                        + "HurtByTargetGoalAlertMixin.java"),
                        "private void iamzombieq$preAngerAlertedNeutral("
                                + "Mob other, LivingEntity target, CallbackInfo ci)",
                        "neutral",
                        "target",
                        null,
                        null),
                new AngerTargetCallsite(
                        Path.of(
                                "dev/molang/iamzombieq/gameplay/"
                                        + "ZombieReinforcementEvents.java"),
                        "private static void alertFormMatchedUndead("
                                + "ServerLevel level, ServerPlayer player, LivingEntity attacker,",
                        "piglin",
                        "attacker",
                        null,
                        "ally.setTarget(attacker);"));
        Set<Path> expectedFiles = expectedCallsites.stream()
                .map(AngerTargetCallsite::path)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Path> observedFiles = new HashSet<>();
        int rawMarkers = 0;
        int rawModernCalls = 0;
        int rawLegacyCalls = 0;
        int activeModernCalls = 0;
        int activeLegacyCalls = 0;
        int rawNullClears = 0;
        int activeNullClears = 0;
        int rawNonNullWrites = 0;
        int activeNonNullWrites = 0;
        Pattern nonNullWrite =
                Pattern.compile("[.]setPersistentAngerTarget[(](?!null[)])");

        try (var paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Path relative = MAIN_JAVA.relativize(path);
                String source = Files.readString(path);
                String activeSource = SourceScan.stripComments(source);
                int modernCalls = SourceScan.countOccurrences(
                        source, ".setPersistentAngerTarget(EntityReference.of(");
                int legacyCalls = countMatches(
                        Pattern.compile(
                                "[.]setPersistentAngerTarget[(][A-Za-z_$][A-Za-z0-9_$]*"
                                        + "[.]getUUID[(][)][)][;]"),
                        source);
                if (modernCalls + legacyCalls > 0) {
                    observedFiles.add(relative);
                }
                rawMarkers += SourceScan.countOccurrences(
                        source, "CROSS_VERSION-PERSISTENT-ANGER-TARGET-API");
                rawModernCalls += modernCalls;
                rawLegacyCalls += legacyCalls;
                activeModernCalls += SourceScan.countOccurrences(
                        activeSource, ".setPersistentAngerTarget(EntityReference.of(");
                activeLegacyCalls += countMatches(
                        Pattern.compile(
                                "[.]setPersistentAngerTarget[(][A-Za-z_$][A-Za-z0-9_$]*"
                                        + "[.]getUUID[(][)][)][;]"),
                        activeSource);
                rawNullClears +=
                        SourceScan.countOccurrences(source, ".setPersistentAngerTarget(null)");
                activeNullClears += SourceScan.countOccurrences(
                        activeSource, ".setPersistentAngerTarget(null)");
                rawNonNullWrites += Math.toIntExact(
                        nonNullWrite.matcher(source).results().count());
                activeNonNullWrites += Math.toIntExact(
                        nonNullWrite.matcher(activeSource).results().count());
            }
        }

        for (AngerTargetCallsite expected : expectedCallsites) {
            String source = Files.readString(MAIN_JAVA.resolve(expected.path()));
            String method = SourceScan.methodBody(source, expected.methodSignature());
            String activeMethod = SourceScan.stripComments(method);
            String modern = expected.receiver()
                    + ".setPersistentAngerTarget(EntityReference.of("
                    + expected.target() + "));";
            String legacy = expected.receiver()
                    + ".setPersistentAngerTarget("
                    + expected.target() + ".getUUID());";
            String timer = expected.receiver() + ".startPersistentAngerTimer();";
            assertEquals(1, SourceScan.countOccurrences(
                    method, "CROSS_VERSION-PERSISTENT-ANGER-TARGET-API"), expected.path().toString());
            assertTrue(SourceScan.containsInOrder(
                            method,
                            "CROSS_VERSION-PERSISTENT-ANGER-TARGET-API",
                            "//? if >=1.21.11 {",
                            modern,
                            "//?} else {",
                            legacy,
                            "//?}"),
                    expected.path() + " must retain the exact typed high/low branch order");
            assertEquals(1, SourceScan.countOccurrences(method, modern));
            assertEquals(1, SourceScan.countOccurrences(method, legacy));
            assertEquals(entityReferenceApi ? 1 : 0,
                    SourceScan.countOccurrences(activeMethod, modern));
            assertEquals(entityReferenceApi ? 0 : 1,
                    SourceScan.countOccurrences(activeMethod, legacy));
            String activeSetter = entityReferenceApi ? modern : legacy;
            if (expected.before() != null) {
                assertTrue(SourceScan.containsInOrder(
                                SourceScan.compact(activeMethod),
                                SourceScan.compact(expected.before()),
                                SourceScan.compact(activeSetter),
                                SourceScan.compact(timer)),
                        expected.path() + " must seed the same target before starting anger");
            } else {
                assertTrue(SourceScan.containsInOrder(
                                SourceScan.compact(activeMethod),
                                SourceScan.compact(activeSetter),
                                SourceScan.compact(timer)),
                        expected.path() + " must start the timer after setting the target");
            }
            if (expected.after() != null) {
                assertTrue(SourceScan.containsInOrder(
                                SourceScan.compact(activeMethod),
                                SourceScan.compact(activeSetter),
                                SourceScan.compact(timer),
                                SourceScan.compact(expected.after())),
                        expected.path() + " must preserve the gameplay ordering after anger");
            }
            assertFalse(method.contains("java.lang.reflect")
                            || method.contains("Class." + "forName")
                            || method.contains("Method" + "Handle")
                            || method.contains("setAccessible")
                            || method.contains("@Suppress" + "Warnings")
                            || method.contains("required=0"),
                    expected.path() + " must use only typed compile-time branches");
        }

        assertEquals(expectedFiles, observedFiles,
                "only the four recorded anger callsites may join this API family");
        assertEquals(4, rawMarkers);
        assertEquals(4, rawModernCalls);
        assertEquals(4, rawLegacyCalls);
        assertEquals(entityReferenceApi ? 4 : 0, activeModernCalls);
        assertEquals(entityReferenceApi ? 0 : 4, activeLegacyCalls);
        assertEquals(1, rawNullClears,
                "the conversion-grace anger clear is an adjacent invariant");
        assertEquals(1, activeNullClears);
        assertEquals(8, rawNonNullWrites,
                "exactly four mutually-exclusive typed branches must exist");
        assertEquals(4, activeNonNullWrites,
                "each node must compile exactly one typed write per callsite");

        String mixin = Files.readString(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/mixin/HurtByTargetGoalAlertMixin.java"));
        assertTrue(SourceScan.containsInOrder(
                        SourceScan.compact(mixin),
                        "@Inject(method=\"alertOther\",at=@At(\"HEAD\"))",
                        "CROSS_VERSION-PERSISTENT-ANGER-TARGET-API"),
                "the production alert seam must remain a HEAD pre-anger injection");

        String controller = Files.readString(CONTROLLER);
        assertFalse(controller.contains("EntityReference")
                        || controller.contains("setPersistentAngerTarget"),
                "the typed setter boundary must not use a global source replacement");
    }

    private static String activeMethod(String source, String signature) {
        return SourceScan.stripComments(SourceScan.methodBody(source, signature));
    }

    private static int countMatches(Pattern pattern, String source) {
        return Math.toIntExact(pattern.matcher(source).results().count());
    }
}
