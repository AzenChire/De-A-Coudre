package fr.fixemy.deacoudre.game;

/**
 * State of a participant inside a {@link GameSession}.
 */
public enum PlayerStatus {
    /** In the lobby, the game has not started yet. */
    WAITING,
    /** Still in the game. */
    ALIVE,
    /** Eliminated, watching the game as a spectator. */
    ELIMINATED
}
