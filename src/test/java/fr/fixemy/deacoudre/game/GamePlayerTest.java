package fr.fixemy.deacoudre.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePlayerTest {

    @Test
    @DisplayName("7. Dé à Coudre at max-lives: no extra life")
    void gainLifeRespectsMax() {
        GamePlayer player = new GamePlayer(UUID.randomUUID(), "Loan");
        player.setLives(2);
        assertTrue(player.gainLife(3));
        assertEquals(3, player.lives());
        assertFalse(player.gainLife(3), "already at max-lives");
        assertEquals(3, player.lives());
    }
}
