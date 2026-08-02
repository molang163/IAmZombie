package dev.molang.iamzombieq.state;

/**
 * Immutable per-player Herobrine encounter accumulator driving the OBSERVATION → ESCALATION → LETHAL
 * dread arc. Formerly a private inner class in {@code HerobrineEvents} held in an in-memory map;
 * promoted to a durable per-player attachment (see {@code IAmZombieAttachments.HEROBRINE_ENCOUNTER})
 * so dread survives logout/restart and the player's own death → respawn ("veteran forever",
 * matching the documented "once Herobrine has killed you it stays lethal" rule). Server-thread only.
 *
 * <p>Now a record: updates go through the {@code with*} copy helpers (or the canonical constructor)
 * and are persisted by re-setting the attachment, mirroring the {@code PlayerZombieData} pattern.
 */
public record HerobrineEncounterState(int sightings, long lastSightingTick, long lastLethalTick,
                                      boolean escalatedBefore) {
    /**
     * Fresh state matching the previous EncounterState defaults (0 / MIN_VALUE / -1 / false).
     * Kept explicit so the attachment default supplier can stay a {@code HerobrineEncounterState::new}
     * no-arg constructor reference.
     */
    public HerobrineEncounterState() {
        this(0, Long.MIN_VALUE, -1L, false);
    }

    /** Copy with the accumulated sightings decayed back to zero (memory-window expiry). */
    public HerobrineEncounterState withSightingsReset() {
        return new HerobrineEncounterState(0, lastSightingTick, lastLethalTick, escalatedBefore);
    }

    /** Copy with one more non-lethal sighting recorded at {@code tick}. */
    public HerobrineEncounterState withSightingRecorded(long tick) {
        return new HerobrineEncounterState(sightings + 1, tick, lastLethalTick, escalatedBefore);
    }

    /** Copy marking a lethal encounter at {@code tick} — the veteran flag is set for good. */
    public HerobrineEncounterState withLethalTriggered(long tick) {
        return new HerobrineEncounterState(sightings, lastSightingTick, tick, true);
    }
}
