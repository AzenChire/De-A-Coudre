package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Pure analysis of the jumper's movement. It does not change any state; the
 * {@link TurnManager} decides what to do with the {@link Outcome}.
 * <ul>
 *     <li>The whole path between two positions is sampled, so a fast fall
 *     (several blocks per tick) cannot skip the water surface.</li>
 *     <li>The exact entered cell is the column of the first water block met on
 *     that path, which stays correct with a pool several blocks deep.</li>
 *     <li>Landing is detected server side from the block collision shapes
 *     (Player#isOnGround is client controlled and deprecated).</li>
 * </ul>
 */
public final class JumpDetector {

    private static final double STEP = 0.25;
    private static final double GROUND_EPSILON = 0.05;

    public enum Kind {
        NONE,
        WATER,
        HIT_BLOCK,
        OUT_OF_POOL,
        OUT_OF_ZONE
    }

    public record Outcome(Kind kind, @Nullable BlockPos cell) {
        static final Outcome NONE = new Outcome(Kind.NONE, null);
        static final Outcome HIT_BLOCK = new Outcome(Kind.HIT_BLOCK, null);
        static final Outcome OUT_OF_POOL = new Outcome(Kind.OUT_OF_POOL, null);
        static final Outcome OUT_OF_ZONE = new Outcome(Kind.OUT_OF_ZONE, null);
    }

    private final PoolGrid grid;
    private final BoundingBox zone;

    public JumpDetector(PoolGrid grid, Location jump, int margin) {
        this.grid = grid;
        Cuboid region = grid.region();
        this.zone = new BoundingBox(
                Math.min(region.minX(), jump.getX()) - margin,
                region.minY() - 1,
                Math.min(region.minZ(), jump.getZ()) - margin,
                Math.max(region.maxX() + 1, jump.getX()) + margin,
                Math.max(region.maxY() + 1, jump.getY()) + margin + 4,
                Math.max(region.maxZ() + 1, jump.getZ()) + margin);
    }

    /**
     * @param box     bounding box of the player at {@code to}
     * @param falling whether the player already left the platform
     */
    public Outcome analyzeMove(World world, Location from, Location to, BoundingBox box, boolean falling) {
        Outcome water = scanPath(world, from, to);
        if (water != null) {
            return water;
        }
        if (!zone.contains(to.getX(), to.getY(), to.getZ())) {
            return Outcome.OUT_OF_ZONE;
        }
        if (falling && isSupported(world, box)) {
            return grid.region().containsColumn(to.getBlockX(), to.getBlockZ()) ? Outcome.HIT_BLOCK : Outcome.OUT_OF_POOL;
        }
        return Outcome.NONE;
    }

    /**
     * Fallback used by the turn timer: the player is in water, find where.
     */
    public Outcome analyzePosition(World world, Location location) {
        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (feet.getType() == Material.WATER) {
            return classifyWater(feet.getX(), feet.getY(), feet.getZ());
        }
        Block head = feet.getRelative(0, 1, 0);
        if (head.getType() == Material.WATER) {
            return classifyWater(head.getX(), head.getY(), head.getZ());
        }
        return Outcome.NONE;
    }

    /**
     * Landing position used when the server reports fall damage (hard landing).
     */
    public Outcome classifyLanding(Location location) {
        return grid.region().containsColumn(location.getBlockX(), location.getBlockZ()) ? Outcome.HIT_BLOCK : Outcome.OUT_OF_POOL;
    }

    private @Nullable Outcome scanPath(World world, Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance / STEP));
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(from.getX() + dx * t);
            int y = (int) Math.floor(from.getY() + dy * t);
            int z = (int) Math.floor(from.getZ() + dz * t);
            if (x == lastX && y == lastY && z == lastZ) {
                continue;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                return classifyWater(x, y, z);
            }
        }
        return null;
    }

    private Outcome classifyWater(int x, int y, int z) {
        Cuboid region = grid.region();
        if (region.containsColumn(x, z) && y >= region.minY() && y <= grid.surfaceY()) {
            if (grid.isFree(x, z)) {
                return new Outcome(Kind.WATER, new BlockPos(x, grid.surfaceY(), z));
            }
            // Column already filled or not a playable cell.
            return Outcome.HIT_BLOCK;
        }
        return Outcome.OUT_OF_POOL;
    }

    /**
     * True when a collision box of a block is right under the feet of the player.
     */
    static boolean isSupported(World world, BoundingBox box) {
        double feet = box.getMinY();
        int minX = (int) Math.floor(box.getMinX() + 0.001);
        int maxX = (int) Math.floor(box.getMaxX() - 0.001);
        int minZ = (int) Math.floor(box.getMinZ() + 0.001);
        int maxZ = (int) Math.floor(box.getMaxZ() - 0.001);
        int topLayer = (int) Math.floor(feet - GROUND_EPSILON);
        for (int y = topLayer; y >= topLayer - 1; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable()) {
                        continue;
                    }
                    for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                        double top = y + shape.getMaxY();
                        if (Math.abs(top - feet) <= GROUND_EPSILON
                                && x + shape.getMinX() < box.getMaxX() && x + shape.getMaxX() > box.getMinX()
                                && z + shape.getMinZ() < box.getMaxZ() && z + shape.getMaxZ() > box.getMinZ()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
