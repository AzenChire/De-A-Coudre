package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Arena worlds being loaded / unloaded while the server runs.
 */
public final class WorldListener implements Listener {

    private final DeACoudrePlugin plugin;

    public WorldListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(WorldUnloadEvent event) {
        String name = event.getWorld().getName();
        for (Arena arena : plugin.arenas().all()) {
            // The world is still loaded here: players and pool can be restored normally.
            if (name.equals(arena.worldName()) && plugin.games().stop(arena, true)) {
                plugin.getLogger().warning("World " + name + " unloaded: game in arena " + arena.name() + " stopped.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(WorldLoadEvent event) {
        plugin.poolBackups().recover(event.getWorld());
    }
}
