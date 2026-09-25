package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.gui.BlockSelectorItem;
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
