package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.game.GameSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Deaths of participants (damage is cancelled, so only /kill-like deaths get here):
 * nothing is dropped, the jumper fails, and the player respawns in the arena.
 */
public final class PlayerDeathListener implements Listener {

    private final DeACoudrePlugin plugin;

    public PlayerDeathListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session == null) {
            return;
        }
        // Never drop or lose anything: the real inventory is in the snapshot anyway.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.deathMessage(null);
        session.runSafely(() -> session.handleDeath(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.players().handleRespawn(event)) {
            return;
        }
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location location = session.handleRespawn(player);
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }
}
