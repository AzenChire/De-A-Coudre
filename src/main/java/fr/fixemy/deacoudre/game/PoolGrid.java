package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.arena.PoolScanner;
import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Logical state of the pool surface during a game.
 * <ul>
 *     <li><b>grid</b>: every column of the surface layer inside the pool selection
 *     that holds water or a block (air columns are not part of it);</li>
 *     <li><b>water cells</b>: the columns that were water at the start, the only
 *     ones the game modifies, saves and restores;</li>
 *     <li><b>free</b>: water cells nobody has filled yet.</li>
 * </ul>
 * Everything is decided from this state, never from the material or the
 * physical properties of the blocks in the world.
 */
public final class PoolGrid {

    private static final int[][] ORTHOGONAL = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};

    private final Cuboid region;
    private final int surfaceY;
    private final Set<Long> grid = new HashSet<>();
    private final Set<Long> waterCells = new HashSet<>();
    private final Set<Long> free = new HashSet<>();

    PoolGrid(Cuboid region, int surfaceY, Collection<BlockPos> water, Collection<Long> filledColumns) {
        this.region = region;
        this.surfaceY = surfaceY;
        for (BlockPos cell : water) {
            long key = cell.columnKey();
            grid.add(key);
            waterCells.add(key);
            free.add(key);
        }
        grid.addAll(filledColumns);
    }

    /**
     * Scans the pool region (and only it).
     *
     * @return the grid, or {@code null} when the region contains no water
     */
    public static @Nullable PoolGrid scan(World world, Cuboid region) {
        OptionalInt surface = PoolScanner.findSurfaceY(world, region);
        if (surface.isEmpty()) {
            return null;
        }
        int surfaceY = surface.getAsInt();
        List<BlockPos> water = PoolScanner.waterCells(world, region, surfaceY);
        return water.isEmpty() ? null
                : new PoolGrid(region, surfaceY, water, PoolScanner.filledColumns(world, region, surfaceY));
    }

    public Cuboid region() {
        return region;
    }

    public int surfaceY() {
        return surfaceY;
    }

    public boolean isFree(int x, int z) {
        return free.contains(BlockPos.columnKey(x, z));
    }

    /**
     * A cell of the grid that is no longer available water: filled by a player
     * during the game, or already filled with any block when the game started.
     */
    public boolean isOccupied(int x, int z) {
        long key = BlockPos.columnKey(x, z);
        return grid.contains(key) && !free.contains(key);
    }

    /**
     * "Dé à Coudre": a free water cell whose four orthogonal neighbours (north,
     * south, east, west; diagonals ignored) all belong to the grid and are
     * occupied. A cell on the edge of the pool never qualifies (its missing
     * neighbour is outside the grid), nor does one next to available water.
     * Must be called before {@link #occupy} for the same cell.
     */
    public boolean isIsolatedHole(int x, int z) {
        if (!isFree(x, z)) {
            return false;
        }
        for (int[] offset : ORTHOGONAL) {
            if (!isOccupied(x + offset[0], z + offset[1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Marks a cell as used.
     *
     * @return false if the cell was not free (prevents double validation)
     */
    public boolean occupy(int x, int z) {
        return free.remove(BlockPos.columnKey(x, z));
    }

    public int freeCount() {
        return free.size();
    }

    /**
     * Number of playable (initially water) cells.
     */
    public int totalCells() {
        return waterCells.size();
    }

    /**
     * Positions of the initially water cells: the only blocks the game changes.
     */
    public List<BlockPos> cellPositions() {
        List<BlockPos> positions = new ArrayList<>(waterCells.size());
        for (long key : waterCells) {
            positions.add(new BlockPos((int) (key >> 32), surfaceY, (int) key));
        }
        return positions;
    }
}
