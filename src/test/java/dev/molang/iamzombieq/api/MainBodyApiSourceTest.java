package dev.molang.iamzombieq.api;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Source-scan tests (no Minecraft bootstrap) for the Phase-1 main-body API + portability seam. They assert the
 * new api/internal/platform classes exist, that the additive POST-fire points and the FOOD hook-query landed in
 * the edited handlers, and that the unattended-safety gates hold (publisher isolation, no raw bus post in the
 * handlers, no FakePlayer-unsafe connection reads, no static side effects, neutral-when-empty).
 */
class MainBodyApiSourceTest {

    private static final String MAIN = "src/main/java/dev/molang/iamzombieq/";
    private static final Path ZOMBIE_PLAYER_EVENTS = Path.of(MAIN + "gameplay/ZombiePlayerEvents.java");
    private static final Path ZOMBIE_FOOD_EVENTS = Path.of(MAIN + "gameplay/ZombieFoodEvents.java");

    private static String read(String relativeUnderMain) throws IOException {
        return Files.readString(Path.of(MAIN + relativeUnderMain));
    }

    @Test
    void allNewApiInternalAndPlatformFilesExist() {
        String[] expected = {
                "api/core/IZombiePlayer.java",
                "api/core/IZombiePlayerAPI.java",
                "api/event/ZombieTransformPreEvent.java",
                "api/event/ZombieTransformedEvent.java",
                "api/event/ZombieEvolvePreEvent.java",
                "api/event/ZombieEvolvedEvent.java",
                "api/event/ZombieInfectPreEvent.java",
                "api/event/ZombieInfectedEvent.java",
                "api/event/ZombieEatPreEvent.java",
                "api/event/ZombieAteEvent.java",
                "api/extension/IZombieExtensions.java",
                "api/extension/IFoodRuleProvider.java",
                "api/extension/IAttackerHook.java",
                "api/extension/AttackerDecision.java",
                "api/registry/ZombieFormSpec.java",
                "internal/core/ServerZombiePlayer.java",
                "internal/event/ZombieEventPublisher.java",
                "platform/AttachmentService.java",
                "platform/EventBusService.java",
                "platform/Services.java",
                "platform/neoforge/NeoForgeAttachmentService.java",
                "platform/neoforge/NeoForgeEventBusService.java",
        };
        for (String relative : expected) {
            assertTrue(Files.exists(Path.of(MAIN + relative)), "missing new API file: " + relative);
        }
    }

    @Test
    void preEventsAreCancellableAndPostEventsAreObservers() throws IOException {
        for (String pre : new String[] {
                "api/event/ZombieTransformPreEvent.java",
                "api/event/ZombieEvolvePreEvent.java",
                "api/event/ZombieInfectPreEvent.java",
                "api/event/ZombieEatPreEvent.java" }) {
            String src = read(pre);
            assertTrue(src.contains("extends Event implements ICancellableEvent"), pre + " should be a cancellable Pre event");
        }
        for (String post : new String[] {
                "api/event/ZombieTransformedEvent.java",
                "api/event/ZombieEvolvedEvent.java",
                "api/event/ZombieInfectedEvent.java",
                "api/event/ZombieAteEvent.java" }) {
            String src = read(post);
            assertTrue(src.contains("extends Event"), post + " should extend Event");
            assertFalse(src.contains("ICancellableEvent"), post + " (a Post observer) must not be cancellable");
        }
    }

    @Test
    void facadeMutatorsSnapshotPostPreSetSyncPost() throws IOException {
        String facade = read("internal/core/ServerZombiePlayer.java");
        assertTrue(facade.contains("implements IZombiePlayer"), "the facade should implement the public interface");
        assertTrue(facade.contains("postCancelable(new ZombieTransformPreEvent"), "transform should post a cancellable Pre");
        assertTrue(facade.contains("post(new ZombieTransformedEvent"), "transform should post the observer Post");
        assertTrue(facade.contains("postCancelable(new ZombieEvolvePreEvent"), "evolve should post a cancellable Pre");
        assertTrue(facade.contains("post(new ZombieEvolvedEvent"), "evolve should post the observer Post");
        // Mutators must go through the platform attachment + event services (no raw entity calls).
        assertTrue(facade.contains("Services.ATTACHMENTS.set") && facade.contains("Services.ATTACHMENTS.sync"),
                "the facade should write+sync via the platform attachment service");
        assertTrue(facade.contains("ZombieEventPublisher.postCancelable") && facade.contains("ZombieEventPublisher.post"),
                "the facade should post through the isolation-wrapped publisher");
    }

