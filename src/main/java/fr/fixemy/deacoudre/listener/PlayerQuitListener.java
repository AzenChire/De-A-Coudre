package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.game.LeaveCause;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Disconnection (or kick): the player leaves the game and is restored before
 * the server saves their data. During a game this counts as an elimination and
 * the next turn starts if it was their turn.
 */
public final class PlayerQuitListener implements Listener {

    private final DeACoudrePlugin plugin;

    public PlayerQuitListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        plugin.games().leave(event.getPlayer(), LeaveCause.QUIT);
        plugin.players().handleQuit(event.getPlayer());
    }
}
