package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.config.GameSound;
import fr.fixemy.deacoudre.game.GameSession;
import fr.fixemy.deacoudre.gui.BlockSelectorItem;
import fr.fixemy.deacoudre.gui.BlockSelectorMenu;
import fr.fixemy.deacoudre.util.Placeholders;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

/**
 * Block selector: opening from the lobby item and clicks in the menu.
 * <p>
 * Every click / drag involving the menu is cancelled (whatever the click type,
 * shift-click, number key, double click...), so nothing can be taken from or put
 * into it. The selection itself only changes the in-memory session state.
 */
public final class BlockSelectorListener implements Listener {

    private final DeACoudrePlugin plugin;

    public BlockSelectorListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !BlockSelectorItem.isSelectorItem(plugin, event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        GameSession session = plugin.games().session(player.getUniqueId());
        plugin.debug("Lobby selector item used by " + player.getName() + " (" + event.getAction() + "), session: "
                + (session != null ? session.arena().name() : "none"));
        if (session != null) {
            session.runSafely(() -> session.openBlockSelector(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BlockSelectorMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(menu.viewer())
                || event.getClickedInventory() != top) {
            return;
        }
        Material block = menu.blockAt(event.getRawSlot());
        GameSession session = menu.session();
        if (block == null || session.isDisposed()) {
            return;
        }
        session.runSafely(() -> {
            Placeholders placeholders = Placeholders.create().raw("block", plugin.messages().colorName(block));
            switch (session.selectBlock(player, block)) {
                case SELECTED -> {
                    player.closeInventory();
                    plugin.messages().send(player, "selector.selected", placeholders);
                    plugin.configManager().playSound(player, GameSound.BLOCK_SELECT);
                }
                case TAKEN -> plugin.messages().send(player, "selector.taken", placeholders);
                case NOT_ALLOWED -> plugin.messages().send(player, "selector.not-allowed", placeholders);
                case NOT_IN_LOBBY -> {
                    player.closeInventory();
                    plugin.messages().send(player, "selector.not-in-lobby");
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BlockSelectorMenu) {
            event.setCancelled(true);
        }
    }
}
