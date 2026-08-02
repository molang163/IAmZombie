package dev.molang.iamzombieq.rules.herobrine;

/**
 * Pure, side-effect-free logic for the per-player Herobrine "dread escalation" arc.
 *
 * <p>The encounter walks each player through {@link Phase#DORMANT} → {@link Phase#OBSERVATION}
 * → {@link Phase#ESCALATION} → {@link Phase#LETHAL}. Early phases let the player live (Herobrine
 * vanishes and a sighting is recorded); only LETHAL kills. This gives the player at least one
 * non-lethal sighting before a lethal one, so "I looked before and lived, now looking kills"
 * reads as a learnable rule instead of randomness.
 *
 * <p>Everything here is intentionally free of Minecraft imports so it can be unit tested with
 * plain JUnit (see {@code HerobrineRulesTest}). The event layer collects runtime data, feeds it
 * to these functions, and performs the side effects.
 */
public final class HerobrineEncounter {
    /** Escalation phase for a single player's Herobrine encounter history. */
    public enum Phase {
        /** No meaningful history yet. */
        DORMANT,
        /** First sightings — silent, watched, never lethal. */
        OBSERVATION,
        /** Pressure builds — heartbeat onset, stronger omens, still non-lethal. */
        ESCALATION,
        /** The gaze (or an attack) now kills. */
        LETHAL
    }

    /** Default number of non-lethal sightings before reaching {@link Phase#ESCALATION}. */
    public static final int DEFAULT_ESCALATION_SIGHTINGS = 2;
    /** Default number of additional sightings (beyond escalation) before {@link Phase#LETHAL}. */
    public static final int DEFAULT_LETHAL_SIGHTINGS = 1;

    private HerobrineEncounter() {
    }

    /**
     * Phase for a player using the default thresholds.
     *
     * @param sightings       how many non-lethal sightings this player has accumulated
     * @param escalatedBefore whether this player has previously reached LETHAL at least once
     */
    public static Phase phaseFor(int sightings, boolean escalatedBefore) {
        return phaseFor(sightings, escalatedBefore, DEFAULT_ESCALATION_SIGHTINGS, DEFAULT_LETHAL_SIGHTINGS);
    }

    /**
     * Phase for a player given explicit thresholds.
     *
     * <p>Setting {@code escalationSightings} to 0 collapses the whole arc straight to LETHAL on
     * the very first encounter — the original "instant kill" behaviour, preserved for backward
     * compatibility / config.
     *
     * @param sightings           how many non-lethal sightings this player has accumulated
     * @param escalatedBefore     whether this player has previously reached LETHAL at least once
     *                            (a veteran is treated as already lethal)
     * @param escalationSightings sightings required to enter ESCALATION (and gate above OBSERVATION)
     * @param lethalSightings     additional sightings beyond escalation required to enter LETHAL
     */
    public static Phase phaseFor(int sightings, boolean escalatedBefore, int escalationSightings, int lethalSightings) {
        int escalate = Math.max(0, escalationSightings);
        int lethal = Math.max(0, lethalSightings);
        // A "0 escalation threshold" means there is no observation grace period at all.
        if (escalatedBefore || sightings >= escalate + lethal) {
            return Phase.LETHAL;
        }
        if (sightings >= escalate) {
            return Phase.ESCALATION;
        }
        return Phase.OBSERVATION;
    }

    /**
     * Whether a gaze/attack in this phase should make Herobrine vanish (and record a sighting)
     * rather than kill the player. True for every non-lethal phase.
     */
    public static boolean shouldVanishOnGaze(Phase phase) {
        return phase != Phase.LETHAL;
    }

    /** Whether the gaze/attack in this phase is the lethal one. */
    public static boolean isLethal(Phase phase) {
        return phase == Phase.LETHAL;
    }

    /**
     * Whether a recorded sighting has aged out of the memory window and should be forgotten,
     * decaying the player's accumulated dread back down.
     *
     * @param now    current game time (ticks)
     * @param last   game time of the most recent sighting (ticks)
     * @param window memory window length (ticks); &le; 0 means "never expires"
     */
    public static boolean isSightingExpired(long now, long last, long window) {
        if (window <= 0) {
            return false;
        }
        return now - last > window;
    }

