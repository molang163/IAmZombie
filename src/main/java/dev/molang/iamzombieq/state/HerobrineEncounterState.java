package dev.molang.iamzombieq.state;

/**
 * Mutable per-player Herobrine encounter accumulator driving the OBSERVATION → ESCALATION → LETHAL
 * dread arc. Formerly a private inner class in {@code HerobrineEvents} held in an in-memory map;
 * promoted to a durable per-player attachment (see {@code IAmZombieAttachments.HEROBRINE_ENCOUNTER})
 * so dread survives logout/restart and the player's own death → respawn ("veteran forever",
 * matching the documented "once Herobrine has killed you it stays lethal" rule). Server-thread only.
 */
public final class HerobrineEncounterState {
    public int sightings = 0;
    public long lastSightingTick = Long.MIN_VALUE;
    public long lastLethalTick = -1L;
    public boolean escalatedBefore = false;

    /** Fresh state matching the previous EncounterState defaults (0 / MIN_VALUE / -1 / false). */
    public HerobrineEncounterState() {
    }

    /** Copy constructor used to carry dread across the player's own death → respawn clone. */
    public HerobrineEncounterState(int sightings, long lastSightingTick, long lastLethalTick, boolean escalatedBefore) {
        this.sightings = sightings;
        this.lastSightingTick = lastSightingTick;
        this.lastLethalTick = lastLethalTick;
        this.escalatedBefore = escalatedBefore;
    }
}
