package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.gui.BlockSelectorMenu;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Locale;

/**
 * Blocks every interaction that is not needed by the mini-game, for
 * participants only (lobby, alive and spectators).
 */
public final class InteractionListener implements Listener {

    private final DeACoudrePlugin plugin;

    public InteractionListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isParticipant(Entity entity) {
        return entity instanceof Player player && plugin.games().isInGame(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isParticipant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isParticipant(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isParticipant(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Containers (chests, furnaces...) cannot be opened by participants. The plugin's
     * own menus are chest-type inventories too: they must never be blocked here,
     * otherwise openInventory() is silently cancelled.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof BlockSelectorMenu) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING && isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (event.isFlying() && player.getGameMode() != GameMode.SPECTATOR && isParticipant(player)) {
            event.setCancelled(true);
            player.setAllowFlight(false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (!settings.blockCommands() || !isParticipant(player) || player.hasPermission("deacoudre.admin")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        if (!settings.allowedCommands().contains(label)) {
            event.setCancelled(true);
            plugin.messages().send(player, "errors.command-blocked");
        }
    }
}
