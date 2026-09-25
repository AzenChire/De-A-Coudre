package fr.fixemy.deacoudre.game;

import org.bukkit.Material;

/**
 * Block placed by a player when they land in the water.
 *
 * @param block       solid block material
 * @param displayName MiniMessage display name (from messages.yml, section colors)
 */
public record PlayerColor(Material block, String displayName) {
}
