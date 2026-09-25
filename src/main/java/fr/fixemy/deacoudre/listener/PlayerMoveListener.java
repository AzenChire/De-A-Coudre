package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.game.GameSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Movement of participants only (one map lookup for everybody else).
 * <ul>
 *     <li>NORMAL: freezes the jumper at the start of the turn and keeps waiting
 *     players / spectators inside the arena.</li>
 *     <li>MONITOR: jump detection, on the final movement accepted by the server.</li>
 * </ul>
 */
public final class PlayerMoveListener implements Listener {

    private final DeACoudrePlugin plugin;

    public PlayerMoveListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMoveControl(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.turns().isFrozen(player.getUniqueId())) {
            // Cancelling puts the player back without a PlayerTeleportEvent
            // (changing "to" would trigger a PLUGIN teleport caught by the teleport listener).
            event.setCancelled(true);
            return;
        }
        if (event.hasChangedBlock() && !session.turns().isActive(player.getUniqueId())) {
            Location to = event.getTo();
            session.runSafely(() -> session.enforceArea(player, to));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveMonitor(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session != null && session.turns().isActive(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            session.runSafely(() -> session.turns().handleMove(player, from, to));
        }
    }
}
