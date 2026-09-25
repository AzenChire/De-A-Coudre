package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.game.GameSession;
import fr.fixemy.deacoudre.game.LeaveCause;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.EnumSet;
import java.util.Set;

/**
 * Teleports of participants that were not made by the plugin itself.
 * <ul>
 *     <li>Plugin teleports are flagged internally and always allowed (no loop).</li>
 *     <li>Pearls, portals, consumables...: cancelled.</li>
 *     <li>Spectators may use the spectator menu only inside the arena area.</li>
 *     <li>Other plugins / commands moving a player away: cancelled, or the player
 *     leaves the game, depending on {@code protection.external-teleport}.</li>
 * </ul>
 */
public final class TeleportListener implements Listener {

    private static final Set<TeleportCause> ALWAYS_BLOCKED = EnumSet.of(
            TeleportCause.ENDER_PEARL, TeleportCause.CONSUMABLE_EFFECT, TeleportCause.NETHER_PORTAL,
            TeleportCause.END_PORTAL, TeleportCause.END_GATEWAY, TeleportCause.EXIT_BED);

    private final DeACoudrePlugin plugin;

    public TeleportListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session == null || plugin.players().isInternalTeleport(player.getUniqueId())) {
            return;
        }
        TeleportCause cause = event.getCause();
        boolean far = isFar(session, event.getTo());
        if (cause == TeleportCause.SPECTATE) {
            if (far) {
                event.setCancelled(true);
            }
            return;
        }
        if (ALWAYS_BLOCKED.contains(cause)) {
            event.setCancelled(true);
            return;
        }
        if (!far) {
            // Small adjustments (e.g. the server putting a frozen jumper back) are fine,
            // except for a jumper that may already be falling: it would corrupt the jump detection.
            if (session.turns().isActive(player.getUniqueId()) && !session.turns().isFrozen(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.settings().externalTeleport() == Settings.ExternalTeleportMode.CANCEL) {
            event.setCancelled(true);
            plugin.messages().send(player, "errors.teleport-blocked");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportMonitor(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        if (session == null || plugin.players().isInternalTeleport(player.getUniqueId())
                || plugin.settings().externalTeleport() != Settings.ExternalTeleportMode.LEAVE
                || !isFar(session, event.getTo())) {
            return;
        }
        // The teleport goes through: the player leaves the game, everything but the location is restored.
        plugin.games().leave(player, LeaveCause.EXTERNAL_TELEPORT);
        plugin.messages().send(player, "game.left-teleport");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plugin.games().isInGame(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isFar(GameSession session, Location to) {
        Location anchor = session.anchor();
        World arenaWorld = session.arena().world();
        if (anchor == null) {
            return arenaWorld == null || to.getWorld() != arenaWorld;
        }
        int max = plugin.settings().maxDistance();
        return to.getWorld() != anchor.getWorld() || to.distanceSquared(anchor) > (double) max * max;
    }
}
