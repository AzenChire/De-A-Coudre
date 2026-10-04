package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.gui.BlockSelectorItem;
import fr.fixemy.deacoudre.util.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Restores players whose state could not be restored before (crash, failed teleport...).
 */
public final class PlayerJoinListener implements Listener {

    private final DeACoudrePlugin plugin;

    public PlayerJoinListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        removeStraySelectorItems(player);
        plugin.players().handleJoin(player);
        scheduleAutoJoin(player);
    }

    /**
     * auto-join: the player joins the configured arena (or any joinable one) shortly
     * after connecting, once a possible state recovery has been applied. The usual
     * join rules and messages apply (permission, arena full, game already started...).
     */
    private void scheduleAutoJoin(Player player) {
        Settings settings = plugin.settings();
        if (!settings.autoJoin()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Settings current = plugin.settings();
            if (!current.autoJoin() || !player.isOnline() || !player.hasPermission("deacoudre.play")
                    || plugin.games().isInGame(player.getUniqueId())) {
                return;
            }
            if (current.autoJoinArena().isEmpty()) {
                plugin.games().autoJoin(player);
                return;
            }
            Arena arena = plugin.arenas().get(current.autoJoinArena());
            if (arena == null) {
                plugin.getLogger().warning("auto-join.arena '" + current.autoJoinArena() + "' does not exist.");
                plugin.messages().send(player, "errors.arena-not-found",
                        Placeholders.create().text("arena", current.autoJoinArena()));
                return;
            }
            plugin.debug("Auto-join of " + player.getName() + " into " + arena.name());
            plugin.games().join(player, arena);
        }, settings.autoJoinDelayTicks());
    }

    /**
     * Safety net: a lobby item can only exist in a lobby inventory, never outside a game
     * (e.g. after a server crash before the inventory could be restored).
     */
    private void removeStraySelectorItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (BlockSelectorItem.isSelectorItem(plugin, contents[slot])) {
                inventory.setItem(slot, ItemStack.empty());
            }
        }
    }
}
