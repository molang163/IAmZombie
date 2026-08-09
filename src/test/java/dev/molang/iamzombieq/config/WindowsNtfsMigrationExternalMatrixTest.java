package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Hosted-Windows ACL and genuine cross-process permanent-lock gate. */
@EnabledOnOs(OS.WINDOWS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WindowsNtfsMigrationExternalMatrixTest {
    private static final String EXTERNAL_ARM_ENV =
            "IAMZOMBIEQ_WINDOWS_EXTERNAL_MATRIX_ARMED";
    private static final String GATE_ROOT_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_ROOT";
    private static final String DRIVE_TYPE_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_DRIVE_TYPE";
    private static final String DRIVE_FORMAT_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_DRIVE_FORMAT";
    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private static final String PROCESS_MAGIC =
            "IAMZOMBIEQ-WINDOWS-EXTERNAL-PROCESS-V1";
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(45);
    private static final byte[] NEVER_PARSE_LEGACY =
            "C1_WINDOWS_ACL_DENIAL_MUST_PRECEDE_LEGACY_PARSE\n"
                    .getBytes(StandardCharsets.UTF_8);

    private Path gateRoot;
    private Path suiteRoot;

    @BeforeAll
    void requireExactHostedWindowsTuple() throws Exception {
        assertTrue(
                System.getProperty("os.name", "unknown")
                        .startsWith("Windows"),
                "external matrix must run only on Windows");
        int javaFeature = Runtime.version().feature();
        assertTrue(
                MigrationJavaRuntimeMatrix.supportsBasicProfile(javaFeature),
                "external matrix requires a BASIC runtime approved by this "
                        + "Stonecutter node; actual feature="
                        + javaFeature
                        + ", approved="
                        + MigrationJavaRuntimeMatrix.runtimeFeatures());
        assertEquals(
                Integer.toString(javaFeature),
                System.getProperty("iamzombieq.test.runtimeJavaFeature"),
                "Gradle runtime declaration must match the actual test worker");
        assertEquals(
                "1",
                System.getenv(EXTERNAL_ARM_ENV),
                "hosted matrix must explicitly arm " + EXTERNAL_ARM_ENV);
        assertEquals(
                "Fixed",
                System.getenv(DRIVE_TYPE_ENV),
                "runner must certify a local fixed drive");
        assertEquals(
                "NTFS",
                System.getenv(DRIVE_FORMAT_ENV),
                "runner must certify exact NTFS");

        String configured = System.getenv(GATE_ROOT_ENV);
        assertNotNull(configured, "runner must provide " + GATE_ROOT_ENV);
        assertFalse(configured.isBlank(), GATE_ROOT_ENV + " must not be blank");
        gateRoot = Path.of(configured);
        assertTrue(gateRoot.isAbsolute(), "gate root must be absolute");
        assertEquals(
                gateRoot.toAbsolutePath().normalize(),
                gateRoot,
                "gate root must already be normalized");
        assertFalse(isUnc(gateRoot), "gate root must not be UNC");

        var provider = gateRoot.getFileSystem().provider();
        assertTrue(
                provider == FileSystems.getDefault().provider(),
                "gate root must use the exact default provider instance");
        assertEquals("file", provider.getScheme());
        assertEquals(WINDOWS_PROVIDER, provider.getClass().getName());

        BasicFileAttributes attributes = nofollow(gateRoot);
        assertTrue(attributes.isDirectory(), "gate root must be a directory");
        assertFalse(attributes.isSymbolicLink(), "gate root must not be a symlink");
        assertFalse(attributes.isOther(), "gate root must not be a reparse point");
        assertEquals(null, attributes.fileKey(), "Windows fileKey must be null");
        FileStore store = Files.getFileStore(gateRoot);
        assertEquals("NTFS", store.type(), "gate root must be exact NTFS");
        Object firstVsn = store.getAttribute("volume:vsn");
        Object secondVsn = Files.getFileStore(gateRoot)
                .getAttribute("volume:vsn");
        assertNotNull(firstVsn, "volume:vsn must be readable");
        assertEquals(firstVsn.getClass(), secondVsn == null
                ? null
                : secondVsn.getClass());
        assertEquals(firstVsn, secondVsn, "volume:vsn must be stable");
        assertTrue(isEmptyDirectory(gateRoot), "gate root must be disposable and empty");
        suiteRoot = Files.createDirectory(
                gateRoot.resolve("external-junit-" + UUID.randomUUID()));
    }

    @AfterAll
    void removeOnlyTheCreatedSuiteTree() throws IOException {
        if (suiteRoot != null
                && suiteRoot.getParent() != null
                && suiteRoot.getParent().equals(gateRoot)
                && suiteRoot.getFileName()
                        .toString()
                        .startsWith("external-junit-")
                && Files.exists(suiteRoot, LinkOption.NOFOLLOW_LINKS)) {
            deleteTreeNofollow(suiteRoot);
        }
    }

    @Test
    @Order(1)
    void aclWriteDenialFailsClosedAndRestoresPublicAclView() throws Exception {
        Path scenario = Files.createDirectory(suiteRoot.resolve("acl-denial"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                NEVER_PARSE_LEGACY);
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        AclFileAttributeView view = Files.getFileAttributeView(
                global,
                AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        assertNotNull(view, "NTFS must expose the public ACL attribute view");
        List<AclEntry> originalAcl = List.copyOf(view.getAcl());
        UserPrincipal aclOwner = view.getOwner();
        AclEntry writeDeny = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(aclOwner)
                .setPermissions(EnumSet.of(
                        AclEntryPermission.ADD_FILE,
                        AclEntryPermission.ADD_SUBDIRECTORY,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.DELETE_CHILD))
                .build();
        ArrayList<AclEntry> deniedAcl = new ArrayList<>(
                originalAcl.size() + 1);
        deniedAcl.add(writeDeny);
        deniedAcl.addAll(originalAcl);

        try {
            view.setAcl(deniedAcl);
            assertTrue(
                    view.getAcl().stream().anyMatch(entry ->
                            entry.type() == AclEntryType.DENY
                                    && entry.principal().equals(aclOwner)
                                    && entry.permissions().contains(
                                            AclEntryPermission.ADD_FILE)),
                    "the explicit current-user ADD_FILE deny must be active");
            Map<String, FilePortrait> before = portrait(global);

            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> ProductionConfigMigration.migratePreferences(global));

            assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
            assertEquals("migration-core", failure.artifact());
            assertEquals("engine-execution", failure.operation());
            assertFalse(failure.synthetic());
            assertTrue(
                    hasAccessDeniedCause(failure)
                            || MigrationFailure.describe(failure)
                                    .toLowerCase(Locale.ROOT)
                                    .contains("access is denied")
                            || MigrationFailure.describe(failure)
                                    .toLowerCase(Locale.ROOT)
                                    .contains("access denied"),
                    "failure must preserve the Windows access-denied cause: "
                            + MigrationFailure.describe(failure));
            assertTrue(failure.recovery().contains("C1-F1-STOP-PRESERVE-v1"));
            assertArrayEquals(NEVER_PARSE_LEGACY, Files.readAllBytes(legacy));
            assertNoArtifacts(target);
            assertEquals(
                    before,
                    portrait(global),
                    "ACL denial must preserve every path, byte, size, and timestamp");
        } finally {
            view.setAcl(originalAcl);
            assertEquals(
                    originalAcl,
                    view.getAcl(),
                    "the exact original public JDK ACL-view list must be restored");
            Path restorationProbe = global.resolve("acl-restoration.probe");
            Files.writeString(
                    restorationProbe,
                    "restored\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            assertTrue(Files.deleteIfExists(restorationProbe));
            assertFalse(Files.exists(
                    restorationProbe, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    @Order(2)
    void twoIndependentJavaProcessesEnforcePermanentLockContention()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("two-process-lock"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path control = Files.createDirectory(scenario.resolve("control"));
        byte[] legacyBytes = LegacyConfigParserTest.fixtureBytes();
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                legacyBytes);
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        Path classpathJar = createClasspathJar(control);
        Path ready = control.resolve("holder.ready");
        Path release = control.resolve("holder.release");
        Path holderResult = control.resolve("holder.result");
        Path contenderResult = control.resolve("contender.result");
        Path holderLog = control.resolve("holder.log");
        Path contenderLog = control.resolve("contender.log");
        Process holder = null;
        Process contender = null;

        try {
            holder = startJavaProcess(
                    classpathJar,
                    holderLog,
                    "holder",
                    global.toString(),
                    holderResult.toString(),
                    ready.toString(),
                    release.toString());
            awaitReady(ready, holder, holderLog);
            Map<String, String> readyFields = readProtocol(ready);
            assertEquals("holder", readyFields.get("role"));
            assertEquals("LOCK_HELD", readyFields.get("status"));
            long holderPid = Long.parseLong(readyFields.get("pid"));
            assertEquals(holder.pid(), holderPid);
            assertNotEquals(ProcessHandle.current().pid(), holderPid);
            assertEquals(
                    artifacts.lock().toString(),
                    readyFields.get("lock"),
                    "READY must name the production permanent lock path");
            String heldLockSha256 = readyFields.get("lockSha256");
            assertNotNull(heldLockSha256, "READY must bind the held lock SHA");
            assertTrue(
                    heldLockSha256.matches("[0-9a-f]{64}"),
                    "READY lock SHA must be lowercase SHA-256");
            long heldLockSize = Long.parseLong(readyFields.get("lockSize"));
            assertTrue(holder.isAlive(), "holder exited immediately after READY");
            assertOnlyInitializedLock(target);
            assertEquals(heldLockSize, Files.size(artifacts.lock()));
            Map<String, FilePortrait> beforeContention = portrait(
                    global, Map.of(artifacts.lock(), heldLockSha256));
            AclFileAttributeView legacyAclView = Files.getFileAttributeView(
                    legacy,
                    AclFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            assertNotNull(legacyAclView, "NTFS legacy must expose an ACL view");
            List<AclEntry> originalLegacyAcl =
                    List.copyOf(legacyAclView.getAcl());
            UserPrincipal legacyOwner = legacyAclView.getOwner();
            AclEntry readDeny = AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(legacyOwner)
                    .setPermissions(AclEntryPermission.READ_DATA)
                    .build();
            ArrayList<AclEntry> unreadableLegacyAcl = new ArrayList<>(
                    originalLegacyAcl.size() + 1);
            unreadableLegacyAcl.add(readDeny);
            unreadableLegacyAcl.addAll(originalLegacyAcl);
            try {
                legacyAclView.setAcl(unreadableLegacyAcl);
                assertTrue(
                        legacyAclView.getAcl().stream().anyMatch(entry ->
                                entry.type() == AclEntryType.DENY
                                        && entry.principal().equals(legacyOwner)
                                        && entry.permissions().contains(
                                                AclEntryPermission.READ_DATA)),
                        "legacy READ_DATA deny must be active during contention");
                contender = startJavaProcess(
                        classpathJar,
                        contenderLog,
                        "contender",
                        global.toString(),
                        contenderResult.toString());
                assertTrue(
                        contender.waitFor(
                                PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                        "contender timed out; log:\n"
                                + readIfPresent(contenderLog));
                assertEquals(
                        0,
                        contender.exitValue(),
                        "contender process failed; log:\n"
                                + readIfPresent(contenderLog));
                Map<String, String> contenderFields =
                        readProtocol(contenderResult);
                assertEquals("contender", contenderFields.get("role"));
                assertEquals(
                        "EXPECTED_CONTENTION_F1",
                        contenderFields.get("status"));
                long contenderPid = Long.parseLong(
                        contenderFields.get("pid"));
                assertEquals(contender.pid(), contenderPid);
                assertNotEquals(holderPid, contenderPid);
                assertNotEquals(ProcessHandle.current().pid(), contenderPid);
                assertEquals("LOCKED", contenderFields.get("phase"));
                assertEquals(
                        "migration-core", contenderFields.get("artifact"));
                assertEquals(
                        "engine-execution", contenderFields.get("operation"));
            } finally {
                legacyAclView.setAcl(originalLegacyAcl);
                assertEquals(
                        originalLegacyAcl,
                        legacyAclView.getAcl(),
                        "legacy public JDK ACL view must be restored before "
                                + "holder release");
            }
            assertTrue(holder.isAlive(), "holder must still own the lock");
            assertEquals(
                    beforeContention,
                    portrait(global, Map.of(artifacts.lock(), heldLockSha256)),
                    "contender must not publish or alter the held-lock portrait");
            assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
            assertOnlyInitializedLock(target);

            Files.writeString(
                    release,
                    "RELEASE\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            assertTrue(
                    holder.waitFor(
                            PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "holder timed out after RELEASE; log:\n"
                            + readIfPresent(holderLog));
            assertEquals(
                    0,
                    holder.exitValue(),
                    "holder process failed; log:\n" + readIfPresent(holderLog));
            Map<String, String> holderFields = readProtocol(holderResult);
            assertEquals("holder", holderFields.get("role"));
            assertEquals(
                    "COMPLETED_AND_RELEASED",
                    holderFields.get("status"));
            assertEquals("MIGRATED", holderFields.get("outcome"));
            assertEquals("COMPLETE", holderFields.get("phase"));

            assertReleased(artifacts.lock());
            assertEquals(
                    heldLockSha256,
                    FilePortrait.sha256(Files.readAllBytes(artifacts.lock())),
                    "released permanent lock bytes must match READY");
            assertCompleteArtifacts(target);
            MigrationTargetState restarted = ProductionConfigMigration
                    .migratePreferences(global)
                    .orElseThrow();
            assertEquals(
                    MigrationTargetState.Outcome.COMPLETE,
                    restarted.outcome());
            assertEquals(MigrationTargetState.Phase.COMPLETE, restarted.phase());
            assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
            assertCompleteArtifacts(target);
        } finally {
            if (holder != null && holder.isAlive()) {
                try {
                    if (!Files.exists(release, LinkOption.NOFOLLOW_LINKS)) {
                        Files.writeString(
                                release,
                                "RELEASE-AFTER-FAILURE\n",
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                    }
                } catch (IOException ignored) {
                    // Process termination below remains the fail-safe cleanup.
                }
            }
            stopProcess(contender);
            stopProcess(holder);
        }
    }

    private static Process startJavaProcess(
            Path classpathJar, Path log, String... arguments)
            throws IOException {
        Path java = Path.of(
                System.getProperty("java.home"), "bin", "java.exe");
        assertTrue(Files.isRegularFile(java), "missing current java.exe: " + java);
        ArrayList<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-cp");
        command.add(classpathJar.toString());
        command.add(WindowsMigrationLockProcessMain.class.getName());
        command.addAll(Arrays.asList(arguments));
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static Path createClasspathJar(Path control) throws IOException {
        LinkedHashSet<URI> entries = new LinkedHashSet<>();
        ClassLoader loader = WindowsNtfsMigrationExternalMatrixTest.class
                .getClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader urls) {
                for (URL url : urls.getURLs()) {
                    if (url.getProtocol().equals("file")) {
                        try {
                            entries.add(url.toURI());
                        } catch (URISyntaxException failure) {
                            throw new IOException(
                                    "invalid test runtime classpath URL: " + url,
                                    failure);
                        }
                    }
                }
            }
            loader = loader.getParent();
        }
        Arrays.stream(System.getProperty("java.class.path")
                        .split(Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize().toUri())
                .forEach(entries::add);
        URI testClasses = codeSource(
                WindowsMigrationLockProcessMain.class);
        URI productionClasses = codeSource(ProductionConfigMigration.class);
        entries.add(testClasses);
        entries.add(productionClasses);
        String classPath = entries.stream()
                .map(URI::toASCIIString)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow(() -> new IllegalStateException(
                        "test runtime classpath is empty"));
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.CLASS_PATH, classPath);
        Path jar = control.resolve("test-runtime-classpath.jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(
                        jar,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE),
                manifest)) {
            // The manifest-only pathing JAR avoids the Windows command limit.
        }
        return jar;
    }

    private static URI codeSource(Class<?> type) {
        try {
            return type.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(
                    "invalid code source for " + type.getName(), failure);
        }
    }

    private static void awaitReady(Path ready, Process holder, Path log)
            throws Exception {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = nofollow(ready);
                assertTrue(attributes.isRegularFile());
                assertFalse(attributes.isSymbolicLink());
                assertFalse(attributes.isOther());
                return;
            }
            if (!holder.isAlive()) {
                fail("holder exited before READY (exit "
                        + holder.exitValue()
                        + "); log:\n"
                        + readIfPresent(log));
            }
            Thread.sleep(25L);
        }
        fail("holder did not emit READY; log:\n" + readIfPresent(log));
    }

    private static Map<String, String> readProtocol(Path path)
            throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "empty process protocol file: " + path);
        assertEquals(PROCESS_MAGIC, lines.getFirst());
        TreeMap<String, String> fields = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            int separator = line.indexOf('=');
            assertTrue(separator > 0, "malformed process protocol line: " + line);
            String previous = fields.put(
                    line.substring(0, separator),
                    line.substring(separator + 1));
            assertEquals(null, previous, "duplicate process protocol field");
        }
        return Map.copyOf(fields);
    }

    private static void assertReleased(Path lock) throws Exception {
        try (FileChannel channel = FileChannel.open(
                        lock,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
                FileLock acquired = channel.tryLock()) {
            assertNotNull(
                    acquired,
                    "holder process exited but the OS permanent lock remains held");
        }
    }

    private static void assertNoArtifacts(Path target) {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertFalse(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
        for (Path artifact : artifacts.fixedCandidates()) {
            assertFalse(
                    Files.exists(artifact, LinkOption.NOFOLLOW_LINKS),
                    "migration artifact must remain absent: " + artifact);
        }
    }

    private static void assertOnlyInitializedLock(Path target)
            throws IOException {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        BasicFileAttributes lockAttributes = nofollow(artifacts.lock());
        assertTrue(lockAttributes.isRegularFile());
        assertFalse(lockAttributes.isSymbolicLink());
        assertFalse(lockAttributes.isOther());
        assertEquals(null, lockAttributes.fileKey());
        assertTrue(Files.size(artifacts.lock()) > 0);
        assertFalse(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
        for (Path absent : new Path[] {
                artifacts.journal(),
                artifacts.backup(),
                artifacts.initial(),
                artifacts.marker()
        }) {
            assertFalse(Files.exists(absent, LinkOption.NOFOLLOW_LINKS));
        }
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(Files.exists(stage, LinkOption.NOFOLLOW_LINKS));
        }
    }

    private static void assertCompleteArtifacts(Path target)
            throws IOException {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        for (Path present : new Path[] {
                target,
                artifacts.lock(),
                artifacts.journal(),
                artifacts.backup(),
                artifacts.initial(),
                artifacts.marker()
        }) {
            BasicFileAttributes attributes = nofollow(present);
            assertTrue(attributes.isRegularFile(), "missing regular file: " + present);
            assertFalse(attributes.isSymbolicLink(), "unexpected symlink: " + present);
            assertFalse(attributes.isOther(), "unexpected reparse point: " + present);
            assertEquals(null, attributes.fileKey(), "Windows fileKey must be null");
        }
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(
                    Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                    "fixed stage must be cleaned up: " + stage);
        }
        assertTrue(new TargetConfigValidator(ConfigSchemaCatalog.load())
                .validateEncoded(
                        MigrationTarget.PREFERENCES,
                        Files.readString(target, StandardCharsets.UTF_8))
                .valid());
        assertEquals(
                MigrationTargetState.Phase.COMPLETE,
                MigrationJournal.decode(Files.readAllBytes(artifacts.journal()))
                        .phase());
        assertEquals(
                MigrationTargetState.Phase.COMPLETE,
                MigrationMarker.decode(Files.readAllBytes(artifacts.marker()))
                        .phase());
    }

    private static Map<String, FilePortrait> portrait(Path root)
            throws IOException {
        return portrait(root, Map.of());
    }

    private static Map<String, FilePortrait> portrait(
            Path root, Map<Path, String> lockedFileHashes)
            throws IOException {
        TreeMap<String, FilePortrait> portrait = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(directory, attributes);
                add(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(file, attributes);
                add(file);
                return FileVisitResult.CONTINUE;
            }

            private void add(Path path) throws IOException {
                BasicFileAttributes attributes = nofollow(path);
                String relative = root.equals(path)
                        ? "."
                        : root.relativize(path).toString();
                portrait.put(
                        relative,
                        FilePortrait.from(
                                path,
                                attributes,
                                lockedFileHashes.get(path)));
            }
        });
        return Map.copyOf(portrait);
    }

    private static void rejectReparse(
            Path path, BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException(
                    "Refusing to traverse a reparse point in the external "
                            + "matrix tree: "
                            + path);
        }
    }

    private static BasicFileAttributes nofollow(Path path) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean hasAccessDeniedCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.nio.file.AccessDeniedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isEmptyDirectory(Path directory)
            throws IOException {
        try (var entries = Files.newDirectoryStream(directory)) {
            return !entries.iterator().hasNext();
        }
    }

    private static boolean isUnc(Path path) {
        return path.toString().startsWith("\\\\");
    }

    private static String readIfPresent(Path path) {
        try {
            return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    ? Files.readString(path, StandardCharsets.UTF_8)
                    : "<absent>";
        } catch (IOException failure) {
            return "<unreadable: " + failure + ">";
        }
    }

    private static void stopProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void deleteTreeNofollow(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (directory.equals(root)) {
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new IOException(
                                "Refusing to traverse a replaced external "
                                        + "matrix suite root: "
                                        + directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    Files.delete(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (file.equals(root)) {
                    throw new IOException(
                            "Refusing to delete a replaced external matrix "
                                    + "suite root: "
                                    + file);
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record FilePortrait(
            boolean regularFile,
            boolean directory,
            boolean symbolicLink,
            boolean other,
            long size,
            FileTime creationTime,
            FileTime lastModifiedTime,
            String sha256) {
        private static FilePortrait from(
                Path path, BasicFileAttributes attributes) throws IOException {
            return from(path, attributes, null);
        }

        private static FilePortrait from(
                Path path,
                BasicFileAttributes attributes,
                String trustedLockedSha256) throws IOException {
            return new FilePortrait(
                    attributes.isRegularFile(),
                    attributes.isDirectory(),
                    attributes.isSymbolicLink(),
                    attributes.isOther(),
                    attributes.size(),
                    attributes.creationTime(),
                    attributes.lastModifiedTime(),
                    attributes.isRegularFile()
                            ? trustedLockedSha256 == null
                                    ? sha256(Files.readAllBytes(path))
                                    : trustedLockedSha256
                            : "-");
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }
    }
}
