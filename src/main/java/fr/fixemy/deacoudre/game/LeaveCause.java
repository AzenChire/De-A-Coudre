package fr.fixemy.deacoudre.game;

/**
 * Why a player is removed from a session.
 */
public enum LeaveCause {
    /** /dac leave. */
    COMMAND(true),
    /** Disconnection or kick. */
    QUIT(true),
    /** Teleported away by another plugin while external-teleport is LEAVE. */
    EXTERNAL_TELEPORT(false),
    /** The lobby teleport failed when joining. */
    JOIN_FAILED(true);

    private final boolean restoreLocation;

    LeaveCause(boolean restoreLocation) {
        this.restoreLocation = restoreLocation;
    }

    public boolean restoreLocation() {
        return restoreLocation;
    }
}
