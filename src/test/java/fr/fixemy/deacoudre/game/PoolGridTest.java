package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Dé à Coudre" (1x1 hole) detection, from the logical pool state only.
 * <p>
 * A layout is a top view of the surface layer, one string per z row, one char
 * per x column; the pool selection is exactly the layout:
 * {@code W} water (free cell), {@code .} air (not part of the grid), any other
 * letter a block already present when the game starts (G glass, C concrete,
 * L wool, S stone...). The material letter never matters.
 */
class PoolGridTest {

    private static final int Y = 64;

    private static PoolGrid grid(String... rows) {
        List<BlockPos> water = new ArrayList<>();
        List<Long> filled = new ArrayList<>();
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                char c = rows[z].charAt(x);
                if (c == 'W') {
                    water.add(new BlockPos(x, Y, z));
                } else if (c != '.') {
                    filled.add(BlockPos.columnKey(x, z));
                }
            }
        }
        Cuboid region = new Cuboid(0, Y, 0, rows[0].length() - 1, Y, rows.length - 1);
        return new PoolGrid(region, Y, water, filled);
    }

    /** Fills every free cell except (x, z), as players would during a game. */
    private static void fillAllBut(PoolGrid grid, int keepX, int keepZ, String... rows) {
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                if (rows[z].charAt(x) == 'W' && !(x == keepX && z == keepZ)) {
                    assertTrue(grid.occupy(x, z));
                }
            }
        }
    }

    @Test
    @DisplayName("1. hole surrounded by concrete placed by players -> bonus")
    void concreteFilledByPlayers() {
        String[] rows = {"WWWWW", "WWWWW", "WWWWW", "WWWWW", "WWWWW"};
        PoolGrid grid = grid(rows);
        fillAllBut(grid, 2, 2, rows);
        assertTrue(grid.isIsolatedHole(2, 2));
    }

    @Test
    @DisplayName("2. hole surrounded by glass already in the pool -> bonus")
    void surroundedByGlass() {
        PoolGrid grid = grid(
                "WWWWW",
                "WGGGW",
                "WGWGW",
                "WGGGW",
                "WWWWW");
        assertTrue(grid.isIsolatedHole(2, 2));
    }

    @Test
    @DisplayName("3. mix of glass, concrete, wool, stone and player blocks -> bonus")
    void mixedBlocks() {
        String[] rows = {
                "WWWWW",
                "WWGWW",
                "WCWLW",
                "WWWWW",
                "WWWWW"};
        PoolGrid grid = grid(rows);
        // South neighbour filled by a player during the game.
        assertTrue(grid.occupy(2, 3));
        assertTrue(grid.isIsolatedHole(2, 2));
        // Only pre-existing blocks of four different kinds around the hole.
        assertTrue(grid(
                "SGL",
                "CWG",
                "LSC").isIsolatedHole(1, 1));
    }

    @Test
    @DisplayName("4. one orthogonal neighbour still water -> no bonus")
    void neighbourStillWater() {
        PoolGrid grid = grid(
                "GGGGG",
                "GGGGG",
                "GGWWG",
                "GGGGG",
                "GGGGG");
        assertFalse(grid.isIsolatedHole(2, 2));
        assertFalse(grid.isIsolatedHole(3, 2));
        // Once the neighbour is filled, the remaining cell becomes a hole.
        assertTrue(grid.occupy(3, 2));
        assertTrue(grid.isIsolatedHole(2, 2));
    }

    @Test
    @DisplayName("5. hole on the edge of the pool -> no bonus")
    void edgeOfPool() {
        PoolGrid grid = grid(
                "GGG",
                "WGG",
                "GGG");
        assertFalse(grid.isIsolatedHole(0, 1), "west neighbour is outside the pool selection");
        // Air is not a filled cell either.
        PoolGrid withAir = grid(
                "GGGGG",
                "GGGGG",
                "G.WGG",
                "GGGGG",
                "GGGGG");
        assertFalse(withAir.isIsolatedHole(2, 2));
    }

    @Test
    @DisplayName("6. diagonals still water, the four orthogonal neighbours filled -> bonus")
    void diagonalsIgnored() {
        PoolGrid grid = grid(
                "WGW",
                "GWG",
                "WGW");
        assertTrue(grid.isIsolatedHole(1, 1), "diagonal water cells do not matter");
        PoolGrid bigger = grid(
                "GGGGG",
                "GWGWG",
                "GGWGG",
                "GWGWG",
                "GGGGG");
        assertTrue(bigger.isIsolatedHole(2, 2));
    }

    @Test
    @DisplayName("9. a cell can give the bonus only once (checked before occupying it)")
    void noDoubleTrigger() {
        PoolGrid grid = grid(
                "GGG",
                "GGG",
                "GGG",
                "GWG",
                "GGG");
        assertTrue(grid.isIsolatedHole(1, 3));
        assertTrue(grid.occupy(1, 3));
        assertFalse(grid.occupy(1, 3), "second validation of the same cell is refused");
        assertFalse(grid.isIsolatedHole(1, 3), "an occupied cell is never a hole");
        assertEquals(0, grid.freeCount());
        assertEquals(1, grid.totalCells(), "only water cells are playable / restored");
    }
}
