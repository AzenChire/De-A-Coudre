package fr.fixemy.deacoudre.game;

import org.jetbrains.annotations.Nullable;

/**
 * Why a player was eliminated. Each reason maps to a message of messages.yml.
 */
public enum FailReason {
    /** Landed on an already placed block (or a non playable cell of the pool). */
    HIT_BLOCK("game.fail.hit-block"),
    /** Landed on the rim / the ground / water outside of the pool. */
    OUT_OF_POOL("game.fail.out-of-pool"),
    /** Left the allowed jump zone. */
    OUT_OF_ZONE("game.fail.out-of-zone"),
    /** Died during the game. */
    DEATH("game.eliminated"),
    /** Did not jump in time. */
    TIMEOUT("game.timeout"),
    /** Left the game (command, disconnection, external teleport): already announced. */
    QUIT(null);

    private final @Nullable String messageKey;

    FailReason(@Nullable String messageKey) {
        this.messageKey = messageKey;
    }

    public @Nullable String messageKey() {
        return messageKey;
    }
}