    /**
     * Whether the player is still inside the post-lethal cooldown (during which Herobrine should
     * not be lethal again, to prevent farming repeated kills).
     *
     * @param now        current game time (ticks)
     * @param lastLethal game time of the most recent lethal encounter (ticks); &lt; 0 = never
     * @param cooldown   cooldown length (ticks); &le; 0 means "no cooldown"
     */
    public static boolean isOnLethalCooldown(long now, long lastLethal, long cooldown) {
        if (cooldown <= 0 || lastLethal < 0) {
            return false;
        }
        return now - lastLethal < cooldown;
    }

    /**
     * Phase-scaled omen intensity: how many lit blocks to extinguish, how many phantom footsteps
     * to play, and how long (ticks) the omen lasts before things are restored.
     *
     * <p>Pure data — the event layer caps each field with config maxima and applies the effects.
     */
    public static OmenIntensity omenIntensityFor(Phase phase) {
        return switch (phase) {
            case DORMANT, OBSERVATION -> new OmenIntensity(2, 1, 20 * 6);
            case ESCALATION -> new OmenIntensity(3, 2, 20 * 9);
            case LETHAL -> new OmenIntensity(4, 3, 20 * 12);
        };
    }

    /**
     * Phase-scaled, distance-scaled heartbeat period in ticks (lower = faster/more intense).
     *
     * <p>OBSERVATION (and below) returns {@code 0}, the contract for "do not play a heartbeat at
     * all — keep the dead silence". ESCALATION onsets a slow heartbeat; LETHAL is faster and the
     * heartbeat speeds up as the player closes the distance.
     *
     * @param phase    the local player's current encounter phase
     * @param distance distance (blocks) from the player to Herobrine
     */
    public static int heartbeatPeriodTicks(Phase phase, double distance) {
        if (phase == Phase.OBSERVATION || phase == Phase.DORMANT) {
            return 0;
        }
        // Clamp distance to the audible band [12, 28] used by the client mute scan.
        double clamped = Math.max(12.0, Math.min(28.0, distance));
        double t = (clamped - 12.0) / (28.0 - 12.0); // 0 at closest, 1 at farthest
        int min;
        int max;
        if (phase == Phase.LETHAL) {
            min = 8;   // 0.4s at point blank
            max = 26;  // ~1.3s far away
        } else { // ESCALATION
            min = 16;  // 0.8s at closest
            max = 40;  // 2.0s far away
        }
        return (int) Math.round(min + (max - min) * t);
    }

    /**
     * The perceptible cue, if any, to emit when a player's phase changes. Returns {@code null}
     * when no transition cue should fire (no upgrade, or upgrade into a phase with no cue).
     *
     * <p>Used to broadcast an unsettling subtitle/message so the escalation feels rule-based.
     */
    public static TransitionCue phaseTransitionCue(Phase previous, Phase next) {
        if (next.ordinal() <= previous.ordinal()) {
            return null; // only upgrades produce a cue
        }
        return switch (next) {
            case ESCALATION -> TransitionCue.BREATHING;
            case LETHAL -> TransitionCue.WATCHED;
            default -> null;
        };
    }

    /**
     * Immutable snapshot of a player's dread state — mirrors the four fields of
     * {@code HerobrineEncounterState} (state layer) using only primitives so the whole encounter
     * decision can run and be unit tested without any Minecraft runtime.
     */
    public record Snapshot(int sightings, long lastSightingTick, long lastLethalTick, boolean escalatedBefore) {
    }

    /** What the event layer should do after resolving one gaze/attack/projectile encounter. */
    public enum Action {
        /** Non-lethal sighting: Herobrine vanishes; the sighting is already recorded in the next snapshot. */
        CONTINUE,
        /** The lethal encounter: run the real encounter death; the next snapshot marks the veteran. */
        LETHAL
    }

    /**
     * Full outcome of {@link #resolveEncounter}: the snapshot to persist, the side effect to run,
     * the phase the player is in once this resolution is applied (for {@link Action#CONTINUE} the
     * phase including the just-recorded sighting; for {@link Action#LETHAL} always
     * {@link Phase#LETHAL}), and the perceptible upgrade cue ({@code null} when no cue should
     * fire — always {@code null} for {@link Action#LETHAL}).
     */
    public record Resolution(Snapshot nextSnapshot, Action action, Phase phase, TransitionCue cue) {
    }

