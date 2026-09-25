package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Reads the pool region of an arena. Only the region itself is inspected,
 * never the surrounding world.
 */
public final class PoolScanner {

    private PoolScanner() {
    }

    /**
     * The jump surface is the highest layer of the region that contains water.
     * This lets administrators select the pool loosely (e.g. one layer of air above
     * the water) while the grid is still the real water surface.
     */
    public static OptionalInt findSurfaceY(World world, Cuboid region) {
        for (int y = region.maxY(); y >= region.minY(); y--) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                        return OptionalInt.of(y);
                    }
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Every water block of the surface layer: these are the playable cells.
     */
    public static List<BlockPos> waterCells(World world, Cuboid region, int surfaceY) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                if (world.getBlockAt(x, surfaceY, z).getType() == Material.WATER) {
                    cells.add(new BlockPos(x, surfaceY, z));
                }
            }
        }
        return cells;
    }

    /**
     * Columns of the surface layer already filled when the game starts (any block
     * except water and air: glass, stone, decoration, rim inside the selection...).
     * They are part of the pool grid as occupied cells. The material is irrelevant.
     */
    public static List<Long> filledColumns(World world, Cuboid region, int surfaceY) {
        List<Long> columns = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                Material type = world.getBlockAt(x, surfaceY, z).getType();
                if (type != Material.WATER && !type.isAir()) {
                    columns.add(BlockPos.columnKey(x, z));
                }
            }
        }
        return columns;
    }
}
