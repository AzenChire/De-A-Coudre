package fr.fixemy.deacoudre.arena;

/**
 * Lifecycle of an arena.
 */
public enum ArenaState {
    /** Enabled, waiting for players (lobby open). */
    WAITING,
    /** Enough players, countdown running (lobby still open). */
    STARTING,
    /** Game in progress, registrations locked. */
    PLAYING,
    /** Game finished, results displayed. */
    ENDING,
    /** Players and blocks are being restored. */
    RESETTING,
    /** Disabled by an administrator or invalid configuration. */
    DISABLED;

    public boolean isJoinable() {
        return this == WAITING || this == STARTING;
    }

    public String messageKey() {
        return "states." + name();
    }
}
