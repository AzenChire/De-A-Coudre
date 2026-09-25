package fr.fixemy.deacoudre.game;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A participant of a game session.
 */
public final class GamePlayer {

    private final UUID uuid;
    private final String name;
    private PlayerStatus status = PlayerStatus.WAITING;
    private @Nullable PlayerColor color;
    private int successfulJumps;
    /** Lives of the current game only (a session, hence a GamePlayer, never outlives one game). */
    private int lives;

    public GamePlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public PlayerStatus status() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public boolean isAlive() {
        return status == PlayerStatus.ALIVE;
    }

    public @Nullable PlayerColor color() {
        return color;
    }

    public void setColor(@Nullable PlayerColor color) {
        this.color = color;
    }

    public int lives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = Math.max(0, lives);
    }

    /**
     * @return remaining lives after losing one
     */
    public int loseLife() {
        if (lives > 0) {
            lives--;
        }
        return lives;
    }

    /**
     * @return false when the player already has {@code maxLives}
     */
    public boolean gainLife(int maxLives) {
        if (lives >= maxLives) {
            return false;
        }
        lives++;
        return true;
    }

    public int successfulJumps() {
        return successfulJumps;
    }

    public void addSuccessfulJump() {
        successfulJumps++;
    }
}