    @Test
    void publisherWrapsEveryPostInExceptionIsolationAndNeverRethrows() throws IOException {
        String publisher = read("internal/event/ZombieEventPublisher.java");
        // Listener isolation: catch listener Exceptions (NOT Throwable — JVM Errors must propagate).
        assertTrue(publisher.contains("catch (Exception e)"), "the publisher must catch listener Exceptions");
        assertFalse(publisher.contains("catch (Throwable"), "the publisher must let JVM Errors propagate (no catch Throwable)");
        assertTrue(publisher.contains("IAmZombieMod.LOGGER"), "the publisher must log listener exceptions");
        assertTrue(publisher.contains("static void post(") && publisher.contains("postCancelable("),
                "the publisher must expose both the observer post and the cancelable post");
        // postCancelable returns the event's canceled flag from the catch, so a cancellation set by an earlier
        // listener (before a later one threw) is preserved (NOT discarded as not-canceled).
        assertTrue(publisher.contains("return event.isCanceled();"),
                "a thrown listener in postCancelable should preserve the event's current canceled state");
    }

    @Test
    void zombiePlayerEventsFiresPostObserversAfterEachWriteSite() throws IOException {
        String src = Files.readString(ZOMBIE_PLAYER_EVENTS);
        // onPlayerClone reset + onLivingDeath giant-kill => ZombieTransformedEvent; onLivingDeath evolution => ZombieEvolvedEvent.
        assertTrue(src.contains("ZombieEventPublisher.post(new ZombieTransformedEvent(player, previous.state().form(), nextData.state().form()))"),
                "the clone-reset write site should fire a ZombieTransformedEvent POST observer");
        assertTrue(src.contains("ZombieEventPublisher.post(new ZombieTransformedEvent(killer, data.state().form(), nextData.state().form()))"),
                "the giant-kill write site should fire a ZombieTransformedEvent POST observer");
        assertTrue(src.contains("ZombieEventPublisher.post(new ZombieEvolvedEvent(player, data.state(), nextData.state(), result.outcome()))"),
                "the death-evolution write site should fire a ZombieEvolvedEvent POST observer");
        // The existing setData/syncData calls at all three sites must be preserved (not removed).
        assertTrue(src.contains("player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)")
                        && src.contains("killer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, nextData)"),
                "existing setData writes must be preserved");
    }

    @Test
    void zombieFoodEventsFiresEatPreAndRealStackAteAndQueriesFoodHook() throws IOException {
        String src = Files.readString(ZOMBIE_FOOD_EVENTS);
        // The item-eat path fires a cancellable PRE before applying effects, carrying the REAL eaten stack.
        assertTrue(src.contains("postCancelable(new ZombieEatPreEvent(prePlayer, eaten, rule))"),
                "the item-eat path should fire a cancellable ZombieEatPreEvent before applying effects");
        // The item-eat path fires the observer ZombieAteEvent with the REAL eaten stack (not ItemStack.EMPTY).
        assertTrue(src.contains("ZombieEventPublisher.post(new ZombieAteEvent(atePlayer, eaten, rule))"),
                "the item-eat path should fire a ZombieAteEvent POST observer carrying the real eaten stack");
        // The old misplaced baby->adult-only EMPTY-stack fire must be gone.
        assertFalse(src.contains("new ZombieAteEvent(serverPlayer, ItemStack.EMPTY, rule)"),
                "the misplaced baby->adult-only EMPTY-stack ZombieAteEvent fire should be removed");
        // The existing baby->adult setData write must still be preserved (only the EMPTY-stack fire was removed).
        assertTrue(src.contains("player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data.withState(data.state().asAdult()))"),
                "the existing baby->adult setData write must be preserved");
        // FOOD hook-query: iterate providers first-non-null, else the built-in ruleForStack call unchanged.
        assertTrue(src.contains("for (IFoodRuleProvider provider : IZombieExtensions.foodRuleProviders())"),
                "the food handler should iterate the registered IFoodRuleProviders");
        assertTrue(src.contains("ZombieFoodRules.ruleForStack(stack, itemId, configuredZombieFoods())"),
                "the built-in ruleForStack call should remain as the fallback inside the helper");
        // The two ItemStack-eat sites (where a real eaten stack exists) route through the hook-querying helper.
        assertTrue(src.contains("resolveFoodRule(player, eaten, eatenItemId)")
                        && src.contains("resolveFoodRule(player, eaten, eatenId)"),
                "the ItemStack-eat rule-resolution sites should route through the hook-querying helper");
        // The cake block-eat path keeps the built-in id-only call unchanged (no real ItemStack for a block).
        assertTrue(src.contains("ZombieFoodRules.ruleForStack(ItemStack.EMPTY, \"minecraft:cake\""),
                "the cake block-eat path keeps the built-in id-only ruleForStack call");
    }

