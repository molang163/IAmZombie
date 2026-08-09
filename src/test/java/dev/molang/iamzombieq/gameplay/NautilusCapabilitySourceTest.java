package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NautilusCapabilitySourceTest {
    private static final Path CENTRAL_PROPERTIES = Path.of("stonecutter.properties.toml");
    private static final Path BUILD = Path.of("build.gradle");
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path INFECTION = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gameplay/ZombieInfectionEvents.java");
    private static final Path MOUNT = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gameplay/ZombieMountEvents.java");
    private static final Path GIANT = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gameplay/GiantPlayerEvents.java");
    private static final Path FIX_REGISTRATIONS = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTests.java");
    private static final Path FIX_BODIES = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gametest/IAmZombieFixRegressionGameTestBodies.java");
    private static final Path LEGACY_PADDING = MAIN_JAVA.resolve(
            "dev/molang/iamzombieq/gametest/LegacyGameTestPadding.java");

    @Test
    void capabilityMatrixIsTheSingleFrozenNautilusAuthority() throws IOException {
        String properties = Files.readString(CENTRAL_PROPERTIES);
        Pattern table = Pattern.compile(
                "(?ms)^\\[\"([^\"]+)\"\\]\\R(.*?)(?=^\\[\"|\\z)");
        Pattern capability = Pattern.compile(
                "(?m)^platform\\.nautilus\\s*=\\s*\"([^\"]+)\"\\s*$");
        Map<String, String> configured = new HashMap<>();
        table.matcher(properties).results().forEach(result -> {
            var value = capability.matcher(result.group(2));
            assertTrue(value.find(), "node is missing platform.nautilus: " + result.group(1));
            String previous = configured.put(result.group(1), value.group(1));
            assertNull(previous, "duplicate node capability row: " + result.group(1));
            assertFalse(value.find(), "node has duplicate platform.nautilus values: " + result.group(1));
        });

        Map<String, String> expected = Map.of(
                "26.2.x", StonecutterCapabilityMatrix.NAUTILUS_PRESENT,
                "26.1.x", StonecutterCapabilityMatrix.NAUTILUS_PRESENT,
                "1.21.11", StonecutterCapabilityMatrix.NAUTILUS_PRESENT,
                "1.21.10", StonecutterCapabilityMatrix.NAUTILUS_PLATFORM_ABSENT,
                "1.21.8", StonecutterCapabilityMatrix.NAUTILUS_PLATFORM_ABSENT);
        assertEquals(expected, configured,
                "the five-node Nautilus capability matrix must match the owner decision exactly");
        assertEquals(configured.get(StonecutterCapabilityMatrix.nodeId()),
                StonecutterCapabilityMatrix.nautilusStatus(),
                "Gradle must inject the active row from the central capability matrix");

        String compactBuild = SourceScan.compact(SourceScan.stripComments(Files.readString(BUILD)));
        assertEquals(1, SourceScan.countOccurrences(
                        compactBuild, "defnautilusCapability=scProperty('platform.nautilus')"),
                "build configuration must read the capability exactly once");
        assertEquals(1, SourceScan.countOccurrences(
                        compactBuild,
                        "systemProperty'iamzombieq.test.platform.nautilus',nautilusCapability"),
                "tests must receive the active capability exactly once");
    }

    @Test
    void capabilityBlocksUseOneRecordedStonecutterBoundary() throws IOException {
        Map<Path, Set<String>> expected = Map.of(
                INFECTION, Set.of(
                        "infection-imports",
                        "infection-dispatch",
                        "infection-pipeline-adapter",
                        "infection-converter"),
                MOUNT, Set.of("mount-import", "mount-classification"),
                GIANT, Set.of("giant-owned-exemption"),
                FIX_REGISTRATIONS, Set.of("gametest-saddle-registration", "gametest-stomp-registration"),
                FIX_BODIES, Set.of("gametest-body-imports", "gametest-saddle-body", "gametest-stomp-body"),
                LEGACY_PADDING, Set.of("legacy-padding-count"));
        Pattern marker = Pattern.compile(
                "(?m)^\\s*// CROSS_VERSION-NAUTILUS-CAPABILITY:([a-z0-9-]+)\\R"
                        + "\\s*//\\? if >=1\\.21\\.11 \\{");
        Map<Path, Set<String>> observed = new HashMap<>();
        int markers = 0;
        int boundaries = 0;
        for (Path path : productionJavaFiles()) {
            String source = Files.readString(path);
            Set<String> ids = new HashSet<>();
            var matcher = marker.matcher(source);
            while (matcher.find()) {
                assertTrue(ids.add(matcher.group(1)), "duplicate capability marker in " + path);
                markers++;
            }
            if (!ids.isEmpty()) {
                observed.put(path, ids);
            }
            if (expected.containsKey(path)) {
                boundaries += SourceScan.countOccurrences(source, "//? if >=1.21.11 {");
            }
        }
        assertEquals(expected, observed, "every Nautilus compile boundary must be recorded in one inventory");
        assertEquals(13, markers);
        assertEquals(13, boundaries,
                "the six affected files must contain no unrecorded >=1.21.11 capability block");
    }

    @Test
    void activeSourceMatchesNautilusCapabilityAndPreservesConversionSemantics() throws IOException {
        boolean present = StonecutterCapabilityMatrix.hasNautilusEntityApi();
        String infection = active(INFECTION);
        String mount = active(MOUNT);
        String giant = active(GIANT);
        String registrations = active(FIX_REGISTRATIONS);
        String bodies = active(FIX_BODIES);
        String entityPackage = "net.minecraft.world.entity.animal." + "nautilus";
        String nautilus = "Nauti" + "lus";
        String zombieNautilus = "Zombie" + nautilus;
        String entityTypes = "Entity" + "Types";
        String entityType = "Entity" + "Type";
        String activeEntityHolder = StonecutterCapabilityMatrix.activeEntityTypeHolder();
        Pattern nativeType = Pattern.compile("\\b(?:" + nautilus + "|" + zombieNautilus + ")\\b");
        StringBuilder allActiveProduction = new StringBuilder();
        Set<Path> activeNativeTypeFiles = new HashSet<>();
        for (Path path : productionJavaFiles()) {
            String source = active(path);
            allActiveProduction.append(source).append('\n');
            if (source.contains(entityPackage + ".")
                    || source.contains(entityTypes + ".NAUTILUS")
                    || source.contains(entityType + ".NAUTILUS")
                    || source.contains(entityTypes + ".ZOMBIE_NAUTILUS")
                    || source.contains(entityType + ".ZOMBIE_NAUTILUS")
                    || nativeType.matcher(source).find()) {
                activeNativeTypeFiles.add(path);
            }
        }
        Set<Path> expectedNativeTypeFiles = present
                ? Set.of(INFECTION, MOUNT, GIANT, FIX_BODIES)
                : Set.of();
        assertEquals(expectedNativeTypeFiles, activeNativeTypeFiles,
                "native Nautilus type references must stay on the exact capability-owned production surface");
        String activeProduction = allActiveProduction.toString();
        for (String id : StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS) {
            assertEquals(present ? 1 : 0, SourceScan.countOccurrences(activeProduction, "\"" + id + "\""),
                    "Nautilus GameTest ID must occur globally exactly when the platform capability is present");
        }
        for (String body : Set.of(
                "nautilusSaddleNotFabricated",
                "giantAuraSparesOwnedNautilusStompsWild")) {
            assertEquals(present ? 1 : 0, countMethod(activeProduction, body),
                    "Nautilus GameTest body must occur globally exactly when the platform capability is present");
        }

        if (present) {
            assertEquals(2, SourceScan.countOccurrences(infection, "import " + entityPackage + "."));
            assertEquals(1, SourceScan.countOccurrences(mount, "import " + entityPackage + "."));
            assertEquals(2, SourceScan.countOccurrences(bodies, "import " + entityPackage + "."));

            String dispatch = SourceScan.methodBody(infection, "public static void onLivingDeath");
            assertEquals(1, SourceScan.countOccurrences(dispatch, "victim instanceof " + nautilus));
            assertEquals(1, SourceScan.countOccurrences(dispatch, "tryInfect" + nautilus + "("));

            String adapter = SourceScan.compact(SourceScan.methodBody(
                    infection, "private static void tryInfect" + nautilus));
            assertTrue(adapter.contains("runInfectionPipeline(event,level,nautilus,player,"
                    + activeEntityHolder + ".ZOMBIE_NAUTILUS,"));
            assertTrue(adapter.contains("convert" + nautilus + "To" + zombieNautilus
                    + "(level,nautilus,player)"));
            assertTrue(adapter.endsWith("null);}"), "Nautilus has no advancement but must use the common pipeline");

            String converter = SourceScan.compact(SourceScan.methodBody(
                    infection, "private static boolean convert" + nautilus + "To" + zombieNautilus));
            assertTrue(SourceScan.containsInOrder(
                    converter,
                    activeEntityHolder + ".ZOMBIE_NAUTILUS.create(level,EntitySpawnReason.CONVERSION)",
                    "snapTo(",
                    "finalizeSpawn(",
                    "setTame(true,true)",
                    "setOwner(owner)",
                    "setPersistenceRequired()",
                    "setHealth(zombieNautilus.getMaxHealth())",
                    "setItemSlot(EquipmentSlot.SADDLE,nautilus.getItemBySlot(EquipmentSlot.SADDLE).copy())",
                    "setItemSlot(EquipmentSlot.BODY,nautilus.getItemBySlot(EquipmentSlot.BODY).copy())",
                    "hasCustomName()",
                    "setCustomName(nautilus.getCustomName())",
                    "setCustomNameVisible(nautilus.isCustomNameVisible())",
                    "level.addFreshEntity(zombieNautilus)",
                    "nautilus.discard()",
                    "level.levelEvent(null,1026,nautilus.blockPosition(),0)",
                    "returntrue"));

            String classification = SourceScan.compact(
                    SourceScan.methodBody(mount, "private static MountKind mountKindFor"));
            assertTrue(classification.contains(
                    "if(mountedinstanceof" + zombieNautilus + "){returnMountKind.ZOMBIE_NAUTILUS;}"));
            String owned = SourceScan.compact(
                    SourceScan.methodBody(giant, "private static boolean isOwnedMount"));
            assertTrue(owned.contains("targetinstanceof" + entityPackage + "." + zombieNautilus));
            for (String id : StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS) {
                assertEquals(1, SourceScan.countOccurrences(registrations, "\"" + id + "\""));
            }
            assertEquals(1, countMethod(bodies, "nautilusSaddleNotFabricated"));
            assertEquals(1, countMethod(bodies, "giantAuraSparesOwnedNautilusStompsWild"));
        } else {
            assertEquals(0, SourceScan.countOccurrences(activeProduction, entityPackage));
            assertEquals(0, SourceScan.countOccurrences(activeProduction, entityTypes + ".NAUTILUS"));
            assertEquals(0, SourceScan.countOccurrences(activeProduction, entityType + ".NAUTILUS"));
            assertEquals(0, SourceScan.countOccurrences(activeProduction, entityTypes + ".ZOMBIE_NAUTILUS"));
            assertEquals(0, SourceScan.countOccurrences(activeProduction, entityType + ".ZOMBIE_NAUTILUS"));
            assertEquals(0, nativeType.matcher(activeProduction).results().count());
            for (String id : StonecutterCapabilityMatrix.NAUTILUS_NA_GAMETEST_IDS) {
                assertEquals(0, SourceScan.countOccurrences(registrations, "\"" + id + "\""));
            }
            assertEquals(0, countMethod(bodies, "nautilusSaddleNotFabricated"));
            assertEquals(0, countMethod(bodies, "giantAuraSparesOwnedNautilusStompsWild"));
        }
    }

    @Test
    void adjacentGameplayPublicEventsAndPureRulesRemainOnEveryNode() throws IOException {
        String infection = active(INFECTION);
        String dispatch = SourceScan.compact(
                SourceScan.methodBody(infection, "public static void onLivingDeath"));
        assertTrue(dispatch.contains("victiminstanceofVillager"));
        assertTrue(dispatch.contains("victiminstanceofPig"));
        assertTrue(dispatch.contains("victiminstanceofAbstractPiglin"));
        assertTrue(dispatch.contains("victiminstanceofHorse"));
        for (String method : Set.of(
                "tryInfectVillager",
                "tryInfectIntoZombifiedPiglin",
                "tryInfectHorse",
                "convertVillagerToZombieVillager",
                "convertToZombifiedPiglin",
                "convertHorseToZombieHorse")) {
            assertEquals(1, countMethod(infection, method), "adjacent infection path must remain: " + method);
        }
        String horseConverter = SourceScan.compact(
                SourceScan.methodBody(infection, "private static boolean convertHorseToZombieHorse"));
        assertTrue(horseConverter.contains("copyHorseStateToZombieHorse("
                + "horse,zombieHorse,pendingHorseHealthRatio)"));
        String horseStateCopy = SourceScan.compact(
                SourceScan.methodBody(infection, "private static void copyHorseStateToZombieHorse"));
        assertTrue(horseStateCopy.contains(
                "setItemSlot(EquipmentSlot.SADDLE,horse.getItemBySlot(EquipmentSlot.SADDLE).copy())"));
        assertTrue(horseStateCopy.contains(
                "setItemSlot(EquipmentSlot.BODY,horse.getItemBySlot(EquipmentSlot.BODY).copy())"));

        String mount = SourceScan.compact(active(MOUNT));
        for (String kind : Set.of(
                "SPIDER", "ZOMBIE_HORSE", "SKELETON_HORSE", "NORMAL_HORSE", "CHICKEN", "STRIDER", "BIG_ZOMBIE")) {
            assertTrue(mount.contains("MountKind." + kind), "adjacent mount classification must remain: " + kind);
        }
        String giant = SourceScan.compact(
                SourceScan.methodBody(active(GIANT), "private static boolean isOwnedMount"));
        String horsePackage = Set.of("1.21.10", "1.21.8").contains(StonecutterCapabilityMatrix.nodeId())
                ? "horse"
                : "equine";
        assertTrue(giant.contains(
                "targetinstanceofnet.minecraft.world.entity.animal." + horsePackage + ".AbstractHorsehorse"));
        assertTrue(giant.contains("owner.equals(ref.getUUID())"));

        String registrations = active(FIX_REGISTRATIONS);
        assertTrue(registrations.contains("\"reg_piglin_conversion_not_baby_and_armed\""));
        assertTrue(registrations.contains("\"reg_giant_aura_spares_owned_horse_stomps_wild\""));
        String bodies = active(FIX_BODIES);
        assertEquals(1, countMethod(bodies, "piglinConversionNotBabyAndArmed"));
        assertEquals(1, countMethod(bodies, "giantAuraSparesOwnedHorseStompsWild"));

        String mountKind = Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/rules/mount/MountKind.java"));
        String mountRules = Files.readString(MAIN_JAVA.resolve(
                "dev/molang/iamzombieq/rules/mount/ZombieMountRules.java"));
        assertTrue(mountKind.contains("ZOMBIE_NAUTILUS"));
        assertTrue(mountRules.contains("case ZOMBIE_NAUTILUS -> true"));
        assertTrue(mountRules.contains("mountKind != MountKind.ZOMBIE_NAUTILUS"));

        for (Path event : Set.of(
                MAIN_JAVA.resolve("dev/molang/iamzombieq/api/event/ZombieInfectPreEvent.java"),
                MAIN_JAVA.resolve("dev/molang/iamzombieq/api/event/ZombieInfectedEvent.java"))) {
            String source = Files.readString(event);
            assertFalse(source.contains("Nautilus"));
            assertTrue(source.contains("ServerPlayer attacker"));
            assertTrue(source.contains("LivingEntity victim"));
            assertTrue(source.contains("EntityType<?> resultType"));
            assertTrue(source.contains("ServerPlayer attacker()"));
            assertTrue(source.contains("LivingEntity victim()"));
            assertTrue(source.contains("EntityType<?> resultType()"));
        }
    }

    private static String active(Path path) throws IOException {
        return SourceScan.stripComments(Files.readString(path));
    }

    private static List<Path> productionJavaFiles() throws IOException {
        try (var paths = Files.walk(MAIN_JAVA)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static int countMethod(String source, String name) {
        return Math.toIntExact(Pattern.compile("\\b(?:public\\s+|private\\s+|protected\\s+)?"
                        + "static\\s+(?:boolean|void)\\s+" + Pattern.quote(name) + "\\s*\\(")
                .matcher(source).results().count());
    }
}