    /**
     * Read-only phase query: the phase the player would be in after memory decay, WITHOUT
     * recording a new sighting. Single source of truth for "what phase is this player in right
     * now" (e.g. scaling spawn omens / the client heartbeat) — it never advances the arc.
     *
     * <p>Deliberately does not report the decayed count back for write-out: decay is persisted
     * only by {@link #resolveEncounter}, keeping this a pure query (matching the historical
     * read-only {@code currentPhase} semantics in the event layer).
     */
    public static Phase phaseAfterDecay(Snapshot snapshot, long now, int escalationSightings,
                                        int lethalSightings, long memoryWindow) {
        return phaseFor(decayedSightings(snapshot, now, memoryWindow), snapshot.escalatedBefore(),
                escalationSightings, lethalSightings);
    }

    /**
     * Single entry point resolving one encounter, migrated verbatim from the event layer:
     * memory decay → phase → lethal + cooldown decision → (non-lethal only) record the sighting
     * and derive the phase-upgrade cue.
     *
     * <ul>
     *   <li>{@link Action#LETHAL}: sightings are NOT incremented; {@code lastLethalTick = now};
     *       {@code escalatedBefore = true} (veteran forever); {@code lastSightingTick} unchanged;
     *       no cue.</li>
     *   <li>{@link Action#CONTINUE}: decayed sightings + 1; {@code lastSightingTick = now};
     *       lethal fields unchanged; cue = upgrade cue between the pre- and post-recording phases
     *       (may be {@code null}).</li>
     * </ul>
     */
    public static Resolution resolveEncounter(Snapshot snapshot, long now, int escalationSightings,
                                              int lethalSightings, long memoryWindow, long lethalCooldown) {
        // Memory decay: a sighting that aged out of the window resets the accumulated sightings,
        // but NOT escalatedBefore — once Herobrine has killed you it stays lethal to you (a veteran
        // is marked for good), matching the documented "veteran immediately lethal again" rule.
        int sightings = decayedSightings(snapshot, now, memoryWindow);
        Phase before = phaseAfterDecay(snapshot, now, escalationSightings, lethalSightings, memoryWindow);

        boolean lethal = isLethal(before) && !isOnLethalCooldown(now, snapshot.lastLethalTick(), lethalCooldown);
        if (lethal) {
            Snapshot next = new Snapshot(sightings, snapshot.lastSightingTick(), now, true);
            return new Resolution(next, Action.LETHAL, before, null);
        }

        // Non-lethal sighting: record it and report any phase upgrade as a cue.
        int recorded = sightings + 1;
        Phase after = phaseFor(recorded, snapshot.escalatedBefore(), escalationSightings, lethalSightings);
        Snapshot next = new Snapshot(recorded, now, snapshot.lastLethalTick(), snapshot.escalatedBefore());
        return new Resolution(next, Action.CONTINUE, after, phaseTransitionCue(before, after));
    }

    /** Sightings after applying memory decay (shared by the phase query and the full resolution). */
    private static int decayedSightings(Snapshot snapshot, long now, long memoryWindow) {
        if (snapshot.sightings() > 0 && isSightingExpired(now, snapshot.lastSightingTick(), memoryWindow)) {
            return 0;
        }
        return snapshot.sightings();
    }

    /** Phase-scaled omen instructions; all fields are pre-cap suggestions for the event layer. */
    public record OmenIntensity(int litBlocks, int footsteps, int durationTicks) {
    }

    /**
     * A perceptible phase-upgrade cue. {@link #subtitleKey()} maps to a lang key broadcast to the
     * player so the dread escalation reads as a learnable progression.
     */
    public enum TransitionCue {
        BREATHING("subtitles.iamzombieq.herobrine.breathing"),
        WATCHED("subtitles.iamzombieq.herobrine.watched");

        private final String subtitleKey;

        TransitionCue(String subtitleKey) {
            this.subtitleKey = subtitleKey;
        }

        public String subtitleKey() {
            return subtitleKey;
        }
    }
}
