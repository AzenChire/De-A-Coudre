package fr.fixemy.deacoudre.config;

import java.util.Locale;

/**
 * Every configurable sound of the mini-game (section {@code sounds} of config.yml).
 */
public enum GameSound {
    JOIN,
    COUNTDOWN,
    START,
    TURN,
    TICK,
    SUCCESS,
    /** Personal "Dé à Coudre" (1x1 hole) sound, played to the jumper only. */
    PERFECT_JUMP,
    /** Personal confirmation when a block is chosen in the selector. */
    BLOCK_SELECT,
    LIFE_LOST,
    ELIMINATION,
    VICTORY,
    END;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
