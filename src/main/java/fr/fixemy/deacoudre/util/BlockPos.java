package fr.fixemy.deacoudre.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Immutable integer block coordinates (world-less).
 */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Packs an (x, z) column into a single long, used as a key for pool cells.
     */
    public static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public long columnKey() {
        return columnKey(x, z);
    }

    public Block toBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