    @Test
    void publisherGate_handlersUseThePublisherNotRawNeoForgeBusPost() throws IOException {
        String player = Files.readString(ZOMBIE_PLAYER_EVENTS);
        String food = Files.readString(ZOMBIE_FOOD_EVENTS);
        assertFalse(player.contains("NeoForge.EVENT_BUS.post"), "edited handler must not post on the raw bus");
        assertFalse(food.contains("NeoForge.EVENT_BUS.post"), "edited handler must not post on the raw bus");
    }

    @Test
    void neutralWhenEmptyGate_baseModNeverCallsIZombieExtensionsRegister() throws IOException, java.io.UncheckedIOException {
        // Only addons/tests call register(); the base mod's Phase-1 wiring must never register a provider.
        try (Stream<Path> paths = Files.walk(Path.of(MAIN))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            assertFalse(Files.readString(p).contains("IZombieExtensions.register("),
                                    "main source must not call IZombieExtensions.register(): " + p);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
        // The provider lists are initialized empty with no static block.
        String ext = read("api/extension/IZombieExtensions.java");
        assertTrue(ext.contains("new CopyOnWriteArrayList<>()"), "provider lists must be initialized empty");
        assertFalse(ext.contains("static {"), "IZombieExtensions must have no static initializer block");
    }

    @Test
    void staticSideEffectsGate_noNewClassHasAStaticBlockTouchingNeoForgeOrRegister() throws IOException {
        for (String dir : new String[] {"api", "internal", "platform"}) {
            try (Stream<Path> paths = Files.walk(Path.of(MAIN + dir))) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String src = Files.readString(p);
                        if (src.contains("static {")) {
                            // A static block is permitted only if it does not call NeoForge / register / global mutation.
                            int idx = src.indexOf("static {");
                            String block = src.substring(idx, Math.min(src.length(), idx + 400));
                            assertFalse(block.contains("NeoForge") || block.contains("register("),
                                    "static block must not call NeoForge/register: " + p);
                        }
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @Test
    void fakePlayerGate_noConnectionReadsInApiInternalOrPlatform() throws IOException {
        for (String dir : new String[] {"api", "internal", "platform"}) {
            try (Stream<Path> paths = Files.walk(Path.of(MAIN + dir))) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String src = Files.readString(p);
                        assertFalse(src.contains(".connection") || src.contains("getConnection"),
                                "FakePlayer-safety: no connection reads allowed in " + p);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @Test
    void servicesHolderConstructsNeoForgeImplsWithoutAStaticBlock() throws IOException {
        String services = read("platform/Services.java");
        assertTrue(services.contains("new NeoForgeAttachmentService()") && services.contains("new NeoForgeEventBusService()"),
                "Services should statically construct the NeoForge impls via plain new");
        assertFalse(services.contains("static {"), "Services must not use a static initializer block");
    }

    @Test
    void registryFormSpecIsAnExperimentalPhase2Placeholder() throws IOException {
        String spec = read("api/registry/ZombieFormSpec.java");
        assertTrue(spec.contains("@ApiStatus.Experimental"), "the form-spec stub should be marked experimental");
        assertTrue(spec.contains("NOT usable in 1.0") || spec.contains("not usable in 1.0"),
                "the form-spec stub should document that it is not usable in 1.0");
    }

    @Test
    void attackerHookShipsExperimentalEnumBasedButIsNotWiredIntoMainHandlersInPhase1() throws IOException, java.io.UncheckedIOException {
        // A3: the IAttackerHook interface ships, but its query is DEFERRED — no handler calls attackerHooks() yet.
        assertTrue(Files.exists(Path.of(MAIN + "api/extension/IAttackerHook.java")), "IAttackerHook should ship");
        // FIX 7: the hook is now @Experimental and enum-based (AttackerDecision), not @Nullable Boolean.
        String hook = read("api/extension/IAttackerHook.java");
        assertTrue(hook.contains("@ApiStatus.Experimental"), "the attacker hook should be marked @ApiStatus.Experimental");
        assertTrue(hook.contains("AttackerDecision shouldAttack("),
                "the attacker hook should return the AttackerDecision enum");
        assertTrue(Files.exists(Path.of(MAIN + "api/extension/AttackerDecision.java")),
                "the AttackerDecision enum should ship");
        // Still DEFERRED: no handler queries attackerHooks() yet.
        try (Stream<Path> paths = Files.walk(Path.of(MAIN + "gameplay"))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    assertFalse(Files.readString(p).contains("IZombieExtensions.attackerHooks()"),
                            "the attacker hook query is deferred and must not be wired into a handler: " + p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
