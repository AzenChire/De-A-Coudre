package fr.fixemy.deacoudre.gui;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.config.MessageManager;
import fr.fixemy.deacoudre.game.GamePlayer;
import fr.fixemy.deacoudre.game.GameSession;
import fr.fixemy.deacoudre.game.PlayerColor;
import fr.fixemy.deacoudre.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * "Choose your block" menu of one player in a lobby.
 * <p>
 * The inventory is identified by this holder (never by its title) and every
 * click on it is cancelled by the listener: its items can never be taken. The
 * menu only renders the state kept by the {@link GameSession}; it is re-rendered
 * when a player of the lobby picks a block or leaves.
 */
public final class BlockSelectorMenu implements InventoryHolder {

    /** A double chest: more blocks than that are ignored (see ConfigManager). */
    public static final int MAX_SLOTS = 54;

    private final DeACoudrePlugin plugin;
    private final GameSession session;
    private final UUID viewer;
    private final Inventory inventory;
    private final Material[] slots;

    public BlockSelectorMenu(DeACoudrePlugin plugin, GameSession session, UUID viewer) {
        this.plugin = plugin;
        this.session = session;
        this.viewer = viewer;
        int blocks = Math.min(plugin.settings().colors().size(), MAX_SLOTS);
        int size = Math.max(1, (blocks + 8) / 9) * 9;
        this.slots = new Material[size];
        this.inventory = Bukkit.createInventory(this, size, plugin.messages().get("selector.title"));
        render();
    }

    public GameSession session() {
        return session;
    }

    public UUID viewer() {
        return viewer;
    }

    /**
     * @return the block displayed in this slot of the menu, if any
     */
    public @Nullable Material blockAt(int slot) {
        return slot >= 0 && slot < slots.length ? slots[slot] : null;
    }

    public void render() {
        MessageManager messages = plugin.messages();
        GamePlayer self = session.gamePlayer(viewer);
        PlayerColor current = self != null ? self.color() : null;
        List<Material> blocks = plugin.settings().colors();
        inventory.clear();
        for (int slot = 0; slot < slots.length; slot++) {
            Material block = slot < blocks.size() ? blocks.get(slot) : null;
            slots[slot] = block;
            if (block == null) {
                continue;
            }
            String owner = session.blockOwner(block, viewer);
            boolean selected = current != null && current.block() == block;
            String state = selected ? "selected" : owner != null ? "taken" : "available";
            Placeholders placeholders = Placeholders.create()
                    .raw("block", messages.colorName(block))
                    .text("player", owner != null ? owner : "");
            ItemStack item = ItemStack.of(block);
            item.editMeta(meta -> {
                meta.itemName(messages.item(messages.colorName(block), placeholders));
                meta.lore(messages.itemLines("selector.lore-" + state, placeholders));
                if (selected) {
                    meta.setEnchantmentGlintOverride(true);
                }
            });
            inventory.setItem(slot, item);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
