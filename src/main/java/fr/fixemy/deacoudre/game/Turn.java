package fr.fixemy.deacoudre.game;

import java.util.UUID;

/**
 * State of one turn. A turn is resolved exactly once ({@link #resolve()}
 * returns false on any later attempt), which makes double validation impossible
 * even if a move event, a damage event and the timer fire in the same tick.
 */
final class Turn {

    enum Phase {
        /** Just teleported on the platform, briefly frozen. */
        PREPARING,
        /** On the platform, may jump. */
        READY,
        /** Left the platform, falling. */
        FALLING,
        /** Success or failure already handled. */
        RESOLVED
    }

    private final long id;
    private final GamePlayer player;
    private final int duration;
    private Phase phase = Phase.PREPARING;
    private int secondsLeft;
    private int graceLeft;

    Turn(long id, GamePlayer player, int duration, int grace) {
        this.id = id;
        this.player = player;
        this.duration = duration;
        this.secondsLeft = duration;
        this.graceLeft = grace;
    }

    int duration() {
        return duration;
    }

    long id() {
        return id;
    }

    GamePlayer player() {
        return player;
    }

    UUID playerId() {
        return player.uuid();
    }

    Phase phase() {
        return phase;
    }

    void setPhase(Phase phase) {
        if (this.phase != Phase.RESOLVED) {
            this.phase = phase;
        }
    }

    boolean isResolved() {
        return phase == Phase.RESOLVED;
    }

    /**
     * @return true only for the first call
     */
    boolean resolve() {
        if (phase == Phase.RESOLVED) {
            return false;
        }
        phase = Phase.RESOLVED;
        return true;
    }

    int secondsLeft() {
        return secondsLeft;
    }

    void decrementSeconds() {
        if (secondsLeft > 0) {
            secondsLeft--;
        }
    }

    /**
     * @return true when the fall grace period is over
     */
    boolean consumeGrace() {
        return --graceLeft <= 0;
    }
}
