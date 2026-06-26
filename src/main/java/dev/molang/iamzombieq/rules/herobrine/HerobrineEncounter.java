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
