package fr.fixemy.deacoudre.gui;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.util.Placeholders;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Hotbar item given in the lobby to open the {@link BlockSelectorMenu}. It is
 * tagged in its persistent data so it is recognized regardless of its name, and
 * it only ever exists in the temporary lobby inventory (the real inventory is
 * saved in the player snapshot and restored over it).
 */
public final class BlockSelectorItem {

    private BlockSelectorItem() {
    }

    public static ItemStack create(DeACoudrePlugin plugin) {
        ItemStack item = ItemStack.of(plugin.settings().lobbyItemMaterial());
        item.editMeta(meta -> {
            meta.itemName(plugin.messages().item(plugin.messages().raw("selector.item-name"), Placeholders.create()));
            meta.lore(plugin.messages().itemLines("selector.item-lore", Placeholders.create()));
            meta.getPersistentDataContainer().set(plugin.selectorItemKey(), PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isSelectorItem(DeACoudrePlugin plugin, @Nullable ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(plugin.selectorItemKey());
    }
}
